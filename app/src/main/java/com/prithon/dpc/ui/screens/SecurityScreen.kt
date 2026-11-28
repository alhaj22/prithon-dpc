package com.prithon.dpc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.prithon.dpc.ui.components.StatusPill
import com.prithon.dpc.ui.theme.Blue50
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.PureWhite
import com.prithon.dpc.ui.theme.Slate200
import com.prithon.dpc.ui.theme.Slate500
import com.prithon.dpc.ui.viewmodel.DpcUiState

@Composable
fun SecurityScreen(
    state: DpcUiState,
    onGrantAdmin: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val security = state.security

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Hardware & Anti-Tamper Controls",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Verify that all required DPC permissions are armed for reliable EMI lock enforcement.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
        }

        if (!security.isDeviceOwner && !security.isFullyProtected) {
            item {
                Button(
                    onClick = onOpenWizard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("LAUNCH GUIDED PERMISSION WIZARD")
                }
            }
        }

        item {
            PermissionCheckCard(
                title = if (security.isDeviceOwner) "Device Owner (Enterprise)" else "Device Administrator",
                description = if (security.isDeviceOwner) {
                    "Full Android Enterprise Device Owner mode active. Hard kiosk & power menu suppression enabled."
                } else {
                    "Prevents unauthorized app removal and enforces remote lock commands."
                },
                icon = Icons.Rounded.AdminPanelSettings,
                isGranted = security.isDeviceOwner || security.isDeviceAdmin,
                actionLabel = "ACTIVATE ADMIN",
                onAction = onGrantAdmin
            )
        }

        item {
            PermissionCheckCard(
                title = "Display Over Other Apps",
                description = "Draws the EMI overdue lock screen immediately over any running foreground app.",
                icon = Icons.Rounded.Layers,
                isGranted = security.isDeviceOwner || security.hasOverlayPermission,
                actionLabel = "GRANT OVERLAY",
                onAction = onGrantOverlay
            )
        }

        item {
            PermissionCheckCard(
                title = "Anti-Power-Off Accessibility Guard",
                description = "Intercepts power-off & settings tampering attempts while the device is in LOCKED state.",
                icon = Icons.Rounded.AccessibilityNew,
                isGranted = security.isDeviceOwner || security.hasAccessibilityEnabled,
                actionLabel = "ENABLE GUARD",
                onAction = onGrantAccessibility
            )
        }

        item {
            PermissionCheckCard(
                title = "Live GPS Telemetry",
                description = "Reports device location to the financier portal for asset recovery.",
                icon = Icons.Rounded.LocationOn,
                isGranted = security.hasLocationPermission,
                actionLabel = "ALLOW GPS",
                onAction = onRequestLocation
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Blue600, modifier = Modifier.size(20.dp))
                        Text("Enforced Lock Policies", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = Slate200)
                    PolicyLine("Direct Boot Persistence", "Restores lock before PIN entry after reboot")
                    PolicyLine("Offline SMS Fallback", "0-KB lock/unlock via signed SMS commands")
                    PolicyLine("Power Menu & Safe Boot", if (security.isDeviceOwner) "Hardware blocked (Device Owner)" else "Accessibility guarded")
                    PolicyLine("Factory Reset Protection", if (security.isDeviceOwner) "Blocked by Device Owner policy" else "Admin protected")
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(color = Blue50, shape = RoundedCornerShape(10.dp)) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Blue600,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
                StatusPill(
                    text = if (isGranted) "ACTIVE" else "REQUIRED",
                    isPositive = isGranted,
                    isWarning = !isGranted
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
            if (!isGranted) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun PolicyLine(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = Slate500)
    }
}
