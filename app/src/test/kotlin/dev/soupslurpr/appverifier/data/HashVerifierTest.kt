package dev.soupslurpr.appverifier.data

import dev.soupslurpr.appverifier.Source
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource

class HashVerifierTest {

    private val verifier = HashVerifier()

    private val sampleAppHashes = Hashes(
        sources = listOf(Source.NONE),
        hashes = listOf("AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"),
        hasMultipleSigners = false,
    )

    @Nested
    inner class IsValidSha256Hash {

        @ParameterizedTest
        @ValueSource(strings = [
            "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d",
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
            "aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99:aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99",
        ])
        fun validHashes(hash: String) {
            assertTrue(verifier.isValidSha256Hash(hash))
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "",
            "not a hash",
            "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434dXXX",
            "XX:BB:CC:DD",
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:",
        ])
        fun invalidHashes(hash: String) {
            assertFalse(verifier.isValidSha256Hash(hash))
        }
    }

    @Nested
    inner class GetVerificationInfoText {

        @Test
        fun plainTextLines() {
            val input = "com.example.app\nAA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
            val result = verifier.getVerificationInfoText(input)
            val lines = result.lines().filter { it.isNotBlank() }
            assertEquals(2, lines.size)
            assertEquals("com.example.app", lines[0])
        }

        @Test
        fun quotedText() {
            val input = "\"com.example.app\" \"AA:BB:CC:DD\""
            val result = verifier.getVerificationInfoText(input)
            assertTrue(result.lines().any { it.trim().contains("com.example.app") })
        }

        @Test
        fun spaceSeparated() {
            val input = "com.example.app AA:BB:CC:DD"
            val result = verifier.getVerificationInfoText(input)
            assertTrue(result.contains("com.example.app"))
            assertTrue(result.contains("AA:BB:CC:DD"))
        }

        @Test
        fun trailingSpaces() {
            val input = "  com.example.app  \n  AA:BB:CC:DD  "
            val result = verifier.getVerificationInfoText(input)
            assertFalse(result.lines().any { it.startsWith(" ") })
        }
    }

    @Nested
    inner class ConvertHexHashToColonFormat {

        @Test
        fun converts64CharHexTo95CharColonFormat() {
            val hex = "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899"
            val result = verifier.convertHexHashToColonFormat(hex)
            assertEquals(95, result.length)
            assertTrue(result.contains(":"))
        }

        @Test
        fun preservesUpperCase() {
            val hex = "AA".repeat(32)
            val result = verifier.convertHexHashToColonFormat(hex)
            assertEquals("AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA", result)
        }
    }

    @Nested
    inner class ParseTextToVerificationStatus {

        private val hashes = Hashes(
            listOf(Source.NONE),
            listOf("AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"),
            false,
        )

        @Test
        fun match() {
            val text = "com.example.app\nAA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
            val result = verifier.parseTextToVerificationStatus(text, hashes, "com.example.app")
            assertEquals(VerificationStatus.MATCH, result)
        }

        @Test
        fun noMatch() {
            val text = "com.example.app\n11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
            val result = verifier.parseTextToVerificationStatus(text, hashes, "com.example.app")
            assertEquals(VerificationStatus.NOMATCH, result)
        }

        @Test
        fun pkgNotGivenButHashMatch() {
            val text = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
            val result = verifier.parseTextToVerificationStatus(text, hashes, "com.example.app")
            assertEquals(VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH, result)
        }

        @Test
        fun pkgMatchButHashNoMatch() {
            val text = "com.example.app\n11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
            val result = verifier.parseTextToVerificationStatus(text, hashes, "com.example.app")
            assertEquals(VerificationStatus.PKG_MATCH_BUT_SIG_HASH_NOMATCH, result)
        }

        @Test
        fun pkgNoMatchButHashMatch() {
            val text = "com.other.app\nAA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
            val result = verifier.parseTextToVerificationStatus(text, hashes, "com.example.app")
            assertEquals(VerificationStatus.PKG_NOMATCH_BUT_SIG_HASH_MATCH, result)
        }

        @Test
        fun pkgNotGivenAndHashNoMatch() {
            val text = "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
            val result = verifier.parseTextToVerificationStatus(text, hashes, "com.example.app")
            assertEquals(VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH, result)
        }

        @Test
        fun hashOnlyTextWithHexNoMatch() {
            val hexNoMatch = "11".repeat(32)
            val result = verifier.parseTextToVerificationStatus(hexNoMatch, hashes, "com.example.app")
            assertEquals(VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH, result)
        }

        @Test
        fun hashOnlyText64CharHexMatch() {
            val hexMatch = "AA".repeat(32)
            val result = verifier.parseTextToVerificationStatus(hexMatch, hashes, "com.example.app")
            assertEquals(VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH, result)
        }

        @Test
        fun unknownWhenHashesEmpty() {
            val emptyHashes = Hashes(listOf(Source.NONE), listOf(""), false)
            val text = "com.example.app\nAA:BB:CC"
            val result = verifier.parseTextToVerificationStatus(text, emptyHashes, "com.example.app")
            assertEquals(VerificationStatus.NOMATCH, result)
        }

        @Test
        fun twoLineSplitHashMatch() {
            val hashesTwoLine = Hashes(
                listOf(Source.NONE),
                listOf("AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"),
                false,
            )
            val text = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99\nAA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
            val result = verifier.parseTextToVerificationStatus(text, hashesTwoLine, "ignore")
            assertEquals(VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH, result)
        }
    }
}
