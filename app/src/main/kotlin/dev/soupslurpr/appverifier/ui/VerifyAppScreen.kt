package dev.soupslurpr.appverifier.ui

import android.app.ActivityOptions
import android.content.ClipData
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.typography
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
import dev.soupslurpr.appverifier.data.SimpleVerificationStatus
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.VerificationStatus

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

    var showMoreInfoAboutInternalDatabaseStatusDialog by rememberSaveable { mutableStateOf(false) }

    var showMoreInfoAboutUserDatabaseStatusDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hashes.hashes.isEmpty()) {
            onLaunchedEffectHashEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(verticalScroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (apkFailedToParse) {
            Text("APK FAILED TO PARSE")
            Text(
                "Make sure you provided a valid apk file."
            )
        } else if (appNotFound) {
            Text("APP NOT INSTALLED OR INVALID FORMAT")
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
        } else if (invalidHashFormat) {
            Text("INVALID HASH FORMAT")
            Text(
                "The provided verification info does not contain a valid SHA-256 hash. " +
                        "A valid hash is 64 hexadecimal characters or 95 characters in " +
                        "XX:XX:XX:... format."
            )
        } else {
            val showInternal = databaseStatusDisplayMode != DatabaseStatusDisplayMode.USER_ONLY
            val showUser = databaseStatusDisplayMode != DatabaseStatusDisplayMode.INTERNAL_ONLY
            if (showInternal || showUser) {
            Text("Database Status:", style = typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (showInternal) {
                Row(
                    modifier = Modifier.clickable { showMoreInfoAboutInternalDatabaseStatusDialog = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        "More info about internal database status",
                        tint = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Internal: ${internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.name.replace('_', ' ')}",
                        style = typography.titleLarge,
                    )
                }
            }
            if (showUser) {
                val userStatusText = if (userDbEntry != null) {
                    if (userDbMatch) "MATCH" else "NOMATCH"
                } else {
                    "NOT FOUND"
                }
                val userStatusColor = if (userDbEntry != null) {
                    if (userDbMatch) Color(0xFF9C27B0) else SimpleVerificationStatus.FAILURE.color
                } else {
                    Color.Gray
                }
                Row(
                    modifier = Modifier.clickable { showMoreInfoAboutUserDatabaseStatusDialog = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        "More info about user database status",
                        tint = userStatusColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "User: $userStatusText",
                        style = typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            }
            if (icon != null) {
                Image(
                    rememberDrawablePainter(drawable = icon),
                    null,
                    Modifier.size(150.dp),
                )
            }
            Text(
                text = name,
                style = typography.titleLarge
            )
            Text(text = packageName)
            if (expectedHashes.isNotEmpty() && (verificationStatus.simpleVerificationStatus == SimpleVerificationStatus.FAILURE || verificationStatus.simpleVerificationStatus == SimpleVerificationStatus.WARNING)) {
                Text("Expected:", fontWeight = FontWeight.Bold)
                Text(
                    text = expectedHashes.joinToString("\n"),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                Text("Found:", fontWeight = FontWeight.Bold)
                Text(
                    text = hashes.hashes.joinToString("\n"),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = hashes.hashes.joinToString("\n"),
                    fontFamily = FontFamily.Monospace
                )
            }
            if (showHasMultipleSigners) {
                Text(
                    "hasMultipleSigners: "
                )
                Text(
                    hashes.hasMultipleSigners.toString(),
                    fontWeight = FontWeight.Black
                )
            }
            if (hashes.isDebug) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        "Debug certificate",
                        tint = SimpleVerificationStatus.FAILURE.color,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "DEBUG",
                        color = SimpleVerificationStatus.FAILURE.color,
                        fontWeight = FontWeight.Black,
                        style = typography.titleLarge,
                    )
                }
                Text(
                    "This app is signed with a debug certificate and may not be genuine.",
                    color = SimpleVerificationStatus.FAILURE.color,
                )
            }
            val verificationData = "$packageName\n${hashes.hashes.joinToString("\n")}"
            val mimeType = "text/plain"
            Button(onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, verificationData)
                    type = mimeType
                }

                val shareIntent = Intent.createChooser(
                    sendIntent,
                    null,
                )

                startActivity(context, shareIntent, ActivityOptions.makeBasic().toBundle())
            }) {
                Text("Share Verification Info")
            }
            Button(onClick = {
                val clip: ClipData = ClipData.newPlainText(mimeType, verificationData)
                clipboardManager.setClip(ClipEntry(clip));
            }) {
                Text("Copy Verification Info")
            }
            Button(onClick = {
                if (clipboardManager.hasText()) {
                    onVerifyFromClipboard(clipboardManager.getText()!!.text)
                } else {
                    showClipboardEmptyMessage()
                }
            }) {
                Text("Verify from clipboard")
            }
            if (databaseStatusDisplayMode != DatabaseStatusDisplayMode.INTERNAL_ONLY) {
                Button(onClick = onAddToUserDatabase) {
                    Text("Add to user database")
                }
            }
            val displayVerificationStatus = if (isSelfVerification) {
                "SKIPPED"
            } else if (sharedTextHashMatch != null) {
                if (sharedTextHashMatch) "MATCH" else "NOMATCH"
            } else {
                verificationStatus.simpleVerificationStatus.name
            }
            val displayVerificationColor = if (isSelfVerification) {
                Color.Gray
            } else if (sharedTextHashMatch != null) {
                if (sharedTextHashMatch) Color(0xFFFF9800) else SimpleVerificationStatus.FAILURE.color
            } else {
                verificationStatus.simpleVerificationStatus.color
            }
            Text(
                "Verification Status:",
            )
            Row {
                FilledTonalButton(
                    onClick = { showMoreInfoAboutVerificationStatusDialog = true },
                ) {
                    Text(
                        displayVerificationStatus,
                        style = typography.headlineLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Info,
                        "More info about verification status",
                        tint = displayVerificationColor,
                    )
                }
            }
        }

        Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
    }

    if (showMoreInfoAboutInternalDatabaseStatusDialog) {
        AlertDialog(
            onDismissRequest = { showMoreInfoAboutInternalDatabaseStatusDialog = false },
            confirmButton = {
                TextButton(
                    { showMoreInfoAboutInternalDatabaseStatusDialog = false }
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
                        internalDatabaseInfo.internalDatabaseStatus.name,
                        style = typography.headlineSmall,
                        color = internalDatabaseInfo.internalDatabaseStatus.simpleInternalDatabaseStatus.color,
                    )
                }
            },
            text = {
                LazyColumn {
                    item {
                        Text(internalDatabaseInfo.internalDatabaseStatus.info)
                    }
                    item {
                        if (internalDatabaseInfo.internalDatabaseStatus == InternalDatabaseStatus.MATCH) {
                            Text("\nThe matched database entry for this app is from the following sources:\n")
                            Text(
                                text = internalDatabaseInfo.sources.joinToString("\n") { it.displayName },
                                style = typography.headlineSmall,
                            )
                            Text(
                                "\nThis information can be useful if you distrust a specific source and want to make" +
                                        " sure the app isn't from them."
                            )
                        }
                    }
                }
            }
        )
    }

    if (showMoreInfoAboutUserDatabaseStatusDialog) {
        val userDialogTitle = if (userDbEntry != null) {
            if (userDbMatch) "MATCH" else "NOMATCH"
        } else {
            "NOT FOUND"
        }
        val userDialogColor = if (userDbEntry != null) {
            if (userDbMatch) Color(0xFF9C27B0) else SimpleVerificationStatus.FAILURE.color
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
            if (sharedTextHashMatch) "MATCH" else "NOMATCH"
        } else {
            verificationStatus.name
        }
        val dialogColor = if (isSelfVerification) {
            Color.Gray
        } else if (sharedTextHashMatch != null) {
            if (sharedTextHashMatch) Color(0xFFFF9800) else SimpleVerificationStatus.FAILURE.color
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
}