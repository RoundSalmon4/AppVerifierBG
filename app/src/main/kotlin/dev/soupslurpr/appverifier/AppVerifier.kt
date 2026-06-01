package dev.soupslurpr.appverifier

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
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
import dev.soupslurpr.appverifier.preferences.CURRENT_PRIVACY_POLICY_VERSION
import dev.soupslurpr.appverifier.preferences.PreferencesViewModel
import dev.soupslurpr.appverifier.ui.AppListScreen
import dev.soupslurpr.appverifier.ui.SortMode
import dev.soupslurpr.appverifier.ui.CreditsScreen
import dev.soupslurpr.appverifier.ui.LicenseScreen
import dev.soupslurpr.appverifier.ui.ReviewPrivacyPolicyAndLicense
import dev.soupslurpr.appverifier.ui.PrivacyPolicyScreen
import dev.soupslurpr.appverifier.ui.SettingsScreen
import dev.soupslurpr.appverifier.ui.StartupScreen
import dev.soupslurpr.appverifier.ui.VerifyAppScreen
import dev.soupslurpr.appverifier.ui.VerifyAppViewModel
import kotlinx.coroutines.launch

enum class AppVerifierScreens(@StringRes val title: Int) {
    Start(title = R.string.app_name),
    AppList(title = R.string.app_list),
    VerifyApp(title = R.string.verify_app),
    Settings(title = R.string.settings),
    License(title = R.string.license),
    PrivacyPolicy(title = R.string.privacy_policy),
    Credits(title = R.string.credits)
}

@Composable
fun AppVerifierApp(
    modifier: Modifier,
    verifyAppViewModel: VerifyAppViewModel,
    preferencesViewModel: PreferencesViewModel,
    isActionSend: Boolean,
    isActionView: Boolean,
    sharedFilteredEntries: List<UserDatabaseEntry>? = null,
) {
    val preferencesUiState = preferencesViewModel.uiState.collectAsState()

    val verifyAppUiState = verifyAppViewModel.uiState.collectAsState()

    val userDatabaseEntries by preferencesViewModel.userDatabaseEntries.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarCoroutineScope = rememberCoroutineScope()

    var filteredEntries by remember { mutableStateOf(sharedFilteredEntries) }

    LaunchedEffect(sharedFilteredEntries) {
        if (sharedFilteredEntries != null) {
            filteredEntries = sharedFilteredEntries
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

    val openApkFileLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                verifyAppViewModel.setApkVerificationInfoAndInternalDatabaseStatusFromUri(
                    context.contentResolver,
                    uri,
                    context.packageManager,
                )
                pendingNavigation = AppVerifierScreens.VerifyApp.name
            }
        }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    if (preferencesUiState.value.acceptedPrivacyPolicyVersion.second.value < CURRENT_PRIVACY_POLICY_VERSION) {
        ReviewPrivacyPolicyAndLicense(preferencesViewModel = preferencesViewModel)
        return
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = remember(Unit) {
                if (filteredEntries != null) {
                    AppVerifierScreens.AppList.name
                } else if (isActionSend || isActionView) {
                    AppVerifierScreens.VerifyApp.name
                } else {
                    AppVerifierScreens.Start.name
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
                    onSettingsButtonClicked = {
                        navController.navigate(AppVerifierScreens.Settings.name)
                    },
                    onAppListButtonClicked = {
                        searchQuery = ""
                        navController.navigate(AppVerifierScreens.AppList.name)
                    },
                    onVerifyApkFileButtonClicked = {
                        openApkFileLauncher.launch(arrayOf("application/vnd.android.package-archive"))
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
                                if (verifyAppViewModel.findAndSetAppVerificationInfoFromPackageName(
                                        verificationInfoText.lines()[0],
                                        context.packageManager
                                    )
                                ) {
                                    verifyAppViewModel.verifyFromText(verificationInfoText)
                                }
                                navController.navigate(AppVerifierScreens.VerifyApp.name)
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
                    { },
                    { },
                    { verifyAppViewModel.getHashesFromPackageInfo(it) },
                    { verifyAppViewModel.getInternalDatabaseInfoFromVerificationInfo(it) },
                    DatabaseStatusDisplayMode.valueOf(preferencesUiState.value.databaseStatusDisplayMode.second.value),
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
                    preferencesUiState.value.showClipboardCheckmark.second.value,
                    preferencesViewModel.clipboardVerifiedPackages.collectAsState().value,
                    preferencesUiState.value.showUnverifiedOnly.second.value,
                    preferencesUiState.value.unverifiedExcludeUserDb.second.value,
                    SortMode.valueOf(preferencesUiState.value.defaultSortMode.second.value),
                )
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.VerifyApp) {
                val currentPackageName = verifyAppUiState.value.packageName.value
                val currentHashes = verifyAppUiState.value.hashes.value

                val sharedTextEntryForVerify = filteredEntries?.find { it.packageName == currentPackageName }
                val sharedTextHashMatchForVerify = if (sharedTextEntryForVerify != null && sharedTextEntryForVerify.hashes.isNotEmpty()) {
                    sharedTextEntryForVerify.hashes.toSet().containsAll(currentHashes.hashes.toSet())
                } else {
                    null
                }

                VerifyAppScreen(
                    verifyAppUiState.value.icon.value,
                    verifyAppUiState.value.name.value,
                    currentPackageName,
                    currentHashes,
                    verifyAppUiState.value.verificationStatus.value,
                    verifyAppUiState.value.appNotFoundOrInvalidFormat.value,
                    verifyAppUiState.value.invalidHashFormat.value,
                    verifyAppUiState.value.expectedHashes.value,
                    { verifyAppViewModel.verifyFromText(it) },
                    { navController.navigateUp() },
                    verifyAppUiState.value.internalDatabaseInfo.value,
                    verifyAppUiState.value.apkFailedToParse.value,
                    preferencesUiState.value.showHasMultipleSigners.second.value,
                    {
                        snackbarCoroutineScope.launch {
                            snackbarHostState.showSnackbar("Clipboard is empty!")
                        }
                    },
                    DatabaseStatusDisplayMode.valueOf(preferencesUiState.value.databaseStatusDisplayMode.second.value),
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
                            snackbarHostState.showSnackbar("Added ${verifyAppUiState.value.name.value} to user database")
                        }
                    },
                    sharedTextHashMatch = sharedTextHashMatchForVerify,
                )
            }
            composableWithDefaultSlideTransitions(route = AppVerifierScreens.Settings) {
                SettingsScreen(
                    onLicenseIconButtonClicked = {
                        navController.navigate(AppVerifierScreens.License.name)
                    },
                    onPrivacyPolicyIconButtonClicked = {
                        navController.navigate(AppVerifierScreens.PrivacyPolicy.name)
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