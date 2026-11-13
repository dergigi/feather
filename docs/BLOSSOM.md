# Blossom server settings

Open your own profile’s settings and tap the list icon beside the wallet. Feather reads your public BUD-03 list before enabling editing. Edit URLs, add or remove rows, and use the arrows to put the most trusted server first. Tap Send, confirm the public replacement, then approve it in your Android signer. Closing the sheet cancels unfinished work and discards local edits. There is no publication on login or reset.

[BUD-03](https://github.com/hzrd149/blossom/blob/master/buds/03.md) requires at least one `server` tag. Feather therefore does not publish empty lists. The editor supports 1–16 HTTPS servers, matching Feather’s upload limit and HTTPS media policy. HTTP-only entries from another client remain visible for correction; they are not silently replaced with defaults. URLs cannot contain credentials, queries or fragments. Equivalent normalized URLs are duplicates. Server paths are supported.

Publication uses kind 10063 with empty content, preserves non-server tags and extension fields on retained server tags, and keeps the chosen order. It uses the connected signer without handling private keys. Discovery refreshes NIP-65 account routes and checks the list on all advertised write relays (up to 32). Seeds bootstrap accounts without advertised write relays and supplement NIP-65 discovery; seed outages do not block existing authoritative relay reads. Every required relay must complete its read, and an absent list requires completion on every queried route; a known newest event must also be witnessed on a completed relay. Missing or incomplete reads block replacement. Timestamp ties use the lowest event ID. This cannot guarantee knowledge of events on undiscovered relays.

The list is checked again after signer approval. Concurrent changes require reopening the editor to review the newest list. Failed or uncertain publication is never shown as saved and is not automatically retried. A successful relay acknowledgement updates the existing event database; attachment uploads use that ordered list immediately and after relaunch. A later list from another client can supersede it normally.

## Manual device review

These checks require an Android device and a connected signer; automated URL/tag tests do not establish these outcomes:

- Open settings on an account with an existing list and verify order and unknown tags survive an edit/publication.
- Create a first list, confirm it in the signer, and confirm an independent client sees ordered server tags and empty content.
- Reject signer approval or close the sheet during discovery/signing; verify no saved-success indication appears. If a network send already started, reopen to resolve uncertain delivery.
- Disconnect or reject publication at the relays; verify the error and refresh before retrying.
- Change the list in another client while the editor or signer is open; verify replacement is blocked.
- Switch accounts during discovery/signing; verify the previous draft disappears and cannot be published under the new account.
- Publish a reordered list, share an image, then relaunch and share again; verify the first listed server is attempted first both times.
- Try HTTP, credentials, fragments, malformed URLs, duplicate URLs and removing every row; verify publication is disabled with a useful message.

## Contribution validation

Local debug compilation and APK assembly succeeded. The four Blossom JVM tests pass. The full suite has four failures among 24 tests; untouched upstream master (`aece430`) reproduces exactly those four failures among its 20 tests. They cover existing media metadata/autoplay, relay-list bounds and mention/reply classification.

Android Lint completes analysis but fails with 15 errors, 89 warnings and two hints. Untouched upstream master reproduces the same findings (compared by issue ID, severity, message and filename, ignoring shifted line numbers). No lint finding points to the new Blossom files. No test/lint suppression was added.

No emulator, real signer approval, profile publication or live media upload was performed. The debug APK was built only for validation and was not distributed. The manual checklist above remains for device review.
