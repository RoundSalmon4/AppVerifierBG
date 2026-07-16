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
    val themeMode: String = "SYSTEM",
    val useAmoledTheme: Boolean = false,
    val colorSchemeMode: String = "STANDARD",
    val primaryColor: Int = 0xFF1A237E.toInt(),
    val secondaryColor: Int = 0xFFFFD54F.toInt(),
) {
    object Keys {
        val PRIVACY_POLICY_ACCEPTED_VERSION = intPreferencesKey("PRIVACY_POLICY_ACCEPTED_VERSION")
        val SHOW_HAS_MULTIPLE_SIGNERS = booleanPreferencesKey("SHOW_HAS_MULTIPLE_SIGNERS")
        val DATABASE_STATUS_DISPLAY_MODE = stringPreferencesKey("DATABASE_STATUS_DISPLAY_MODE")
        val SHOW_CLIPBOARD_CHECKMARK = booleanPreferencesKey("SHOW_CLIPBOARD_CHECKMARK")
        val SHOW_UNVERIFIED_ONLY = booleanPreferencesKey("SHOW_UNVERIFIED_ONLY")
        val UNVERIFIED_EXCLUDE_USER_DB = booleanPreferencesKey("UNVERIFIED_EXCLUDE_USER_DB")
        val DEFAULT_SORT_MODE = stringPreferencesKey("DEFAULT_SORT_MODE")
        val THEME_MODE = stringPreferencesKey("THEME_MODE")
        val USE_AMOLED_THEME = booleanPreferencesKey("USE_AMOLED_THEME")
        val COLOR_SCHEME_MODE = stringPreferencesKey("COLOR_SCHEME_MODE")
        val PRIMARY_COLOR = intPreferencesKey("PRIMARY_COLOR")
        val SECONDARY_COLOR = intPreferencesKey("SECONDARY_COLOR")
    }
}
