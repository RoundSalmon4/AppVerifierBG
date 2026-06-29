# AppVerifier BG

Verify installed apps against shared signature hashes, the internal database, or a user-created database. This fork extends the original AppVerifier — still does everything the original does, plus the features below.

<div align="center">

<div style="display: flex; justify-content: center; align-items: center; gap: 8px; flex-wrap: wrap;">
  <a href="https://github.com/RoundSalmon4/AppVerifierBG/releases/latest"><img src="https://github.com/NeoApplications/Neo-Backup/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png?raw=true" alt="Get it on GitHub" height="80"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7b%22id%22%3a%22com.roundsalmon4.appverifier%22%2c%22url%22%3a%22https%3a%2f%2fgithub.com%2fRoundSalmon4%2fAppVerifierBG%22%2c%22author%22%3a%22RoundSalmon4%22%2c%22name%22%3a%22AppVerifier+BG%22%2c%22preferredApkIndex%22%3a0%2c%22additionalSettings%22%3a%22%7b%5c%22includePrereleases%5c%22%3atrue%2c%5c%22fallbackToOlderReleases%5c%22%3atrue%2c%5c%22filterReleaseTitlesByRegEx%5c%22%3a%5c%22%5c%22%2c%5c%22filterReleaseNotesByRegEx%5c%22%3a%5c%22%5c%22%2c%5c%22verifyLatestTag%5c%22%3afalse%2c%5c%22sortMethodChoice%5c%22%3a%5c%22date%5c%22%2c%5c%22useLatestAssetDateAsReleaseDate%5c%22%3afalse%2c%5c%22releaseTitleAsVersion%5c%22%3atrue%2c%5c%22trackOnly%5c%22%3afalse%2c%5c%22versionExtractionRegEx%5c%22%3a%5c%22%5c%22%2c%5c%22matchGroupToUse%5c%22%3a%5c%22%5c%22%2c%5c%22versionDetection%5c%22%3atrue%2c%5c%22releaseDateAsVersion%5c%22%3afalse%2c%5c%22useVersionCodeAsOSVersion%5c%22%3afalse%2c%5c%22apkFilterRegEx%5c%22%3a%5c%22%5c%22%2c%5c%22invertAPKFilter%5c%22%3afalse%2c%5c%22autoApkFilterByArch%5c%22%3atrue%2c%5c%22appName%5c%22%3a%5c%22%5c%22%2c%5c%22appAuthor%5c%22%3a%5c%22%5c%22%2c%5c%22shizukuPretendToBeGooglePlay%5c%22%3afalse%2c%5c%22allowInsecure%5c%22%3afalse%2c%5c%22exemptFromBackgroundUpdates%5c%22%3afalse%2c%5c%22skipUpdateNotifications%5c%22%3afalse%2c%5c%22about%5c%22%3a%5c%22%5c%22%2c%5c%22refreshBeforeDownload%5c%22%3afalse%2c%5c%22includeZips%5c%22%3afalse%2c%5c%22zippedApkFilterRegEx%5c%22%3a%5c%22%5c%22%7d%22%2c%22overrideSource%22%3anull%7d"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/b1c8ac6f2ab08497189721a788a5763e28ff64cd/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a>
  <a href="https://f-droid.org/en/packages/com.roundsalmon4.appverifier/"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"></a>
  <a href="https://apt.izzysoft.de/packages/com.roundsalmon4.appverifier"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80"></a>
</div>

</div>

## Verification

Pasted hashes are checked for valid SHA-256 format and show a clear error if something's wrong. When verification fails, hashes are labeled "Expected" (what you pasted) and "Found" (on-device fingerprint) so you can tell which is which. Apps signed with debug certificates are flagged as insecure. Verification results use chip-based status indicators. The expected vs. found hash comparison has a collapsible toggle. `.apks` split APK container files are also accepted — the base APK is extracted and verified the same way as a regular APK.

## App List

Every installed user app shows status icons for internal database matches, user database entries, clipboard verification, and shared text matches all at a glance. Sort by name, database status, debug builds, clipboard verified, or shared text — pick whichever you need from the dropdown and it only shows modes with matching data. A filter chip hides everything except mismatches. The default sort order can be set in settings. A search bar lets you filter by name or package name. Long-press an app with a user database entry or clipboard checkmark to remove it individually.

## Shared Text

Share verification info for several apps at once. Multiple entries separated by blank lines are accepted on receive. When opening shared text with multiple entries, the app list filters to show matching apps only, with icons indicating hash match status. You can bulk-add all verified matches to your database from the filtered list. AppVerifier also handles `ACTION_SEND` and `ACTION_VIEW` intents so you can share text or APK files directly from other apps.

## Clipboard Verification

Verify from clipboard with a single button on the startup screen. Successful clipboard verifications add a blue checkmark in the app list. The checkmark can be toggled on or off in settings and cleared separately. Individual checkmarks can be removed by long-pressing the app in the list.

## Hash-Only Verification

Paste or share a SHA-256 hash on its own and AppVerifier BG will find every installed app signed with that certificate. Handy when someone shares just a hash without a package name. If multiple installed apps share the same signing key — common when a developer uses one certificate for all their apps — a picker lets you choose the right one. Only verify the app you actually got the hash for; a matching certificate doesn't mean every app from that developer is safe to trust. Works from the clipboard, shared text, or an intent from another app.

## User Database

Save an app's verification info so you can check it later without needing the shared text. Add entries one at a time from the verification screen or bulk-add from a shared text filtered list. Supports import and export in JSON, text, and YAML formats (auto-detected on import, choose on export). Import lets you combine with existing entries or replace them, and shows a summary of what changed. Entries can be removed individually by long-pressing the app in the list, or removed in batch from selection mode.

**Plain text** — entries separated by a blank line:
```
com.example.app
AA:BB:CC:DD:EE:FF:00:11:...

com.other.app
11:22:33:44:55:66:77:88:...
```

**JSON** — array of objects with packageName, hashes, and hasMultipleSigners:
```json
[
  {"packageName": "com.example.app", "hashes": ["AA:BB:CC:DD:EE:FF:00:11:..."], "hasMultipleSigners": false}
]
```

**YAML** — documents separated by `---`:
```yaml
packageName: com.example.app
hashes:
  - AA:BB:CC:DD:EE:FF:00:11:...
---
packageName: com.other.app
hashes:
  - 11:22:33:44:55:66:77:88:...
```

## Combined Database Status

See internal and user database results side by side on the app list and verification screen. A setting lets you choose between both, internal only, or user database only.

## Privacy Guides Database

The internal database is extended with entries from [privacyguides/verified-apps](https://github.com/privacyguides/verified-apps), updated with each build. The database download is verified against GitHub attestations before every build.

## Community Hashes

Nightly builds include a downloadable text file with hashes shared by users on the GrapheneOS forum. These are not added to the internal database — import them into your user database if you wish. Cross-verify against multiple sources before relying on any entry.

## Share All Apps

Share every installed app's verification info as text from the settings screen.

---

For the original README with download, community, and contributing info see https://github.com/soupslurpr/AppVerifier.

---

This repo is mirrored to [Codeberg](https://codeberg.org/unwanted9855/AppVerifierBG). Releases and release assets are synced automatically.
