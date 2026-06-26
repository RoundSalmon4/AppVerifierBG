package dev.soupslurpr.appverifier

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soupslurpr.appverifier.data.SimpleVerificationStatus
import dev.soupslurpr.appverifier.ui.AppHashMatch
import dev.soupslurpr.appverifier.ui.VerifyAppViewModel.VerifyAppViewModelFactory
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.parseUserDatabaseEntriesFromAny
import dev.soupslurpr.appverifier.preferences.PreferencesViewModel

import dev.soupslurpr.appverifier.ui.VerifyAppViewModel
import dev.soupslurpr.appverifier.ui.theme.AppVerifierTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

class MainActivity : ComponentActivity() {

    private val newIntentFlow = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val verifyAppViewModel: VerifyAppViewModel = viewModel(
                factory = VerifyAppViewModelFactory(application)
            )

            val preferencesViewModel: PreferencesViewModel = viewModel(
                factory = PreferencesViewModel.PreferencesViewModelFactory(dataStore)
            )

            val preferencesUiState by preferencesViewModel.uiState.collectAsState()

            val coroutineScope = rememberCoroutineScope()

            verifyAppViewModel.onVerificationResult = { status ->
                if (status.simpleVerificationStatus == SimpleVerificationStatus.SUCCESS
                    && preferencesUiState.showClipboardCheckmark
                ) {
                    coroutineScope.launch {
                        preferencesViewModel.addClipboardVerifiedPackage(
                            verifyAppViewModel.uiState.value.packageName
                        )
                    }
                }
            }

            val isActionSend =
                (intent.action == Intent.ACTION_SEND)

            val isActionView =
                (intent.action == Intent.ACTION_VIEW)

            var sharedFilteredEntries by remember { mutableStateOf<List<UserDatabaseEntry>?>(null) }
            var hashMatchCandidates by remember { mutableStateOf<List<AppHashMatch>?>(null) }
            var hashMatchText by remember { mutableStateOf<String?>(null) }

            if (isActionSend) {
                val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val extraStream: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

                val sharedText = when {
                    extraText != null -> extraText
                    extraStream != null && intent.type?.startsWith("text/") == true -> {
                        contentResolver.openInputStream(extraStream)?.bufferedReader()?.use { it.readText() }
                    }
                    else -> null
                }

                if (sharedText != null) {
                    val trimmed = sharedText.trim()
                    val entries = trimmed.split("\n\n").filter { it.isNotBlank() }
                    if (entries.size > 1) {
                        val parsed = parseUserDatabaseEntriesFromAny(trimmed)
                        if (parsed.entries.isNotEmpty()) {
                            sharedFilteredEntries = parsed.entries
                        } else {
                            val packageNames = entries.mapNotNull { entry ->
                                entry.lines().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                            }
                            if (packageNames.isNotEmpty()) {
                                sharedFilteredEntries = packageNames.map { UserDatabaseEntry(it, emptyList(), false) }
                            }
                        }
                    } else {
                        val verificationInfoText = verifyAppViewModel.getVerificationInfoText(sharedText)
                        val lines = verificationInfoText.lines().filter { it.isNotBlank() }
                        val firstLine = lines.firstOrNull()

                        if (firstLine != null && verifyAppViewModel.findAndSetAppVerificationInfoFromPackageName(
                                firstLine,
                                packageManager
                            )
                        ) {
                            verifyAppViewModel.verifyFromText(verificationInfoText)
                        } else if (lines.isNotEmpty() && lines.all { verifyAppViewModel.isValidSha256Hash(it.trim()) }) {
                            val matches = verifyAppViewModel.findAppsByHash(lines, packageManager)
                            when (matches.size) {
                                0 -> {
                                    if (lines.size > 1) {
                                        verifyAppViewModel.setMultipleHashesWithoutPackageName(true)
                                    } else {
                                        verifyAppViewModel.setAppNotFoundOrInvalidFormat(true)
                                    }
                                }
                                1 -> {
                                    val match = matches[0]
                                    verifyAppViewModel.setAppVerificationInfo(
                                        match.name, match.packageName, match.hashes, match.internalDatabaseInfo
                                    )
                                    verifyAppViewModel.verifyFromText(verificationInfoText)
                                }
                                else -> {
                                    hashMatchCandidates = matches
                                    hashMatchText = verificationInfoText
                                }
                            }
                        } else {
                            verifyAppViewModel.setAppNotFoundOrInvalidFormat(true)
                        }
                    }
                } else if (extraStream != null) {
                    LaunchedEffect(extraStream) {
                        verifyAppViewModel.setApkVerificationInfoAndInternalDatabaseStatusFromUri(
                            contentResolver,
                            extraStream,
                            packageManager
                        )
                    }
                }
            } else if (isActionView) {
                val uri = intent.data
                if (uri != null) {
                    LaunchedEffect(uri) {
                        verifyAppViewModel.setApkVerificationInfoAndInternalDatabaseStatusFromUri(
                            contentResolver,
                            uri,
                            packageManager
                        )
                    }
                }
            }

            AppVerifierTheme {
                AppVerifierApp(
                    modifier = Modifier,
                    verifyAppViewModel = verifyAppViewModel,
                    preferencesViewModel = preferencesViewModel,
                    isActionSend = isActionSend,
                    isActionView = isActionView,
                    sharedFilteredEntries = sharedFilteredEntries,
                    hashMatchData = if (hashMatchCandidates != null && hashMatchText != null) {
                        HashMatchData(hashMatchCandidates!!, hashMatchText!!)
                    } else {
                        null
                    },
                    newIntentFlow = newIntentFlow,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntentFlow.tryEmit(intent)
    }
}