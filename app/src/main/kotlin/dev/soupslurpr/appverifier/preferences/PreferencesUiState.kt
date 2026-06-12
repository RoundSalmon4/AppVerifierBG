package dev.soupslurpr.appverifier.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

const val CURRENT_PRIVACY_POLICY_VERSION = 2

data class PreferencesUiState(
    val acceptedPrivacyPolicyVersion: Int = 0,
    val showHasMultipleSigners: Boolean = false,
    val databaseStatusDisplayMode: String = "BOTH",
    val showClipboardCheckmark: Boolean = false,
    val showUnverifiedOnly: Boolean = false,
    val unverifiedExcludeUserDb: Boolean = false,
    val defaultSortMode: String = "NAME_ASC",
) {
    object Keys {
        val PRIVACY_POLICY_ACCEPTED_VERSION = intPreferencesKey("PRIVACY_POLICY_ACCEPTED_VERSION")
        val SHOW_HAS_MULTIPLE_SIGNERS = booleanPreferencesKey("SHOW_HAS_MULTIPLE_SIGNERS")
        val DATABASE_STATUS_DISPLAY_MODE = stringPreferencesKey("DATABASE_STATUS_DISPLAY_MODE")
        val SHOW_CLIPBOARD_CHECKMARK = booleanPreferencesKey("SHOW_CLIPBOARD_CHECKMARK")
        val SHOW_UNVERIFIED_ONLY = booleanPreferencesKey("SHOW_UNVERIFIED_ONLY")
        val UNVERIFIED_EXCLUDE_USER_DB = booleanPreferencesKey("UNVERIFIED_EXCLUDE_USER_DB")
        val DEFAULT_SORT_MODE = stringPreferencesKey("DEFAULT_SORT_MODE")
    }
}
