# Agent 04: home and attendance migration

## Commits and scope

- Launch baseline: `acb90da4df7f2148c0d53420da2c67a5d322aa68`.
- Source and test commit: `2a8a08574b30f58eea46950755537fedd7f2c0f5`.
- Follow-up commit: `b26d8b40a95644aa377b47b3181df96b147e857c`.
- Review regression tests cherry-picked as `f066ddee3a0171b32b5e0c506b518442362c6533` from `f18a954da7b1680065db1a18c52feb82c7be3016`.
- Review corrections: `baae244807499e69480b5db8920506aafaaa3a5d`.
- Changed only `ui/dashboard/*`, `ui/attendance/*`, `ui/daywise/*`, `ui/academics/*`, their tests, and this report.
- No repository, coordinator, shared UI, or lifecycle files changed. No public data API was added.

## Behavior

- Home observes summary and week snapshots separately. Either section can render while the other waits or fails. A failed refresh keeps saved content and shows its error.
- Overall Attendance observes summary and current week snapshots. Threshold, combined attendance, and semester end date recompute presentation from existing data. Forecast still uses the weekly timetable.
- Day-wise observes `DaywiseKey` range snapshots. It uses server date and slot records, keeps the snapshot update time, and declares month demand only while its tab is settled and visible.
- The Academics pager declares overall and planner priority from `settledPage`. Day-wise declares its own visible range from that same settled state.
- Home and Overall manual refresh request both summary and timetable through the coordinator. Day-wise manual refresh requests its current range.
- Shared `AppCard` and `AppSectionLabel` replace repeated simple surfaces and section labels. Selection, month, scroll, and pull-to-refresh state remain screen local.

## Removed subscriptions and compatibility

- Removed three screen-owned `DataRefreshSignal` subscriptions and emissions.
- Removed Home's `ON_RESUME` cache reload, Day-wise's one-time forced visible refresh, and semester-change refetch loops.
- Removed direct screen calls to `getAttendance`, `getTimetable`, `getDaywiseAttendance`, and profile refresh.
- `getPreferredSemester` remains a compatibility dependency for the selected key on blank settings and for Overall's display label. Saved year and class IDs bind snapshots without an option lookup in Home and Day-wise. Overall resolves the label after binding its saved key.

## Master review corrections

- Semester discovery now publishes loading, selected option, and error as consumer state. Non-cancellation failures show an error and leave collectors active. Refresh retries missing metadata; a selection change also retries. Cancellation still propagates.
- Home, Overall, and Day-wise derive displayed errors from current selection, snapshot, coordinator demand, and manual-refresh sources. Clearing one source removes its stale message without hiding another current failure.
- Account and selected-key transitions clear old derived counts, subjects, forecasts, dates, timestamps, and errors before binding the new key. A label update for the same key does not clear saved content.
- The two supplied Home assertions remain unchanged. Their test cleanup waits for coroutine cancellation before resetting the test Main dispatcher.

## Integration wiring for agent 06 and API gap for the master

- Activate the coordinator once with the matching account, selected semester, and current week before expecting these screens to load. Deactivate before account switch or logout. Do not activate from a screen.
- On selected semester mutations, call `selectSemester`. The screens rebind to the selected key but do not request it themselves. Keep QR invalidation and cache-clearing wiring in agent 06.
- Keep the resolved `activeContext` publication for timetable binding. Home asks for core demand on entry. The settled Overall and Planner tabs ask for their priorities; Day-wise asks for its month.
- Missing API: the coordinator does not expose an observable selected `SemesterOption`. The consumer fallback resolves options through `AttendanceRepository.getPreferredSemester` for Overall's label and blank selections. A coordinator selection flow would remove this remaining lookup.

## Checks and limits

- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin`: passed.
- Seven targeted test classes: 10 tests passed. They cover the original behaviors, both supplied Home regressions, metadata retry on Overall and Day-wise, demand-error recovery without snapshot changes, and preference or selected-key changes during errors.
- `:app:ktlintCheck :app:detekt`: passed. `git diff --check`: passed.
- `adb devices`: no connected device. Screenshots, empty/error visual review, large-text checks, and frame checks were not run.
- Source delta against launch baseline: +692 / -524 production lines, net +168. Test delta: +624 / -0 lines, net +624. The production increase includes explicit error-source state and the temporary semester fallback.
- Integrated lifecycle, settings, QR, account isolation, and device acceptance remain for agents 06 and 07.
