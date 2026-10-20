# toggleboard — Final Report

A 5-session sprint build of a feature flag service: a Spring Boot 3 REST API backed by
DynamoDB, a standalone Java client SDK, an Angular admin console, and Terraform to run
it all on AWS. All 5 sessions are complete. See [PROGRESS.md](PROGRESS.md) for the
phase-by-phase build log and every design decision; this document summarizes the
result.

## What was built, session by session

**Session 1 — Foundation.** The core domain model (`Flag`, `Variant`, `Environment`,
`FlagType`, `EvaluationResult`), a deterministic stable-hash (FNV-1a) rollout engine
with the property that raising a rollout percentage only ever adds subjects, never
removes them, `FlagEvaluator` (kill switch → boolean rollout / multivariate
resolution), in-memory repositories behind interfaces so later storage swaps wouldn't
touch the service layer, the REST API for environments/flags/evaluation, per-environment
API-key auth on the evaluation endpoint, centralized validation/error handling, and
OpenAPI/Swagger docs. 56 tests.

**Session 2 — Persistence, targeting & audit.** A real DynamoDB-backed
`FlagRepository`/`EnvironmentRepository`/`SegmentRepository`/`AuditRepository` (AWS SDK
v2, selected via the `dynamodb` Spring profile, same interfaces the in-memory repos
implement) tested against the embedded DynamoDB Local Maven dependency — no Docker,
confirmed working on Apple Silicon. `DynamoDbTableInitializer` creates all four tables
idempotently on startup. Targeting rules (`EQUALS`/`IN`/`CONTAINS`) and reusable named
segments, evaluated ahead of the percentage rollout. A full audit log (every
create/update/delete, with the kill switch flip recorded as its own
`KILL_SWITCH_ENABLED`/`KILL_SWITCH_DISABLED` action and a before/after JSON snapshot).
A lightweight `X-Role` RBAC layer on admin CRUD (reads open, mutations need
`X-Role: admin`). 131 tests.

**Session 3 — Client SDK & performance.** `toggleboard-client-sdk`: a standalone Java
module depending on nothing but Jackson and the JDK's own `HttpClient` — no dependency
on `toggleboard-api`'s classes. It evaluates flags over REST, caches results per
flag/subject pair, falls back to the last cached value on failure, refreshes via a
background poller, and subscribes to a new `/stream` SSE endpoint for push updates
with automatic reconnect (exponential backoff, 1s–30s cap). A hand-rolled p50/p99
evaluation-latency benchmark (in-process sub-microsecond, full REST round trip
sub-millisecond on localhost) and a concurrent load test (20 threads × 50 requests,
zero failures) both run as part of `mvn test`, not as one-off scripts. 149 tests in
`toggleboard-api` + 6 in `toggleboard-client-sdk` (155 total), including a real
end-to-end integration test (SDK against the actual running server, not MockMvc).

**Session 4 — Angular admin console.** `toggleboard-console`: an Angular 19 app
(standalone components, no NgModules, no third-party UI library) with a flags list
(inline kill-switch toggle, delete), a create/edit form (rollout slider for boolean
flags, a variant editor with a live weight-sum check for multivariate flags), a shared
targeting-rules editor reused by both flags and segments, a segments list/editor, an
audit log viewer with expandable before/after JSON diffs, and an environments screen
(create an environment, copy its API key, switch which one is current). Wired to the
real running API via an `ApiService`, an `EnvironmentContextService` (signal-based,
persisted to `localStorage`), and an interceptor that stamps every mutation with
`X-Role: admin` + `X-Actor`. A `CorsRegistry` bean lets the console's dev server call
the API directly. 151 tests in `toggleboard-api` (new: a CORS preflight test) + 6 in
`toggleboard-client-sdk` + 38 in `toggleboard-console` (195 total).

**Session 5 — Infrastructure & final polish.** `terraform/`: ECS Fargate running
`toggleboard-api` behind an internet-facing ALB (target group health-checking the new
`GET /actuator/health` endpoint, added this session via
`spring-boot-starter-actuator`), the four DynamoDB tables with names/keys/GSIs matching
`DynamoDbTableInitializer`'s schema exactly, and a private S3 bucket + CloudFront
distribution (Origin Access Control, SPA deep-link fallback to `index.html`) serving
the console's static build. The ECS task role is scoped to only those four tables.
Validated with `terraform fmt`/`validate` and a `terraform test` suite (8 assertions
across 4 files) against `mock_provider` — no real AWS account exists in this
environment, so nothing was ever applied against one. README finalized with a 26-image
Preview section, this report written, full test suite re-run green end to end.

## Final test results

Run fresh at the end of Session 5:

- **`toggleboard-api`**: 152 tests, 0 failures, 0 errors — domain (rollout engine,
  targeting, evaluation), both repository implementations (in-memory and DynamoDB, via
  embedded DynamoDB Local), service-layer validation and audit recording, MockMvc
  integration tests for every endpoint (API-key and `X-Role` auth paths, SSE lifecycle,
  CORS preflight, the health endpoint), and the latency benchmark + load test.
- **`toggleboard-client-sdk`**: 6 tests, 0 failures — evaluation, caching/fallback,
  polling, SSE push and reconnect, against an in-process JDK `HttpServer`.
- **`toggleboard-console`**: 38 Karma/Jasmine tests (headless Chrome), 0 failures.
- **`terraform`**: 8 `terraform test` assertions against `mock_provider`, 0 failures.

**196 application tests + 8 infrastructure test assertions, all green.**

## Known limitations

- **No real user identity.** `X-Role: admin`/`viewer` (admin CRUD) and the
  per-environment API key (the evaluation data-plane) are both self-declared —
  there's no authentication behind either. The console's `X-Actor` header is a
  plain-text field a user can type anything into. This was a deliberate scope cut
  from Session 1 onward, flagged again at the end of every session since; real
  identity/authorization would be the natural next phase.
- **Terraform has never been applied against a real AWS account.** This sprint's
  environment only has dummy credentials, so the module is validated with
  `fmt`/`validate`/`test` (mocked provider) only. A first real `apply` would likely
  surface small issues `validate`/mocked tests can't catch (IAM eventual consistency,
  exact AZ availability for the configured region, ALB/CloudFront propagation timing).
- **No container image build/push pipeline.** `container_image` in Terraform is a
  placeholder variable; there's no Dockerfile or CI step in this repo that builds and
  pushes `toggleboard-api`'s image to a registry Terraform could reference.
- **No HTTPS.** The ALB listener is plain HTTP on port 80 (no ACM certificate
  provisioned), and CloudFront uses its default certificate rather than a custom
  domain.
- **SDK cache key is `(flagKey, subjectKey)`, not including attributes.** A
  deliberate simplification (see PROGRESS.md's Session 3 design decisions) — correct
  for the common case of a subject's attributes not changing between calls, but not
  for a subject re-evaluated with different attributes mid-session.
- **DynamoDB items store `variants`/`targetingRules`/`segmentKeys` as JSON blobs**
  rather than native DynamoDB maps/lists, since nothing queries into them directly.
  This keeps the item shape simple but means no server-side filtering on, say, a
  specific targeting rule's attribute without reading the whole flag back first.

## Future ideas

- Real identity: replace the self-declared `X-Role`/`X-Actor` with actual
  authentication (e.g. OIDC) and move RBAC off a trust-the-caller header.
- A CI pipeline: build/push the `toggleboard-api` image, `ng build` the console,
  `terraform plan` on PRs and `apply` on merge to a real account.
- HTTPS end to end: an ACM certificate for the ALB listener and a custom domain for
  CloudFront.
- A DynamoDB VPC endpoint instead of the Fargate tasks reaching DynamoDB over the
  public internet via their public IPs (would also remove the need for
  `assign_public_ip = true` on the ECS service).
- Multi-region or at least multi-AZ failover drills now that the infrastructure
  exists to provision a second environment from the same Terraform module.
- Flag dependencies / prerequisites (flag B only evaluates if flag A is enabled for
  the same subject) — came up while building targeting rules in Session 2 but was out
  of scope for this sprint.

## Follow-up: public-readiness pass

- The README was restructured for visitors: quick start first, a curated preview, the build history moved to PROGRESS.md.
- The screenshots are now generated by `scripts/capture`, which starts the real API and console, creates the demo
  data through the API and runs every command shown. The earlier set had no generator script and several near-duplicates
  (three `mvn test` captures, a latency benchmark that depended on the machine it ran on); those were dropped.
- Added a LICENSE (MIT) and a CI badge.
