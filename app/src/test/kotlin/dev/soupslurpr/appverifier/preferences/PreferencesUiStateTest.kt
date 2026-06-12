package dev.soupslurpr.appverifier.preferences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesUiStateTest {

    @Test
    fun defaultValues() {
        val state = PreferencesUiState()

        assertEquals(0, state.acceptedPrivacyPolicyVersion)
        assertEquals(false, state.showHasMultipleSigners)
        assertEquals(false, state.showClipboardCheckmark)
        assertEquals(false, state.showUnverifiedOnly)
        assertEquals(false, state.unverifiedExcludeUserDb)
    }

    @Test
    fun copyModifiesOnlySpecifiedFields() {
        val state = PreferencesUiState()
        val modified = state.copy(showUnverifiedOnly = true)

        assertEquals(true, modified.showUnverifiedOnly)
        assertEquals(false, modified.showClipboardCheckmark)
        assertEquals(0, modified.acceptedPrivacyPolicyVersion)
    }

    @Test
    fun currentPrivacyPolicyVersionConstant() {
        assertEquals(2, CURRENT_PRIVACY_POLICY_VERSION)
    }
}
