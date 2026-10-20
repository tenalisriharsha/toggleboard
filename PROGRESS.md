# toggleboard — Progress

## Vision

toggleboard is a feature flag service: a Spring Boot 3 REST API backed by DynamoDB,
supporting boolean and multivariate flags, percentage rollouts with stable hashing,
targeting rules (user attributes, segments, environments), a kill switch, and a full
audit history of every change. It ships a Java client SDK with local caching and
streaming updates (SSE), plus an Angular admin console for managing flags,
environments, segments, rollout sliders and the audit log. Access is scoped by
per-environment API keys and role-based access control, with OpenAPI docs describing
the whole surface. Terraform provisions it on AWS (ECS Fargate, DynamoDB, ALB,
CloudFront for the console), and the project is validated with Testcontainers/DynamoDB
Local, a load test, and an evaluation-latency benchmark.

This is a 5-session sprint build, worked on in public.

## Architecture

```
toggleboard/
├── toggleboard-api/          Spring Boot 3 REST API (flags, environments, evaluation)
│   ├── domain/                Flag, Segment, TargetingRule, AuditEntry, FlagEvaluator, ...
│   ├── repository/            Storage interfaces
│   │   ├── memory/             In-memory implementations (default profile)
│   │   └── dynamodb/           DynamoDB implementations (dynamodb profile) + table init
│   ├── service/                Business logic: validation, orchestration, audit recording
│   ├── web/                   REST controllers + DTOs
│   ├── security/              API-key filter (evaluation data-plane) + X-Role filter (admin CRUD)
│   └── exception/              Centralized error handling
├── toggleboard-client-sdk/    Java client: local cache + SSE streaming (Phase 3)
├── toggleboard-console/       Angular admin console (Phase 4)
├── terraform/                 ECS Fargate, DynamoDB, ALB, CloudFront (Phase 5)
└── docs/screenshots/          Preview images for the README
```

**Design decisions from Session 1:**
- Storage is behind a `FlagRepository` / `EnvironmentRepository` interface so the
  DynamoDB implementation (Phase 2) is a drop-in replacement for the in-memory one
  used today — the service layer never changes.
- Rollout uses 64-bit FNV-1a hashing of `flagKey:subjectKey` bucketed into `[0, 100)`.
  This is dependency-free, deterministic, and has the property that raising a flag's
  rollout percentage only ever adds subjects to the rollout, never removes existing ones.
- The kill switch (`enabled=false`) is checked before rollout/targeting and always wins.
- The evaluation endpoint (the client SDK data-plane) is the only one gated by API key
  today; admin CRUD endpoints get full RBAC in a later phase.

**Design decisions from Session 2** (see the Phase 2 section below for the full list).

## Build Plan

### Phase 1 — Foundation (Session 1) ✅ DONE
- [x] Maven multi-module scaffold (`toggleboard` parent, `toggleboard-api`)
- [x] Core domain model: `Flag`, `Variant`, `Environment`, `FlagType`, `EvaluationResult`
- [x] `StableHashRolloutEngine`: deterministic percentage rollout + weighted variant picking
- [x] `FlagEvaluator`: kill switch → boolean rollout / multivariate resolution
- [x] In-memory `FlagRepository` / `EnvironmentRepository` (interface-first, DynamoDB-ready)
- [x] REST API: environments CRUD, flags CRUD, flag evaluation endpoint
- [x] Per-environment API key auth filter guarding the evaluation endpoint
- [x] Centralized validation + error handling (`GlobalExceptionHandler`)
- [x] OpenAPI/Swagger UI docs
- [x] 56 tests (unit + MockMvc integration), all green
- [x] README with Preview section, PROGRESS.md

### Phase 2 — Persistence, Targeting & Audit (Session 2) ✅ DONE
- [x] DynamoDB-backed `FlagRepository`/`EnvironmentRepository`/`SegmentRepository`/
      `AuditRepository` using the real AWS SDK v2 `DynamoDbClient`, selected via the
      `dynamodb` Spring profile (default profile stays in-memory); tested against the
      embedded DynamoDB Local Maven dependency (no Docker) — confirmed working
      end-to-end on this machine (Apple Silicon), including a manual smoke test
      against a real running instance of the app with `--spring.profiles.active=dynamodb`
- [x] `DynamoDbTableInitializer`: creates all four tables (with their GSIs) on startup
      if they don't already exist, idempotent against both DynamoDB Local and real AWS
- [x] Targeting rules: `EQUALS`/`IN`/`CONTAINS` on subject attributes, matched directly
      on a flag (ORed together) ahead of its referenced segments and the percentage
      rollout; an optional `overrideVariantKey` picks a specific variant on match
- [x] Segments: reusable named groups of targeting rules (ANDed together), referenced
      by flags via `segmentKeys`; CRUD under `/environments/{env}/segments`
- [x] Audit log: every create/update/delete recorded with actor, timestamp, and
      before/after JSON snapshots; a flag's `enabled` flip is recorded as its own
      `KILL_SWITCH_ENABLED`/`KILL_SWITCH_DISABLED` action, not a generic update;
      `GET /environments/{env}/audit` with optional `entityType`/`limit` filters
- [x] Role-based access control: `X-Role: admin` required for mutating admin-CRUD
      calls (`X-Role: viewer` → 403, missing header → 401); reads are unrestricted;
      the evaluation data-plane keeps using the API-key filter instead
- [x] Evaluation endpoint accepts arbitrary extra query params as subject attributes
      for targeting, alongside `subjectKey`
- [x] 131 tests, all green (up from 56): targeting/segment matching, repository
      contract tests against both in-memory and DynamoDB-backed implementations,
      audit recording, RBAC 401/403 paths, evaluation-reason coverage
- [x] README Preview section refreshed with 6 new screenshots (13 total)

**Design decisions from Session 2:**
- DynamoDB repositories store `variants`/`targetingRules`/`segmentKeys` as JSON-blob
  attributes rather than nested DynamoDB maps/lists — these are never queried on
  directly, only read back whole with the rest of the flag, so a JSON blob keeps the
  item shape simple without losing anything.
- The audit table's sort key is `<ISO-8601 timestamp>#<id>`, so a descending query on
  `environmentKey` alone returns newest-first without a separate GSI.
- `software.amazon.awssdk:apache5-client` had to be excluded (pom.xml) wherever it was
  pulled in transitively: the AWS SDK BOM version this project uses (2.57.0) resolves
  `apache5-client` against a `httpclient5` version too old for it
  (`NoClassDefFoundError: TlsSocketStrategy`), which breaks the SDK's HTTP-client SPI
  discovery at runtime. `url-connection-client` is the actual HTTP client in use; it's
  lighter-weight anyway and this service makes no highly-concurrent DynamoDB calls.
- Embedded DynamoDB Local's `-inMemory` mode does **not** need the native
  `sqlite4java` library it ships as a dependency, so it runs fine on Apple Silicon
  (arm64) despite that library only ever having had x86_64/PPC builds — confirmed by
  a standalone spike before committing to this approach over a plain in-memory
  repository for the "DynamoDB" layer.

### Phase 3 — Client SDK & Performance (Session 3) ✅ DONE
- [x] `toggleboard-client-sdk`: new Maven module, sibling to `toggleboard-api`, depending
      on nothing but Jackson (JSON) and the JDK's own `java.net.http.HttpClient` — no
      dependency on `toggleboard-api`'s classes, so it's genuinely standalone
- [x] `ToggleboardClient`: `evaluate(flagKey, subjectKey[, attributes])` hits the REST
      endpoint and caches the result per flag/subject pair; on failure, falls back to
      the last cached value (throws `ToggleboardClientException` only if there's no
      cached value at all)
- [x] Background poller (`ScheduledExecutorService`, configurable interval) refetches
      every cached entry
- [x] New SSE endpoint `GET /api/v1/environments/{environmentKey}/stream` on
      `toggleboard-api`, guarded by the same API-key filter as `/evaluate`
      (`DataPlanePaths`, generalized from the old single-pattern `EvaluationPaths`);
      `FlagChangePublisher` fans a `flag-change` event out to every environment's SSE
      subscribers; `FlagService` publishes `CREATED`/`UPDATED`/`DELETED` events on every
      create/update/delete
- [x] SDK's SSE subscriber: reads the stream via `HttpClient.send(..., BodyHandlers.ofLines())`
      (non-blocking per the JDK's own semantics — returns once headers arrive, then the
      `Stream<String>` blocks per line as data arrives), parses SSE frames by hand (no
      extra dependency), and on each event proactively refetches every cached
      flag/subject pair for that flag — so a push updates the cache before the next
      `evaluate()` call, not just the next poll tick. Automatic reconnect with
      exponential backoff (1s → 30s cap) on disconnect; polling keeps running
      regardless as the fallback.
- [x] `SdkDemo` (`toggleboard-client-sdk`'s `examples` package): a runnable end-to-end
      walkthrough — create env/flag via the admin API, evaluate via the SDK, flip the
      kill switch via the admin API, watch the push notification arrive and the cache
      refresh itself
- [x] Hand-rolled evaluation-latency benchmark (`EvaluationLatencyBenchmarkTest`):
      p50/p99 for `FlagEvaluator.evaluate()` directly (50k samples, sub-microsecond)
      and for the full REST round trip over a real randomly-ported embedded server
      (300 samples, sub-millisecond on localhost); runs as part of `mvn test` with
      generous (non-flaky) ceiling assertions, not just a one-off script
- [x] Concurrent load test (`EvaluationLoadTest`): 20 threads × 50 requests against
      `/evaluate` over a real embedded server, asserting zero failures; also part of
      `mvn test`
- [x] `ClientSdkIntegrationTest` in `toggleboard-api` (test-scope dependency on
      `toggleboard-client-sdk`): proves the SDK and server actually interoperate —
      a real HTTP evaluation round trip and a real SSE push notification, not MockMvc
- [x] 155 tests total (149 in `toggleboard-api`, up from 131; 6 new in
      `toggleboard-client-sdk`), all green
- [x] README Preview section refreshed with 4 new screenshots (17 total): the SDK
      demo's push-notification flow, the latency benchmark, the load test, and a
      refreshed full-suite `mvn test` run

### Phase 4 — Angular Admin Console (Session 4)
- [ ] Angular workspace scaffold (`toggleboard-console`)
- [ ] Flags list + detail/edit view with rollout sliders
- [ ] Environments and segments management screens
- [ ] Audit log viewer
- [ ] Wired to the real API (environment switcher, API key display)

### Phase 5 — Infrastructure & Final Polish (Session 5)
- [ ] Terraform: ECS Fargate service, DynamoDB tables, ALB, CloudFront for the console
      (validated with `terraform fmt`/`validate`/`plan` and mocked-provider tests only —
      no real AWS account is used)
- [ ] Full test suite green end-to-end (API, SDK, console)
- [ ] README finalized, DAILY_REPORT.md written
- [ ] STATUS flipped to COMPLETE below

**Design decisions from Session 3:**
- `DataPlanePaths` (renamed from the old single-pattern `EvaluationPaths`) now lists
  both `/evaluate` and `/stream`, shared by `ApiKeyAuthFilter` (guards both) and
  `RoleAuthFilter` (excludes both from the `X-Role` requirement) — adding a third
  data-plane path later is a one-line change to that list, not a filter rewrite.
- `FlagChangePublisher` holds `SseEmitter`s in a `Map<environmentKey, List<SseEmitter>>`
  and removes one on `onCompletion`/`onTimeout`/`onError` or on a failed `send()` —
  there's no separate heartbeat/cleanup task, since a client that's gone will trip one
  of those callbacks (or the emitter's own timeout) on its own.
- The SDK's cache key is `(flagKey, subjectKey)`, not `(flagKey, subjectKey, attributes)`
  — if the same subject is evaluated again with different attributes, that's a fresh
  network call that replaces the cached entry. This is a deliberate simplification: in
  practice the same subject's attributes don't change between calls, and tracking every
  distinct attribute combination per subject would complicate the cache for no real
  benefit in a sprint-scoped SDK.
- The SDK's own tests run against a plain JDK `com.sun.net.httpserver.HttpServer`
  instead of Spring/MockMvc, keeping `toggleboard-client-sdk`'s test classpath as
  dependency-light as its main classpath; the one true end-to-end proof (SDK talking to
  the *real* Spring app) lives in `toggleboard-api`'s `ClientSdkIntegrationTest`
  instead, via a test-scope-only dependency from `toggleboard-api` on
  `toggleboard-client-sdk` (the dependency direction is test-only and one-way — the SDK
  module still depends on nothing).
- `HttpClient` gained `AutoCloseable`/`shutdownNow()` in JDK 21; `ToggleboardClient`
  uses that to abort an in-flight SSE connection immediately on `close()` rather than
  waiting for a graceful (and here, never-arriving) end of stream.

## Where to resume (next session)

Start Phase 4 — the Angular admin console (`toggleboard-console`):
1. Scaffold an Angular workspace at `toggleboard-console/` (Angular CLI, standalone
   components are fine — no need for NgModules). It's a separate app, not a Maven
   module; it talks to `toggleboard-api` over HTTP the same way the client SDK does,
   just using the admin CRUD endpoints (`X-Role: admin`) instead of the data-plane.
2. Flags list + detail/edit view: boolean vs. multivariate, rollout percentage slider,
   targeting rules editor, segment picker, kill switch toggle.
3. Environments screen (create environment, show its API key) and segments management
   (reusable targeting-rule groups).
4. Audit log viewer: filterable by entity type, newest first (the API already supports
   this via `GET /environments/{env}/audit?entityType=...&limit=...`).
5. An environment switcher in the UI chrome, and a way to see/copy the current
   environment's API key (useful for pointing the client SDK or a `curl` call at it).
6. Wire it all to the real, running `toggleboard-api` (CORS will need enabling on the
   API for the console's dev-server origin — check whether Spring's default same-origin
   policy needs a `CorsConfigurationSource` bean, since there currently isn't one).

No known blockers carried over from Session 3 — the client SDK, SSE streaming, the
latency benchmark and the load test are all done and tested. `toggleboard-api`'s test
suite now has a test-scope dependency on `toggleboard-client-sdk`
(`toggleboard-api/pom.xml`); if Phase 4 adds any new cross-module test dependency, run
`mvn install -DskipTests` at the repo root first so the reactor can resolve it (a plain
`mvn -pl <module> test` without `-am` only works once the dependency is already
installed locally).

STATUS: IN_PROGRESS
