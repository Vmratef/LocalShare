// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.defname.localshare.ui.screens.main.components.BottomBarItem
import com.defname.localshare.ui.screens.main.components.GlassBottomBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.defname.localshare.data.RuntimeState
import com.defname.localshare.ui.screens.files.FilesScreen
import com.defname.localshare.ui.screens.home.HomeScreen
import com.defname.localshare.ui.screens.info.InfoScreen
import com.defname.localshare.ui.screens.logs.LogsScreen
import com.defname.localshare.ui.screens.main.components.MainMenu
import com.defname.localshare.ui.screens.main.components.WelcomeOnboarding
import com.defname.localshare.ui.screens.settings.SettingsScreen
import com.defname.localshare.ui.screens.sharedcontent.SharedContentScreen
import com.defname.localshare.ui.screens.transfers.TransfersScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentRoute = navBackStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val navigateTo: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Home / Files / Transfers live in the floating bottom bar, the rest stays in the side menu.
    val bottomItems = listOf(
        BottomBarItem(Screen.Main.route, "Home", Icons.Default.Home),
        BottomBarItem(Screen.Files.route, "Files", Icons.Default.ListAlt),
        BottomBarItem(Screen.Transfers.route, "Transfers", Icons.Default.SwapVert)
    )
    val bottomRoutes = bottomItems.map { it.route }.toSet()
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    if (state.welcomeMessageVisible) {
        WelcomeOnboarding(
            onDismiss = { scope.launch { viewModel.onDismissWelcomeMessage(true) } },
            onDontShowAgain = { scope.launch { viewModel.onDismissWelcomeMessage(false) } }
        )
    }

    // swipe left/right = next/previous tab. The side menu opens with the menu button only
    // (swiping stays possible while it is open, to close it).
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            MainMenu(
                drawerItems = state.navigationItems.filter { it.route !in bottomRoutes },
                onItemClick = { route ->
                    // navigate right away, the menu slides out at the same time
                    navigateTo(route)
                    scope.launch { drawerState.close() }
                },
                currentRoute = currentRoute ?: state.navigationItems.getOrNull(0)?.route ?: "",
                onHelpClick = {
                    if (state.serverState != RuntimeState.RUNNING) {
                        android.widget.Toast.makeText(
                            context,
                            "Please start the server first",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    else {
                        viewModel.openInBrowser(context, state.helpLink)
                    }
                }
            )
        }
    ) {
        val onOpenDrawer: () -> Unit = {
            scope.launch { drawerState.open() }
        }

        Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // the bottom bar handles the navigation-bar inset, so inner Scaffolds must not add it again
                .consumeWindowInsets(WindowInsets.navigationBars)
                .pointerInput(currentRoute) {
                    val index = bottomItems.indexOfFirst { it.route == currentRoute }
                    if (index < 0) return@pointerInput
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            total += dragAmount
                            if (kotlin.math.abs(total) > 0f) change.consume()
                        },
                        onDragEnd = {
                            val target = when {
                                total < -swipeThresholdPx -> index + 1
                                total > swipeThresholdPx -> index - 1
                                else -> -1
                            }
                            bottomItems.getOrNull(target)?.let { navigateTo(it.route) }
                        }
                    )
                }
        ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.fillMaxSize(),
            // Quick tab switches (the default is a slow 700 ms cross-fade that shows two glass screens on top of each other)
            enterTransition = { fadeIn(tween(130)) },
            exitTransition = { fadeOut(tween(80)) },
            popEnterTransition = { fadeIn(tween(130)) },
            popExitTransition = { fadeOut(tween(80)) }
        ) {
            composable(Screen.Main.route) {
                HomeScreen(
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToLogs = { navController.navigate(Screen.Logs.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    } },
                    onNavigateToTransfers = { navController.navigate(Screen.Transfers.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    } }
                )
            }
            composable(Screen.Files.route) {
                FilesScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(Screen.SharedContent.route) {
                SharedContentScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(
                route = Screen.Transfers.route,
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = com.defname.localshare.service.notification.NotificationHelper.DEEP_LINK_URI }
                )
            ) {
                TransfersScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenDrawer = onOpenDrawer
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(Screen.Info.route) {
                InfoScreen(onOpenDrawer = onOpenDrawer)
            }
        }
        }

        if (!keyboardOpen) {
            GlassBottomBar(
                items = bottomItems,
                currentRoute = currentRoute,
                onItemClick = navigateTo,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp, top = 4.dp)
            )
        }
        }
    }
}
