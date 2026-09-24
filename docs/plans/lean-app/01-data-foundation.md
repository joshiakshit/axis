# Agent 01: academic data foundation

Run in wave 1 beside agent 02. Read README.md and CONTRACT.md. Implement this assignment only.

## Objective

Build the reusable attendance/timetable data path and small coordinator that consumer agents will use. Keep existing callers working during this wave. Do not migrate screens or wire app lifecycle yet.

## Start with

- `data/repository/AttendanceRepository.kt`, `TimetableRepository.kt`, their cache stores and `TimetableNormalizer.kt`.
- `data/repository/AuthRepository.kt`, `StudentApiParser.kt`, and `CachedFeed.kt` as read-only dependencies.
- `data/db/CacheDao.kt`, `CacheEntity.kt`; `core/storage/CachePolicy.kt`.
- `domain/model/Auth.kt`, `Attendance.kt`, `Timetable.kt`; relevant repository and routing tests.
- `docs/PROJECT_MEMORY.md` for the timetable identity and routing constraints. Current code and tests override stale implementation descriptions.

Paths without a root above are under `app/src/main/kotlin/com/ash/axis/`.

## Write ownership

- New `app/.../data/academic/*` files.
- Attendance and timetable repositories, their cache stores, and relevant new cache helpers under `data/db/`.
- `CacheDao.kt` only if a necessary cache operation cannot use the existing API. No schema migration is expected.
- Matching tests under `app/src/test/.../data/academic/`, `data/db/`, and the attendance/timetable repository test files.

Do not edit AuthRepository, TokenManager, StudentApiParser, domain wire models, DI modules, consumers, grades, or shared UI. Report a required cross-scope change to the master.

## Work

1. Add characterization tests for current cache fallback, successful empty responses, routing, and forced refresh where coverage is missing.
2. Consolidate repeated JSON cache reading/writing into a small typed helper. Keep policy decisions in repositories. Expose successful timestamps and stale/error metadata. Publish its API for agent 03.
3. Give repositories observable data and request reuse by complete resource key. Keep network parsing and cache policy out of the coordinator. Avoid a second permanent store holding the same snapshots.
4. Implement active-context lifecycle and core-data priority in a small coordinator. Its public entry points must support activation, visible demand, manual refresh, invalidation, and deactivation. Initialization alone must not start global work.
5. Hydrate available saved data before route discovery. Reuse route discovery per account/request context during the session, with explicit invalidation on forced refresh/context change. Preserve strict routing and legacy fallback rules.
6. Reuse matching timetable transport work across weekly/date consumers if equivalent normalization can be proved. Otherwise document the remaining duplication for integration; do not discard semantics.
7. Retain old repository methods only as transitional adapters where existing callers need them. Enumerate them in the report so agent 06 can remove unused paths.

Do not implement a database of jobs, a general priority queue framework, generic repository inheritance, or background OS scheduling. Support the concrete operations in CONTRACT.md.

## Required tests

Cached data publishes before a blocked network request completes; fresh cache avoids network work; stale cache revalidates; empty success differs from failure; failure preserves data/timestamp; duplicate demands share one call; cancellation is propagated; account switch rejects late results; cache clearing rejects late writes; forced refresh runs despite valid cache; invalidation during a load schedules a newer load; visible demand is not stuck behind queued speculative weeks.

Exercise independent attendance/timetable success and failure. Check exact-case client ID and separate profile/attendance academic years through existing routing tests.

## Checks and deliverables

Run targeted data tests with `:app:testDebugUnitTest --tests ...`, then `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`. Fix failures caused by this change. Do not run release or backend validation here.

Write `reports/01-data-foundation.md` and `reports/01-data-contract.md`. The contract must show actual types, constructor/lifecycle ownership, account keys, method signatures, freshness/error semantics, cancellation behavior, and short examples for each consumer. Include the JSON cache helper API for grades. Keep the temporary compatibility inventory explicit.

Stop after the report and focused local commits. The master freezes the contract before wave 2.
