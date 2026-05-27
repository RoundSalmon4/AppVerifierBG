package dev.soupslurpr.appverifier.data

import org.json.JSONArray
import org.json.JSONObject

enum class DatabaseStatusDisplayMode {
    BOTH,
    INTERNAL_ONLY,
    USER_ONLY,
}

data class UserDatabaseEntry(
    val packageName: String,
    val hashes: List<String>,
    val hasMultipleSigners: Boolean,
)

data class ImportResult(
    val entries: List<UserDatabaseEntry>,
    val skippedLines: List<String>,
)

data class ImportSummary(
    val newCount: Int,
    val updatedCount: Int,
    val skippedLines: List<String>,
)

fun List<UserDatabaseEntry>.toJson(): String {
    val arr = JSONArray()
    forEach { entry ->
        val obj = JSONObject()
        obj.put("packageName", entry.packageName)
        val hashesArr = JSONArray()
        entry.hashes.forEach { hashesArr.put(it) }
        obj.put("hashes", hashesArr)
        obj.put("hasMultipleSigners", entry.hasMultipleSigners)
        arr.put(obj)
    }
    return arr.toString()
}

fun String.toUserDatabaseEntries(): ImportResult {
    if (this.isBlank()) return ImportResult(emptyList(), emptyList())
    val arr = JSONArray(this)
    val entries = mutableListOf<UserDatabaseEntry>()
    val skipped = mutableListOf<String>()
    for (i in 0 until arr.length()) {
        try {
            val obj = arr.getJSONObject(i)
            val hashesArr = obj.getJSONArray("hashes")
            entries.add(UserDatabaseEntry(
                packageName = obj.getString("packageName"),
                hashes = (0 until hashesArr.length()).map { hashesArr.getString(it) },
                hasMultipleSigners = obj.getBoolean("hasMultipleSigners"),
            ))
        } catch (_: Exception) {
            skipped.add(arr.opt(i).toString())
        }
    }
    return ImportResult(entries, skipped)
}

fun parseUserDatabaseEntriesFromText(text: String): ImportResult {
    val blocks = text.trim().split("\n\n").filter { it.isNotBlank() }
    val entries = mutableListOf<UserDatabaseEntry>()
    val skipped = mutableListOf<String>()
    for (block in blocks) {
        val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) {
            skipped.add(block); continue
        }
        val packageName = lines.first()
        if (!packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
            skipped.add(block); continue
        }
        val hashes = lines.drop(1).filter { it.contains(":") }
        if (hashes.isEmpty()) {
            skipped.add(block); continue
        }
        entries.add(UserDatabaseEntry(
            packageName = packageName,
            hashes = hashes,
            hasMultipleSigners = hashes.size > 1,
        ))
    }
    return ImportResult(entries, skipped)
}

fun parseUserDatabaseEntriesFromYaml(text: String): ImportResult {
    val entries = mutableListOf<UserDatabaseEntry>()
    val skipped = mutableListOf<String>()
    val docs = text.trim().split(Regex("(?m)^---\\s*$"))
    for (doc in docs) {
        if (doc.isBlank()) continue
        var packageName: String? = null
        val hashes = mutableListOf<String>()
        var inHashes = false
        for (line in doc.lines()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("- ") && inHashes) {
                val hash = trimmed.removePrefix("- ").trim()
                if (hash.contains(":")) hashes.add(hash)
            } else {
                inHashes = false
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx > 0) {
                    val key = trimmed.substring(0, colonIdx).trim()
                    val value = trimmed.substring(colonIdx + 1).trim().removeSurrounding("\"")
                    when (key.lowercase()) {
                        "packagename" -> packageName = value
                        "hashes" -> inHashes = true
                    }
                }
            }
        }
        if (packageName != null && hashes.isNotEmpty()) {
            entries.add(UserDatabaseEntry(
                packageName = packageName,
                hashes = hashes,
                hasMultipleSigners = hashes.size > 1,
            ))
        } else {
            skipped.add(doc.trim())
        }
    }
    return ImportResult(entries, skipped)
}

fun parseUserDatabaseEntriesFromPrivacyGuides(text: String): ImportResult {
    val entries = mutableListOf<UserDatabaseEntry>()
    var currentPackage: String? = null
    val currentFingerprints = mutableListOf<String>()
    var inBlockFingerprint = false
    var blockContentIndent = -1
    for (line in text.lines()) {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        if (inBlockFingerprint) {
            if (blockContentIndent < 0) {
                if (trimmed.isNotEmpty()) {
                    blockContentIndent = indent
                    currentFingerprints.add(trimmed)
                }
                continue
            }
            if (indent < blockContentIndent) {
                inBlockFingerprint = false
            } else {
                if (trimmed.isNotEmpty()) currentFingerprints.add(trimmed)
                continue
            }
        }
        val packageMatch = Regex("^- package:\\s*\"?(\\S+)\"?\\s*$").find(trimmed)
        if (packageMatch != null) {
            if (currentPackage != null && currentFingerprints.isNotEmpty()) {
                entries.add(UserDatabaseEntry(currentPackage!!, currentFingerprints.toList(), currentFingerprints.size > 1))
            }
            currentPackage = packageMatch.groupValues[1]
            currentFingerprints.clear()
            continue
        }
        if (currentPackage != null && trimmed.startsWith("- fingerprint:")) {
            val rest = trimmed.substringAfter("- fingerprint:").trim()
            if (rest == "|") {
                inBlockFingerprint = true
                blockContentIndent = -1
            } else {
                val fp = rest.removeSurrounding("\"").trim()
                if (fp.isNotEmpty()) {
                    for (f in fp.split("\\n")) {
                        val cleaned = f.trim()
                        if (cleaned.isNotEmpty()) currentFingerprints.add(cleaned)
                    }
                }
            }
            continue
        }
    }
    if (currentPackage != null && currentFingerprints.isNotEmpty()) {
        entries.add(UserDatabaseEntry(currentPackage!!, currentFingerprints.toList(), currentFingerprints.size > 1))
    }
    return ImportResult(entries, emptyList())
}

fun parseUserDatabaseEntriesFromAny(input: String): ImportResult {
    val trimmed = input.trimStart { it == '\uFEFF' }.trim()
    if (trimmed.startsWith("[")) {
        val jsonResult = try { trimmed.toUserDatabaseEntries() } catch (_: Exception) { null }
        if (jsonResult != null) return jsonResult
    }
    val pgPrefix = trimmed.removePrefix("---").trimStart()
    if (pgPrefix.startsWith("schema:") || trimmed.startsWith("- package:")) {
        val pgResult = parseUserDatabaseEntriesFromPrivacyGuides(pgPrefix)
        if (pgResult.entries.isNotEmpty()) return pgResult
    }
    val textResult = parseUserDatabaseEntriesFromText(trimmed)
    if (textResult.entries.isNotEmpty()) return textResult
    return parseUserDatabaseEntriesFromYaml(trimmed)
}
