package com.prithon.dpc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prithon.dpc.data.SecurityStatus
import com.prithon.dpc.data.UpdateUiState
import com.prithon.dpc.ui.theme.Amber50
import com.prithon.dpc.ui.theme.Amber600
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.Green50
import com.prithon.dpc.ui.theme.Green600
import com.prithon.dpc.ui.theme.Red600
import com.prithon.dpc.ui.theme.Slate100
import com.prithon.dpc.ui.theme.Slate700

@Composable
fun StatusPill(
    text: String,
    isPositive: Boolean,
    isWarning: Boolean = false
) {
    val bgColor = when {
        isPositive -> Green50
        isWarning -> Amber50
        else -> Slate100
    }
    val contentColor = when {
        isPositive -> Green600
        isWarning -> Amber600
        else -> Slate700
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PermissionWizardDialog(
    security: SecurityStatus,
    onDismiss: () -> Unit,
    onGrantAdmin: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantAccessibility: () -> Unit
) {
    if (security.isDeviceOwner || security.isFullyProtected) {
        onDismiss()
        return
    }

    val (stepTitle, stepBody, actionLabel, onAction) = when {
        !security.isDeviceAdmin -> Quad(
            "Step 1 of 3: Activate Device Admin",
            "Device Administrator permission is mandatory to safeguard financed hardware and enforce remote lock policies.",
            "ALLOW DEVICE ADMIN",
            onGrantAdmin
        )
        !security.hasOverlayPermission -> Quad(
            "Step 2 of 3: Display Over Other Apps",
            "Allow 'Display over other apps' so the EMI overdue lock screen can appear reliably.\n\nAndroid 13/14 Tip: If the toggle is greyed out, open Settings > Apps > Prithon Device Guard > 3-dots menu (⋮) > Allow restricted settings.",
            "ALLOW OVERLAY",
            onGrantOverlay
        )
        else -> Quad(
            "Step 3 of 3: Anti-Power-Off Guard",
            "Enable 'Prithon Anti-Power-Off Protection' in Accessibility settings to prevent unauthorized shutdown or tampering while locked.",
            "OPEN ACCESSIBILITY",
            onGrantAccessibility
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = Blue600,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { (security.completedStepsCount + 1) / 3f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stepBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("LATER")
            }
        }
    )
}

@Composable
fun UpdateStateDialogs(
    updateState: UpdateUiState,
    onConfirmUpdate: (UpdateUiState.Available) -> Unit,
    onDismiss: () -> Unit
) {
    when (updateState) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Blue600)
                },
                title = { Text("Checking for Updates") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Contacting Prithon release server...")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Blue600)
                },
                title = { Text("Update Available (v${updateState.versionName})") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Build ${updateState.versionCode} is ready to install.",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = updateState.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onConfirmUpdate(updateState) }) {
                        Text("UPDATE NOW")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("LATER")
                    }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Blue600)
                },
                title = { Text("Downloading v${updateState.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(
                            progress = { (updateState.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${updateState.progress}% completed",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Red600)
                },
                title = { Text("Update Failed") },
                text = {
                    Text("Download error: ${updateState.message}\nPlease verify network connectivity and retry.")
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun CustomServerDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Gateway URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the full HTTP/HTTPS URL of your Prithon MDM control plane.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.3:4000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(urlInput) },
                enabled = urlInput.isNotBlank()
            ) {
                Text("CONNECT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
fun UninstallConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Red600)
        },
        title = { Text("Deactivate Admin & Uninstall?") },
        text = {
            Text(
                "This will clear local finance credentials, revoke Device Administrator / Owner privileges, and prompt uninstallation of Prithon Device Guard."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Red600)
            ) {
                Text("YES, UNINSTALL")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
