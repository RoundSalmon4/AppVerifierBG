package dev.soupslurpr.appverifier.preferences

import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import kotlinx.coroutines.flow.update
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
        dataStore.data.map { settings ->
            _uiState.update { currentState ->
                currentState.copy(
                    acceptedPrivacyPolicyVersion = Pair(
                        uiState.value.acceptedPrivacyPolicyVersion.first,
                        mutableStateOf(
                            settings[uiState.value.acceptedPrivacyPolicyVersion.first] ?: uiState.value
                                .acceptedPrivacyPolicyVersion.second.value
                        )
                    ),
                    showHasMultipleSigners = Pair(
                        uiState.value.showHasMultipleSigners.first,
                        mutableStateOf(
                            settings[uiState.value.showHasMultipleSigners.first] ?: uiState.value
                                .showHasMultipleSigners.second.value
                        )
                    ),
                    databaseStatusDisplayMode = Pair(
                        uiState.value.databaseStatusDisplayMode.first,
                        mutableStateOf(
                            settings[uiState.value.databaseStatusDisplayMode.first] ?: uiState.value
                                .databaseStatusDisplayMode.second.value
                        )
                    ),
                    showClipboardCheckmark = Pair(
                        uiState.value.showClipboardCheckmark.first,
                        mutableStateOf(
                            settings[uiState.value.showClipboardCheckmark.first] ?: uiState.value
                                .showClipboardCheckmark.second.value
                        )
                    ),
                    showUnverifiedOnly = Pair(
                        uiState.value.showUnverifiedOnly.first,
                        mutableStateOf(
                            settings[uiState.value.showUnverifiedOnly.first] ?: uiState.value
                                .showUnverifiedOnly.second.value
                        )
                    ),
                    unverifiedExcludeUserDb = Pair(
                        uiState.value.unverifiedExcludeUserDb.first,
                        mutableStateOf(
                            settings[uiState.value.unverifiedExcludeUserDb.first] ?: uiState.value
                                .unverifiedExcludeUserDb.second.value
                        )
                    ),
                    defaultSortMode = Pair(
                        uiState.value.defaultSortMode.first,
                        mutableStateOf(
                            settings[uiState.value.defaultSortMode.first] ?: uiState.value
                                .defaultSortMode.second.value
                        )
                    ),
                )
            }
        }.collect()
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

    suspend fun importUserDatabase(data: String, replace: Boolean = true): ImportSummary {
        val result = parseUserDatabaseEntriesFromAny(data)
        val entries = result.entries
        if (entries.isEmpty()) return ImportSummary(0, 0, result.skippedLines)

        var newCount = 0
        var updatedCount = 0
        val updated: List<UserDatabaseEntry>
        if (replace) {
            newCount = entries.count { entry ->
                _userDatabaseEntries.value.none { it.packageName == entry.packageName }
            }
            updatedCount = entries.size - newCount
            updated = entries
        } else {
            val current = _userDatabaseEntries.value.toMutableList()
            for (entry in entries) {
                val index = current.indexOfFirst { it.packageName == entry.packageName }
                if (index != -1) {
                    updatedCount++
                    val existing = current[index]
                    current[index] = existing.copy(
                        hashes = (existing.hashes + entry.hashes).distinct(),
                        hasMultipleSigners = existing.hasMultipleSigners || entry.hasMultipleSigners,
                    )
                } else {
                    newCount++
                    current.add(entry)
                }
            }
            updated = current
        }
        dataStore.edit { preferences ->
            preferences[USER_DATABASE_JSON] = updated.toJson()
        }
        _userDatabaseEntries.value = updated
        return ImportSummary(newCount, updatedCount, result.skippedLines)
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

    suspend fun acceptPrivacyPolicy() {
        dataStore.edit { preferences ->
            preferences[uiState.value.acceptedPrivacyPolicyVersion.first] = CURRENT_PRIVACY_POLICY_VERSION
            preferences.remove(booleanPreferencesKey("ACCEPTED_PRIVACY_POLICY_AND_LICENSE_DATE_1/4/2024"))
        }
    }

    suspend fun setDatabaseStatusDisplayMode(mode: DatabaseStatusDisplayMode) {
        dataStore.edit { preferences ->
            preferences[uiState.value.databaseStatusDisplayMode.first] = mode.name
        }
    }

    suspend fun setDefaultSortMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[uiState.value.defaultSortMode.first] = mode
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