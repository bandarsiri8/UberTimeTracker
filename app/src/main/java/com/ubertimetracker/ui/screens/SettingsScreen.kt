package com.ubertimetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ubertimetracker.ui.theme.*
import com.ubertimetracker.ui.viewmodel.SettingsViewModel
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    isDarkTheme: Boolean = false,
    onThemeChange: (Boolean?) -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()

    val showThemeDialog by viewModel.showThemeDialog.collectAsState()
    val isDriveSignedIn by viewModel.isDriveSignedIn.collectAsState()

    val signInError by viewModel.signInError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(signInError) {
        signInError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result)
    }


    val backgroundColor = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(GradientStartDark, GradientEndDark)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(GradientStartLight, GradientEndLight)
        )
    }

    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Google Drive") },
            text = { Text("Are you sure you want to disconnect from Google Drive?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnectDrive()
                        showDisconnectDialog = false
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = PurplePrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Accessibility Section
        SettingsSection(title = "Accessibility", isDarkTheme = isDarkTheme) {
            SettingsItem(
                icon = Icons.Outlined.Accessibility,
                title = "Accessibility Service",
                subtitle = if (isAccessibilityEnabled) "Connected" else "Not enabled - Tap to enable",
                isDarkTheme = isDarkTheme,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAccessibilityEnabled) GreenOnline else RedStop)
                    )
                },
                onClick = { viewModel.openAccessibilitySettings(context) }
            )
            
            Divider(modifier = Modifier.padding(start = 56.dp).padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))

            SettingsItem(
                icon = Icons.Outlined.NotificationsActive,
                title = "Notification Access",
                subtitle = "Enhanced background detection (Required)",
                isDarkTheme = isDarkTheme,
                trailing = {
                     Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isDarkTheme) TextSecondaryDark else TextSecondaryLight
                    )
                },
                onClick = { viewModel.openNotificationSettings(context) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sync Section
        SettingsSection(title = "Sync Settings", isDarkTheme = isDarkTheme) {
            SettingsToggleItem(
                icon = Icons.Outlined.Sync,
                title = "Auto-Sync",
                subtitle = "Automatically sync with Uber app",
                isChecked = settings.autoSyncEnabled,
                isDarkTheme = isDarkTheme,
                onToggle = { viewModel.updateAutoSync(it) }
            )

            Divider(
                modifier = Modifier.padding(start = 56.dp),
                color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
            )

            SettingsItem(
                icon = Icons.Outlined.CloudUpload,
                title = "Google Drive",
                subtitle = if (isDriveSignedIn) "Connected" else "Tap to connect",
                isDarkTheme = isDarkTheme,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDriveSignedIn) GreenOnline else Color.Gray)
                    )
                },
                onClick = { 
                    if (isDriveSignedIn) {
                        showDisconnectDialog = true
                    } else {
                        driveSignInLauncher.launch(viewModel.getDriveSignInIntent()) 
                    }
                }
            )

            Divider(
                modifier = Modifier.padding(start = 56.dp),
                color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
            )

            Divider(
                modifier = Modifier.padding(start = 56.dp),
                color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
            )

            SettingsToggleItem(
                icon = Icons.Outlined.Storage,
                title = "Offline Cache",
                subtitle = "Store data locally for offline use",
                isChecked = settings.offlineCacheEnabled,
                isDarkTheme = isDarkTheme,
                onToggle = { viewModel.updateOfflineCache(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance Section
        SettingsSection(title = "Appearance", isDarkTheme = isDarkTheme) {
            SettingsItem(
                icon = Icons.Outlined.DarkMode,
                title = "Theme",
                subtitle = viewModel.getThemeText(settings.isDarkMode),
                isDarkTheme = isDarkTheme,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isDarkTheme) TextSecondaryDark else TextSecondaryLight
                    )
                },
                onClick = { viewModel.showThemeDialog() }
            )


        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications Section
        SettingsSection(title = "Notifications", isDarkTheme = isDarkTheme) {
            SettingsToggleItem(
                icon = Icons.Outlined.Notifications,
                title = "Push Notifications",
                subtitle = "Receive timer and sync notifications",
                isChecked = settings.notificationsEnabled,
                isDarkTheme = isDarkTheme,
                onToggle = { viewModel.updateNotifications(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Section
        SettingsSection(title = "About", isDarkTheme = isDarkTheme) {
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "Version",
                subtitle = "1.0.0",
                isDarkTheme = isDarkTheme
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Data Management Section
        SettingsSection(title = "Data Management", isDarkTheme = isDarkTheme) {
            var showClearDialog by remember { mutableStateOf(false) }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Clear All Data") },
                    text = { Text("Are you sure you want to delete ALL timesheet data? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearAllData()
                                showClearDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = RedStop)
                        ) {
                            Text("Delete All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            SettingsItem(
                icon = Icons.Outlined.DeleteForever,
                title = "Clear All Data",
                subtitle = "Permanently delete all sessions",
                isDarkTheme = isDarkTheme,
                onClick = { showClearDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = settings.isDarkMode,
            onDismiss = { viewModel.hideThemeDialog() },
            onSelect = { theme ->
                viewModel.updateDarkMode(theme)
                onThemeChange(theme)
                viewModel.hideThemeDialog()
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    isDarkTheme: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PurplePrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) CardDark else CardLight
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDarkTheme: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PurplePrimary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDarkTheme) TextPrimaryDark else TextPrimaryLight
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkTheme) TextSecondaryDark else TextSecondaryLight
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    isDarkTheme: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PurplePrimary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDarkTheme) TextPrimaryDark else TextPrimaryLight
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkTheme) TextSecondaryDark else TextSecondaryLight
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PurplePrimary
            )
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: Boolean?,
    onDismiss: () -> Unit,
    onSelect: (Boolean?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Theme",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                ThemeOption(
                    title = "Light Mode",
                    isSelected = currentTheme == false,
                    onClick = { onSelect(false) }
                )
                ThemeOption(
                    title = "Dark Mode",
                    isSelected = currentTheme == true,
                    onClick = { onSelect(true) }
                )
                ThemeOption(
                    title = "System Default",
                    isSelected = currentTheme == null,
                    onClick = { onSelect(null) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = PurplePrimary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
