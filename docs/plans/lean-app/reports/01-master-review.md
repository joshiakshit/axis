# Wave 1 master review

Date: 2026-09-24. Wave 2 is blocked pending the data correction pass.

## Reviewed revisions

- Shared baseline: `e672a36102f3108e35b3ee431270d318e6ebaaee`.
- Data: `75a8e11032b5cb9b1f927549cc49d9b263efc995`, in `C:/Coding/Axis/axis-lean-01-data`.
- UI: `3b966d9` and `ff04223`. Accepted for foundation integration. Cherry-picked as `a848836` and `42d1c63` on `joshi/lean-integration` in `C:/Coding/Axis/axis`.
- UI device checks remain unverified. No data commits are integrated yet.

## Reproduced data failures

Five regression tests compile and fail against the submitted data revision. They are committed separately as `1a7199f175a8d5a0c12a824e879dea56200d2d12` on `joshi/lean-wave1-review`, in `C:/Coding/Axis/axis-lean-wave1-review`.

Command: `./gradlew :app:testDebugUnitTest --tests com.ash.axis.data.academic.WaveOneAcceptanceTest --console=plain`.

Result: 5 tests run, 5 assertion failures. Existing agent checks are separate evidence; passing them did not cover these cases.

| Case | Observed failure | Required correction |
| --- | --- | --- |
| Fresh cache later becomes stale | Refresh count stays zero | Recalculate freshness on later demand using the existing policy. Do not let an in-memory FRESH flag bypass revalidation forever. |
| Clear cache while a consumer retains its flow | Consumer remains empty after a successful reload | Keep active observers connected across cache clearing. Account deactivation must still clear account state safely. |
| Cache read completes after clear and entry recreation | Cleared data hydrates the replacement entry | Guard reads with entry identity or a monotonic invalidation epoch, as well as guarding writes. Entry-local generation zero can repeat. |
| Timetable profile discovery fails at startup | Attendance is never requested | Start the two core demands independently. A slow or failed profile/cache/route operation must not prevent the other dataset from loading. Expose relevant errors without uncaught background exceptions. Preserve cancellation. |
| Manual profile refresh completes after deactivate/reactivate for the same account | Old profile year overwrites the new activation | Check the activation/invalidation generation around forced profile discovery. Account equality alone is insufficient. Extend coverage to destructive cache clearing. |

The first three failures are in `AcademicResource.kt`. The last two are in `AcademicDataCoordinator.kt`. These files are under `app/src/main/kotlin/com/ash/axis/data/academic/`.

## Additional contract gaps from source review

1. Priority and bounds are not complete. `activate` handles DAYWISE, PLANNER, and OTHER through `warmCore` before their concrete visible demand is supplied. Resource jobs have no shared concurrency bound across keys. `invalidateAll` immediately reloads every retained day-wise range. Implement the concrete bounded policy in CONTRACT.md. Avoid a general scheduler. Add controlled request-count tests for visible-first activation, blocked discovery, rapid range changes, queued-demand promotion where applicable, and invalidation after several historical ranges were observed. Inactive historical ranges can remain invalidated until needed.
2. Check upgrade cache compatibility. New attendance reads only v4 keys, while old callers persist v2 keys. The added legacy fallback test covers the old method. It does not establish saved-data availability through the new observable API on upgrade. Add characterization coverage for that transition. Reuse legacy data only when ownership and context can be established safely. If old keys cannot prove identity, report that limitation explicitly instead of weakening account isolation or claiming complete migration.

## Correction assignment for agent 01

Continue in `C:/Coding/Axis/axis-lean-01-data`, branch `joshi/lean-01-data`, from the submitted data HEAD above. Do not reset the checkout or modify another worktree.

1. Read this review, the shared CONTRACT.md, and the original 01 assignment. Cherry-pick the regression-test commit `1a7199f175a8d5a0c12a824e879dea56200d2d12` into your branch.
2. Fix the five reproduced failures and the priority/bounds gap within existing ownership. Add useful regression coverage before changing behavior. Tests may use an injected test scope or clock if APIs change; retain their behavioral assertions. Do not replace deterministic coverage with sleeps.
3. Resolve or explicitly document the upgrade-cache finding with evidence. Keep cache identity checks strict. Report any necessary cross-scope change to the master.
4. Rerun the new acceptance tests and the existing academic resource, coordinator, JSON cache, attendance, timetable, and routing tests. Run `:app:compileDebugKotlin :app:ktlintCheck :app:detekt` and `git diff --check`.
5. Update `01-data-foundation.md` and `01-data-contract.md` to match actual semantics. Record exact checks, remaining limits, and production/test line deltas. Make focused local commits and return the final SHA.

Keep lifecycle and QR wiring with agent 06. This correction covers the APIs those integrations require. Do not migrate screens, merge, push, publish, or start wave 2.

## Master next step

Review the corrected data diff and rerun the acceptance tests. Then integrate data with the accepted UI work, run the integrated baseline gate, and supply the exact wave 2 SHA and three isolated checkouts. No wave 2 launch SHA has been issued yet.
