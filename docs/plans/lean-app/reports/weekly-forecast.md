# Weekly forecast

Checkout: `C:/Coding/Axis/axis-lean-feature-cuts`, branch `joshi/lean-feature-cuts`. Changes are local and uncommitted.

Planner now forecasts from the selected attendance snapshot and the current weekly timetable. It repeats that timetable through an inclusive end date, defaults the end date to the saved semester end, and subtracts planned absence ranges. Saved exam and holiday blocks remain in local storage and exclude teaching days. Overlapping absence ranges count each class once.

The screen contains an end-date picker, planned absences, compact subject results, and an Academic calendar sheet. Below-target subjects appear first. Source update times and estimate wording are visible. Unmatched subjects retain their attendance totals with a no-matching-classes message. Empty weekly schedules show a recoverable error.

Today has Attend all, Miss all, and Already counted choices. The answer applies only to the same date and attendance snapshot. A refreshed attendance snapshot requires a new answer. Forecast inputs remain local to the open planner; saved academic blocks retain their existing persistence.

Removed the permanent simulator grid, long-press previews, temporary holiday mode, maximum-reachable comparison, multi-week coverage state, required-week queue/API, and obsolete tests. Timetable adjacent-week prefetch remains. No future week is requested when the forecast horizon or absences change. Latest-input collection cancels superseded calculations; context changes clear student inputs.

Physical Kotlin production lines, including blank lines and comments: Planner 2,093 -> 1,084 (-1,009; 48.2%). Current repository production is 17,342 lines and tests are 4,924 lines. Repository totals include Android main/debug/release Kotlin and backend src TypeScript; tests include Android src/test Kotlin and backend test TypeScript.

Tests cover repeated weeks, inclusive end dates, Sundays, overlapping absences, no-class exclusions, all today choices, code/name/lecture-type matching, unmatched subjects, target ordering, no future-week demand, marker updates, refreshed attendance, midnight, missing-data recovery, Clear during calculation, and logout during calculation. The existing selected-semester binding test remains.

Validation: debug assembly, debug/release unit tests, ktlint, detekt, release Kotlin compilation, and git diff --check passed. All 191 distinct Android tests passed in both variants. Installed the signed debug build on the connected Samsung SM-A156E with an in-place update. App data was preserved. Device UI review is left to the user; no live attendance was submitted.
