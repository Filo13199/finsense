package com.finsense.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.finsense.ui.budget.BudgetScreen
import com.finsense.ui.categories.CategoriesScreen
import com.finsense.ui.dashboard.DashboardScreen
import com.finsense.ui.permission.PermissionScreen
import com.finsense.ui.settings.SettingsScreen
import com.finsense.ui.transactions.TransactionsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard    : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Transactions : Screen("transactions", "Transactions", Icons.AutoMirrored.Filled.List)
    object Budgets      : Screen("budgets", "Budgets", Icons.Default.AccountBalance)
    object Categories   : Screen("categories", "Categories", Icons.Default.Category)
    object Settings     : Screen("settings", "Settings", Icons.Default.Settings)
    object Permission   : Screen("permission", "Permission", Icons.Default.Lock)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.Budgets,
    Screen.Categories,
    Screen.Settings
)

@Composable
fun FinsenseNavGraph(onRequestSmsPermission: () -> Unit) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Permission.route) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Permission.route
        ) {
            composable(Screen.Permission.route) {
                PermissionScreen(
                    onRequestPermission = onRequestSmsPermission,
                    onPermissionGranted = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Permission.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(contentPadding = padding)
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen(contentPadding = padding)
            }
            composable(Screen.Budgets.route) {
                BudgetScreen(contentPadding = padding)
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(contentPadding = padding)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(contentPadding = padding)
            }
        }
    }
}
