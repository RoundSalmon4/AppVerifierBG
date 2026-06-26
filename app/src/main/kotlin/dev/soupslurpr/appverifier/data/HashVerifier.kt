package dev.soupslurpr.appverifier.data

class HashVerifier {

    fun isValidSha256Hash(hash: String): Boolean {
        if (Regex("^[0-9A-Fa-f]{64}$").matches(hash)) return true
        if (Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}$").matches(hash)) return true
        return false
    }

    fun getVerificationInfoText(text: String): String {
        val trimmedText = text.trim().trim('"').lines().joinToString("") { it.trim().plus('\n') }

        val tokens = if (trimmedText.contains('"') || trimmedText.contains(' ')) {
            trimmedText
                .lines()
                .filter { it.isNotBlank() }
                .flatMap { line ->
                    line.trim()
                        .replace('"', ' ')
                        .split(' ')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
        } else {
            listOf(trimmedText.trim())
        }

        val hashTokens = tokens.filter { isValidSha256Hash(it) }
        if (hashTokens.isNotEmpty()) {
            return hashTokens.joinToString("\n").uppercase().trim() + "\n"
        }

        val hexHashPattern = Regex("[0-9A-Fa-f]{64}")
        val colonHashPattern = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}")
        val hexMatches = hexHashPattern.findAll(trimmedText).toList()
        val colonMatches = colonHashPattern.findAll(trimmedText).toList()
        if (hexMatches.isNotEmpty() || colonMatches.isNotEmpty()) {
            return (hexMatches.map { convertHexHashToColonFormat(it.value) } +
                    colonMatches.map { it.value.uppercase() })
                .joinToString("\n") + "\n"
        }

        return if (trimmedText.contains('"') || trimmedText.contains(' ')) {
            tokens
                .filterNot { Regex("^[A-Za-z][A-Za-z0-9-]*:$").matches(it) }
                .joinToString("\n")
                .trim() + "\n"
        } else {
            trimmedText
        }
    }

    fun parseTextToVerificationStatus(
        text: String,
        currentHashes: Hashes,
        currentPackageName: String,
    ): VerificationStatus {
        val verificationInfoText = getVerificationInfoText(text)
        return parseVerificationInfoTextToVerificationStatus(verificationInfoText, currentHashes, currentPackageName)
    }

    fun convertHexHashToColonFormat(hexHash: String): String {
        return hexHash.chunked(2).joinToString(":").uppercase()
    }

    private fun parseVerificationInfoTextToVerificationStatus(
        verificationInfoText: String,
        currentHashes: Hashes,
        currentPackageName: String,
    ): VerificationStatus {
        val lines = verificationInfoText.trimEnd().lines()

        if (currentHashes.hashes.toSet().isNotEmpty() && currentHashes.hashes.toSet() == lines.toSet()) {
            return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
        }

        if (lines.size == 1 && lines[0].length == 64) {
            val convertedHash = convertHexHashToColonFormat(lines[0].trim())
            if (convertedHash.uppercase() in currentHashes.hashes) {
                return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
            }
        }

        if (lines.size == 1 && lines[0].length == 95) {
            if (lines[0].trim().uppercase() in currentHashes.hashes) {
                return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
            }
            return VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH
        }

        if (lines.size == 2) {
            val combinedColonHash = lines[0].trim() + ":" + lines[1].trim()
            if (combinedColonHash.length == 95 && combinedColonHash in currentHashes.hashes) {
                return VerificationStatus.PKG_NOT_GIVEN_BUT_SIG_HASH_MATCH
            }
        }

        if (lines.all { it.length == 95 || isValidSha256Hash(it) }) {
            return VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH
        }

        val isPackageNameMatch = lines[0] == currentPackageName
        val hashSet = currentHashes.hashes.toSet()
        val textHashSet = lines.drop(1).toSet()
        val verificationStatus = if (hashSet.isNotEmpty() && hashSet == textHashSet) {
            VerificationStatus.MATCH
        } else {
            VerificationStatus.NOMATCH
        }

        return when {
            isPackageNameMatch && verificationStatus == VerificationStatus.NOMATCH ->
                VerificationStatus.PKG_MATCH_BUT_SIG_HASH_NOMATCH
            !isPackageNameMatch && verificationStatus == VerificationStatus.MATCH ->
                VerificationStatus.PKG_NOMATCH_BUT_SIG_HASH_MATCH
            verificationStatus == VerificationStatus.NOMATCH -> VerificationStatus.NOMATCH
            verificationStatus == VerificationStatus.MATCH -> VerificationStatus.MATCH
            else -> VerificationStatus.NOMATCH
        }
    }
}
