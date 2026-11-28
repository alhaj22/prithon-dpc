package com.prithon.dpc.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prithon.dpc.data.UpdateUiState
import com.prithon.dpc.ui.components.PermissionWizardDialog
import com.prithon.dpc.ui.components.StatusPill
import com.prithon.dpc.ui.components.UpdateStateDialogs
import com.prithon.dpc.ui.screens.CustomerScreen
import com.prithon.dpc.ui.screens.DashboardScreen
import com.prithon.dpc.ui.screens.SecurityScreen
import com.prithon.dpc.ui.screens.SettingsScreen
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.PureWhite
import com.prithon.dpc.ui.theme.Slate50
import com.prithon.dpc.ui.viewmodel.DpcUiState

sealed class DpcDestination(
    val route: String,
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Dashboard : DpcDestination("dashboard", "Overview", Icons.Outlined.Shield, Icons.Filled.Shield)
    data object Customer : DpcDestination("customer", "Customer", Icons.Outlined.PersonOutline, Icons.Filled.Person)
    data object Security : DpcDestination("security", "Security", Icons.Outlined.Security, Icons.Filled.Security)
    data object Settings : DpcDestination("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)

    companion object {
        val entries = listOf(Dashboard, Customer, Security, Settings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrithonAppScaffold(
    state: DpcUiState,
    onGrantAdmin: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onRequestLocation: () -> Unit,
    onDismissWizard: () -> Unit,
    onOpenWizard: () -> Unit,
    onSaveCustomer: (String, String, String, String, String, String) -> Boolean,
    onSyncNow: () -> Unit,
    onCheckUpdates: () -> Unit,
    onConfirmUpdate: (UpdateUiState.Available) -> Unit,
    onDismissUpdate: () -> Unit,
    onSelectServer: (String) -> Unit,
    onDeactivateAndUninstall: () -> Unit,
    onCopiedDeviceId: () -> Unit,
    onSnackbarShown: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onSnackbarShown()
        }
    }

    if (state.showPermissionWizard) {
        PermissionWizardDialog(
            security = state.security,
            onDismiss = onDismissWizard,
            onGrantAdmin = onGrantAdmin,
            onGrantOverlay = onGrantOverlay,
            onGrantAccessibility = onGrantAccessibility
        )
    }

    UpdateStateDialogs(
        updateState = state.updateState,
        onConfirmUpdate = onConfirmUpdate,
        onDismiss = onDismissUpdate
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = Slate50,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Blue600,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Prithon Guard",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    val badgeText = when {
                        state.security.isDeviceOwner -> "DEVICE OWNER"
                        state.security.isFullyProtected -> "ARMED"
                        else -> "SETUP ${state.security.completedStepsCount}/3"
                    }
                    Row(modifier = Modifier.padding(end = 12.dp)) {
                        StatusPill(
                            text = badgeText,
                            isPositive = state.security.isFullyProtected,
                            isWarning = !state.security.isFullyProtected
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = PureWhite) {
                DpcDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(dest.route) },
                        icon = {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label
                            )
                        },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DpcDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(DpcDestination.Dashboard.route) {
                DashboardScreen(
                    state = state,
                    onNavigateToCustomer = { navigateToTab(DpcDestination.Customer.route) },
                    onNavigateToSecurity = { navigateToTab(DpcDestination.Security.route) },
                    onNavigateToSettings = { navigateToTab(DpcDestination.Settings.route) },
                    onOpenPermissionWizard = onOpenWizard,
                    onSyncNow = onSyncNow,
                    onCheckUpdates = onCheckUpdates,
                    onCopiedDeviceId = onCopiedDeviceId
                )
            }
            composable(DpcDestination.Customer.route) {
                CustomerScreen(
                    state = state,
                    onSaveCustomer = onSaveCustomer,
                    onSyncNow = onSyncNow
                )
            }
            composable(DpcDestination.Security.route) {
                SecurityScreen(
                    state = state,
                    onGrantAdmin = onGrantAdmin,
                    onGrantOverlay = onGrantOverlay,
                    onGrantAccessibility = onGrantAccessibility,
                    onRequestLocation = onRequestLocation,
                    onOpenWizard = onOpenWizard
                )
            }
            composable(DpcDestination.Settings.route) {
                SettingsScreen(
                    state = state,
                    onSelectServer = onSelectServer,
                    onCheckUpdates = onCheckUpdates,
                    onDeactivateAndUninstall = onDeactivateAndUninstall
                )
            }
        }
    }
}
