package com.ash.core.ui.navigation

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun CoreNavHost(
    navController: NavHostController,
    startDestination: String,
    routes: Map<String, @Composable () -> Unit>,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        routes.forEach { (route, screen) ->
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
