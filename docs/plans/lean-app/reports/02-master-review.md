# Wave 2 review: agents 04 and 05

Reviewed submissions: `9603ce2f9df7999a28d54cedb387df917f9a5406` (04) and `feb453638aeac6ec285c49e2dd11b106b7f0e1e9` (05).

Decision: return both for focused corrections. They are combined only in `C:/Coding/Axis/axis-lean-wave2-review`, branch `joshi/lean-wave2-review`. Neither submission is accepted on `joshi/lean-integration` yet. This review does not assess assignment 03.

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
