package dev.soupslurpr.appverifier.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dev.soupslurpr.appverifier.R
import dev.soupslurpr.appverifier.Source
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode
import dev.soupslurpr.appverifier.data.Hashes
import dev.soupslurpr.appverifier.data.InternalDatabaseInfo
import dev.soupslurpr.appverifier.data.InternalDatabaseStatus
import dev.soupslurpr.appverifier.data.SimpleVerificationStatus
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.VerificationInfo
import dev.soupslurpr.appverifier.ui.theme.ClipboardBlue
import dev.soupslurpr.appverifier.ui.theme.InfoTeal
import dev.soupslurpr.appverifier.ui.theme.UserDbPurple
import dev.soupslurpr.appverifier.ui.theme.WarningOrange
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

enum class SortMode(val label: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    INTERNAL_DB("Internal DB"),
    USER_DB("User DB"),
    DEBUG("Debug"),
    CLIPBOARD("Clipboard"),
    SHARED_TEXT("Shared text");
}

enum class FilterMode { ALL, FAILURES_ONLY }

data class AppListData(
    val hashes: Hashes,
    val name: String,
    val internalDbInfo: InternalDatabaseInfo,
)

private val appDataCache = object : LruCache<String, AppListData>(256) {
    override fun sizeOf(packageName: String, appData: AppListData): Int = 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    searchQuery: String,
    onClickAppItem: (
        name: String,
        packageName: String,
        hash: Hashes,
        icon: Drawable,
        internalDatabaseInfo: InternalDatabaseInfo,
    ) -> Unit,
    onQueryChange: (query: String) -> Unit,
    getHashesFromPackageInfo: (packageInfo: PackageInfo) -> Hashes,
    getInternalDatabaseInfoFromVerificationInfo: (verification: VerificationInfo) -> InternalDatabaseInfo,
    databaseStatusDisplayMode: DatabaseStatusDisplayMode = DatabaseStatusDisplayMode.BOTH,
    userDatabaseEntries: List<UserDatabaseEntry> = emptyList(),
    sharedFilteredEntries: List<UserDatabaseEntry>? = null,
    onDoneFiltered: (() -> Unit)? = null,
    onAddAllVerified: ((List<UserDatabaseEntry>) -> Unit)? = null,
    showClipboardCheckmark: Boolean = false,
    clipboardVerifiedPackages: Set<String> = emptySet(),
    showUnverifiedOnly: Boolean = false,
    unverifiedExcludeUserDb: Boolean = false,
    defaultSortMode: SortMode = SortMode.NAME_ASC,
    onRemoveFromUserDatabase: ((String) -> Unit)? = null,
    onRemoveClipboardVerification: ((String) -> Unit)? = null,
    onAddToUserDatabase: ((List<UserDatabaseEntry>) -> Unit)? = null,
) {
    val context = LocalContext.current

    val packageManager: PackageManager = context.packageManager

    var userInstalledPackages by remember { mutableStateOf(emptyList<PackageInfo>()) }

    val filteredPackages = if (sharedFilteredEntries != null) {
        val filterNames = sharedFilteredEntries.map { it.packageName }.toSet()
        userInstalledPackages.filter { it.packageName in filterNames }
    } else {
        userInstalledPackages
    }

    var sortMode by rememberSaveable { mutableStateOf(defaultSortMode) }
    var filterMode by rememberSaveable { mutableStateOf(FilterMode.ALL) }
    var isSelecting by rememberSaveable { mutableStateOf(false) }
    var selectedPackageNames by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showAddSelectedDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveSelectedDialog by rememberSaveable { mutableStateOf(false) }

    data class AppSortStatus(
        val internalDbStatus: InternalDatabaseStatus,
        val userDbMatch: Boolean,
        val isDebug: Boolean,
        val isClipboardVerified: Boolean,
        val hasSharedText: Boolean,
    )

    var isLoadingAppData by remember { mutableStateOf(true) }
    var appDataMap by remember { mutableStateOf<Map<String, AppListData>>(emptyMap()) }

    var savedScrollIndex by rememberSaveable { mutableStateOf(0) }
    val listState = rememberLazyListState()

    val onAppItemClicked: (String, String, Hashes, Drawable, InternalDatabaseInfo) -> Unit = { n, p, h, i, info ->
        savedScrollIndex = listState.firstVisibleItemIndex
        onClickAppItem(n, p, h, i, info)
    }

    val packageHashes: Map<String, Hashes> = appDataMap.mapValues { it.value.hashes }

    var foregroundRefreshTrigger by remember { mutableStateOf(0) }
    var hasSeenInitialResume by remember { mutableStateOf(false) }

    (context as? LifecycleOwner)?.let { lifecycleOwner ->
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (!hasSeenInitialResume) {
                        hasSeenInitialResume = true
                    } else {
                        foregroundRefreshTrigger++
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(sharedFilteredEntries, foregroundRefreshTrigger) {
        isLoadingAppData = true

        val packages = withContext(Dispatchers.IO) {
            packageManager.getInstalledPackages(0)
                .filter { (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }
        }
        userInstalledPackages = packages

        val packagesToLoad = if (sharedFilteredEntries != null) {
            val filterNames = sharedFilteredEntries.map { it.packageName }.toSet()
            packages.filter { it.packageName in filterNames }
        } else {
            packages
        }

        val allCached = packagesToLoad.all { pkg ->
            pkg.packageName == context.packageName || appDataCache.get(pkg.packageName) != null
        }
        if (allCached) {
            appDataMap = packagesToLoad.mapNotNull { pkg ->
                if (pkg.packageName == context.packageName) return@mapNotNull null
                appDataCache.get(pkg.packageName)?.let { pkg.packageName to it }
            }.toMap()
            isLoadingAppData = false
        } else {
            val result = withContext(Dispatchers.IO) {
                packagesToLoad.mapNotNull { pkg ->
                    if (pkg.packageName == context.packageName) return@mapNotNull null
                    val appData = appDataCache.get(pkg.packageName)
                        ?: run {
                            val packageInfo = try {
                                packageManager.getPackageInfo(pkg.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                            } catch (_: Exception) { null } ?: return@mapNotNull null
                            val hashes = getHashesFromPackageInfo(packageInfo)
                            val name = packageInfo.applicationInfo?.let {
                                packageManager.getApplicationLabel(it).toString()
                            } ?: pkg.packageName
                            val internalDbInfo = getInternalDatabaseInfoFromVerificationInfo(
                                VerificationInfo(pkg.packageName, hashes)
                            )
                            AppListData(hashes, name, internalDbInfo).also {
                                appDataCache.put(pkg.packageName, it)
                            }
                        }
                    pkg.packageName to appData
                }.toMap()
            }
            appDataMap = result
            isLoadingAppData = false
        }
    }

    val packageStatuses = remember(appDataMap, userDatabaseEntries, clipboardVerifiedPackages, sharedFilteredEntries) {
        appDataMap.mapValues { (packageName, appData) ->
            val internalDbInfo = appData.internalDbInfo
            val hashes = appData.hashes
            val userDbEntry = userDatabaseEntries.find { it.packageName == packageName }
            val userDbMatch = if (userDbEntry != null) {
                userDbEntry.hashes.toSet().containsAll(hashes.hashes.toSet())
            } else false
            val sharedEntry = sharedFilteredEntries?.find { it.packageName == packageName }
            AppSortStatus(
                internalDbStatus = internalDbInfo.internalDatabaseStatus,
                userDbMatch = userDbMatch,
                isDebug = hashes.isDebug,
                isClipboardVerified = packageName in clipboardVerifiedPackages,
                hasSharedText = sharedEntry != null && sharedEntry.hashes.isNotEmpty(),
            )
        }
    }

    val availableSortModes = remember(packageStatuses) {
        val modes = mutableListOf(SortMode.NAME_ASC, SortMode.NAME_DESC)
        if (packageStatuses.values.any { it.internalDbStatus != InternalDatabaseStatus.NOT_FOUND }) {
            modes.add(SortMode.INTERNAL_DB)
        }
        if (packageStatuses.values.any { it.userDbMatch }) {
            modes.add(SortMode.USER_DB)
        }
        if (packageStatuses.values.any { it.isDebug }) {
            modes.add(SortMode.DEBUG)
        }
        if (packageStatuses.values.any { it.isClipboardVerified }) {
            modes.add(SortMode.CLIPBOARD)
        }
        if (packageStatuses.values.any { it.hasSharedText }) {
            modes.add(SortMode.SHARED_TEXT)
        }
        if (sortMode !in modes) modes.add(sortMode)
        modes
    }

    var showSortMenu by remember { mutableStateOf(false) }

    val displayPackages = remember(filteredPackages, sortMode, filterMode, packageStatuses, searchQuery) {
        val filtered = if (filterMode == FilterMode.FAILURES_ONLY) {
            filteredPackages.filter { pkg ->
                packageStatuses[pkg.packageName]?.let { it.internalDbStatus == InternalDatabaseStatus.NOMATCH } == true
            }
        } else {
            filteredPackages
        }

        val sorted = when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { pkg ->
                appDataMap[pkg.packageName]?.name?.lowercase() ?: pkg.packageName.lowercase()
            }
            SortMode.NAME_DESC -> filtered.sortedByDescending { pkg ->
                appDataMap[pkg.packageName]?.name?.lowercase() ?: pkg.packageName.lowercase()
            }
            SortMode.INTERNAL_DB -> filtered.sortedBy { pkg ->
                val s = packageStatuses[pkg.packageName]
                when (s?.internalDbStatus) {
                    InternalDatabaseStatus.MATCH -> 0
                    InternalDatabaseStatus.NOMATCH -> 2
                    else -> 1
                }
            }
            SortMode.USER_DB -> filtered.sortedBy { pkg ->
                if (packageStatuses[pkg.packageName]?.userDbMatch == true) 0 else 1
            }
            SortMode.DEBUG -> filtered.sortedByDescending { pkg ->
                if (packageStatuses[pkg.packageName]?.isDebug == true) 1 else 0
            }
            SortMode.CLIPBOARD -> filtered.sortedByDescending { pkg ->
                if (packageStatuses[pkg.packageName]?.isClipboardVerified == true) 1 else 0
            }
            SortMode.SHARED_TEXT -> filtered.sortedByDescending { pkg ->
                if (packageStatuses[pkg.packageName]?.hasSharedText == true) 1 else 0
            }
        }

        if (searchQuery.isNotBlank()) {
            sorted.filter { pkg ->
                val name = appDataMap[pkg.packageName]?.name ?: pkg.packageName
                name.contains(searchQuery, true) || pkg.packageName.contains(searchQuery, true)
            }
        } else {
            sorted
        }
    }

    LaunchedEffect(isLoadingAppData) {
        if (!isLoadingAppData && savedScrollIndex > 0 && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(savedScrollIndex)
        }
    }

    val showUserDbIcon = databaseStatusDisplayMode == DatabaseStatusDisplayMode.BOTH ||
            databaseStatusDisplayMode == DatabaseStatusDisplayMode.USER_ONLY

    val showInternalDbIcon = databaseStatusDisplayMode == DatabaseStatusDisplayMode.BOTH ||
            databaseStatusDisplayMode == DatabaseStatusDisplayMode.INTERNAL_ONLY

    val mismatchCount = remember(packageStatuses) {
        packageStatuses.values.count { it.internalDbStatus == InternalDatabaseStatus.NOMATCH }
    }

    val existingPackageNames = userDatabaseEntries.map { it.packageName }.toSet()
    val verifiedEntries = remember(sharedFilteredEntries, packageHashes, existingPackageNames) {
        if (sharedFilteredEntries != null) {
            sharedFilteredEntries.mapNotNull { entry ->
                if (entry.packageName in existingPackageNames) return@mapNotNull null
                if (entry.hashes.isEmpty()) return@mapNotNull null
                val hashes = packageHashes[entry.packageName] ?: return@mapNotNull null
                val match = entry.hashes.toSet().containsAll(hashes.hashes.toSet())
                if (match) UserDatabaseEntry(entry.packageName, hashes.hashes, hashes.hasMultipleSigners) else null
            }
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            val colors1 = SearchBarDefaults.colors()
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onQueryChange,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(android.R.string.search_go)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        colors = colors1.inputFieldColors,
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp),
                colors = colors1
            ) {}
        }
        ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(
                innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                innerPadding.calculateTopPadding(),
                innerPadding.calculateEndPadding(LayoutDirection.Ltr)
            ),
            state = listState,
        ) {
            item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isSelecting) {
                                FilterChip(
                                    selected = filterMode == FilterMode.FAILURES_ONLY,
                                    onClick = { filterMode = if (filterMode == FilterMode.FAILURES_ONLY) FilterMode.ALL else FilterMode.FAILURES_ONLY },
                                    label = { Text(
                                        if (mismatchCount > 0) stringResource(R.string.mismatches_only_chip, mismatchCount)
                                        else "Mismatches only"
                                    ) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isSelecting && selectedPackageNames.isNotEmpty()) {
                                val selectedInDb = selectedPackageNames.count { it in existingPackageNames }
                                val selectedNotInDb = selectedPackageNames.size - selectedInDb
                                if (selectedNotInDb > 0) {
                                    TextButton(
                                        onClick = { showAddSelectedDialog = true }
                                    ) {
                                        Text("Add $selectedNotInDb selected")
                                    }
                                }
                                if (selectedInDb > 0) {
                                    TextButton(
                                        onClick = { showRemoveSelectedDialog = true }
                                    ) {
                                        Text("Remove $selectedInDb selected")
                                    }
                                }
                            }
                            Box {
                                if (!isSelecting) {
                                    TextButton(onClick = { showSortMenu = true }) {
                                        Text(sortMode.label)
                                    }
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                ) {
                                    availableSortModes.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label) },
                                            onClick = { sortMode = mode; showSortMenu = false },
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = {
                                isSelecting = !isSelecting
                                if (!isSelecting) selectedPackageNames = emptySet()
                            }) {
                                Text(if (isSelecting) "Cancel" else "Select")
                            }
                        }
                    }
                    if (!isSelecting) {
                        Text(
                            text = stringResource(R.string.app_count, displayPackages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                    if (isSelecting) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedPackageNames == packageHashes.keys.filter { it !in existingPackageNames }.toSet() && selectedPackageNames.isNotEmpty(),
                                onClick = {
                                    selectedPackageNames = packageHashes.keys
                                        .filter { it !in existingPackageNames }
                                        .toSet()
                                },
                                label = { Text("Not in user DB") },
                            )
                            FilterChip(
                                selected = selectedPackageNames == packageHashes.keys.filter {
                                    it !in existingPackageNames && packageStatuses[it]?.internalDbStatus != InternalDatabaseStatus.MATCH
                                }.toSet() && selectedPackageNames.isNotEmpty(),
                                onClick = {
                                    selectedPackageNames = packageHashes.keys
                                        .filter {
                                            it !in existingPackageNames &&
                                            packageStatuses[it]?.internalDbStatus != InternalDatabaseStatus.MATCH
                                        }
                                        .toSet()
                                },
                                label = { Text("Not in either DB") },
                            )
                            FilterChip(
                                selected = selectedPackageNames == packageHashes.keys.filter { it in existingPackageNames }.toSet() && selectedPackageNames.isNotEmpty(),
                                onClick = {
                                    selectedPackageNames = packageHashes.keys
                                        .filter { it in existingPackageNames }
                                        .toSet()
                                },
                                label = { Text("In user DB") },
                            )
                        }
                    }
            }
            if (sharedFilteredEntries != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Showing ${filteredPackages.size} installed of ${sharedFilteredEntries.size} total",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { onDoneFiltered?.invoke() }) {
                                Text("Done")
                            }
                        }
                        if (verifiedEntries.isNotEmpty()) {
                            TextButton(
                                onClick = { onAddAllVerified?.invoke(verifiedEntries) }
                            ) {
                                Text("Add ${verifiedEntries.size} verified to database")
                            }
                        }
                    }
                }
            }
            if (filteredPackages.isEmpty() && sharedFilteredEntries != null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No installed apps match the shared package names.",
                            modifier = Modifier.padding(32.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (isLoadingAppData) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            items(displayPackages, key = { it.packageName }) {
                val pkg = it
                if (pkg.packageName == context.packageName) return@items

                val appData = appDataMap[pkg.packageName]
                val name = appData?.name ?: pkg.packageName
                val hashes = appData?.hashes ?: Hashes(listOf(Source.NONE), emptyList(), false)
                val internalDbInfo = appData?.internalDbInfo ?: InternalDatabaseInfo(InternalDatabaseStatus.NOT_FOUND, listOf(Source.NONE))

                if (showUnverifiedOnly && internalDbInfo.internalDatabaseStatus == InternalDatabaseStatus.MATCH) return@items

                val userDbEntry = userDatabaseEntries.find { entry ->
                    entry.packageName == pkg.packageName
                }
                val userDbMatch = if (userDbEntry != null) {
                    userDbEntry.hashes.toSet().containsAll(hashes.hashes.toSet())
                } else {
                    false
                }

                if (showUnverifiedOnly && unverifiedExcludeUserDb && userDbMatch) return@items

                val sharedEntry = sharedFilteredEntries?.find { entry ->
                    entry.packageName == pkg.packageName
                }
                val sharedHashMatch = if (sharedEntry != null && sharedEntry.hashes.isNotEmpty()) {
                    sharedEntry.hashes.toSet().containsAll(hashes.hashes.toSet())
                } else {
                    null
                }
                val icon by produceState<Drawable?>(
                    initialValue = AppIconCache.get(pkg.packageName),
                    key1 = pkg.packageName,
                ) {
                    if (value == null) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val info = packageManager.getPackageInfo(pkg.packageName, 0)
                                val loaded = packageManager.getApplicationIcon(
                                    info.applicationInfo ?: ApplicationInfo()
                                )
                                AppIconCache.put(pkg.packageName, loaded)
                                loaded
                            }.getOrNull()
                        }
                    }
                }
                if (isSelecting) {
                    val isSelected = pkg.packageName in selectedPackageNames
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedPackageNames = if (checked) {
                                    selectedPackageNames + pkg.packageName
                                } else {
                                    selectedPackageNames - pkg.packageName
                                }
                            },
                        )
                        AppItem(
                            name = name,
                            packageName = pkg.packageName,
                            hashes = hashes,
                            icon = icon,
                            onClickAppItem = onAppItemClicked,
                            internalDatabaseInfo = internalDbInfo,
                            showInternalDbIcon = showInternalDbIcon,
                            showUserDbIcon = showUserDbIcon,
                            internalDbStatus = internalDbInfo.internalDatabaseStatus,
                            userDbMatch = userDbMatch,
                            sharedHashMatch = sharedHashMatch,
                            showClipboardCheckmark = showClipboardCheckmark,
                            isClipboardVerified = pkg.packageName in clipboardVerifiedPackages,
                            onRemoveFromUserDatabase = onRemoveFromUserDatabase,
                            onRemoveClipboardVerification = onRemoveClipboardVerification,
                        )
                    }
                } else {
                    AppItem(
                        name = name,
                        packageName = pkg.packageName,
                        hashes = hashes,
                        icon = icon,
                        onClickAppItem = onAppItemClicked,
                        internalDatabaseInfo = internalDbInfo,
                        showInternalDbIcon = showInternalDbIcon,
                        showUserDbIcon = showUserDbIcon,
                        internalDbStatus = internalDbInfo.internalDatabaseStatus,
                        userDbMatch = userDbMatch,
                        sharedHashMatch = sharedHashMatch,
                        showClipboardCheckmark = showClipboardCheckmark,
                        isClipboardVerified = pkg.packageName in clipboardVerifiedPackages,
                        onRemoveFromUserDatabase = onRemoveFromUserDatabase,
                        onRemoveClipboardVerification = onRemoveClipboardVerification,
                    )
                }
            }
            item {
                Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
            }
        }
    }

    if (showAddSelectedDialog) {
        val addCount = selectedPackageNames.count { it !in existingPackageNames }
        AlertDialog(
            onDismissRequest = { showAddSelectedDialog = false },
            confirmButton = {
                TextButton(
                    {
                        showAddSelectedDialog = false
                        val entries = selectedPackageNames.mapNotNull { packageName ->
                            if (packageName in existingPackageNames) return@mapNotNull null
                            val hashes = packageHashes[packageName] ?: return@mapNotNull null
                            UserDatabaseEntry(packageName, hashes.hashes, hashes.hasMultipleSigners)
                        }
                        onAddToUserDatabase?.invoke(entries)
                        selectedPackageNames = emptySet()
                        isSelecting = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton({ showAddSelectedDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
            title = {
                Text("Add $addCount apps to user database?")
            },
            text = {
                Text("This will add the selected apps' signing certificate hashes to your user database.")
            },
        )
    }

    if (showRemoveSelectedDialog) {
        val removeNames = selectedPackageNames.filter { it in existingPackageNames }
        AlertDialog(
            onDismissRequest = { showRemoveSelectedDialog = false },
            confirmButton = {
                TextButton(
                    {
                        showRemoveSelectedDialog = false
                        for (packageName in removeNames) {
                            onRemoveFromUserDatabase?.invoke(packageName)
                        }
                        selectedPackageNames = emptySet()
                        isSelecting = false
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton({ showRemoveSelectedDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
            title = {
                Text("Remove ${removeNames.size} apps from user database?")
            },
            text = {
                Text("This will remove the selected apps from your user database.")
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    name: String,
    packageName: String,
    hashes: Hashes,
    icon: Drawable?,
    onClickAppItem: (
        name: String,
        packageName: String,
        hash: Hashes,
        icon: Drawable,
        internalDatabaseInfo: InternalDatabaseInfo
    ) -> Unit,
    internalDatabaseInfo: InternalDatabaseInfo,
    showInternalDbIcon: Boolean = true,
    showUserDbIcon: Boolean = false,
    internalDbStatus: InternalDatabaseStatus = InternalDatabaseStatus.NOT_FOUND,
    userDbMatch: Boolean = false,
    sharedHashMatch: Boolean? = null,
    showClipboardCheckmark: Boolean = false,
    isClipboardVerified: Boolean = false,
    onRemoveFromUserDatabase: ((String) -> Unit)? = null,
    onRemoveClipboardVerification: ((String) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = {
                    icon?.let { onClickAppItem(name, packageName, hashes, it, internalDatabaseInfo) }
                },
                onLongClick = {
                    if (userDbMatch || isClipboardVerified) {
                        showContextMenu = true
                    }
                },
            ),
            headlineContent = {
                Text(name)
            },
            overlineContent = {
                Text(packageName)
            },
            leadingContent = {
                if (icon != null) {
                    Image(
                        rememberDrawablePainter(drawable = icon),
                        null,
                        Modifier.size(50.dp),
                    )
                }
            },
            trailingContent = {
                Row {
                    if (hashes.isDebug) {
                        Icon(
                            Icons.Filled.Warning,
                            "This app is signed with a debug certificate",
                            Modifier,
                            SimpleVerificationStatus.WARNING.color,
                        )
                    }
                    if (showInternalDbIcon && !hashes.isDebug) {
                        when (internalDbStatus) {
                            InternalDatabaseStatus.MATCH -> Icon(
                                Icons.Filled.Verified,
                                "Verified successfully with internal database",
                                Modifier,
                                SimpleVerificationStatus.SUCCESS.color,
                            )
                            InternalDatabaseStatus.NOMATCH -> {
                                Icon(
                                    Icons.Filled.Error,
                                    "Verification with internal database NOT successful!",
                                    Modifier,
                                    SimpleVerificationStatus.FAILURE.color,
                                )
                            }
                            InternalDatabaseStatus.NOT_FOUND -> {
                                Icon(
                                    Icons.Outlined.Help,
                                    "No entry in internal database",
                                    Modifier,
                                    InfoTeal,
                                )
                            }
                        }
                    }
                    if (showClipboardCheckmark && isClipboardVerified &&
                        internalDbStatus != InternalDatabaseStatus.MATCH
                    ) {
                        Icon(
                            Icons.Filled.Verified,
                            "Verified successfully with clipboard verification",
                            Modifier,
                            ClipboardBlue,
                        )
                    }
                    if (showUserDbIcon && userDbMatch) {
                        Icon(
                            Icons.Filled.Verified,
                            "Verified with user database",
                            Modifier,
                            UserDbPurple,
                        )
                    }
                    if (!hashes.isDebug) {
                        when (sharedHashMatch) {
                            true -> Icon(
                                Icons.Filled.Verified,
                                "Shared text hashes match installed app",
                                Modifier,
                                WarningOrange,
                            )
                            false -> Icon(
                                Icons.Filled.Error,
                                "Shared text hashes do NOT match installed app",
                                Modifier,
                                WarningOrange,
                            )
                            null -> {}
                        }
                    }
                }
            }
        )
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            if (userDbMatch) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_user_database)) },
                    onClick = {
                        showContextMenu = false
                        onRemoveFromUserDatabase?.invoke(packageName)
                    },
                )
            }
            if (isClipboardVerified) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_clipboard_verification)) },
                    onClick = {
                        showContextMenu = false
                        onRemoveClipboardVerification?.invoke(packageName)
                    },
                )
            }
        }
    }
}