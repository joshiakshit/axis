# Agent 06: lifecycle integration and final cleanup

Run alone in wave 3 after the master integrates all five preceding assignments. Read README.md, CONTRACT.md, reports/00-wave3-baseline.md, and reports 01-05. Follow the current acceptance updates in the master reviews. Do not reopen settled architecture choices without evidence of a defect.

## Objective

Connect the shared data path to real app lifecycle, navigation, preferences, QR success, and cache clearing. Retire obsolete refresh paths. Validate the complete app.

## Write ownership

- `MainActivity.kt`, `MainApp.kt`, `SessionGate.kt`, `SessionViewModel.kt`, and necessary small app-lifecycle adapters.
- `di/AppModule.kt`, `data/DataRefreshSignal.kt`, `ui/qr/QrScanViewModel.kt`, `ui/settings/SettingsViewModel.kt`.
- `data/export/DataExporter.kt` only for compatibility with the final repository APIs; preserve export behavior and formats.
- Dead transitional APIs in earlier agents' files, after confirming all callers are migrated. No new broad refactor.
- Integration tests, minimal debug-only measurement support if necessary, and screenshots in `docs/screenshots/lean-app/06/`.
- Master-approved wave 2 API closure: `data/academic/*`, attendance/timetable repository demand APIs, matching tests, and the minimal 04/05 consumer changes needed to adopt coherent selected-semester state and bounded planner/prefetch requests. Read `reports/02-master-review.md`. This is limited to the documented gaps; do not reopen unrelated data architecture.

Keep auth/token storage, backend, dependency upgrades, signing, release versions, and deployment outside this task. Escalate a required security/lifecycle change to the master rather than weakening isolation.

## Work

1. Activate the coordinator for the current signed-in account. Hydrate cached data without adding a network wait to the splash. Deactivate and invalidate correctly on account replacement/logout.
2. Connect the actual initial route and later visible demand. The current `planner` navigation route renders TimetableScreen; do not mistake it for the attendance-planner subtab. Respect Academics settled subtab demands from agent 04.
3. Keep account access, configuration refresh, update flow, and notification behavior intact. Core warmup must not block grades/settings or QR shortcut entry.
4. Route manual refresh, selected semester changes, cache clearing, and successful QR mutations through the new invalidation path. Capture the originating account for QR results. Invalidate both summary and affected daily attendance. A late old-account QR result must not refresh or overwrite the new account.
5. Remove DataRefreshSignal only after all remaining callers are migrated. If non-core consumers still use it, retain a narrow path with a documented reason. Do not leave parallel old/new fetching for the same dataset.
6. Remove unused transitional repository APIs, duplicate raw state, and dead imports. Do not remove methods still used by grades, exports, or tests of live behavior.
7. Validate DI in debug and release compilation. Reconcile integrated API mismatches with the responsible agent's contract.
8. Close the shared API gaps assigned in `reports/02-master-review.md`. Publish account-bound semester selection, separate visible/current-week demand from required planner coverage and speculative prefetch, update the data contract, and remove temporary consumer selection discovery. Add controlled consumer-level tests for these transitions. A missing coverage API or per-resource semaphore alone does not satisfy integrated priority acceptance.

## Automated gate

Run `./gradlew :app:assembleDebug test ktlintCheck detekt` and `:app:compileReleaseKotlin`. On Windows use gradlew.bat. Run backend `npm test` and `npm run typecheck` once as a final unchanged-service regression check. Never call release.sh or deploy.

Add integration coverage for cached startup, independent dataset failures, account-switch late completion, logout/cache-clear late writes, semester selection, QR invalidation during an existing load, and identical requests from multiple consumers.

## Device and performance gate

If a device is available, use the same device, build type, account/fixture, and cache state for baseline and candidate. A debug run cannot establish release performance. Prefer an unsigned/non-distributed comparable build if signing is not part of the approved task.

Measure several repeated runs for cached cold entry, empty-cache entry, warm tab switches, and timetable scrolling. Record the method, sample count, median/tail observations, request counts, and frame timing where available. Do not claim a latency improvement from source inspection. Do not add a large benchmark module just to complete the report.

Exercise Home, Attendance, Day-wise, Planner, Timetable, Grades, settings, account switching, exports, notification/calendar views, and QR entry. Test success invalidation with fakes; live attendance submission requires separate user authorization.

Acceptance: cached first content has no network dependency; identical requests are shared; visible work is not delayed by speculative weeks; failure preserves usable content; switching accounts cannot show old data; no measured material startup/scroll regression. Record any unavailable device/production gate as open.

Write `reports/06-integration.md` with final architecture map, removed compatibility paths, exact validation, performance evidence, deferred gates, and baseline-to-final line counts separated into production and tests. Stop after focused local commits. Do not label the build production-ready or publish it.
