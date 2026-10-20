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

### Phase 3 — Client SDK & Performance (Session 3)
- [ ] `toggleboard-client-sdk`: Java client with local in-memory cache
- [ ] Server-Sent Events (SSE) endpoint for streaming flag updates to subscribers
- [ ] Client SDK polling fallback + SSE subscription with automatic reconnect
- [ ] Evaluation-latency benchmark (JMH or hand-rolled) measuring p50/p99
- [ ] Load test (e.g. Gatling or a simple concurrent harness) against the evaluation endpoint

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

## Where to resume (next session)

Start Phase 3 — a new `toggleboard-client-sdk` Maven module:
1. Scaffold `toggleboard-client-sdk` as a sibling Maven module (add it to the root
   `pom.xml`'s `<modules>`), depending on nothing but a JSON library and an HTTP
   client — it should be usable standalone, not depend on `toggleboard-api`'s classes.
2. A `ToggleboardClient` with a local in-memory cache of evaluated/streamed flags,
   a polling fallback (hit the existing evaluation endpoint on an interval) and an
   SSE subscription for push updates once the SSE endpoint below exists.
3. Add an SSE endpoint to `toggleboard-api` (e.g.
   `/api/v1/environments/{environmentKey}/stream`) that pushes flag changes to
   subscribers; hook it into `FlagService`'s mutating calls so a create/update/delete
   publishes an event. Reuse the existing API-key filter pattern for auth on this path.
4. Client SDK: automatic reconnect with backoff if the SSE connection drops, falling
   back to polling in the meantime.
5. Evaluation-latency benchmark (JMH or hand-rolled) measuring p50/p99 for
   `FlagEvaluator.evaluate(...)` directly (no HTTP) and for the full REST round trip.
6. A load test against the evaluation endpoint (Gatling, or a simple
   `ExecutorService`-based concurrent harness if Gatling adds too much setup weight).

No known blockers carried over from Session 2 — the DynamoDB profile, targeting/
segments, audit log and RBAC are all done and tested. The `software.amazon.awssdk`
BOM version is pinned at 2.57.0 in `toggleboard-api/pom.xml`; reuse that version (or
bump deliberately) if the client SDK or anything else ends up needing AWS SDK types.

STATUS: IN_PROGRESS
