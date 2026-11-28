package com.prithon.dpc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prithon.dpc.data.ServerPreset
import com.prithon.dpc.ui.components.CustomServerDialog
import com.prithon.dpc.ui.components.UninstallConfirmDialog
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.PureWhite
import com.prithon.dpc.ui.theme.Red50
import com.prithon.dpc.ui.theme.Red600
import com.prithon.dpc.ui.theme.Slate200
import com.prithon.dpc.ui.theme.Slate500
import com.prithon.dpc.ui.viewmodel.DpcUiState

@Composable
fun SettingsScreen(
    state: DpcUiState,
    onSelectServer: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onDeactivateAndUninstall: () -> Unit
) {
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    if (showCustomUrlDialog) {
        CustomServerDialog(
            currentUrl = state.serverUrl,
            onDismiss = { showCustomUrlDialog = false },
            onConfirm = { custom ->
                showCustomUrlDialog = false
                onSelectServer(custom)
            }
        )
    }

    if (showUninstallDialog) {
        UninstallConfirmDialog(
            onDismiss = { showUninstallDialog = false },
            onConfirm = {
                showUninstallDialog = false
                onDeactivateAndUninstall()
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Server Gateway Card
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
                        Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = Blue600, modifier = Modifier.size(20.dp))
                        Text("Control Plane Server", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Active: ${state.serverUrl}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                    HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))

                    ServerPreset.entries.forEach { preset ->
                        val selected = state.serverUrl.equals(preset.url, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectServer(preset.url) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectServer(preset.url) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.label, style = MaterialTheme.typography.bodyLarge)
                                Text(preset.url, style = MaterialTheme.typography.bodyMedium, color = Slate500)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showCustomUrlDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ENTER CUSTOM SERVER URL")
                    }
                }
            }
        }

        // 2. App Version & OTA Updates
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
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Blue600, modifier = Modifier.size(20.dp))
                        Text("Application & OTA Updates", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Installed: v${state.identity.appVersionName} (Build ${state.identity.appVersionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CHECK FOR UPDATES")
                    }
                }
            }
        }

        // 3. Danger Zone - Deactivate & Uninstall
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Red50),
                border = BorderStroke(1.dp, Red600.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Release Device & Uninstall",
                        style = MaterialTheme.typography.titleMedium,
                        color = Red600
                    )
                    Text(
                        text = "Revoke Device Administrator / Owner privileges and uninstall Prithon Device Guard after EMI settlement.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                    OutlinedButton(
                        onClick = { showUninstallDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red600),
                        border = BorderStroke(1.dp, Red600.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("DEACTIVATE ADMIN & UNINSTALL")
                    }
                }
            }
        }
    }
}
