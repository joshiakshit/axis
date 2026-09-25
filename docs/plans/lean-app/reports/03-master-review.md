# Agent 03 master review

Reviewed on 2026-09-25. Submission: `de411944e539d707d5581ce2788ba540e0d746e5` on `joshi/lean-03-grades`.

Decision: return for two focused request-state corrections. Grades is not integrated yet. Agents 04 and 05 remain integrated. Do not issue the agent 06 launch baseline until these corrections are reviewed and the integrated baseline gate passes.

## Evidence

Review checkout: `C:/Coding/Axis/axis-lean-03-review`, branch `joshi/lean-03-review`, created from the submitted SHA without production changes.

Regression tests: `app/src/test/kotlin/com/ash/axis/ui/grades/GradesReviewTest.kt`, committed as `ebf9342833dc7bb6a4ff7e0234b16852143f55ef`.

Command: `gradlew.bat :app:testDebugUnitTest --tests com.ash.axis.ui.grades.GradesReviewTest --console=plain`.

Result: compilation passed; 2 tests ran and both failed. Deferred responses control completion; no sleeps or live traffic are used.

## Corrections

1. **Reject marks for a changed exam selection.** `GradesViewModel.kt:122`, `togglePerformanceExam`, changes the selected exams and clears courses but does not cancel or invalidate the running marks request. Exam chips remain enabled during loading. A response for the previous selection later fills courses under the new chips without another press of Load. Invalidate the obsolete request on selection change and clear its busy state. Reject both late success and late failure. Preserve the explicit Load action and generation guards for newer requests.
2. **Reset cancelled PDF loading state.** `GradesViewModel.kt:57`, `launchResult`, cancels the PDF job and advances its generation. The cancellation path propagates without clearing `isLoadingPdf`, and `GradesState.kt:114`, `withoutReportCard`, leaves that flag set. Selecting another result during a PDF request leaves the new result's PDF button disabled and loading indefinitely. Clear obsolete loading state as part of invalidation. A late old PDF completion must not reset a newer PDF job's busy state or publish an old file.

## Agent 03 follow-up

Continue in `C:/Coding/Axis/axis-lean-03-grades` on `joshi/lean-03-grades`, from submitted HEAD `de411944e539d707d5581ce2788ba540e0d746e5`.

1. Cherry-pick the regression-test commit `ebf9342833dc7bb6a4ff7e0234b16852143f55ef`.
2. Fix both cases within the original grades ownership. Keep the supplied assertions and add controlled coverage for late failures and a superseded PDF completion while a new PDF request is active. Do not weaken cancellation or add a generic request framework.
3. Run `:app:testDebugUnitTest --tests "*Grades*Test"`, then `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`, and `git diff --check`.
4. Update `reports/03-grades.md` with results and revised source/test deltas. Make focused local commits and return the final SHA.

No shared data, lifecycle, backend, or UI foundation changes are needed for these corrections. Device/PDF viewing and sharing checks remain unverified until a device is available. Do not merge, push, publish, or start another assignment.
