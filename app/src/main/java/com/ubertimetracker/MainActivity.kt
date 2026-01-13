package com.ubertimetracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ubertimetracker.data.local.AppDatabase
import com.ubertimetracker.ui.navigation.Screen
import com.ubertimetracker.ui.navigation.bottomNavItems
import com.ubertimetracker.ui.screens.HomeScreen
import com.ubertimetracker.ui.screens.SettingsScreen
import com.ubertimetracker.ui.screens.TimesheetScreen
import com.ubertimetracker.ui.theme.PurplePrimary
import com.ubertimetracker.ui.theme.UberTimeTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.ubertimetracker.util.LocaleHelper.onAttach(newBase))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handle permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            val database = remember { AppDatabase.getDatabase(this) }
            val scope = rememberCoroutineScope()
            
            // Collect settings from database
            val settingsState by database.settingsDao().getSettings()
                .collectAsStateWithLifecycle(initialValue = null)

            val darkModeSetting = settingsState?.isDarkMode
            val systemDarkTheme = isSystemInDarkTheme()
            // Removed unused 'isDarkTheme' variable matching warning

            var currentTheme by remember { mutableStateOf<Boolean?>(null) }
            
            LaunchedEffect(darkModeSetting) {
                currentTheme = darkModeSetting
            }

            val effectiveDarkTheme = currentTheme ?: systemDarkTheme

            UberTimeTrackerTheme(darkTheme = effectiveDarkTheme) {
                MainScreen(
                    isDarkTheme = effectiveDarkTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
                        scope.launch {
                            database.settingsDao().updateDarkMode(newTheme)
                        }
                    }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean?) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = if (isDarkTheme) {
                    Color(0xFF1E1E1E)
                } else {
                    Color.White
                },
                contentColor = PurplePrimary
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(text = screen.title)
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PurplePrimary,
                            selectedTextColor = PurplePrimary,
                            unselectedIconColor = if (isDarkTheme) Color.Gray else Color.DarkGray,
                            unselectedTextColor = if (isDarkTheme) Color.Gray else Color.DarkGray,
                            indicatorColor = PurplePrimary.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Home.route,
                enterTransition = {
                    fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
                exitTransition = {
                    fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                }
            ) {
                HomeScreen()
            }

            composable(
                route = Screen.Timesheet.route,
                enterTransition = {
                    fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                exitTransition = {
                    fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                }
            ) {
                TimesheetScreen()
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = {
                    fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                exitTransition = {
                    fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                }
            ) {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}
