# Agent 07: independent acceptance review

Run alone in wave 4 after agent 06. Read README.md, CONTRACT.md, the original baseline identifier, and reports 01-06. Inspect the complete baseline-to-candidate diff. Do not implement changes or delegate.

## Objective

Find correctness defects and unsupported claims in the refactor. Determine which acceptance conditions are proved, failed, or still unverified. A passing test count alone is not acceptance.

## Review priorities

1. Account and semester isolation, token/context pairing, logout, cache clearing, late completions, and discarded generations.
2. Request reuse, forced refresh, QR invalidation during in-flight loads, cancellation, error metadata, timestamps, and empty results.
3. Cached first content, priority ordering, bounded prefetch, and independent attendance/timetable publication.
4. Actual-date versus weekly timetable handling; profile year versus attendance year; route selector fallback and refresh; preserved projection calculations.
5. Screen subscriptions, preference recomputation, navigation state, export week, and remaining duplicate fetch paths.
6. UI behavior, light/dark/accent consistency, enlarged text, touch targets, and screenshot evidence.
7. Code reduction that removes responsibility or duplication rather than hiding it behind generic machinery.

Read relevant tests and verify that race tests control completion order rather than depend on sleeps. Check that production and test line counts use the same baseline and exclude generated/vendor output.

Use targeted read-only checks where evidence is missing. Do not rerun the whole suite when recent integrated results and unchanged code already establish the result. Do not make live API calls or submit attendance.

## Deliverable

Write only `reports/07-review.md`. Put actionable findings first, ordered by severity. Include file/line, concrete failure scenario, affected behavior, and the smallest recommended correction. Distinguish verified defects from questions needing evidence.

Then give an acceptance matrix with pass/fail/unverified for each CONTRACT.md requirement group and a recommendation: accept refactor, return for fixes, or accept code with specified device/release gates still open.

Carry forward the separate backend authentication review and any unavailable device/performance validation. Do not silently convert them into completed production-readiness claims. Return findings to the master; original owners fix them before final acceptance.
