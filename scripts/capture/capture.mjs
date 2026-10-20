// Regenerates docs/screenshots by RUNNING the real API and the real console.
//   Terminal images: every command is executed for real (against the API started below); its combined
//   stdout/stderr and exit code are captured as-is and rendered into terminal.html. Nothing is typed, edited
//   or shortened by hand, and the command shown is exactly the command that ran.
//   Browser images: the console and the Swagger UI are screenshotted with Playwright after the demo data was
//   created through the API's own endpoints.
// Usage: cd scripts/capture && npm install && npm run capture   (needs Java 21, Maven, Node, Terraform, jq)
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const shots = path.join(root, 'docs/screenshots');
const work = '/tmp/toggleboard-demo';
fs.rmSync(work, { recursive: true, force: true });
fs.mkdirSync(work, { recursive: true });
const API = 'http://localhost:8080';
const BASE = `${API}/api/v1/environments`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const JSON_H = `-H "Content-Type: application/json" -H "X-Role: admin"`;

function sh(cmd, cwd, opts = {}) {
  const r = spawnSync('bash', ['-c', cmd], { cwd, encoding: 'utf8', ...opts });
  if (r.status !== 0 && !opts.allowFail) throw new Error(`failed: ${cmd}\n${r.stdout}\n${r.stderr}`);
  return r;
}

function findChromium() {
  for (const dir of [path.join(os.homedir(), 'Library/Caches/ms-playwright'), path.join(os.homedir(), '.cache/ms-playwright')]) {
    if (!fs.existsSync(dir)) continue;
    for (const d of fs.readdirSync(dir).filter((x) => x.startsWith('chromium-')).sort().reverse()) {
      for (const rel of ['chrome-mac/Chromium.app/Contents/MacOS/Chromium', 'chrome-mac-arm64/Chromium.app/Contents/MacOS/Chromium', 'chrome-linux/chrome']) {
        const p = path.join(dir, d, rel);
        if (fs.existsSync(p)) return p;
      }
    }
  }
  return undefined;
}

function runCaptured(cmd, cwd, env) {
  return new Promise((resolve) => {
    const child = spawn('bash', ['-c', cmd], { cwd, env: { ...process.env, ...env } });
    let out = '';
    child.stdout.on('data', (d) => (out += d));
    child.stderr.on('data', (d) => (out += d));
    child.on('close', (code) => resolve({ out: out.replace(/\n$/, ''), code }));
  });
}

let apiKey = '';
async function terminalShot(browser, name, title, cmd, cwd = work) {
  const { out, code } = await runCaptured(cmd, cwd, { API_KEY: apiKey });
  if (!name) { console.log(`ran (no image): exit ${code}`); return { out, code }; }
  const page = await browser.newPage({ viewport: { width: 1040, height: 400 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(here, 'terminal.html'));
  await page.evaluate(({ title, cmd, out, code }) => {
    document.getElementById('title').textContent = title;
    const ex = document.getElementById('exit');
    ex.textContent = 'exit code ' + code;
    ex.className = 'exit ' + (code === 0 ? 'ok' : 'bad');
    const p = document.createElement('span'); p.className = 'p'; p.textContent = '$ ';
    const c = document.createElement('span'); c.className = 'c'; c.textContent = cmd;
    document.getElementById('body').append(p, c, document.createTextNode('\n' + out));
  }, { title, cmd, out, code });
  await page.locator('#win').screenshot({ path: path.join(shots, name + '.png') });
  await page.close();
  console.log(`captured ${name} (exit ${code})`);
  return { out, code };
}

async function waitFor(url, label, tries = 180) {
  for (let i = 0; i < tries; i++) {
    try { const r = await fetch(url); if (r.status < 500) return; } catch { /* not up yet */ }
    await sleep(1000);
  }
  throw new Error(`${label} did not start at ${url}`);
}

const children = [];
function start(cmd, cwd, label) {
  children.push(spawn('bash', ['-c', cmd], { cwd, detached: true, stdio: 'ignore' }));
  console.log(`started ${label}`);
}
function stopAll() { for (const c of children) { try { process.kill(-c.pid); } catch { /* gone */ } } }
process.on('exit', stopAll);

try {
  fs.mkdirSync(shots, { recursive: true });
  for (const f of fs.readdirSync(shots)) if (f.endsWith('.png')) fs.rmSync(path.join(shots, f));

  console.log('building the API ...');
  sh('mvn -B -q -DskipTests install', root);
  sh('terraform -chdir=terraform init -backend=false -input=false -no-color', root, { stdio: 'ignore' });

  start('mvn -B -q -pl toggleboard-api spring-boot:run', root, 'API on :8080 (in-memory storage)');
  await waitFor(`${API}/actuator/health`, 'API');
  start('npx ng serve --port 4200', path.join(root, 'toggleboard-console'), 'console on :4200');
  await waitFor('http://localhost:4200/', 'console', 300);

  const browser = await chromium.launch({ executablePath: findChromium() });

  // ---- terminal images (the first command also creates the environment the rest of the demo uses)
  const env = await terminalShot(browser, null, '',
    `curl -s -X POST ${BASE} ${JSON_H} -d '{"key":"production","name":"Production"}'`);
  apiKey = JSON.parse(env.out).apiKey;

  await terminalShot(browser, null, '',
    `curl -s -X POST ${BASE}/production/segments ${JSON_H} -d '{"key":"beta-users","name":"Beta Users","rules":[{"attribute":"beta","operator":"EQUALS","values":["true"]}]}'`);
  await terminalShot(browser, '02-create-flag', 'create a flag: 40% rollout, targeting rule and a segment',
    `curl -s -X POST ${BASE}/production/flags ${JSON_H} -d '{"key":"new-checkout-flow","name":"New Checkout Flow","type":"BOOLEAN","enabled":true,"rolloutPercentage":40,"targetingRules":[{"attribute":"country","operator":"IN","values":["US","CA"]}],"segmentKeys":["beta-users"]}' | jq .`);

  const flag = (body) => `curl -s -o /dev/null -X POST ${BASE}/production/flags ${JSON_H} -d '${JSON.stringify(body)}'`;
  sh(flag({ key: 'gradual-rollout', name: 'Gradual Rollout', type: 'BOOLEAN', enabled: true, rolloutPercentage: 40 }), work);
  sh(flag({ key: 'dark-mode', name: 'Dark Mode', type: 'BOOLEAN', enabled: true, rolloutPercentage: 100 }), work);
  sh(flag({ key: 'legacy-search', name: 'Legacy Search', type: 'BOOLEAN', enabled: true, rolloutPercentage: 100 }), work);
  sh(flag({
    key: 'checkout-button-color', name: 'Checkout Button Color', type: 'MULTIVARIATE', enabled: true, rolloutPercentage: 100,
    variants: [{ key: 'blue', value: '#2563eb', weight: 50 }, { key: 'green', value: '#16a34a', weight: 30 }, { key: 'orange', value: '#ea580c', weight: 20 }],
    defaultVariantKey: 'blue'
  }), work);
  // switch the legacy search flag off (the kill switch), which lands in the audit log
  sh(`curl -s -o /dev/null -X PUT ${BASE}/production/flags/legacy-search ${JSON_H} -H "X-Actor: sam.rivera" -d '${JSON.stringify({ name: 'Legacy Search', enabled: false, rolloutPercentage: 100 })}'`, work);

  const ev = (extra = '') => `${BASE}/production/flags/new-checkout-flow/evaluate?subjectKey=user-482&country=US${extra}`;
  await terminalShot(browser, '03-evaluate', 'evaluate a flag for a subject with the environment API key',
    `curl -s "${ev()}" -H "X-API-Key: $API_KEY" | jq .`);
  await terminalShot(browser, '04-evaluate-unauthorized', 'no API key: rejected',
    `curl -s -w "\\nHTTP %{http_code}\\n" "${ev()}"`);
  await terminalShot(browser, '05-rollout-distribution', 'stable-hash rollout: a 40% flag across 500 subjects',
    `for i in $(seq 1 500); do curl -s "${BASE}/production/flags/gradual-rollout/evaluate?subjectKey=user-$i" -H "X-API-Key: $API_KEY"; echo; done | awk '/"enabled":true/{on++} END{printf "%d of %d subjects enabled (%.1f%%)\\n", on, NR, 100*on/NR}'`);
  await terminalShot(browser, '06-audit-log', 'every change is recorded in the audit log',
    `curl -s ${BASE}/production/audit | jq -c '.[] | {action, entityType, entityKey, actor}'`);
  await terminalShot(browser, '12-terraform-test', 'terraform test (mocked AWS provider, no AWS account needed)',
    'terraform -chdir=terraform test -no-color', root);

  // ---- browser images
  const shot = async (page, file, opts = {}) => { await sleep(700); await page.screenshot({ path: path.join(shots, file), ...opts }); console.log(`captured ${file}`); };
  const swagger = await browser.newPage({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  await swagger.goto(`${API}/swagger-ui.html`);
  await swagger.waitForSelector('.opblock', { timeout: 30000 });
  await shot(swagger, '01-swagger-overview.png');
  await swagger.close();

  const page = await browser.newPage({ viewport: { width: 1280, height: 760 }, deviceScaleFactor: 2 });
  await page.addInitScript(() => localStorage.setItem('toggleboard.currentEnvironmentKey', 'production'));
  await page.goto('http://localhost:4200/flags');
  await page.waitForSelector('table tbody tr', { timeout: 30000 });
  await shot(page, '07-console-flags.png', { fullPage: true });
  await page.goto('http://localhost:4200/flags/checkout-button-color');
  await page.waitForSelector('.form-grid', { timeout: 30000 });
  await shot(page, '08-console-flag-edit.png', { fullPage: true });
  await page.goto('http://localhost:4200/segments');
  await page.waitForSelector('table tbody tr', { timeout: 30000 });
  await shot(page, '09-console-segments.png', { fullPage: true });
  await page.goto('http://localhost:4200/audit');
  await page.waitForSelector('text=legacy-search', { timeout: 30000 });
  await shot(page, '10-console-audit.png', { fullPage: true });
  await page.goto('http://localhost:4200/environments');
  await page.waitForSelector('table tbody tr', { timeout: 30000 });
  await shot(page, '11-console-environments.png', { fullPage: true });
  await browser.close();
} finally {
  stopAll();
  console.log(`scratch dir: ${work}`);
}
