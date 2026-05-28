# AppVerifier BG

Verify installed apps against shared signature hashes, the internal database, or a user-created database.

This fork extends the original AppVerifier — still does everything the original does, plus the features below.

## Added Features

**User Database.** Save an app's verification info so you can check it later without needing the shared text. Supports import and export in JSON, text, and YAML formats (auto-detected):

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

**Combined Database Status.** See internal and user database results side by side on the app list and verification screen. A toggle lets you choose between both, internal only, or user database only.

**Multi-App Text Sharing.** Share verification info for several apps at once. Multiple entries separated by blank lines are accepted on receive.

**Filtered App Lists.** Shared text with multiple apps filters your installed list to show matching apps only, with icons indicating hash match status.

**Clipboard Verification.** Verify from clipboard with a single button on the startup screen. Successful clipboard verifications add a blue checkmark in the app list.

**Share All Apps.** Share every installed app's verification info as text.

**Privacy Guides Database.** The internal database is extended with entries from [privacyguides/verified-apps](https://github.com/privacyguides/verified-apps), updated with each build.

**Community Database.** Nightly builds include hashes collected from the GrapheneOS forum, submitted by other users. Cross-verify against multiple sources before relying on these entries.

---

For the original README with download, community, and contributing info see https://github.com/soupslurpr/AppVerifier.
