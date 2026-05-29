# Contributing

Thanks for your interest in contributing!

If you want to suggest a feature or notify us about a bug, please use the issue tracker.

Before working on a feature, please make sure to discuss the planned implementation in the issue for
the feature to ensure it meets the project's requirements.

## Internal database

The internal verification info database is sourced from:

- [privacyguides/verified-apps](https://github.com/privacyguides/verified-apps) — downloaded and
  verified against GitHub attestations before each build
- [soupslurpr/AppVerifier](https://github.com/soupslurpr/AppVerifier) — entries from the upstream
  internal database that are not already covered by Privacy Guides are preserved

Individual contributions to the internal database are not accepted. If you would like an app to be
added, please submit it to [privacyguides/verified-apps](https://github.com/privacyguides/verified-apps/issues/new?template=app-submission.yml).
Once accepted upstream, it will be included in the next build.

If you have a suggestion for another upstream source to include, please open an issue.

## General guidelines

Translations won't be accepted at this time.

Java code is not accepted; only Kotlin will be used. Views should be avoided — Jetpack Compose
only.
