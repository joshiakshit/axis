# Agent 05: timetable and planner consumers

Run in wave 2 beside agents 03 and 04. Start only from the integrated wave-1 baseline. Read README.md, CONTRACT.md, both API contract reports, and the timetable identity constraints in docs/PROJECT_MEMORY.md.

## Objective

Make timetable and planner reuse the shared timetable/attendance data. Remove duplicated loading and raw caches while preserving date-specific schedules and projection rules. Apply shared UI components.

## Write ownership

- `app/.../ui/timetable/*`, `ui/planner/*`.
- Corresponding ViewModel/presentation tests and screenshots under `docs/screenshots/lean-app/05/`.

Do not edit repositories, coordinator implementation, shared UI, domain use cases, marker storage, calendar repositories, AcademicsScreen, exports, or app lifecycle. Report a missing API through the master.

## Work

1. Characterize visible-week loading, jump/swipe behavior, duplicate week requests, planner coverage, date-specific changes, markers, and today-attendance handling.
2. Replace local raw timetable/attendance stores and fetch loops with observable repository snapshots. Keep selected date, pager anchor, selections, and derived progress local to each screen.
3. Prioritize the visible week. Prefetch adjacent weeks through the coordinator after visible needs. A minute/progress ticker must only update presentation; it must not reload data.
4. Let planner show its initial useful data before extended future coverage completes. Avoid starting four weeks of speculative work before first content. Range loads remain date-aware and reuse matching transport work.
5. Preserve existing projections, explicit holiday/exam marker rules, manual today-attendance choices, and distinction between actual future dates and weekly fallback. Do not treat incomplete coverage as confirmed no classes.
6. Preserve calendar display and range changes as separate feed behavior. University calendar entries remain informational. Do not silently apply them to projection exclusions.
7. Preserve `ExportKeys.TIMETABLE_VIEW_DATE` updates so exports still match the visible week.
8. Remove duplicate refresh-event plumbing for migrated data. Apply shared cards, spacing, typography, and status components without redesigning calendars or navigation.

## Checks and deliverables

Use controlled coroutine tests for rapid date changes, promotion of a queued week, failed prefetch, and stale response completion. Verify current content remains usable when another range fails. Rerun PlannerUseCaseTest and TimetableUseCaseTest to protect the calculations even though their source is outside this assignment.

Run scoped tests, then `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`. Inspect date jumps, week boundaries, markers, projections, and enlarged text on an available device. Record unavailable checks.

Write `reports/05-timetable-planner.md` with deleted caches/load paths, remaining compatibility dependencies, viewport/priority API use, checks, source/test deltas, and screenshots. Stop after focused local commits.
