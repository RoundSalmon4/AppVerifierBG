package dev.soupslurpr.appverifier.ui

import android.app.Application
import android.content.ContentResolver
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import dev.soupslurpr.appverifier.Source
import dev.soupslurpr.appverifier.data.Hashes
import dev.soupslurpr.appverifier.data.InternalDatabaseInfo
import dev.soupslurpr.appverifier.data.InternalDatabaseStatus
import dev.soupslurpr.appverifier.data.VerificationInfo
import dev.soupslurpr.appverifier.data.VerificationStatus
import dev.soupslurpr.appverifier.data.VerifyAppUiState
import dev.soupslurpr.appverifier.internalVerificationInfoDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class VerifyAppViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VerifyAppUiState())
    val uiState: StateFlow<VerifyAppUiState> = _uiState.asStateFlow()

    var onVerificationResult: ((VerificationStatus) -> Unit)? = null

    fun setAppVerificationInfo(
        name: String,
        packageName: String,
        hashes: Hashes,
        internalDatabaseInfo: InternalDatabaseInfo,
    ) {
        _uiState.value.name.value = name
        _uiState.value.packageName.value = packageName
        _uiState.value.hashes.value = hashes
        _uiState.value.internalDatabaseInfo.value = internalDatabaseInfo
        _uiState.value.verificationStatus.value = VerificationStatus.UNKNOWN
        _uiState.value.searchQuery.value = ""
        _uiState.value.appNotFoundOrInvalidFormat.value = false
        _uiState.value.apkFailedToParse.value = false
        _uiState.value.invalidHashFormat.value = false
        _uiState.value.expectedHashes.value = emptyList()
    }

    fun setAppIcon(icon: Drawable) {
        _uiState.value.icon.value = icon
    }

    fun verifyFromText(text: String) {
        val verificationInfoText = getVerificationInfoText(text)
        val lines = verificationInfoText.lines().filter { it.isNotBlank() }
        val packageName = _uiState.value.packageName.value

        val hashLines = if (lines.isNotEmpty() && (lines[0] == packageName || !isValidSha256Hash(lines[0].trim()))) {
            lines.drop(1)
        } else {
            lines
        }

        val allHashesAreValid = hashLines.all { isValidSha256Hash(it.trim()) }

        if (!allHashesAreValid) {
            _uiState.value.invalidHashFormat.value = true
            _uiState.value.verificationStatus.value = VerificationStatus.UNKNOWN
            onVerificationResult?.invoke(VerificationStatus.UNKNOWN)
            return
        }

        _uiState.value.expectedHashes.value = hashLines
        _uiState.value.invalidHashFormat.value = false
        val status = parseTextToVerificationStatus(text)
        _uiState.value.verificationStatus.value = status
        onVerificationResult?.invoke(status)
    }

    private fun isValidSha256Hash(hash: String): Boolean {
        if (Regex("^[0-9A-Fa-f]{64}$").matches(hash)) return true
        if (Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}$").matches(hash)) return true
        return false
    }

    fun getVerificationInfoText(text: String): String {
        val trimmedText = text.trim().trim('"').lines().joinToString("") { it.trim().plus('\n') }

        return if (trimmedText.contains('"')) {
            trimmedText
                .lines()
                .dropLast(2)
                .joinToString("") {
                    it
                        .trim()
                        .replace(
                            ' ',
                            '\n'
                        )
                        .trim('"')
                        .plus('\n')
                }
        } else if (trimmedText.contains(' ')) {
            trimmedText
                .lines()
                .joinToString("") {
                    it
                        .trim()
                        .replace(
                            ' ',
                            '\n'
                        )
                        .plus('\n')
                }
        } else {
            trimmedText
        }
    }
    private fun parseTextToVerificationStatus(text: String): VerificationStatus {
        fun parseVerificationInfoTextToVerificationStatus(verificationInfoText: String): VerificationStatus {
            if (!uiState.value.hashes.value.hasMultipleSigners) {
                if (
                    (uiState.value.hashes.value.hashes.last() == verificationInfoText.lines()[0])
                    || (verificationInfoText.lines()[0].trim().iterator().run {
                        var convertedHash = ""
                        this.withIndex().forEach {
                            convertedHash += it.value
                            if (it.index % 2 != 0 && (it.index != verificationInfoText.lines()[0].trim().length.dec())) {
                                convertedHash += ":"
                            }
                        }
                        uiState.value.hashes.value.hashes.last() == convertedHash.uppercase()
                    })
                    || uiState.value.hashes.value.hashes.last() ==
                        verificationInfoText.lines()[0].trim() + ":" + verificationInfoText.lines()[1].trim()
                ) {
                    return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
                } else if (verificationInfoText.lines()[0].length == 95) {
                    return VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH
                }
            } else if (uiState.value.hashes.value.hashes == verificationInfoText.lines()) {
                return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
            }

            val isPackageNameMatch = verificationInfoText.lines()[0] == uiState.value.packageName.value
            val verificationStatus = if (uiState.value.hashes.value.hasMultipleSigners) {
                if (verificationInfoText.lines().drop(1) == uiState.value.hashes.value.hashes) {
                    VerificationStatus.MATCH
                } else {
                    VerificationStatus.NOMATCH
                }
            } else if (verificationInfoText.lines().drop(1).any {
                    uiState.value.hashes.value.hashes.last() == it
                }) {
                VerificationStatus.MATCH
            } else {
                VerificationStatus.NOMATCH
            }

            return if (isPackageNameMatch && (verificationStatus.ordinal == VerificationStatus.NOMATCH.ordinal)) {
                VerificationStatus.PKG_MATCH_BUT_SIG_HASH_NOMATCH
            } else if (!isPackageNameMatch && (verificationStatus.ordinal == VerificationStatus.MATCH.ordinal)) {
                VerificationStatus.PKG_NOMATCH_BUT_SIG_HASH_MATCH
            } else if (verificationStatus.ordinal == VerificationStatus.NOMATCH.ordinal) {
                VerificationStatus.NOMATCH
            } else if (verificationStatus.ordinal == VerificationStatus.MATCH.ordinal) {
                VerificationStatus.MATCH
            } else {
                VerificationStatus.NOMATCH
            }
        }

        return parseVerificationInfoTextToVerificationStatus(getVerificationInfoText(text))
    }

    fun getHashesFromPackageInfo(packageInfo: PackageInfo): Hashes {
        val signingInfo = packageInfo.signingInfo
        val hasMultipleSigners = signingInfo!!.hasMultipleSigners()

        val certFactory = CertificateFactory.getInstance("X.509")

        val signatureList = if (hasMultipleSigners) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }

        val isDebug = signatureList.any { signature ->
            try {
                val cert = certFactory.generateCertificate(
                    ByteArrayInputStream(signature.toByteArray())
                ) as X509Certificate
                cert.subjectX500Principal.name.contains("Android Debug")
            } catch (_: Exception) {
                false
            }
        }

        val signatures = signatureList.map { signature ->
            MessageDigest
                .getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString(":") {
                    "%02x".format(it)
                }
                .uppercase()
        }

        return Hashes(listOf(Source.NONE), signatures, hasMultipleSigners, isDebug)
    }

    fun findAndSetAppVerificationInfoFromPackageName(packageName: String, packageManager: PackageManager) {
        val systemPackages = packageManager.getInstalledPackages(PackageManager.MATCH_SYSTEM_ONLY)

        val userInstalledPackages = packageManager.getInstalledPackages(0)

        userInstalledPackages.removeIf { userInstalledPackage ->
            userInstalledPackage.packageName == systemPackages.firstOrNull {
                it.packageName == userInstalledPackage.packageName
            }?.packageName
        }

        userInstalledPackages.find { packageInfo: PackageInfo? -> packageInfo?.packageName == packageName }.run {
            if (this != null) {
                val packageInfo =
                    packageManager.getPackageInfo(this.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

                val applicationInfo = packageInfo.applicationInfo ?: ApplicationInfo()

                val hashes = getHashesFromPackageInfo(packageInfo)

                setAppVerificationInfo(
                    packageManager.getApplicationLabel(
                        applicationInfo
                    )
                        .toString(),
                    packageInfo.packageName,
                    hashes,
                    getInternalDatabaseInfoFromVerificationInfo(VerificationInfo(packageName, hashes)),
                )
                setAppIcon(packageManager.getApplicationIcon(applicationInfo))
            } else {
                setAppNotFoundOrInvalidFormat(true)
            }
        }
    }

    fun setAppNotFoundOrInvalidFormat(b: Boolean) {
        _uiState.value.appNotFoundOrInvalidFormat.value = b
        if (b) {
            _uiState.value.apkFailedToParse.value = false
        }
    }

    fun setApkFailedToParse(b: Boolean) {
        _uiState.value.apkFailedToParse.value = b
        if (b) {
            _uiState.value.appNotFoundOrInvalidFormat.value = false
        }
    }

    fun setInvalidHashFormat(b: Boolean) {
        _uiState.value.invalidHashFormat.value = b
    }

    fun getInternalDatabaseInfoFromVerificationInfo(verificationInfo: VerificationInfo): InternalDatabaseInfo {
        return internalVerificationInfoDatabase.run {
            val packageNameMatchedInternalDatabaseVerificationInfo = try {
                this.first {
                    it.packageName == verificationInfo.packageName
                }
            } catch (e: NoSuchElementException) {
                return@run InternalDatabaseInfo(InternalDatabaseStatus.NOT_FOUND, listOf(Source.NONE))
            }

            return@run if (verificationInfo.hashes.hasMultipleSigners) {
                val maybeMatchedHashes = packageNameMatchedInternalDatabaseVerificationInfo.hashesList.find {
                    it ==
                            verificationInfo.hashes
                }
                if (maybeMatchedHashes != null) {
                    InternalDatabaseInfo(InternalDatabaseStatus.MATCH, maybeMatchedHashes.sources)
                } else {
                    InternalDatabaseInfo(InternalDatabaseStatus.NOMATCH, listOf(Source.NONE))
                }
            } else {
                packageNameMatchedInternalDatabaseVerificationInfo
                    .hashesList
                    .forEach { internalDatabaseHashes ->
                        if (internalDatabaseHashes
                                .hasMultipleSigners
                            == verificationInfo.hashes
                                .hasMultipleSigners
                        ) {
                            verificationInfo.hashes.hashes.last().let { hash ->
                                if (internalDatabaseHashes.hashes.last() == hash) {
                                    return@run InternalDatabaseInfo(
                                        InternalDatabaseStatus.MATCH,
                                        internalDatabaseHashes.sources
                                    )
                                }
                            }
                        }
                    }
                return InternalDatabaseInfo(InternalDatabaseStatus.NOMATCH, listOf(Source.NONE))
            }
        }
    }

    fun setApkVerificationInfoAndInternalDatabaseStatusFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        packageManager: PackageManager,
    ) {
        contentResolver.openInputStream(uri).use { inputStream ->
            val tempFile = File.createTempFile("temp", null, getApplication<Application>().cacheDir)

            tempFile.outputStream().use { fileOut ->
                inputStream.use { it!!.copyTo(fileOut) }
            }

            val packageInfo = packageManager.getPackageArchiveInfo(
                tempFile.path,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val applicationInfo = packageInfo?.applicationInfo ?: ApplicationInfo()

            if (packageInfo == null) {
                setApkFailedToParse(true)

                val isFileDeleted = tempFile.delete()

                if (!isFileDeleted) {
                    throw IOException(
                        "Temporary APK file couldn't be deleted! Report this bug please with instructions " +
                                "on how to reproduce!"
                    )
                }

                return
            }

            applicationInfo.sourceDir = tempFile.path
            applicationInfo.publicSourceDir = tempFile.path

            val packageName = packageInfo.packageName

            val hashes = getHashesFromPackageInfo(packageInfo)

            setAppVerificationInfo(
                packageManager.getApplicationLabel(applicationInfo).toString(),
                packageName,
                hashes,
                getInternalDatabaseInfoFromVerificationInfo(VerificationInfo(packageName, hashes)),
            )
            setAppIcon(packageManager.getApplicationIcon(applicationInfo))

            val isFileDeleted = tempFile.delete()

            if (!isFileDeleted) {
                throw IOException(
                    "Temporary APK file couldn't be deleted! Report this bug please with instructions " +
                            "on how to reproduce!"
                )
            }
        }
    }

    fun clearUiState() {
        _uiState.value = VerifyAppUiState()
    }
}
