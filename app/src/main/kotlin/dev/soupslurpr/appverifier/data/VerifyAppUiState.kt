package dev.soupslurpr.appverifier.data

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import dev.soupslurpr.appverifier.Source

data class VerifyAppUiState(
    val name: String = "",
    val packageName: String = "",
    val hashes: Hashes = Hashes(listOf(Source.NONE), listOf(""), false),
    val icon: Drawable? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.UNKNOWN,
    val appNotFoundOrInvalidFormat: Boolean = false,
    val apkFailedToParse: Boolean = false,
    val invalidHashFormat: Boolean = false,
    val expectedHashes: List<String> = emptyList(),
    val internalDatabaseInfo: InternalDatabaseInfo = InternalDatabaseInfo(
        InternalDatabaseStatus.NOT_FOUND,
        hashSources = listOf(Source.NONE)
    ),
)


class InternalDatabaseInfo(
    val internalDatabaseStatus: InternalDatabaseStatus,
    val hashSources: List<Source>,
    val domainSources: List<Source> = emptyList(),
)

enum class InternalDatabaseStatus(
    val info: String,
    val simpleInternalDatabaseStatus: SimpleInternalDatabaseStatus,
) {
    NOT_FOUND(
        "This app was not found in the internal database. This isn't anything to worry about, but please verify the " +
                "app normally.",
        SimpleInternalDatabaseStatus.NOT_FOUND,
    ),
    MATCH(
        "This app's verification info matches an entry in the internal database. You don't need to verify normally.",
        SimpleInternalDatabaseStatus.SUCCESS,
    ),
    NOMATCH(
        "This app was found in the internal database, but its hash did NOT match. This app may be " +
                "non-genuine.",
        SimpleInternalDatabaseStatus.FAILURE,
    ),
}

enum class SimpleInternalDatabaseStatus(val color: Color) {
    NOT_FOUND(Color(0xFF9E9E9E)),
    SUCCESS(Color(0xFF4CAF50)),
    FAILURE(Color(0xFFE53935))
}

data class Hashes(
    val sources: List<Source>,
    val hashes: List<String>,
    val hasMultipleSigners: Boolean,
    val isDebug: Boolean = false
)

data class VerificationInfo(val packageName: String, val hashes: Hashes)

enum class SimpleVerificationStatus(val color: Color) {
    UNKNOWN(Color(0xFF9E9E9E)),
    SUCCESS(Color(0xFF4CAF50)),
    WARNING(Color(0xFFFF9800)),
    FAILURE(Color(0xFFE53935))
}

enum class VerificationStatus(val info: String, val simpleVerificationStatus: SimpleVerificationStatus) {
    UNKNOWN(
        "No verification text has been compared against this app yet.",
        SimpleVerificationStatus.UNKNOWN,
    ),
    MATCH(
        "Both the package name and signing certificate hash match with the expected values",
        SimpleVerificationStatus.SUCCESS,
    ),
    NOMATCH(
        "Both the package name and the signing certificate hash DO NOT match with the expected values. Please make " +
                "sure you are verifying the correct app and check the formatting.",
        SimpleVerificationStatus.FAILURE,
    ),
    PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH(
        "The package name was not given but the signing certificate hash matches",
        SimpleVerificationStatus.SUCCESS,
    ),
    PKG_NOMATCH_BUT_SIG_HASH_MATCH(
        "The package name does not match but the signing certificate hash matches. Please make sure you are verifying" +
                " the correct app.",
        SimpleVerificationStatus.WARNING,
    ),
    PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH(
        "The package name was not given and the signing certificate hash DOES NOT match. Please make sure you are " +
                "verifying the correct app.",
        SimpleVerificationStatus.FAILURE,
    ),
    PKG_MATCH_BUT_SIG_HASH_NOMATCH(
        "The package name matches but the signing certificate hash DOES NOT match. Be wary, the application might " +
                "be non-genuine.",
        SimpleVerificationStatus.FAILURE
    ),
}