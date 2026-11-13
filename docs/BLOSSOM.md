# Blossom server settings

Open your own profile’s settings and tap the list icon beside the wallet. Feather reads your public BUD-03 list before enabling editing. Edit URLs, add or remove rows, and use the arrows to put the most trusted server first. Tap Send, confirm the public replacement, then approve it in your Android signer. Closing the sheet cancels unfinished work and discards local edits. There is no publication on login or reset.

[BUD-03](https://github.com/hzrd149/blossom/blob/master/buds/03.md) requires at least one `server` tag. Feather therefore does not publish empty lists. The editor supports 1–16 HTTPS servers, matching Feather’s upload limit and HTTPS media policy. HTTP-only entries from another client remain visible for correction; they are not silently replaced with defaults. URLs cannot contain credentials, queries or fragments. Equivalent normalized URLs are duplicates. Server paths are supported.

Publication uses kind 10063 with empty content, preserves non-server tags and extension fields on retained server tags, and keeps the chosen order. It uses the connected signer without handling private keys. Discovery refreshes NIP-65 account routes and queries advertised write relays plus three bootstrap seeds (up to 32 routes per stage, four sockets at a time, five seconds per relay). Each stage needs at least one usable EOSE; offline account relays or seeds do not veto completed reads. Valid events are compared with the newest verified cached event, which is retained even when relays return older events or no event. Timestamp ties use the lowest event ID.

Snapshots record queried/completed routes and whether a completed relay returned the newest known event. Incomplete coverage or an unwitnessed cached winner shows a visible warning and requires explicit confirmation of publication despite possible unseen changes. Partial absence means only that no list was found, not that none exists: users may explicitly create after that warning. All-offline discovery fails without erasing cache. This cannot guarantee knowledge of events on undiscovered or offline relays. Both server and account-relay discovery use this policy; no other list editor policy is changed.

The list is checked again after signer approval. Coverage changes alone are not conflicts after informed confirmation. If coverage first becomes incomplete during refresh, publication stops so the warning can be reviewed after reopening. Concurrent event changes require reopening the editor to review the newest list. Failed or uncertain publication is never shown as saved and is not automatically retried. A successful relay acknowledgement updates the existing event database; attachment uploads use that ordered list immediately and after relaunch. A later list from another client can supersede it normally.

## Manual device review

These checks require an Android device and a connected signer; automated URL/tag tests do not establish these outcomes:

- Open settings on an account with an existing list and verify order and unknown tags survive an edit/publication.
- Create a first list, confirm it in the signer, and confirm an independent client sees ordered server tags and empty content.
- Reject signer approval or close the sheet during discovery/signing; verify no saved-success indication appears. If a network send already started, reopen to resolve uncertain delivery.
- Leave one query relay online and account relays offline; verify the existing or absent list is editable with a coverage warning, and the confirmation repeats the uncertainty. Disconnect every query relay and verify reading fails without clearing cached data.
- Disconnect or reject publication at the relays; verify the error and refresh before retrying.
- Change the list in another client while the editor or signer is open; verify replacement is blocked.
- Switch accounts during discovery/signing; verify the previous draft disappears and cannot be published under the new account.
- Publish a reordered list, share an image, then relaunch and share again; verify the first listed server is attempted first both times.
- Try HTTP, credentials, fragments, malformed URLs, duplicate URLs and removing every row; verify publication is disabled with a useful message.

## Contribution validation

Local debug compilation and APK assembly succeeded. The ten Blossom JVM tests pass, covering mixed online/offline response policy, partial absence, all-offline failure, cached-newest retention and stale/account conflicts. The original contribution’s full suite had four failures among 24 tests; untouched upstream master (`aece430`) reproduces exactly those four failures among its 20 tests. They cover existing media metadata/autoplay, relay-list bounds and mention/reply classification.

Android Lint completes analysis but fails with 15 errors, 89 warnings and two hints. Untouched upstream master reproduces the same findings (compared by issue ID, severity, message and filename, ignoring shifted line numbers). No lint finding points to the new Blossom files. No test/lint suppression was added.

No emulator, real signer approval, profile publication or live media upload was performed. The debug APK was built only for validation and was not distributed. The manual checklist above remains for device review.
