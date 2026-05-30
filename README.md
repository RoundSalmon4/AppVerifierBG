# AppVerifier BG

Verify installed apps against shared signature hashes, the internal database, or a user-created database.

This fork extends the original AppVerifier — still does everything the original does, plus the features below.

## Verification

Pasted hashes are checked for valid SHA-256 format and show a clear error if something's wrong. When verification fails, hashes are labeled "Expected" (what you pasted) and "Found" (on-device fingerprint) so you can tell which is which. Apps signed with debug certificates are flagged as insecure.

## App List

Every installed user app shows status icons for internal database matches, user database entries, clipboard verification, and shared text matches all at a glance. Sort by name, database status, debug builds, clipboard verified, or shared text — pick whichever you need from the dropdown and it only shows modes with matching data. A filter chip hides everything except failures. The default sort order can be set in settings. A search bar lets you filter by name or package name.

## Shared Text

Share verification info for several apps at once. Multiple entries separated by blank lines are accepted on receive. When opening shared text with multiple entries, the app list filters to show matching apps only, with icons indicating hash match status. You can bulk-add all verified matches to your database from the filtered list. AppVerifier also handles `ACTION_SEND` and `ACTION_VIEW` intents so you can share text or APK files directly from other apps.

## Clipboard Verification

Verify from clipboard with a single button on the startup screen. Successful clipboard verifications add a blue checkmark in the app list. The checkmark can be toggled on or off in settings and cleared separately.

## User Database

Save an app's verification info so you can check it later without needing the shared text. Add entries one at a time from the verification screen or bulk-add from a shared text filtered list. Supports import and export in JSON, text, and YAML formats (auto-detected on import, choose on export). Import lets you combine with existing entries or replace them, and shows a summary of what changed.

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
