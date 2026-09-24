# Agent 03: grades simplification

Run in wave 2 beside agents 04 and 05, after the master integrates agents 01 and 02. Read README.md, CONTRACT.md, and both implemented API contract reports.

## Objective

Remove repeated loading, filter reset, request-building, caching, and presentation code from grades. Keep grades and PDFs on demand. Do not add grades to core startup warmup.

## Write ownership

- `app/.../ui/grades/*`.
- `app/.../data/repository/GradesRepository.kt`, `GradesParser.kt`.
- Grade-specific new helpers beside those files and corresponding tests.
- Grade screenshots under `docs/screenshots/lean-app/03/`.

Read shared API, auth, cache, and model files as needed. Do not modify shared data contracts, AttendanceRepository, wire models in `domain/model/`, UI foundation, DI, or app lifecycle. Report necessary contract changes to the master.

## Work

1. Characterize dependent performance filters, tab loading, result selection, cached fallback, and PDF failure behavior before changing them. Test slow older responses as selections change.
2. Simplify the grade UI state so resets have one clear owner. Separate distinct tab state if it reduces invalid combinations. Splitting files alone is not a reduction.
3. Consolidate repeated load/error handling locally, with explicit result-specific state updates. Preserve cancellation and prevent old filter responses overwriting newer selections. Avoid a generic base ViewModel.
4. Use agent 01's JSON cache helper to replace repeated typed cache read/write functions while preserving grade-specific freshness and fallback rules.
5. Factor shared report-card form fields without merging endpoints with different field requirements. Keep known response aliases and parsing fallbacks.
6. Apply agent 02's UI components to repeated grade cards, rows, filters, and status presentation. Preserve controls, tab state, PDF sharing, result semantics, and accessibility.
7. Delete replaced helpers once callers are migrated. Do not remove features or squeeze code into one-liners.

## Checks and deliverables

Run targeted grade repository/parser/ViewModel tests, then `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`.

Check performance filters, empty/error results, admit cards, PDF viewing and sharing, and large-text layouts on an available device. Record unavailable live data or device checks honestly. Do not request live student data just for fixtures.

Write `reports/03-grades.md` with APIs changed, preserved edge cases, checks, screenshots, source/test deltas, and integration notes. Stop after focused local commits.
