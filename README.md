# AppVerifier - Feature Enhancements

This fork adds to the original AppVerifier — still does everything the original does, plus the features below.

---

## Added Features

### User Database
Save an apps verification info so you can check it again later without needing the shared text. The database stores package names and their hashes, and you can export it as JSON or import lists in JSON, text, or YAML format.

The import accepts these formats (format is auto-detected):

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

### Combined Database Status
See internal and user database results side by side on both the app list and the verification screen. A three-way toggle in settings lets you choose between showing both, internal only, or user database only. Purple checkmarks appear in the app list for user database matches.

### Multi-App Text Sharing
Share verification info for several apps at once instead of one at a time. Send multiple entries separated by blank lines — the format is just package name then hashes, repeated.

### Filtered App Lists
When you receive shared text with multiple apps, this branch filters your installed app list to show only the ones that match. Each app gets a green or orange icon so you can see at a glance which ones check out. A Done button exits the filtered view.

### Paste From Clipboard
The startup screen has a button to paste multi-entry text from your clipboard, same behavior as receiving shared text.

### Shared Hash Comparison
When viewing an app that was included in shared text, the verification status (shown in orange) tells you whether the installed hashes match what was shared. Tapping it shows more info.

### Clipboard Verification
When you verify an app from clipboard and it passes, a blue checkmark appears next to it in the app list. An option in settings lets the blue checkmark override a failed internal database match.

### Share All Apps
A settings option that shares every installed apps verification info as text, so you can send your full list to someone.

### Clickable Database Status
Tap the database status row on the verification screen to see match details and sources.

### Privacy Guides Database
The internal database is extended with entries from [privacyguides/verified-apps](https://github.com/privacyguides/verified-apps), updated with each new build. Hashes from both the original upstream and this database are preserved when they overlap.

---

For the original README with download, community, and contributing info see the main upstream repository at https://github.com/soupslurpr/AppVerifier.
