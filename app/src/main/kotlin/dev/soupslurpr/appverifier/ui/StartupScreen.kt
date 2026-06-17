package dev.soupslurpr.appverifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.foundation.Image
import dev.soupslurpr.appverifier.R
import dev.soupslurpr.appverifier.data.ImportSummary
import dev.soupslurpr.appverifier.data.parseUserDatabaseEntriesFromAny
import dev.soupslurpr.appverifier.preferences.PreferencesViewModel
import kotlinx.coroutines.launch

private sealed interface ImportDialogState {
    data object Idle : ImportDialogState
    data class CombineOrReplace(val json: String) : ImportDialogState
    data class Summary(val summary: ImportSummary) : ImportDialogState
    data object ParseError : ImportDialogState
}

@Composable
fun StartupScreen(
    modifier: Modifier,
    onSettingsButtonClicked: () -> Unit,
    onPrivacyPolicyButtonClicked: () -> Unit,
    onAppListButtonClicked: () -> Unit,
    onVerifyApkFileButtonClicked: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    preferencesViewModel: PreferencesViewModel,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val icon = remember { packageManager.getApplicationIcon(context.packageName) }
    val coroutineScope = rememberCoroutineScope()

    var installedPackages by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            installedPackages = packageManager.getInstalledPackages(0)
                .map { it.packageName }
                .toSet()
        }
    }

    var importDialogState by remember { mutableStateOf<ImportDialogState>(ImportDialogState.Idle) }
    var pendingReportText by remember { mutableStateOf("") }

    val reportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            @Suppress("CoroutineCreationDuringComposition")
            coroutineScope.launch {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingReportText.toByteArray())
                }
            }
        }
        importDialogState = ImportDialogState.Idle
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            @Suppress("CoroutineCreationDuringComposition")
            coroutineScope.launch {
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType == null || !(mimeType.startsWith("text/") || mimeType == "application/json")) {
                    importDialogState = ImportDialogState.ParseError
                    return@launch
                }
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
                if (content != null) {
                    val parsed = parseUserDatabaseEntriesFromAny(content)
                    if (parsed.entries.isNotEmpty()) {
                        importDialogState = ImportDialogState.CombineOrReplace(content)
                    } else {
                        importDialogState = ImportDialogState.ParseError
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.padding(top = 8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.app_list),
                    description = stringResource(R.string.startup_app_list_description),
                    onClick = onAppListButtonClicked,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.ContentPaste,
                    title = stringResource(R.string.startup_paste_title),
                    description = stringResource(R.string.startup_paste_description),
                    onClick = onPasteFromClipboard,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.FileOpen,
                    title = stringResource(R.string.startup_verify_apk_title),
                    description = stringResource(R.string.startup_verify_apk_description),
                    onClick = onVerifyApkFileButtonClicked,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.FileDownload,
                    title = stringResource(R.string.import_user_database),
                    description = stringResource(R.string.import_user_database_description),
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.settings),
                    description = stringResource(R.string.startup_settings_description),
                    onClick = onSettingsButtonClicked,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.startup_privacy_policy_title),
                    description = stringResource(R.string.startup_privacy_policy_description),
                    onClick = onPrivacyPolicyButtonClicked,
                )
            }
        }

        Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
    }

    when (val state = importDialogState) {
        is ImportDialogState.Idle -> {}
        is ImportDialogState.CombineOrReplace -> {
            AlertDialog(
                onDismissRequest = { importDialogState = ImportDialogState.Idle },
                title = { Text(stringResource(R.string.import_title)) },
                text = { Text(stringResource(R.string.import_how_to)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val summary = preferencesViewModel.importUserDatabase(state.json, replace = false, installedPackages)
                                importDialogState = ImportDialogState.Summary(summary)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.import_combine))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val summary = preferencesViewModel.importUserDatabase(state.json, replace = true, installedPackages)
                                importDialogState = ImportDialogState.Summary(summary)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.import_replace))
                    }
                }
            )
        }
        is ImportDialogState.ParseError -> {
            AlertDialog(
                onDismissRequest = { importDialogState = ImportDialogState.Idle },
                title = { Text(stringResource(R.string.import_invalid_format_title)) },
                text = { Text(stringResource(R.string.format_not_recognized)) },
                confirmButton = {
                    TextButton(onClick = { importDialogState = ImportDialogState.Idle }) {
                        Text(stringResource(R.string.continue_to_app))
                    }
                }
            )
        }
        is ImportDialogState.Summary -> {
            AlertDialog(
                onDismissRequest = { importDialogState = ImportDialogState.Idle },
                title = { Text(stringResource(R.string.import_complete)) },
                text = {
                    val couldNotBeRead = stringResource(R.string.could_not_be_read, state.summary.skippedLines.size)
                    val msg = buildString {
                        if (state.summary.totalEntries == 0) {
                            append(stringResource(R.string.format_not_recognized))
                        } else {
                            append("${state.summary.totalEntries} entries imported.")
                            val parts = mutableListOf<String>()
                            if (state.summary.verifiedCount > 0) parts.add("${state.summary.verifiedCount} apps verified")
                            if (state.summary.updatedCount > 0) parts.add("${state.summary.updatedCount} apps updated")
                            if (parts.isNotEmpty()) {
                                append(" ${parts.joinToString(", ")}.")
                            }
                        }
                        if (state.summary.skippedLines.isNotEmpty()) {
                            append("\n\n")
                            append(couldNotBeRead)
                        }
                    }
                    Text(msg)
                },
                confirmButton = {
                    TextButton(onClick = { importDialogState = ImportDialogState.Idle }) {
                        Text(stringResource(R.string.continue_to_app))
                    }
                },
                dismissButton = {
                    if (state.summary.skippedLines.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                pendingReportText = state.summary.skippedLines.joinToString("\n\n---\n\n")
                                reportLauncher.launch("import_errors.txt")
                            }
                        ) {
                            Text(stringResource(R.string.download_report))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
