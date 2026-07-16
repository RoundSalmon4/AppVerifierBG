package dev.soupslurpr.appverifier.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var viewModel: PreferencesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create {
            tempDir.resolve("test_preferences.preferences_pb")
        }
        viewModel = PreferencesViewModel(dataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateDefaults() = runTest {
        val state = viewModel.uiState.first()

        assertFalse(state.showUnverifiedOnly)
        assertFalse(state.showClipboardCheckmark)
    }

    @Test
    fun setPreferenceUpdatesUiState() = runTest {
        viewModel.setPreference(booleanPreferencesKey("SHOW_UNVERIFIED_ONLY"), true)

        val state = viewModel.uiState.first { it.showUnverifiedOnly }
        assertTrue(state.showUnverifiedOnly)
    }


}
