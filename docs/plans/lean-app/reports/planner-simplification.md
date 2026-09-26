# Planner simplification

Checkout: `C:/Coding/Axis/axis-lean-feature-cuts`, branch `joshi/lean-feature-cuts`.

Reused SubjectAttendance in the planner. Removed the unused PlannerSubject adapter, DaySafety calculator, weekly counts, spare-class totals, and duplicate semester-end state. Shared timetable merge and coverage reset paths. Simplified ratio text and calendar accessibility modifiers. Kept visible features and projection formulas.

Clear now invalidates a calculation already in progress. The new regression test failed before the fix and passes afterward. Slot matching tests now exercise actual projections. Removed six tests for deleted calculations; added one reset regression test.

Physical production lines, including blank lines and comments:

| Area | Before | After | Change |
| --- | ---: | ---: | ---: |
| Planner UI and state | 1,974 | 1,896 | -78 |
| Planner calculations | 347 | 230 | -117 |
| Planner total | 2,321 | 2,126 | -195 |
| Repository production | 18,984 | 18,789 | -195 |
| Repository tests | 5,478 | 5,432 | -46 |

Repository counts include Android main/debug/release Kotlin and backend src TypeScript. Test counts include Android src/test Kotlin and backend test TypeScript. Build output and dependencies are excluded.

Validation: debug assembly, debug/release unit tests, ktlint, detekt, release Kotlin compilation, and git diff --check passed. All 204 distinct Android tests passed in both variants (174 app, 30 core). Backend source is unchanged. No device checks or performance measurements were performed. Changes remain local and uncommitted.

After the initial simplification, Planner still read shared attendance and timetable snapshots, loaded timetable weeks required by the visible month and forecast, fetched the university calendar feed, and stored personal exam/holiday markers. The semester maximum repeats the weekly schedule and assumes all future classes are attended. It is an estimate. Date-range forecasts retain loading, failure, and estimated-coverage handling.

## University feed removal

Removed the university events/holidays feed from Planner and Timetable, including automatic loads, refresh actions, API endpoints, repository, feed cache helper, models, presentation, and obsolete feed tests. Saved student exam and holiday blocks remain in local storage. They still exclude their date ranges from attendance projections. No student-marker data or schema was changed.

This cut removes another 384 production lines and 143 test lines. Repository production is now 18,405 lines; tests are 5,289 lines. Planner UI and state are 1,863 lines, with another 230 lines for calculations (2,093 total). README now describes the personal academic calendar.

Validation after feed removal: debug assembly, debug/release unit tests, ktlint, detekt, release Kotlin compilation, and git diff --check passed. All 200 distinct Android tests passed in both variants. Existing marker range, overlap, and no-class projection tests remain. No device checks were performed.
