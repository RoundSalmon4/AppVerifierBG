# Roadmap

## Reorder the app list

The list has a lot of icons now — internal DB matches, user DB, clipboard, shared text, debug. Sorting by name or status and filtering would help find what you're looking for. Also would let you quickly see all the failures at once instead of scrolling through everything.

Ref: [#61](https://github.com/soupslurpr/AppVerifier/issues/61)

## Check pasted hashes are valid

Right now if you paste something that isn't actually a SHA-256 hash it just silently fails. Should validate the length and show a message if its wrong so you know you didn't fully copy it.

Ref: [#3](https://github.com/soupslurpr/AppVerifier/issues/3)

## Show which hash is which on failure

When verification fails it shows a hash but doesn't say if thats the one from the app or the one from the database. Just needs labels — "Expected" and "Found" — under the failure info. Simple clarification, not a big feature.

Ref: [#180](https://github.com/soupslurpr/AppVerifier/issues/180)
