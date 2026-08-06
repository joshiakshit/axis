package com.ash.core.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun CoreNavHost(
    navController: NavHostController,
    startDestination: String,
    routes: Map<String, @Composable () -> Unit>,
    slideRoutes: Set<String> = emptySet(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        routes.forEach { (route, screen) ->
            if (route in slideRoutes) {
                composable(
                    route,
                    enterTransition = {
                        slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it }
                    },
                    exitTransition = { fadeOut(tween(100)) },
                    popEnterTransition = { fadeIn(spring(stiffness = 800f)) },
                    popExitTransition = {
                        slideOutHorizontally(spring(stiffness = 350f)) { it } +
                            scaleOut(targetScale = 0.9f)
                    },
                ) { screen() }
            } else {
                composable(
                    route,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(100)) },
                    popEnterTransition = { fadeIn(spring(stiffness = 800f)) },
                    popExitTransition = { fadeOut(spring(stiffness = 800f)) },
                ) { screen() }
            }
        }
    }
}
