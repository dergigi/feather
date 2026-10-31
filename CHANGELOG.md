# Changelog

All notable changes are documented here, following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Commits follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

## [Unreleased]

## [1.10.3] - 2026-10-31

### Changed
- Restore compact article-tab previews: title and summary beside a small thumbnail, with reading time next to the header timestamp. Keep large recipe cards and full article covers.

## [1.10.2] - 2026-10-31

### Changed
- Place article reading time immediately left of its timestamp in cards and the reader.
- Show the article title on the reader toolbar, shortened to one line with an ellipsis; tap it to return to the top.

### Added
- Publish a visual recipe-tab interview on the Feather blog, with cropped screenshots and a dedicated tab illustration.

## [1.10.1] - 2026-10-31

### Changed
- Give articles and recipes shared photo-led cards and a larger responsive reader cover, with titles over a bottom gradient, familiar author/timestamp placement, and room for long titles.
- Place Recipes immediately after Polls and before Reactions.
- Align recipe cards with regular posts: author and avatar at the top left, the standard timestamp at the top right, and the title over a shaded gradient on the photo.

## [1.10.0] - 2026-10-31

### Added
- Add a fork-and-knife Recipes tab for zap.cooking recipes, including its legacy `nostrcooking` tags. Discover public recipes independently of follows, browse photo-led cards, and read ingredients and instructions with existing article and note actions.
- Show recipe preparation time, cooking time and servings when supplied in structured tags. Keep recipes separate from Articles and expose the category in profile and hashtag views when matching recipes are present.

## [1.9.1] - 2026-10-31

### Fixed
- Resolve profile mentions and note references in notification previews before shortening them. Show cached note excerpts and refresh labels as profile metadata arrives, while retaining compact rows and moderation filtering.

## [1.9.0] - 2026-10-31

### Added
- Read BUD-10 `blossom:` image, video, and audio links inline, including gallery viewing, saving, and cached playback. Resolve server hints, public author server lists, and fallback servers over HTTPS; preserve original links when sharing or copying.
- Verify hashes on complete Blossom downloads and check advertised sizes, including total sizes on ranged video responses.

## [1.8.7] - 2026-10-30

### Changed
- Add a small lightning bolt inside the zap comment icon.

## [1.8.6] - 2026-10-29

### Changed
- Use regular Android toasts for saved images/videos and simple permission, selection-length, invalid-input, and missing-app prompts. Keep copyable diagnostic errors and actionable messages.

### Fixed
- Allow replies to comments with missing root metadata: recover the root kind from its address, cached event, or external identifier; fall back to a direct comment on the parent when its original scope is unavailable.

## [1.8.5] - 2026-10-27

### Changed
- Emphasize the report reason with a filled picker, collapse optional details until requested, and remove the public-report notice.

## [1.8.4] - 2026-10-27

### Changed
- Add a checked-by-default “Mute after reporting” checkbox; mute the account only after the report succeeds.
- Compact the reporting sheet by placing the reason picker beside the reported account, replacing the standalone flag and separate reason row.

## [1.8.3] - 2026-10-26

### Fixed
- Include relay hints in replies, quotes, reactions, reposts, and shared or copied Nostr references. Prefer the relay that delivered the event so other clients can find app listings too.

## [1.8.2] - 2026-10-26

### Changed
- Simplify photo composition: add photos with a plus tile beside the images, keep upload progress there, and show only Send at the bottom.

## [1.8.1] - 2026-10-26

### Fixed
- Stop copying photo captions into the title, which displayed the same text twice in other clients.
- Restore the missing Save all images icon in the gallery menu.

## [1.8.0] - 2026-10-25

### Added
- Create kind-20 picture posts from the Photos tab using a photo-with-feather compose button, images above the caption, separate saved drafts, and offline delivery.

### Changed
- Repository issue lists use pull-to-refresh and show bug-report and feature-request actions in the toolbar.

## [1.7.2] - 2026-10-25

### Fixed
- Reject malformed relay hosts using the socket client's URL parser and handle connection setup failures without crashing. Invalid profile relays no longer abort reads from the remaining relays.

## [1.7.1] - 2026-10-25

### Fixed
- Move the translation control and its progress indicator immediately left of the note timestamp.
- Show the bug or feature icon beside the repository name in the issue composer.

## [1.7.0] - 2026-10-25

### Added
- Offline Translator integration: translate notes and articles from the overflow menu, switch back to the original inline, or translate selected passages in a compact bottom sheet. Uses the separately installed app and downloaded language packs, with a bounded session cache and no cloud fallback.

## [1.6.1] - 2026-10-25

### Fixed
- Display raw profile mentions in articles and issue bodies as tappable names, preserving Markdown styling and literal code.
- Keep the main navigation pill visible for a short grace period after switching tabs, then gently return to its scroll-hidden position.
- Render embedded Nostr notes in long-form articles using the shared cache, with up to 16 references per article instead of the short-note limit of three.

## [1.6.0] - 2026-10-25

### Added
- Native repository issue browser with All/Open/Closed filters, cached results, status icons, and more-results loading. Open it from an issue's repository name or Feather Settings.
- Report issues to the selected repository using its advertised relays, with separate drafts per repository.

### Changed
- Align the report composer's close control with the regular toolbar and show the signed-in account avatar at the top right.

## [1.5.1] - 2026-10-25

### Fixed
- Show one podcast player when different links in a note resolve to the same audio episode.

## [1.5.0] - 2026-10-25

### Fixed
- Render Nostr references in issue bodies as embedded cards in place, preserving Markdown and using cached notes and profiles.

### Changed
- Share issues through GitWorkshop instead of njump.

### Added
- Issue icons show open, resolved, closed, or draft status directly in lists and detail views, using cached NIP-34 status updates from their author or repository maintainers.

## [1.4.0] - 2026-10-25

### Added
- Drag or flick the main navigation pill horizontally to reach distant feeds quickly.

### Changed
- Show text labels alongside icons in note, profile, and image-gallery overflow menus.

## [1.3.0] - 2026-10-25

### Added
- Queue signed reactions, reposts, and their undo requests for offline and background delivery. Keep the selected reaction/repost state across restarts, reuse the same signed events on retries, and share the existing two delivery slots.
- Undo atomically removes the queued original and saves its deletion request, including when the original may already be in flight.

### Changed
- Keep Share visible in note and profile title bars; move Copy and Open in another app into a compact overflow menu.

## [1.2.0] - 2026-10-25

### Added
- Deliver already-signed queued posts and replies in the background using Android WorkManager when connectivity returns, including after process restarts and device reboots. Share the existing two delivery slots and parent-before-reply ordering with foreground delivery; pause on logout.

## [1.1.1] - 2026-10-25

### Fixed
- Clear pending posts when a relay returns the exact verified event, even if its publishing acknowledgement was missed. Reconcile existing pending posts against saved relay provenance.
- Keep the hourglass still while offline or waiting between delivery attempts; animate only during active publication.

### Changed
- Wake post delivery immediately on enqueue and reconnection, with up to two concurrent deliveries while preserving queued parent-before-reply ordering.

## [1.1.0] - 2026-10-25

### Added
- Persist signed posts and replies in an on-device outbox, close the composer after saving, and display them immediately. Retry the same events when relay connections return while Feather is open, including after restarts; preserve queued posts outside the disposable cache.
- Show a muted hourglass beside pending posts’ timestamps, flipping every two seconds until a relay confirms delivery.

### Changed
- Order revealed avatar actions as Follow/Unfollow, Report, then Mute/Unmute in feeds, profiles, and notifications.

## [1.0.0] - 2026-10-25

### Changed
- Add Follow/Unfollow to the actions revealed by long-pressing an author avatar or name, reusing the existing follow-list safeguards and Undo.
- Match revealed action-button diameters to their avatar, including the larger profile avatar.

## [0.100.1] - 2026-10-24

### Added
- Bundle Halo’s official client icon and link its attribution to haloapp.fyi.

## [0.100.0] - 2026-10-23

### Added
- Add muted-red icons alongside reporting reason labels.
- Add “AI slop” as a reporting reason, published as the standard NIP-56 `other` type with the reason preserved in the report text.
- Delete your own notes and replies through a red trash action revealed by long-pressing the author avatar or name. Hold to confirm; publish a signed NIP-09 request and hide through the existing deletion handling. Failed publication retries reuse the same signed request.

## [0.99.0] - 2026-10-23

### Added
- Render linked MP3 and other audio files as inline players in notes and quoted notes, with background playback, system controls, and playback speed selection. Recognize common audio extensions, imeta MIME types, and audio detected by link previews.

## [0.98.7] - 2026-10-23

### Added
- Bundled Nostrich client icon with an in-app link to its Zapstore listing.

## [0.98.6] - 2026-10-23

### Changed
- Reposting also rebroadcasts the original signed event to the same relays in the background. Skip protected events and silently tolerate rebroadcast failures.

## [0.98.5] - 2026-10-23

### Fixed
- Keep images, GIFs, videos, and quoted notes in their original relative order instead of always placing attachments after quotes.

## [0.98.4] - 2026-10-23

### Changed
- Tighten ordinary note line spacing for easier reading and scanning while retaining the larger focused-note typography.

## [0.98.3] - 2026-10-22

### Changed
- Profile avatar controls use a one-tap mute/unmute toggle with a stronger red active state. Keep the controls open after toggling.

## [0.98.2] - 2026-10-22

### Fixed
- A tap outside revealed avatar controls dismisses them without opening the tapped avatar, banner, or note; scrolling remains available.
- Wait for the signer handoff before refreshing account lists instead of failing after 15 seconds in the background. Recheck list freshness before retrying publication.

### Changed
- Tapping the profile mute indicator reveals the avatar controls. Muted accounts offer hold-to-unmute there instead of a duplicate mute action.
- Rename the report reason to “Spam / scam.”

## [0.98.1] - 2026-10-22

### Fixed
- Load both avatars in the report sheet using the shared media cache and cache-first account profiles.

### Changed
- Long-press author names as well as avatars to reveal moderation controls; tighten the gap beside the avatar without moving it.
- Keep the public-report notice beside Send below the explanation field, and label the spam reason “Spam and scams.”

## [0.98.0] - 2026-10-22

### Added
- Long-press author avatars in notes, quoted notes, and profiles to reveal compact red mute and report controls. Normal taps retain their existing navigation.

## [0.97.0] - 2026-10-22

### Added
- Report accounts from the red flag beside Mute in notification rows. A bottom sheet offers standard NIP-56 reasons, optional details, and explicit public submission; reporting does not automatically mute the account.

## [0.96.0] - 2026-10-22

### Changed
- Hold to unmute accounts on profiles and in settings. Short taps animate a half-filled ring that gently drains; a full hold confirms with haptic feedback.
- Use a subdued red tint for muted-account indicators in search and unmute controls on profiles and in the muted-accounts list.

## [0.95.2] - 2026-10-22

### Changed
- Remove extra layout padding around the notification mute button and place it closer to the avatar.
- Place Unmute first in the profile action row, before Follow.

## [0.95.1] - 2026-10-22

### Changed
- Include muted accounts in profile search with a subdued mute indicator; allow opening them directly by Nostr identifier.
- Match the notification mute control’s visible circle to the avatar size, with a muted red surface and a full-size touch target.

## [0.95.0] - 2026-10-22

### Added
- Manage muted accounts from settings and unmute directly beside Follow on muted profiles.
- Copy, open in another Nostr app, and share profiles from the top bar.
- Long-press a notification to reveal an account-mute button between its avatar and text. Hide the sender’s notifications immediately and save the mute privately to the existing NIP-51 list, preserving other clients’ entries.

## [0.94.4] - 2026-10-21

### Fixed
- Restore the previous profile toolbar and sticky-tab layout, reverting the transparent floating profile toolbar introduced in 0.94.3.

## [0.94.3] - 2026-10-21

### Changed
- Extend profile banners beneath a transparent toolbar with a floating back button. Keep scrolled profile names readable and profile tabs pinned below the controls, without an empty toolbar strip above the banner.

## [0.94.2] - 2026-10-21

### Fixed
- Keep block-height lookup failures silent and show the normal date while unavailable. Fall back to a second Mempool provider, cool down failing providers across all notes, and retain successful results even if optional disk caching fails.

## [0.94.1] - 2026-10-21

### Fixed
- Publish up to four independent reaction/repost changes and process two independent zaps concurrently, while preserving same-note ordering, serialized signer access, and duplicate-payment protection.
- Give zaps, reactions, reposts, and votes independent bounded queues so a payment backlog cannot exhaust the capacity for likes or other social actions. Allow bursts of up to 64 queued reactions; keep reaction/repost overflow quiet and retain journaled payments.

## [0.94.0] - 2026-10-21

### Added
- Tap a client name or logo to open its bundled Zapstore listing inside Feather, or its official website when no listing is available.

## [0.93.2] - 2026-10-21

### Changed
- Place client icons after the client name; bundle muted logos for 31 Nostr clients and publishing apps, with a generic fallback for unknown clients.

## [0.93.1] - 2026-10-21

### Fixed
- Accept emoji hashtags such as #⭕ in search, note links, and hashtag tags on new posts.

### Changed
- Show the client icon and name right-aligned on the same compact row as observed relays.

## [0.93.0] - 2026-10-21

### Added
- Collapse suspected duplicate replies in threads after five distinct matching events from the same non-followed author, with an icon-only reveal control and cached content preserved.
- Show the declared client name in expanded note details when the note includes a client tag.

## [0.92.1] - 2026-10-20

### Changed
- Use a lightning bolt with sideways branching arrows for zap splits.

### Added
- Website NIP-05 identity for Feather at `_@feather.dergigi.com`.

## [0.92.0] - 2026-10-20

### Added
- Honor NIP-57 zap splits when paying, preserve the selected total with deterministic rounding, and journal recipient payments so partial or uncertain payments can be recovered without duplicate sends.
- Compact declared zap-split recipient avatars and percentages in expanded note details and the long-press amount picker; tap recipients to open their profiles.

### Fixed
- Prioritize visible authors over optional detail-panel participants and restore cached names before engagement work.
- Persist verified relay observations across restarts, including finite profile/history reads. Show known relays independently of engagement loading and retain details after transient read failures.

## [0.91.2] - 2026-10-20

### Fixed
- Preserve the current feed visit’s retained posts when a smaller cache window is restored, keeping back-navigation scroll anchors available.
- Display cached feed and notification rows before optional discovery and engagement work; restore notifications during startup before cache pruning. Keep cached-view updates alive after a transient cache read failure.

### Changed
- Link to the homepage directly in the Zapstore description.

## [0.91.1] - 2026-10-20

### Fixed
- Show Photos and Videos beside Home immediately at startup, keeping the main tab switcher stable while content loads.

### Changed
- Tighten spacing between avatar rows and individual avatars in note details.
- Group Zapstore screenshots by theme: dark first, with the feather gallery second, and light screenshots last.

## [0.91.0] - 2026-10-20

### Changed
- Move long-note expansion beside the text ellipsis, matching profile biographies; collapse expanded text inline.

### Added
- Subtle action-bar chevron opens compact, tappable avatar rows for reposts, emoji reactions, and verified zaps, plus observed relay hostnames. Read existing cached activity, group repeated zaps by sender, and preserve thread layout.

## [0.90.1] - 2026-10-20

### Fixed
- Add video lock-screen artwork: use the advertised thumbnail or a cached playback frame, then the author’s avatar, with Feather artwork as the final fallback.

## [0.90.0] - 2026-10-20

### Added
- Android lock-screen and notification controls for fullscreen or unmuted videos, with play/pause, 10-second seeking, playback speed, and a link back to the source note. Keep the selected video playing when the screen locks; muted feed previews stay out of system controls.

### Changed
- Replace hand-drawn playback-speed numerals with consistent Roboto font outlines for audio and video system controls.

## [0.89.0] - 2026-10-20

### Added
- Navigate the full-screen image gallery with Left and Right arrow keys on an external keyboard, including when controls are hidden.

## [0.88.2] - 2026-10-20

### Fixed
- Tap a post/reply publication-failure message to reopen its saved draft. Keep the copy icon for full error details and retry the existing signed event when unchanged.

### Changed
- Use `ethereum` as the muted-word input example.

### Added
- “Let people play”: a second Feather interview, with shared interactive feed, video, and GIF composer demos.

## [0.88.1] - 2026-10-20

### Fixed
- Refresh account metadata and lists independently from feed routing on launch and when opening Settings or your own profile. Discover account relays first, then fetch current lists from those relays and bootstrap relays.
- Show cached muted words immediately, update them after private-list decryption, and distinguish loading/retry from an empty list using icons.

### Changed
- Remove the notification search placeholder; retain the search icon and accessibility label.

## [0.88.0] - 2026-10-20

### Added
- Compact notification search that scrolls with the list and locally filters notification text and sender names, without relay search requests.

### Fixed
- Muted-word additions and removals update immediately, with a bounded background edit queue instead of blocking the editor behind a spinner. Failed edits roll back; successful edits stay visible while the cache catches up.

## [0.87.0] - 2026-10-20

### Added
- Add private muted words or phrases and remove existing public/private words from Settings, preserving the freshly loaded mute list through the signer.
- Long-press note and notification timestamps to copy their `nostr:` event link, using `naddr` for addressable events.

## [0.86.0] - 2026-10-20

### Added
- Honor existing public/private NIP-51 muted words with case-insensitive text matching in feeds and notifications and per-reply reveal in threads.
- Read-only muted-words sheet in Settings, opened by a speech-bubble-with-slash icon.

## [0.85.5] - 2026-10-20

### Fixed
- Show incoming hashtag/profile posts through a dedicated cache update path, without waiting for home discovery and engagement processing. Request the selected hashtag category first and defer optional tab discovery.

## [0.85.4] - 2026-10-20

### Changed
- Make all scroll-hidden reader bars and home controls follow scroll distance, including flings, and retain partially hidden positions across navigation.

## [0.85.3] - 2026-10-20

### Fixed
- Retain cached reply and zap evidence per note so counts do not disappear as the visible query window changes; preserve successful zap verification across provider refreshes.

### Changed
- Remove the explanation popup from the muted-account indicator in threads.
- Show a subdued, tappable author name on revealed muted replies instead of an explanatory label; tapping opens their profile.

## [0.85.2] - 2026-10-20

### Changed
- Make profile bars slide away and return at the speed of vertical scrolling instead of snapping after a threshold.
- Hide sticky profile tabs together with the toolbar when scrolling down, and restore them when scrolling up.
- Place Replies immediately after Posts in profile views.
- Link the Feather origin interview to Gigi’s “Taste and Values” post beside the discussion of taste.

## [0.85.1] - 2026-10-20

### Changed
- Keep profile tabs below the profile information, pinning them at the top while browsing posts and returning them to their original position when scrolling back.

## [0.85.0] - 2026-10-19

### Added
- Follow and unfollow hashtags from their header, syncing the NIP-51 interests list through the signer while preserving existing public and private entries.

### Changed
- Increase enabled proof-of-work difficulty from 16 to 21 bits for posts and replies; emoji reactions remain at 12 bits.
- Show hashtag and profile tabs together with the top bar, revealing both when scrolling upward instead of requiring a return to the start of the feed. Show the profile name in the toolbar only after its header name scrolls out of view, with its banner and biography below the combined header.

### Fixed
- Keep newly opened hashtag readers at their tab bar while categories load, with a stable header slot and independent reader composition state.

## [0.84.0] - 2026-10-19

### Changed
- Add Replies to the shared website/blog swipe widget and remove the separate homepage screenshot grid.
- Remove the duplicate full-screen screenshot beside the website video demo so the flower player uses the full available width on mobile.
- State “No DMs, by design” beside the homepage features, linking to the explanation in the origin interview.
- Simplify the blog social preview to its large title and app screenshots.
- Lightly rebalance the origin interview with more narrative between its central quotes.
- Give “Why Feather exists” its own social preview artwork with the interview title, weekend quote and cropped app screenshots.
- Extend the website/blog swipe demo with themed Articles, Podcasts and artist profile gallery screenshots, keeping the icon switcher compact and scrollable.
- Refresh website and blog previews with paired light/dark screenshots, the app’s Home icon, and real full-screen video. Curate updated Zapstore screenshots while retaining the feather gallery and settings captures; remove Android system bars from all new public captures.
- Let the shared website feed switcher follow light and dark mode, including its controls, phone frames and video mockup.
- Share the interactive homepage feed switcher with the origin interview, beside the discussion of separate content tabs.

### Added
- Browse hashtags with the same discovered content tabs as profiles, including related hashtags, cached content and separate scroll positions; hashtags opened from profiles keep their author scope.
- A static Feather blog at `/blog/`, beginning with an edited creator interview about why Feather exists.

## [0.83.4] - 2026-10-19

### Changed
- Show zap-feed profile names beside their avatars, keep the amount centered between them, and place the zap timestamp at the top-right above the recipient avatar.
- Increase spacing between zap-feed entries while keeping each zap header, comment and referenced note grouped closely together.

### Fixed
- Align the account avatar picture and center with zap-feed avatars, with its border extending around the matching 36dp picture.

## [0.83.3] - 2026-10-19

### Changed
- Align the floating account avatar’s right edge with the recipient avatars in the zap feed, including its onboarding counterpart.
- Place zap-feed reply, repost, reaction and zap controls inside the referenced note card, including article and app previews.

## [0.83.2] - 2026-10-19

### Fixed
- Allow follow/unfollow actions on other profiles while an update is pending. Queue up to 16 profile edits, refresh the latest list for each, and keep pending state local to each profile.

## [0.83.1] - 2026-10-19

### Changed
- Render People-tab profiles with the same shared row as search results: 40dp avatar, name, NIP-05 identity and verification, and the follow indicator on the right.

## [0.83.0] - 2026-10-19

### Added
- Long-press the top-right account avatar to open Notifications; a normal tap still opens your profile.

### Changed
- Place the profile biography expand chevron inline at the end of its three-line preview, removing the separate expand row while preserving links and expanded embedded cards.

## [0.82.1] - 2026-10-19

### Fixed
- Distinguish HTML pages, malformed JSON, and oversized responses in NIP-05 verification errors instead of reporting all three as an invalid or oversized record.
- Speed up follow-list checks with four continuously occupied relay slots and a five-second per-relay deadline. Update follow indicators directly from the cached contact-list winner before clearing the spinner, without waiting for feed enrichment or allowing older snapshots to revert them.

## [0.82.0] - 2026-10-19

### Fixed
- Avoid blocking follow changes when one advertised relay is unavailable: require two completed relay responses and fresh evidence of the exact follow-list version, retaining conflict checks and strict first-list safeguards. Include relay completion details in errors.
- Restore profile feeds directly from disk on profile opens and tab switches, before the full home-feed discovery and engagement refresh.

### Added
- Reply, repost, like, and zap referenced posts directly from the zap feed, with the existing long-press pickers and quote-post action.

## [0.81.0] - 2026-10-19

### Added
- Follow and unfollow profiles from the profile header, with Undo, first-list confirmation, and fresh relay checks before signing and publishing. Preserve unrelated follow-list data and stop when a newer list appears.

### Changed
- Publish follow-list changes to advertised write relays and three discovery relays; use read relays only for freshness checks unless they also serve as discovery relays.

## [0.80.1] - 2026-10-19

### Changed
- Render image URLs used as poll options as selectable images, with selection indicators, vote percentages, and thumbnails in voter lists.
- Show the posting account’s avatar in the reply composer’s top-right corner, matching the full-screen new-note composer.

## [0.80.0] - 2026-10-18

### Added
- Resolve podcast identifiers into full players using supplied URL hints, Fountain’s public GUID directory, and RSS fallback. Preserve parent podcast IDs, cache successful lookups, and retain compact cards when resolution is unavailable.

### Fixed
- Keep Posts visible on profiles and combine that profile’s posts and reposts using the same deduplicated rendering as Home, with the distinct Posts icon.

## [0.79.3] - 2026-10-18

### Changed
- Render links and space-separated link groups at the end of a paragraph as rich previews, keeping the introductory text above them. Links followed by prose remain inline.

## [0.79.2] - 2026-10-18

### Changed
- Replace standalone URL lines with rich previews in place, without repeating the link. Links within prose stay inline without a preview; failed preview requests retain the original clickable URL. Applies to notes, quoted notes, and polls, including YouTube and Fountain links.

## [0.79.1] - 2026-10-18

### Fixed
- Keep reaction and repost counts stable while scrolling: query activity per note, retain bounded cached evidence, and refresh recently viewed count entries instead of evicting them as old entries.

### Changed
- Keep Notifications visible when logged out, with a sign-in empty state.
- Show the compose feather when logged out; tapping it opens signer login directly, including on Search and Notifications.

## [0.79.0] - 2026-10-18

### Added
- Tap a poll’s vote count to see voters and their selected options, with avatars and profile links.

### Fixed
- Show poll images and videos through the regular media renderer, hide rendered media URLs, and open images in the full-screen gallery. Poll questions use note-style selectable rich text, mentions, hashtags, links, and inline quoted notes.
- Render Primal polls whose option IDs contain UUID hyphens; preserve the original IDs when voting.

### Changed
- Move Zap activity directly before Highlights in main and profile tab order.

## [0.78.0] - 2026-10-18

### Added
- Native NIP-88 polls with their own feed/profile tab, single- and multiple-choice voting through the signer, closing times, and cached results from the poll’s advertised relays.

### Fixed
- Keep discovered notifications when bounded cache queries change, protect recent notification events and targets from cache eviction, and preserve loaded previews when the shared preview cache rotates.
- Wallet connection errors distinguish expired/not-yet-valid TLS certificates and include the underlying certificate-chain failure.

### Changed
- Tap Home while already at the top to refresh using the same action as pull-to-refresh; tapping while scrolled still returns to the top.

## [0.77.4] - 2026-10-18

### Fixed
- NWC pairing failures use the copyable error toast with the failed step, relay host, wallet error code, and sanitized stack trace.

### Changed
- Author-filtered hashtag feeds show a single text title, “#hashtag by Name,” in the top bar; tap the header to open their profile.

## [0.77.3] - 2026-10-17

### Fixed
- Profile hashtag links show only that author’s posts, including cached results and older pages.
- Preserve video mute state and volume alongside playback position when opening a note or returning to the feed; prepared neighboring videos remain silent.

## [0.77.2] - 2026-10-17

### Fixed
- Resume videos at their previous playback position when opening a note from the feed or navigating back, using a bounded in-memory history.

### Changed
- Move Hashtags directly before Bookmarks in the main feed and profile tab order.

## [0.77.1] - 2026-10-17

### Fixed
- Keep discovered profile hashtag tabs visible across refreshes, tab changes, and return navigation instead of hiding them when the current bounded sample contains no hashtags.

## [0.77.0] - 2026-10-17

### Added
- Hashtag discovery lists recent tags ranked by frequency over the last 24 hours, without counts. Profiles show tags used in their posts; tap a tag to open its feed.
- Home posts show a compact matching followed hashtag beside the timestamp, with +N for additional matches; tap to open the hashtag or choose from all matches. Author names truncate to leave room.

### Changed
- Followed-hashtag posts now appear in Home, deduplicated with followed authors and reposts.
- Main and profile tabs now use a fixed relative order instead of ranking categories by content count. Undiscovered categories stay hidden; Replies appears when available.

## [0.76.0] - 2026-10-17

### Added
- Read-only followed-hashtag tab combining kind-1 posts from the account’s NIP-51 interests, including private hashtag follows read through the connected signer.

## [0.75.0] - 2026-10-17

### Changed
- Home combines kind-1 posts with kind-6/kind-16 reposts, deduplicating direct and shared copies with grouped attribution. Existing cards keep their order until refresh; reposted content retains its original kind.
- Give the main home feed a birdhouse icon, with a round entrance and perch in Feather’s existing outline style.

## [0.74.0] - 2026-10-17

### Changed
- Group the Reposts tab by original post, ordered by its latest repost, with a compact attribution and a tappable list of distinct reposters.
- Removed file size captions below composer attachment thumbnails and their extra spacing; upload progress still shows size.

## [0.73.1] - 2026-10-17

### Fixed
- Show attachment size reductions only when image compression produced the upload; metadata-only cleanup shows the final size.
- Keep notification rows and scroll position when opening a notification or profile and returning with Back.

## [0.73.0] - 2026-10-17

### Added
- Submit a single hashtag or Nostr identifier in Search to open its feed, profile, note, article, or other supported content directly. Identifiers bypass Vertex profile search.

### Changed
- Updated the homepage introduction to “Everything is just one swipe away.”
- Homepage GIF examples now feature good morning, sunrise, and zap reactions; removed the extra draft prompt.
- Homepage download caption now reads “No ads. No bullshit.”
- Removed the homepage hero eyebrow and top-right download link.
- Corrected spacing in the homepage’s “Swipe, tap, done.” heading.
- Homepage headings now read “Light like a feather.”, “Made for keyboard lovers”, and “Batteries (and GIFs) included.”

## [0.72.0] - 2026-10-17

### Changed
- Visible video neighbors now prepare their first frame. Clips that start playing continue until fully offscreen, with at most three visible players and one audible clip.
- Rebuilt the homepage around concise copy, an interactive feed switcher, VLC-style video gestures, and a local composer/GIF preview.

## [0.71.0] - 2026-10-16

### Added
- Ctrl+Tab and Ctrl+Shift+Tab cycle through main feeds and profile tabs. Ctrl/Cmd+F opens profile search and focuses its text field.

### Changed
- Website now highlights external keyboard support for mechanical keyboard enthusiasts, including composer shortcuts and text navigation.

## [0.70.0] - 2026-10-16

### Added
- Automatically optimize large static photo uploads: above 2 MB, fit within 2,560 pixels on the longest edge and encode at JPEG quality 85. Prefer lossless compression for screenshots and transparent images, retain animation, and keep the smaller sanitized version when available. Process oversized originals before enforcing the upload limit.
- Subtle original/uploaded size labels in the composer, retained with saved attachment drafts.

### Fixed
- Keep Bluetooth keyboard Up/Down arrows inside the composer text field for cursor movement and selection instead of moving focus to buttons and toggling the software keyboard.

## [0.69.6] - 2026-10-16

### Fixed
- Restore encrypted notification filters after reinstalling by using the same account and public fallback relays for settings publication and discovery. Wait for foreground relay access after signer interaction; retry failed sync when reopening Settings or Notifications, or refreshing Notifications.
- Open NWC setup when a signed-in reader taps the muted gallery zap button without a connected wallet. Preserve the selected image when closing setup; logged-out taps still open signer login.

## [0.69.5] - 2026-10-16

### Fixed
- Prevent the oversized feather flash at startup by keeping Android's splash visible until the tutorial has drawn at the measured icon size and position.
- Keep the white feather stem visible on every colored tutorial page by changing only the body's fill color.

## [0.69.4] - 2026-10-16

### Fixed
- Match the tutorial feather to Android's splash artwork scale so its size stays consistent during the handoff.

## [0.69.3] - 2026-10-16

### Fixed
- Fix a tutorial crash after signer login when the pager count grew before its page-key callback received the new tab list. Share the current list across callbacks and guard transient out-of-range indices in welcome and home pagers.

## [0.69.2] - 2026-10-16

### Added
- Blue homepage button beside the Feather profile button in settings.

### Fixed
- Keep the selected main feed anchored by category as newly discovered tabs arrive; remove background forced scrolling to a stale selection. Preserve category identity in onboarding previews too.

### Changed
- Remove the long-press reaction/zap hint. Keep only login and swipe guidance.

## [0.69.1] - 2026-10-16

### Changed
- Onboarding hints are speech bubbles pointing at login and the feed switcher. Tap anywhere to dismiss each hint; swipe gestures still work, and close buttons are removed.
- Keep the welcome screen through signer login and continue with swipe guidance while the personalized feed loads behind it.

## [0.69.0] - 2026-10-16

### Changed
- Sync notification filter selections with encrypted account settings over Nostr, retaining existing local selections and compatibility with older settings events.
- Swap the open-in-app and share actions in detail toolbars.

## [0.68.3] - 2026-10-16

### Changed
- Tapping the muted gallery like or zap button while logged out opens signer login. No reaction or payment is queued; signed-in accounts without a wallet still have zapping disabled.

## [0.68.2] - 2026-10-16

### Changed
- Clarify the welcome hint: “Connect your signer to log in.”
- Show the swipe hint and feed switcher over the welcome background. Swipe between colored feathers to practice; dismiss the hint to drop the selected feather and reveal that feed.
- Gently fade the welcome background over 800 ms after the feather finishes falling.

## [0.68.1] - 2026-10-16

### Changed
- Pause the first-launch welcome at a “Connect your signer” hint beneath login. Dismissing it starts a slower feather drift, followed by the swipe hint.

## [0.68.0] - 2026-10-16

### Added
- First-launch welcome: login gently appears while the splash feather drifts away, revealing the feed already loading underneath. Skippable, respects disabled animations and never replays after completion.
- Brief, dismissible hints for swiping feeds and long-pressing reaction/zap controls, remembered on the device.

### Changed
- Hide the compose floating action button on the main feed and profiles while logged out.
- Website features now highlight the dedicated Podcasts tab, value-for-value and Podcasting 2.0 discovery, and background playback with lockscreen controls.

## [0.67.0] - 2026-10-16

### Added
- Sync per-account NSFW and PoW settings over Nostr using encrypted NIP-78 app data, with local persistence and bounded publishing retries. Cache limits and wallet credentials stay on the device.

### Changed
- Logged-out account buttons open the Android signer directly. Settings are available only when logged in; logging out restores guest defaults.

## [0.66.1] - 2026-10-16

### Added
- Show the posting account's cached avatar in the top-right corner of the full-screen new-post composer.

## [0.66.0] - 2026-10-16

### Added
- Initial Japanese localization using Android's system language, with English fallback. Translate core visible labels, common icon accessibility descriptions, the selection reply action and system audio controls.

### Changed
- Use the Feather silhouette as the lockscreen and notification audio player's app icon instead of the default play icon.
- Add the “x” suffix to every system playback-speed icon and keep numeral size and stroke weight consistent across speeds.

## [0.65.0] - 2026-10-16

### Added
- Pocket Casts-inspired system audio controls: explicit 10-second rewind and 30-second forward, numeric playback-speed controls through 3×, and a heart action routed through the existing signed orange-heart reaction flow.
- Open the playing voice note or podcast-share note by tapping the lockscreen/notification player. Heart actions are bound to that source note and account; Android may require unlocking for the signer flow.

### Changed
- Restore cached home-feed notes and initial author metadata before tab discovery, engagement loading and cache pruning. Keep cached content visible without a feed spinner while relays refresh.
- Protect a bounded home-feed window and the signed-in account’s cached profile, follows, mute and discovery lists from routine pruning so they remain available offline.

## [0.64.3] - 2026-10-16

### Changed
- Show discovered repost counts beside the repost icon, with immediate updates when reposting or undoing, unique-author counting, and muted/deleted repost filtering.
- Show a subtle image placeholder in the cover area while episode artwork loads, retaining it if the image cannot load.

## [0.64.2] - 2026-10-15

### Changed
- Long-press any icon in the bottom feed switcher to jump directly back to the home Posts feed.
- Limit the long-press zap picker to three columns and three rows, with up to seven amounts sorted from smallest to largest and the custom-amount and comment buttons together in the final two cells.

## [0.64.1] - 2026-10-15

### Fixed
- Prevent Material 3 accessibility metadata from traversing detached controls after a queued UI update (`visitAncestors called on an unattached node`).

### Changed
- Compact podcast widgets with cover artwork beside and matching the player height, podcast name beside the duration, and the external-link action in the top-right corner.

## [0.64.0] - 2026-10-15

### Added
- Discover podcast and episode shares, NIP-22 comments and kind-17 episode likes using NIP-73 tags, alongside Fountain links. Show compact playable cards with sharer context, including exact-GUID RSS hints and advertised audio metadata; keep unresolved references accessible.
- Optional public zap messages from the long-press amount picker, carried in signed NIP-57 zap requests with existing payment recovery protections.
- Audio playback-speed menu with 0.75×, 1×, 1.25×, 1.5×, 1.75×, 2×, 2.5× and 3× presets. Add a synchronized speed-cycle action to Android media controls for podcasts and voice notes.

## [0.63.2] - 2026-10-15

### Changed
- Keep the zap amount picker usable during an in-flight payment in notes and the gallery. Queue explicitly selected different amounts, deduplicate pending note/amount pairs, and keep uncertain-payment recovery intact.
- Remove suggested search terms, pills and autocomplete from the GIF picker; keep direct text search and the results grid.

## [0.63.1] - 2026-10-15

### Fixed
- Apply verified author deletions across main feeds, profile feeds, cached navigation and embedded content. Discover deletion requests for visible notes on bounded relay subscriptions, persist bounded public deletion history across restarts, and prevent retained feed snapshots or stale relay copies from resurrecting deleted content. Keep cached-text reveal exclusive to threads.

## [0.63.0] - 2026-10-15

### Added
- Collapsed muted/blocked-author placeholders preserve thread chains, with per-note cached-text reveal. Move direct muted replies to a subdued section at the bottom while keeping other views filtered.
- Collapsed deleted-note placeholders in threads with an eye toggle to reveal an available cached text copy inline. Keep muted/sensitive content hidden, omit note actions and media loading, and never request known-deleted event IDs to recover their content.

## [0.62.2] - 2026-10-15

### Changed
- Replace the logout-like login arrow with a person-and-key sign-in icon in the home account button, account sheet and issue reporter.

## [0.62.1] - 2026-10-15

### Fixed
- Restore cached conversations independently of feed/profile/engagement loading. Remember the replies belonging to the eight most recently opened threads across app restarts, protect their cached notes and deletion requests from publication-age eviction, and continue discovering new replies in the background.

## [0.62.0] - 2026-10-15

### Added
- Long-press links in notes, quoted notes, profile bios, highlights, articles and web previews to copy their full destination. Keep normal tap navigation and text selection outside links.

## [0.61.0] - 2026-10-15

### Added
- Reply from the Android text-selection menu in notes, embedded notes and article bodies. Prefill the reply with the selected passage as a blockquote, leave one empty line before the cursor, and preserve any saved reply text and attachments.

## [0.60.0] - 2026-10-15

### Added
- Open the current note in another Nostr app from a phone icon beside copy/share in the detail toolbar. Use Android's chooser, exclude Feather, and preserve article/app addresses when opening addressable events.

### Changed
- Keep the zap bolt visible with a gentle shimmer while payment is pending instead of a spinner. Replace the brief success flash with a clearer 600 ms gold pulse after confirmation, across notes and the gallery.

## [0.59.4] - 2026-10-14

### Changed
- Render thread roots at normal post width in conversations and feed previews. Connect the root's reply-icon tip to the next avatar, keeping the icon fixed as counts change and the line clear of text and media; keep replies indented.

## [0.59.3] - 2026-10-14

### Changed
- Describe connected conversation threads, in-place reply expansion, and preserved reading position on the website.

### Fixed
- Load parents from the thread’s upper ellipsis in place, retaining the focused note, reading position, and Back history. Check cached events first and preserve expanded ancestry when returning to the thread.

## [0.59.2] - 2026-10-14

### Changed
- Use ngit / GRASP as the website header’s repository link, opening Feather’s code and issues on GitWorkshop.
- Match thread continuation dots to the connecting line’s color in light and dark mode.
- Place thread continuation dots beside the note actions in the avatar gutter, without an extra row.
- Fade and collapse the selected note’s parent summary once its parent is rendered above it; keep the summary when the parent is unavailable.

## [0.59.1] - 2026-10-14

### Fixed
- Keep the opened note in place while ancestors load, including short conversations. Apply initial/restored positions by event key and reserve only the space needed below the reading anchor.

### Changed
- Keep the selected note’s single-line parent summary, with a small muted spinner while earlier context is loading. Stop the indicator on relay completion or a bounded timeout.
- Use vertical continuation dots on the avatar connector. Reveal more reply branches in place, retaining expansions when returning to the conversation.
- Remove horizontal separators between feed/profile notes and conversation branches. Use spacing and vertical thread connectors to distinguish entries.

## [0.59.0] - 2026-10-14

### Added
- Connected conversation view with ancestors above the selected note, grouped reply branches below it, avatar connectors, and compact continuation controls. Give the selected note larger text and full reading width.
- Short self-thread previews in Posts feeds and profiles, assembled from events already available locally.
- Explicit gaps for unavailable, hidden, deleted, excessively deep or cyclic ancestry, with supported missing parents openable. Preserve verified deletion state across retained reader screens.

### Changed
- Resolve threads through the shared cache-first path, with bounded ancestor/branch discovery and separate queries for new replies and deletion requests. Keep existing article, app, voice-reply and NIP-22 comment behavior.
- Preserve conversation positions when parents arrive, when returning from a branch, and when Android restores the reader. Wrap compact reply actions when space is limited.

### Fixed
- Distinguish immediate replies from root references, legacy positional tags, mentions and quotes. Count direct replies without treating every root-scoped descendant as a direct reply.

## [0.58.1] - 2026-10-14

### Fixed
- Resolve exact event IDs from shared memory and disk caches before requesting relays. Share missing-ID requests across threads and embedded notes, remove resolved IDs promptly, and re-check the cache after backgrounding. Opening a cached note no longer fetches its root again; discovery of new replies and activity continues separately.

### Changed
- Make never re-fetching a locally retained, verified event a core design principle for all future features.

## [0.58.0] - 2026-10-14

### Changed
- Flash the zap bolt with three tiny gold sparks for 200 ms after a newly confirmed payment. Failures, uncertain payments and already-confirmed history do not trigger it.
- Give newly added reactions a quick 120% pop and gentle settle, including selections from either emoji picker and custom emoji. Keep the button and count steady.
- Animate repost taps with a quick clockwise turn, a slight overshoot and settle, while immediately showing the optimistic active state.

## [0.57.2] - 2026-10-14

### Fixed
- Accept generic file and mixed-media shares from Android file explorers. Detect images/videos from file contents instead of trusting provider MIME labels; validate media before upload authorization or sending any bytes to Blossom.

## [0.57.1] - 2026-10-14

### Fixed
- Accept video files shared from other Android apps, using the same upload queue as shared images. Attach to the current post/reply draft or open the new-note composer when none is open.

## [0.57.0] - 2026-10-14

### Added
- Select images and videos together from the composer attachment button. Upload videos up to 250 MB to your Blossom servers with progress, preview and removal controls. Repackage audio/video tracks without location metadata before upload; retain original encoded quality and rotation.

### Changed
- Place the GIF button at the far left of composer actions, with attachments and Send on the right.

## [0.56.1] - 2026-10-14

### Changed
- Reduce the feather logo inside the settings profile button while keeping its touch target unchanged.

## [0.56.0] - 2026-10-13

### Added
- A feather button at the far right of the settings feedback row opens Feather’s profile inside the app.
- Website link to Feather’s Nostr profile and details about composer GIF search.

### Fixed
- Retry pre-payment Lightning address and invoice HTTP requests twice on network failures, including DNS errors and connection aborts. Reuse the signed request and include attempt details in copied diagnostics; wallet payments are never automatically resent.

## [0.55.0] - 2026-10-13

### Added
- GIF search from nostr.build in post and reply composers, with a circular GIF button to the left of image attachments, starter searches, autocomplete and emoji/non-Latin queries.
- A lazy two-column picker with bounded native animated previews, reduced-motion stills, clear error/retry states and subtle linked attribution.
- Attach original GIF links without re-uploading. Preserve NIP-92 dimensions, format, size and description in saved drafts and published events.

## [0.54.0] - 2026-10-13

### Fixed
- Follow bounded HTTPS redirects when resolving Lightning addresses and invoice callbacks instead of failing on HTTP 302. Preserve the request deadline and include redirect hosts in copied zap diagnostics.

### Changed
- Keep reaction pickers usable while sending. Queue additional emoji selections and removals in order with immediate optimistic feedback.
- Use `nostr+walletconnect://` as the wallet connection field hint.

### Added
- Swipe vertically in the full-screen image gallery to adjust brightness, with the video player’s brightness indicator. Horizontal paging and zoomed-image panning remain available; closing the gallery restores normal brightness.

## [0.53.0] - 2026-10-13

### Changed
- Show a faster orange composer spinner specifically while mining proof of work for posts and replies; shorten the settings toggle to “PoW”.
- Tap an active repost to undo it with a signed deletion request. Optimistically clear the icon, retry the same deletion up to three times, and restore the active state if delivery fails.
- Reaction buttons immediately show the selected emoji or removal without a spinner, reverting to confirmed state if retries fail.
- Reposts show the active icon immediately without a spinner, retry relay delivery up to three times using the same signed event, and quietly revert if delivery fails.

## [0.52.0] - 2026-10-13

### Added
- Automatic NIP-13 proof of work for new posts and replies (16 bits) and emoji reactions (12 bits), matching Wisp’s defaults. A settings toggle controls both, enabled by default. Mine on one background CPU worker before signing; delivery retries reuse the same signed event.


## [0.51.0] - 2026-10-13

### Added
- Native app listing details with app icon, description, screenshot gallery, publisher, website/source links and an explicit Zapstore shortcut.
- Comment on apps, reply to existing comments, react and zap using Feather’s regular controls. Load NIP-22 discussions by the stable app address, including older Zapstore root comments, and route discussion activity through the Zapstore relay.
- Open app naddr links and embedded app listings inside Feather.


## [0.50.0] - 2026-10-13

### Added
- Open `nostr:` note and profile links from Android’s app chooser, whether Feather is already running or starting fresh. Preserve relay hints and the reader back stack; unsupported event types open through njump.

### Fixed
- Retry post and reply delivery up to twice after relay timeouts, connection failures or missing acknowledgements, reusing the saved signed event to avoid duplicates. Preserve the draft if all three attempts fail.

### Changed
- Give Nostr issue reporting, ngit and GRASP mirrors a prominent website section with Feather icons, GitWorkshop links, contribution details and updated public-report privacy information.

## [0.49.0] - 2026-10-13

### Added
- Report bugs and suggest features from settings as public Nostr issues using the connected Android signer. Crash reports can prefill an editable report with common secrets and URLs removed. Private drafts and exact signed retries survive restarts.
- Discover an Issues tab for kind-1621 events on profiles and the main feed, with repository links, subject previews and Markdown detail views.
- Nostr repository and issue tracking through GitWorkshop, with GRASP copies on relay.ngit.dev and gitnostr.com alongside GitHub.

## [0.48.1] - 2026-10-13

### Fixed
- Keep article Markdown parser, reference links and image transformer stable across screen updates, preventing repeated loading-state height collapses, flicker and scroll jumps.

## [0.48.0] - 2026-10-13

### Changed
- Render complete articles with the same full Markdown engine as boris-android, preserving headings, nested lists, block quotes, tables, code blocks and reference links instead of flattening blocks to text. Keep Feather’s theme, cached images, gallery taps and Nostr link navigation.
- Show article covers full width behind the title with a dark readability gradient; keep body text padded and provide a regular title when no cover exists.

### Fixed
- Save notification filter selections per account on this device and restore them across app restarts, without briefly showing unfiltered notifications.

## [0.47.0] - 2026-10-13

### Changed
- Tighten note author spacing and the gap above embedded quotes; remove trailing blank lines left by hidden quote references.
- Render linked articles with the compact article preview card and show timestamps on embedded notes.
- Show the compose button on your own profile without pre-filling a self-mention.
- Include the year in absolute timestamps for notes older than one year.

### Fixed
- Preserve notification filter selections and scroll position when returning from a note; show an @ indicator for mentions.
- Load private mute lists at app level, including when viewing notifications.
- Start quote-post drafts with one newline before the reference and place the cursor above it.

### Added
- Open the new-note composer with Ctrl+N or Cmd+N; preserve an already open draft.
- Send new notes and replies with Ctrl+Enter or Cmd+Enter on a hardware keyboard, including numpad Enter. Preserve ordinary Enter for newlines and block sending while uploading or publishing.

## [0.46.0] - 2026-10-12

### Changed
- Remember encrypted NWC connections separately for each account on this device. Logout deactivates the wallet; signing back into the same account restores it. The trash action deletes only that account’s connection. Preserve existing account-owned connections during migration.

### Added
- Share one or multiple images to Feather from Android galleries and file managers. Attach and upload to the open composer/reply, or open the full-screen new-note composer with its saved draft. Queue incoming shares behind current uploads and reuse metadata stripping, progress, and Blossom server discovery.

## [0.45.1] - 2026-10-12

### Fixed
- Closing the composer immediately clears upload busy state so it can reopen while cancellation cleans up; canceled uploads cannot modify reopened drafts or overwrite their progress. Keep upload file work serialized.

### Changed
- Show NIP-05 verification before the follow indicator in profile search and composer profile suggestions, keeping follow status at the far right.

## [0.45.0] - 2026-10-12

### Added
- Strip embedded metadata before image upload, including EXIF/GPS, XMP, IPTC, comments and embedded thumbnails. Hash and authorize only sanitized files; preserve image orientation and supported animations.
- Open composer attachment thumbnails in the full-screen gallery, with swiping between attachments.

### Changed
- Remove the four-image picker/queue cap and eight-thumbnail cap; use Android’s picker limit and upload sequentially within the existing draft length limit.
- Tighten attachment thumbnail spacing while preserving corner-centered removal buttons and full touch targets.

### Fixed
- Publish lowercase, deduplicated hashtag tags on new posts and replies so relay hashtag queries can find them; preserve the original text and exclude URL fragments.

## [0.44.2] - 2026-10-12

### Fixed
- Insert uploaded image URLs on the next line without an extra blank line.
- Center attachment removal buttons on the thumbnail’s top-right corner, with room for the full touch target outside the image.
- Distinguish local image file opening, reading and format checks before upload; show bytes read and include the current stage in upload errors.

## [0.44.1] - 2026-10-12

### Fixed
- Show upload stages while discovering servers, waiting for the signer, connecting and mirroring; read file size before discovery. Bound discovery and include the signer queue in the authorization timeout.
- Make composer upload percentages and file sizes easier to read, and keep completed upload details visible for four seconds while the composer is in the foreground.

## [0.44.0] - 2026-10-12

### Fixed
- Retry wallet payment-status lookups up to twice after connection failures or timeouts, with bounded delays and per-attempt copied diagnostics. Never retry the payment itself.
- Retry reaction and reaction-removal publishing up to twice using the same signed event; leave the last confirmed state and fail silently without a toast.

### Added
- Play shared Fountain episode links inline with episode artwork, title, duration, background audio and lock-screen controls. Cache public episode metadata within the media budget and retain ordinary link previews when an episode is unavailable.

### Changed
- Put the full-screen composer’s image attachment button beside Send at the bottom right, preserving upload progress and matching round button sizes.
- Replace kind-54/10154 podcast support with a Podcasts tab for Fountain episode links shared in posts and replies by the profile or followed accounts.
- Move the profile search field to the top beside the account avatar, reserving space on the right.

## [0.43.0] - 2026-10-12

### Added
- Keep voice notes and podcasts playing across navigation and while locked/backgrounded, using an Android media session service with system playback controls and author/title/artwork metadata.

### Fixed
- Query payment status once after an NWC payment timeout, without resending payment. Preserve the original timeout in copied diagnostics if confirmation still fails.
- Recognize NWC lookup settlement timestamps when the optional state field is absent. Require an exact payment hash and millisatoshi amount match, reject contradictory states, and keep uncertain payments on the lookup-only path. Copied errors now distinguish valid settlement timestamps from missing or invalid values.

## [0.42.0] - 2026-10-12

### Added
- Add dynamic Voice and Podcasts tabs to profiles and main feeds. Render NIP-A0 voice notes/replies and NIP-F4 podcast episodes/show metadata with cached, tap-to-play audio, seeking, duration, optional waveform, and episode artwork. Audio pauses when leaving the reading screen or backgrounding the app and suppresses automatic video playback.

## [0.41.0] - 2026-10-12

### Changed
- Prioritize Posts → Photos → Videos → Highlights → Articles → Replies across main feeds and profile tabs, with remaining kinds following their existing dynamic order.
- Restore the bell for clearing notification filters, with a distinct outlined circular surface and blue all-notifications state. Preserve filter spacing and multi-selection.

### Added
- Show the current image author’s avatar beside the close control in the full-screen gallery. Tap it to open their profile; it updates when swiping and fades with the gallery controls.

## [0.40.2] - 2026-10-12

### Changed
- Give the full emoji picker its own most-recent-first reaction history, filling up to three rows instead of reusing six shortcut suggestions. Keep the small picker unchanged.

### Fixed
- Preserve sanitized provider rejection reasons from unsuccessful Lightning HTTP responses, including Alby HTTP 400 errors.
- Check payment status when a wallet omits the payment preimage, without resending the payment. Accept a signed settled lookup only when its payment hash and amount match. Include safe response-shape diagnostics in copied errors.

## [0.40.1] - 2026-10-12

### Changed
- Allow invoices from zap providers that omit or use a different description hash, matching established clients; retain amount, checksum, expiry, payment-hash and payment confirmation checks.
- Copying a zap error includes the failed step, sanitized stack trace, version, provider hosts and invoice hash diagnostics without wallet credentials or payable invoices.
- Always show passive relative timestamps in notifications: timestamp taps open the notification, while avatar taps still open the actor’s profile.
- Replace the notification-filter bell with a dedicated clear-filter action, disabled without a selection. Align the filter row beside the account avatar with reserved space and horizontal scrolling on narrow screens.

## [0.40.0] - 2026-10-12

### Changed
- Allow multiple notification types to be selected together; the bell or clearing the last filter restores all notifications.
- Trim trailing whitespace and blank lines before signing posts and replies, preserving leading and internal formatting.
- Adopt “Nostr with nothing in the way.” as the website and Zapstore tagline.

## [0.39.2] - 2026-10-12

### Changed
- Center long-post expand/collapse controls in the existing action bar instead of a separate row.
- Remove the compose button’s elevation while scrolling and fading; restore its shadow at full opacity, without clipping the fade layer.
- Hide the reaction count when the sole reaction is your own; keep the active emoji visible.

## [0.39.1] - 2026-10-12

### Changed
- Use the feather’s blue for active reposts, inset Reply slightly from the text edge, and reduce active reaction emoji size to match nearby action icons.

### Fixed
- Strip rendered image/video URLs from shared one-line reply previews using the same media text cleanup as notes.
- Load settings/account avatars through the shared image cache with the sheet’s own active lifecycle, independently of paused feed media.
- Explicitly scale inline images and video surfaces to the full content width, preserving their aspect ratios, including low-resolution media.

## [0.39.0] - 2026-10-12

### Added
- Show image upload percentage inside a compact progress ring and a subtle human-readable file size in the composer. Progress follows bytes sent; preparation and server-side mirroring remain indeterminate.

### Changed
- Reuse the single-line reply-context row in the composer, with content-aware video, photo, article, and highlight icons.
- Round composer attachment thumbnails and overlay a white-X remove button on a dark-red circle in the top-right corner.
- Give composer text fields a subtly tinted rounded surface and image attachment buttons a secondary circular fill matching the send button size.
- Present the website hero as three phone screens: notifications on the left, main feed in the center, and photos on the right, using status-bar-free screenshots.

## [0.38.0] - 2026-10-12

### Added
- Tap the repost icon to publish a signer-authorized repost; hold to compose a quote post. Persist signed reposts for exact-event retries and support generic kind-16 reposts alongside kind 6.
- Filter notifications by replies, reactions, zaps/nutzaps, reposts, or mentions using a compact icon row; tap the active filter again to show everything.
- Show follow status and NIP-05 verification icons in profile search results, using the same indicators as composer mentions.

### Changed
- Add eight real app screenshots to the website and Zapstore, with Android status bars removed, a feed preview, and a swipeable website gallery.
- Update the Zapstore description to describe Feather’s focus on simplicity and discoverability.
- Refresh the website with app icons, a responsive light/dark design, 18 detailed feature descriptions, evergreen download links, and current privacy information. Describe sane defaults, kind discovery and swipes, VLC-style controls, the full-screen gallery, NWC one-tap zaps, and custom reactions with emoji pickers.

## [0.37.1] - 2026-10-12

### Fixed
- Encode Blossom upload authorization using standard padded Base64 for compatibility with Haven and Primal; include bounded server error details when uploads fail.

### Added
- Copy a `nostr:naddr` reference from addressable-event details, or `nostr:nevent` for regular notes, beside the share action.

## [0.37.0] - 2026-10-11

### Changed
- Right-align like and zap actions while keeping reply on the left; show sharing only in the note detail toolbar, not individual post rows.
- Place the full-screen composer’s send button at the bottom-right floating-action position, above the keyboard, with content padding to prevent overlap.

### Added
- Attach images in post/reply composers with Android’s photo picker; upload to the account’s Blossom servers using signer authorization, mirror across configured servers, and retain successful URLs in drafts with thumbnails/removal controls.
- Show the feather compose button on other profiles and prefill a Nostr mention, preserving any existing post draft and avoiding duplicate mentions.

## [0.36.1] - 2026-10-11

### Changed
- Color cache usage green, orange, then red as it fills the selected storage budget; red starts at 95%, with matching usage text and theme-aware colors.

## [0.36.0] - 2026-10-11

### Changed
- Use zap presets only when the account has no known amount history; otherwise show its recent confirmed amounts without filling extra slots with defaults.
- Align the reply action icon with the note text’s leading edge, with or without counts, while retaining its full tap target.
- Use a round, filled primary-color send button in the reply composer.

### Added
- Open a full-screen text-only kind-1 post composer from the main feather button, reusing mentions, account-scoped drafts, signer-only publishing, and exact-event retries.
- Show the normal reply, reaction, and zap controls in full article detail views, including long-press pickers and engagement counts.
- Share articles from an icon in the top-right detail toolbar using the Android share sheet.

## [0.35.0] - 2026-10-11

### Added
- Show follow status and live NIP-05 verification icons in reply mention suggestions, using the shared bounded identity cache.
- Long-press zap amount picker in notes and galleries, with recent account-specific amounts, eight starter amounts, and custom sats; one-tap zaps remain 21 sats.

## [0.34.0] - 2026-10-11

### Added
- Text reply composer with Vertex @mention lookup, signer-only publishing, saved local drafts, exact-event retries, and a Feather client tag; use NIP-10 for notes and NIP-22 for other supported content.

### Changed
- Draw current media cache usage on the storage slider in a contrasting color, with usage and the selected limit sharing a proportional scale.
- Label the 18+ setting “Show NSFW”.

### Fixed
- Keep the feed selector filled at either end: Search stays at the left edge without opening empty icon slots; swipe animation and selection emphasis remain.
- Report NIP-05 HTTP errors accurately; only redirect responses mention the redirect restriction, and missing endpoints report HTTP 404.

## [0.33.1] - 2026-10-11

### Fixed
- Handle duplicate and superseded cache events as normal database outcomes instead of crashing; keep event ingestion on IO and report genuine write failures without marking them cached.
- Read every advertised write relay for profile tabs and older pages in batches of four, without truncating write-relay lists or expanding the main-feed connection pool.
- Pull-to-refresh on a profile explicitly restarts its selected-kind request without resetting home-feed pagination.

## [0.33.0] - 2026-10-11

### Added
- Minimal Vertex profile search with avatars and tappable results, available without login; debounce requests and cancel them when leaving search.

### Changed
- Order gallery floating actions as zap, like, then open note; dim and disable zaps without a connected wallet, and both zap and like when logged out.

### Fixed
- Use the theme’s foreground color for notification names and uncolored reaction glyphs so they remain readable in dark mode.
- Guard the profile tab indicator against changing tab counts during layout, preventing an out-of-bounds crash.

## [0.32.0] - 2026-10-11

### Added
- Add a logged-in-only Notifications page left of Posts, with compact replies, mentions, reactions, reposts, zaps, and nutzaps from the account’s read relays, cached content, pull-to-refresh, and mute/content filtering.
- Add a disabled Search placeholder farther left; no search queries or indexing yet.
- Show a homepage icon left of the Lightning address icon on profiles with a website; tap to open it.

### Changed
- Use a fixed short calendar date on article cards and compact reading durations such as 5m, 1h, or 1h 15m beside the existing clock icon.

### Fixed
- Recognize standard Nostr likes (+ or empty content) and dislikes from other apps as active reactions that can be removed through the signer.
- Fetch the account’s reactions for visible notes independently of crowded engagement results and recent-reaction history; keep exact signed event IDs for deletion.

## [0.31.0] - 2026-10-11

### Added
- Tap transient error messages or their copy icon to copy the full message, including zap errors; keep messages accessible above galleries, fullscreen video, and settings.
- Show author avatars and word-count-based reading time estimates (“5 min read”) in article lists.
- Show NIP-05 instead of the profile username when present, with background verification: green check for a matching identity, double check for a root identity, warning for invalid claims, and copyable verification details.

### Changed
- Tap the active profile tab or main-feed pill icon again to scroll its feed to the top.
- Open profile avatars and banners together in a swipeable gallery, starting on the tapped image.
- Present gallery reaction, zap, and open-note actions as separate floating buttons, preserving auto-hide and the long-press emoji picker.

### Fixed
- Render up to 32 attachments on ordinary notes, including detail views, instead of leaving media beyond the first four as bare links.
- Include the original LNURL in signed zap requests and invoice callbacks; identify the provider and hash mismatch when an invoice fails validation, without sending payment.

## [0.30.1] - 2026-10-11

### Fixed
- Ignore malformed relay URLs before routing, preventing crashes when relay metadata contains several addresses joined into one.

### Changed
- Overlap the profile avatar and identity row with the banner’s faded bottom edge.
- Present articles as a compact reading list with a title, two-line excerpt, optional right-side cover thumbnail, and quiet author/time metadata.

## [0.30.0] - 2026-10-11

### Added
- React and send 21-sat zaps to an image’s source note directly from the gallery, including long-press emoji selection, reaction removal, and shared engagement state.

### Changed
- Let gallery images fill the viewport with their aspect ratio preserved; hide system bars and float auto-hiding controls over the image, revealed by a single tap.
- Cycle timestamp format globally when tapping any note timestamp, remembering the choice across app restarts.

## [0.29.0] - 2026-10-11

### Added
- Read NIP-22 kind-1111 comments in reply feeds, profiles, embeds, and threads, with parent navigation and reply counts.

### Changed
- Fade profile banners softly into the profile surface in light and dark mode.
- Hide the Android status bar in Photos feeds/profile tabs and image galleries; restore it when leaving.
- Prioritize Posts, Highlights, Photos, Videos, Articles, and Replies in that order on home and profiles; keep other categories ordered by observed frequency.
- Shrink and fade the home avatar to a dot when hiding, reversing the animation when it returns.

## [0.28.2] - 2026-10-11

### Changed
- Slide the main-feed avatar and selector in and out from the top and bottom screen edges respectively.
- Move the floating feather button 8dp farther inward from the right edge.
- Fade the floating feather button to 40% opacity during feed scrolling or page swipes, restoring full opacity when movement stops.
- Hide the top-right account avatar on downward scroll and restore it on upward scroll, sharing the bottom bar’s scroll threshold.
- Thicken the home avatar border to 2dp and color it gold when the signed-in account has a connected NWC wallet; otherwise keep it neutral.

## [0.28.1] - 2026-10-11

### Changed
- Keep the main feed selector at the bottom, restore a smaller bordered account avatar at the top right, and make the feather a floating scroll-to-top action above the selector.

## [0.28.0] - 2026-10-11

### Changed
- Move the main feed bar to the bottom, preserving its controls, swipe indicator, and scroll visibility behavior. Respect navigation-bar insets and leave room to reach the last feed item. Profile and note bars remain at the top.

## [0.27.2] - 2026-10-11

### Fixed
- Fetch the signed-in account’s follow list independently from profile metadata, using existing account and bootstrap relays so a missing contact list does not leave the main feed showing only the account’s own posts.

### Changed
- Standardize round toolbar, login, and settings controls at 48dp, with a matching borderless top-right account avatar.

## [0.27.1] - 2026-10-11

### Fixed
- Revert the profile metadata recovery changes introduced in 0.26.2, restoring the previous relay priorities, subscriptions, and batched local metadata query.

## [0.27.0] - 2026-10-11

### Added
- Scan NWC QR codes directly in the wallet sheet, with local QR decoding, runtime camera permission, and validation before filling the masked connection field.

### Changed
- Right-align the NWC confirm button beside the QR scan action and replace its checkmark with a centered spinner while connecting.
- Compact settings footer: smaller GitHub icon at the left, version and commit links at the right.
- Place settings and login side by side in the logged-out account sheet, right-aligned with matching tonal icon buttons.

## [0.26.2] - 2026-10-11

### Fixed
- Retry missing profile metadata across rotating fallback relays and prioritize available write relays. Fetch viewed/missing profiles individually so shared response limits cannot starve their name and avatar metadata.

### Changed
- Compact zap activity cards: place the timestamp beneath the amount between profiles, remove its separate top row, and reduce vertical padding.

## [0.26.1] - 2026-10-11

### Changed
- Use an 18+ icon for the sensitive-content setting, retaining an accessible switch description.
- Replace the ambiguous signer-login arrow with a right-aligned door-and-arrow login icon, remove the decorative feather from the login sheet, and label the missing-signer action “Get Amber”.

## [0.26.0] - 2026-10-11

### Added
- Hide author-marked NSFW/content-warning content by default across feeds, embeds, bookmarks, and media galleries. Add a persistent “Show NSFW content” opt-in setting.

## [0.25.0] - 2026-10-11

### Fixed
- Bind NWC connections to the signed-in account, remove credentials on logout/account changes, and hide wallet setup while logged out. Existing unbound connections require reconnecting; payment history remains protected.
- Show the failing zap step, HTTP status, Lightning endpoint reason, or NWC wallet error instead of a generic failure. Preserve payment uncertainty and redact connection secrets.

### Added
- Tap your displayed reaction again to remove it, including custom emojis. Selected picker reactions also toggle off via signed NIP-09 deletion requests.

## [0.24.0] - 2026-10-11

### Fixed
- Let reactions and zaps be tapped in quick succession without disabling other note actions. Track progress per note/action and serialize only the shared signer interaction across action types.

### Added
- Dynamic Zap activity tab on profiles and home feeds, showing sent/received Lightning zaps and Cashu nutzaps with sender, recipient, amount/unit, comment, and linked note. Nutzaps use purple styling, matching ants-android.

## [0.23.4] - 2026-10-11

### Changed
- Render highlighted passages with a yellow background only, without an added underline.

### Fixed
- Color the zap bolt gold for loaded, validated zap receipts sent by the current account from any app, alongside locally confirmed payments.

## [0.23.3] - 2026-10-11

### Changed
- Center note actions within rounded touch targets and keep icons, reactions, and loading spinners in fixed-size slots to prevent shifts on tap.
- Align the wallet balance to the right of its name on the same row.

## [0.23.2] - 2026-10-11

### Changed
- Show the connected wallet's name and balance when permitted, loaded in the background when opening its sheet.
- Confirm successful zaps with a golden zap icon instead of a toast; retain confirmed status per account across restarts.

### Fixed
- Keep the reaction icon still while zapping; only the zap button shows payment progress.

## [0.23.1] - 2026-10-11

### Added
- Subtle settings footer with version and build commit, linking to the GitHub release and commit respectively.

### Changed
- Default the media cache to 1 GB, preserving saved cache limits.
- Separate cache usage and limit readouts from the slider; remove the competing usage progress bar.
- Use the GitHub logo for the settings link to the source repository.
- Place the wallet button immediately to the left of logout in settings.
- Use a trash icon to remove the Nostr Wallet Connect connection.
- Remove explanatory wallet copy and the 21-sat label from the wallet sheet.

## [0.23.0] - 2026-10-11

### Added
- Dynamic Emoji packs category for authored kind-30030 sets on profiles and the main feed, with browsable emoji images.
- Nostr Wallet Connect in settings, encrypted credential storage, and explicit 21-sat post zaps through the connected wallet and Android signer. Verify invoice/payment details and retain pending invoices to avoid duplicate payments after timeouts.

## [0.22.2] - 2026-10-11

### Changed
- Enlarge the signed-in toolbar avatar from 28 to 36 dp and remove its surrounding circular background.

## [0.22.1] - 2026-10-11

### Fixed
- Keep a viewport-sized minimum beneath profile tabs while loading or showing short content, preventing the profile header from snapping back into view during tab switches. Rows remain lazy; measured content replaces the reserved space.

## [0.22.0] - 2026-10-11

### Added
- Automatically load your public NIP-51 emoji collection and referenced packs into the reaction picker, with a custom-emoji row and local shortcode search. Reuse the existing signer and image cache; no setup or private-list access.

### Changed
- Show your most recent reaction on each post’s reaction button, including custom emoji, instead of a generic orange heart.

## [0.21.1] - 2026-10-11

### Fixed
- Temporarily reveal only the feed-selector pill during horizontal main-feed swipes when the top bar is hidden. Hide it again once the swipe settles or is cancelled, preserving normal top-bar visibility.

## [0.21.0] - 2026-10-11

### Added
- Reposts category on profiles and the main feed, shown when repost activity is discovered and ordered by frequency after Posts and Replies.

### Fixed
- Reduce category-picker emoji size to match search results more closely, with adaptive columns on wider screens.
- Show a compact reply icon and tappable parent-note preview for replies, including notes with only a NIP-10 root tag. Resolve parent context through the existing bounded background cache/fetch pipeline.

### Changed
- Remove the website’s app version label and decouple website deployments from app releases. Keep evergreen GitHub latest-release and Zapstore links.

## [0.20.0] - 2026-10-11

### Added
- Offline search in the full emoji picker by English names and keywords, including multiword searches and skin-tone variants. Load and filter the bundled Unicode index off the UI thread.

## [0.19.0] - 2026-10-11

### Added
- Six recent/frequent quick reactions plus an icon to open the native AndroidX full emoji picker, including categories and skin-tone variants. Full-picker selections use the existing signer and duplicate-reaction protection.

## [0.18.0] - 2026-10-11

### Changed
- Restore the transparent home toolbar; place a semi-transparent pill behind only the five-icon feed selector. Slide, scale, and fade the icons continuously with the swipe gesture, including cancelled swipes and tap-to-switch animations.

### Added
- Save uncaught Java/Kotlin crashes locally and show the error and stack trace on the next launch, with selectable text and one-tap copying. Keep one bounded report until dismissed; no automatic uploads.

## [0.17.1] - 2026-10-11

### Changed
- Give the home toolbar a subtle theme-aware background and show five feed-category slots, with progressively faded neighboring icons.

## [0.17.0] - 2026-10-11

### Changed
- Keep a shared home toolbar stationary during feed swipes. Show the current category icon between softly faded, tappable previous/next hints, without a conventional tab bar.

### Fixed
- Align note and embedded-note avatars with the content’s left edge while retaining 48 dp profile tap targets. Reduce repost attribution height from 48 dp to 24 dp.

## [0.16.1] - 2026-10-11

### Fixed
- Remove the tumbleweed. Show a spinner while preparing an empty view or waiting for content, allow brief relay/cache handoffs to settle, and leave genuinely empty feeds quiet. Keep error and hidden-content details.

## [0.16.0] - 2026-10-11

### Added
- Swipe between full-screen home feeds without a tab bar. Keep Posts and Replies first, then order all other discovered categories by activity frequency. Preserve each page’s scroll position and cached content, with only the settled page driving live reading, pagination, and media playback.

## [0.15.0] - 2026-10-11

### Added
- Long-press the heart for five emoji choices based on recent and frequent reactions, including previously used custom emoji. Keep a normal tap as 🧡; mark sent choices and preserve per-emoji retry IDs.
- Use a crossed-out account for muted/blocked content, a tumbleweed for empty content, an info icon for unavailable posts, and a warning triangle for reported loading errors. Tap any of them to view selectable status details and copy the message, app version, view, target, and relay connection count. Distinguish empty or filtered content from reported errors.
- Automatically hide notes from accounts in your NIP-51 mute/block list across feeds, threads, profiles, reposts, embeds, bookmarks, and media prefetch. Support public entries, signer-decrypted private entries, offline caching, and the deprecated mute-list format.

## [0.14.0] - 2026-10-11

### Fixed
- Render every Markdown text block in full articles and highlights instead of only the first paragraph. Keep unsupported blocks visible as plain text, and retain compact excerpts only in preview cards.

### Added
- Tap a post’s heart to send a 🧡 reaction through the connected Android signer. Show pending/sent states, verify the signed response, publish through discovered relays, and reuse persisted signed events for manual retries. Guests are directed to signer login.
- Open the current gallery image’s source note with an icon. Preserve per-image source events across Photos-tab browsing, regular/embedded notes, and article images; open cached content immediately.

## [0.13.0] - 2026-10-11

### Added
- Tap the Feather icon to return to the top of the current feed; animate short distances and jump immediately when far down the feed.

### Fixed
- Give reply, reaction, and zap indicators consistent minimum widths and spacing, including when zero counts are hidden.
- Hide orphaned U+FFFC object-replacement markers in notes, embedded notes, article text, highlights, and profile biographies without modifying signed events.
- Restrict author-profile navigation on regular note cards to the avatar button. Author names and header whitespace open the note; repost attribution names also open the note, with profile navigation limited to the repost icon.

## [0.12.1] - 2026-10-11

### Fixed
- Resolve public address-based article bookmarks from cache and relays into author/title/image previews, and open loaded articles directly in Feather. Keep visible article subscriptions current and retain external fallback for unavailable or unsupported content; encrypted list content remains untouched.

## [0.12.0] - 2026-10-11

### Fixed
- Scope gallery openings from the Photos tab to its full loaded image collection, including callers that supply only the tapped image. Preserve the selected index and expose paging controls for the album.
- Render extensionless image/video URLs, including AT Protocol blob links, using Content-Type discovered by the existing background link-preview fetch. Cache discoveries and replace raw links/web cards with normal inline media and gallery/player controls.

### Added
- Dynamic Articles, People, and Projects profile tabs: in-app Markdown articles with cover/inline images, public follows and named people lists with profile navigation, and repository cards with source links. Deduplicate replaced articles/lists/projects; resolve visible people in bounded background batches.
- Show media-cache usage and a usage bar alongside the storage limit in settings; read cache counters on IO only while settings is open and foregrounded.
- Dynamic Apps profile tab for publisher-signed Zapstore app metadata (kind 32267), with cached icons, name/summary cards, catalog relay discovery, and publisher-specific links opening in Zapstore or the browser.
- Website dark mode follows the system setting, using Feather’s app palette on the homepage and privacy page.

## [0.11.1] - 2026-10-11

### Fixed
- Restrict embedded-note profile navigation to the avatar’s 48 dp button; the author name and remaining header space open the note.
- Show the profile settings gear only on the signed-in user’s own profile.
- Open the full loaded profile-photo sequence in the gallery so swiping left/right moves between posts, starting at the exact tapped thumbnail. Prepare gallery URLs off-main alongside the grid.

## [0.11.0] - 2026-10-10

### Added
- Dynamic Photos, Videos, and public Bookmarks profile tabs. Photos use a thumbnail grid and the existing gallery; videos reuse the cached player, including HLS streams. Discover dedicated media kinds alongside attachments in regular notes. Resolve bookmarked notes and provide external links for addressable items.
- Copy a profile’s Lightning address using a bolt immediately left of the copy-npub button.

### Changed
- Keep Posts and Replies first, then order profile tabs by observed matching-event frequency, with a horizontally scrollable icon-only tab bar.
- Center the profile biography expand/collapse chevron.
- Improve custom emoji reaction rendering with SVG support, existing animated GIF playback, shortcode fallback on loading/failure, and media preloading.
- Render video mute and fullscreen overlay icons in white without button backgrounds.

### Fixed
- Android Back and the toolbar back arrow retrace profile, thread, and hashtag visits instead of jumping to the feed. Retain each visit’s scroll position, selected profile tab, toolbar state, expanded content, and previously displayed notes; restore navigation after activity recreation.

## [0.10.1] - 2026-10-10

### Changed
- Give timestamps a minimum 64 × 48 dp touch target while keeping relative-time labels compact and right-aligned.
- Open YouTube links and preview cards in NewPipe when installed, then YouTube or another available video app, with browser fallback. Preserve timestamps and other URL parameters.
- Warm the first 4 MiB of up to three upcoming videos, looking twelve notes ahead with two background requests and the existing media-cache budget. Cancel speculative work when playback takes over or the feed is covered.
- Render videos at their actual aspect ratio, using note metadata, cached-file dimensions, and player size updates so portrait clips fill the feed width.
- Persist every successful timestamp-to-block-height lookup without expiry in local app data; reuse it across restarts and memory-cache eviction. Migrate valid entries from the old cache and keep failed lookups retryable.
- Hide the bottom comment/share action bar on reaction cards.
- Use a text-highlighter marker icon for the Highlights profile tab.

## [0.10.0] - 2026-10-10

### Added
- Rich Open Graph link cards with preview images, titles, descriptions, and source domains; fall back to Twitter/HTML metadata and retain YouTube cards. Fetch and parse in the background with bounded local caching.
- Include NIP-18 text-note reposts in the main feed and pagination. Show a repost icon and the reposter’s name above the original note; reuse verified enclosed originals or resolve them from relays.

### Fixed
- Fullscreen volume swipes explicitly unmute playback and adjust Android’s media volume, with feedback showing the actual device level.

### Changed
- Default note timestamps to short relative time (`5h`, `2d`, `1w`). Each tap cycles through date/time, comma-separated block height, and relative time; update relative labels only when visible and active.
- Hide the image counter and previous/next arrows in galleries containing only one image.
- Preserve the current scroll position and toolbar visibility when tapping or swiping between profile tabs.
- Center the expand/collapse chevron below note text.

## [0.9.1] - 2026-10-10

### Changed
- Format Bitcoin block heights with comma separators and no prefix, for example `987,654`.

## [0.9.0] - 2026-10-10

### Added
- Rich YouTube previews with a thumbnail, title, and channel name in notes and embedded notes. Cache metadata and thumbnails locally; open the original link in YouTube or the browser.

### Changed
- Move the inline fullscreen control to the bottom-right; use the native fullscreen button when playback controls are visible.

### Fixed
- Keep partially visible videos playing while scrolling; retain the current clip until it fully leaves the viewport instead of stopping below 60% visibility.
- Render animated GIFs in the feed and gallery; stop animations offscreen and in the background.
- Keep fullscreen videos open: Media3's initial fullscreen-state callback no longer immediately dismisses the player.
- Use a transparent feather splash drawable with system-following light/dark launch backgrounds.
- Show a spinner while notes load, and a neutral empty-state icon instead of a feather when no notes are available.

## [0.8.3] - 2026-10-10

### Changed
- Show profile tabs only for discovered activity: posts, replies, reactions, and highlights. Probe rare kinds independently in the background, and make swipes skip hidden tabs.

## [0.8.2] - 2026-10-10

### Changed
- Float the main toolbar transparently over the feed with theme-aware circular control backgrounds, matching the profile banner treatment.
- Replace the load-more button with automatic near-end pagination in feeds, profile tabs, and hashtag timelines, with a small footer progress indicator.
- Keep history requests serialized, serve cached pages first, retain visible notes in bounded rolling windows, and stop repeated requests when a page adds nothing. Pull-to-refresh returns to the latest notes.

## [0.8.1] - 2026-10-10

### Added
- Swipe left/right between adjacent profile tabs, preserving the tab-bar scroll anchor and leaving vertical scrolling and pull-to-refresh intact.

## [0.8.0] - 2026-10-10

### Added
- Tap note timestamps to toggle Bitcoin block height; resolve blocktime on demand in the background with a bounded persistent cache and date/time fallback.
- Fullscreen video playback with native controls, timeline scrubbing, playback speed, and subtitles when available. Keep the same decoder and playback position across fullscreen transitions and rotation.
- VLC-style video gestures: double-tap left/right to seek ten seconds in the feed and fullscreen; swipe horizontally to scrub, or vertically for brightness (left) and volume (right) in fullscreen.
- Download videos to Movies, share video files, and copy video URLs from the fullscreen toolbar. Export cached video ranges and missing bytes on IO with bounded temporary storage.

### Changed
- Show total validated zap amounts in sats beside the lightning icon instead of receipt counts; deduplicate invoices and calculate amounts in the existing background validation worker.

## [0.7.2] - 2026-10-10

### Changed
- Collapse profile biographies to three lines, with an accessible chevron to expand the full text and embedded event previews.

## [0.7.1] - 2026-10-10

### Fixed
- Render Nostr event previews, profile mentions, hashtags, and links in profile biographies; resolve referenced events and profiles in the background.
- Keep profile tab switches anchored at the tab bar instead of jumping to the profile header; hide the toolbar so it cannot cover the tabs.

## [0.7.0] - 2026-10-10

### Fixed
- Open tapped notes immediately from memory in detail views, including profile activity, hashtag results, and embeds. Retain the selected note independently of feed limits while cached replies and relay updates load in the background.

### Added
- Clickable hashtags in notes, embedded posts, and highlights open a native chronological hashtag feed, with pull-to-refresh and older posts.

### Changed
- Fit inline images to the full content width at their natural aspect ratio; reuse prefetched dimensions instead of forcing photos into widescreen boxes.
- Shorten the Zapstore description to two sentences.

## [0.6.0] - 2026-10-10

### Added
- Icon-only profile tabs for posts, replies, reactions, and highlights, with kind-specific subscriptions and pagination.
- Ants-style highlights: Markdown, surrounding context, yellow passage emphasis, comments, source links, and author attribution, formatted in the background.
- Reply, reaction, and zap counts beneath posts. Counts reflect bounded, locally discovered relay events; zap provider lookup and receipt validation run independently in the background and never delay displaying a post.

### Fixed
- Resolve missing embedded events through rotating fallback relays, retain resolved previews, and render full embedded text and up to 32 media attachments in detail views.

### Changed
- Follow the Android system dark-mode setting automatically across reading, profiles, settings, media controls, and launch surfaces; no theme preference.
- Use a transparent feather launcher icon and matching store/web icon artwork, removing the solid background. Launcher-imposed icon styling can still vary.
- Show the feather logo only on the main feed; screens with back navigation omit it.
- Extend profile banners behind a transparent toolbar, with theme-aware circular backgrounds to keep navigation icons legible. Restore the solid toolbar once the banner scrolls out from behind it.

## [0.5.7] - 2026-10-10

### Fixed
- Restore avatars in profiles, the toolbar, and notes: the thumbnail interceptor now preserves Coil's request size resolver and uses the interceptor chain to select thumbnail dimensions. Previously Coil rejected avatar requests even when the same image loaded successfully in the gallery.

## [0.5.6] - 2026-10-10

### Changed
- Move the working logout action to the top of settings beside the connected identity, and remove it from below the profile biography.
- Removed generic supporting slogans from the website, social preview, README, brand guidelines, and Zapstore description.

## [0.5.5] - 2026-10-10

### Changed
- Prefetch avatars, images, and video posters six notes ahead and two behind, with two concurrent speculative requests, separate HTTP scheduling, shared caches, and background URL planning.
- Preserve useful in-flight downloads across scrolling, react when profile metadata arrives, and ignore non-note list items when finding the prefetch window.
- Move the profile settings gear to the top-right and restore back navigation to the top-left.

## [0.5.4] - 2026-10-10

### Changed
- Move settings to the top-left toolbar position on profile views, with back navigation on the right. Remove the settings gear from the profile body.

## [0.5.3] - 2026-10-10

### Changed
- Profile layout follows ants-android’s structure with Feather styling: optional banner, avatar beside name and username, left-aligned bio, compact copy action, and a separate row for your own account controls.

## [0.5.2] - 2026-10-10

### Changed
- Tap a note's body or surrounding row to open its detail/thread view. Author names, links, media, quoted notes, and action buttons keep their own interactions.

## [0.5.1] - 2026-10-10

### Fixed
- Display decoded avatars immediately without waiting for thumbnail encoding or disk writes. A single background worker saves thumbnails with a bounded queue and duplicate suppression, and stops before cache replacement.

## [0.5.0] - 2026-10-10

### Added
- Tappable profile mentions for npub/nprofile references and legacy indexed mentions, resolved to display names.
- Embedded text-note previews for note/nevent references and quote tags, with relay hints, cached content, and one level of previews. Unsupported event kinds open externally.
- Local 256-pixel WebP avatar thumbnails sharing the existing image cache budget; original files remain separate for full-size viewing.

### Fixed
- Profile metadata requests now follow visible notes and nearby content rather than only the newest 100 notes, with a metadata indexer and subscriptions restored after routing changes.
- Images retry transient failures twice automatically while visible and active.

### Changed
- The toolbar has one account icon. Settings and disconnect move to your profile; guests can access settings from the account sheet.
- Aligned the README, GitHub description, Zapstore summary, and website description metadata with the tagline “a lightweight android client”.

## [0.4.0] - 2026-10-10

### Added
- Automatic relay selection from the account’s NIP-65 relay set, with twelve bootstrap candidates, background outbox discovery for guest-feed authors, and connection-failure cooldowns.

### Changed
- Convention over configuration is a core design principle: shared defaults, no relay setup, and only a media cache budget preference.
- White account and settings bottom sheets, a toolbar account avatar, and a calmer profile header with a copy-key icon.
- Brand tagline changed to “a lightweight android client” across the website and social preview.

### Removed
- Manual relay configuration and its saved preferences.

## [0.3.0] - 2026-10-10

### Added
- Full-screen image gallery adapted from ants-android and boris-android: swipe between images, pinch and double-tap zoom, black/white/gray backgrounds, save, share, copy URL, and external opening.
- Gallery for note images and profile pictures, reusing the existing media cache. Video playback pauses while the gallery is open.

### Changed
- Top bar hides after scrolling down 32 dp and reappears after scrolling up 32 dp, with a short animation and automatic restoration at the top.
- Pull down to refresh feeds, profiles, and conversations, including empty lists. Removed the toolbar and empty-state refresh buttons.
- Added a screen-reader refresh action without a visible label.

## [0.2.0] - 2026-10-10

### Added
- Automatically loaded profile pictures and inline images, using Coil with persistent caching and nearby-note prefetch.
- Muted autoplay for a visible video, using one shared Media3 player and a persistent video cache.
- Media-storage setting from 128 MB to 2 GB, with a 512 MB default and bounded image memory.
- NIP-65 outbox discovery for feeds, profiles, metadata, conversations, and older notes, using a shared budget of up to eight discovered relays plus four configured relays.
- Draft design philosophy covering reader-only scope, visual identity, icon-only controls, accessibility, and performance.

### Changed
- Feather is now a reader: chronological notes, existing follows, profiles, and conversations.
- Login exclusively uses an Android signer such as Amber, with no signing or encryption permissions.
- Icon-only interface controls, more space around notes, and comfortable line spacing.
- Feed updates are sampled to keep content appearing during continuous relay traffic.
- Expansion controls use actual text overflow, making long wrapped notes readable; web links open directly from note text.

### Removed
- Posting, replying, drafts, follow/unfollow editing, and profile search.
- Read-only npub login. Existing sessions without a signer are disconnected on upgrade.

### Notes
- Up to two write relays per author within the connection budget; configured relays cover unknown or unselected routes. Authenticated relays and exhaustive history are not supported.
- Notes and profile content remain text; interface labels are replaced with icons and screen-reader descriptions. Essential errors and editable relay addresses remain visible.

## [0.1.0] - 2026-10-10

### Added
- Native Kotlin/Compose app with the Soft Plume feather identity and white/light-blue theme.
- Chronological public and following feeds backed by a bounded local Quartz/SQLite cache.
- Android signer login, read-only npub accounts, profiles, follows, public notes, and replies.
- Saved drafts, signature verification, and relay-acknowledged publishing.
- Up to four configurable secure relays; connections pause in the background.
- Static website, privacy policy, CI, and GitHub/Zapstore release tooling.

### Notes
- Initial text-first MVP. No DMs, wallet, media uploads, reactions, or notifications.
- Following subscriptions cover up to 1,000 authors; notes and profiles are cached with retention limits.

[Unreleased]: https://github.com/dergigi/feather/compare/v0.69.5...HEAD
[0.69.5]: https://github.com/dergigi/feather/compare/v0.69.4...v0.69.5
[0.69.4]: https://github.com/dergigi/feather/compare/v0.69.3...v0.69.4
[0.23.0]: https://github.com/dergigi/feather/compare/v0.22.2...v0.23.0
[0.22.2]: https://github.com/dergigi/feather/compare/v0.22.1...v0.22.2
[0.22.1]: https://github.com/dergigi/feather/compare/v0.22.0...v0.22.1
[0.22.0]: https://github.com/dergigi/feather/compare/v0.21.1...v0.22.0
[0.21.1]: https://github.com/dergigi/feather/compare/v0.21.0...v0.21.1
[0.21.0]: https://github.com/dergigi/feather/compare/v0.20.0...v0.21.0
[0.20.0]: https://github.com/dergigi/feather/compare/v0.19.0...v0.20.0
[0.19.0]: https://github.com/dergigi/feather/compare/v0.18.0...v0.19.0
[0.18.0]: https://github.com/dergigi/feather/compare/v0.17.1...v0.18.0
[0.17.1]: https://github.com/dergigi/feather/compare/v0.17.0...v0.17.1
[0.17.0]: https://github.com/dergigi/feather/compare/v0.16.1...v0.17.0
[0.16.1]: https://github.com/dergigi/feather/compare/v0.16.0...v0.16.1
[0.16.0]: https://github.com/dergigi/feather/compare/v0.15.0...v0.16.0
[0.15.0]: https://github.com/dergigi/feather/compare/v0.14.0...v0.15.0
[0.14.0]: https://github.com/dergigi/feather/compare/v0.13.0...v0.14.0
[0.13.0]: https://github.com/dergigi/feather/compare/v0.12.1...v0.13.0
[0.12.1]: https://github.com/dergigi/feather/compare/v0.12.0...v0.12.1
[0.12.0]: https://github.com/dergigi/feather/compare/v0.11.1...v0.12.0
[0.11.1]: https://github.com/dergigi/feather/compare/v0.11.0...v0.11.1
[0.11.0]: https://github.com/dergigi/feather/compare/v0.10.1...v0.11.0
[0.10.1]: https://github.com/dergigi/feather/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/dergigi/feather/compare/v0.9.1...v0.10.0
[0.9.1]: https://github.com/dergigi/feather/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/dergigi/feather/compare/v0.8.3...v0.9.0
[0.8.3]: https://github.com/dergigi/feather/compare/v0.8.2...v0.8.3
[0.8.2]: https://github.com/dergigi/feather/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/dergigi/feather/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/dergigi/feather/compare/v0.7.2...v0.8.0
[0.7.2]: https://github.com/dergigi/feather/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/dergigi/feather/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/dergigi/feather/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/dergigi/feather/compare/v0.5.7...v0.6.0
[0.5.7]: https://github.com/dergigi/feather/compare/v0.5.6...v0.5.7
[0.5.6]: https://github.com/dergigi/feather/compare/v0.5.5...v0.5.6
[0.5.5]: https://github.com/dergigi/feather/compare/v0.5.4...v0.5.5
[0.5.4]: https://github.com/dergigi/feather/compare/v0.5.3...v0.5.4
[0.5.3]: https://github.com/dergigi/feather/compare/v0.5.2...v0.5.3
[0.5.2]: https://github.com/dergigi/feather/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/dergigi/feather/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/dergigi/feather/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/dergigi/feather/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/dergigi/feather/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/dergigi/feather/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/dergigi/feather/releases/tag/v0.1.0
