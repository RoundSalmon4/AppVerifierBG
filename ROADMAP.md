# Roadmap

## □ Visual overhaul

Refresh the UI to have a distinct look from upstream — custom color
palette instead of defaults, updated component styling, and a cleaner
layout across screens.

**Status: In progress.**

## □ .apks split APK support

The file picker and shared APK handling assume a single `.apk` file, but
split APKs (`.apks`) are common for large apps (e.g., from APKMirror,
Aurora Store backups). Should handle the container format, extract the base
APK, and verify signatures.

Ref: [#227](https://github.com/soupslurpr/AppVerifier/issues/227)

**Status: Not started.**

## ✅ Submit to F-Droid

Metadata submitted to the F-Droid repository at
[fdroid/fdroiddata!39736](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/39736).
MR merged, awaiting the F-Droid build pipeline.

**Status: Merged.**

## ✅ Reorder the app list

The list has a lot of icons now — internal DB matches, user DB, clipboard, shared text, debug. Sorting by name or status and filtering would help find what you're looking for. Also would let you quickly see all the failures at once instead of scrolling through everything.

Ref: [#61](https://github.com/soupslurpr/AppVerifier/issues/61)

**Status: Completed.** Added sort by name, internal DB status, user DB matches, debug builds, clipboard verified, and shared text — each pushes matching items to the top. Filter chip shows only failures. Default sort order configurable in settings. Unavailable sort modes are hidden from the dropdown.

## ✅ Check pasted hashes are valid

Right now if you paste something that isn't actually a SHA-256 hash it just silently fails. Should validate the length and show a message if its wrong so you know you didn't fully copy it.

Ref: [#3](https://github.com/soupslurpr/AppVerifier/issues/3)

**Status: Completed.** Pasted text is validated for proper SHA-256 format (64 hex chars or 95 colon-separated) before comparing. Invalid formats show a clear error instead of silently failing.

## ✅ Show which hash is which on failure

When verification fails it shows a hash but doesn't say if thats the one from the app or the one from the database. Just needs labels — "Expected" and "Found" — under the failure info. Simple clarification, not a big feature.

Ref: [#180](https://github.com/soupslurpr/AppVerifier/issues/180)

**Status: Completed.** Failed verifications now label hashes as "Expected" (what was pasted) and "Found" (on-device fingerprint), so you can tell which is which.
