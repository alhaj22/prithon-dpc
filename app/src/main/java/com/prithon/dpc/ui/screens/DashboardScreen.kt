package com.prithon.dpc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prithon.dpc.ui.components.StatusPill
import com.prithon.dpc.ui.theme.Amber50
import com.prithon.dpc.ui.theme.Amber600
import com.prithon.dpc.ui.theme.Blue50
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.Green50
import com.prithon.dpc.ui.theme.Green600
import com.prithon.dpc.ui.theme.PureWhite
import com.prithon.dpc.ui.theme.Slate200
import com.prithon.dpc.ui.theme.Slate500
import com.prithon.dpc.ui.theme.Slate900
import com.prithon.dpc.ui.viewmodel.DpcUiState

@Composable
fun DashboardScreen(
    state: DpcUiState,
    onNavigateToCustomer: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenPermissionWizard: () -> Unit,
    onSyncNow: () -> Unit,
    onCheckUpdates: () -> Unit,
    onCopiedDeviceId: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Protection Status Card
        item {
            ProtectionHeroCard(
                state = state,
                onOpenWizard = onOpenPermissionWizard,
                onViewSecurity = onNavigateToSecurity
            )
        }

        // 2. Customer & EMI Binding Summary Card
        item {
            CustomerSnapshotCard(
                state = state,
                onBindOrEdit = onNavigateToCustomer
            )
        }

        // 3. Hardware & Gateway Telemetry Card
        item {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = Blue600,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Device & Gateway Telemetry",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    HorizontalDivider(color = Slate200)

                    TelemetryRow(
                        label = "Hardware",
                        value = "${state.identity.manufacturer} ${state.identity.model} (Android ${state.identity.androidVersion})"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device ID",
                                style = MaterialTheme.typography.labelMedium,
                                color = Slate500
                            )
                            Text(
                                text = state.identity.androidId,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.identity.androidId))
                                onCopiedDeviceId()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Device ID",
                                tint = Blue600,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    TelemetryRow(
                        label = "Control Server",
                        value = state.serverUrl
                    )

                    TelemetryRow(
                        label = "App Build",
                        value = "v${state.identity.appVersionName} (Build ${state.identity.appVersionCode})"
                    )
                }
            }
        }

        // 4. Quick Actions Grid
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Sync MDM",
                    subtitle = "Push telemetry",
                    icon = Icons.Rounded.Sync,
                    onClick = onSyncNow,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Check OTA",
                    subtitle = "v${state.identity.appVersionName}",
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = onCheckUpdates,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Security Audit",
                    subtitle = if (state.security.isFullyProtected) "All active" else "Setup needed",
                    icon = Icons.Rounded.Security,
                    onClick = onNavigateToSecurity,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Server Config",
                    subtitle = "Switch gateway",
                    icon = Icons.Rounded.Settings,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProtectionHeroCard(
    state: DpcUiState,
    onOpenWizard: () -> Unit,
    onViewSecurity: () -> Unit
) {
    val security = state.security
    val containerColor = when {
        security.isDeviceOwner -> Green50
        security.isFullyProtected -> Blue50
        else -> Amber50
    }
    val borderColor = when {
        security.isDeviceOwner -> Green600.copy(alpha = 0.35f)
        security.isFullyProtected -> Blue600.copy(alpha = 0.35f)
        else -> Amber600.copy(alpha = 0.45f)
    }
    val accentColor = when {
        security.isDeviceOwner -> Green600
        security.isFullyProtected -> Blue600
        else -> Amber600
    }
    val headline = when {
        security.isDeviceOwner -> "Device Owner Verified"
        security.isFullyProtected -> "Finance Guard Armed"
        else -> "Protection Setup Incomplete"
    }
    val description = when {
        security.isDeviceOwner -> "Hardware kiosk lock, power-menu suppression, and silent OTA updates are active."
        security.isFullyProtected -> "Device Admin, Overlay Lock, and Anti-Power-Off Accessibility guard are active."
        else -> "${security.completedStepsCount} of 3 required security permissions granted. Complete setup to arm EMI lock."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (security.isFullyProtected) Icons.Rounded.VerifiedUser else Icons.Rounded.GppMaybe,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                }
            }

            if (!security.isFullyProtected) {
                LinearProgressIndicator(
                    progress = { security.completedStepsCount / 3f },
                    color = Amber600,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onOpenWizard,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber600),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("COMPLETE SETUP (${security.completedStepsCount}/3)")
                }
            } else {
                OutlinedButton(
                    onClick = onViewSecurity,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("VIEW SECURITY POLICIES")
                }
            }
        }
    }
}

@Composable
private fun CustomerSnapshotCard(
    state: DpcUiState,
    onBindOrEdit: () -> Unit
) {
    val customer = state.customer
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
                Text(
                    text = "Financed Customer & EMI",
                    style = MaterialTheme.typography.titleMedium
                )
                StatusPill(
                    text = if (customer.isRegistered) customer.deviceStatus else "UNBOUND",
                    isPositive = customer.isRegistered && customer.deviceStatus == "NORMAL",
                    isWarning = !customer.isRegistered
                )
            }

            HorizontalDivider(color = Slate200)

            if (customer.isRegistered) {
                TelemetryRow(label = "Shop Code", value = customer.tenantCode.ifEmpty { "Unassigned" })
                TelemetryRow(label = "Customer", value = "${customer.customerName} (${customer.customerPhone})")
                TelemetryRow(label = "IMEI", value = customer.enteredImei.ifEmpty { "Hardware Bound" })
                TelemetryRow(
                    label = "Loan / Monthly EMI",
                    value = "${customer.loanId.ifEmpty { "Standard" }}  •  ${if (customer.emiAmount.isNotEmpty()) "₹${customer.emiAmount}/mo" else "N/A"}"
                )
                OutlinedButton(
                    onClick = onBindOrEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("MANAGE CUSTOMER BINDING")
                }
            } else {
                Text(
                    text = "No customer or EMI loan is bound to this device yet. Register the customer to link this handset with your retailer portal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500
                )
                Button(
                    onClick = onBindOrEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("REGISTER CUSTOMER & IMEI")
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Slate500
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Blue50,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Blue600,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
