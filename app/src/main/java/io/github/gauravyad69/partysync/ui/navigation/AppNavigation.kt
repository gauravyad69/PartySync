package io.github.gauravyad69.partysync.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.gauravyad69.partysync.ui.screens.HomeScreen
import io.github.gauravyad69.partysync.ui.screens.HostScreen
import io.github.gauravyad69.partysync.ui.screens.JoinScreen

@Composable
fun AppNavigation(
    modifier: Modifier,
) {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onHostParty = { navController.navigate("host") },
                    onJoinParty = { navController.navigate("join") }
                )
            }

            composable("host") {
                HostScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("join") {
                JoinScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
