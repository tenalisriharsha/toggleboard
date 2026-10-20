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
│   ├── domain/                Flag, Environment, Variant, StableHashRolloutEngine, FlagEvaluator
│   ├── repository/            Storage interfaces + in-memory impl (DynamoDB impl in Phase 2)
│   ├── service/                Business logic: validation, orchestration
│   ├── web/                   REST controllers + DTOs
│   ├── security/              Per-environment API key filter (evaluation data-plane)
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

### Phase 2 — Persistence, Targeting & Audit (Session 2)
- [ ] DynamoDB-backed `FlagRepository`/`EnvironmentRepository` using the embedded
      DynamoDB Local Maven dependency for tests (no Docker)
- [ ] Targeting rules: match on user attributes (equals/in/contains) before rollout
- [ ] Segments: reusable named groups of targeting rules, referenced by flags
- [ ] Audit log: every create/update/delete/kill-switch-toggle recorded with actor,
      timestamp, before/after diff; `GET /audit` endpoint
- [ ] Role-based access control for admin CRUD endpoints (admin vs. viewer)
- [ ] Expanded OpenAPI coverage for the new endpoints

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

Start Phase 2 in `toggleboard-api`:
1. Add the embedded DynamoDB Local test dependency and write a `DynamoDbFlagRepository`
   / `DynamoDbEnvironmentRepository` implementing the existing repository interfaces.
2. Add `TargetingRule` and `Segment` to the domain model; extend `FlagEvaluator` to
   check rules before falling back to percentage rollout.
3. Add an `AuditEntry` domain type + `AuditRepository`, and record one on every
   mutating `FlagService`/`EnvironmentService` call.
4. Add a minimal role concept (header-based `X-Role: admin|viewer` is fine for now)
   enforced on the admin CRUD endpoints, separate from the existing API-key filter
   on `/evaluate`.

STATUS: IN_PROGRESS
