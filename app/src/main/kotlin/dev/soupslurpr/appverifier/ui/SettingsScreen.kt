package dev.soupslurpr.appverifier.ui

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import dev.soupslurpr.appverifier.R
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode
import dev.soupslurpr.appverifier.data.toText
import dev.soupslurpr.appverifier.data.toYaml
import dev.soupslurpr.appverifier.preferences.PreferencesUiState
import dev.soupslurpr.appverifier.preferences.PreferencesViewModel
import kotlinx.coroutines.launch

private enum class ExportFormat { JSON, YAML, TEXT }

@Composable
fun SettingsScreen(
    onLicenseIconButtonClicked: () -> Unit,
    onCreditsIconButtonClicked: () -> Unit,
    preferencesViewModel: PreferencesViewModel,
) {
    val localUriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val preferencesUiState by preferencesViewModel.uiState.collectAsState()
    val databaseStatusDisplayMode = DatabaseStatusDisplayMode.valueOf(
        preferencesUiState.databaseStatusDisplayMode
    )
    val defaultSortMode = preferencesUiState.defaultSortMode
    val userDatabaseEntries by preferencesViewModel.userDatabaseEntries.collectAsState()
    val clipboardVerifiedPackages by preferencesViewModel.clipboardVerifiedPackages.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearedDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var showExportFormatDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    val exportSuccessMessage = stringResource(R.string.export_success)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingExportFormat != null) {
            coroutineScope.launch {
                val data = when (pendingExportFormat) {
                    ExportFormat.JSON -> preferencesViewModel.exportUserDatabase()
                    ExportFormat.YAML -> userDatabaseEntries.toYaml()
                    ExportFormat.TEXT -> userDatabaseEntries.toText()
                    null -> return@launch
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(data.toByteArray())
                }
                snackbarHostState.showSnackbar(exportSuccessMessage)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) {
    Column(
        modifier = Modifier
            .padding(it)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            SettingsCategoryText(category = stringResource(R.string.theme))

            val themeModeOptions = listOf(
                stringResource(R.string.theme_system) to "SYSTEM",
                stringResource(R.string.theme_light) to "LIGHT",
                stringResource(R.string.theme_dark) to "DARK",
            )
            val selectedThemeIndex = themeModeOptions.indexOfFirst { it.second == preferencesUiState.themeMode }.coerceAtLeast(0)

            Text(
                text = stringResource(R.string.theme_mode),
                style = typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                themeModeOptions.forEachIndexed { index, (label, _) ->
                    SegmentedButton(
                        selected = selectedThemeIndex == index,
                        onClick = {
                            coroutineScope.launch {
                                preferencesViewModel.setThemeMode(themeModeOptions[index].second)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = themeModeOptions.size,
                        ),
                        icon = {
                            Icon(
                                imageVector = when (themeModeOptions[index].second) {
                                    "LIGHT" -> Icons.Filled.LightMode
                                    "DARK" -> Icons.Filled.DarkMode
                                    else -> Icons.AutoMirrored.Filled.RotateLeft
                                },
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(label)
                    }
                }
            }

            val isDarkModeActive = preferencesUiState.themeMode == "DARK" ||
                (preferencesUiState.themeMode == "SYSTEM" && isSystemInDarkTheme())

            if (isDarkModeActive) {
                SettingsItem(
                    name = stringResource(R.string.amoled_theme_name),
                    description = stringResource(R.string.amoled_theme_description),
                    hasSwitch = true,
                    checked = preferencesUiState.useAmoledTheme,
                    onCheckedChange = {
                        coroutineScope.launch {
                            preferencesViewModel.setUseAmoledTheme(it)
                        }
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val colorSchemeOptions = listOf(
                    stringResource(R.string.color_scheme_standard) to "STANDARD",
                    stringResource(R.string.color_scheme_dynamic) to "DYNAMIC_COLOR",
                )
                val selectedColorSchemeIndex = colorSchemeOptions.indexOfFirst { it.second == preferencesUiState.colorSchemeMode }.coerceAtLeast(0)

                Text(
                    text = stringResource(R.string.color_scheme),
                    style = typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    colorSchemeOptions.forEachIndexed { index, (label, _) ->
                        SegmentedButton(
                            selected = selectedColorSchemeIndex == index,
                            onClick = {
                                coroutineScope.launch {
                                    preferencesViewModel.setColorSchemeMode(colorSchemeOptions[index].second)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = colorSchemeOptions.size,
                            ),
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        Column {
            SettingsCategoryText(category = stringResource(R.string.app_list))
            SettingsItem(
                name = stringResource(id = R.string.show_unverified_only_setting_name),
                description = stringResource(id = R.string.show_unverified_only_setting_description),
                hasSwitch = true,
                checked = preferencesUiState.showUnverifiedOnly,
                onCheckedChange = {
                    coroutineScope.launch {
                        preferencesViewModel.setPreference(PreferencesUiState.Keys.SHOW_UNVERIFIED_ONLY, it)
                    }
                }
            )
            if (preferencesUiState.showUnverifiedOnly) {
                SettingsItem(
                    name = stringResource(id = R.string.unverified_exclude_user_db_setting_name),
                    description = stringResource(id = R.string.unverified_exclude_user_db_setting_description),
                    hasSwitch = true,
                    checked = preferencesUiState.unverifiedExcludeUserDb,
                    onCheckedChange = {
                        coroutineScope.launch {
                            preferencesViewModel.setPreference(PreferencesUiState.Keys.UNVERIFIED_EXCLUDE_USER_DB, it)
                        }
                    }
                )
            }
            SettingsItem(
                name = stringResource(id = R.string.clipboard_verification_setting_name),
                description = stringResource(id = R.string.clipboard_verification_setting_description),
                hasSwitch = true,
                checked = preferencesUiState.showClipboardCheckmark,
                onCheckedChange = {
                    coroutineScope.launch {
                        preferencesViewModel.setPreference(PreferencesUiState.Keys.SHOW_CLIPBOARD_CHECKMARK, it)
                    }
                }
            )
            if (preferencesUiState.showClipboardCheckmark && clipboardVerifiedPackages.isNotEmpty()) {
                SettingsItem(
                        name = stringResource(id = R.string.clear_clipboard_checkmarks_setting_name),
                        description = stringResource(id = R.string.clear_clipboard_checkmarks_setting_description),
                        hasIcon = true,
                        onClickIconSetting = {
                            coroutineScope.launch {
                                preferencesViewModel.clearClipboardVerifiedPackages()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null
                            )
                        }
                        )
            }
            SettingsItem(
                name = stringResource(id = R.string.show_hasmultiplesigners_setting_name),
                description = stringResource(R.string.show_hasmultiplesigners_setting_description),
                hasSwitch = true,
                checked = preferencesUiState.showHasMultipleSigners,
                onCheckedChange = {
                    coroutineScope.launch {
                        preferencesViewModel.setPreference(PreferencesUiState.Keys.SHOW_HAS_MULTIPLE_SIGNERS, it)
                    }
                }
            )
            Text(
                text = stringResource(R.string.default_sort_mode_name),
                style = typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            var expanded by remember { mutableStateOf(false) }
            val selectedSortMode = SortMode.entries.find { it.name == defaultSortMode } ?: SortMode.NAME_ASC
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text = when (selectedSortMode) {
                            SortMode.NAME_ASC -> stringResource(R.string.sort_mode_name_asc)
                            SortMode.NAME_DESC -> stringResource(R.string.sort_mode_name_desc)
                            SortMode.INTERNAL_DB -> stringResource(R.string.sort_mode_internal_db)
                            SortMode.USER_DB -> stringResource(R.string.sort_mode_user_db)
                            SortMode.DEBUG -> stringResource(R.string.sort_mode_debug)
                            SortMode.CLIPBOARD -> stringResource(R.string.sort_mode_clipboard)
                            SortMode.SHARED_TEXT -> stringResource(R.string.sort_mode_shared_text)
                        },
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(
                                when (mode) {
                                    SortMode.NAME_ASC -> stringResource(R.string.sort_mode_name_asc)
                                    SortMode.NAME_DESC -> stringResource(R.string.sort_mode_name_desc)
                                    SortMode.INTERNAL_DB -> stringResource(R.string.sort_mode_internal_db)
                                    SortMode.USER_DB -> stringResource(R.string.sort_mode_user_db)
                                    SortMode.DEBUG -> stringResource(R.string.sort_mode_debug)
                                    SortMode.CLIPBOARD -> stringResource(R.string.sort_mode_clipboard)
                                    SortMode.SHARED_TEXT -> stringResource(R.string.sort_mode_shared_text)
                                },
                            ) },
                            onClick = {
                                if (defaultSortMode != mode.name) {
                                    coroutineScope.launch {
                                        preferencesViewModel.setDefaultSortMode(mode.name)
                                    }
                                }
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        Column {
            SettingsCategoryText(category = stringResource(id = R.string.user_database))
            Text(
                text = stringResource(R.string.db_display_label),
                style = typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            DatabaseStatusDisplayMode.entries.forEach { mode ->
                val selected = databaseStatusDisplayMode == mode
                SettingsItem(
                    name = when (mode) {
                        DatabaseStatusDisplayMode.BOTH -> stringResource(R.string.db_mode_both)
                        DatabaseStatusDisplayMode.INTERNAL_ONLY -> stringResource(R.string.db_mode_internal)
                        DatabaseStatusDisplayMode.USER_ONLY -> stringResource(R.string.db_mode_user)
                    },
                    description = when (mode) {
                        DatabaseStatusDisplayMode.BOTH -> stringResource(R.string.db_desc_both)
                        DatabaseStatusDisplayMode.INTERNAL_ONLY -> stringResource(R.string.db_desc_internal)
                        DatabaseStatusDisplayMode.USER_ONLY -> stringResource(R.string.db_desc_user)
                    },
                    hasSwitch = true,
                    checked = selected,
                    onCheckedChange = {
                        if (!selected) {
                            coroutineScope.launch {
                                preferencesViewModel.setDatabaseStatusDisplayMode(mode)
                            }
                        }
                    }
                )
            }
            SettingsItem(
                name = stringResource(id = R.string.export_user_database),
                description = stringResource(id = R.string.export_user_database_description),
                hasIcon = true,
                onClickIconSetting = {
                    showExportFormatDialog = true
                },
                icon = {
                    Icon(Icons.Filled.FileUpload, null)
                }
            )
            if (showExportFormatDialog) {
                AlertDialog(
                    onDismissRequest = { showExportFormatDialog = false },
                    title = { Text(stringResource(R.string.export_format_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    pendingExportFormat = ExportFormat.JSON
                                    exportLauncher.launch("user_database.json")
                                    showExportFormatDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.export_format_json))
                            }
                            TextButton(
                                onClick = {
                                    pendingExportFormat = ExportFormat.YAML
                                    exportLauncher.launch("user_database.yaml")
                                    showExportFormatDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.export_format_yaml))
                            }
                            TextButton(
                                onClick = {
                                    pendingExportFormat = ExportFormat.TEXT
                                    exportLauncher.launch("user_database.txt")
                                    showExportFormatDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.export_format_text))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showExportFormatDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            SettingsItem(
                name = stringResource(id = R.string.clear_user_database),
                description = stringResource(id = R.string.clear_user_database_description),
                hasIcon = true,
                onClickIconSetting = {
                    showClearConfirmDialog = true
                },
                icon = {
                    Icon(Icons.Filled.Delete, null)
                }
            )
            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    title = { Text(stringResource(R.string.clear_database_confirm_title)) },
                    text = { Text(stringResource(R.string.clear_database_confirm_message, userDatabaseEntries.size)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearConfirmDialog = false
                                coroutineScope.launch {
                                    preferencesViewModel.clearUserDatabase()
                                    showClearedDialog = true
                                }
                            }
                        ) {
                            Text(stringResource(R.string.clear_user_database))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
            if (showClearedDialog) {
                AlertDialog(
                    onDismissRequest = { showClearedDialog = false },
                    title = { Text(stringResource(R.string.clear_user_database)) },
                    text = { Text(stringResource(R.string.user_database_cleared)) },
                    confirmButton = {
                        TextButton(onClick = { showClearedDialog = false }) {
                            Text(stringResource(R.string.continue_to_app))
                        }
                    }
                )
            }
            SettingsItem(
                name = stringResource(R.string.share_all_verification_info),
                description = stringResource(R.string.share_all_verification_info_description),
                hasIcon = true,
                onClickIconSetting = {
                    val packageManager = context.packageManager
                    val userInstalledPackages = packageManager.getInstalledPackages(0)
                        .filter { (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }
                    val text = userInstalledPackages.joinToString("\n\n") { pkg ->
                        val packageInfo = packageManager.getPackageInfo(
                            pkg.packageName,
                            PackageManager.GET_SIGNING_CERTIFICATES
                        )
                        val signingInfo = packageInfo.signingInfo!!
                        val signatures = if (signingInfo.hasMultipleSigners()) {
                            signingInfo.apkContentsSigners
                        } else {
                            signingInfo.signingCertificateHistory
                        }
                        val hashStrings = signatures.map { signature ->
                            java.security.MessageDigest
                                .getInstance("SHA-256")
                                .digest(signature.toByteArray())
                                .joinToString(":") { "%02x".format(it) }
                                .uppercase()
                        }
                        "${pkg.packageName}\n${hashStrings.joinToString("\n")}"
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    startActivity(context, Intent.createChooser(sendIntent, null), ActivityOptions.makeBasic().toBundle())
                },
                icon = {
                    Icon(Icons.Filled.Share, null)
                }
            )
        }

        Column {
            SettingsCategoryText(category = stringResource(id = R.string.about))
            SettingsItem(
                name = stringResource(id = R.string.view_source_code_setting_name),
                description = stringResource(id = R.string.view_source_code_setting_description),
                hasIcon = true,
                onClickIconSetting = {
                    runCatching {
                        localUriHandler.openUri("https://github.com/RoundSalmon4/AppVerifierBG")
                    }.onFailure { _ ->
                        Toast.makeText(context, R.string.no_browser_available, Toast.LENGTH_SHORT).show()
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null
                    )
                }
            )
            SettingsItem(
                name = stringResource(id = R.string.license_setting_name),
                description = stringResource(id = R.string.license_setting_description),
                hasIcon = true,
                onClickIconSetting = { onLicenseIconButtonClicked() },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null
                    )
                }
            )
            SettingsItem(
                name = stringResource(id = R.string.credits_setting_name),
                description = stringResource(id = R.string.credits_setting_description),
                hasIcon = true,
                onClickIconSetting = { onCreditsIconButtonClicked() },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null
                    )
                }
            )
        }

        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
    }
    }
}

@Composable
fun SettingsItem(
    name: String,
    description: String,
    hasSwitch: Boolean = false,
    hasIcon: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    checked: Boolean = false,
    onClickIconSetting: () -> Unit = {},
    icon: @Composable () -> Unit = {},
) {
    ListItem(
        modifier = when {
            hasIcon -> Modifier.clickable(onClick = { onClickIconSetting() })
            hasSwitch -> Modifier.toggleable(
                value = checked,
                onValueChange = { onCheckedChange(it) }
            )

            else -> Modifier
        },
        headlineContent = {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = { Text(text = description) },
        trailingContent = {
            when {
                hasIcon -> icon()
                hasSwitch -> Switch(
                    checked = checked,
                    onCheckedChange = null,
                )
            }
        }
    )
}

@Composable
fun SettingsCategoryText(category: String) {
    Text(
        text = category,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(8.dp),
        style = typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )
}