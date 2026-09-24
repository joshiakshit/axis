# UI foundation contract

Package: `com.ash.core.ui.components`. All three APIs are public composables in `AppSurfaces.kt`.

```kotlin
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = cardColor(),
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
)

@Composable
fun AppSectionLabel(text: String, modifier: Modifier = Modifier)

@Composable
fun SubtleDivider(modifier: Modifier = Modifier)
```

`AppCard` provides a full-width `Surface` with `AppShapes.medium`. It adds no content padding. Use it for the repeated simple card surface. Pass a color or border only when the screen already needs one.

```kotlin
AppCard { Text("Summary", modifier = Modifier.padding(AppDimens.cardPadding)) }
AppSectionLabel("ATTENDANCE")
SubtleDivider(modifier = Modifier.padding(vertical = AppDimens.itemSpacing))
```

`AppSectionLabel` uses 11 sp bold text, 0.6 sp letter spacing, and `onSurfaceVariant`. It adds no spacing. `SubtleDivider` uses `onSurfaceVariant` at 12% alpha. Callers own placement and spacing.

Existing `LoadingStateContainer`, `EmptyState`, `PullToRefreshContainer`, and `StatusBadge` signatures remain unchanged. Settings-local `SettingsCard(content)` and `SectionLabel(text)` remain available to existing settings files. No consumer migration is required.
