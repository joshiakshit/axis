# Assignment 03: grades

## Baseline and commits

- Started at `acb90da4df7f2148c0d53420da2c67a5d322aa68` on `joshi/lean-03-grades`.
- Code commits: `5b1f8a6` (cache and form fields), `f2bc617` (state and request guards), `b2d5d63` (UI surfaces).
- Review follow-up: cherry-picked regression tests in `d488e06`, then fixed the two request-state gaps in `01c2550`.
- The expected untracked `AGENTS.md` and ignored `local.properties` were not committed.

## Changes

- `GradesRepository` uses the published `JsonCache` for semester, session, result, performance, and admit-card entries. Grade result and admit-card network failures still read saved data at any age. Cancellation now propagates instead of becoming cached fallback.
- The three report-card controller calls share their common form fields. The PDF endpoint keeps its distinct fields and remains on demand.
- `GradesUiState` owns dependent filter and report-card resets. The ViewModel cancels older jobs and checks request generations before publishing each tab's response. PDF parameters are captured at request start, and an obsolete PDF response cannot replace the selected file.
- Changing exam chips now invalidates a running marks request and clears its busy state. Starting a new result load resets obsolete PDF loading. A late old PDF response cannot clear a newer PDF request's busy state.
- Admit cards, performance filters, course cards, section labels, and status cards use the published UI components. Filters, result selection, response aliases, PDF viewing and sharing, and tab navigation remain available.
- No public API changed. The state transition helpers are internal to grades. No shared foundation or lifecycle file changed.

## Checks

- `:app:testDebugUnitTest --tests "*Grades*Test"`: passed, 11 tests. Includes the supplied exam-chip and PDF cancellation assertions, a late marks failure, and an old PDF completion while a new PDF request is active.
- `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`: passed in a separate scoped Gradle run.
- `git diff --check`: passed.
- `adb devices`: no attached device. Performance filters, empty/error views, admit cards, PDF viewing and sharing, and large-text layouts were not checked on a device. No screenshots were captured. Live student data was not used.

## Counts and integration

- Changed production Kotlin: 2,252 to 2,238 physical lines, delta **-14**.
- Added grade tests: 0 to 424 physical lines, delta **+424**.
- No missing foundation API was found. The master can integrate the three code commits and this report. Device checks remain an integration gate.
