package dev.soupslurpr.appverifier.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode
import dev.soupslurpr.appverifier.data.Hashes
import dev.soupslurpr.appverifier.data.InternalDatabaseInfo
import dev.soupslurpr.appverifier.data.InternalDatabaseStatus
import dev.soupslurpr.appverifier.data.SimpleVerificationStatus
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.VerificationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

data class AppItemData(
    val packageName: String,
    val name: String,
    val icon: Drawable,
    val hashes: Hashes,
    val internalDbInfo: InternalDatabaseInfo,
    val userDbMatch: Boolean,
    val isClipboardVerified: Boolean,
    val sharedHashMatch: Boolean?,
)

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
    onSearch: (query: String) -> Unit,
    onSearchActiveChange: (active: Boolean) -> Unit,
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
    cachedAppItems: List<AppItemData>? = null,
    onAppItemsCached: (List<AppItemData>) -> Unit = {},
) {
    val context = LocalContext.current

    val packageManager: PackageManager = context.packageManager

    val userInstalledPackages = remember {
        packageManager.getInstalledPackages(0)
            .filter { (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }
    }

    val filteredPackages = if (sharedFilteredEntries != null) {
        val filterNames = sharedFilteredEntries.map { it.packageName }.toSet()
        userInstalledPackages.filter { it.packageName in filterNames }
    } else {
        userInstalledPackages
    }

    var sortMode by rememberSaveable { mutableStateOf(defaultSortMode) }
    var filterMode by rememberSaveable { mutableStateOf(FilterMode.ALL) }

    var isLoading by remember { mutableStateOf(cachedAppItems == null) }
    var appItems by remember { mutableStateOf(cachedAppItems ?: emptyList()) }

    LaunchedEffect(filteredPackages, userDatabaseEntries, clipboardVerifiedPackages, sharedFilteredEntries) {
        if (cachedAppItems != null) return@LaunchedEffect
        isLoading = true
        appItems = withContext(Dispatchers.Default) {
            filteredPackages.mapNotNull { pkg ->
                if (pkg.packageName == context.packageName) return@mapNotNull null
                val packageInfo = try {
                    packageManager.getPackageInfo(
                        pkg.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                    )
                } catch (_: Exception) { null } ?: return@mapNotNull null

                val name = packageInfo.applicationInfo?.let {
                    packageManager.getApplicationLabel(it).toString()
                } ?: pkg.packageName

                val hashes = getHashesFromPackageInfo(packageInfo)
                val internalDbInfo = getInternalDatabaseInfoFromVerificationInfo(
                    VerificationInfo(pkg.packageName, hashes)
                )

                val userDbEntry = userDatabaseEntries.find { it.packageName == pkg.packageName }
                val userDbMatch = userDbEntry != null &&
                        userDbEntry.hashes.toSet().containsAll(hashes.hashes.toSet())

                val sharedEntry = sharedFilteredEntries?.find { it.packageName == pkg.packageName }
                val sharedHashMatch = if (sharedEntry != null && sharedEntry.hashes.isNotEmpty()) {
                    sharedEntry.hashes.toSet().containsAll(hashes.hashes.toSet())
                } else null

                AppItemData(
                    packageName = pkg.packageName,
                    name = name,
                    icon = packageManager.getApplicationIcon(
                        packageInfo.applicationInfo ?: ApplicationInfo()
                    ),
                    hashes = hashes,
                    internalDbInfo = internalDbInfo,
                    userDbMatch = userDbMatch,
                    isClipboardVerified = pkg.packageName in clipboardVerifiedPackages,
                    sharedHashMatch = sharedHashMatch,
                )
            }
        }
        if (cachedAppItems == null) {
            onAppItemsCached(appItems)
        }
        isLoading = false
    }

    val displayItems = remember(appItems, sortMode, filterMode) {
        val filtered = if (filterMode == FilterMode.FAILURES_ONLY) {
            appItems.filter { it.internalDbInfo.internalDatabaseStatus == InternalDatabaseStatus.NOMATCH }
        } else {
            appItems
        }

        when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.INTERNAL_DB -> filtered.sortedBy {
                when (it.internalDbInfo.internalDatabaseStatus) {
                    InternalDatabaseStatus.MATCH -> 0
                    InternalDatabaseStatus.NOMATCH -> 2
                    InternalDatabaseStatus.NOT_FOUND -> 1
                }
            }
            SortMode.USER_DB -> filtered.sortedBy { if (it.userDbMatch) 0 else 1 }
            SortMode.DEBUG -> filtered.sortedByDescending { if (it.hashes.isDebug) 1 else 0 }
            SortMode.CLIPBOARD -> filtered.sortedByDescending { if (it.isClipboardVerified) 1 else 0 }
            SortMode.SHARED_TEXT -> filtered.sortedByDescending { if (it.sharedHashMatch != null) 1 else 0 }
        }
    }

    val availableSortModes = remember(appItems) {
        val modes = mutableListOf(SortMode.NAME_ASC, SortMode.NAME_DESC)
        if (appItems.any { it.internalDbInfo.internalDatabaseStatus != InternalDatabaseStatus.NOT_FOUND }) {
            modes.add(SortMode.INTERNAL_DB)
        }
        if (appItems.any { it.userDbMatch }) modes.add(SortMode.USER_DB)
        if (appItems.any { it.hashes.isDebug }) modes.add(SortMode.DEBUG)
        if (appItems.any { it.isClipboardVerified }) modes.add(SortMode.CLIPBOARD)
        if (appItems.any { it.sharedHashMatch != null }) modes.add(SortMode.SHARED_TEXT)
        if (sortMode !in modes) modes.add(sortMode)
        modes
    }

    var showSortMenu by remember { mutableStateOf(false) }

    val showUserDbIcon = databaseStatusDisplayMode == DatabaseStatusDisplayMode.BOTH ||
            databaseStatusDisplayMode == DatabaseStatusDisplayMode.USER_ONLY

    val showInternalDbIcon = databaseStatusDisplayMode == DatabaseStatusDisplayMode.BOTH ||
            databaseStatusDisplayMode == DatabaseStatusDisplayMode.INTERNAL_ONLY

    val existingPackageNames = userDatabaseEntries.map { it.packageName }.toSet()
    val verifiedEntries = if (sharedFilteredEntries != null) {
        appItems.filter { it.packageName !in existingPackageNames && it.sharedHashMatch == true }
            .map { UserDatabaseEntry(it.packageName, it.hashes.hashes, it.hashes.hasMultipleSigners) }
    } else {
        emptyList()
    }

    Scaffold(
        topBar = {
            val colors1 = SearchBarDefaults.colors()
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onQueryChange,
                        onSearch = onSearch,
                        expanded = false,
                        onExpandedChange = onSearchActiveChange,
                        placeholder = { Text(stringResource(android.R.string.search_go)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        colors = colors1.inputFieldColors,
                    )
                },
                expanded = false,
                onExpandedChange = onSearchActiveChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp),
                colors = colors1
            ) {}
        }
    ) { innerPadding ->
        LazyColumn(
            Modifier.padding(
                innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                innerPadding.calculateTopPadding(),
                innerPadding.calculateEndPadding(LayoutDirection.Ltr)
            )
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filterMode == FilterMode.FAILURES_ONLY,
                            onClick = {
                                filterMode = if (filterMode == FilterMode.FAILURES_ONLY) FilterMode.ALL
                                else FilterMode.FAILURES_ONLY
                            },
                            label = { Text("Failures only") },
                        )
                    }
                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Text(sortMode.label)
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
                }
            }
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
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
                                    "Showing ${appItems.size} installed of ${sharedFilteredEntries.size} total",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                )
                                androidx.compose.material3.TextButton(onClick = { onDoneFiltered?.invoke() }) {
                                    Text("Done")
                                }
                            }
                            if (verifiedEntries.isNotEmpty()) {
                                androidx.compose.material3.TextButton(
                                    onClick = { onAddAllVerified?.invoke(verifiedEntries) }
                                ) {
                                    Text("Add ${verifiedEntries.size} verified to database")
                                }
                            }
                        }
                    }
                }
                if (appItems.isEmpty() && sharedFilteredEntries != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No installed apps match the shared package names.",
                                modifier = Modifier.padding(32.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                items(displayItems, key = { it.packageName }) { appItem ->
                    if (searchQuery.isBlank() ||
                        appItem.name.contains(searchQuery, true) ||
                        appItem.packageName.contains(searchQuery, true)
                    ) {
                        if (showUnverifiedOnly &&
                            appItem.internalDbInfo.internalDatabaseStatus == InternalDatabaseStatus.MATCH
                        ) return@items

                        if (showUnverifiedOnly && unverifiedExcludeUserDb && appItem.userDbMatch) return@items

                        AppItem(
                            name = appItem.name,
                            packageName = appItem.packageName,
                            hashes = appItem.hashes,
                            icon = appItem.icon,
                            onClickAppItem = onClickAppItem,
                            internalDatabaseInfo = appItem.internalDbInfo,
                            showInternalDbIcon = showInternalDbIcon,
                            showUserDbIcon = showUserDbIcon,
                            internalDbStatus = appItem.internalDbInfo.internalDatabaseStatus,
                            userDbMatch = appItem.userDbMatch,
                            sharedHashMatch = appItem.sharedHashMatch,
                            showClipboardCheckmark = showClipboardCheckmark,
                            isClipboardVerified = appItem.isClipboardVerified,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
            }
        }
    }
}

@Composable
fun AppItem(
    name: String,
    packageName: String,
    hashes: Hashes,
    icon: Drawable,
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
) {
    ListItem(
        modifier = Modifier.clickable {
            onClickAppItem(name, packageName, hashes, icon, internalDatabaseInfo)
        },
        headlineContent = {
            Text(name)
        },
        overlineContent = {
            Text(packageName)
        },
        leadingContent = {
            Image(
                rememberDrawablePainter(drawable = icon),
                null,
                Modifier.size(50.dp),
            )
        },
        trailingContent = {
            if (hashes.isDebug) {
                Icon(
                    Icons.Filled.Error,
                    "This app is signed with a debug certificate",
                    Modifier,
                    SimpleVerificationStatus.FAILURE.color,
                )
            } else {
            Row {
                if (showInternalDbIcon) {
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
                        InternalDatabaseStatus.NOT_FOUND -> {}
                    }
                }
                if (showClipboardCheckmark && isClipboardVerified &&
                    internalDbStatus != InternalDatabaseStatus.MATCH
                ) {
                    Icon(
                        Icons.Filled.Verified,
                        "Verified successfully with clipboard verification",
                        Modifier,
                        Color.Blue,
                    )
                }
                if (showUserDbIcon && userDbMatch) {
                    Icon(
                        Icons.Filled.Verified,
                        "Verified with user database",
                        Modifier,
                        Color(0xFF9C27B0),
                    )
                }
                when (sharedHashMatch) {
                    true -> Icon(
                        Icons.Filled.Verified,
                        "Shared text hashes match installed app",
                        Modifier,
                        Color(0xFFFF9800),
                    )
                    false -> Icon(
                        Icons.Filled.Error,
                        "Shared text hashes do NOT match installed app",
                        Modifier,
                        Color(0xFFFF9800),
                    )
                    null -> {}
                }
            }
            }
        }
    )
}