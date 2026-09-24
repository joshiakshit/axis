# Agent 02: shared UI foundation

Run in wave 1 beside agent 01. Read README.md and CONTRACT.md. This task has no dependency on the new data APIs.

## Objective

Reduce repeated Compose presentation code through a small, consistent set of components. Preserve existing behavior and the app's visual identity. Provide stable APIs for wave 2.

## Start with

- `core/.../ui/theme/AppTheme.kt`, `AppDimens.kt`, `AppShapes.kt`.
- `core/.../ui/components/LoadingState.kt`, `EmptyState.kt`, `PullToRefresh.kt`, `StatusBadge.kt`.
- `app/.../ui/settings/SettingsCommon.kt`, `SettingsAppearance.kt`, `SettingsAttendance.kt`.
- Read representative dashboard, attendance, timetable, planner, and grades cards to identify repeated patterns. Do not edit those features.

## Write ownership

- Existing theme tokens and small new components in `core/src/main/kotlin/com/ash/core/ui/`.
- `app/.../ui/settings/SettingsCommon.kt`, `SettingsAppearance.kt`, `SettingsAttendance.kt`, `SettingsSupport.kt`, `SettingsUpdate.kt`, and `SettingsScreen.kt` for presentation only.
- Relevant core component tests if behavior changes. Screenshots of the edited screens in `docs/screenshots/lean-app/02/` when a device is available.

Do not edit SettingsViewModel, repositories, navigation, AppModule, MainApp, AccountManager, AxisCalendar, QR, or other feature directories. Preserve existing shared component signatures or add compatible overloads until consumers migrate.

## Work

1. Identify repeated card, section, text, row, and status patterns. Extract only patterns with at least two real uses. Use existing MaterialTheme and AppDimens before adding tokens.
2. Establish consistent screen spacing, card surfaces, label styles, and loading/error/empty presentation. Allow local layout differences where they carry meaning.
3. Apply the components to owned settings presentation. Avoid changing text, callbacks, preference values, control behavior, or navigation.
4. Keep APIs small. Prefer composable content slots to many flags. Do not invent a renderer driven by JSON or screen specifications.
5. Preserve dark/light/accent behavior, scroll behavior, large text, and touch targets. Do not replace custom controls solely to reduce lines.

## Checks and deliverables

Run `:app:compileDebugKotlin :core:ktlintCheck :core:detekt :app:ktlintCheck :app:detekt`. Run targeted tests only when useful for changed behavior.

If device tooling is available, capture settings before/after with matching theme, text size, and content. Check light and dark modes plus enlarged text. If not available, clearly defer visual acceptance to agent 06; compilation is not visual verification.

Write `reports/02-ui-foundation.md` and `reports/02-ui-contract.md`. List actual component signatures, intended uses, preserved compatibility APIs, and short examples. State any deliberate visible change. Include source-line delta and screenshot paths.

Stop after focused commits and the reports. The master freezes the shared UI APIs before wave 2.
