package dev.soupslurpr.appverifier

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode
import dev.soupslurpr.appverifier.data.Hashes
import dev.soupslurpr.appverifier.data.InternalDatabaseInfo
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.parseUserDatabaseEntriesFromAny
import dev.soupslurpr.appverifier.preferences.PreferencesViewModel
import dev.soupslurpr.appverifier.ui.AppHashMatch
import dev.soupslurpr.appverifier.ui.AppIconCache
import dev.soupslurpr.appverifier.ui.AppListScreen
import dev.soupslurpr.appverifier.ui.SortMode
import dev.soupslurpr.appverifier.ui.CreditsScreen
import dev.soupslurpr.appverifier.ui.LicenseScreen
import dev.soupslurpr.appverifier.ui.PrivacyPolicyScreen
import dev.soupslurpr.appverifier.ui.SettingsScreen
import dev.soupslurpr.appverifier.ui.StartupScreen
import dev.soupslurpr.appverifier.ui.VerifyAppScreen
import dev.soupslurpr.appverifier.ui.VerifyAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HashMatchData(
    val candidates: List<AppHashMatch>,
    val hashText: String,
)

enum class AppVerifierScreens(@StringRes val title: Int) {
    Start(title = R.string.app_name),
    AppList(title = R.string.app_list),
    VerifyApp(title = R.string.verify_app),
    Settings(title = R.string.settings),
    License(title = R.string.license),
    PrivacyPolicy(title = R.string.privacy_policy),
    Credits(title = R.string.credits),
    HashPicker(title = R.string.verify_app)
}

@Composable
fun AppVerifierApp(
    modifier: Modifier,
    verifyAppViewModel: VerifyAppViewModel,
    preferencesViewModel: PreferencesViewModel,
    isActionSend: Boolean,
    isActionView: Boolean,
    sharedFilteredEntries: List<UserDatabaseEntry>? = null,
    hashMatchData: HashMatchData? = null,
    newIntentFlow: Flow<Intent> = emptyFlow(),
) {
    val preferencesUiState = preferencesViewModel.uiState.collectAsState()

    val verifyAppUiState = verifyAppViewModel.uiState.collectAsState()

    val userDatabaseEntries by preferencesViewModel.userDatabaseEntries.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarCoroutineScope = rememberCoroutineScope()

    val coroutineScope = rememberCoroutineScope()

    var filteredEntries by remember { mutableStateOf(sharedFilteredEntries) }
    var currentHashMatchData by remember { mutableStateOf(hashMatchData) }

    LaunchedEffect(sharedFilteredEntries) {
        if (sharedFilteredEntries != null) {
            filteredEntries = sharedFilteredEntries
        }
    }

    LaunchedEffect(hashMatchData) {
        if (hashMatchData != null) {
            currentHashMatchData = hashMatchData
        }
    }

    val navController = rememberNavController()

    val context = LocalContext.current

    val clipboardManager = LocalClipboardManager.current

    var pendingNavigation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { route ->
            navController.navigate(route)
            pendingNavigation = null
        }
    }

    LaunchedEffect(Unit) {
        newIntentFlow.collect { newIntent ->
            if (newIntent.action == Intent.ACTION_SEND) {
                val extraText = newIntent.getStringExtra(Intent.EXTRA_TEXT)
                val extraStream = newIntent.getParcelableExtra<Uri?>(Intent.EXTRA_STREAM)

                val sharedText = when {
                    extraText != null -> extraText
                    extraStream != null && newIntent.type?.startsWith("text/") == true -> {
                        context.contentResolver.openInputStream(extraStream)?.bufferedReader()?.use { it.readText() }
                    }
                    else -> null
                }

                if (sharedText != null) {
                    val trimmed = sharedText.trim()
                    val verificationInfoText = verifyAppViewModel.getVerificationInfoText(trimmed)
                    val lines = verificationInfoText.lines().filter { it.isNotBlank() }
                    val firstLine = lines.firstOrNull()

                    if (firstLine != null && verifyAppViewModel.findAndSetAppVerificationInfoFromPackageName(firstLine, context.packageManager)) {
                        verifyAppViewModel.verifyFromText(verificationInfoText)
                        pendingNavigation = AppVerifierScreens.VerifyApp.name
                    } else if (lines.isNotEmpty() && lines.all { verifyAppViewModel.isValidSha256Hash(it.trim()) }) {
                        val matches = verifyAppViewModel.findAppsByHash(lines, context.packageManager)
                        when (matches.size) {
                            0 -> {
                                if (lines.size > 1) {
                                    verifyAppViewModel.setMultipleHashesWithoutPackageName(true)
                                } else {
                                    verifyAppViewModel.setAppNotFoundOrInvalidFormat(true)
                                }
                                pendingNavigation = AppVerifierScreens.VerifyApp.name
                            }
                            1 -> {
                                val match = matches[0]
                                verifyAppViewModel.setAppVerificationInfo(
                                    match.name, match.packageName, match.hashes, match.internalDatabaseInfo
                                )
                                verifyAppViewModel.verifyFromText(verificationInfoText)
                                pendingNavigation = AppVerifierScreens.VerifyApp.name
                            }
                            else -> {
                                currentHashMatchData = HashMatchData(
                                    candidates = matches,
                                    hashText = verificationInfoText,
                                )
                                pendingNavigation = AppVerifierScreens.HashPicker.name
                            }
                        }
                    } else {
                        verifyAppViewModel.setAppNotFoundOrInvalidFormat(true)
                        pendingNavigation = AppVerifierScreens.VerifyApp.name
                    }
                } else if (extraStream != null) {
                    verifyAppViewModel.setApkVerificationInfoAndInternalDatabaseStatusFromUri(
                        context.contentResolver,
                        extraStream,
                        context.packageManager,
                    )
                    pendingNavigation = AppVerifierScreens.VerifyApp.name
                }
            } else if (newIntent.action == Intent.ACTION_VIEW) {
                newIntent.data?.let { uri ->
                    verifyAppViewModel.setApkVerificationInfoAndInternalDatabaseStatusFromUri(
                        context.contentResolver,
                        uri,
                        context.packageManager,
                    )
                    pendingNavigation = AppVerifierScreens.VerifyApp.name
                }
            }
        }
    }

    val openApkFileLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    verifyAppViewModel.setApkVerificationInfoAndInternalDatabaseStatusFromUri(
                        context.contentResolver,
                        uri,
                        context.packageManager,
                    )
                    pendingNavigation = AppVerifierScreens.VerifyApp.name
                }
            }
        }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
    ) { innerPadding ->
        val initialHashMatchData = remember { currentHashMatchData }
        NavHost(
            navController = navController,
            startDestination = remember(filteredEntries, initialHashMatchData, isActionSend, isActionView) {
                when {
                    filteredEntries != null -> AppVerifierScreens.AppList.name
                    initialHashMatchData != null -> AppVerifierScreens.HashPicker.name
                    isActionSend || isActionView -> AppVerifierScreens.VerifyApp.name
                    else -> AppVerifierScreens.Start.name
                }
            },
            modifier = modifier.padding(
                innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                innerPadding.calculateTopPadding(),
                innerPadding.calculateEndPadding(LocalLayoutDirection.current)
            ),
        ) {
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.Start) {
                StartupScreen(
                    modifier = modifier,
                    preferencesViewModel = preferencesViewModel,
                    onSettingsButtonClicked = {
                        navController.navigate(AppVerifierScreens.Settings.name)
                    },
                    onPrivacyPolicyButtonClicked = {
                        navController.navigate(AppVerifierScreens.PrivacyPolicy.name)
                    },
                    onAppListButtonClicked = {
                        searchQuery = ""
                        navController.navigate(AppVerifierScreens.AppList.name)
                    },
                    onVerifyApkFileButtonClicked = {
                        openApkFileLauncher.launch(arrayOf("*/*"))
                    },
                    onPasteFromClipboard = {
                        val text = clipboardManager.getText()?.text
                        if (text != null) {
                            val trimmed = text.trim()
                            val entries = trimmed.split("\n\n").filter { it.isNotBlank() }
                            if (entries.size > 1) {
                                val parsed = parseUserDatabaseEntriesFromAny(trimmed)
                                if (parsed.entries.isNotEmpty()) {
                                    filteredEntries = parsed.entries
                                    navController.navigate(AppVerifierScreens.AppList.name)
                                } else {
                                    snackbarCoroutineScope.launch {
                                        snackbarHostState.showSnackbar("Clipboard text is not in a valid format")
                                    }
                                }
                            } else {
                                val verificationInfoText = verifyAppViewModel.getVerificationInfoText(trimmed)
                                val lines = verificationInfoText.lines().filter { it.isNotBlank() }
                                val firstLine = lines.firstOrNull()

                                if (firstLine != null && verifyAppViewModel.findAndSetAppVerificationInfoFromPackageName(
                                        firstLine,
                                        context.packageManager
                                    )
                                ) {
                                    verifyAppViewModel.verifyFromText(verificationInfoText)
                                    navController.navigate(AppVerifierScreens.VerifyApp.name)
                                } else if (lines.isNotEmpty() && lines.all { verifyAppViewModel.isValidSha256Hash(it.trim()) }) {
                                    val matches = verifyAppViewModel.findAppsByHash(lines, context.packageManager)
                                    when (matches.size) {
                                         0 -> {
                                            if (lines.size > 1) {
                                                verifyAppViewModel.setMultipleHashesWithoutPackageName(true)
                                            } else {
                                                verifyAppViewModel.setAppNotFoundOrInvalidFormat(true)
                                            }
                                            navController.navigate(AppVerifierScreens.VerifyApp.name)
                                        }
                                        1 -> {
                                            val match = matches[0]
                                            verifyAppViewModel.setAppVerificationInfo(
                                                match.name, match.packageName, match.hashes, match.internalDatabaseInfo
                                            )
                                            verifyAppViewModel.verifyFromText(verificationInfoText)
                                            navController.navigate(AppVerifierScreens.VerifyApp.name)
                                        }
                                        else -> {
                                            currentHashMatchData = HashMatchData(
                                                candidates = matches,
                                                hashText = verificationInfoText,
                                            )
                                            navController.navigate(AppVerifierScreens.HashPicker.name)
                                        }
                                    }
                                } else {
                                    verifyAppViewModel.setAppNotFoundOrInvalidFormat(true)
                                    navController.navigate(AppVerifierScreens.VerifyApp.name)
                                }
                            }
                        } else {
                            snackbarCoroutineScope.launch {
                                snackbarHostState.showSnackbar("Clipboard is empty!")
                            }
                        }
                    },
                )
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.AppList) {
                AppListScreen(
                    searchQuery,
                    { name: String, packageName: String, hashes: Hashes, icon: Drawable, internalDatabaseInfo:
                    InternalDatabaseInfo ->
                        verifyAppViewModel.setAppVerificationInfo(
                            name,
                            packageName,
                            hashes,
                            internalDatabaseInfo
                        )
                        verifyAppViewModel.setAppIcon(icon)
                        navController.navigate(AppVerifierScreens.VerifyApp.name)
                    },
                    { searchQuery = it },
                    { verifyAppViewModel.getHashesFromPackageInfo(it) },
                    { verifyAppViewModel.getInternalDatabaseInfoFromVerificationInfo(it) },
                    DatabaseStatusDisplayMode.valueOf(preferencesUiState.value.databaseStatusDisplayMode),
                    userDatabaseEntries,
                    filteredEntries,
                    onDoneFiltered = {
                        filteredEntries = null
                        verifyAppViewModel.clearUiState()
                        navController.navigate(AppVerifierScreens.Start.name) {
                            popUpTo(AppVerifierScreens.AppList.name) { inclusive = true }
                        }
                    },
                    onAddAllVerified = { entries ->
                        snackbarCoroutineScope.launch {
                            var count = 0
                            for (entry in entries) {
                                preferencesViewModel.addUserDatabaseEntry(entry)
                                count++
                            }
                            snackbarHostState.showSnackbar("Added $count apps to user database")
                        }
                    },
                    preferencesUiState.value.showClipboardCheckmark,
                    preferencesViewModel.clipboardVerifiedPackages.collectAsState().value,
                    preferencesUiState.value.showUnverifiedOnly,
                    preferencesUiState.value.unverifiedExcludeUserDb,
                    SortMode.valueOf(preferencesUiState.value.defaultSortMode),
                    onRemoveFromUserDatabase = { packageName ->
                        snackbarCoroutineScope.launch {
                            preferencesViewModel.removeUserDatabaseEntry(packageName)
                        }
                    },
                    onRemoveClipboardVerification = { packageName ->
                        snackbarCoroutineScope.launch {
                            preferencesViewModel.removeClipboardVerifiedPackage(packageName)
                        }
                    },
                    onAddToUserDatabase = { entries ->
                        snackbarCoroutineScope.launch {
                            var count = 0
                            for (entry in entries) {
                                preferencesViewModel.addUserDatabaseEntry(entry)
                                count++
                            }
                            snackbarHostState.showSnackbar("Added $count apps to user database")
                        }
                    },
                )
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.HashPicker) {
                val data = currentHashMatchData
                if (data != null) {
                    LazyColumn {
                        item {
                            Text(
                                "Select the app you want to verify:",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        items(data.candidates, key = { it.packageName }) { match ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    val hashText = data.hashText
                                    if (verifyAppViewModel.findAndSetAppVerificationInfoFromPackageName(
                                            match.packageName, context.packageManager
                                        )
                                    ) {
                                        verifyAppViewModel.verifyFromText(hashText)
                                        navController.navigate(AppVerifierScreens.VerifyApp.name) {
                                            popUpTo(AppVerifierScreens.HashPicker.name) { inclusive = true }
                                        }
                                    }
                                },
                                headlineContent = { Text(match.name) },
                                overlineContent = { Text(match.packageName) },
                                leadingContent = {
                                    val icon by produceState<Drawable?>(
                                        initialValue = AppIconCache.get(match.packageName),
                                        key1 = match.packageName,
                                    ) {
                                        if (value == null) {
                                            value = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val info = context.packageManager.getPackageInfo(match.packageName, 0)
                                                    val loaded = context.packageManager.getApplicationIcon(
                                                        info.applicationInfo ?: ApplicationInfo()
                                                    )
                                                    AppIconCache.put(match.packageName, loaded)
                                                    loaded
                                                }.getOrNull()
                                            }
                                        }
                                    }
                                    if (icon != null) {
                                        Image(
                                            rememberDrawablePainter(drawable = icon),
                                            null,
                                            Modifier.size(50.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.VerifyApp) {
                val currentPackageName = verifyAppUiState.value.packageName
                val currentHashes = verifyAppUiState.value.hashes

                val sharedTextEntryForVerify = filteredEntries?.find { it.packageName == currentPackageName }
                val sharedTextHashMatchForVerify = if (sharedTextEntryForVerify != null && sharedTextEntryForVerify.hashes.isNotEmpty()) {
                    sharedTextEntryForVerify.hashes.toSet().containsAll(currentHashes.hashes.toSet())
                } else {
                    null
                }

                VerifyAppScreen(
                    verifyAppUiState.value.icon,
                    verifyAppUiState.value.name,
                    currentPackageName,
                    currentHashes,
                    verifyAppUiState.value.verificationStatus,
                    verifyAppUiState.value.appNotFoundOrInvalidFormat,
                    verifyAppUiState.value.invalidHashFormat,
                    verifyAppUiState.value.multipleHashesWithoutPackageName,
                    verifyAppUiState.value.expectedHashes,
                    { verifyAppViewModel.verifyFromText(it) },
                    { navController.navigateUp() },
                    verifyAppUiState.value.internalDatabaseInfo,
                    verifyAppUiState.value.apkFailedToParse,
                    preferencesUiState.value.showHasMultipleSigners,
                    {
                        snackbarCoroutineScope.launch {
                            snackbarHostState.showSnackbar("Clipboard is empty!")
                        }
                    },
                    DatabaseStatusDisplayMode.valueOf(preferencesUiState.value.databaseStatusDisplayMode),
                    userDatabaseEntries.find { it.packageName == currentPackageName },
                    userDatabaseEntries.find { it.packageName == currentPackageName }?.let { entry ->
                        entry.hashes.toSet().containsAll(currentHashes.hashes.toSet())
                    } ?: false,
                    {
                        snackbarCoroutineScope.launch {
                            preferencesViewModel.addUserDatabaseEntry(
                                UserDatabaseEntry(
                                    packageName = currentPackageName,
                                    hashes = currentHashes.hashes,
                                    hasMultipleSigners = currentHashes.hasMultipleSigners,
                                )
                            )
                            snackbarHostState.showSnackbar("Added ${verifyAppUiState.value.name} to user database")
                        }
                    },
                    sharedTextHashMatch = sharedTextHashMatchForVerify,
                    onDone = { navController.navigate(AppVerifierScreens.Start.name) { popUpTo(AppVerifierScreens.Start.name) { inclusive = true } } },
                    extractedFromSplitBundle = verifyAppUiState.value.extractedFromSplitBundle,
                    unsupportedFileType = verifyAppUiState.value.unsupportedFileType,
                )
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.Settings) {
                SettingsScreen(
                    onLicenseIconButtonClicked = {
                        navController.navigate(AppVerifierScreens.License.name)
                    },
                    onCreditsIconButtonClicked = {
                        navController.navigate(AppVerifierScreens.Credits.name)
                    },
                    preferencesViewModel = preferencesViewModel
                )
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.License) {
                LicenseScreen()
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.PrivacyPolicy) {
                PrivacyPolicyScreen()
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.Credits) {
                CreditsScreen()
            }
        }
    }
}

fun getStateDestinationRoute(state: NavBackStackEntry): AppVerifierScreens? {
    state.destination.route?.let { return AppVerifierScreens.valueOf(it) }
    return null
}

fun getEnterTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): EnterTransition {
    val initialNavBarRoute = getStateDestinationRoute(initialState)
    val targetNavBarRoute = getStateDestinationRoute(targetState)

    return if ((initialNavBarRoute != null) && (targetNavBarRoute != null)) {
        slideIn {
            IntOffset(
                if (initialNavBarRoute.ordinal > targetNavBarRoute.ordinal) {
                    -it.width
                } else {
                    it.width
                }, 0
            )
        } + fadeIn()
    } else {
        EnterTransition.None
    }
}

fun getExitTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): ExitTransition {
    val initialNavBarRoute = getStateDestinationRoute(initialState)
    val targetNavBarRoute = getStateDestinationRoute(targetState)

    return if ((initialNavBarRoute != null) && (targetNavBarRoute != null)) {
        slideOut {
            IntOffset(
                if (initialNavBarRoute.ordinal > targetNavBarRoute.ordinal) {
                    it.width
                } else {
                    -it.width
                }, 0
            )
        } + fadeOut()
    } else {
        ExitTransition.None
    }
}

fun NavGraphBuilder.composableWithDefaultSlideTransitions(
    route: AppVerifierScreens,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
    popExitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
    sizeTransform: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform?)? = null,
    content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    composable(route.name, arguments, deepLinks, if (enterTransition == null) {
        {
            getEnterTransition(initialState, targetState)
        }
    } else {
        null
    }, if (exitTransition == null) {
        {
            getExitTransition(initialState, targetState)
        }
    } else {
        null
    }, if (popEnterTransition == null) {
        {
            getEnterTransition(initialState, targetState)
        }
    } else {
        null
    }, if (popExitTransition == null) {
        {
            getExitTransition(initialState, targetState)
        }
    } else {
        null
    }, sizeTransform, content)
}