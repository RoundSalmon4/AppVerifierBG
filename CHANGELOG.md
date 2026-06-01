# Changelog

Dates are based on UTC.

## 2026-06-02

- Removed upstream soupslurpr/AppVerifier database dependency. Existing APPVERIFIER entries in InternalVerificationInfoDatabase.kt remain as-is. No data is lost.
- Releases now use a proper semver version with its own release workflow instead of piggybacking on the nightly tag.
- Hash matching now allows the reference to have extra signatures.

## 2026-06-01

- Self-verification now shows a SKIPPED status instead of a misleading hash mismatch.
- Apps using signature rotation no longer show false hash mismatches.

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
