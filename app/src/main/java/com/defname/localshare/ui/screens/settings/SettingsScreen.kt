// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.settings

import com.defname.localshare.ui.components.ThemeToggleButton
import androidx.compose.foundation.clickable
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.defname.localshare.ui.theme.ThemeController
import com.defname.localshare.ui.theme.ThemeMode
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.defname.localshare.R
import com.defname.localshare.data.RuntimeState
import com.defname.localshare.ui.components.ConfirmedTextField
import com.defname.localshare.ui.theme.LocalShareTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onOpenDrawer: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = { ThemeToggleButton() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance
            SettingsGroup(title = "Appearance") {
                SettingsRow {
                    Column(Modifier.weight(1f)) {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ThemeMode.SYSTEM to "System",
                                ThemeMode.LIGHT to "Light",
                                ThemeMode.DARK to "Dark"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = ThemeController.mode == mode,
                                    onClick = { ThemeController.set(context, mode) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Gruppe 1: Server Konfiguration
            SettingsGroup(title = stringResource(R.string.settings_server_title)) {
                // Port Einstellung
                SettingsRow {
                    ConfirmedTextField(
                        value = settings.serverPort.toString(),
                        validator = { input -> input.toIntOrNull().let { it != null && it in 1024.. 65535 } },
                        errorHint = { Text("Port must be between 1024 and 65535") },
                        enabled = runtimeState.serviceState == RuntimeState.STOPPED,
                        label = { Text(stringResource(R.string.settings_server_port_label)) },
                        modifier = Modifier.weight(1f),
                        supportingText = {
                            if (runtimeState.serviceState != RuntimeState.STOPPED) {
                                Text(stringResource(R.string.settings_server_port_hint_not_available))
                            } else {
                                Text(stringResource(R.string.settings_server_port_hint))
                            }
                        },
                        onValueChange = { viewModel.setPort(it.toInt()) },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                SettingsRow {
                    ConfirmedTextField(
                        value = settings.serverIdleTimeoutSeconds.toString(),
                        label = { Text(stringResource(R.string.settings_server_timeout_label)) },
                        validator = { input -> input.toIntOrNull() != null },
                        errorHint = { Text("Number of seonds must be a number.") },
                        modifier = Modifier.weight(1f),
                        supportingText = {
                            Text(stringResource(R.string.settings_server_timeout_hint))
                        },
                        onValueChange = { viewModel.setIdleTimeoutSeconds(it.toInt()) },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            }

            // Gruppe 2: Sicherheit
            SettingsGroup(title = stringResource(R.string.settings_security_title)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_regenerate_token_at_app_start_label),
                    subtitle = stringResource(R.string.settings_generate_a_new_security_token_every_time_the_app_starts_hint),
                    checked = settings.regenerateTokenAtAppStart,
                    onCheckedChange =viewModel::setRegenerateTokenAtAppStart
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_security_require_approval_label),
                    subtitle = stringResource(R.string.settings_security_require_approval_hint),
                    checked = settings.requireApproval,
                    onCheckedChange =viewModel::setRequireApproval
                )

                if (settings.requireApproval) {
                    SettingsRow {
                        ConfirmedTextField(
                            value = settings.whitelistEntryTTLSeconds.toString(),
                            label = { Text(stringResource(R.string.settings_security_whitelist_ttl_label)) },
                            validator = { it.toIntOrNull() != null },
                            errorHint = { Text("Number of seconds must be a number.") },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.weight(1f),
                            supportingText = {
                                Text(stringResource(R.string.settings_security_whitelist_ttl_hint))
                            },
                            onValueChange = { viewModel.setWhiteListEntryTTLSeconds(it.toInt()) }
                        )
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_webinterface_title)) {
                SettingsRow {
                    ConfirmedTextField(
                        value = settings.sseHeartbeatPeriodSeconds.toString(),
                        label = { Text(stringResource(R.string.settings_webinterface_heartbeat_label)) },
                        validator = { it.toIntOrNull() != null },
                        errorHint = { Text("Number of seconds must be a number.") },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.weight(1f),
                        supportingText = {
                            Text(stringResource(R.string.settings_webinterface_heartbeat_hint))
                        },
                        onValueChange = { viewModel.setHeartbeatPeriodSeconds(it.toInt()) }
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_misc_title)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_misc_clear_file_list_on_share_label),
                    subtitle = stringResource(R.string.settings_misc_clear_file_list_on_share_hint),
                    checked = settings.clearFileListOnShareIntent,
                    onCheckedChange = viewModel::setClearFilesListOnSendIntent
                )
            }

            SettingsGroup(title = "Experimental Features") {
                SettingsSwitchRow(
                    title = "Upload Feature",
                    subtitle = "Send files from the web UI to the app.",
                    checked = settings.uploadFeatureActive,
                    onCheckedChange = viewModel::setUploadFeatureActive
                )
            }

            SettingsGroup(title = "Received files") {
                SettingsRow {
                    Column(Modifier.weight(1f)) {
                        Text("Received files list", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Files sent to this device are saved in Downloads/LocalShare. Clearing the history only removes the \"from / when\" info, never the files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { confirmClearHistory = true }) {
                            Text("Clear history")
                        }
                    }
                }
            }

            SettingsGroup(title = "Keep the server running") {
                SettingsRow {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "If sharing stops when the screen is off, allow LocalShare to run in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openBatterySettings(context) }) { Text("Battery") }
                            OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Notifications") }
                            OutlinedButton(onClick = { openAppInfo(context) }) { Text("App info") }
                        }
                    }
                }
            }

            SettingsGroup(title = "Reset") {
                SettingsRow {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Restore every setting to its default value.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { confirmReset = true }) {
                            Text("Reset all settings")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Port, security and web interface options go back to their defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSettings()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear history?") },
            text = { Text("The received files stay on your device. Only the sender and date info is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearReceivedHistory()
                    confirmClearHistory = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } }
        )
    }
}

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Not available on this device", Toast.LENGTH_SHORT).show()
    }
}

private fun openBatterySettings(context: Context) =
    safeStart(context, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

private fun openNotificationSettings(context: Context) =
    safeStart(
        context,
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    )

private fun openAppInfo(context: Context) =
    safeStart(
        context,
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    LocalShareTheme {
        SettingsScreen(
            onOpenDrawer = {},
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Column(content = content)
    }
}

@Composable
fun SettingsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
