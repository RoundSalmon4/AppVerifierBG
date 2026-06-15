package dev.soupslurpr.appverifier.data

class HashVerifier {

    fun isValidSha256Hash(hash: String): Boolean {
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
                    it.trim().replace(' ', '\n').trim('"').plus('\n')
                }
        } else if (trimmedText.contains(' ')) {
            trimmedText
                .lines()
                .joinToString("") {
                    it.trim().replace(' ', '\n').plus('\n')
                }
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
        return hexHash.iterator().run {
            var result = ""
            this.withIndex().forEach {
                result += it.value
                if (it.index % 2 != 0 && it.index != hexHash.length.dec()) {
                    result += ":"
                }
            }
            result
        }
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
            return VerificationStatus.PKG_NOT_GIVEN_AND_SIG_HASH_NOMATCH
        }

        if (lines.size == 2 && lines[1].length == 64 && lines[0].trim().length + lines[1].trim().length == 128) {
            if (lines[0].trim() + ":" + lines[1].trim() in currentHashes.hashes) {
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
