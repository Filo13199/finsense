package com.finsense.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.finsense.data.preferences.AppCurrency
import com.finsense.ui.budget.BudgetScreen
import com.finsense.ui.categories.CategoriesScreen
import com.finsense.ui.dashboard.DashboardScreen
import com.finsense.ui.insights.InsightTransactionsScreen
import com.finsense.ui.insights.InsightsScreen
import com.finsense.ui.permission.PermissionScreen
import com.finsense.ui.settings.SettingsScreen
import com.finsense.ui.transactions.TransactionsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard    : Screen("dashboard",    "Dashboard",    Icons.Default.Home)
    object Transactions : Screen("transactions", "Transactions", Icons.AutoMirrored.Filled.List)
    object Budgets      : Screen("budgets",      "Budgets",      Icons.Default.AccountBalance)
    object Insights     : Screen("insights",     "Insights",     Icons.Default.BarChart)
    object Settings     : Screen("settings",     "Settings",     Icons.Default.Settings)
    object Categories   : Screen("categories",   "Categories",   Icons.Default.Category)
    object Permission   : Screen("permission",   "Permission",   Icons.Default.Lock)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.Budgets,
    Screen.Insights,
    Screen.Settings
)

@Composable
fun FinsenseNavGraph(onRequestSmsPermission: () -> Unit) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            val showBottomBar = currentRoute != Screen.Permission.route &&
                currentRoute?.startsWith("insight_transactions") != true
            if (showBottomBar) {
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
            composable(Screen.Insights.route) {
                InsightsScreen(
                    contentPadding = padding,
                    onNavigateToSlice = { startMs, endMs, filterType, filterValue, label, currency ->
                        navController.navigate(
                            "insight_transactions?startMs=$startMs&endMs=$endMs" +
                                "&filterType=$filterType&filterValue=${Uri.encode(filterValue)}" +
                                "&label=${Uri.encode(label)}&currency=${currency.name}"
                        )
                    }
                )
            }
            composable(
                route = "insight_transactions?startMs={startMs}&endMs={endMs}" +
                    "&filterType={filterType}&filterValue={filterValue}&label={label}&currency={currency}",
                arguments = listOf(
                    navArgument("startMs") { type = NavType.LongType },
                    navArgument("endMs") { type = NavType.LongType },
                    navArgument("filterType") { type = NavType.StringType },
                    navArgument("filterValue") { type = NavType.StringType },
                    navArgument("label") { type = NavType.StringType },
                    navArgument("currency") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val label = Uri.decode(backStackEntry.arguments?.getString("label") ?: "")
                InsightTransactionsScreen(
                    label = label,
                    onBack = { navController.popBackStack() },
                    contentPadding = padding
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    contentPadding = padding,
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) }
                )
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(contentPadding = padding)
            }
        }
    }
}
