package com.tomjxyz.shipinfo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tomjxyz.shipinfo.ui.screens.LiveScreen
import com.tomjxyz.shipinfo.ui.screens.PinsScreen
import com.tomjxyz.shipinfo.ui.screens.RecordingsScreen
import com.tomjxyz.shipinfo.ui.screens.RollWatchScreen
import com.tomjxyz.shipinfo.ui.screens.SessionDetailScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("live", "Live", Icons.Filled.Speed),
    Tab("pins", "Positions", Icons.Filled.Place),
    Tab("roll", "Roll watch", Icons.Filled.Waves),
    Tab("recordings", "Recordings", Icons.Filled.FolderOpen),
)

@Composable
fun ShipInfoNav(requestedTab: String?, onTabHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null && tabs.any { it.route == requestedTab }) goTab(requestedTab)
        if (requestedTab != null) onTabHandled()
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "live", modifier = Modifier.padding(padding)) {
            composable("live") { LiveScreen() }
            composable("pins") { PinsScreen() }
            composable("roll") { RollWatchScreen(onOpenSession = { nav.navigate("session/$it") }) }
            composable("recordings") { RecordingsScreen(onOpen = { nav.navigate("session/$it") }) }
            composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                SessionDetailScreen(
                    sessionId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
