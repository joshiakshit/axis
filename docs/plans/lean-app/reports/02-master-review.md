# Wave 2 review: agents 04 and 05

Reviewed submissions: `9603ce2f9df7999a28d54cedb387df917f9a5406` (04) and `feb453638aeac6ec285c49e2dd11b106b7f0e1e9` (05).

Initial decision: return both for focused corrections. The original submissions were combined only in `C:/Coding/Axis/axis-lean-wave2-review`, branch `joshi/lean-wave2-review`. The updates below supersede that decision for agents 04 and 05 at the consumer correction stage. This review does not assess assignment 03.

## Agent 05 correction acceptance, 2026-09-25

- Reviewed final submission: `25eb48df9609ec1dbc2ab85a6be3485a2243b803`. The supplied reset and saved-selection assertions remain unchanged. New fixture stubs cover paths reached after the fixes.
- Accepted for staged integration. Cherry-picked all seven assignment commits onto `joshi/lean-integration`, ending at `0b4e3398a039a16dc5fa6d2df35ba17468aff18e` before this documentation update.
- Verified together with accepted agent 04: `:app:testDebugUnitTest` filtered to the academics, attendance, dashboard, daywise, planner, and timetable UI packages plus `PlannerUseCaseTest` and `TimetableUseCaseTest`; then `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`. All passed: 61 tests, no failures, errors, or skips. `git diff --check` passed.
- Derived timetable weeks now respond to snapshot resets. Planner binds saved IDs before metadata lookup, observes used coverage continuously, removes cleared dates, and distinguishes incomplete, failed, and estimated projection inputs.
- Agent 06 must replace direct per-week requests with the assigned shared coverage API. `syncCoverage` currently launches every wanted request and `requestWeek` prioritizes each key, so queued required ranges can still be cancelled. This is not accepted final scheduling behavior. The temporary 32-week presentation limit must not become an unexplained permanent loss of supported planner range behavior; bound work and subscriptions while preserving useful requested coverage.
- `PlannerCoverageViewModelTest` still polls with `Thread.sleep(2)` because computation uses `Dispatchers.Default`. Agent 06 must make dispatch/completion controllable and remove wall-clock polling before final acceptance. Current passes establish the tested outcomes, not deterministic race coverage.
- Recheck account/range identity after suspending projection calculations before publishing whole-state replacements. `applyCoverageSnapshot`, marker updates, and preview/month actions can suspend during recomputation. Add controlled late-completion tests alongside the shared API work so old computations cannot restore cleared or replaced state.
- No device evidence was added. Agent 03 remains unreviewed, and no wave 3 launch baseline has been issued.

## Agent 04 correction acceptance, 2026-09-25

- Reviewed final submission: `402537cf9ae289636569fe75da2d046d3bf50326`. Both supplied Home assertions are unchanged; added coroutine cleanup and formatting do not weaken them.
- Accepted for staged integration. Cherry-picked the six assignment commits onto `joshi/lean-integration`, ending at `e33a4db2e29907123c0b96b47f4865087d39840d` before this documentation update.
- Verified on the integration branch: `:app:testDebugUnitTest` filtered to `com.ash.axis.ui.academics.*`, `com.ash.axis.ui.attendance.*`, `com.ash.axis.ui.dashboard.*`, and `com.ash.axis.ui.daywise.*`, followed by `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`. All passed: 10 tests, no failures or skips. `git diff --check` passed.
- The original metadata-error escape and stale cleared-demand-error regressions are resolved. Overall and Day-wise tests cover metadata retry and current error presentation.
- This is consumer correction acceptance, not completed lifecycle acceptance. Agent 06 must still replace temporary selection discovery with shared selection state and validate account-bound error lifetime. In particular, local manual-refresh errors are not consistently cleared on every key transition, and coordinator demand errors are not keyed. An old pending refresh must not publish an error into a new account, semester, or range. Cover these cases while closing the shared selection/error contract; do not retain duplicate error state solely for compatibility.
- At this acceptance checkpoint, agent 05 corrections and agent 03 still needed review. See the later agent 05 update above for current status. Original wave 2 baseline remains unchanged. Device and performance checks remain open.

## Reproduction

Command in the review checkout:

`gradlew.bat :app:testDebugUnitTest --tests com.ash.axis.ui.dashboard.DashboardReviewTest --tests com.ash.axis.ui.timetable.TimetableViewModelTest --tests com.ash.axis.ui.planner.PlannerSelectionReviewTest --console=plain`

Result: compilation passed; 7 tests ran, 4 failed. The three existing timetable tests passed. All four added regression tests failed. The tested production code is the two submitted branches combined without source edits.

## Agent 04 corrections

Continue in `C:/Coding/Axis/axis-lean-04-home-attendance` on `joshi/lean-04-home-attendance`. Submitted HEAD is `9603ce2f9df7999a28d54cedb387df917f9a5406`.

Cherry-pick only the regression-test commit `f18a954da7b1680065db1a18c52feb82c7be3016`.

1. **Handle semester discovery failure in the consumer.** `SemesterSelection.kt:44` rethrows a non-cancellation error for blank saved selection. Home, Overall, and Day-wise collect it in uncaught `viewModelScope.launch` jobs. The Home test reproduces the escaping `semester offline` exception and missing error state. Display the error, preserve unrelated useful content, and allow recovery on retry or selection change. Cancellation must still propagate. Do not turn missing metadata into an empty success or leave the collector permanently dead.
2. **Derive errors from their current sources.** `DashboardViewModel.kt:146` retains the previous demand error when the coordinator clears it. The regression test reproduces the stale message with unchanged snapshot data. Combine snapshot errors and demand errors so clearing one does not retain it or erase a different current error. Apply the same principle to Overall and Day-wise. Test recovery without a new successful snapshot emission and preference changes while a demand error remains active.
3. Keep the saved-selection fast path. Do not add another semester metadata lookup to fix these cases. Account and selected-key transitions must discard prior presentation and errors coherently.

Add coverage for the affected Overall and Day-wise behavior. Rerun existing assignment tests plus `DashboardReviewTest`, compilation, ktlint, detekt, and `git diff --check`. Update `04-home-attendance.md`, make focused commits, and return the final SHA. No shared foundation edits.

## Agent 05 corrections

Continue in `C:/Coding/Axis/axis-lean-05-timetable-planner` on `joshi/lean-05-timetable-planner`. Submitted HEAD is `feb453638aeac6ec285c49e2dd11b106b7f0e1e9`.

Cherry-pick only the regression-test commit `e37c103ca5483b3c5b315ff5825357233a63f8a4`.

1. **Reflect destructive snapshot resets.** `TimetableViewModel.kt:212` retains derived days and loaded-week membership when the observed snapshot becomes empty. The added test reproduces this. A failed refresh with non-null saved data must retain content; a reset to no data must remove that week's derived content. Audit other retained weeks and planner presentation for the same problem. Test clear and reload, not only account replacement.
2. **Bind saved planner data before semester metadata discovery.** `PlannerViewModel.kt:355` awaits `getPreferredSemester` before observing either core snapshot, even with saved year/class IDs. A blocked lookup prevents binding shared saved attendance in the added test. Bind the valid saved key immediately. Do not force semester metadata discovery on every planner refresh. Use an explicit loading/error path only when identity is genuinely missing. The shared selection API is assigned to agent 06 below.
3. **Keep used planner coverage observable.** Source inspection: `requestCoverage` at lines 486-508 takes only `flow.first`, then marks the week covered. An old saved value can end observation before its refresh completes. Later refreshes, invalidations, and cache clearing never update that week in planner state. Keep observation of the currently required ranges and replace/remove derived dates when their snapshots change. Add controlled PlannerViewModel tests for saved data followed by refreshed dates, a cleared covered week, and recovery after a failed range. Do not add a second raw-data cache or an unbounded collection of subscriptions.
4. **Do not present partial coverage as a completed projection.** `recomputeProjection` schedules missing coverage and immediately computes a horizon result using the currently available dates. Distinguish provisional/loading/failed coverage from a complete horizon. Preserve weekly estimates as estimates; `dated == null` from a legacy weekly cache must not become confirmed actual-date coverage. Add tests for a missing intermediate week, a failed range, and legacy weekly fallback. Do not modify domain calculations to hide missing inputs.

Keep the missing priority/prefetch API explicit. Do not create a local scheduler or edit repositories to solve it. Agent 06 owns that shared gap. Rerun assignment tests and both supplied regression classes, compilation, ktlint, detekt, and `git diff --check`. Update `05-timetable-planner.md`, make focused commits, and return the final SHA.

## Shared API decision for agent 06

The reported gaps are real. They are not accepted permanent limitations. Agent 06 runs alone after corrected consumer submissions and agent 03 are reviewed and integrated. Its ownership is extended narrowly in `06-integration.md`:

- Publish one coherent account-bound selected semester and its loading/error state. Use it in 04 and 05 consumers and remove their duplicated selection discovery. Publish identity and selection atomically, or provide equivalent guards against mixed-account inputs. Saved keys must be usable before optional label/network discovery.
- Provide concrete coordinator operations for visible timetable demand, required planner coverage, and lower-priority adjacent prefetch. Required coverage cannot be silently dropped as obsolete speculative work. Visible demand can promote queued work. Bound startup and nested fanout and preserve request reuse. Do not build a general scheduler framework.
- Separate Home/current-week demand from timetable viewport selection. Home currently observes the real current week while `homeVisible()` warms the coordinator's last selected week. Test visiting a future timetable week and returning Home, including manual refresh.
- Update the published data contract and migrate all affected callers in the same integration assignment. Prove cache-clear propagation through derived consumer state, account/selection transitions, and simultaneous planner/timetable demand with controlled tests.

Agents 04 and 05 can fix their owned defects in parallel now. Do not launch 06 until the master supplies its integrated baseline and isolated checkout. No merge, push, publication, live student traffic, or device acceptance occurred during this review.
