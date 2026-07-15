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
import dev.soupslurpr.appverifier.data.HashVerifier
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
import androidx.lifecycle.ViewModelProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class AppHashMatch(
    val packageName: String,
    val name: String,
    val hashes: Hashes,
    val internalDatabaseInfo: InternalDatabaseInfo,
)

class VerifyAppViewModel(
    application: Application,
    private val hashVerifier: HashVerifier = HashVerifier(),
) : AndroidViewModel(application) {

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
        extractedFromSplitBundle: Boolean = false,
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
            multipleHashesWithoutPackageName = false,
            expectedHashes = emptyList(),
            extractedFromSplitBundle = extractedFromSplitBundle,
        ) }
    }

    fun setAppIcon(icon: Drawable) {
        _uiState.update { it.copy(icon = icon) }
    }

    fun verifyFromText(text: String) {
        val verificationInfoText = hashVerifier.getVerificationInfoText(text)
        val lines = verificationInfoText.lines().filter { it.isNotBlank() }
        val packageName = _uiState.value.packageName

        val hashLines = if (lines.isNotEmpty() && (lines[0] == packageName || !hashVerifier.isValidSha256Hash(lines[0].trim()))) {
            lines.drop(1)
        } else {
            lines
        }

        val allHashesAreValid = hashLines.all { hashVerifier.isValidSha256Hash(it.trim()) }

        if (!allHashesAreValid) {
            _uiState.update { it.copy(
                invalidHashFormat = true,
                multipleHashesWithoutPackageName = false,
                verificationStatus = VerificationStatus.UNKNOWN,
            ) }
            onVerificationResult?.invoke(VerificationStatus.UNKNOWN)
            return
        }

        val currentHashes = _uiState.value.hashes
        val currentPackageName = _uiState.value.packageName
        val status = hashVerifier.parseTextToVerificationStatus(text, currentHashes, currentPackageName)
        _uiState.update { it.copy(
            expectedHashes = hashLines,
            invalidHashFormat = false,
            verificationStatus = status,
        ) }
        onVerificationResult?.invoke(status)
    }

    fun getVerificationInfoText(text: String): String {
        return hashVerifier.getVerificationInfoText(text)
    }

    fun getHashesFromPackageInfo(packageInfo: PackageInfo): Hashes {
        val signingInfo = packageInfo.signingInfo ?: return Hashes(listOf(Source.NONE), emptyList(), false)
        val hasMultipleSigners = signingInfo.hasMultipleSigners()

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

    fun isValidSha256Hash(hash: String): Boolean = hashVerifier.isValidSha256Hash(hash)

    fun findAppsByHash(lines: List<String>, packageManager: PackageManager): List<AppHashMatch> {
        val normalizedLines = lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.length == 64 && hashVerifier.isValidSha256Hash(trimmed)) {
                hashVerifier.convertHexHashToColonFormat(trimmed)
            } else {
                trimmed.uppercase()
            }
        }.toSet()

        if (normalizedLines.isEmpty()) return emptyList()

        val userInstalledPackages = packageManager.getInstalledPackages(0)
            .filter { (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }

        return userInstalledPackages.mapNotNull { packageInfo ->
            val fullInfo = runCatching {
                packageManager.getPackageInfo(packageInfo.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }.getOrNull() ?: return@mapNotNull null
            val hashes = getHashesFromPackageInfo(fullInfo)
            if (normalizedLines.all { it in hashes.hashes }) {
                val applicationInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                AppHashMatch(
                    packageName = packageInfo.packageName,
                    name = packageManager.getApplicationLabel(applicationInfo).toString(),
                    hashes = hashes,
                    internalDatabaseInfo = getInternalDatabaseInfoFromVerificationInfo(
                        VerificationInfo(packageInfo.packageName, hashes)
                    ),
                )
            } else {
                null
            }
        }
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
        _uiState.update { it.copy(
            appNotFoundOrInvalidFormat = b,
            multipleHashesWithoutPackageName = false,
            apkFailedToParse = if (b) false else it.apkFailedToParse
        ) }
    }

    fun setMultipleHashesWithoutPackageName(b: Boolean) {
        _uiState.update { it.copy(
            multipleHashesWithoutPackageName = b,
            appNotFoundOrInvalidFormat = if (b) false else it.appNotFoundOrInvalidFormat,
            apkFailedToParse = false,
        ) }
    }

    fun setApkFailedToParse(b: Boolean) {
        _uiState.update { it.copy(
            apkFailedToParse = b,
            appNotFoundOrInvalidFormat = if (b) false else it.appNotFoundOrInvalidFormat,
            multipleHashesWithoutPackageName = false,
        ) }
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
        val domainSourceValues = listOf(Source.VERIFIED_DOMAIN, Source.VERIFIED_DOMAIN_HTTPS, Source.VERIFIED_DOMAIN_DNS)
        return if (maybeMatchedHashes != null) {
            val hashSources = maybeMatchedHashes.sources.filter { it !in domainSourceValues }
            val domainSources = maybeMatchedHashes.sources.filter { it in domainSourceValues }
            InternalDatabaseInfo(InternalDatabaseStatus.MATCH, hashSources, domainSources)
        } else {
            val domainSources = entry.hashesList
                .flatMap { it.sources }
                .filter { it in domainSourceValues }
                .distinct()
            InternalDatabaseInfo(InternalDatabaseStatus.NOMATCH, listOf(Source.NONE), domainSources)
        }
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, maxBytes: Long = 4L * 1024 * 1024 * 1024) {
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
        var extractedFromSplitBundle = false

        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("VerifyAppViewModel", "openInputStream returned null for URI: $uri")
                setApkFailedToParse(true)
                return@withContext
            }

            val supportedExtensions = setOf("apk", "apks", "apkm", "xapk", "zip")
            val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
            val fileExtension = fileName?.substringAfterLast('.', "")?.lowercase()
            if (!fileExtension.isNullOrEmpty() && fileExtension !in supportedExtensions) {
                inputStream.close()
                _uiState.update { it.copy(unsupportedFileType = true) }
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
                        val apkEntries = zip.entries().asSequence()
                            .filter { !it.isDirectory && !it.name.contains('/') && it.name.endsWith(".apk", ignoreCase = true) }
                            .toList()

                        if (apkEntries.isNotEmpty()) {
                            val selectedEntry = apkEntries.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
                                ?: apkEntries.firstOrNull { it.name.contains("base", ignoreCase = true) }
                                ?: apkEntries.firstOrNull { it.name.contains("main", ignoreCase = true) || it.name.contains("master", ignoreCase = true) }
                                ?: apkEntries.maxByOrNull { it.size }
                                ?: apkEntries.sortedBy { it.name }.first()

                            baseApkFile = File.createTempFile("base", ".apk", getApplication<Application>().cacheDir)
                            zip.getInputStream(selectedEntry).use { input ->
                                baseApkFile!!.outputStream().use { output ->
                                    copyBounded(input, output)
                                }
                            }
                            extractedFromSplitBundle = true
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
                    extractedFromSplitBundle,
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

    class VerifyAppViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VerifyAppViewModel::class.java)) {
                return VerifyAppViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
