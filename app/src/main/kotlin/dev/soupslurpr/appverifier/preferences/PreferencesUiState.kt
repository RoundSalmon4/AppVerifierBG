package dev.soupslurpr.appverifier.preferences

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.soupslurpr.appverifier.data.DatabaseStatusDisplayMode

data class PreferencesUiState(
    val acceptedPrivacyPolicyAndLicense: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("ACCEPTED_PRIVACY_POLICY_AND_LICENSE_DATE_1/4/2024")),
        mutableStateOf(false)
    ),

    val showHasMultipleSigners: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("SHOW_HAS_MULTIPLE_SIGNERS")),
        mutableStateOf(false)
    ),

    val pitchBlackBackground: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("PITCH_BLACK_BACKGROUND")),
        mutableStateOf(false)
    ),

    val databaseStatusDisplayMode: Pair<Preferences.Key<String>, MutableState<String>> = Pair(
        (stringPreferencesKey("DATABASE_STATUS_DISPLAY_MODE")),
        mutableStateOf(DatabaseStatusDisplayMode.BOTH.name)
    ),
    val showClipboardCheckmark: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("SHOW_CLIPBOARD_CHECKMARK")),
        mutableStateOf(false)
    ),

    val showUnverifiedOnly: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("SHOW_UNVERIFIED_ONLY")),
        mutableStateOf(false)
    ),

    val unverifiedExcludeUserDb: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("UNVERIFIED_EXCLUDE_USER_DB")),
        mutableStateOf(false)
    ),
)
