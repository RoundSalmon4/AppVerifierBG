package dev.soupslurpr.appverifier.ui

import android.app.ActivityOptions
import android.content.ClipData
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode
import dev.soupslurpr.appverifier.data.Hashes
import dev.soupslurpr.appverifier.data.InternalDatabaseInfo
import dev.soupslurpr.appverifier.data.InternalDatabaseStatus
import dev.soupslurpr.appverifier.data.SimpleInternalDatabaseStatus
import dev.soupslurpr.appverifier.data.SimpleVerificationStatus
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.VerificationStatus
import dev.soupslurpr.appverifier.ui.theme.Gold80
import dev.soupslurpr.appverifier.ui.theme.UserDbPurple
import dev.soupslurpr.appverifier.ui.theme.WarningOrange

@Composable
fun VerifyAppScreen(
    icon: Drawable?,
    name: String,
    packageName: String,
    hashes: Hashes,
    verificationStatus: VerificationStatus,
    appNotFound: Boolean,
    invalidHashFormat: Boolean,
    expectedHashes: List<String>,
    onVerifyFromClipboard: (String) -> Unit,
    onLaunchedEffectHashEmpty: () -> Unit,
    internalDatabaseInfo: InternalDatabaseInfo,
    apkFailedToParse: Boolean,
    showHasMultipleSigners: Boolean,
    showClipboardEmptyMessage: () -> Unit,
    databaseStatusDisplayMode: DatabaseStatusDisplayMode = DatabaseStatusDisplayMode.BOTH,
    userDbEntry: UserDatabaseEntry? = null,
    userDbMatch: Boolean = false,
    onAddToUserDatabase: () -> Unit = {},
    sharedTextHashMatch: Boolean? = null,
) {
    val context = LocalContext.current
    val isSelfVerification = packageName == context.packageName

    val clipboardManager = LocalClipboardManager.current

    val verticalScroll = rememberScrollState()

    var showMoreInfoAboutVerificationStatusDialog by rememberSaveable { mutableStateOf(false) }

    var showMoreInfoAboutHashStatusDialog by rememberSaveable { mutableStateOf(false) }

    var showMoreInfoAboutDomainStatusDialog by rememberSaveable { mutableStateOf(false) }

    var showMoreInfoAboutUserDatabaseStatusDialog by rememberSaveable { mutableStateOf(false) }

    var showMoreInfoAboutDebugDialog by rememberSaveable { mutableStateOf(false) }

    var showHashComparison by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hashes.hashes.isEmpty()) {
            onLaunchedEffectHashEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(verticalScroll),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (apkFailedToParse) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("APK FAILED TO PARSE", style = typography.titleMedium)
                    Text("Make sure you provided a valid apk file.")
                }
            }
        } else if (appNotFound) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("APP NOT INSTALLED OR INVALID FORMAT", style = typography.titleMedium)
                    Text(
                        "The package name doesn't seem to correspond to any installed user app." +
                                "\nPlease note system apps are not included in the search."
                    )
                    Text(
                        "Also please make sure the provided text is in the correct format, like the " +
                                "following:\n\ncom.example" +
                                ".app\n96:C0:2C:55:75:5C:17:1C:68:13:70:29:3B:37:11:2B:4A:5D:F7:B9:82:C2:C5:58:05:4C:45:51:AD:F5:50:DC" +
                                "\n\nThere may be multiple hashes, which is normal."
                    )
                }
            }
        } else if (invalidHashFormat) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("INVALID HASH FORMAT", style = typography.titleMedium)
                    Text(
                        "The provided verification info does not contain a valid SHA-256 hash. " +
                                "A valid hash is 64 hexadecimal characters or 95 characters in " +
                                "XX:XX:XX:... format."
                    )
                }
            }
        } else {
            val showInternal = databaseStatusDisplayMode != DatabaseStatusDisplayMode.USER_ONLY
            val showUser = databaseStatusDisplayMode != DatabaseStatusDisplayMode.INTERNAL_ONLY

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showInternal || showUser || hashes.isDebug || (showHasMultipleSigners && hashes.hasMultipleSigners)) {
                        Text("Database Status:", style = typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (showInternal) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SuggestionChip(
                                    onClick = { showMoreInfoAboutHashStatusDialog = true },
                                    label = {
                                        Text("Hash: ${internalDatabaseInfo.internalDatabaseStatus.name.replace('_', ' ').replace("NOMATCH", "NO MATCH")}")
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color.copy(alpha = 0.12f),
                                        labelColor = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                                        iconContentColor = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                                    ),
                                )
                                if (internalDatabaseInfo.domainSources.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    val domainStatusText = internalDatabaseInfo.internalDatabaseStatus.name.replace('_', ' ').replace("NOMATCH", "NO MATCH")
                                    SuggestionChip(
                                        onClick = { showMoreInfoAboutDomainStatusDialog = true },
                                        label = { Text("Domain: $domainStatusText") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color.copy(alpha = 0.12f),
                                            labelColor = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                                            iconContentColor = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                                        ),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (showUser) {
                            val userStatusText = if (userDbEntry != null) {
                                if (userDbMatch) "MATCH" else "NO MATCH"
                            } else {
                                "NOT FOUND"
                            }
                            val userStatusColor = if (userDbEntry != null) {
                                if (userDbMatch) SimpleInternalDatabaseStatus.SUCCESS.color else SimpleInternalDatabaseStatus.FAILURE.color
                            } else {
                                SimpleInternalDatabaseStatus.NOT_FOUND.color
                            }
                            SuggestionChip(
                                onClick = { showMoreInfoAboutUserDatabaseStatusDialog = true },
                                label = { Text("User: $userStatusText") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = userStatusColor.copy(alpha = 0.12f),
                                    labelColor = userStatusColor,
                                    iconContentColor = userStatusColor,
                                ),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (hashes.isDebug) {
                            SuggestionChip(
                                onClick = { showMoreInfoAboutDebugDialog = true },
                                icon = {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = { Text("DEBUG") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = WarningOrange.copy(alpha = 0.12f),
                                    labelColor = WarningOrange,
                                    iconContentColor = WarningOrange,
                                ),
                            )
                        }
                        if (showHasMultipleSigners && hashes.hasMultipleSigners) {
                            Spacer(Modifier.height(4.dp))
                            SuggestionChip(
                                onClick = { },
                                icon = {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = { Text("MULTI-SIGNER") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    labelColor = MaterialTheme.colorScheme.primary,
                                    iconContentColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (icon != null) {
                        Image(
                            rememberDrawablePainter(drawable = icon),
                            null,
                            Modifier.size(80.dp),
                        )
                    }
                    Text(name, style = typography.titleLarge)
                    Text(packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expectedHashes.isNotEmpty() && (verificationStatus.simpleVerificationStatus == SimpleVerificationStatus.FAILURE || verificationStatus.simpleVerificationStatus == SimpleVerificationStatus.WARNING)) {
                        Spacer(Modifier.height(8.dp))
                        SuggestionChip(
                            onClick = { showHashComparison = !showHashComparison },
                            label = { Text(if (showHashComparison) "Hide hash comparison" else "Show hash comparison") },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                labelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        if (showHashComparison) {
                            Text("Expected:", style = typography.titleMedium)
                            Text(
                                text = expectedHashes.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                style = typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Found:", style = typography.titleMedium)
                            Text(
                                text = hashes.hashes.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                style = typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            text = hashes.hashes.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            style = typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    val displayVerificationStatus = if (isSelfVerification) {
                        "SKIPPED"
                    } else if (sharedTextHashMatch != null) {
                        if (sharedTextHashMatch) "MATCH" else "NO MATCH"
                    } else if (verificationStatus == VerificationStatus.UNKNOWN) {
                        "NONE"
                    } else {
                        verificationStatus.simpleVerificationStatus.name
                    }
                    val displayVerificationColor = if (isSelfVerification) {
                        Color.Gray
                    } else if (sharedTextHashMatch != null) {
                        if (sharedTextHashMatch) WarningOrange else SimpleVerificationStatus.FAILURE.color
                    } else {
                        verificationStatus.simpleVerificationStatus.color
                    }
                    Text("Text Match:", style = typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = { showMoreInfoAboutVerificationStatusDialog = true },
                        label = { Text(displayVerificationStatus) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = displayVerificationColor.copy(alpha = 0.12f),
                            labelColor = displayVerificationColor,
                            iconContentColor = displayVerificationColor,
                        ),
                        icon = {
                            Icon(
                                Icons.Filled.Info,
                                "More info about verification status",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val verificationData = "$packageName\n${hashes.hashes.joinToString("\n")}"
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, verificationData)
                                    type = "text/plain"
                                }
                                startActivity(context, Intent.createChooser(sendIntent, null), ActivityOptions.makeBasic().toBundle())
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Share Verification Info", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Send this app's verification data to another app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val verificationData = "$packageName\n${hashes.hashes.joinToString("\n")}"
                                val clip: ClipData = ClipData.newPlainText("text/plain", verificationData)
                                clipboardManager.setClip(ClipEntry(clip))
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Copy Verification Info", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Copy this app's verification data to the clipboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (clipboardManager.hasText()) {
                                    onVerifyFromClipboard(clipboardManager.getText()!!.text)
                                } else {
                                    showClipboardEmptyMessage()
                                }
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Verify from clipboard", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Paste app verification info from your clipboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (databaseStatusDisplayMode != DatabaseStatusDisplayMode.INTERNAL_ONLY) {
                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onAddToUserDatabase)
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Add to user database", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Save this app's hashes to your personal database",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
    }

    if (showMoreInfoAboutHashStatusDialog) {
        val hashInfoText = when (internalDatabaseInfo.internalDatabaseStatus) {
            InternalDatabaseStatus.MATCH -> "This app's signing certificate hash matches an entry in the internal database. You don't need to verify normally."
            InternalDatabaseStatus.NOMATCH -> "Found in the database, but the hash does not match. The app may be tampered with or has a new signing key."
            InternalDatabaseStatus.NOT_FOUND -> "This app was not found in the internal database. This isn't anything to worry about, but please verify the app normally."
        }
        AlertDialog(
            onDismissRequest = { showMoreInfoAboutHashStatusDialog = false },
            confirmButton = {
                TextButton(
                    { showMoreInfoAboutHashStatusDialog = false }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "HASH ${internalDatabaseInfo.internalDatabaseStatus.name.replace('_', ' ').replace("NOMATCH", "NO MATCH")}",
                        style = typography.headlineSmall,
                        color = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                    )
                }
            },
            text = {
                LazyColumn {
                    item {
                        Text(hashInfoText)
                    }
                    item {
                        if (internalDatabaseInfo.internalDatabaseStatus == InternalDatabaseStatus.MATCH && internalDatabaseInfo.hashSources.isNotEmpty()) {
                            Text("\nThe matched hash entry for this app is from the following sources:\n")
                            Text(
                                text = internalDatabaseInfo.hashSources.joinToString("\n") { it.displayName },
                                style = typography.bodyMedium,
                                color = Gold80,
                            )
                            Text(
                                "\nThis information can be useful if you distrust a specific source and want to make" +
                                        " sure the app isn't from them."
                            )
                            if (internalDatabaseInfo.isSubsetMatch) {
                                Text(
                                    "\nNote: This app has fewer signing certificates than the database knows" +
                                            " about. This is normal for signature rotation or older versions.",
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    if (showMoreInfoAboutDomainStatusDialog) {
        val domainSegments = packageName.split(".")
        val domain = if (domainSegments.size >= 2) {
            "${domainSegments[1]}.${domainSegments[0]}"
        } else {
            null
        }
        val domainInfoText = when (internalDatabaseInfo.internalDatabaseStatus) {
            InternalDatabaseStatus.MATCH -> "This app's signing certificate hash matches against a domain-verified source in the internal database. You don't need to verify normally."
            InternalDatabaseStatus.NOMATCH -> "Found via domain verification, but the hash does not match. The app may be tampered with or has a new signing key."
            InternalDatabaseStatus.NOT_FOUND -> "This app was not found in the internal database. This isn't anything to worry about, but please verify the app normally."
        }
        AlertDialog(
            onDismissRequest = { showMoreInfoAboutDomainStatusDialog = false },
            confirmButton = {
                TextButton(
                    { showMoreInfoAboutDomainStatusDialog = false }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "DOMAIN ${internalDatabaseInfo.internalDatabaseStatus.name.replace('_', ' ').replace("NOMATCH", "NO MATCH")}",
                        style = typography.headlineSmall,
                        color = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                    )
                }
            },
            text = {
                LazyColumn {
                    item {
                        Text(domainInfoText)
                    }
                    item {
                        if (internalDatabaseInfo.internalDatabaseStatus == InternalDatabaseStatus.MATCH && domain != null) {
                            Text("\nThe matched hash entry for this app was verified against the following domain:\n")
                            Text(
                                text = domain,
                                style = typography.bodyMedium,
                                color = Gold80,
                            )
                        }
                    }
                }
            }
        )
    }

    if (showMoreInfoAboutUserDatabaseStatusDialog) {
        val userDialogTitle = if (userDbEntry != null) {
            if (userDbMatch) "MATCH" else "NO MATCH"
        } else {
            "NOT FOUND"
        }
        val userDialogColor = if (userDbEntry != null) {
            if (userDbMatch) UserDbPurple else SimpleVerificationStatus.FAILURE.color
        } else {
            Color.Gray
        }
        AlertDialog(
            onDismissRequest = { showMoreInfoAboutUserDatabaseStatusDialog = false },
            confirmButton = {
                TextButton(
                    { showMoreInfoAboutUserDatabaseStatusDialog = false }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        userDialogTitle,
                        style = typography.headlineSmall,
                        color = userDialogColor,
                    )
                }
            },
            text = {
                LazyColumn {
                    item {
                        Text("Package Name: ${userDbEntry?.packageName ?: packageName}")
                    }
                    item {
                        if (userDbEntry != null) {
                            Text("\nStored hashes:")
                            Text(
                                text = userDbEntry.hashes.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    item {
                        if (userDbEntry == null) {
                            Text("\nThis app has no entry in the user database.")
                        } else if (userDbMatch) {
                            Text("\nThe current app hashes match the user database entry.")
                        } else {
                            Text("\nThe current app hashes do NOT match the user database entry.")
                        }
                    }
                }
            }
        )
    }

    if (showMoreInfoAboutVerificationStatusDialog) {
        val dialogTitle = if (isSelfVerification) {
            "SKIPPED"
        } else if (sharedTextHashMatch != null) {
            if (sharedTextHashMatch) "MATCH" else "NO MATCH"
        } else {
            if (verificationStatus == VerificationStatus.UNKNOWN) "NONE" else verificationStatus.name.replace('_', ' ').replace("NOMATCH", "NO MATCH")
        }
        val dialogColor = if (isSelfVerification) {
            Color.Gray
        } else if (sharedTextHashMatch != null) {
            if (sharedTextHashMatch) WarningOrange else SimpleVerificationStatus.FAILURE.color
        } else {
            verificationStatus.simpleVerificationStatus.color
        }
        val dialogInfo = if (isSelfVerification) {
            "Self-verification skipped: you cannot verify this app using itself."
        } else if (sharedTextHashMatch != null) {
            if (sharedTextHashMatch) {
                "The app's hashes match the shared text's expected values."
            } else {
                "The app's hashes do NOT match the shared text's expected values."
            }
        } else {
            verificationStatus.info
        }
        AlertDialog(
            onDismissRequest = { showMoreInfoAboutVerificationStatusDialog = false },
            confirmButton = {
                TextButton(
                    { showMoreInfoAboutVerificationStatusDialog = false }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        dialogTitle,
                        style = typography.headlineSmall,
                        color = dialogColor,
                    )
                }
            },
            text = {
                LazyColumn {
                    item {
                        Text(dialogInfo)
                    }
                }
            }
        )
    }

    if (showMoreInfoAboutDebugDialog) {
        AlertDialog(
            onDismissRequest = { showMoreInfoAboutDebugDialog = false },
            confirmButton = {
                TextButton(
                    { showMoreInfoAboutDebugDialog = false }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = WarningOrange,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "DEBUG",
                        style = typography.headlineSmall,
                        color = WarningOrange,
                    )
                }
            },
            text = {
                Text("This app is signed with a debug certificate and may not be genuine.")
            },
        )
    }
}
