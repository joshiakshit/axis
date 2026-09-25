# Wave 3 launch baseline

Verified on 2026-09-25. Agents 01-05 are integrated. Agent 06 may now run alone. This is a verified implementation baseline, not a finished app or release candidate: session activation and the documented shared API gaps still require integration.

## Commits

- Original comparison baseline: `e672a36102f3108e35b3ee431270d318e6ebaaee`.
- Wave 2 launch baseline: `acb90da4df7f2148c0d53420da2c67a5d322aa68`.
- Accepted grades submission: `c6a54c495c7b0b3ccfdb69244e122b7c6b0eec25`.
- Accepted Home/attendance submission: `402537cf9ae289636569fe75da2d046d3bf50326`.
- Accepted timetable/planner submission: `25eb48df9609ec1dbc2ab85a6be3485a2243b803`.
- Integrated source checkpoint verified here: `d4c839f87ef3d71d33a662f00be1e137947cf04b` on `joshi/lean-integration`.
- Launch baseline: the following documentation-only commit containing this report, pinned by `joshi/lean-wave3-baseline`. Resolve its full SHA with `git rev-parse joshi/lean-wave3-baseline`. The master also supplies the exact SHA with the launch prompt.

Keep the earlier baseline branches fixed. No submitted assignment history was rewritten.

## Validation

- `gradlew.bat :app:assembleDebug test ktlintCheck detekt :app:compileReleaseKotlin --console=plain`: passed, 159 tasks, 37 executed and 122 up to date.
- App tests: 184 passed in each of debug and release. Core tests: 26 passed in each variant. No failures, errors, or skips. This is 210 distinct tests run in both variants, not 420 distinct cases.
- All master regression assertions for data foundation, Home, planner/timetable, and grades pass in the integrated build.
- Backend `npm test`: 55 passed. `npm run typecheck`: passed. Backend source is unchanged by wave 2.
- `git diff --check`: passed.
- `adb devices`: no connected device. Screenshots, PDF viewing/sharing, visual accessibility, and performance checks remain unverified.

The two grade review failures are resolved. Exam selection invalidates obsolete marks work; result changes reset cancelled PDF loading. Controlled tests also cover late marks failure and an old PDF completion during a newer request.

## Agent 06 handoff

Read `06-integration.md`, CONTRACT.md, the corrected foundation contracts, reports 01-05, and the current acceptance updates in `02-master-review.md` and `03-master-review.md`. Historical failed-test sections describe fixed submissions; current open items below and in the acceptance updates remain required.

1. Finish coherent account-bound selection and shared demand APIs first. Remove temporary per-screen semester discovery when callers migrate. Saved keys must bind before optional metadata discovery. Errors and pending work must belong to their originating account, semester, and range.
2. Implement visible-week, required planner coverage, and lower-priority adjacent prefetch through the coordinator. Do not silently drop required weeks as obsolete prefetch. Enforce the startup and nested concurrency bounds in CONTRACT.md. Separate Home's current week from the timetable viewport. Replace direct planner range scheduling and the unexplained temporary 32-week limit with bounded work that preserves requested coverage.
3. Wire app-session activation, actual navigation, preference changes, manual refresh, cache clearing, logout/account replacement, and confirmed originating-account QR success. Startup/profile discovery must not add a network wait to the splash or prevent independent useful content.
4. Remove obsolete repository adapters, duplicate selection/loading/error state, and refresh signals only after all consumers, exports, and tests use the final APIs. Preserve grades, calendar, configuration, notifications, account access, PDF/export behavior, and domain calculations.
5. Replace wall-clock polling in `PlannerCoverageViewModelTest` with controlled dispatch/completion. Verify generation/context after suspending projection work before publishing state. Test old error and projection completions across account, semester, range, and cache-reset transitions, plus simultaneous timetable/planner demand.
6. Run the complete gate in `06-integration.md` after implementation. Record unavailable device/performance checks. Keep the separate backend authentication concern open; do not modify auth/token/backend security within this assignment.

Agent 06 has the narrow cross-file ownership granted in its assignment and the master review. No other implementation agent should edit these paths concurrently. This task requires closing the shared API gaps, not merely connecting MainApp and leaving placeholders.

## Counts

Physical lines in tracked Kotlin/TypeScript, including Android main/debug/release source. Same exclusions and method as the original baseline.

| Category | Original | Wave 3 |
| --- | ---: | ---: |
| App production Kotlin | 19,664 | 20,708 |
| Core production Kotlin | 2,307 | 2,361 |
| Backend production TypeScript | 846 | 846 |
| Total production | 22,817 | 23,915 |
| Android tests | 2,374 | 4,845 |
| Backend tests | 693 | 693 |

Production is temporarily 1,098 lines above the original baseline. Shared paths and transitional code coexist. Delete superseded responsibilities during integration and report the final net result honestly. Do not compress formatting, remove behavior/tests, or create speculative abstractions to meet a reduction target.

## Isolated checkout

- Path: `C:/Coding/Axis/axis-lean-06-integration`.
- Branch: `joshi/lean-06-integration`.
- Starting reference: `joshi/lean-wave3-baseline`, exact launch SHA supplied by the master.
- Local setup: untracked AGENTS.md and ignored SDK-only local.properties. Do not commit them. No production credentials or signing configuration are copied.

Make focused local commits and write `reports/06-integration.md`. Return the final SHA and checks to the master. Do not merge, push, publish, deploy, or start agent 07.
