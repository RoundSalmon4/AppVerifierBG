package dev.soupslurpr.appverifier.ui

import android.app.Application
import dev.soupslurpr.appverifier.Source
import dev.soupslurpr.appverifier.data.HashVerifier
import dev.soupslurpr.appverifier.data.Hashes
import dev.soupslurpr.appverifier.data.VerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VerifyAppViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val hashVerifier = mockk<HashVerifier>()
    private lateinit var viewModel: VerifyAppViewModel

    @BeforeEach
    fun setUp() {
        viewModel = VerifyAppViewModel(application, hashVerifier)
    }

    @Test
    fun verifyFromText_delegatesToHashVerifier() {
        val hashes = Hashes(listOf(Source.NONE), listOf("AA:BB:CC:DD"), false)
        viewModel.setAppVerificationInfo(
            name = "TestApp",
            packageName = "com.example.app",
            hashes = hashes,
            internalDatabaseInfo = mockk(relaxed = true),
        )

        every { hashVerifier.getVerificationInfoText(any()) } returns "com.example.app\nAA:BB:CC:DD"
        every { hashVerifier.isValidSha256Hash(any()) } returns true
        every { hashVerifier.parseTextToVerificationStatus(any(), any(), any()) } returns VerificationStatus.MATCH

        viewModel.verifyFromText("com.example.app\nAA:BB:CC:DD")

        verify { hashVerifier.getVerificationInfoText("com.example.app\nAA:BB:CC:DD") }
        verify { hashVerifier.parseTextToVerificationStatus(any(), any(), any()) }
        assertEquals(VerificationStatus.MATCH, viewModel.uiState.value.verificationStatus)
    }

    @Test
    fun verifyFromText_withInvalidHash_showsError() {
        val hashes = Hashes(listOf(Source.NONE), listOf("AA:BB:CC:DD"), false)
        viewModel.setAppVerificationInfo(
            name = "TestApp",
            packageName = "com.example.app",
            hashes = hashes,
            internalDatabaseInfo = mockk(relaxed = true),
        )

        every { hashVerifier.getVerificationInfoText(any()) } returns "com.example.app\ninvalid"
        every { hashVerifier.isValidSha256Hash("invalid") } returns false

        viewModel.verifyFromText("com.example.app\ninvalid")

        assertEquals(VerificationStatus.UNKNOWN, viewModel.uiState.value.verificationStatus)
        assertEquals(true, viewModel.uiState.value.invalidHashFormat)
    }

    @Test
    fun setAppVerificationInfo_clearsPreviousState() {
        viewModel.setAppVerificationInfo(
            name = "TestApp",
            packageName = "com.example.app",
            hashes = Hashes(listOf(Source.NONE), listOf("AA:BB:CC:DD"), false),
            internalDatabaseInfo = mockk(relaxed = true),
        )

        val state = viewModel.uiState.value
        assertEquals("TestApp", state.name)
        assertEquals("com.example.app", state.packageName)
        assertEquals(VerificationStatus.UNKNOWN, state.verificationStatus)
        assertEquals(false, state.invalidHashFormat)
        assertEquals(false, state.appNotFoundOrInvalidFormat)
    }

    @Test
    fun clearUiState_resetsToDefaults() {
        viewModel.setAppVerificationInfo(
            name = "TestApp",
            packageName = "com.example.app",
            hashes = Hashes(listOf(Source.NONE), listOf("AA:BB:CC:DD"), false),
            internalDatabaseInfo = mockk(relaxed = true),
        )

        viewModel.clearUiState()

        assertEquals("", viewModel.uiState.value.packageName)
    }
}
