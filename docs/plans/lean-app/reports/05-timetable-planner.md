# Agent 05: timetable and planner migration

## Commits and scope

- Baseline: `acb90da4df7f2148c0d53420da2c67a5d322aa68` on `joshi/lean-05-timetable-planner`.
- Timetable: `8c5611783e5c1e5718297ff36044d9d00bee7c02`.
- Planner: `4d3534ff9d7475f8bde52af836d33ce45d7469d4`.
- Planner demand errors: `63a0b39ef536f51d75a8d78ed7071b4a26fa071d`.
- Master regression tests: `c53bb86` (cherry-picked from `e37c103ca5483b3c5b315ff5825357233a63f8a4`).
- Consumer corrections: `5a96efe`.
- Changed only `ui/timetable/*`, `ui/planner/*`, corresponding tests, and this report. No public foundation API changed.
- Production Kotlin: **173 more physical lines**. Test Kotlin: **534 more physical lines**. Counts compare this branch with the baseline and exclude this report.

## Loading and behavior

- Timetable removed its raw week map, cache peek, direct `getTimetable` loop, refresh-signal listener, and requests for offscreen pager pages. It observes `TimetableRepository.observeWeek` by the resolved `activeContext` and visible date range. The progress ticker reads the current snapshot and only rebuilds presentation. Settled swipes and jumps declare visible demand through `timetableVisible`; manual refresh and retry use the coordinator's forced profile refresh.
- Planner removed direct `getAttendance`, `getTimetable`, `getDateKeyedTimetable`, the four-week startup fanout, raw date cache, and refresh-signal listener. It binds saved year/class IDs directly to the shared summary before any optional metadata lookup. It observes summary, current week, and a bounded set of used month and projection weeks. Changed, cleared, and recovered snapshots replace derived dates. Month changes and projection horizons request additional used coverage. Threshold, combined-attendance, and semester-end changes recompute from observed snapshots.
- Both consumers rebind keys when resolved `activeContext` changes. Timetable retains export date updates. Planner keeps dated slots above weekly fallback, markers and explicit no-class rules, manual today-attendance choices, and selected dates. Unknown dates remain unknown in the simulator. Calendar loading and month changes remain a separate feed. University calendar entries remain informational.
- Timetable clears a week's derived days and loaded membership when its snapshot has no data. Failed refreshes with saved data retain that content. Planner clears current and used range data on snapshot resets and restores it when snapshots return. The simulator blocks date choices until today's schedule is covered.
- Planner suppresses projected rows while a needed week is pending, missing, failed, or beyond its bounded horizon. Legacy weekly fallback remains labeled as an estimate. It does not mark an estimated empty day as confirmed no classes.
- Applied `AppCard` and `AppSectionLabel` to repeated timetable and planner surfaces. Existing navigation and calendar layouts remain intact.

## Dependencies and limits for the master

- Agent 06 must activate and deactivate the coordinator for the account and destination. This branch does not start the session. Agent 06 also owns retirement of old lifecycle and refresh wiring.
- The coordinator has no speculative prefetch API. This branch does **not** request adjacent weeks as visible demand. Add a lower-priority adjacent-week API if prefetch remains required.
- The coordinator also has no required multi-week planner coverage API. Planner calls repository `requestWeek` for bounded weeks shown in the month or used by a projection. That API prioritizes every key and can cancel older queued keys. Missing weeks remain visibly incomplete. Agent 06 must provide required coverage without starvation. Global priority and concurrency remain an integration gate.
- No device was connected (`adb devices` returned an empty list). Date jumps, week boundaries, markers, enlarged text, visual layout, and frame checks are unverified on device. No screenshots were captured.

## Verification

- `:app:testDebugUnitTest` scoped to `PlannerCoverageTest`, `PlannerCoverageViewModelTest`, `PlannerSelectionReviewTest`, `TimetableViewModelTest`, `PlannerUseCaseTest`, and `TimetableUseCaseTest`: **49 passed**, no skips or failures. The two master regression assertions remain unchanged. Fixture stubs and coroutine cleanup were added for their newly reached paths.
- `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`: passed in the final Gradle run.
- `git diff --check`: passed.
- Backend tests and the full integrated gate were not run in this assignment. Agent 06 owns the full gate after integration.
