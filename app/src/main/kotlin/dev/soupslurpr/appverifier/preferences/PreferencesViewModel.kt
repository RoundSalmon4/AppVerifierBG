package dev.soupslurpr.appverifier.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode
import dev.soupslurpr.appverifier.data.ImportSummary
import dev.soupslurpr.appverifier.data.UserDatabaseEntry
import dev.soupslurpr.appverifier.data.toJson
import dev.soupslurpr.appverifier.data.parseUserDatabaseEntriesFromAny
import dev.soupslurpr.appverifier.data.toUserDatabaseEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PreferencesViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private val _userDatabaseEntries = MutableStateFlow<List<UserDatabaseEntry>>(emptyList())
    val userDatabaseEntries: StateFlow<List<UserDatabaseEntry>> = _userDatabaseEntries.asStateFlow()

    private val _clipboardVerifiedPackages = MutableStateFlow<Set<String>>(emptySet())
    val clipboardVerifiedPackages: StateFlow<Set<String>> = _clipboardVerifiedPackages.asStateFlow()

    init {
        viewModelScope.launch {
            populateSettingsFromDatastore()
        }
        viewModelScope.launch {
            populateUserDatabase()
        }
        viewModelScope.launch {
            populateClipboardVerifiedPackages()
        }
    }

    private suspend fun populateSettingsFromDatastore() {
        dataStore.data.collect { settings ->
            _uiState.value = PreferencesUiState(
                acceptedPrivacyPolicyVersion = settings[PreferencesUiState.Keys.PRIVACY_POLICY_ACCEPTED_VERSION] ?: 0,
                showHasMultipleSigners = settings[PreferencesUiState.Keys.SHOW_HAS_MULTIPLE_SIGNERS] ?: false,
                databaseStatusDisplayMode = settings[PreferencesUiState.Keys.DATABASE_STATUS_DISPLAY_MODE] ?: "BOTH",
                showClipboardCheckmark = settings[PreferencesUiState.Keys.SHOW_CLIPBOARD_CHECKMARK] ?: false,
                showUnverifiedOnly = settings[PreferencesUiState.Keys.SHOW_UNVERIFIED_ONLY] ?: false,
                unverifiedExcludeUserDb = settings[PreferencesUiState.Keys.UNVERIFIED_EXCLUDE_USER_DB] ?: false,
                defaultSortMode = settings[PreferencesUiState.Keys.DEFAULT_SORT_MODE] ?: "NAME_ASC",
                themeMode = settings[PreferencesUiState.Keys.THEME_MODE] ?: "SYSTEM",
                useAmoledTheme = settings[PreferencesUiState.Keys.USE_AMOLED_THEME] ?: false,
                colorSchemeMode = settings[PreferencesUiState.Keys.COLOR_SCHEME_MODE] ?: "STANDARD",
            )
        }
    }

    private suspend fun populateUserDatabase() {
        val settings = dataStore.data.first()
        val json = settings[USER_DATABASE_JSON] ?: return
        _userDatabaseEntries.value = json.toUserDatabaseEntries().entries
    }

    suspend fun addUserDatabaseEntry(entry: UserDatabaseEntry) {
        val current = _userDatabaseEntries.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.packageName == entry.packageName }
        if (existingIndex != -1) {
            current[existingIndex] = entry
        } else {
            current.add(entry)
        }
        dataStore.edit { preferences ->
            preferences[USER_DATABASE_JSON] = current.toJson()
        }
        _userDatabaseEntries.value = current
    }

    suspend fun removeUserDatabaseEntry(packageName: String) {
        _userDatabaseEntries.value = _userDatabaseEntries.value.filter { it.packageName != packageName }
        dataStore.edit { preferences ->
            preferences[USER_DATABASE_JSON] = _userDatabaseEntries.value.toJson()
        }
    }

    suspend fun clearUserDatabase() {
        dataStore.edit { preferences ->
            preferences.remove(USER_DATABASE_JSON)
        }
        _userDatabaseEntries.value = emptyList()
    }

    fun exportUserDatabase(): String {
        return _userDatabaseEntries.value.toJson()
    }

    suspend fun importUserDatabase(data: String, replace: Boolean = true, installedPackages: Set<String> = emptySet()): ImportSummary {
        val result = parseUserDatabaseEntriesFromAny(data)
        val entries = result.entries
        if (entries.isEmpty()) return ImportSummary(0, 0, 0, result.skippedLines)

        var verifiedCount = 0
        var updatedCount = 0
        val updated: List<UserDatabaseEntry>
        if (replace) {
            for (entry in entries) {
                val alreadyExists = _userDatabaseEntries.value.any { it.packageName == entry.packageName }
                if (entry.packageName in installedPackages) {
                    if (alreadyExists) updatedCount++ else verifiedCount++
                }
            }
            updated = entries
        } else {
            val current = _userDatabaseEntries.value.toMutableList()
            for (entry in entries) {
                val index = current.indexOfFirst { it.packageName == entry.packageName }
                if (index != -1) {
                    val existing = current[index]
                    current[index] = existing.copy(
                        hashes = (existing.hashes + entry.hashes).distinct(),
                        hasMultipleSigners = existing.hasMultipleSigners || entry.hasMultipleSigners,
                    )
                    if (entry.packageName in installedPackages) updatedCount++
                } else {
                    current.add(entry)
                    if (entry.packageName in installedPackages) verifiedCount++
                }
            }
            updated = current
        }
        dataStore.edit { preferences ->
            preferences[USER_DATABASE_JSON] = updated.toJson()
        }
        _userDatabaseEntries.value = updated
        return ImportSummary(entries.size, verifiedCount, updatedCount, result.skippedLines)
    }

    private suspend fun populateClipboardVerifiedPackages() {
        dataStore.data.map { settings ->
            _clipboardVerifiedPackages.value =
                settings[CLIPBOARD_VERIFIED_PACKAGES] ?: emptySet()
        }.collect()
    }

    suspend fun removeClipboardVerifiedPackage(packageName: String) {
        _clipboardVerifiedPackages.value = _clipboardVerifiedPackages.value - packageName
        dataStore.edit { preferences ->
            preferences[CLIPBOARD_VERIFIED_PACKAGES] = _clipboardVerifiedPackages.value
        }
    }

    suspend fun clearClipboardVerifiedPackages() {
        _clipboardVerifiedPackages.value = emptySet()
        dataStore.edit { preferences ->
            preferences.remove(CLIPBOARD_VERIFIED_PACKAGES)
        }
    }

    suspend fun addClipboardVerifiedPackage(packageName: String) {
        val current = _clipboardVerifiedPackages.value
        if (packageName !in current) {
            _clipboardVerifiedPackages.value = current + packageName
            dataStore.edit { preferences ->
                preferences[CLIPBOARD_VERIFIED_PACKAGES] = current + packageName
            }
        }
    }

    suspend fun setPreference(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun setDatabaseStatusDisplayMode(mode: DatabaseStatusDisplayMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesUiState.Keys.DATABASE_STATUS_DISPLAY_MODE] = mode.name
        }
    }

    suspend fun setDefaultSortMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesUiState.Keys.DEFAULT_SORT_MODE] = mode
        }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesUiState.Keys.THEME_MODE] = mode
        }
    }

    suspend fun setUseAmoledTheme(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesUiState.Keys.USE_AMOLED_THEME] = enabled
        }
    }

    suspend fun setColorSchemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesUiState.Keys.COLOR_SCHEME_MODE] = mode
        }
    }

    companion object {
        val USER_DATABASE_JSON = stringPreferencesKey("USER_DATABASE_JSON")
        val CLIPBOARD_VERIFIED_PACKAGES = stringSetPreferencesKey("CLIPBOARD_VERIFIED_PACKAGES")
    }

    class PreferencesViewModelFactory(private val dataStore: DataStore<Preferences>) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PreferencesViewModel(dataStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class $modelClass")
        }
    }
}