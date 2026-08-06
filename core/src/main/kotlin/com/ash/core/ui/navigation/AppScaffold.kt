@file:Suppress("MatchingDeclarationName")

package com.ash.core.ui.navigation

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
@Suppress("LongParameterList")
fun AppScaffold(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showBottomBar: Boolean = true,
    compactNavBar: Boolean = false,
    fabIcon: ImageVector? = null,
    onFabClick: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = topBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(innerPadding)

            if (showBottomBar) {
                FloatingBottomBar(
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    compact = compactNavBar,
                    fabIcon = fabIcon,
                    onFabClick = onFabClick,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
private fun FloatingBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    compact: Boolean,
    fabIcon: ImageVector?,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val tint =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            surfaceColor.copy(alpha = 0.92f)
        } else {
            surfaceColor.copy(alpha = 0.97f)
        }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    val bgColor = MaterialTheme.colorScheme.background
    val midIndex = items.size / 2
    val hasFab = fabIcon != null

    val density = LocalDensity.current
    val cradleShape =
        remember(density) {
            val cornerPx = with(density) { 28.dp.toPx() }
            val fabRadiusPx = with(density) { 28.dp.toPx() }
            val cradleGapPx = with(density) { 8.dp.toPx() }
            GenericShape { size, _ ->
                val cr = cornerPx
                val totalR = fabRadiusPx + cradleGapPx
                val depth = totalR * 0.78f
                val center = size.width / 2f
                val cradleW = totalR * 1.4f

                moveTo(0f, cr)
                arcTo(Rect(0f, 0f, cr * 2, cr * 2), 180f, 90f, false)
                lineTo(center - cradleW, 0f)

                cubicTo(
                    center - cradleW * 0.45f,
                    0f,
                    center - totalR * 0.55f,
                    depth,
                    center,
                    depth,
                )
                cubicTo(
                    center + totalR * 0.55f,
                    depth,
                    center + cradleW * 0.45f,
                    0f,
                    center + cradleW,
                    0f,
                )

                lineTo(size.width - cr, 0f)
                arcTo(Rect(size.width - cr * 2, 0f, size.width, cr * 2), 270f, 90f, false)
                lineTo(size.width, size.height - cr)
                arcTo(
                    Rect(size.width - cr * 2, size.height - cr * 2, size.width, size.height),
                    0f,
                    90f,
                    false,
                )
                lineTo(cr, size.height)
                arcTo(Rect(0f, size.height - cr * 2, cr * 2, size.height), 90f, 90f, false)
                close()
            }
        }

    val barShape = if (hasFab) cradleShape else RoundedCornerShape(28.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                bgColor.copy(alpha = 0.7f),
                                bgColor.copy(alpha = 0.95f),
                            ),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = barShape,
            color = tint,
            border = BorderStroke(0.5.dp, borderColor),
            shadowElevation = 12.dp,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .height(if (compact) 54.dp else 58.dp)
                        .padding(horizontal = if (compact) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.spacedBy(6.dp) else Arrangement.Center,
            ) {
                items.take(midIndex).forEach { item ->
                    if (compact) {
                        CompactNavBarItem(
                            item = item,
                            selected = item.route == currentRoute,
                            onClick = { onNavigate(item.route) },
                        )
                    } else {
                        NavBarItem(
                            item = item,
                            selected = item.route == currentRoute,
                            onClick = { onNavigate(item.route) },
                            modifier = Modifier.width(68.dp),
                        )
                    }
                }

                if (hasFab) {
                    Spacer(Modifier.width(if (compact) 46.dp else 52.dp))
                } else {
                    Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                }

                items.drop(midIndex).forEach { item ->
                    if (compact) {
                        CompactNavBarItem(
                            item = item,
                            selected = item.route == currentRoute,
                            onClick = { onNavigate(item.route) },
                        )
                    } else {
                        NavBarItem(
                            item = item,
                            selected = item.route == currentRoute,
                            onClick = { onNavigate(item.route) },
                            modifier = Modifier.width(68.dp),
                        )
                    }
                }
            }
        }

        if (hasFab) {
            val haptic = LocalHapticFeedback.current
            val fabOffsetY = with(density) { (-18).dp.roundToPx() }
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFabClick()
                },
                modifier =
                    Modifier
                        .size(52.dp)
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, fabOffsetY) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation =
                    FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp,
                    ),
            ) {
                Icon(
                    imageVector = fabIcon!!,
                    contentDescription = "Quick action",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "nav_scale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.5f,
        label = "nav_alpha",
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier =
                    Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = iconAlpha
                        },
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.label,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
            )
        }
    }
}

@Composable
private fun CompactNavBarItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "nav_scale",
    )

    val iconTint =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        }

    Surface(
        onClick = onClick,
        modifier = modifier.size(42.dp),
        color = Color.Transparent,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier =
                    Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                tint = iconTint,
            )
        }
    }
}
