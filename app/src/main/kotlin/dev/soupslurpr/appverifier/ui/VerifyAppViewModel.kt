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
import dev.soupslurpr.appverifier.internalVerificationInfoDatabaseMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class VerifyAppViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VerifyAppUiState())
    val uiState: StateFlow<VerifyAppUiState> = _uiState.asStateFlow()

    var onVerificationResult: ((VerificationStatus) -> Unit)? = null

    private var generationCounter = 0
    private var currentGeneration = 0

    fun setAppVerificationInfo(
        name: String,
        packageName: String,
        hashes: Hashes,
        internalDatabaseInfo: InternalDatabaseInfo,
    ) {
        _uiState.update { it.copy(
            name = name,
            packageName = packageName,
            hashes = hashes,
            internalDatabaseInfo = internalDatabaseInfo,
            verificationStatus = VerificationStatus.UNKNOWN,
            appNotFoundOrInvalidFormat = false,
            apkFailedToParse = false,
            invalidHashFormat = false,
            expectedHashes = emptyList(),
        ) }
    }

    fun setAppIcon(icon: Drawable) {
        _uiState.update { it.copy(icon = icon) }
    }

    fun verifyFromText(text: String) {
        val verificationInfoText = getVerificationInfoText(text)
        val lines = verificationInfoText.lines().filter { it.isNotBlank() }
        val packageName = _uiState.value.packageName

        val hashLines = if (lines.isNotEmpty() && (lines[0] == packageName || !isValidSha256Hash(lines[0].trim()))) {
            lines.drop(1)
        } else {
            lines
        }

        val allHashesAreValid = hashLines.all { isValidSha256Hash(it.trim()) }

        if (!allHashesAreValid) {
            _uiState.update { it.copy(
                invalidHashFormat = true,
                verificationStatus = VerificationStatus.UNKNOWN,
            ) }
            onVerificationResult?.invoke(VerificationStatus.UNKNOWN)
            return
        }

        val status = parseTextToVerificationStatus(text)
        _uiState.update { it.copy(
            expectedHashes = hashLines,
            invalidHashFormat = false,
            verificationStatus = status,
        ) }
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
            val state = uiState.value
            val lines = verificationInfoText.trimEnd().lines()
            if (state.hashes.hashes.toSet().isNotEmpty() && state.hashes.hashes.toSet() == lines.toSet()) {
                return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
            }

            if (lines.size == 1 && lines[0].length == 64) {
                val convertedHash = lines[0].trim().iterator().run {
                    var result = ""
                    this.withIndex().forEach {
                        result += it.value
                        if (it.index % 2 != 0 && (it.index != lines[0].trim().length.dec())) {
                            result += ":"
                        }
                    }
                    result
                }
                if (convertedHash.uppercase() in state.hashes.hashes) {
                    return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
                }
                if (lines[0].length == 95) {
                    return VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH
                }
            }

            if (lines.size == 2 &&
                lines[1].length == 64 &&
                lines[0].trim().length + lines[1].trim().length == 128
            ) {
                if (lines[0].trim() + ":" + lines[1].trim()
                    in state.hashes.hashes
                ) {
                    return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
                }
            }

            if (lines.all { it.length == 95 || isValidSha256Hash(it) }) {
                return VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH
            }

            val isPackageNameMatch = lines[0] == state.packageName
            val verificationStatus = if (state.hashes.hashes.toSet().isNotEmpty() && state.hashes.hashes.toSet() == lines.drop(1).toSet()) {
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

    fun findAndSetAppVerificationInfoFromPackageName(packageName: String, packageManager: PackageManager): Boolean {
        val userInstalledPackages = packageManager.getInstalledPackages(0)
            .filter { (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }

        val found = userInstalledPackages.find { it.packageName == packageName }
        if (found != null) {
            val packageInfo =
                packageManager.getPackageInfo(found.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

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
            return true
        } else {
            setAppNotFoundOrInvalidFormat(true)
            return false
        }
    }

    fun setAppNotFoundOrInvalidFormat(b: Boolean) {
        _uiState.update { it.copy(appNotFoundOrInvalidFormat = b, apkFailedToParse = if (b) false else it.apkFailedToParse) }
    }

    fun setApkFailedToParse(b: Boolean) {
        _uiState.update { it.copy(apkFailedToParse = b, appNotFoundOrInvalidFormat = if (b) false else it.appNotFoundOrInvalidFormat) }
    }

    fun getInternalDatabaseInfoFromVerificationInfo(verificationInfo: VerificationInfo): InternalDatabaseInfo {
        if (verificationInfo.packageName == getApplication<Application>().packageName) {
            return InternalDatabaseInfo(InternalDatabaseStatus.NOT_FOUND, listOf(Source.NONE))
        }

        val entry = internalVerificationInfoDatabaseMap[verificationInfo.packageName]
            ?: return InternalDatabaseInfo(InternalDatabaseStatus.NOT_FOUND, listOf(Source.NONE))

        val maybeMatchedHashes = entry.hashesList.find {
            it.hashes.toSet().containsAll(verificationInfo.hashes.hashes.toSet())
        }
        return if (maybeMatchedHashes != null) {
            val hashSources = maybeMatchedHashes.sources.filter { it != Source.VERIFIED_DOMAIN }
            val domainSources = maybeMatchedHashes.sources.filter { it == Source.VERIFIED_DOMAIN }
            InternalDatabaseInfo(InternalDatabaseStatus.MATCH, hashSources, domainSources)
        } else {
            val domainSources = entry.hashesList
                .flatMap { it.sources }
                .filter { it == Source.VERIFIED_DOMAIN }
                .distinct()
            InternalDatabaseInfo(InternalDatabaseStatus.NOMATCH, listOf(Source.NONE), domainSources)
        }
    }

    fun copyBounded(input: InputStream, output: java.io.OutputStream, maxBytes: Long = 4L * 1024 * 1024 * 1024) {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            total += n
            if (total > maxBytes) {
                throw java.io.IOException("Input stream exceeded maximum size of $maxBytes bytes")
            }
            output.write(buffer, 0, n)
        }
    }

    suspend fun setApkVerificationInfoAndInternalDatabaseStatusFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        packageManager: PackageManager,
    ) = withContext(Dispatchers.IO) {
        val generation = ++generationCounter
        currentGeneration = generation

        _uiState.value = VerifyAppUiState()

        var baseApkFile: File? = null

        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("VerifyAppViewModel", "openInputStream returned null for URI: $uri")
                setApkFailedToParse(true)
                return@withContext
            }

            val tempFile = File.createTempFile("temp", null, getApplication<Application>().cacheDir)

            try {
                inputStream.use { fileIn ->
                    tempFile.outputStream().use { fileOut ->
                        copyBounded(fileIn, fileOut)
                    }
                }

                if (generation != currentGeneration) return@withContext

                try {
                    ZipFile(tempFile).use { zip ->
                        val baseEntry = zip.getEntry("base.apk")
                        if (baseEntry != null) {
                            baseApkFile = File.createTempFile("base", ".apk", getApplication<Application>().cacheDir)
                            zip.getInputStream(baseEntry).use { input ->
                                baseApkFile!!.outputStream().use { output ->
                                    copyBounded(input, output)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                if (generation != currentGeneration) return@withContext

                val apkPath = baseApkFile?.path ?: tempFile.path

                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val applicationInfo = packageInfo?.applicationInfo ?: ApplicationInfo()

                if (packageInfo == null) {
                    setApkFailedToParse(true)
                    return@withContext
                }

                applicationInfo.sourceDir = apkPath
                applicationInfo.publicSourceDir = apkPath

                val packageName = packageInfo.packageName

                val hashes = getHashesFromPackageInfo(packageInfo)

                if (generation != currentGeneration) return@withContext

                setAppVerificationInfo(
                    packageManager.getApplicationLabel(applicationInfo).toString(),
                    packageName,
                    hashes,
                    getInternalDatabaseInfoFromVerificationInfo(VerificationInfo(packageName, hashes)),
                )
                setAppIcon(packageManager.getApplicationIcon(applicationInfo))
            } finally {
                if (!tempFile.delete()) {
                    Log.e("VerifyAppViewModel", "Failed to delete temporary file")
                }
                baseApkFile?.let {
                    if (!it.delete()) {
                        Log.e("VerifyAppViewModel", "Failed to delete temporary base APK")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VerifyAppViewModel", "Failed to process APK file", e)
            setApkFailedToParse(true)
        }
    }

    fun clearUiState() {
        _uiState.value = VerifyAppUiState()
    }
}
