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
) {
    val context = LocalContext.current

    val packageManager: PackageManager = context.packageManager

    val userInstalledPackages = remember {
        val systemPackages = packageManager.getInstalledPackages(PackageManager.MATCH_SYSTEM_ONLY)
        val userPackages = packageManager.getInstalledPackages(0)
        userPackages.removeIf { userInstalledPackage ->
            userInstalledPackage.packageName == systemPackages.firstOrNull {
                it.packageName == userInstalledPackage.packageName
            }?.packageName
        }
        userPackages
    }

    val filteredPackages = if (sharedFilteredEntries != null) {
        val filterNames = sharedFilteredEntries.map { it.packageName }.toSet()
        userInstalledPackages.filter { it.packageName in filterNames }
    } else {
        userInstalledPackages
    }

    var sortMode by rememberSaveable { mutableStateOf(defaultSortMode) }
    var filterMode by rememberSaveable { mutableStateOf(FilterMode.ALL) }

    data class AppSortStatus(
        val internalDbStatus: InternalDatabaseStatus,
        val userDbMatch: Boolean,
        val isDebug: Boolean,
        val isClipboardVerified: Boolean,
        val hasSharedText: Boolean,
    )

    val packageStatuses = remember(filteredPackages, userDatabaseEntries, clipboardVerifiedPackages, sharedFilteredEntries) {
        filteredPackages.mapNotNull { pkg ->
            if (pkg.packageName == context.packageName) return@mapNotNull null
            val packageInfo = try {
                packageManager.getPackageInfo(pkg.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } catch (_: Exception) { null } ?: return@mapNotNull null
            val hashes = getHashesFromPackageInfo(packageInfo)
            val internalDbInfo = getInternalDatabaseInfoFromVerificationInfo(VerificationInfo(pkg.packageName, hashes))
            val userDbEntry = userDatabaseEntries.find { it.packageName == pkg.packageName }
            val userDbMatch = if (userDbEntry != null) {
                if (hashes.hasMultipleSigners) userDbEntry.hashes == hashes.hashes
                else hashes.hashes.last() in userDbEntry.hashes
            } else false
            val sharedEntry = sharedFilteredEntries?.find { it.packageName == pkg.packageName }
            pkg.packageName to AppSortStatus(
                internalDbStatus = internalDbInfo.internalDatabaseStatus,
                userDbMatch = userDbMatch,
                isDebug = hashes.isDebug,
                isClipboardVerified = pkg.packageName in clipboardVerifiedPackages,
                hasSharedText = sharedEntry != null && sharedEntry.hashes.isNotEmpty(),
            )
        }.toMap()
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
        modes
    }

    var showSortMenu by remember { mutableStateOf(false) }

    val displayPackages = remember(filteredPackages, sortMode, filterMode, packageStatuses) {
        val filtered = if (filterMode == FilterMode.FAILURES_ONLY) {
            filteredPackages.filter { pkg ->
                packageStatuses[pkg.packageName]?.let { it.internalDbStatus == InternalDatabaseStatus.NOMATCH } == true
            }
        } else {
            filteredPackages
        }

        when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { pkg ->
                try { packageManager.getApplicationLabel(pkg.applicationInfo ?: ApplicationInfo()).toString().lowercase() }
                catch (_: Exception) { pkg.packageName.lowercase() }
            }
            SortMode.NAME_DESC -> filtered.sortedByDescending { pkg ->
                try { packageManager.getApplicationLabel(pkg.applicationInfo ?: ApplicationInfo()).toString().lowercase() }
                catch (_: Exception) { pkg.packageName.lowercase() }
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
    }

    val showUserDbIcon = databaseStatusDisplayMode == DatabaseStatusDisplayMode.BOTH ||
            databaseStatusDisplayMode == DatabaseStatusDisplayMode.USER_ONLY

    val showInternalDbIcon = databaseStatusDisplayMode == DatabaseStatusDisplayMode.BOTH ||
            databaseStatusDisplayMode == DatabaseStatusDisplayMode.INTERNAL_ONLY

    val existingPackageNames = userDatabaseEntries.map { it.packageName }.toSet()
    val verifiedEntries = if (sharedFilteredEntries != null) {
        filteredPackages.mapNotNull { pkg ->
            if (pkg.packageName in existingPackageNames) return@mapNotNull null
            val packageInfo = try {
                packageManager.getPackageInfo(pkg.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } catch (_: Exception) { null } ?: return@mapNotNull null
            val hashes = getHashesFromPackageInfo(packageInfo)
            val sharedEntry = sharedFilteredEntries.find { it.packageName == pkg.packageName }
            if (sharedEntry != null && sharedEntry.hashes.isNotEmpty()) {
                val match = if (hashes.hasMultipleSigners) {
                    sharedEntry.hashes == hashes.hashes
                } else {
                    hashes.hashes.last() in sharedEntry.hashes
                }
                if (match) UserDatabaseEntry(pkg.packageName, hashes.hashes, hashes.hasMultipleSigners) else null
            } else null
        }
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
                            onClick = { filterMode = if (filterMode == FilterMode.FAILURES_ONLY) FilterMode.ALL else FilterMode.FAILURES_ONLY },
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
            if (filteredPackages.isEmpty() && sharedFilteredEntries != null) {
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
            items(displayPackages, key = { it.packageName }) {
                if (it.packageName == context.packageName) return@items

                val packageInfo = remember(it.packageName) {
                    packageManager.getPackageInfo(
                        it.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                }
                val name = remember(it.packageName) {
                    packageInfo.applicationInfo?.let { appInfo ->
                        packageManager.getApplicationLabel(appInfo)
                            .toString()
                    } ?: it.packageName
                }

                if (searchQuery == "" || name.contains(searchQuery, true) ||
                    it.packageName.contains(searchQuery, true))
                {
                    val hashes = remember(it.packageName) {
                        getHashesFromPackageInfo(packageInfo)
                    }

                    val internalDbInfo = remember(it.packageName) {
                        getInternalDatabaseInfoFromVerificationInfo(
                            VerificationInfo(packageInfo.packageName, hashes)
                        )
                    }

                    if (showUnverifiedOnly && internalDbInfo.internalDatabaseStatus == InternalDatabaseStatus.MATCH) return@items

                    val userDbEntry = userDatabaseEntries.find {
                        it.packageName == packageInfo.packageName
                    }
                    val userDbMatch = if (userDbEntry != null) {
                        if (hashes.hasMultipleSigners) {
                            userDbEntry.hashes == hashes.hashes
                        } else {
                            hashes.hashes.last() in userDbEntry.hashes
                        }
                    } else {
                        false
                    }

                    if (showUnverifiedOnly && unverifiedExcludeUserDb && userDbMatch) return@items

                    val sharedEntry = sharedFilteredEntries?.find {
                        it.packageName == packageInfo.packageName
                    }
                    val sharedHashMatch = if (sharedEntry != null && sharedEntry.hashes.isNotEmpty()) {
                        if (hashes.hasMultipleSigners) {
                            sharedEntry.hashes == hashes.hashes
                        } else {
                            hashes.hashes.last() in sharedEntry.hashes
                        }
                    } else {
                        null
                    }

                    val icon = remember(it.packageName) {
                        packageManager.getApplicationIcon(
                            packageInfo.applicationInfo ?: ApplicationInfo()
                        )
                    }
                    AppItem(
                        name = name,
                        packageName = packageInfo.packageName,
                        hashes = hashes,
                        icon = icon,
                        onClickAppItem = onClickAppItem,
                        internalDatabaseInfo = internalDbInfo,
                        showInternalDbIcon = showInternalDbIcon,
                        showUserDbIcon = showUserDbIcon,
                        internalDbStatus = internalDbInfo.internalDatabaseStatus,
                        userDbMatch = userDbMatch,
                        sharedHashMatch = sharedHashMatch,
                        showClipboardCheckmark = showClipboardCheckmark,
                        isClipboardVerified = packageInfo.packageName in clipboardVerifiedPackages,
                    )
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