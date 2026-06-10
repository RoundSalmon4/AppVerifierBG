# Changelog

Dates are based on UTC (releases typically around 2am UTC).

## 2026-06-11

- Internal database status chip now shows separate Hash and Domain info with dedicated dialogs.

## 2026-06-09

- .apks split APK container support.
- Normalized text sizing across error cards, labels, and dialogs.
- DB dialog source names now use gold accent with readable body text.
- Dialog titles no longer show underscores (NOT_FOUND -> NOT FOUND).
- Renamed "Verification Status" to "Text Match" for clarity.

## 2026-06-08

- Privacy policy updated.
- Added .apks split APK container support.

## 2026-06-07

- Blue/gold theme replaces dynamic color.
- Startup screen redesigned with card-based action items.
- Verification screen: chip-based status indicators, collapsible hash comparison toggle.
- Privacy policy no longer forces acceptance on first launch. Moved from a gated startup screen to an action item on the main screen.
- App list: async icon loading with LRU cache, O(1) internal database lookups.
- Long-press an app to remove its user database entry or clipboard checkmark individually.

## 2026-06-05

- Credits now include upstream AppVerifier, Lifecycle ViewModel Ktx, and Kotlin Coroutines Android.

## 2026-06-04

- Fixed Privacy Guides import parser to handle `|-` YAML block scalar fingerprints (inline and multi-line).
- System app filtering now uses `FLAG_SYSTEM` instead of `MATCH_SYSTEM_ONLY`.
- Added Privacy Guides database license (CC BY 4.0) to app credits. ([#16](https://github.com/RoundSalmon4/AppVerifierBG/pull/16))
- Internal database and paste verification now use exact hash matching instead of subset checks. ([#13](https://github.com/RoundSalmon4/AppVerifierBG/issues/13))
- Enabled immutable releases to prevent replaced attachments. ([#14](https://github.com/RoundSalmon4/AppVerifierBG/issues/14))

## 2026-06-02

- Removed upstream soupslurpr/AppVerifier database dependency. Existing APPVERIFIER entries in InternalVerificationInfoDatabase.kt remain as-is. No data is lost.
- Releases now use a proper semver version with its own release workflow instead of piggybacking on the nightly tag.
- Hash matching now allows the reference to have extra signatures.

## 2026-06-01

- Self-verification now shows a SKIPPED status instead of a misleading hash mismatch.
- Apps using signature rotation / key rotation no longer show false hash mismatches.

## 2026-05-31

- Nightly builds are now reproducible — the same tag always produces the same APK. The 5/30 build had timestamps pinned to the build time instead of the commit date, and R8's output ordering wasn't deterministic. Both issues are now fixed.
- Pasted hashes are validated as proper SHA-256 before comparing, with a clear error if the format is wrong.
- Failed verifications now label hashes as "Expected" and "Found" so you can tell which is which.
- App list can be sorted by name, database status, debug builds, clipboard verified, and shared text with a dropdown that hides unavailable options.
- Added a filter chip to show only failures in the app list.
- Default sort order is now configurable in settings.

## 2026-05-30

- Renamed repository to AppVerifierBG
- Debug signing certificates are now detected and shown as insecure

## 2026-05-29

- Removed pitch black background option
- Added version number to settings screen
- Updated references from "AppVerifier" to "AppVerifier BG" throughout the app
- Added CHANGELOG.md — nightly release notes are sourced from this file
- Renamed to AppVerifier BG with its own signing key and nightly builds
- Privacy Guides database is now verified against GitHub attestations before each build ([#12](https://github.com/RoundSalmon4/AppVerifierBG/pull/12))

## 2026-05-28

- Added nightly community hashes from GrapheneOS forum
- Added multi-format export (JSON, YAML, text) for user database
- Updated privacy policy to cover fork features
- Fixed import counting when combining or replacing database entries
- Skip already-imported entries in shared list verified count
- Removed donation screen, trimmed credits

## 2026-05-27

- Added import result report with download for skipped lines
- Added clear database confirmation dialog
- Fixed import format detection and error messaging

## 2026-05-26

- Added user database with import and export (JSON, YAML, text)
- Added combined internal and user database status display
- Added Privacy Guides database sync, updated with each build

## 2026-05-25

- Added multi-app text sharing and filtered app lists
- Added share all verification info for every installed app
- Added clipboard verification with checkmark

## Earlier

- Version 13 release
- Initial upstream development by soupslurpr
