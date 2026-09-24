# Agent 04: home and attendance consumers

Run in wave 2 beside agents 03 and 05. Start only from the integrated wave-1 baseline. Read README.md, CONTRACT.md, and both implemented API contract reports.

## Objective

Make Home, Overall Attendance, and Day-wise Attendance consume shared data. Remove screen-owned copies of common context, fetching, cache refresh, and preference coordination. Apply the shared presentation components.

## Write ownership

- `app/.../ui/dashboard/*`, `ui/attendance/*`, `ui/daywise/*`, `ui/academics/*`.
- Corresponding ViewModel/presentation tests and screenshots in `docs/screenshots/lean-app/04/`.
- New small presentation mappers inside these feature directories if genuinely shared by owned callers.

Do not edit data repositories, coordinator implementation, domain calculation functions, shared UI components, settings, planner, timetable, MainApp, or DataRefreshSignal. Report a missing shared API to the master.

## Work

1. Add tests for cached first content, partial failures on Home, manual refresh, account/semester changes, and presentation preferences.
2. Replace independent account/semester resolution and repository loading with the implemented academic contract. Derive subject totals, tones, and forecasts from shared data without maintaining another raw-data store.
3. Let Home display useful attendance when timetable fails or waits, and useful timetable when attendance fails or waits. Do not put both behind one full-screen loading gate.
4. Remove `ON_RESUME` fetch/cache reload loops and duplicate refresh-event subscriptions for migrated data. Screen visibility should only express demand or priority when necessary.
5. Keep Day-wise Attendance as range-based server data. Month changes declare a new demand; fresh or in-flight matching ranges are reused. Summary data cannot replace daily records.
6. Wire the Academics pager's settled tab to demand/priority using agent 01's API. Do not fetch on every intermediate animated page. Coordinate with agent 05 through the published API, not direct edits to planner code.
7. Recompute threshold/combined-attendance changes from existing data. Preserve intentionally different screen calculations unless explicitly covered by tests.
8. Apply shared layout, cards, labels, and status presentation. Retain screen-specific content, scroll state, pull-to-refresh, and empty/offline behavior.

## Checks and deliverables

Tests must show that navigation/visibility does not create another request for available fresh data, refresh updates both interested screens, failures preserve usable content, and changed account/semester state never flashes the old dataset.

Run targeted tests, then `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`. Capture affected screens with an available device. Include refresh/empty/error and large-text checks where practical.

Write `reports/04-home-attendance.md`. List old subscriptions removed, retained compatibility dependencies, priority calls that agent 06 must wire at the app level, validation, source/test deltas, and screenshots. Stop after focused local commits.
