# 0.3.0 validation

Signed release build only, following the requested fast-iteration workflow. No new tests or emulator regression pass were run. The gallery reuses the ants-android implementation, adapted to Feather’s media cache and icon-only controls. Pull-to-refresh uses Material 3; toolbar visibility requires 32 dp of directional user scrolling.

# 0.2.0 validation

Checked locally on October 10, 2026, before the request to skip further tests:

- Twenty JVM tests passed, covering protocol parsing, signature verification, NIP-65 relay directions and bounded routing, media metadata, storage budgets, and autoplay visibility eligibility.
- Android Lint passed with no errors before the final minor media-memory-cap adjustment.
- Android 15 emulator: signer-only account dialog, missing-signer recovery, legacy npub-session removal, following feed from a debug preference fixture, and 151 verified NIP-65 relay lists in the local cache.
- Local signed media fixtures exercised automatic avatars, inline images, muted video autoplay, and persistent image/video cache writes. No fixture event was published.
- A scripted cache-slider tap did not change the displayed value; cache-size persistence was not confirmed before further testing was stopped.
- Desktop and mobile website layout checked before final media-copy changes.
- Custom-domain DNS and HTTPS were verified after the DNS update.

The user requested direct master publication without additional tests. Final packaging therefore uses the signed release build without another test/lint pass. No physical-device benchmarks or real Amber approval round trip were performed. Storage-limit and background behavior have unit coverage and implementation guards, but have not received a complete final device regression pass.

# 0.1.0 validation

Checked locally on October 10, 2026:

- Eight JVM tests cover public-key parsing, relay validation, reply threading, contact tags, replaceable-event ordering, signed-event round trips, and tamper rejection.
- Android Lint passes with no errors. Remaining warnings are API convenience/version suggestions and a redundant resource qualifier.
- Optimized, resource-shrunk signed release APK builds successfully.
- APK signing certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd` (same local signing configuration as boris-android).
- Android 15 / API 35 ARM64 emulator: release installation and launch, live public feed, profile metadata, relay settings, and cached feed after force-stop/relaunch with Wi-Fi/data disabled.
- Read-only npub login and following-feed loading exercised in the debug build.
- No AndroidRuntime crash in the smoke-test log.
- Website checked in Chromium at 1440px and 390px widths; no horizontal overflow; privacy page loads.

Limits: no physical-device testing or end-to-end Amber approval/public posting test has been performed. Tests do not publish notes or change a real account's follows. Performance observations from an emulator are not device benchmarks.


## 0.4.0 — October 10, 2026

Signed optimized release build passed after automatic relay selection and account/settings/profile changes. No additional automated tests or device smoke test, following the requested fast iteration workflow. NIP-66 is documented future work; no monitor-based liveness claims are made.


## 0.5.0 — October 10, 2026

Signed optimized release build passed for visible-note profile fetching, Nostr references, local avatar thumbnails, and profile settings placement. No automated suite or device smoke test was run for this iteration. Original remote media availability remains outside Feather's control. Quoted previews cover kind-1 text notes; unsupported identifiers link externally.


## 0.5.7 — October 10, 2026

Traced the avatar/gallery mismatch to Coil 3.6.3's `RealInterceptorChain.checkRequest`: interceptors must preserve the original size resolver. Removed the request-builder size override; both original and cached-thumbnail paths already use the supported `Chain.withSize` API. Background thumbnail writes and shared cache limits are unchanged. Signed release build passed. No device smoke test or automated suite was run, following the requested fast iteration workflow.


## Unreleased — October 10, 2026

Clean debug build passed for the profile banner/toolbar overlay, feed-only toolbar logo, transparent launcher artwork, and system-following dark theme. Checked store-icon alpha and inspected the regenerated artwork. No automated suite, emulator, or device visual pass was run. No version bump, release tag, or store publication.


## 0.6.0 — October 10, 2026

Debug and signed optimized release builds passed for profile tabs, NIP-84 highlights, observed engagement counts, system dark mode, transparent icon artwork, banner toolbar layout, and full detail embeds. Checked the exact quoted event from the reported screenshot: `ac5770b9…5d8f` was absent from Damus/nos.lol/Primal queries and returned by nostr.mom and nostr.wine. Feather now rotates two fallback seed routes for unresolved embeds within its existing connection budget.

Zap provider lookup and receipt validation are independent background jobs: two provider lookups, one validation worker, at most 64 queued receipts. Post snapshots read cached results and enqueue optional work without waiting for it. Counts cover bounded discovered events, not exhaustive totals. No new automated suite or device/emulator visual pass was run, following the requested fast iteration workflow.


## Unreleased image sizing — October 10, 2026

Debug build passed. Inline images request a width-constrained decode with unconstrained height and update their layout from decoded dimensions; image metadata ratios are no longer clamped to video proportions. Prefetch shares a bounded 256-entry dimensions cache. No device visual pass or new tests were run.


## Unreleased hashtags — October 10, 2026

Debug build passed. Hashtags share the link tokenizer across notes, quotes, and highlights and are excluded from event/author lookups. Hashtag feeds query kind-1 events by normalized `t` tags, use existing bounded relays, page older posts, and unsubscribe on navigation. Unicode letters, numbers, combining marks, and underscores are recognized; URL fragments stay part of their URL. No new tests or device visual pass were run.


## Unreleased detail handoff — October 10, 2026

Debug build passed. Detail navigation immediately retains the clicked event, or resolves it from in-memory feed/profile/hashtag/embed state. The selected event is stored independently of the general feed limit, precedes replies, and survives delayed database snapshots. Snapshot updates guard against replacing a newer navigation target. Disk and relay work remains on the repository background scope. No device smoke test or automated suite was run.


## 0.7.0 — October 10, 2026

Signed optimized release build passed for natural image sizing, native hashtag feeds, and immediate cached-note detail navigation. Verified APK version 0.7.0 (code 14) and the existing signing certificate. No additional automated tests or device visual pass were run.


## Unreleased profile refinements — October 10, 2026

Debug build passed. Profile tab switches retain the list state and request the tab-bar item as the next scroll anchor, hiding the overlay toolbar. Biography references are tokenized in the repository background scope, included in bounded event/profile discovery, and rendered as in-place previews or rich links. At most three biography event previews are fetched; unsupported event types remain external links. No device visual pass or new automated tests were run.


## 0.7.1 — October 10, 2026

Signed optimized release build passed for profile biography reference rendering and tab-bar scroll anchoring. Verified APK version 0.7.1 (code 15) and the existing signing certificate. No additional automated tests or device visual pass were run.


## Unreleased compact biographies — October 10, 2026

Debug build passed. Biographies initially lay out at most three lines with ellipsis and retain clickable rich links. An accessible chevron reveals the existing full biography renderer and embedded cards; expansion resets when the profile or biography changes. Hidden cards are composed only when expanded. Short plain biographies have no redundant toggle. No device visual pass or new automated tests were run.


## 0.7.2 — October 10, 2026

Signed optimized release build passed for compact profile biographies. Verified APK version 0.7.2 (code 16) and the existing signing certificate. No additional automated tests or device visual pass were run.


## Unreleased video controls and zap amounts — October 10, 2026

Debug build passed. Reviewed single-player handoff, fullscreen lifecycle/rotation handling, gesture direction locking and seek bounds, native-control hit handling, and export cancellation/serialization. Controls can hide without disposing export jobs. Video exports stream through the existing cache on IO, validate media metadata, clean partial files, and use scoped MediaStore/FileProvider grants. Zap invoice amounts are parsed only by the existing background validator, summed exactly with BigDecimal, and deduplicated before display. No Android device was connected; no device visual/gesture pass or additional automated suite was run.


## Unreleased blocktime — October 10, 2026

Verified the mempool.space timestamp endpoint with Unix time 1700000000: height 816785, block timestamp 2023-11-14T21:51:06Z. Checked the upstream query selects a non-stale block timestamp at or before the requested time. Note timestamps retain date/time during lookup, toggle independently, and fall back on failure. Network and bounded cache persistence run on IO; writes are conflated and do not delay showing a resolved height. No device UI pass or automated suite was run.


## 0.8.0 — October 10, 2026

Signed optimized release build passed for fullscreen video gestures and export actions, sats-based zap totals, and blocktime timestamps. Verified APK version 0.8.0 (code 17) and the existing signing certificate. No device was connected; gesture, rotation, and sharing behavior have not had a device smoke pass.


## Profile tab swipes — October 10, 2026

Debug build passed. Horizontal swipes of at least 56 dp select the adjacent profile tab through the same handler as tab taps, preserving the tab-bar anchor. Direction follows layout direction, tab ends do not wrap, and cancelled/short gestures do not switch tabs. Per user instruction, device testing is performed live by the user through Zapstore; no automated tests were run.


## 0.8.1 — October 10, 2026

Signed optimized release build passed. Verified version 0.8.1 (code 18) and the existing signing certificate. Device testing remains with the user through Zapstore; no automated tests were run.


## Infinite scrolling — October 10, 2026

Build verification only, per the live Zapstore iteration workflow. Near-end observation uses stable history boundaries, excludes thread views, and permits one history request at a time. Empty filtered tabs probe at most three pages without scrolling; unchanged boundaries are not repeatedly requested. History uses cached pages first, a 12-second relay deadline, independent footer loading state, bounded retained events, and serialized snapshots. Navigation/backgrounding cancels history subscriptions. Pull-to-refresh resets the history window.


## Transparent feed toolbar — October 10, 2026

The main toolbar uses the profile overlay treatment. Feed content extends behind it while a fixed 64 dp top content inset keeps the first note clear at rest. Showing or hiding the overlay does not change the feed viewport padding. Theme-aware circular surfaces maintain icon contrast. Build verification only; device testing is handled by the user through Zapstore.


## 0.8.2 — October 10, 2026

Signed optimized release build passed for automatic timeline pagination and the transparent main toolbar. Verified APK version 0.8.2 (code 19) and the existing signing certificate. No automated tests were run; the user tests live through Zapstore.


## Dynamic profile tabs — October 10, 2026

Debug build passed. Cached and newly discovered events determine the visible tabs; rare kinds have separate one-event relay probes, excluding the active tab's existing subscription. Posts and replies are classified from discovered kind-1 events. Availability is scoped to the viewed profile and remains stable during that visit. Tab indices and swipes use the filtered tab list, with automatic selection of an available tab when necessary. No automated tests were run; device testing is performed live by the user through Zapstore.


## 0.8.3 — October 10, 2026

Signed optimized release build passed for dynamic profile tabs. Verified APK version 0.8.3 (code 20) and the existing signing certificate. No automated tests were run.

## Launch and media fixes — October 10, 2026

Debug build passed for transparent splash icon resources, note-loading spinners, YouTube cards, animated GIF decoding, and the fullscreen fix. Confirmed in the Media3 1.11.1 source that setting fullscreen state invokes the same callback as the fullscreen button; the callback now honors its Boolean state instead of always exiting. YouTube parsing and bounded oEmbed fetches run off the UI thread; thumbnails use the shared image cache. GIF start/stop uses UI APIs while Coil decodes in the background. No automated tests or device verification were performed; the user tests live through Zapstore.

## 0.9.0 — October 10, 2026

Signed optimized release build passed, including the bottom-right fullscreen control and retention of partially visible videos. Verified version 0.9.0 (code 21) and the existing signing certificate. Manually checked a public YouTube oEmbed response for the expected title/channel fields. No automated tests were run; fullscreen transitions, GIF playback, and scrolling remain for live device verification through Zapstore.

## 0.9.1 — October 10, 2026

Debug and signed release builds passed for comma-separated block-height display (`987,654`, without a prefix). Explicit US integer formatting preserves the requested comma separators across device locales; formatting is remembered by height. Verified APK version 0.9.1 (code 22) and the existing signing certificate. No automated tests were run.

## Unreleased reading improvements — October 10, 2026

Debug builds passed for Open Graph cards, NIP-18 text-note reposts, fullscreen volume gestures, and three timestamp modes with relative age as default. Build verification only; no automated tests or device checks were run.

Open Graph fetches use a separate dispatcher, a two-request semaphore, same-key serialization, public-address DNS checks, HTTPS-only redirects, bounded HTML reads, and jsoup parsing on IO. Metadata uses the existing media disk budget and a bounded memory cache. The UI keeps original links clickable and displays a domain fallback if metadata is unavailable.

Reposts are included in live subscriptions, local snapshots, historical queries, and cache pruning. Enclosed originals are parsed and independently verified off-main; e/p tags constrain the target. Missing originals use the existing bounded embedded-event discovery. Original-note media keys include the repost ID to avoid collisions between repeated appearances. Tapping a resolved repost hands the original directly to thread view.

Volume gestures clear the player's mute, restore full player gain, and change only discrete Android media-volume steps; brightness and seek gestures remain independent. Relative timestamps start in compact mode, retain explicit US grouping for block height, and schedule their next update at the next visible unit boundary. Offscreen/background effects are canceled. All changes remain unreleased at the user's request.

## 0.10.0 — October 10, 2026

Signed optimized release build passed for rich Open Graph previews, text-note reposts, relative timestamps and timestamp cycling, fullscreen media-volume gestures, and the accumulated gallery/profile/expand-control refinements. Verified APK version 0.10.0 (code 23) and the existing signing certificate. No automated tests were run; the user validates on-device through Zapstore.

## Permanent block-height mappings — October 10, 2026

Debug build passed. Successful mappings are stored in an indexed SQLite table in app data without expiry or pruning; the 512-entry memory LRU now falls back to SQLite before any HTTP request. A single coroutine mutex serializes cache reads and requests so concurrent requests for the same timestamp reuse the first success. Writes complete on IO before the result is returned; there is no deferred snapshot writer. Initial database creation imports valid legacy JSON results regardless of their old expiry. Failed requests retain a short in-memory cooldown and remain retryable. Lookup/storage errors fall back to the existing timestamp display; no automated tests or device checks were run.

## Video preloading and portrait sizing — October 10, 2026

Debug build passed. The existing image-only prefetch plan now also schedules up to three video URLs within a twelve-note look-ahead window, including originals inside reposts and embedded quotes. Two interruptible IO jobs warm the first 4 MiB using the playback cache and a separate HTTP dispatcher; autoplay remains limited to one player. Active-video changes cancel overlapping speculative work, and fullscreen/background transitions cancel prefetch. Metadata extraction reads only a cached prefix; Media3 video-size callbacks correct dimensions as decoding begins. Removed the imeta aspect-ratio clamp and made inline video height respond to the actual ratio. No automated tests or device/network performance measurements were run.

## YouTube app routing — October 10, 2026

Debug build passed. Preview cards, inline note/profile links, and highlight links share the same external-link routing. YouTube domains first attempt an explicit NewPipe intent, then the official YouTube package, then Android's non-browser handler on API 30+, with normal URL handling as fallback. Each launch handles missing/disabled activities; package visibility queries are unnecessary for explicit activity launches. Original URLs and timestamp parameters are preserved. No automated tests or device app-handoff checks were run.

## 0.10.1 — October 10, 2026

Signed optimized release build passed for video preloading, portrait sizing, permanent block-height caching, external YouTube app routing, and the highlight/reaction UI refinements. Timestamp targets now reserve at least 64 × 48 dp, keeping compact labels right-aligned while accepting taps across the wider area. Verified APK version 0.10.1 (code 24) and the existing signing certificate. No automated tests or device checks were run; the user validates on-device through Zapstore.

## Reader navigation stack — October 10, 2026

Debug build passed. Replaced independent destination fields and unconditional goHome back handlers with per-visit stack entries shared by Android Back and the toolbar arrow. Entries own list state, profile tab, toolbar visibility, relay hints, and lightweight saved route state. SaveableStateHolder preserves expanded-note/profile UI per visit; popped entries are removed. Up to eight recent visits retain event references, and profile/hashtag page merging runs on Default to avoid list collapse while repository subscriptions switch. Sheets/gallery/fullscreen remain modal; login/logout clear history. No automated tests or on-device back-gesture checks were run. This change is not in the already published 0.10.1.

## Custom emoji reactions — October 10, 2026

Confirmed existing kind-7 reaction cards resolve NIP-25/NIP-30 emoji tags. Extended the shared Coil loader with its SVG decoder module, retaining existing GIF animation lifecycle controls. Exact shortcode matching and URL validation run on Default; malformed or unavailable images keep readable reaction text. Upcoming reaction emoji share existing bounded prefetch and media caches, with 128-pixel prefetch decoding rather than full-feed dimensions. Debug build passed. No automated tests or on-device visual checks were run.

## 0.11.0 — October 10, 2026

Debug and signed optimized release builds passed for Photos, Videos, and public Bookmarks tabs, observed-frequency ordering, the centered bio chevron, and Lightning-address copying. Verified APK version 0.11.0 (code 25) and the existing signing certificate. Photo rows and bookmark rows are prepared off-main and rendered lazily; visible keys drive bounded reference/media prefetch. Dedicated media kinds are accepted, cached, queried, paginated, and rendered through the existing reader; imeta supplies media URLs and MIME types, with Media3 HLS support. Latest list versions are selected before filtering public bookmarks. Tab ordering is stored as a list so ordering-only changes propagate. No automated tests or device checks were run; live device validation remains with the user through Zapstore.

## Gallery swiping and profile settings — October 10, 2026

The gallery already uses HorizontalPager; profile thumbnails previously supplied only the tapped post’s images. Grid preparation now produces the full loaded profile-photo URL sequence on Default, and row/column indices select the exact tapped occurrence, including duplicate URLs. Existing pager, zoom, and image actions are reused. Settings gear visibility requires a non-null signed-in public key matching the viewed profile. Debug build passed; no automated tests or device gesture checks were run.

## Embedded-note tap targets — October 11, 2026

Removed the full-width author-row profile click target from embedded cards. Only an explicitly labeled 48 dp avatar button opens the author; the name and unused header area inherit the card’s note-opening action. Existing content links and media controls retain their actions. Debug build passed. No automated tests or device touch checks were run.

## 0.11.1 — October 11, 2026

Signed optimized release build passed for gallery swiping across loaded profile photos, own-profile settings visibility, and embedded-note tap targets. Verified APK version 0.11.1 (code 26) and the existing signing certificate. No automated tests or device checks were run; the user validates live through Zapstore.

## Apps tab and website dark mode — October 11, 2026

Debug build passed. Read-only author-filtered kind-32267 queries to Zapstore returned Feather, ants, Boris, FishyFishy, and Offline Entropy Manual with app identifiers and icons. Checked Zapstore’s manifest/package and naddr route handling in its official source. Metadata parsing/link encoding run on Default; app icons use existing background fetching/cache limits. The catalog subscription is profile-scoped and pagination includes the catalog relay. No automated tests or Android app-handoff checks were run.

Website theme uses prefers-color-scheme CSS with the Android palette, explicit light/dark theme-color metadata, and matching foreground/button colors on both HTML pages. No JavaScript or theme setting added. Source and deployment checks only; no browser visual checks were run.

## Media-cache usage — October 11, 2026

Debug build passed. Settings displays used bytes against the chosen limit plus a compact usage bar, using the same binary unit convention as existing storage options. The total adds Coil disk-cache size (including thumbnails/preview metadata) and Media3 cache space, excluding memory and temporary exports. Counter reads run on IO under the cache-rebuild mutex; no directory walk is performed. Polling runs every two seconds only while settings is composed and lifecycle STARTED, and cancels on dismissal/background. Unknown usage is displayed as a dash rather than zero. No automated tests or device checks were run.

## Extensionless media links — October 11, 2026

Confirmed the Eurosky getBlob URL shown in the report returns HTTP 200 and image/jpeg without a filename extension. The existing bounded Open Graph request now recognizes image/video Content-Type headers before HTML parsing, closes the metadata response without reading the media body, and caches the MIME result with preview metadata. A bounded 256-entry observable MIME map updates note and embedded-card attachment parsing on Default; normal Coil/Media3 loading handles the media itself. No extra preflight request, file-extension assumption, or blanket classification of getBlob as an image. Debug build passed. No automated tests or device checks were run.

## 2026-10-11 — Articles, People, Projects (unreleased)

- Added kind 30023 articles, kinds 3/30000 public people lists, and kind 30617 repository announcements to bounded profile discovery, paging, cache pruning, and latest-version deduplication. Confirmed tag formats against NIP-23, NIP-51, and NIP-34.
- Articles use cached-event detail navigation, existing Markdown formatting on Dispatchers.Default, cover images, and up to 32 inline Markdown images. Article acceptance is bounded to 262,144 content characters. NIP-22 article comments and arbitrary naddr resolution remain outside this change.
- People rows are lazy, flattened off-main, capped at 5,000 displayed members, and resolve up to 32 visible/nearby profile references per update. Private content is never parsed. Retained people rows are separate from bookmarks for back navigation.
- Project metadata is parsed off-main; cards open a valid HTTP(S) web tag or the repository's Nostr address via njump. No new dependency or publishing permission.
- `./gradlew :app:assembleDebug`: passed after final changes. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — 0.12.0 release

- Version 0.12.0, versionCode 27. Includes Apps/Articles/People/Projects tabs, cache usage, extensionless media detection, and website dark mode.
- User clarified that Photos-tab galleries had no counter/arrows despite a large grid. Scoped gallery openings to the current Photos album at the host boundary, including single-image callers; full-list callers retain their exact index. Existing swipe/zoom handling retained. Live device confirmation is still needed.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests run.
- Signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.12.0 (27), 14,021,652 bytes.
- APK SHA-256: `6a0b88af6aa7db5fab7b5b3489d9d75cb83524e634939747aa094fb94bec85d9`.

## 2026-10-11 — Public article bookmark resolution (unreleased)

- Public `a` bookmark tags now produce kind/author/#d filters for supported addressable reader kinds. Coordinates are never sent as event IDs. Cached results are mapped back to the bookmark coordinate so existing preview cards can render article authors, titles, summaries, and images.
- Shared embedded-event budget remains 24. Visible/nearby address subscriptions use existing bounded relay routing, relay hints, and author relay-list discovery; they remain live for updated article versions. All database lookup and relay work uses the existing background repository scope.
- Loaded bookmark taps seed native detail navigation with the cached event. Unavailable and unsupported references retain external fallback. Article placeholders use an author label and a bounded loading indicator instead of raw coordinates. Prefetch maps bookmark row keys to resolved event IDs.
- Encrypted bookmark content is neither parsed nor decrypted. No signer permissions added.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — 0.12.1 release

- Version 0.12.1, versionCode 28; public address-based article bookmark resolution and native cached-article navigation.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.12.1 (28), 14,021,652 bytes.
- APK SHA-256: `baafe582de9e74c9e1ce92e72c0a4944a6a63f6e4aea14e074fb93340b09b6ca`.

## 2026-10-11 — Note header tap targets (unreleased)

- Regular note cards now use a 48 dp avatar button for author-profile navigation, matching embedded cards. Author names and header whitespace inherit the card's note action; timestamp cycling retains its separate target.
- Repost attribution profile navigation is limited to its 48 dp icon button. The attribution name/space opens the original note, including unresolved repost cards. Profile lists, mentions, and media retain their own explicit actions.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — Orphaned object markers (unreleased)

- The reported note `0000e42cb3ac5c90e5c37e7f010388598ecad9e8844a79f89bea174ec92230f5` contains literal U+FFFC before “only”; its tags provide no backing object.
- Remove only U+FFFC from display copies of note bodies (including embedded notes), article text/titles, highlight passages/context/comments, and profile biographies. Clean before tokenization/Markdown to preserve link offsets; leave original event content, IDs, signatures, and persistence unchanged.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — Engagement spacing (unreleased)

- Reply, heart, and zap groups now each reserve at least 56 dp width and 48 dp height, with 16 dp trailing padding and 6 dp between icon/count. Empty counts no longer collapse spacing; share remains aligned at the end.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — Feather scroll-to-top (unreleased)

- The feed's Feather icon is now a 48 dp accessible button that scrolls its existing list to index 0. Short distances animate; positions beyond 20 items jump immediately to avoid traversing/loading intermediate media. No refresh or navigation reset is triggered.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — 0.13.0 release

- Version 0.13.0, versionCode 29; Feather tap-to-top, note header tap targets, engagement spacing, and orphaned OBJ marker display cleanup.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.13.0 (29), 14,021,656 bytes.
- APK SHA-256: `eb7f34818d768990e5340edb13a133406f5904b156a384b47e5a847336d7e511`.

## 2026-10-11 — Open source note from gallery (unreleased)

- Gallery openings now carry optional source events. Photos-tab albums build parallel per-image source lists alongside URLs off-main, preserving the exact source for duplicate image URLs at different grid indices. Regular/embedded note images and article cover/inline images pass their source event.
- A conversation icon in the gallery toolbar opens the current pager page's source note and dismisses the gallery. Native detail navigation is seeded from the retained event; source IDs survive gallery state restoration without serializing events. Avatar/banner galleries without a source note omit the action.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — Full article text (unreleased)

- Traced bookmark navigation through the cached-event handoff: detail views already set compact=false and read the full event content. The loss happened inside the Markdown library's String convenience overload, which selects only the first TEXT/PARAGRAPH node (confirmed against v0.35.0 AnnotatedStringKtx.kt).
- Replaced that overload with a shared background document formatter that visits every top-level block, formats individual paragraphs and headings, and retains unsupported blocks as plain source text. Paragraph breaks, inline formatting/links, existing image ordering, and full detail content remain visible. Preview cards alone retain their excerpt limit.
- Applied the same correction to highlight passages, context, and comments. No dependencies added; formatting remains on Dispatchers.Default.
- `./gradlew :app:assembleDebug`: passed after supplying the annotator required by the library's settings constructor. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — Orange-heart reactions (unreleased)

- Explicit heart taps send only kind 7 with content 🧡 and target e/p/k tags (plus a for addressable targets). NIP-25/NIP-55 formats checked against the official specifications. Login still requests only public identity; signing is requested on action through Quartz's external-signer background/foreground bridge. No encryption/decryption or general publishing UI.
- Signer operations, JSON, signature verification, cache/receipt persistence, and relay publishing run on IO. Only launcher/UI state work runs on main. New signer responses must match the requested pubkey, time, tags, kind, content, and valid signature before publishing.
- One pending reaction at a time; disable repeated sends. Cancel on account changes/logout. Persist the signed event before publication in an app-data store bounded to 256 records; manual retries reuse the same ID. Show sent state only after relay OK or an existing verified reaction. No automatic offline publishing queue.
- Publication uses up to four routes, prioritizing account write relays and recipient read relays inside the existing pool, with automatically selected fallback relays. Foreground resumes before publication after signer return. Gallery/list navigation remains intact.
- Updated project rules, design, README, and staged privacy text to reflect the user-authorized reaction capability. Block/mute lists are still not implemented.
- `./gradlew :app:assembleDebug`: passed after final changes. `git diff --check`: passed. No automated tests, real reactions, or signer/device interaction were performed from this development session; live signer/relay confirmation remains for device testing. No release published.

## 2026-10-11 — 0.14.0 release

- Version 0.14.0, versionCode 30; orange-heart reactions, gallery-to-note navigation, and full article/highlight text.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device/signer checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.14.0 (30), 14,038,040 bytes.
- APK SHA-256: `108fb941e859246976851c259b37887478c7823018f908700ccd1d918dcf5c5f`.

## 2026-10-11 — Account mute/block filtering (unreleased)

- Checked official NIP-51: kind 10000 p tags and private JSON tags (NIP-44; legacy NIP-04), with deprecated kind 30000/d=mute fallback. A current empty list supersedes legacy entries. Only the connected account's signed lists are used; public lists authored by other people never become local blocks.
- Use the existing bounded outbox account subscription with separate per-kind filters so metadata/contact events cannot consume the mute-list limit. Keep raw timeline windows for pagination, with filtering at reading-data selection, cached/retained note display, thread handoff, repost/reaction targets, embedded/bookmarked references, gallery sources, and media prefetch. Remove muted authors from feed routing and reply/reaction counts. Legacy mute sets no longer appear as People lists.
- Decrypt only the account's own private mute list on IO through its existing Android signer; serialize against reaction signing. Signature validation precedes storage, account/event IDs are rechecked after signer return, cancellation handles account changes, and a failed/denied request is not repeatedly relaunched. Pull-to-refresh permits a manual retry. Existing cached private mutes remain active while a replacement awaits approval.
- Cache at most one decrypted list per account, at most eight accounts and 5,000 tags per list, in private app data with backup disabled. Public entries and successfully cached private entries apply offline before the initial note snapshot. No list publishing, list editing, bookmark/people decryption, or configuration added.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or signer/device interaction; live decryption and account-list updates remain for device testing. No release published.

## 2026-10-11 — Tappable reader status (unreleased)

- Muted/blocked content shows a crossed-out account icon; empty feeds/tabs show a small native vector tumbleweed; unavailable posts retain an info icon; reported loading errors show a warning triangle in the theme error color. All have accessible 48 dp tap targets.
- Tapping captures a selectable, scrollable status report with the existing error message (if any), app version, view/tab, target ID, account connection status, and connected relay count. No inferred exception is presented as an actual error. Native copy/close icon buttons; no network or disk work is triggered. The dialog lives outside the lazy list so incoming notes cannot dismiss it while reading/copying; Android Back dismisses it.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device checks; no release published.

## 2026-10-11 — Recent/frequent reaction picker (unreleased)

- Heart combined-click keeps tap as 🧡 and exposes a five-item native long-press menu, with 48 dp choices, accessibility labels, sent checks, and disabled duplicates. Freeze displayed suggestions while the menu is open. Different emoji may be sent to the same note; only one signer request may be pending.
- Query up to 200 own kind-7 events through the existing account outbox routes. Rank cached history off-main: most recent first, then frequency weighted by a 30-day decay, with five default emoji filling gaps. Include NIP-30 custom emoji with exact shortcode/image tags; checked the official NIP-30 format. No additional dependencies.
- Store signed retry IDs per account/target/emoji identity (custom URL included), keep the existing 256-record bound, and read/migrate old orange-heart records without resigning retries. Suggestions use accepted records and verified relay history, not rejected or merely pending local sends. Account changes reset UI history. All JSON, ranking, signing, and persistence stay off-main.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or actual signer/device interaction.

## 2026-10-11 — 0.15.0 release

- Version 0.15.0, versionCode 31; recent/frequent emoji picker, mute/block filtering, and distinct tappable status icons with copyable details.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device/signer checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.15.0 (31), 14,075,560 bytes.
- APK SHA-256: `96274ca62dc7ec71568b1556718ceb2213dee73dcb4a6a4fb6d3881585838499`.

## 2026-10-11 — Full-screen home feed pages (unreleased)

- Native lazy HorizontalPager replaces the single home reader, without a tab bar or visible page labels. Posts and Replies are always first; other available existing categories (photos, videos, highlights, articles, reactions, bookmarks, people, projects, apps) sort by bounded observed activity frequency. Reordering waits for gestures to settle and anchors the current category by identity. Discovered pages remain available during the visit.
- Each page retains its own list/toolbar/content state, with category, page keys and scroll offsets in the navigation saver. Back from notes/profiles returns to the same feed. Home-page pagination uses its category kinds and independent time ceiling; a current full window does not merge discarded head items back in. Photos grids/gallery preserve source notes across up to 600 images.
- Only the last settled page registers the signer launcher and drives reading IDs, pagination, or playback. Cancelling a swipe keeps that page active. Neighbor pages render cached previews/images; MediaHost now gates network activity by reading state as well as lifecycle/gallery visibility. No second video player for an inactive page. Profile swipe behavior remains separate.
- Bounded category discovery shares existing outbox routing and following scope; guest browsing uses public activity and app discovery can use the existing Zapstore catalog relay. Category sampling/formatting runs on IO, cached for five seconds and invalidated for account/mute changes or refresh. Switching feed subscriptions is scheduled off-main.
- `./gradlew :app:assembleDebug`: passed. `git diff --check`: passed. No automated tests or device gesture checks; user validates live through Zapstore.

## 2026-10-11 — 0.16.0 release

- Version 0.16.0, versionCode 32; full-screen home feed pages with dynamic category ordering, per-page scroll state, and active-page media/subscription ownership.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.16.0 (32), 14,075,560 bytes.
- APK SHA-256: `c35dc1b717ca450f6b1a0ee7ee3f4d3be462d41f1d4c63003e3c123532ab2ba7`.

## 2026-10-11 — Quiet empty states

- Removed the tumbleweed resource. Background note preparation carries an input token so pending work is reflected immediately. Empty pages show a spinner during loading/preparation, including inactive home pages, with a 1.5-second settling interval after loading. Existing notes are never delayed. Settled empty feeds are quiet; hidden/unavailable/error diagnostics remain.
- GitHub repository homepage updated and read back as https://feather.dergigi.com. README already points there.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests or device checks.

## 2026-10-11 — 0.16.1 release

- Version 0.16.1, versionCode 33; quiet empty feeds and loading/preparation spinners.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.16.1 (33), 14,074,776 bytes.
- APK SHA-256: `8c9b0302e2f75573b97bca746339e3e4346e6912bc49029cf2ec22de9e6b5bbe`.

## 2026-10-11 — Shared home toolbar

- Hoisted the home toolbar outside HorizontalPager. Feather tap-to-top targets the settled feed; account opens the signed-in profile or the active reader’s guest sheet. Toolbar avatar uses the existing shared image resources with lifecycle-aware network access and no extra player.
- Center selector uses existing profile-category icons: current emphasized, adjacent categories at reduced opacity with 48 dp tap targets. Missing neighbors reserve space so the current icon remains centered. Profile tabs share the icon renderer.
- Vertical toolbar visibility is shared across feed pages; list positions and active-page media ownership remain independent.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests or device interaction.

## 2026-10-11 — 0.17.0 release

- Version 0.17.0, versionCode 34; stationary home toolbar with category hints, aligned avatars, and compact repost attribution.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.17.0 (34), 14,091,160 bytes.
- APK SHA-256: `8cb4eabe68faac3fe0c8b99d92bedfaa4e7daa947da83ffd7a391608488a1bdd`.

## 2026-10-11 — 0.17.1 release

- Version 0.17.1, versionCode 35; theme-aware toolbar background and five progressively faded category slots.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.17.1 (35), 14,091,160 bytes.
- APK SHA-256: `6b2e537867c6d13c776f7f08d7afb03f3a511cb01723f4d7fa2615eda61255a5`.

## 2026-10-11 — Local crash details and feed-selector pill

- Install the uncaught Java/Kotlin exception handler at Application startup. Keep one atomic private report, bounded to 32,768 characters plus a truncation marker; preserve Android’s previous exception handler and termination. Include version/code, time, Android API, manufacturer/model, thread and throwable causes/stack. No telemetry or automatic uploads. Native crashes, ANRs, abrupt OS kills, and failures that prevent report persistence are not guaranteed to be captured.
- Read and acknowledge the previous report on IO. Show the existing selectable, scrollable native details dialog at the app root, with one-tap copy and explicit dismissal; copying retains the report. Check report identity before deleting. The fatal-handler write is intentionally synchronous so termination cannot discard it.
- Restore the transparent home toolbar and constrain the 82%-opaque theme surface to the rounded five-icon selector. The strip uses continuous pager position for translation, scale, and fading, with stable category keys and bounded entering neighbors. Frame-by-frame offset/opacity reads stay in layout/drawing, avoiding full-reader recomposition. Feather/account button surfaces remain.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests, intentional crashes, or device checks.

## 2026-10-11 — 0.18.0 release

- Version 0.18.0, versionCode 36; local crash-report dialog and gesture-driven feed-selector pill.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.18.0 (36), 14,091,160 bytes.
- APK SHA-256: `0c8d7dee0d0c0ff234218e787deed34330c39b97963ce5ebbd665652271593de`.
- Matching R8 mapping archived locally at `output/releases/feather-0.18.0-mapping.txt` for crash retracing.

## 2026-10-11 — Full emoji picker

- Six quick suggestions, including the latest accepted choice, retain recent/frequent ranking and custom NIP-30 support. Add 🙏 to the fallback set; quick menu wraps on narrow screens with 48 dp buttons.
- Plus icon opens AndroidX emoji2-emojipicker 1.7.0 in a native bottom sheet. Instantiate on demand; the library loads its bundled catalog on IO. Use existing reaction suggestions for recent Unicode choices; no additional recent-selection writes. Pending requests and already-sent choices are checked against current state.
- Accept Unicode emoji arrows in addition to the existing sequence/variant ranges. Full-picker selection goes through the existing signer and public-reaction validation.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests or signer/device checks.

## 2026-10-11 — 0.19.0 release

- Version 0.19.0, versionCode 37; six quick reactions and full native emoji picker.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.19.0 (37), 14,554,649 bytes.
- APK SHA-256: `4ea9d72ec8ae241362db2f68db5525e91d6df24d1d8cb875ba29afd25a4cd155`.
- Matching R8 mapping archived locally at `output/releases/feather-0.19.0-mapping.txt`.

## 2026-10-11 — Offline emoji search

- Search field in the full picker filters 3,944 fully qualified Unicode 17 emoji using bundled CLDR 48 English names/keywords. Multiple query words match keyword prefixes; exact names come first, and base emoji precede tone variants.
- Load and parse once on IO under a mutex; debounce queries and filter on Default. Show at most 200 lazy-grid results, with loading/empty/error states. Clearing search restores native category/variant browsing. Existing signer and duplicate guards remain shared.
- Reproducible index generator and Unicode license included; no runtime network or new dependency.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests or device checks. Not released yet.

## 2026-10-11 — 0.20.0 release

- Version 0.20.0, versionCode 38; offline emoji search.
- `./gradlew :app:assembleRelease`: passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.20.0 (38), 14,736,074 bytes.
- APK SHA-256: `92b8d68d3253a3fe1d52bd83f3c58176cf01425b4a2eec55481f1d050f6c3d39`.
- Matching R8 mapping archived locally at `output/releases/feather-0.20.0-mapping.txt`.

## 2026-10-11 — Reply context

- Inspected live event `4bdd49a5e94fea471771ea66c94873c4ce7b5d65a46fec12f3e6db29a9ce46d2`: kind 1, NIP-10 root tag pointing to `0000064b86e0e4a75c5b362e8448e804637f74af72c626fad587f7bae81d71c9`. Existing reply classification was correct; the renderer lacked parent context.
- Add a reply arrow and compact parent-author/text preview with a separate parent-note tap target. Immediate ID fallback, cached/background resolution within the existing 24-event embed budget, relay hints preserved, muted parent content hidden. Parent references are kept separate from quote cards.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests or device checks. Not released yet.

## 2026-10-11 — 0.21.0 release

- Version 0.21.0, versionCode 39; reposts category, reply context, smaller category-picker emojis. Reposts use existing rendering, paging, mute filtering, and dynamic category discovery; Posts still includes reposts.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.21.0 (39), 14,736,610 bytes.
- APK SHA-256: `68ef9ef17f32a2bd5a0ec728d0d45bf5bb20dc84b833fc9859b5adda2f2f4f46`.
- Matching R8 mapping archived locally at `output/releases/feather-0.21.0-mapping.txt`. Website unchanged.

## 2026-10-11 — 0.21.1 release

- Version 0.21.1, versionCode 40; temporary pill-only visibility during horizontal feed swipes.
- Signed release build passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.21.1 (40), 14,736,610 bytes.
- APK SHA-256: `6dfe67d5f80fb797b8151988459a46540f51a4cb701517ba9588914e8f2e1572`.
- Matching R8 mapping archived locally at `output/releases/feather-0.21.1-mapping.txt`. Website unchanged.

## 2026-10-11 — 0.22.0 release

- Version 0.22.0, versionCode 41; public custom emoji collection/pack loading, shortcode search, and latest own reaction display. Collection parsing and storage reads run off-main; 16 packs / 1,024 combined entries maximum. No list decryption or collection editing.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device/signer interaction checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.22.0 (41), 14,752,994 bytes.
- APK SHA-256: `775aa532adce0fae7aa6b2c0f365f12c50b035b9c178b38efeec60f625a130cf`.
- Matching R8 mapping archived locally at `output/releases/feather-0.22.0-mapping.txt`. Website unchanged.

## 2026-10-11 — 0.22.1 release

- Version 0.22.1, versionCode 42; minimum profile-tab content height while loading or displaying short content.
- Signed release build passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.22.1 (42), 14,752,994 bytes.
- APK SHA-256: `1f0f3bdf7157c5d33c05eca3bfacd14a8ac43ce7d1c8869094e524da647819c3`.
- Matching R8 mapping archived locally at `output/releases/feather-0.22.1-mapping.txt`. Website unchanged.

## 2026-10-11 — 0.22.2 release

- Version 0.22.2, versionCode 43; larger toolbar avatar without surrounding circular background.
- Signed release build passed, including release lint and R8. No automated tests or device checks.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.22.2 (43), 14,752,994 bytes.
- APK SHA-256: `099272a60eb1f297a783e8060f9711346586b56d991bad3194dac6eeaa9a21a8`.
- Matching R8 mapping archived locally at `output/releases/feather-0.22.2-mapping.txt`. Website unchanged.

## 2026-10-11 — NWC zaps and emoji-pack browsing

- Wallet settings use a masked, non-saveable connection input; validate `get_info` payment permission before persisting. Wrap credentials and the payment journal with Android Keystore AES-GCM; backup remains disabled. NWC uses the connection key, not the user's identity secret.
- A bolt tap sends 21 sats after external signing of kind 9734. Check exact invoice amount, Bech32 checksum, expiry, description hash, and payment-preimage hash. One payment/signer action at a time. Persist pending invoices before sending; subsequent attempts check the same invoice. No automatic payment retry or synthetic network zap count.
- Kind-30030 authored emoji packs appear as a dynamic category with lazy horizontal image browsing and gallery access. No pack editing or public-list writes.
- `./gradlew :app:assembleDebug` and `git diff --check`: passed. No automated tests or live-wallet payments; end-to-end wallet/signer behavior requires device validation.

## 2026-10-11 — 0.23.0 release

- Version 0.23.0, versionCode 44; NWC wallet, explicit 21-sat zaps, and authored emoji-pack browsing.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live wallet payments; wallet/signer behavior requires device validation.
- APK signature verified against existing certificate SHA-256 `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK metadata: org.dergigi.feather, 0.23.0 (44), 14,786,878 bytes.
- APK SHA-256: `f7f77507271ae4a4ba6bf50308a3c8ca8272d4bb81e8c654f3ae00158d452f5f`.
- Matching R8 mapping archived locally at `output/releases/feather-0.23.0-mapping.txt`. Website unchanged.

## 2026-10-11 — 0.23.1 release

- Version 0.23.1 (45), source commit `f74f3ba7f1a376b425b0f25221a9bbf5da31a8ea` embedded in BuildConfig. Settings layout, wallet controls, GitHub links, and 1 GB default cache.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,787,766 bytes; SHA-256 `9697f2ce6afe5b591bd9da4026c831b5542a3bfeefff3033faf0831d2beffb9e`. Matching mapping archived locally. Website unchanged.

## 2026-10-11 — 0.23.2 release

- Version 0.23.2 (46), source commit `94af2252947fff9d8147d54f571c109a30e6a5a8` embedded in BuildConfig. Wallet name/balance and separate zap progress/confirmation.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live wallet payments.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,787,766 bytes; SHA-256 `a53abb0e52cbbe24ae983517c749faae58f93e745821d1c964996f120c24f435`. Matching mapping archived locally. Website unchanged.

## 2026-10-11 — 0.23.3 release

- Version 0.23.3 (47), source commit `60ef4d171544830dddc1fd168e3480a908c6dadd` embedded in BuildConfig. Stable centered note-action slots and right-aligned wallet balance.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,787,766 bytes; SHA-256 `0a6a28833528de7c9c222b827ecefc6aa7bd6727d21b0a4cf9c00fd8c42c960e`. Matching mapping archived locally. Website unchanged.

## 2026-10-11 — 0.23.4 release

- Version 0.23.4 (48), source commit `3747360791104cf92f8b07f4c11f3cb1dda08bbd` embedded in BuildConfig. Cross-app own-zap indicators and yellow-only highlight styling.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,787,766 bytes; SHA-256 `1932487fa387d49e5f5b7ab0c4e09449380d0c3a283e95787c592a537278333f`. Matching mapping archived locally. Website unchanged.

## 2026-10-11 — Zap activity tab

- Added kind-9735 sent/received activity via p/P participant filters in profile/home discovery, active subscriptions, local snapshots, and paging. Preserves dynamic ordering after Posts/Replies.
- Cards follow ants-android’s sender/amount/recipient layout, with public comments and linked-note/article previews. Request signature/recipient/target binding parsed and cached on background dispatchers. Invoice amounts are previews; these cards do not create payment confirmations.
- Participant profiles are resolved through existing metadata subscriptions; muted participants and linked authors stay hidden. Anonymous senders have no profile link.
- Debug build and diff checks passed; no automated tests or on-device interaction checks.

## 2026-10-11 — Nutzap activity

- Kind 9321 joins the Zap activity tab, including authored (sent) and p-tagged (received) events in discovery, cached snapshots, subscriptions, and pagination. Adds bounded cache retention for nutzaps.
- Uses Ants’ purple `#C084FC` for nutzap bolts and amounts; regular Lightning zaps remain gold. Public proof amounts are summed with bounded parsing on background dispatchers and retain their declared unit (default sat). No token redemption, mint interaction, or synthetic confirmed-payment state.
- Debug build and diff check passed. No automated tests or device checks.

## 2026-10-11 — Independent reaction and zap actions

- Pending sets are per note and action type; only the matching in-flight action is disabled. Explicit taps queue immediately (bounded to 32 combined actions). Existing per-kind publication/payment serialization remains, but reactions and payments can progress independently after signing.
- A shared coroutine mutex coordinates Android signer calls and private-mute decryption. Cancelling a signer request targets that request’s job, not an unrelated payment; logout/account changes cancel all queued work. Uncertain payment journals and duplicate-tap guards remain intact.
- Debug build and diff check passed. No automated tests, device signer interactions, or live payments performed.

## 2026-10-11 — 0.24.0 release

- Version 0.24.0 (49), source commit `d0ffb2828ba270fafdd29a7970b7d350a7e9b14f` embedded in BuildConfig. Zap/nutzap activity and independent reaction/zap controls.
- Debug and signed release builds passed, including release lint and R8. No automated tests, device checks, or live payments.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,787,766 bytes; SHA-256 `65b5b1765e3ec5ac09144344d49e645f464ab636b10941a71a2a4873ac61e982`. Matching mapping archived locally. Website unchanged.

## 2026-10-11 — Remove own reactions

- Tap the displayed reaction or a selected picker emoji to request kind-5 deletion of matching own kind-7 events; validate exact signed tags and author. Uses the same signer coordination and per-note pending state as adding reactions.
- Persist signed deletion requests before publishing for explicit retry; apply accepted author/event tombstones to cached reaction choices, counts, and retained reaction cards. Re-adding creates a fresh event rather than reusing a deleted signed reaction. History is bounded to 512 deletion records, with up to 256 event IDs each.
- Receive the connected account’s kind-5 requests to honor deletions from other apps. Remote relay retention is outside Feather’s control.
- Debug build and diff checks passed; no automated tests or live signer/device checks.

## Zap error details (unreleased)

- Zap failures identify the active step and retain validation, HTTP, LNURL reason, and NWC code/message details. Relay connection failures and subscription/request rejections are retained for timeout diagnostics.
- External error text is bounded and redacts connection URIs, URL paths/queries, private keys, long hex values, and invoices.
- Uncertain payments retain their journal entry and tell the user to check status; no automatic payment retry. Coroutine cancellation still propagates.
- Debug build passed. No automated tests or live payments performed.

## Account-bound NWC (unreleased)

- Wallet reads/saves require an explicit signer public key matching both the active wallet account and persisted connection owner. Keystore-encrypted credentials are removed on logout/account changes; unowned legacy connections are discarded.
- Account transitions cancel and join wallet setup before switching ownership. Delayed wallet details cannot update another account's UI. Wallet setup is hidden while logged out.
- Payment journal remains intact to protect uncertain payments after reconnection. Disk and Keystore work stays on IO.
- Debug build passed; no automated tests or live payments performed. Device checks: reconnect once, restart while signed in, logout, then sign in with a different account.

## 2026-10-11 — 0.25.0 release

- Version 0.25.0 (50), source commit `4a0aefe381ed04672e0e4d8890c409681ab82e0b`.
- Signed release build passed, including release lint and R8. No automated tests or live payments.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,804,150 bytes; SHA-256 `ef95568d3dbedf81ed9010d5b625e624a9f7c8fe8e459dc27427d6f0cc89b350`. Mapping archived locally. Website unchanged.

## Sensitive content default (unreleased)

- Persistent opt-in defaults to false. NIP-36 content-warning tags (including reasonless tags), content-warning labels, NSFW labels/tags, and nsfw/porn/pornography hashtags are hidden by the shared content filter.
- Feed/profile/detail rendering, embedded references, bookmark targets, media prefetch inputs, and cached photo-grid sources use the filter. Galleries close if a known source becomes hidden. Disabling the setting immediately guards retained content while background preparation updates.
- Preference writes and feed discovery run on IO; existing media preparation remains on background dispatchers. No classification of untagged content.
- Debug build passed. No automated tests, release, or website changes. Device checks: tagged photo/video, repost and bookmark of tagged content, toggle on/off, then restart.

## 2026-10-11 — 0.26.0 release

- Version 0.26.0 (51), source commit `53e22f4fbd650e9c4839db3af5657f8f51395619`. Sensitive content hidden by default with persistent opt-in.
- Signed release build passed, including release lint and R8. No automated tests.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,804,154 bytes; SHA-256 `77162238341c86578048381b110bce8e157bf20ae49e1f8b233b8ac1d2fd57ca`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.26.1 release

- Version 0.26.1 (52), source commit `4355de42d95dedd236e9a5831a724b4165468337`. Login/settings icon refinements.
- Debug and signed release builds passed, including release lint and R8. No automated tests.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,805,450 bytes; SHA-256 `efe8acf6e7fc69492810cfb26f906ace8abcb1720e6bfd3cc392ce33c91b0931`. Mapping archived locally. Website unchanged.

## Missing profile metadata (unreleased)

- Missing metadata now activates two rotating healthy seed fallbacks, within the existing 12-relay routing cap, and prioritizes up to two known write relays. Rotation runs every 30 seconds only in the foreground.
- Up to eight viewed/account/missing authors get individual kind-0 filters; remaining targets stay batched. The local metadata query also requests one latest event per author. Route selection updates when missing metadata arrives.
- Read-only relay queries for npub1xq09qwnnzpq073uchkhxr78kdhyyeexhgsdvv7g4r8uswvq0dcysldjz26 returned no kind-0/10002 events from responding bootstrap relays during investigation; some relays were unavailable. This does not prove that profile metadata exists nowhere, and the specific profile remains unverified on device.
- Debug build passed. No automated tests or website changes.

## 2026-10-11 — 0.26.2 release

- Version 0.26.2 (53), source commit `584d32b1a766e553a92fe78dd33b2081a19dd68f`. Missing profile metadata fallback and compact zap activity cards.
- Debug and signed release builds passed, including release lint and R8. No automated tests. Reported profile metadata remains unverified on device.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,805,450 bytes; SHA-256 `49d63311e561abf5ef8c66744c6119d859723d4507e7694adcf1fd746ee208a1`. Mapping archived locally. Website unchanged.

## NWC QR scanning (unreleased)

- Embedded ZXing Android 4.3.0 QR-only camera view; no Play Services dependency. Runtime permission requested only from the scan action. Optional camera hardware declarations retain support for devices without cameras.
- Camera/decoding work runs on library worker threads; parsing runs on Dispatchers.Default. Preview pauses on lifecycle pause and disposal. Closing/cancelling does not connect a wallet.
- Valid NWC scans fill the masked in-memory field and require the existing explicit confirm action, now right-aligned. Invalid QR/permission/camera failures show concise errors without the payload. No image capture, scan logging, saved-state credentials, or automatic connection. Scanner window is secure.
- Debug build and merged-manifest inspection passed. No automated tests or device-camera checks. Device checks: allow/deny permission, scan valid/invalid QR, cancel/back, background/resume, rotate, and confirm via the existing account-bound wallet flow.

## 2026-10-11 — 0.27.0 release

- Version 0.27.0 (54), source commit `ab04b3df788bfc046aceb3cc1c24acd89dc91f6d`. NWC QR scanning, inline connection spinner, and settings/account sheet refinements.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device camera checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,948,315 bytes; SHA-256 `dadeab3fa0a3d5657989c67f7de3e023e76bfe3563ee58675ddef4176049150b`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.27.1 release

- Version 0.27.1 (55), source commit `ec86303a30520a0cf0c8588f135ed4b2b0ce3b12`. Reverts profile recovery from 0.26.2. FeatherRepository.kt exactly matches the parent of commit 4f1c2e6; no experimental replacement is included.
- Signed release build passed, including release lint and R8. No automated tests.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,948,315 bytes; SHA-256 `4bcd0cef5179f829099ef903aca0c85f67712d15630216f7d4d9236e2e64c030`. Mapping archived locally. Website unchanged.

## Follow-list loading after login (unreleased)

- Separate kind-3 contact subscription (one author, one latest event per relay) from kind-0 metadata so metadata cannot consume the shared response limit. Reuse selected account routes, existing base relays, and the metadata relay when already in the pool. No added relay connections or routing-priority changes.
- Existing cached contact query and following-change handling rebuild the feed when contacts arrive. Empty signed lists remain valid; logout removes the contact subscription.
- Debug build and diff check passed. No automated tests or live device login checks.

## 2026-10-11 — 0.27.2 release

- Version 0.27.2 (56), source commit `88008ba26ee7d46b651703eaf7fcd7a07133855a`. Dedicated account follow-list requests and consistent round control/avatar sizing.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device login checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,948,311 bytes; SHA-256 `deb1762f0c1a26155aefb0fa9404e406b0e05b5e79b1c6fb5312e5872cae6bf2`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.28.0 release

- Version 0.28.0 (57), source commit `e0b0ce8b343cbd2a5d32088953dbe6056b798eef`. Main feed controls moved to the bottom, with navigation insets and feed/snackbar clearance; profile and note toolbars stay at the top.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device layout checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,948,315 bytes; SHA-256 `dae9ea95ecd0ad1424b221943ad0066cbfbdaf1c4c1967a7de1e574362ab099b`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.28.1 release

- Version 0.28.1 (58), source commit `1d2e27dbc6cdfdb794d1be5ac95613c16a4c82a1`. Bottom feed selector, smaller bordered top-right avatar, and floating feather scroll-to-top action with content/snackbar clearance.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device layout checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,948,315 bytes; SHA-256 `646b19f95c43557b7a0eaea8196a522dbb0efde2015eeaae1876930d4f9771e3`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.28.2 release

- Version 0.28.2 (59), source commit `cecb37748d3ece1bb7b7c747efee8c34b3e51276`. Sliding feed controls, wallet-colored avatar border, and repositioned translucent feather action.
- Debug and signed release builds passed, including release lint and R8. No automated tests or device animation checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,948,311 bytes; SHA-256 `f9f26319917dae8c3d33ed37e5817c0dea433554c7c47652ca0d0f57d98e6707`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.29.0 release

- Version 0.29.0 (60), source commit `4c89b118935ea48b4fbcfa8d90c230b667ea47cb`. NIP-22 comments, preferred tab ordering, hidden status bar for photos, fading profile banners, and avatar scale/fade animation.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,964,699 bytes; SHA-256 `a003712bdc3afff5c933c6dd3142625314d77e8056fc5f3b40084ee6d14ce92e`. Mapping archived locally. Website unchanged.

## Immersive gallery and note actions (unreleased)

- Images use the full viewport with ContentScale.Fit, no fixed toolbar/footer padding, and hidden status/navigation bars. Controls respect safe insets while images can extend behind short-edge cutouts.
- Single tap toggles floating controls; double tap remains zoom. Controls fade/slide away after an accessibility-adjusted three seconds and remain while menus, emoji pickers, saves/shares, or note actions are pending.
- Gallery uses the same reaction and zap controls as notes. Source events follow the selected image; one gallery source temporarily drives the existing bounded engagement pipeline. Login/wallet setup temporarily covers the gallery. Custom emoji images reuse the shared media loader.
- Debug build and diff check passed. No automated tests or device gesture/orientation/payment checks.

## 2026-10-11 — 0.30.0 release

- Version 0.30.0 (61), source commit `a70ab35af296fb2ace25dc85cd206e322b58aad6`. Immersive gallery, gallery reactions/zaps, and global persisted timestamp format.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,964,695 bytes; SHA-256 `a79e9ea87456d6bd1735d53f6beba519e69167771ab5fde1856405eefbc65746`. Mapping archived locally. Website unchanged.

## Malformed relay metadata crash (unreleased)

- 0.29.0 R8 mapping identifies `sb1.d:240` as FeatherRepository.discoverRelays and `l34.a:17` as Quartz relay normalization. The supplied joined relay address passed the java.net.URI check but failed Quartz normalization during subscription setup.
- Protocol.relay now requires Quartz acceptance inside its existing failure-to-null boundary, with a 2,048-character input bound. Routing candidates are revalidated before taking the existing eight discovered slots. No routing priority, connection cap, or discovery scheduling changes.
- Debug build and diff check passed. No automated tests or live device reproduction.

## 2026-10-11 — 0.30.1 release

- Version 0.30.1 (62), source commit `98a65f4946616bc5a120f254ba91319a48adf897`. Reject malformed relay hints, compact article list, and profile identity overlapping the banner fade.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,964,695 bytes; SHA-256 `b0b7a12f114a64afd2ff693f6c56bc1dcd937e3db61b4d1eabe84ef658a7e589`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.31.0 release

- Version 0.31.0 (63), source commit `e8d64e55284dbd0d87b55a0df996f07b761900ce`. NIP-05 profile identity, article avatars/reading time, gallery floating actions and profile-image pairing, tappable error messages, and active-tab scroll-to-top.
- Signed release build passed, including release lint and R8. No automated tests or live device checks.
- Supplied seven-attachment note exceeded the previous four-item display cap; ordinary notes now render up to 32 attachments.
- Inspected the supplied zap recipient’s Primal LNURL endpoint with an already public signed request, without making a payment. Invoice description hash did not match, including with the LNURL callback parameter. This provider issue remains unresolved; request context and diagnostics improved without weakening validation.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,962,591 bytes; SHA-256 `de13153abfc961264758b20ad2607004a88e282ddf72a5baea9205b9f4c3d65f`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.32.0 release

- Version 0.32.0 (64), source commit `84eef8133e21f660d5587be3f7bf3c2601ee0fb6`. Signed-in notifications, disabled search placeholder, profile homepage links, compact article dates/durations, and cross-client reaction sync/removal.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Notifications: bounded account-tagged requests only on the selected Notifications page, existing inbox/base relays, per-account display guard, cache-first snapshots, visible actor/target resolution, and mute/content filtering. Wisp compact notification rows reviewed as design reference; no source copied.
- NIP-25 reviewed: empty and + incoming reactions normalize to the same like choice; signed events remain unchanged for NIP-09 deletion. Dedicated visible-target own-reaction queries avoid broad engagement and recent-history limits. Publishing remains explicit through the signer.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,979,567 bytes; SHA-256 `4a959345b431518327e92bf7bddd5255bf5d0e9c1492a306325abf5497cd2c22`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.33.0 release

- Version 0.33.0 (65), source commit `95d69397a92977c0cdfabdf72ef9bddaf3bb9cdd`. Minimal Vertex profile search, notification foreground contrast, gallery action order/availability, and profile tab indicator bounds protection.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Live unsigned Vertex NIP-50 query returned kind-0 profiles. Search requests and event verification run off the UI thread with bounded results and cancellation.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,995,951 bytes; SHA-256 `a6d6a02461638ae3a74c71f078c3165d573500641c3554c1de639676374ce502`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.33.1 release

- Version 0.33.1 (66), source commit `80f003aa77ee01b4c31455c2ff754b38f8f5c8cb`. Full advertised write-relay profile reads, targeted refresh, and database conflict handling.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Quartz 1.17.0 insertion source inspected: batch insertion distinguishes duplicate/superseded row rejections from storage failures. Ingestion explicitly dispatches to IO; failures are not marked cached.
- Profile queries retain all valid advertised write relays, process four at a time with per-relay timeouts, close sockets on completion/cancellation, and preserve the bounded main-feed pool. Full article recovery still needs device verification.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 14,995,951 bytes; SHA-256 `80785ea1778a90a9d7fbcbb2c0d3d040759d657d1bd9052773b18967fc6fb887`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.34.0 release

- Version 0.34.0 (67), source commit `46b3622e52c91a17ac4c02a2a2443e58a2297424`. Text replies with Vertex mentions and client attribution, cache usage on the storage slider, NSFW label, feed selector edge layout, and NIP-05 error clarity.
- Signed release build passed, including release lint and R8. No automated tests or live device checks.
- Cache usage reuses existing background size polling; the slider draws usage and budget on a shared byte-proportional scale, keeps the existing limit choices, and supports RTL and accessibility adjustments.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 15,028,719 bytes; SHA-256 `f826d7c74232ffa7af0fea3b2e13d94a98303ecad3f4339ea29bad4597070d07`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.35.0 release

- Version 0.35.0 (68), source commit `3535d4f8d9722df040f3a206dcbc76f5c4d4a346`. Long-press zap amount selection and follow/NIP-05 icons in mention suggestions.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device/payment checks.
- Zap amounts feed the signed request and invoice amount validation; uncertain payments retain their journal and are checked before another payment. Recent confirmed local amounts are account-scoped.
- Mention identity icons reuse bounded background verification and its cache; no network work on the UI thread.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 15,028,719 bytes; SHA-256 `7e6dd90a1eb7e4dccca55ae9414892094dea83cdd878fa7566608a7e490cbfad`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.36.0 release

- Version 0.36.0 (69), source commit `fa0bc6117fd798461d726e2406a0f598fefa7a42`. Full-screen kind-1 composition from the feather button, article share/engagement actions, primary send button, reply alignment, and history-only zap choices when available.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device/publishing checks.
- New posts reuse account-scoped drafts, Vertex mentions, client attribution, signature verification, and persisted exact-event retries. Signer activity handling is shared at the app root for all main-feed pages.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 15,028,719 bytes; SHA-256 `a6309cdad5d985dce6cb25f39234a69915fc2ad3513ae79700bea59a207f693e`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.36.1 release

- Version 0.36.1 (70), source commit `d1b9c1a61e3035d1cedc8dade4003fdb51b844a6`. Adds theme-aware green/orange/red cache fullness to the 0.36.0 changes.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- Color uses the selected cache budget: green through 50%, orange at 75%, red from 95%; the usage label shares the color. Existing background cache measurement is unchanged.
- Version 0.36.0 finished publishing to GitHub while this requested addition was being implemented; Zapstore publication uses 0.36.1 directly.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 15,028,719 bytes; SHA-256 `7941997b95e76c4087d3f40e16a4052a2a7f14b12b68fe69d51128e119e2a6e6`. Mapping archived locally. Website unchanged.

## 2026-10-11 — 0.37.0 release

- Version 0.37.0 (71), source commit `c6ff632c65a1db1da89d14a40bb473a7519b73ec`. Right-aligned reaction/zap actions and detail-only sharing, profile mention compose button, bottom-right send, and Blossom image attachments.
- Debug and signed release builds passed, including release lint and R8. No automated tests, device checks, or live image uploads performed.
- Blossom BUD-02/03/04/11 reviewed: account server discovery (kind 10063), explicit scoped signer authorization (24242), byte/hash/size validation, ordered primary attempts and mirroring. One upload job, four images per selection, 20 MB per image, bounded replies/timeouts; disk and network work on IO.
- Successful upload URLs persist in drafts; cancellation closes remaining network work and removes the temporary file. Device validation of signer authorization and configured-server compatibility remains outstanding.
- Certificate SHA-256: `78fadad16ed2625151be59c4ad9b8ab24a96e9e4e454751f40623fa149c278cd`.
- APK: 15,045,103 bytes; SHA-256 `96b5b11573ed3eb1e42c3ca7de5e3e7bc73e968d33294efc18fc6b6c02089dc9`. Mapping archived locally. Website unchanged.

## 2026-10-12 — 0.37.1 release

- Version 0.37.1 (72), source commit `955a09cc867b63537ba476a1d1cdab93f18c909e`. Standard padded Blossom authorization for Haven/Primal, bounded upload error details, and detail-toolbar Nostr reference copying.
- Signed release build passed, including lint and R8. No automated tests or live device upload checks. Website unchanged.
- APK: 15,045,103 bytes; SHA-256 `33b6eef70fb20fa5c51af4aa8ab616ac5f6031b4179306c7f15e7d72ded0bb14`. Mapping archived locally.
- Primal zap description-hash mismatch remains unresolved; exact-request validation retained, with no payment attempted.

## 2026-10-12 — website refresh and zap interoperability investigation

- Explicitly requested website refresh: 18 detailed feature descriptions, responsive blue/white and system-dark styling, evergreen GitHub/Zapstore links, and current privacy disclosures. Deployed to feather.dergigi.com with Vercel; live HTML matches the source. Local asset paths and section anchors checked. Desktop and mobile visual previews inspected; no automated test suite.
- 0.37.1 Zapstore version event points to asset `f23a87b677beab3364e8ee3c74063ab5d6e097063032acf1272ca1e3846fa357`, versionCode 72, matching APK SHA-256; CDN returned HTTP 200.
- Compared Wisp `2e56fbd3c89ca280263c5543079ddd9ea5aa0ca9` (`Nip57.fetchInvoice`, `ZapSender`) and Amethyst `8557a4fdbc4e7d437df6bde50dc8098359958580` (`LightningAddressResolver`, `LightningInvoiceResolver`). Both serialize the signed request into the URL-encoded nostr callback parameter. Wisp passes the invoice to the wallet without comparing the description hash; Amethyst's invoice resolver checks the amount but does not compare that hash before returning the invoice.
- For supplied note `549ec46754c08210a638cc5f16bf8483bac459ef6d163cf609d29a30feea54db`, the recipient is `lyn@primal.net`. Five public kind-9735 receipts from relay.primal.net have five distinct embedded request IDs but the identical invoice description hash `d3aca82072933721a2cfcb90696cb2fae79ef827f5b13bd3e5fc559577621283`, matching the hash in the user's failed attempt. None matches SHA-256 of its embedded description. Example receipt: `5a10f0232e4a9813e7a5388132c45657ec85aa393ab304cf309dc57e666c1b2f`, with client tag Primal Android. This strongly indicates provider-side request binding failure rather than Feather serialization; no payment or new signed request was made. Exact-hash validation remains intact.

## 2026-10-12 — public screenshots and notification filters

- Website and Zapstore use eight cropped screenshot derivatives only. Status bars are removed from feed, profile/settings, thread, picker, notifications, and search captures; immersive photo-grid/gallery captures already have no status bar. Originals are not committed or uploaded. Website screenshots use compressed WebP, lazy loading below the hero, and a keyboard-accessible horizontal strip; mobile layout visually inspected.
- Zapstore kind-32267 metadata contains eight image URLs ordered feed, photos, gallery, thread, reactions, notifications, search, settings. Description matches the user's requested wording. No new APK version published.
- Profile search and composer suggestions share follow/NIP-05 status icons. Notification filtering uses the existing bounded events, computes previews on Default, and adds no subscriptions. Replies include kind 1/1111; zaps include nutzaps. Active filter toggles back to all; selection is saved per account in UI state.
- Debug builds passed for both UI changes. No automated tests or live device checks.

## 2026-10-12 — 0.38.0 release

- Version 0.38.0 (73), source commit `08f91c53f28273d44b49cca86f788bc2248b0538`. Repost on tap, quote composer on hold, notification type filters, and shared follow/NIP-05 indicators in profile search.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- Signed reposts persist before publication for exact-event retries; account-scoped pending state and bounded journal. Protected reposts omit embedded content.
- APK: 15,061,487 bytes; SHA-256 `a866f5d3555f6ca9f02310681dcafac81838cc1e3265fa7dae296a6a948b5127`. Mapping archived locally. Signing certificate matches previous releases. Website unchanged by this release.

- Zapstore publication completed after the remote signer returned online. Version event references asset `0e9d944f18c16c65afce29295d003d1fab08e7af58a6e4d97ed0581a180ffb57`, versionCode 73, matching APK SHA-256 and signing certificate; CDN returned HTTP 200.

## 2026-10-12 — 0.39.0 release

- Version 0.39.0 (74), source commit `3c8d50b6cf03e0e64e8ead05afab5e1fde9b959b`. Upload byte progress and compact file size, tinted rounded composer field, secondary attachment button, rounded thumbnails with remove overlays, and a shared one-line parent summary with content-aware icons.
- Signed release build passed, including lint and R8. No automated tests or live device checks. Upload progress reserves completion until server descriptor validation; preparation and mirrors remain indeterminate.
- APK: 15,061,487 bytes; SHA-256 `55fbf890b785511d8695515aefd80e77cd1fbf0aea3391f671395ab7afbfabce`. Mapping archived locally. Signing certificate matches previous releases. Website unchanged by this release.

## 2026-10-12 — 0.39.1 release

- Version 0.39.1 (75), source commit `cc1bc438fc60a936c7c233904004906f646f5a01`. Full-width inline media, cached account-sheet avatars, media-URL cleanup in reply previews, feather-blue repost state, Reply inset, and smaller reaction emojis.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK: 15,061,487 bytes; SHA-256 `d852d5c70e0d746f97aa8c5236885c31766e38c1a960b595342ad26ae130e110`. Mapping archived locally. Signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `af7ccbfa5c150359232d50609bb890d510dcae8c61b5fe680753bb01adc3e268` matches versionCode 75 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.39.2 release

- Version 0.39.2 (76), source commit `bd9f331092f28bdf0e944002e49a2ecfa2ea71b1`. Centered expansion chevron in the action bar, no compose-button shadow during scrolling/fading, and no count for a sole own reaction.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK: 15,061,487 bytes; SHA-256 `23ecf37d4c58ab861a4ca0e344dca50891b29bd9a75556d9d28b8f2373484207`. Mapping archived locally. Signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `d0b409aaaa3a81b62e28463ff9b85825402ca01cf63a56a5276dfb3ec14da7fc` matches versionCode 76 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.40.0 release

- Version 0.40.0 (77), source commit `4ab6c31e9aceff6f9e81dbf7e552a5e3d07b176b`. Multi-select notification filters with union matching and reset-to-all bell; trailing whitespace trimmed before signing posts/replies.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK: 15,061,487 bytes; SHA-256 `59921d08ebc0d9030a935ebf7206cb990d0562b157c258fb8a204d6d24de2941`. Mapping archived locally. Signing certificate matches previous releases. Website unchanged by this release; tagline/OG changes were published separately.

- GitHub and Zapstore publication verified. Zapstore asset `1cd572023ed1bb3d47e5aa10218c5ceac5e060bd04d1fa076677f24e3e1cb831` matches versionCode 77 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.40.1 release

- Version 0.40.1 (78), source commit `66370de7bf9621d7afb58437f602a2761f7d2451`. Provider invoice compatibility, copyable sanitized zap diagnostics, and notification navigation/filter polish.
- Signed release build passed, including lint and R8. No automated tests or live payments.
- APK: 15,062,083 bytes; SHA-256 `c530a8a042ecbfea8fc5dcef14869cc92d08efc5ba247aa69192cc2c2f37cbe3`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `3995b5d0bbcd92c191740166c2a7034359c29e5d0da65be940bcd916229ffcaa` matches versionCode 78 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.40.2 release

- Version 0.40.2 (79), source commit `532964c10b81cb2cf8be46ead0bf43ec118c2b41`. Zap confirmation lookup, provider errors and safe response diagnostics, and expanded full-picker emoji recents.
- Signed release build passed, including lint and R8. No automated tests or live payments.
- APK: 15,062,087 bytes; SHA-256 `aaa94c492ce58f3d0726da5476b0692c518b81f2df708cd3e26307d90b8d794b`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `4ac5647d60178cbffd7f33c0070adf3948eed48bb20907a7d86a50e64d45f075` matches versionCode 79 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.41.0 release

- Version 0.41.0 (80), source commit `8960ce74ffba17fa5ee7f811f02a12137854f1f6`. Shared media-first tab order, gallery author avatar/profile navigation, and distinct notification reset bell.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK: 15,077,927 bytes; SHA-256 `a76cce502df40dd5fa3ae8e0e6366c39a3b179bda29111ae4e6af6a5791a3775`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `53c8e7b4443d00abf6f95857dfbd58115b1e08875473f107902fbe8c90aeda6c` matches versionCode 80 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.42.0 release

- Version 0.42.0 (81), source commit `35cf8f1aa18a88d0106a28ec4a746e83d3c09cf6`. Dynamic Voice and Podcasts tabs, cached audio playback, waveform/artwork rendering, and voice reply threading.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK: 15,079,535 bytes; SHA-256 `2b4a902f7b122380861e8c5a5602b113a17d28eee34e67345abc0f1d5629ea52`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `d555a7bb3209c53fadbd380f506c2f394edd8e2af7cf7125b85a4aad532471e6` matches versionCode 81 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.43.0 release

- Version 0.43.0 (82), source commit `5510439ca1572f2c05a39438e500e6c377d0c23c`. Background voice/podcast playback with Android media controls; legacy NWC settlement confirmation and lookup after payment timeouts.
- Signed release build passed, including lint and R8. No automated tests or live device/payment checks.
- APK: 15,474,273 bytes; SHA-256 `7ff5251422154c33d4876a3faa5607d72e1f58a26b8facd6f9791dfa39afd2b2`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `a1b83d640878c0e0695f777d1ed08c67d3259fbc6b04a695b9e28eec9eb7417c` matches versionCode 82 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.44.0 release

- Version 0.44.0 (83), source commit `6951b82e654744c4285503aa0e0472f0b8de98b8`. Fountain episode shares replace kind-54 podcasts; quiet reaction retries, transient wallet lookup retries, top-aligned search, and composer attachment beside Send.
- Signed release build passed, including lint and R8. Inspected a live Fountain episode page and its public audio metadata. No automated tests or live device/payment checks.
- APK: 15,474,273 bytes; SHA-256 `27fdfcda43be9c53f2d1a27ae072069bca24e3e0d57170e59c4970afbebd74dc`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `c82c7f067a2a18137986ecd254e75a77511a5025eafc6358baf9f574100ee814` matches versionCode 83 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.44.1 release

- Version 0.44.1 (84), source commit `6526f5c`. Earlier file-size display, visible upload stages, bounded discovery/signer waits, readable transfer percentage and brief completion display.
- Signed release build passed, including lint and R8. No automated tests or live device/upload checks; the reported stall needs device confirmation.
- APK SHA-256: `894508b9c1bd17950d12f46c50c503c0c1676440225fd88cee92147edab0146f`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `a4e942b6197d5c2b3da7039b8f4e297adb219bff1ddacee5f708f7b39d3315de` matches versionCode 84 and the APK hash.

## 2026-10-12 — 0.44.2 release

- Version 0.44.2 (85), source commit `208a34c`. Thumbnail removal button centered on the corner; single-newline image URLs; clearer local image preparation diagnostics.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK SHA-256: `ca78ffe254e0365de963a6e2da30b922e534a41649f5e5c0103e1047cf716d4b`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `6c327ec4fa61d455d222e157dc19dc988fbc6bed9e0e87b8c44e7c95febee729` matches versionCode 85 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.45.0 release

- Version 0.45.0 (86), source commit `beeaf24`. Metadata-stripped image uploads, attachment gallery previews, tighter thumbnail spacing, system picker selection limit, and hashtag publishing.
- Signed release build passed, including lint and R8. No automated tests or live device/upload checks.
- Reviewed upload ordering: local sanitization precedes the hash, signer authorization, direct upload and mirroring; failures never fall back to original bytes. Known JPEG/PNG/WebP/GIF containers remove metadata while preserving compressed image/animation data where possible. Static orientation changes and other decodable static formats re-encode pixels with bounded memory; unsupported animation/orientation combinations fail explicitly.
- APK SHA-256: `5eec6cc3e2e3b3bd6528c536d218b1fb06219aaee4956e78665f65e53e016b4d`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `be3bad22c0d9c1f24e4bdf16b49391ae6e1ffadb13a04036b7ee1d2664d128c2` matches versionCode 86 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.45.1 release

- Version 0.45.1 (87), source commit `fe9b2e9`. Composer reopening after upload cancellation; NIP-05 before follow status.
- Signed release build passed, including lint and R8. No automated tests or live device checks.
- APK SHA-256: `104d8ccba96978b144a1223b7814dfd4e436501eadd32414db110964f062b4c0`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `3e6d88f341f5613b47c72d6375154d1f7ae7c43499d12ba64fae26d5ecedd76b` matches versionCode 87 and the APK hash; CDN returned HTTP 200.

## 2026-10-12 — 0.46.0 release

- Version 0.46.0 (88), source commit `0e9b3d4`. Android image sharing into the active composer and encrypted per-account NWC restoration after login.
- Signed release build passed, including lint and R8. No automated tests or live device checks. Reviewed logout deactivation, account-specific wallet access/deletion, and migration of existing owned credentials.
- APK SHA-256: `ed336bc6500e5a1275648b33718bf5865652e0bcc033d8261f47b47000e3fdd3`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `d474a8aa461b23f5305b424bf833dd8cadef0f9fb8eeb25058cd3f0208450f84` matches versionCode 88 and the APK hash; CDN returned HTTP 200.

## 2026-10-13 — 0.47.0 release

- Version 0.47.0 (89), source commit `cf5e1fd`. Hardware keyboard composer shortcuts, quote layout/cursor and article previews, notification filter retention and mention icons, app-level private mute loading, own-profile composer, and year-aware timestamps.
- Signed release build passed, including lint and R8. No automated tests or live device checks. Private blocks require a mute list available through the connected signer; the notification-only screen now triggers the same decryption path as readers.
- APK SHA-256: `bccda5037a96e8e1b8256ac07f49eee76acf457dca30035c5c3b2cc0ae247215`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `10fef19c64d18ae3f2f9e7f56ba9e79429874a6d1684788ee30c7aaf37611cf2` matches versionCode 89 and the APK hash; CDN returned HTTP 200.

## 2026-10-13 — 0.48.0 release

- Version 0.48.0 (90), source commit `6641a6a`. Full Markdown article rendering using Boris’s renderer version, full-width cover/title treatment, and durable per-account notification filters.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks. Shared image cache and bounded decode widths retained; notification preferences read/written on IO.
- APK SHA-256: `486417ced47c5974006b9d14170be2e2a09c3d09d303d9f2d922390082e4bcfc`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `59aa5054dce2c5f34ff27924774bff15d3cb62916457b26240f4a6b193cabf45` matches versionCode 90 and the APK hash; CDN returned HTTP 200.

## 2026-10-13 — 0.48.1 release

- Version 0.48.1 (91), source commit `b8eff9f`. Retain article parser, reference-link handler and image transformer across recompositions to prevent loading-state collapse and scroll jumps.
- Debug and signed release builds passed, including release lint and R8. No automated tests or live device checks.
- APK SHA-256: `90e2c77a875a30f442de504bb22deda9e0c7907ade6bce15285c165ba840e4ac`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.

- GitHub and Zapstore publication verified. Zapstore asset `bc664975bbe38c80b0ce26af8fcc276b52cc1cbefecfe78cec0a7be6ddd31a67` matches versionCode 91 and the APK hash; CDN returned HTTP 200.

## 2026-10-13 — 0.49.0 release

- Version 0.49.0 (92), source commit `726236d`. Public NIP-34 issue reporting from settings and the crash dialog, plus discoverable kind-1621 Issues feeds and Markdown detail.
- Debug compilation and signed release build passed, including release lint and R8. Reviewed signature validation, explicit submission, draft persistence, bounded relay lifetimes and exact-event retries. No automated tests, live device checks or synthetic public issues.
- APK SHA-256: `303b088e6ab4ab98fa91892dc9884791c5827c2adf8682e5ca4b3635c593d62e`. Mapping archived locally; signing certificate matches previous releases. Website unchanged.
- GitHub APK digest verified. GitHub and both GRASP mirrors have the release source commit and tag.
- Zapstore release and signed asset `b0c3741adaffd2a043bf5e8c5038ee0049211ca734e31bd748b92ff98d673015` verified: versionCode 92, matching APK hash/certificate, CDN HTTP 200.

## 2026-10-13 — 0.50.0 release

- Version 0.50.0 (93), source commit `4c738ec`. Android nostr: note/profile links on cold and warm starts, plus bounded post/reply delivery retries using the same signed event.
- Debug compilation and signed release build passed, including lint and R8. Inspected packaged VIEW/DEFAULT/BROWSABLE nostr intent filter and version metadata. No automated tests or live device checks.
- APK SHA-256: `2d08300ae41836e66304a3eac5fac7f70a9e002d2b4a0a5de57917c7fbb82563`. Signing certificate matches prior releases; mapping archived locally. Website unchanged.
- GitHub asset digest matches. Zapstore release and signed asset `bccf0075acc1d3a2ba59197c1779f72c0a68ab2c824c6c6a1e5cad8a9a882f87` verified: versionCode 93, matching APK hash/certificate, CDN HTTP 200. Release source and tag pushed through ngit to GitHub and both GRASP mirrors.

## 2026-10-13 — 0.51.0 release

- Version 0.51.0 (94), source commit `0039a30`. Native app details and screenshot galleries, app NIP-22 comments/replies, reactions/zaps and native app address links.
- Debug compilation and signed release build passed, including lint/R8. Reviewed Zapstore comment implementation, current NIP-22 and read-only live app metadata. No automated tests, live device checks or synthetic public comments/payments.
- APK SHA-256: `08d44d9cd2da4b6e93e0081a1d1e3ffad8a62d8f628584c91d312f7be7cd9895`. Signing certificate matches prior releases; mapping archived locally. Website unchanged.
- GitHub digest and signed Zapstore asset `d63ccf2be18a22af050a595f09a7b052a2bb03150a197035307bf296b54d3e4a` verified: versionCode 94, matching APK hash/certificate, CDN HTTP 200. Source and tag pushed to GitHub and both GRASP mirrors after remote signer reconnected.

## 2026-10-13 — 0.52.0 release

- Version 0.52.0 (95), source commit `2713219`. Background NIP-13 proof of work for posts/replies (16 bits) and emoji reactions (12 bits), with a persistent settings toggle enabled by default.
- Debug compilation and signed release build passed, including lint/R8. Reviewed Wisp defaults, Quartz miner, signature/hash checks and exact-event retries. Mining uses a single background CPU worker, outside the signer lock, without a cancellation control. No automated tests or live device checks.
- APK SHA-256: `660619910e8815cf14095ca5d634b27854da1de086f96b60b35c8bedb7bf6459`. Signing certificate matches prior releases; mapping archived locally. Website unchanged.
- GitHub digest and signed Zapstore asset `c526399c8d586a9c1f92bf47aed0cb664a540cc401b279bc9c5ebc60ac2085c3` verified: versionCode 95, matching APK hash/certificate, CDN HTTP 200. Source and tag pushed to GitHub and both GRASP mirrors.

## 2026-10-13 — 0.53.0 release

- Version 0.53.0 (96), source commit `d6b2f8a`. Optimistic reaction/repost icons without spinners, bounded repost retries, signed repost deletion/undo, and faster orange composer progress specifically during PoW mining. Settings label shortened to PoW.
- Debug compilation and signed release build passed, including lint/R8. Reviewed exact-event retries, account-scoped optimistic state, deletion tombstones and fresh IDs after undo/repost. No automated tests or live device checks.
- APK SHA-256: `2a265d10638c0f0b49707d3e5b321bd882b5d69104453eb90082f05cf3e68edc`. Signing certificate matches prior releases; mapping archived locally. Website unchanged.
- GitHub digest and signed Zapstore asset `225dd40b26b8dc3d125c66ec3ba6573a9ae47c5191ed379cc5ed0852eb2b3a04` verified: versionCode 96, matching APK hash/certificate, CDN HTTP 200. Source and tag pushed to GitHub and both GRASP mirrors.

## 2026-10-13 — 0.54.0 release

- Version 0.54.0 (97), source commit `d35b5e2`. Full-screen gallery brightness gestures, bounded HTTPS Lightning redirects with diagnostics, wallet URI hint, and reaction pickers usable during queued sends/removals.
- Debug compilation and signed release build passed, including lint/R8. Reviewed gesture direction locking and window brightness restoration, redirect limits/deadline, and bounded account-scoped reaction queues. No automated tests or live device checks.
- APK SHA-256: `9bf115433b7618ddbb11f9aa7f8c33d4830bced2315c8807ca9307651d0a917f`. Signing certificate matches prior releases; mapping archived locally. Website unchanged.
- GitHub digest and signed Zapstore asset `137dc6eda5ed01127cb12bcec70e0f9bd99068dfdcf2e19a09420f8821215cec` verified: versionCode 97, matching APK hash/certificate, CDN HTTP 200. Source and tag pushed to GitHub and both GRASP mirrors.

## 2026-10-13 — 0.55.0 release

- Version 0.55.0 (98), source commit `28848b1`. Native nostr.build GIF picker in post/reply composers, draft-persisted NIP-92 metadata and subtle linked attribution. API key kept in ignored local build configuration.
- Debug compilation and signed release build passed, including lint/R8. Read integration/grid guides; adapted browser advice to bounded native Coil previews (six visible animations, no original grid downloads). Read-only authenticated API queries for gm, 😂, 猫 and café returned 200 results each. No automated tests, live device checks or synthetic public posts.
- APK SHA-256: `86b4cc47ab8f21d8a1b94b199499a2487b95dcceff1ad5717003573f43520e8e`. Signing certificate matches prior releases; mapping archived locally. Website unchanged.
- GitHub digest and signed Zapstore asset `bbb81ca2bc2599370ce867bd3a8915017f844f40bc4abc6bd3bb44a3892001e9` verified: versionCode 98, matching APK hash/certificate, CDN HTTP 200. Source and tag pushed to GitHub and both GRASP mirrors.

## 2026-10-13 — 0.56.0 release

- Version 0.56.0 (99), source commit `55d413c`. Settings feather button opens the supplied Feather profile internally; pre-payment Lightning HTTP network failures retry twice with diagnostic attempt details, without resending wallet payments.
- Signed release build passed, including lint/R8. APK signature and version verified. No automated tests or live device checks.
- APK SHA-256: `ad4bb0a437077c24816f50e69f39bdc28465a6bc01c09ad701869dd3a7df1c61`. Signing certificate matches prior releases; mapping archived locally.
- GitHub digest and signed Zapstore asset `de6e852abff8160fca5538bc5bc9374a0f2d82d65082325c7c865d5f34e9c5a6` verified: versionCode 99, matching APK hash/certificate, CDN HTTP 200. Source and tag verified on both GRASP mirrors.
- Separately requested website update deployed to feather.dergigi.com: Feather Nostr profile link and GIF keyboard/search description. Verified both on the live page. Existing screenshots unchanged.

## 2026-10-14 — 0.56.1 release

- Version 0.56.1 (100), source `81e57b4`. Settings feather icon reduced to 28dp, with unchanged button and touch target.
- Signed release build including lint/R8 passed; no automated tests. APK hash `eafc33c2f2b2f963941d15fda90580485a257a1db165efcc1991aab81597eccd`, certificate matches previous releases. Mapping archived locally.
- GitHub digest and both GRASP tags verified. After reconnecting the signer, Zapstore asset `89586c5ecfc45319cac224bee4cca137832755a8c47d1b525e8b88ceab9cb523` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.

## 2026-10-14 — 0.57.0 release

- Version 0.57.0 (101), source `a2595cd`. Combined image/video composer picker, bounded sequential Blossom video uploads, metadata-removing remux without re-encoding, video preview/removal and left-aligned GIF actions.
- Debug compilation and signed release build including lint/R8 passed. Reviewed background processing, cancellation, 250 MB video bound, 16 MB frame buffer, player lifecycle, MP4 rotation and WebM container selection. No automated tests, live device uploads or public test posts. Codec/container compatibility and device preview behavior remain for live testing.
- APK SHA-256 `d6173f332b6861127686b0ebc51857b0cdcbaf5fed0472a328a15d1bd49ea49b`; signing certificate matches prior releases. Mapping archived locally. GitHub digest and both GRASP tags verified.
- Signed Zapstore asset `327dc37826f1460ed2e88b53ed02e439c00aaff08e4f709d4788bd1258b22e85` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.

## 2026-10-14 — 0.57.1 release

- Version 0.57.1 (102), source `53d1c15`. Android SEND/SEND_MULTIPLE now accepts video shares and routes them through the existing current-draft/new-composer attachment queue.
- Debug and signed release builds passed, including lint/R8. Packaged manifest verified to advertise image/* and video/*. No automated tests or live device checks.
- APK SHA-256 `0e39bd60076bb745d4bf3f834a925d0c7c5c59a7409023475be4eaa7f6ca53c7`; certificate matches prior releases. Mapping archived locally. GitHub digest and both GRASP tags verified.
- Signed Zapstore asset `6bec69b7716f087fa1509e287d0ddeaaa7f710ef8f85d7d173fefcac5906abd0` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.

## 2026-10-14 — 0.57.2 release

- Version 0.57.2 (103), source `9e2bffc`. Generic and mixed file shares now reach the composer; upload classification inspects actual image/video contents instead of provider MIME labels before signing or uploading.
- Debug and signed release builds passed, including lint/R8. Packaged debug manifest verified to advertise generic shares. No automated tests or live file-explorer checks.
- APK SHA-256 `13e6f5f7708d6e0a14f5b7531a5d7d63dea344c68793291a57b72cd6b2f520b0`; certificate matches prior releases. Mapping archived locally. GitHub digest and both GRASP tags verified.
- Signed Zapstore asset `190523d5668f36e256c8abea8915c517beb12c395eef9e11f36d7d3d886b907d` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.

## 2026-10-14 — 0.58.0 release

- Version 0.58.0 (104), source `b235e81`. Repost clockwise spin/settle (380 ms), reaction pop (260 ms), and confirmed-zap flash with three tiny sparks (200 ms).
- Debug compilation and signed release build including lint/R8 passed. Reviewed local tap triggers, independent icon-only transforms, bounded/account-cleared celebration state, and wallet return paths: failures/uncertain outcomes throw; already-paid history returns false; newly confirmed payments return true. No automated tests, live payments or device animation checks.
- APK SHA-256 `e8795680387180071338324573f27947c2cb1805880357ad10f9aa29760b01c3`; certificate matches prior releases. Mapping archived locally. GitHub digest and both GRASP tags verified.
- Signed Zapstore asset `907ffc7ac6f609b84a22d425eafa037d68a2b85f3d6c49a2d59beab06f3ddc3e` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.


## 2026-10-14 — 0.58.1 release

- Version 0.58.1 (105), source `c178e3e`. Central cache-first exact-ID resolution checks complete verified copies in memory and SQLite, deduplicates thread/embedded requests, and removes fulfilled IDs even when supplied by another subscription. Discovery filters remain separate. The design specification and contributor rules now require this behavior.
- Signed release build including lint/R8 passed. Source audit found one exact-ID relay filter construction site, behind the shared cache check. Reviewed serialized cache access, conflated background lookup work, 25-ID request/128-event memory bounds, background unsubscribe/resume, eviction versus verification memo entries, and exact older-article IDs without replacing newer addressable records. No automated tests or device/network-traffic measurements were run.
- APK SHA-256 `04e5f9d79c61657ec83211dcea7cd1c140c28de3a29d58e9243a41e3d9236670`; 15,623,877 bytes. Certificate matches prior releases. Mapping archived locally. GitHub digest and both GRASP release tags verified.
- Signed Zapstore release `55e59ce896338095567946ff6c9147cd1afb859b6c707684902dcb5000e7be96` and asset `c0b274a248975e8b3a4a9d19b282e8242f7ef9e57824249fd8d0d7f02d814691` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.
- Thread redesign remains separate. The existing `checkpoint/pre-thread-redesign` tag still points to `36f4056` (0.58.0 plus validation).

## 2026-10-14 — 0.59.0 release

- Version 0.59.0 (106), source `325d6f5`. Connected ancestor chains, focused-note layout, grouped reply branches, cache-only self-thread previews in Posts, explicit ancestry gaps, direct-reply counts and conversation scroll restoration.
- Debug compilation and the signed release build passed, including lint/R8. Reviewed NIP-10 marked/positional replies, NIP-22 parent versus root scopes, addressable parents, legacy app comments, quote exclusion, author-matched deletion requests, muted/sensitive ancestry, cycle/depth bounds, stable row keys and independent navigation state. No automated tests or live device interaction checks were run; no Android device was connected.
- Exact-ID network requests still have one construction site behind the shared cache check. Thread work is bounded to 320 candidate events, 31 displayed ancestors, eight missing-parent lookups, 12 discovery parent IDs and 512 retained deleted-note IDs. Existing connection limits remain in place. Actual relay coverage, visual layout and scroll behavior remain for device testing; no exhaustive-history or measured-performance claim is made.
- APK SHA-256 `5237e8ba1b8764218626caaeb90811e4f793f40f50e5b6fb47872ea7e23dbc7c`; 15,624,405 bytes. Certificate matches prior releases; mapping archived locally. GitHub asset digest and both GRASP release tags verified.
- Signed Zapstore release `44897c97e199972893843e860fdaf7c4b078a6d174e7cacfef2086c719bca532` and asset `44ab4c94423c4f28ab47b304e34337ab4a3dfea27a0df2f42fa5fbfcb219d4da` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.
- Rollback tag `checkpoint/cache-first-before-threads` points to `068420b` (0.58.1 plus verification), verified on GitHub and both GRASP mirrors. It retains the cache-first fix without the thread redesign.


## 2026-10-14 — 0.59.1 release

- Version 0.59.1 (107), source `22221ec`. Preserve thread anchors during ancestor loading, retain the selected note's single-line parent preview with muted loading progress, use vertical continuation dots on the avatar rail, expand reply branches in place, and remove horizontal note dividers.
- Debug compilation and the signed release build passed, including lint/R8. Reviewed initial/cache handoff, keyed position restoration, short-list end clamping, same-pass tail measurement, per-visit retained measurements/expansions, gap replacement, bounded branch expansion, and context progress completion/timeout without new lookup subscriptions. No automated tests were run.
- Brief manual emulator check with a public short reply: the selected note remained at the top after its parent was available above it; scrolling upward revealed that parent and its image. No app runtime exception appeared. Remaining multi-step loading/expansion and real-device gesture behavior still need user testing. Screenshots remained local and were not published.
- APK SHA-256 `3896203768ad35041370c82a7e68183f5dafa9a956182db3d67a0bfa8f38de48`; 15,640,793 bytes. Certificate matches prior releases; mapping archived locally. GitHub asset digest and the source/tag on both GRASP mirrors verified.
- Signed Zapstore release `da7de12632fdb5e72612dc77b420a80a0468254704fbb3b3e8e8e89ac9d77a20` and asset `41c8ec0c4e2a8dc88b430bff7f9fab579a88ed5fb401c97d11384635a62b780f` verified with matching hash, certificate and versionCode; CDN HTTP 200. Website unchanged.


## 2026-10-14 — 0.59.2 release

- Version 0.59.2 (108), source `5b4cd8e`. Continuation dots share the existing action row in the avatar gutter and use the connector color. The selected note’s parent summary fades/collapses when a connected, visible parent event is rendered above it; unresolved or filtered/deleted gaps do not trigger removal.
- Debug and signed release builds passed, including lint/R8. Reviewed 48dp continuation hit targets, separation from reply taps, wrapped action-row alignment, stable event keys, initial cached-parent visibility and existing same-pass thread height/anchor handling. No automated tests or device interaction checks were run; animation feel remains for device testing.
- APK SHA-256 `b7c4ef76f241d897e5362e78a960b7e90cd092c65763eacb36398a8937e25bdf`; 15,640,793 bytes. Embedded source commit and version verified; signing certificate matches prior releases. Mapping archived locally. GitHub asset digest and the source/tag on both GRASP mirrors verified.
- Signed Zapstore release `b2c9299fd1fd79af37759fb3e6a7c4c0d50a36739c46745d06dae4c6e4e05390` and asset `e4ab27a92dc08a54a348fda694c28d860f2744a8c56aa6e72926110a4ce4f92f` verified with matching hash, certificate and versionCode; CDN HTTP 200. No website deployment as part of this app release.

## 2026-10-14 — 0.59.3 release

- Version 0.59.3 (109), source `923f6e8`. The upper parent ellipsis extends the current conversation in place, retaining the focused note, reading position and Back history. Expanded ancestry survives returning to the thread; missing parents use the shared cache-first lookup and a muted loading indicator.
- Debug compilation/build and the signed release build passed, including lint/R8. Reviewed bounded ancestor expansion (31 initially, increments of 32, maximum 255), one explicit parent job, the existing eight missing-parent lookup slots, cancellation on navigation/background, saved depth and shared cache-first retries. No automated tests or device interaction checks were run.
- APK SHA-256 `21e18a8d8a6a62974cb2d3607a86fc8cf760833df7d0ce910752612f5902437e`; 15,640,793 bytes. Embedded source commit and version verified; signing certificate matches prior releases. Mapping archived locally. GitHub asset digest and the source/tag on both GRASP mirrors verified.
- Signed Zapstore release `1c8c917ed0bbc34e3769e2d9a59980a99b1e42b147e5f637d25c7f8332fc8bd5` and asset `8a1a1fd50ad10d4f5a7e42cead56bd966c84b1b0d94f679236c26e57ec755373` verified with matching hash, certificate and versionCode; CDN HTTP 200 and matching size. No website deployment as part of this app release.

## 2026-10-14 — 0.59.4 release

- Version 0.59.4 (110), source `101ec7b`. Thread roots use normal post width in conversations and feed previews. The root reply icon's tail aligns with the avatar rail and connects to the next reply, with counts following the fixed icon slot. Missing/hidden parents remain replies, and other reply branches stay separate.
- Debug and signed release builds passed, including lint/R8. Reviewed root classification through the existing parent resolver, first-direct-reply connection, icon/vector geometry, count alignment, right-to-left mirroring and drawing below the content without additional lookup requests. No automated tests or device interaction checks were run; visual feel remains for device testing.
- APK SHA-256 `5b53707f8b820444f5e6b9b319b276c8dd2575087b91bf9dcc50bfbfbe718ed7`; 15,640,793 bytes. Embedded source commit and version verified; signing certificate matches prior releases. Mapping archived locally. GitHub asset digest and the source/tag on both GRASP mirrors verified.
- Signed Zapstore release `1a70578e96ac9cc61d772118f38a39e4ab5bc0ec336c9bf39cd33a6ee99fdbe8` and asset `606e9a69c5c2c2dee1862d26a85820aef110b0e9686abb75d11cbb8f9e9ded7a` verified with matching hash, certificate and versionCode after retrying a transient relay timeout; CDN HTTP 200 and matching size. No website deployment as part of this app release.


## 2026-10-15 — 0.60.0 release

- Version 0.60.0 (111), source `1c193fb`. A phone icon beside copy/share opens the current note in another installed Nostr app, excluding Feather and preserving addressable references. Pending zaps keep a shimmering bolt; newly confirmed payments show a 600 ms gold pulse in notes and the gallery.
- Debug and signed release builds passed, including lint/R8. Reviewed package visibility and chooser exclusions, background package resolution, unchanged copy references, lifecycle-paused shimmer, immediate-failure delay and confirmation-only pulse triggers. Packaged debug manifest includes the narrow `nostr:` query. No automated tests, live payments or device interaction checks were run.
- APK SHA-256 `51f407574e6fb5ed30b8ef6c4d321212ef6a914d89a3c5a3abecd3925d7c08e0`; 15,657,765 bytes. Embedded source commit and version verified; signing certificate matches prior releases. Mapping archived locally. GitHub asset digest and the source/tag on both GRASP mirrors verified.
- Signed Zapstore release `a08592092e9b2a77ae22635fb7aada7335acf0ae3cfd84e43bce5df72534127e` and asset `5663e7a0917ce08140665d4ab9903a2d9f54a20f820d806c73e3f2fe2c13f1a8` verified with matching hash, certificate and versionCode; CDN HTTP 200 and matching size. No website deployment as part of this app release.


## 2026-10-15 — 0.61.0 release

- Version 0.61.0 (112), source `7321897`. Android text selection offers Reply for notes, embedded notes and full Markdown bodies. The reply targets the selected text’s event, prefixes each selected line with `>`, and leaves one empty line before the cursor in a new draft. Saved reply text and attachments remain intact.
- Debug and signed release builds passed, including lint/R8. Reviewed Compose SelectionState/context-menu APIs against the installed library sources, per-event selection ownership, multiline formatting, existing-draft preservation, duplicate-leading-quote handling, the 8,000-character bound and unchanged reply signing/thread tags. Formatting and persistence remain off-main. No automated tests or device interaction checks were run; no Android device was connected.
- APK SHA-256 `654234df2ef5c7f0c048f932d991db382ebfaebfac180150a45c7382e2894ca2`; 15,657,765 bytes. Embedded source commit and version verified; signing certificate matches prior releases. Mapping archived locally. GitHub asset digest and the source/tag on both GRASP mirrors verified.
- Signed Zapstore release `784b7ed59259a9fe03f17f8a0c6177ec6b139f5abfc62e66ef88de58ba500070` and asset `f3e2b37e6ba69d01a1c3b0d6dbf1bc9d9aa70eb0b05df95d48fa01c5b5f25ba4` verified with matching hash, certificate and versionCode after retrying an empty relay response; CDN HTTP 200 and matching size. No website deployment as part of this app release.

## 2026-10-15 — 0.62.0 release

- Debug build passed. Shared link hit-testing covers both glyphs adjacent to Android's cursor offset, copies the full destination, preserves normal navigation and yields to movement before a long press. Markdown paragraphs/headings/table cells retain the existing renderer, and reference-style links resolve to their destination before copying.
- Manual emulator interaction confirmed a long press on a wrapped web link shows Android's clipboard preview without navigating, a short tap opens the browser, a swipe starting on the link scrolls, and holding ordinary text still produces selection handles. These checks caught and corrected a hit-test bug on the right half of a character. Article/reference-link integration was reviewed and compiled, without a separate article interaction pass. No automated tests or live payments.
- Version 0.62.0 (113), source `4e0d7ed`. Signed release build including lint/R8 passed. APK SHA-256 `d80339a86ea8937795ab324dcb10376c0e3edbf514de3333bc49e740b3b82062`; 15,657,765 bytes. Embedded commit/version and signing certificate verified, mapping archived locally. GitHub asset digest and both GRASP release refs verified.
- Signed Zapstore release `bb62d861da3efc263220ec0790d365457ffcdf9bc2dbcc740773419bf9d5c904` and asset `09a3b8acc6cc4acb3cd71321ad2a2c852c6ab48ee1938e37f98d0b790e1e9a33` verified with matching hash, versionCode and certificate; CDN HTTP 200. Website unchanged.

## 2026-10-15 — 0.62.1 release

- Debug build passed. Reviewed independent conversation publication, navigation generation/account/content guards, muted/deleted boundaries, cached legacy/NIP-22 joins, bounded membership/memory, atomic coalesced writes and startup retention. Verified the installed Quartz query implementation requires an explicit limit for newest-first pruning order; eviction now uses exact IDs and clears their verification memo entries.
- Manual emulator check opened a public thread, observed its replies and persistent membership, disabled Wi-Fi and mobile data, force-stopped Feather, and reopened the same root. The root and replies rendered from SQLite with no active network; the membership index survived startup pruning. No repository/cache exception or runtime crash was logged. Debug cold startup was slow on the emulator; this is an offline-correctness check, not a measured performance claim. No automated tests or public event publication were performed.
- Version 0.62.1 (114), source `40ca01b`. Signed release build including lint/R8 passed. APK SHA-256 `6fb612b1a186dbb2a4c348aaa6b913353a6694b11e2abf9b8cc8fd1db799807b`; 15,657,765 bytes. Embedded commit/version and signing certificate verified; mapping archived locally. GitHub asset digest and both GRASP source/tag refs verified.
- Signed Zapstore release `f8cf1fc230d53151c3fa8c99703b2d58fb9c51e31cf64d859a63b73458f9c302` and asset `dfe630ff97656175f4a0f87efd67a7de00f6297c49612a960ac09faf07b6a205` verified with matching hash, versionCode and certificate; CDN HTTP 200 and matching size. Website unchanged.


## 2026-10-15 — 0.62.2 release

- Version 0.62.2 (115), source `f856f07`. Replace the login door/arrow with a person-and-key vector in the home account control, account sheet and issue reporter. Keep right alignment, circular button sizes and signer launch behavior.
- Visually inspected a rendering of the exact vector path on the tonal button surface. Signed release build including lint/R8 passed; packaged version, embedded source and existing signing certificate verified. No automated tests or device interaction checks were run.
- APK SHA-256 `577786a43771b8052f6fd8804579ac5921f16691bb7c080396e3049505e004e6`; 15,657,837 bytes. Mapping archived locally. GitHub asset digest and both GRASP source/tag refs verified.
- Signed Zapstore release `423101acfa0a42a44c85530d7e4c7d9b1bb792c841938fb45a005cbf7f075471` and asset `cb4f628e539ad0456a78be6183597fb293039f61aef083f1e2115f031c85c03b` verified with matching hash, versionCode and certificate; CDN HTTP 200 and matching size. Website unchanged.


## 2026-10-15 — Unreleased deleted-note reveal

- Debug build passed. Reviewed deleted roots, ancestor gaps and direct/nested reply placeholders; stable row keys; branch expansion through deleted replies; current mute/sensitive/expiry checks; and exclusion of known-deleted IDs from shared relay lookups.
- Cached text reveals inline behind an eye toggle, with a deleted label and selectable plain text. It has no note actions, link navigation or media loading. The feature does not add content persistence or change retention. No automated tests or device interaction checks were run; no release was requested for this change.

## 2026-10-15 — 0.63.0 release

- Version 0.63.0 (116), source `c9b6c268f05de382220257e7429246a54df044ec`. Deleted-note cached-text reveal and muted/blocked-author placeholders are now released. Muted ancestors and continuations preserve known connections; direct muted replies move to a subdued bottom section. Reveal is per note, keeps sensitive/deleted restrictions, and does not change the mute list or load media. Collapsed direct muted replies do not trigger descendant discovery.
- Signed release build passed, including its standard lint/R8 pipeline. No tests or emulator runs for this release; the user performs device validation. Updated AGENTS.md to retain that workflow. Earlier debug-build evidence for the deleted-note implementation above predates that instruction.
- APK SHA-256 `fd6caa156789cd0f1d126ef667baa01edb49c4690436fd8e6c37a07d8598b316`; 15,674,873 bytes. Packaged version, embedded commit and existing signing certificate verified; mapping archived locally. GitHub asset digest and both GRASP source/tag refs match.
- Signed Zapstore release `c03ce0e9d46c20bc30c3d54414823f9dda78a1d06cf2166d7d08b10a01ea8842` and asset `f9a8d377bec32adfed9b9cd26251b7473f2ee06f39ffe2577fe5a7a0a85faa09` retrieved and verified after retrying an empty asset lookup. Hash, versionCode and certificate match; CDN returns HTTP 200 with matching size. No website deployment.

## Unreleased — shared note deletion handling

- Source review covered signature and author matching, address timestamp cutoffs, startup restoration, retained timeline/pagination copies, embedded originals, muted/deleted thread reveal, and bounded visible-note deletion discovery. Deletion requests are separate new events; original event IDs are not re-fetched.
- No build, tests or emulator runs, per the requested device-validation workflow. Release remains pending an explicit request.

## 2026-10-15 — 0.63.1 release

- Version 0.63.1 (117), source `b193b4083a3b66bbabf845096c97844206a37b09`. Shared verified deletion handling now applies to feed snapshots, pagination anchors, embedded content and stale relay arrivals, with bounded persistent NIP-09 history and visible-note deletion discovery.
- Signed release build passed, including the standard lint/R8 pipeline. No tests or emulator runs; device validation remains with the user.
- APK SHA-256 `e0b43bd2b8f984f364500be232dfa5608ad109a575492ecc7db3286795acd2cb`; 15,674,877 bytes. Packaged version, embedded commit and signing certificate verified; mapping archived locally. GitHub asset digest and both GRASP source/tag refs match.
- Signed Zapstore release `aebf4ea2406608fcb587360a6a91ce4a4848ec3de5466e24c5a0324824545c6b` and asset `f1c7785792a090d5a6be889cb5946c22d8c4587f086aec5f71a146b238c0185c` retrieved and verified after retrying empty asset lookups. Hash, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## Unreleased — zap picker availability and simpler GIF search

- Source review covered shared note/gallery zap controls, bounded per-note/amount queue deduplication, pending-state cleanup, account-change cancellation and unchanged serialized wallet journal handling. Removed GIF suggestion UI, autocomplete effects and keyboard suggestion navigation.
- No build, tests or emulator runs. Awaiting a release request for the signed build; device validation remains with the user.

## 2026-10-15 — 0.63.2 release

- Version 0.63.2 (118), source `62b056b852d8c3a414534642cbf29e492087d52b`. Zap amount selection remains available during a pending payment, with different amounts queued through the existing serialized payment flow. Duplicate pending note/amount pairs remain suppressed. GIF search suggestion pills and autocomplete are removed.
- Signed release build passed, including standard lint/R8. No tests or emulator runs; device validation remains with the user.
- APK SHA-256 `aa78b57d5ecb012e101fe512e71296c96dc93acff7912935b2a0d58596e54ff8`; 15,658,493 bytes. Packaged version, embedded commit and signing certificate verified; mapping archived locally. GitHub asset digest and both GRASP source/tag refs match.
- Signed Zapstore release `d4de69e114d5b8b3f57da39df2ad392d5a24f3dfe1e2d6f49c3f6e860d83d4d6` and asset `6546bd1c61d98be335ee3af0fa3510dff2ca898bbbb73f717a743bacd7d1792c` retrieved and verified after retrying empty asset lookups. Hash, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## Unreleased — audio playback speed

- Added shared widget speed selection and a Media3 media-session custom speed-cycle command up to 3×. Reviewed controller synchronization, trusted-controller command availability and installed Media3 1.11.1 API signatures. Android controls follow the platform media-button preferences mechanism.
- No build, tests or emulator runs; awaiting release request and user device validation.

## 2026-10-15 — 0.64.0 release

- Version 0.64.0 (119), source `c3a86a1495cd0016c79ff983de3fa9c0a7994075`. Podcast discovery includes NIP-73 shares, NIP-22 root/parent scopes and kind-17 likes with bounded author/tag filters. Visible previews support Fountain audio, exact-GUID RSS hints and advertised audio metadata; unresolved references remain links or copyable identifiers. Playback speed reaches 3× in the shared widget/session. Optional public zap messages use signed kind-9734 content; uncertain payments still recover against their original invoice.
- Signed release build passed, including standard lint/R8. No tests or emulator runs. Playback, provider metadata and real zap-message delivery remain for device validation.
- APK SHA-256 `24a4f154cf570ee16b527585e28671adfc1216b4b61f71cb8384446cc7d88e74`; 15,674,873 bytes. Packaged version, embedded commit and signing certificate verified; mapping archived locally. GitHub asset digest and both GRASP source/tag refs match.
- Signed Zapstore release `816b824e875cafa5d1e4bd307e0058b324b6253ff89695a0a93537c5ec387c7a` and asset `bb3378d600c217853556a6de9a825162cad9a874e5a91fa5bbc516e521c7c43a` retrieved with signature verification. Hash, versionCode, size and certificate match; CDN HTTP 200 with matching size. No website deployment.

## Unreleased — compact podcast widget

- Moved artwork beside playback controls, measuring the controls first and giving the artwork the same height in one layout pass. Moved show name beside the duration and external navigation into the title row; removed the separate footer. Voice-note layout and shared playback behavior remain intact.
- Source review only. No build, tests or emulator runs; awaiting a release request and device validation.

## 2026-10-15 — 0.64.1 release

- Version 0.64.1 (120), source `2d9936d7cc26fc64f78914341bc688790ca9af58`. Compact podcast artwork now matches the player height; the show name shares the duration row and external navigation sits at the top right.
- Retraced the reported `visitAncestors called on an unattached node` crash to Material 3 child semantics. Added a narrow build-time attachment guard; inspected the actual transformed dependency bytecode to confirm the early return precedes ancestor traversal. Attached controls retain their original semantics. See COMPOSE_WORKAROUND.md.
- Signed release build passed, including standard lint/R8. No tests or emulator runs. Crash behavior and layout remain for device confirmation.
- APK SHA-256 `4e9101312a9794b2c7cd315a5af4f198749031f28ebdd801605c0fbffc8ef0e9`; 15,674,877 bytes. Packaged version, embedded commit and signing certificate verified; mapping archived locally. GitHub asset digest and both GRASP source/tag refs match.
- Signed Zapstore release `f19cf4b576886fab4953eb27b939476849a17c9a81a75d654016fbdb4bf7a3fa` and asset `b7e99e989e78a72ef3241a4781c5ece78e73a4c8ad08e7b11703b4942944fdbd` retrieved with signature verification. Hash, versionCode, size and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-15 — 0.64.2 release

- Version 0.64.2 (121), source `7b4208e3e9bd8a24264c5aeddbef466f02305188`. Sorted zap amounts occupy at most seven cells in a 3×3 menu, with custom amount and message in the final two cells. Long-pressing the bottom feed selector returns directly to Posts and retains its reading position.
- Initial optimization failed because the development disk was full. Losslessly compressed and verified archived release mappings to recover approximately 6 GB. The subsequent signed release build passed, including lint/R8. No tests or emulators run.
- APK SHA-256 `8cd57a6fad107e07f491b0dc58ef1bfe17e11b00f07b35e10cca27f4c5af75fe`; 15,674,873 bytes. Version, embedded source and signing certificate verified. GitHub asset digest and GitHub/both GRASP release refs match.
- Signed Zapstore release `84a89b519b274231500d079d61e6df6567936b5ae3046d8a1828aa254c304f74` and asset `67503d2dd1062df6b7f01821ba7beaceb9c2ad5464b60b079e25a79efd4a2433` retrieved and signature-verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.
- At the user's request, marked crash issue `80634714` resolved, noting the detached-control fix shipped in 0.64.1; ngit confirmed the resolved (`applied`) status.

## Unreleased — podcast artwork placeholder

- Added a rounded surface with a small, muted image glyph behind podcast artwork, removed when the shared image loader reports success. The placeholder retains the existing cover dimensions and remains on load failure. No extra requests or animation.
- Source review only; no build, tests or emulator runs. Added after the 0.64.2 APK was published.

## 2026-10-16 — 0.64.3 release

- Version 0.64.3 (122), source `23e5027def1db8e284fd41f3d08d7828986f8bda`. Added distinct-author repost counts through bounded existing engagement discovery/cache queries, with muted/deleted filtering and optimistic own-account adjustment. Added a quiet artwork placeholder for podcast covers.
- Signed release build passed, including lint/R8. No tests or emulators run; device validation remains with the user. Removed old local APK copies only after matching their hashes to published GitHub assets; retained recent APKs and compressed crash mappings.
- APK SHA-256 `bc94d33be53e891c8b778fd65c0305d95aee34e72db45e998c1fa87f5f3cb10d`; 15,674,877 bytes. Packaged version, embedded source and signing certificate verified. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `0bea43c7f00e7420da73d9e3343c94a92f4651b01a2b1637ebd2c4fcd0fe2611` and asset `e1956beb854ed611017d4b0c731b1a2118a1589dc6dc7338acb9742ec711f8eb` retrieved with signature verification. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.


## Unreleased — cached startup feed

- Split a bounded cache-only home snapshot from full discovery/enrichment and moved pruning after initial content. Restore follows, mutes and initial author metadata with the same feed filtering; retain cached notes during relay refresh without a top spinner. Explicitly exclude expired timeline events before deferred pruning.
- Preserve up to 120 home posts, cached repost targets, author metadata and current account lists during normal cache pruning. No new event-ID network path or unbounded cache was added.
- Source review only, including account changes and offline initialization. No build, tests or emulator runs; awaiting a release request and device validation. Content never previously cached, or already evicted by older versions, still needs an initial online fetch.

## 2026-10-16 — 0.65.0 release

- Version 0.65.0 (123), source `4400d9d9f081ba6ee0cafeabffe495145cceffd4`. Includes cache-first startup and protected offline home content, plus explicit system audio seek controls, numeric speed icons through 3×, source-note navigation and a heart action through the existing foreground signer flow.
- Reviewed Pocket Casts' [MediaSessionManager](https://github.com/Automattic/pocket-casts-android/blob/main/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/playback/MediaSessionManager.kt) and Android's [MediaSessionService documentation](https://developer.android.com/media/media3/session/background-playback). Used our existing Media3 service and original numeral vectors. The system owns layout; heart actions can require unlocking, with an expiring one-use account/source-bound handoff. No arbitrary external intent can initiate signing.
- Signed release build passed, including lint/R8. No tests or emulator runs. Flight-mode startup, system seek/speed placement, notification-to-note navigation and signer handoff require device confirmation.
- APK SHA-256 `0f1e4216a1c8a1f77fc1780e6efb38d10a3d794940dac73e09b3d35c11283106`; 15,680,877 bytes. Packaged version, embedded commit and signing certificate verified; compressed mapping archived locally. GitHub digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `c9a200d5d6faae342efebc444ebfc15d89a3c7a9f9b52621330d26ac153f00bb` and asset `44c36785849f8c81327a568986c91c4fe4f68633926139ee5b2af4508cce5969` retrieved with signature verification. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.66.0 release

- Version 0.66.0 (124), source `0756759900aa2db920b5fc732b1c7575ae1e6cee`. Initial Japanese localization: 121 standard resource strings for core UI labels, common icon descriptions, selection reply and system audio controls. Android selects `values-ja` automatically, with English in `values/strings.xml` as fallback. Technical diagnostics and some dynamic accessibility descriptions remain English. Future UI strings should use these resources rather than Kotlin literals.
- Media notifications use a monochrome Feather silhouette. All eight speed icons include an x suffix and share numeral scale and stroke weight.
- Signed release build passed including lint/R8 in 1m 20s. No tests or emulator runs. Japanese layout/wording and system icon appearance await device feedback.
- APK SHA-256 `d23f53592c796c02819f1c1dec38f896116c334a1d33bbcfc06b3bd7fb3dcf6b`; 15,739,057 bytes. Version, embedded source commit, v2 signature and certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `a9db9403ed66b4a3d3f5779de93c7a8f44cad3b8460c400d1dbee8d362c75089` and asset `95fcc8d96c8e66edc690670c12bd071586a8bc3f9f54bca763fa91da2445de0e` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN returns HTTP 200. No website deployment.

## 2026-10-16 — 0.66.1 release

- Version 0.66.1 (125), source `cf6682e7d9465aea458b8f219ded3bfc403ece9c`. Full-screen new-post composer shows the draft owner's cached avatar at top right, with localized account accessibility text and the existing initial fallback.
- Signed release build passed including lint/R8 in 1m 18s. No tests or emulator runs; device validation remains with the user.
- APK SHA-256 `3ccf262f8a1590a9ea65a3e29a1d6bc4fa7f15fb341368f091a5b079ae9d3501`; 15,739,521 bytes. Packaged version, embedded source, v2 signature and existing signing certificate verified. Compressed mapping archived. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `39d2d8460d33b2147bd68c6bca2ee2f65412b9ee9cfd0fcfe246ebd341fddfb5` and asset `2007aed847d9d8235da4baa2c509e159df639e83e8eac9146b1d171a93342512` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200. No website deployment.

## 2026-10-16 — 0.67.0 release

- Version 0.67.0 (126), source `a50fca0ecbafc0fde1260ff0f979f23219308fa5`. Guests open signer login directly and cannot access settings. NSFW and PoW settings are account-scoped and synchronized as NIP-44-to-self encrypted NIP-78 kind-30078 events. Explicit edits persist locally; a conflated worker serializes encryption/signing, saves signed events for bounded retries and checks account identity around external operations. Guest defaults reset on logout. Cache budgets and wallet credentials remain device-local.
- Read the official [NIP-78 specification](https://github.com/nostr-protocol/nips/blob/master/78.md). Review covered cached startup, latest-version ordering, local edits during signer requests, retrying the same signed event, logout cancellation and account changes. Relay authentication requirements can prevent synchronization on restrictive relays; publishing failures remain visible. Device verification is still needed for Amber approvals and synchronization between phones.
- Initial build failed due to disk exhaustion. Removed only regenerable intermediate output from three other local Android projects and old Gradle daemon data, retaining APKs/mappings. Final signed build passed including lint/R8 in 36s; no tests or emulators run.
- APK SHA-256 `3f7f2d24ab9c6376578939b842f95416ab16a3fa5dd28eedc305c22bc3965a3c`; 15,755,661 bytes. Version, embedded source, v2 signature and signing certificate verified. Compressed mapping archived. GitHub asset digest and all three mirror source/tag refs match.
- Signed Zapstore release `ec05ffa41db232648129b5cd39fd9db5848d6233f13e36777b559aff881a287e` and asset `570de95bcb743486cb68ff88149c73ea73196295e64708723ddb8e00a36e58fc` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200. No website deployment.

## 2026-10-16 — 0.68.0 release

- Version 0.68.0 (127), source `92dfaf89135099174f61c804e651a8b69f4da350`. First-launch welcome reuses the system splash artwork/bounds, reveals login, drifts the feather down and fades into the feed loading underneath. Welcome completion and contextual hint dismissals persist locally; existing signed-in installs skip the welcome. Deep links/shared media bypass it, and disabled system animations skip the sequence. Compose FABs are hidden for guests on home and profiles.
- Signed release build passed including lint/R8 in 36s. No tests or emulators run. Animation continuity, hint timing and first-launch behavior await device feedback.
- APK SHA-256 `490ada5e23e72d39b8e413549e5ee9a9ed75ac7a9bb1ff3c31332662c301b2c3`; 15,757,721 bytes. Packaged version, embedded source, v2 signature and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `b1f0aecf53e932ccbbbec33a47e0f5544e6dbfa95ac603c2d1ff985a73c238c0` and asset `1262f83f1c8f5ff861e8e6ee7b4b9f7ba0701d19a669fb0e94e9a6a7d3ba5193` retrieved and signatures verified. Hash, size, versionCode and certificate match. No website deployment.

## 2026-10-16 — 0.68.1 release

- Version 0.68.1 (128), source `616d6ad27a180c82e5090985e836202d982ac8e7`. First-launch welcome waits for dismissal of the localized signer hint under login before starting the 2.4-second feather drift. Swipe guidance follows the welcome. Existing completion flags are retained; no replay on upgrade.
- Signed release build passed including lint/R8 in 27s. No tests or emulators run; visual confirmation remains on device.
- APK SHA-256 `f543446ce519e145117bb274ab2bdb5048f3f4866d006cde1e2748a2a29e255d`; 15,758,185 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `fcaaf942db9188873b6e8024dd6a2978859ddb7df55bbfc2c10605a5bb3b2d1a` and asset `bbc0635a0b1c52ecc28ad33d54b50331bce4d0858186851206c746afe111c069` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.68.2 release

- Version 0.68.2 (129), source `33f5ae4714470f995f7462c2fed63c5138131275`. Welcome shows “Connect your signer to log in,” then a swipe hint with the shared feed switcher above the welcome surface. A separate lazy preview pager shows differently colored feathers; swipes do not dismiss the hint or start relay discovery for preview pages. Dismissing the hint settles the preview, reveals the selected real feed underneath, drops the feather over 2.4 seconds and fades the background over 800 ms. Completion flags remain intact across upgrades.
- Signed release build passed including lint/R8 in 25s. No tests or emulators run; device confirmation remains for animation and gesture behavior.
- APK SHA-256 `5bcd0562b7f24b1f87cbf7203ceddd6ee30b966c0fbf7ff055e0a1ef5cde562e`; 15,758,217 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `e8b3a674f083b5b2f18c5bfecf31baaf0214691bee1ef803f71154a5cd9f40a5` and asset `43d55194b0f1be7a00ad57bf33eaa3f985c3dc8135a6f7a5c4e5d3777cb96f47` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.68.3 release

- Version 0.68.3 (130), source `82dc8b0e59e2d0436974442cb6daeaac4eaafe8f`. Muted guest gallery like/zap controls invoke the existing signer-login launcher via an explicit callback, without queuing reactions or payments. Signed-in wallet-less zaps remain disabled. Added English/Japanese login action descriptions.
- Signed release build passed including lint/R8 in 26s. No tests or emulators run; signer handoff remains for device verification.
- APK SHA-256 `757ece90781b53d79c0c0f0b8abf7eed449475bc85d0873736309b1b4c10f039`; 15,759,165 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `91f14afa3c9c62a18797bd0038f74391693429fdaf20fc1e8e0e1fc5361d1bf0` and asset `464590dcabb8d55488f22760e0b9d98550b99a584233266ebb7b43325412c8d4` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.69.0 release

- Version 0.69.0 (131), source `89842d35a5804fd155c99c8d2b8f584dd44d56e8`. Notification filters join the existing encrypted NIP-78 settings payload as stable category names, reusing per-account local persistence and bounded settings publishing. Missing filter fields in older payloads retain the local selection; unknown category names are ignored. Explicit filter edits check the originating account. Detail toolbar order is copy, open in app, share.
- Signed release build passed including lint/R8 in 23s. No tests or emulators run. Cross-device filter synchronization and signer interaction await device verification.
- APK SHA-256 `58b4dc2af67996fd28d5d2a06a3c5ca27e0c3510fa50ae944d7ec426373ad149`; 15,775,545 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `5844087d2e21c2438067d225dff25f6aef44ca2a5901881ae814ef77be659f68` and asset `e0076091dee4f20af186105529a090a142ac8429f3aab14c8b2a892b8f3f593e` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.69.1 release

- Version 0.69.1 (132), source `20eb383c1049377e10c69997ff6fed0d3218ebe5`. Login/feed hints use pointed speech bubbles without close buttons. A non-consuming pointer observer dismisses the active hint on taps, excluding drags, multi-touch and long presses. Tutorial progress survives the login navigation reset in the view model; an incomplete welcome is persisted before signer handoff, and successful login advances to swipe guidance while the feed loads underneath.
- Signed release build passed including lint/R8 in 24s. No tests or emulators run; bubble layout, gestures and signer return flow await device confirmation.
- APK SHA-256 `78fdfe6e77a501b8e6a3ec6253c341f25a0a99239bbc1f3ab6e96ace22e4a5d0`; 15,775,881 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `ddf15a4fea0048f9d0ed89148a8d4bc6a930e4a5c362e52e6e0ee01c9ecd0f61` and asset `a3995ab42f78e38c22f8b241a90a19cbef1d9faf47ef4d37f71c1c324ab40d27` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.69.2 release

- Version 0.69.2 (133), source `8e4bf250b64590f65416a93a33cb94eebbc81884`. Settings adds a matching blue homepage action beside the Feather profile button. Only login/swipe hints remain. Feed discovery no longer scrolls to a potentially stale home selection; stable pager keys preserve the visible category, and selection follows the rendered page key. Welcome preview pages and handoff also use category identity.
- Signed release build passed including lint/R8 in 23s. No tests or emulators run. Repeated swipes during post-login discovery await device verification. Reviewed Android's HorizontalPager documentation confirming key-based scroll retention when pages are inserted.
- APK SHA-256 `373cc034a634e0e919fe571415ee3a446c477c77861a7f523db08499a8f8f2a8`; 15,775,769 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `08cee6ab8541a2cb146b8d37a12cfcd1ed45e3cddd65904369ab3344b4967313` and asset `4a8ddad4befcdd76002f07190552d8071865bcf3ef119c2adc0401c6217e7997` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## Unreleased — tutorial crash after sign-in

- Decoded the 0.69.2 (133) crash with its archived R8 mapping: `xv2.b:14` is `OnboardingKt.WelcomeFlight` line 151, the `pages[index]` key callback. The following frames are Compose's nearest-range key map and pager item provider. This confirms the report of a crash during tutorial sign-in.
- The count callback could observe a larger page list while the retained key callback captured the previous three-item list. Both now read the same updated state; unique fallback keys guard transitional indices. Main-feed keys/content also handle transient out-of-range indices without indexing unsafely. No forced navigation was reintroduced.
- Source review only. No build, tests or emulator run; awaiting a release request.

## 2026-10-16 — 0.69.3 release

- Version 0.69.3 (134), source `d25f15fbff08786cfebe2d6d55f34c6def5961e6`. Ships the mapped tutorial crash fix above: welcome page count/key callbacks share updated state, with guarded indices in welcome/home pagers. Tab ordering is unchanged.
- Signed release build passed including lint/R8 in 23s. No tests or emulators run; tutorial sign-in verification remains on device.
- APK SHA-256 `e7baef86e08d522196045f1902cc9192d86a326b7ac5c224e0e1eeb24250d07c`; 15,775,769 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `f0262f42a6a004a886d8149ffe335b0225923da6d55215868dee85ef8bab83c5` and asset `de1254a6e9f47b30c90ea0542b3ace873ea32f1586f9a7d7103779b50ccfc1c6` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — 0.69.4 release

- Version 0.69.4 (135), source `4ac86af369a6304337755b52825d7446b2453e6b`. Match Android's adaptive-foreground scale when rendering the tutorial feather inside the measured splash icon bounds.
- Signed release build passed including lint/R8 in 23s. No tests or emulators run; visual confirmation remains on device.
- APK SHA-256 `0fde97c53b6c6193dba5f4b27fd3beebf9e23fce15db549d878992d2786b2f32`; 15,775,769 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `c9c68c58a66bfcf920a445a883f800044a231d54bea55b97e83d6baa0ddece98` and asset `4fa9d4a1e46301d5121ac51228c24f4a48ba0437f5bf733334574f8a4fb74535` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## Unreleased — splash handoff flicker

- The exit listener published measured icon bounds and immediately removed the system splash, allowing a previously composed 288dp fallback feather to appear before the measurement reached Compose. Scaling the artwork made this transient size difference more visible.
- Keep the system splash until the visible tutorial feather draws with the captured bounds and measured window origin. Capture geometry per composition so an older frame cannot acknowledge newer measurements. Each activity gets a fresh handoff; removal is posted outside drawing, bypasses completed/skipped onboarding, and has a 1.5s safety bound independent of network loading.
- Source review only. No build, tests or emulator run; startup appearance awaits device confirmation after a release is requested.

## 2026-10-16 — 0.69.5 release

- Version 0.69.5 (136), source `e13d4ad2824ae5cb6207b887b31d6c41bb985605`. Ships the bounded splash-to-tutorial handoff above and recolors only the vector's named body path, preserving its white stem on colored pages.
- Signed release build passed including lint/R8 in 23s. No tests or emulators run; startup continuity and tutorial artwork await device confirmation.
- APK SHA-256 `30b821d2033da0114fe5eda21a9aa03f2057100de01fa8d1e9758b6907efd8ad`; 15,775,789 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and GitHub/both GRASP source/tag refs match.
- Signed Zapstore release `0ae04c12dccd2200e17dcc11f8aaa98a59ac2fc53c13f340d9e25959ad0c5a54` and asset `a778d5cfd4fdb52c92128750ea4099465c0e374def10204b8d0721f193b03e56` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## Unreleased — settings restore and gallery wallet setup

- Settings publication used reaction routes, while discovery used metadata routes restricted to two advertised write relays. Read-only queries found the account's encrypted settings on relay.primal.net but not its first two advertised write relays. The encrypted payload was not decrypted during investigation.
- Share one bounded settings route set between publication and discovery: three stable public fallback relays and the first two advertised read/write relays, reserved within the existing 12-relay pool. Wait for foreground relay access after the signer returns, collect acknowledgements with a bounded timeout, and retain the same signed event for retry. Reopening Settings/Notifications or refreshing Notifications explicitly retries a failed signer/publication attempt without enabling background signer prompts.
- Signed-in gallery taps without NWC open the existing wallet sheet. The gallery host retains its page while covered; logged-out taps retain signer login, and neither setup path queues a payment. Reviewed both gallery hosts, including composer attachment previews without source-note actions.
- Source review only at this stage; no tests or emulator run. The supplied crash issue `80634714` is already resolved (`applied`), as confirmed through ngit; no duplicate status was published.

## 2026-10-16 — 0.69.6 release

- Version 0.69.6 (137), source `282c52aa2e5f2159f01128dc86c6bc93817c8ee9`. Ships consistent encrypted-settings relay routes, notification-page sync retries and gallery wallet setup.
- Signed release build passed in 29s. No tests or emulators run; reinstall restoration and gallery interaction await device confirmation.
- APK SHA-256 `117f806c7b5db0e929c51b57a011e49dcb219c2f51b4e2098a61e00250be669b`; 15,775,785 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `5c845bd0c2482b0397b052a4f78b6a351e5329dc30fb37a4c5c89df56a7affe7` and asset `1fdcf057021e3b80d7fb09070293dc2d9e3d33c72df1404bd7e7c1efe856ffc9` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — image optimization and composer keyboard review

- Large static photos use target-sized decoding and one JPEG encode at quality 85, capped at 2,560 pixels on the longest edge. PNG, lossless/alpha WebP and recognized screenshot filenames retain their dimensions on the lossless path. Container inspection preserves GIF/WebP/APNG animation; optional optimization falls back to the sanitized original when smaller or when decoding exceeds the memory budget. The 20 MiB image upload limit is enforced after processing, within the existing 250 MiB staging bound.
- Reviewed the upload path: processing runs under the existing IO upload mutex, hashing/signing/mirroring use only sanitized output, and temporary candidates are removed. Original/uploaded byte sizes persist with bounded local drafts and are removed with their attachments. Original phone files are never modified.
- Inspected Compose 1.12.1 source: its D-pad preview interceptor calls `moveFocus` before normal text navigation. Canceling Up/Down focus traversal makes that call return false, allowing the built-in text handler to move the caret by line and retain Shift selection. Tab and Ctrl/Cmd+Enter shortcuts remain unchanged.
- Source review only at this stage; no tests or emulator run. Image quality, size captions and Bluetooth keyboard behavior await device confirmation.

## 2026-10-16 — 0.70.0 release

- Version 0.70.0 (138), source `7acaff0abc1aa59e560738ea68b089df69b6916a`. Ships automatic large-photo optimization, persisted attachment size captions and composer Up/Down focus handling.
- Signed release build passed in 28s. No tests or emulators run; image quality and keyboard interaction await device confirmation.
- APK SHA-256 `de44f77526be3c0e2d2ea3405e4e43f0518717c1d090e44b724308e4181769bc`; 15,775,785 bytes. Packaged version, embedded source and signing certificate verified. Compressed mapping archived. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `b82ebff946dbfe876e8274cec8049f83942500853885f8c7b8df46c67a7bbb9e` and asset `4f26d1abcb9568d1b42299e7cdd6a9b1a3ece1c7e90105e77d2f86897521dde1` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-16 — external keyboard website feature

- Added an explicitly requested Native Android feature for external keyboards, addressing mechanical keyboard enthusiasts and listing Ctrl/Cmd+N, Ctrl/Cmd+Enter, arrow movement, Shift selection and Tab navigation. Added a matching keyboard icon and subtle shortcut keycaps.
- Deployed source `88247d0` to feather.dergigi.com with Vercel CLI 64.4.0 after the installed CLI was rejected as outdated. Live HTML and CSS return HTTP 200 and match the local source exactly. No app build, tests or emulator run.

## Unreleased — reader keyboard navigation

- Ctrl+Tab / Ctrl+Shift+Tab cycle the current feed/profile tab list with wrapping; plain Tab remains untouched. Activity-level dispatch handles the chord before text-field focus traversal, ignores key-up/repeat actions, and leaves Cmd+Tab to the system. Reader navigation is gated by the active window, lifecycle, composer and onboarding state.
- Feed requests use category identity and the last requested destination, with one cancellable pager effect. Discovery waits for both scrolling and pending keyboard navigation to settle. Completion resolves the rendered page key, and interruption/disposal clears only the matching request so it cannot replay on return. Existing per-feed list states are reused.
- Ctrl/Cmd+F jumps directly to Search and requests text-field focus only once the page is active. Existing search text is retained on the main feed; opening Search from a profile/detail keeps that reader on the back stack and releases its subscriptions. Reviewed the search-page Back path and separate dialog windows used by composers/settings/gallery.
- Source review only. No build, tests or emulator run; device verification follows the next requested release.

## 2026-10-16 — 0.71.0 release

- Version 0.71.0 (139), source `9d216df86596c752e73f8c028a4101e7743fd61b`. Ships keyboard tab navigation and Ctrl/Cmd+F profile search.
- Signed release build passed in 1m 3s after rebuilding stale buildSrc output. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `2de57c9a50ddcd2ea7cffaef0db95c42fd3d8ded0624a19dea65c1400f7f1892`; 15,792,169 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `c35bd12d9e781425436cd75c7d0c7d0d57fcfc4aa9b2d0234dfa045ed6e8bbf6` and asset `fb36628a1de09383fb33e4ceff10aa9970a389642c50578c7640b0c2ab10743e` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. Website work is a separate explicit request.

## 2026-10-17 — visual website redesign

- Replaced the long feature catalog with a swipeable five-tab phone preview, bounded six-slot GIF sample picker and local draft preview, video gesture controls, keyboard shortcuts and concise supporting features. Cropped public screenshots remain unchanged; no Android status-bar captures were added.
- Video playback starts only on request. GIF videos reuse a fixed pool, pause offscreen/in background, and use stills for reduced motion or autoplay refusal. GIF media remains hosted by nostr.build; no native API key is shipped. Drafts and local attachments stay in the browser. CSP allows only the required local scripts and specific GIF media host; privacy copy records preview requests.
- Reviewed source and a desktop browser rendering; JavaScript syntax and patch whitespace checks passed. No app tests, emulator or app build for this website change.

## 2026-10-17 — visible video playback

- Replaced the screen's single player with a three-player visible window. Paused neighbors prepare through the shared cache and render a first frame; the nearest-center clip starts once after a stable 120ms selection. Started clips retain their timeline while partially visible, including explicit user pauses. Fully offscreen clips release immediately.
- Only one clip can be audible. Fullscreen transfers the selected player's existing surface/timeline, suspends other decoders, and restores their prior intent on return. Background/covered states stop preparation and decoding; active clip URLs are excluded from speculative prefetch. Failures release the affected slot with a bounded cooldown.
- Reviewed player/view ownership, selection changes, viewport clipping, manual pause, fullscreen and lifecycle paths. No tests or emulator run; playback behavior awaits device verification.
- The separately requested website deployment is live; production HTML, CSS, JavaScript and public GIF catalog match the local source over HTTP 200.

## 2026-10-17 — 0.72.0 release

- Version 0.72.0 (140), source `bd847f2c42684c80861793570731b14e0b67903d`. Signed release build passed in 1m 12s. No tests or emulators run. Packaged version, source commit and signing certificate verified; compressed mapping archived.
- APK SHA-256 `9df26ba0555cec77a21b5d072078ad5cf331036ad1be89eee4dd5a7543271df2`; 15,792,173 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `6fea2c658f9d3c49716f0e729c9c82fc1fc9ea48932606fc24d3d543265901c4` and asset `addfae20809180c66bd072f017d11d7a1ccabcfd0daad6a9e1a4f4e35e48449a` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No additional website deployment as part of this app release.

## 2026-10-17 — direct search destinations

- Search submission (IME search, physical Enter/numpad Enter, or search icon) routes a whole hashtag or NIP-19 identifier directly. Unicode hashtags use the reader's tokenizer and lowercase normalization. Nostr parsing runs on Default; relay hints and address coordinates are retained. Ordinary text keeps bounded Vertex profile search, while direct identifiers (including invalid/private-key-shaped input) bypass it.
- Expanded shared naddr decoding to supported addressable kinds, including articles and emoji packs. Unsupported public types retain the existing HTTPS fallback. Navigation reuses existing reader activation and cache-first paths, respects content filters, and retains the Search entry for Back. Added English/Japanese accessible labels and invalid-input feedback.
- Source review only; no tests or emulator run.

## 2026-10-17 — 0.73.0 release

- Version 0.73.0 (141), source `ed60a8cbfb0c243f6216151def35b81ae3639d1e`. Signed release build passed in 56s. No tests or emulators run. Packaged version, source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `10bf7c705046da6faf7e45dccbf1c33ff3f987593cfb81499ad35b79af84102f`; 15,793,285 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `082ea03949462cfc89b8a41f6d6b183e9daa7edfb1eeb9a3a562c12e7404b1fd` and asset `efc63f44d26b9271b51c5c7fca3518eb82b7df384eaeb64d6184ae704f5e6625` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.73.1 release

- Version 0.73.1 (142), source `a8263fc47d099bb0a48c7951cc61d28cf5d9bd81`. Notification previews persist alongside list state on Back; upload size comparisons require an accepted compression result and persist that flag in drafts.
- Signed release build passed in 1m 21s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `c58fb90581deaa05f682ba531988291ef37668a49bdc95f95fa4099ad2b57c2f`; 15,793,281 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `96cd5dea9a4bceeb9e0e0efbf9fe8bd1277f700d482b9d63a6b578f43dc9a8d6` and asset `1c6ec22963a57cb9a4c3a8ddbc4b0cce9d3b29a24d6bfd1ba4d67c4e88e710e0` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.74.0 release

- Version 0.74.0 (143), source `65edc5cbc12245f6ed90c225da449ec3a3760a82`. Reposts group by original event, with distinct reposters and a profile list; attachment thumbnail size captions removed. Grouping runs on Default and keeps stable original-event keys mapped to source reposts for reading/media lookups.
- Signed release build passed in 1m 24s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `bc7860439fdd2ea77fd5fdbed66d7b5852cd7953061cbfd680d1210fb015014d`; 15,794,729 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `7934fe2ddc0f87675f079a2146e0d2ad980e8fc5f34c6b7e536dd646657485c4` and asset `82a36712148d0dc5981d8b308e6b008e78c990729aaf3c2a5923e6f383cf75ad` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.75.0 release

- Version 0.75.0 (144), source `4d6374d3388240dbaa60f8602e8350362fa2ba89`. Home merges posts/reposts by original event with distinct attribution, stable reading order until explicit refresh, preserved thread projection and source-key mappings for cache-first lookups. Generic repost kind hints corrected. Home uses the new birdhouse vector.
- Final signed release build passed in 57s after the refresh-cycle follow-up. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `c67f603c77aeb7c034621442ce00633ff7cd71b381e557673c94b1d797e5d74f`; 15,795,841 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `78e415aef42ff9daf18332686ae40cc9600d41debbe262e608835efde5c879b8` and asset `82b15cf85e10af27a2810256712707d135283c66ffe5214d9183b1805a679e11` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.76.0 release

- Version 0.76.0 (145), source `cf432c4f5f1c088f6e78b7a23ccc27862e19af9e`. Read-only followed-hashtag feed from public/private kind-10015 t tags, with account-scoped signer decryption, bounded cached tags, combined kind-1 queries, paging and standard content filters. No follow edits or deprecated kind-3 merging.
- Reviewed Amethyst InterestListEvent/InterestListState and [NIP-51](https://github.com/nostr-protocol/nips/blob/master/51.md). Amethyst follows hashtags privately by default; this feature narrowly adds own kind-10015 decryption to the documented signer scope. Linked interest sets are not expanded in this first version.
- Signed release build passed in 1m 16s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `e16eeabf55c031216f5b80cf9082ad707f4881a7094f4e7b86bb105b6f68d5a2`; 15,796,409 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `20726aca7f2a845411bc0acd0409ae968791b3d375f26b616423142c6db0572e` and asset `9375a9af76818d1d75c2c6427ecae97c0d3ac78ed621966c4a87198b7f6dacd0` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.77.0 release

- Version 0.77.0 (146), source `16f492b450238b83780590e8530756888b76a1e9`. Home includes followed hashtags with matching-tag badges and original-event deduplication. The # tab ranks a bounded 24-hour relay sample without visible counts; profile tags use loaded posts without a time cutoff. Ranking runs off-main with bounded tags and retained navigation state. Main/profile categories use fixed relative ordering.
- Final signed release build passed in 1m 22s after fixing a missing badge-shape import. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `976b459053a164fefc9fc3c6057a3e50857707ec3c9577672cd3deecd5e29258`; 15,813,301 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `de0f1f1d4123c2699af816668957b3283897a33b520b7bad132a570009ef9873` and asset `259cb8f58992389d45829ef3dff8e993c16cdcb5abb53f060d976543eda2eea4` retrieved and signatures verified. Hash, size, versionCode and certificate match. No website deployment.

## 2026-10-17 — 0.77.1 release

- Version 0.77.1 (147), source `adab7b3f3d765d8a3f382bb1baf98c072b47fa8d`. Retain discovered profile hashtag tabs across refreshes and navigation; do not interpret a bounded sample without hashtags as an empty category.
- Signed release build passed in 1m 18s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `043914e6e121b331bef8c7066f73bf519ff454cfc7e79088d2d20bf3d3f6941a`; 15,813,301 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `715a59ee10f99425795a8a6a2d5742f8e14b9f1ebb5a7d6ccba489df68cd93f1` and asset `c7127a51dd9323b86bfc14a70b781706d51aadb26323f0eb70596d638e62842c` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.77.2 release

- Version 0.77.2 (148), source `f57ed903b187147fbf49f98ab9c0fd90192a4d9a`. Preserve video resume points across feed/detail navigation in a bounded process-local cache; unwatched neighbors do not overwrite progress. Move Hashtags directly before Bookmarks in feed/profile ordering.
- Signed release build passed in 1m 16s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `051309bf3f810154dfb7de183b5c5ed111b2599117a4e8c241154a3d5236a2c4`; 15,813,305 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `5c2fd3854350c13499789a19ed7c55f676ebaaf709e8b6b5e1b0b04b4f535246` and asset `e86b3aff8cd6c762fc888ce885003b3f9e6f07ef387cb71ff668da5b4da323c6` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-17 — 0.77.3 release

- Version 0.77.3 (149), source `833946c80801bf5d082fc41432e03e78f9f002f6`. Profile hashtag feeds apply an author filter to cached results, relay subscriptions, pagination, and restored navigation. Video playback restores mute state and volume with resume positions; prepared neighbors remain silent until playback starts.
- Signed release build passed in 1m 26s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `0cb1cd227754a1a875060bd27f75d9735dacd2e4b3558c5d71f5317bed0c8af5`; 15,813,305 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `b69508508648ad087b8e55a2feb84650102094d263972fbb9cd3e24c74544ddc` and asset `e5eab9355132a96b91523f2105670e212feff29bcee971c57b90604ad21a028e` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.

## 2026-10-18 — 0.77.4 release

- Version 0.77.4 (150), source `a69812e1ec26a38588e23b1d69d33571356612e5`. Wallet pairing failures use copyable diagnostics with stage, relay host, error code, app version and sanitized stack trace. Profile hashtag headers use a localized text-only author title.
- Signed release build passed in 1m 17s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `597e2eb363cabbc9ad04bf96612c27ff3b37bdc292bce94e35b2018adcf0034b`; 15,813,757 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `4763521a9b81fdb19e143ad892b005473c36e681868c449b0042aff60bd1b9e8` and asset `9d203749354dcf21c5448c776daa052df135eeb99a8c374709a9e98104ce6521` retrieved and signatures verified. Hash, size, versionCode and certificate match; CDN HTTP 200 with matching size. No website deployment.


## 2026-10-18 — 0.78.0 release

- Version 0.78.0 (151), source `93c4f3cc19f9b03d07c4d20f258285a99d8ca3fa`. Native NIP-88 polls and votes, retained notification history/previews, Home tap-to-refresh, and clearer NWC certificate failures.
- Signed release build passed in 1m 27s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; compressed mapping archived.
- APK SHA-256 `eed02e6e3cfc17209aaccbe29982d5c5ab08b04ae2fc03c35bee8e6ac4a786d5`; 15,833,381 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `fdb1e16b191107a51ddbb8bd350200aa6399bfb39f7de41407ed62f9796e1a00` and asset `ac16f7fc5e2039366b3c68a566147270513aae52305dfd45ee72cb3e316ab4d7` retrieved and signatures verified. Hash, size, versionCode and certificate match. No website deployment.


## 2026-10-18 — 0.79.0 release

- Version 0.79.0 (152), source `fc15814ad4f9be1465ff6b746bdb25c788d84907`. Poll media/rich text, Primal option-ID compatibility, voter lists, and Zap activity before Highlights.
- Signed release build passed in 1m 20s. No tests or emulators run. Packaged version, embedded source and certificate verified; compressed mapping archived.
- APK SHA-256 `d9a3c0ccb175e2b05dd6d47b393180eb5abd3d2af7d2a3d66d9247d8e27416ed`; 15,833,837 bytes. GitHub asset digest matches.
- Zapstore release `87146d892f2a19d13e96302221746bdf6b4eb034fd3b57a025ce79aa9f94bbad` and asset `a55e6fac958681a26aac41c59dd334b82260a5c9938532fccb693b9bb4fdedcc` retrieved and signatures verified; hash, size and versionCode match. No website deployment.


## 2026-10-18 — 0.79.1 release

- Version 0.79.1 (153), source `d2acbfd9a9893aef75c549f70a724b8c76242a0b`. Per-note retained reaction/repost count evidence, guest Notifications empty state, and direct signer login from the guest compose feather.
- Final signed release build passed in 1m 22s after fixing an unavailable icon and extracting engagement queries to stay within the compiler method-size limit. No tests or emulators run. Packaged version, embedded source and certificate verified; mapping archived.
- APK SHA-256 `64b77966ffb47359dccb1ca32f5a3ad1e0fabd8d426f32d6b3bb8b8e90589c84`; 15,834,337 bytes. GitHub digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `aa9e46d50f66b19509152e98e44cbf3ca3f87da29791853ab33e83e8d6ffe937` and asset `4a26015f9ffe3623c521edf1c80dab064d23fa43308d154df42ed741e0dd934d` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-18 — 0.79.2 release

- Version 0.79.2 (154), source `757fdc5ee6475b976ef1c5121ac51d7ef8fcb55f`. Standalone links become rich preview cards in place; paragraph links remain inline without cards. Failed previews preserve clickable URLs across notes, quotes and polls.
- Signed release build passed in 1m 18s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `5db61b34a85937e8d131d9339197111484780bd3c15c705affba1f6ae53498a3`; 15,834,341 bytes. GitHub digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `19cfc54487e3349d5ced65f4829fd0f26453237cd051ac9f50de72bf03a2d04a` and asset `dadd61d85dab873225427f4a74169bbb0301c46b1dd55c8452baacef9f6df6a4` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-18 — 0.79.3 release

- Version 0.79.3 (155), source `0295f84c4099bb8ff37e8295e28f6faf702544b0`. Paragraph-ending URLs and space-separated trailing link groups become preview cards with introductory text preserved.
- Signed release build passed in 1m 18s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `548cecf068bd6ff3c3d689e1a171170dbd3d94e286d257f54dab8e3c2c57ffd7`; 15,834,341 bytes. GitHub asset digest matches.
- Signed Zapstore release `cbd8998f38d8794d42ca8ac6339f1ede47575dfc0315f40b132baf629f150f50` and asset `58384cffd55b329c6a7cd15900be473460b5d7c728a7e82c81a4f0b774285f68` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.
- GitHub source and release tag initially published directly while the old ngit signer was unavailable. After pairing the dedicated ngit-feather connection, the release tag and latest master were verified on GitHub and both GRASP mirrors.

## 2026-10-18 — 0.80.0 release

- Version 0.80.0 (156), source `c40aa296958e75e9fc819878f37816108934695a`. Automatic podcast identifier resolution with preserved parent context, supplied hints, public Fountain GUID lookup, RSS fallback and cached metadata. Profile Posts remains visible and combines the profile’s posts/reposts using Home’s deduplication.
- Signed release build passed in 1m 21s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `78fe971e117c8a76da7c119b11f2c5be49ccfeb2c6e0d706bd4f495b86751121`; 15,850,721 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `c02ebd713ee536d3b8b77868586e3248d5f426b7cb5f38bb3f0ac168dabdb4ef` and asset `b678a974c9e52302196463da331e3f89f81d1d9e4b3629de98a9d390e70305fa` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.
- Provider research: Fountain’s documented public /v1/search/lookup returned the example episode GUID with its exact parent show and an audio URL. Episode-only identifiers without sufficient feed context retain the fallback card. Feed GUID lookup uses the documented lookup.wherever.audio endpoint.

## 2026-10-19 — 0.80.1 release

- Version 0.80.1 (157), source `8762a39fe9ac11a3ee523f20d00149a93bd3ff87`. Image poll options render as fitted selectable images with checkmarks/percentages and voter thumbnails; reply composers show the posting account avatar.
- Signed release build passed in 1m 9s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `7e33356dedfb0a8ce493d68cb629d6abf5a4dd449b4b791603686f45b5928b17`; 15,851,189 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `09ca2e4a2b558f12c93a40e9a1f3932406307532be2f1cd01e4e9e434f4455ec` and asset `678007ad98876215cd9f20917b4e0ae0be4209e9778b752636915bde73978e67` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.81.0 release

- Version 0.81.0 (158), source `d8b2c0e0a155fc2223d0cecc1d5fa7c8cd868f66`. Profile follow/unfollow with Undo and first-list confirmation; fresh contact/relay discovery around signing, preserved unrelated data, and publication to write plus discovery relays.
- Signed release build passed in 1m 34s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `809d271cd54a0d26c5d0a6202689b3ec7a297041ad62ac929fa3ad50cdd8875f`; 15,855,317 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `657c06bd9225504c85a1b27781c81cbddf7feef44492b93d44d2cbf5ac1cb1e4` and asset `cfa8a8cb2c6de7993f6d992f75a5bc38de51bb5f3017cf519d37d61a534ff31f` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.82.0 release

- Version 0.82.0 (159), source `74710bea5a7d4272921abe1ba575e308ad01e834`. Follow refresh tolerates unavailable relays while requiring two completed responses and fresh evidence of the selected contact event; direct profile-cache restoration; post actions in zap-feed previews.
- Signed release build passed in 1m 20s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `d3ccbe2c1b60dedcb5503f8ea4fb5050c5a08071318c60426274f36c172b7413`; 15,855,321 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `a4f6dfa0d93dccba0cc8794bf772de95edbabff50e4706e96290d9f77ec9ea57` and asset `12169743fe23cff4317a3e1484b3c4a7e39c2e9339633c563c08590b7fd2395a` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.82.1 release

- Version 0.82.1 (160), source `d2781af0746334c60d3cc881f9dd537cb67d833b`. Rolling bounded follow checks with shorter per-relay deadlines, immediate versioned follow indicators, and distinct NIP-05 HTML/JSON/size diagnostics.
- Signed release build passed in 1m 29s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `06f46b762731351d2642a7c1df0e2731b889553614cbbe353dd49bdbdbeb9bd2`; 15,855,321 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `060302bb7c00b03f6647cef62d3acf59a5cbdf67967e9b49ae2883ea634c9502` and asset `b271bfa41a78b6afffbb13702da74c07e0f36874227067afee51c772bdfa1599` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.83.0 release

- Version 0.83.0 (161), source `311fb862c63896de5d7f875c13f3ee4db6e6d499`. Inline biography expand chevron and long-press account avatar shortcut to Notifications.
- Signed release build passed in 1m 22s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `86e26009988d7323b608d4f558b7a15951e5eaf94ffeb0184838cdd2b58e35ec`; 15,872,165 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `bed2789b35cfb63da5212dff3d421e2cac079962e8ce012dbdc1f7be34416e2c` and asset `6473384b59db4c642c741ed16d4eb768b0bd6756404c06c82630d42aab45e408` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.83.1 release

- Version 0.83.1 (162), source `799fca9d31e0a3b74672094a2564ee0b6c888261`. Shared profile rows for People tabs and search, including NIP-05 and follow indicators. Thread muting deferred.
- Signed release build passed in 1m 18s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `5db448deccc3dd6a85a29711d70143c7d006c295097453ca12e287f23049f1da`; 15,872,165 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `f67de4b0cd3dba2135e7dc291fe9c42e8434679d2d317fa34219e0cfee5f3fca` and asset `524aed84a97e6c392131192c9fe22b4e518a148e46048dcb723784f3e187d1dc` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.83.2 release

- Version 0.83.2 (163), source `06045d71ef35e26039b003ac3818a725b626eb23`. Follow actions queue independently per profile and refresh the latest follow list before each edit.
- Signed release build passed in 1m 17s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `b10d709d76b643338be2da21475711729756fe654969098c70ea84a062317e0a`; 15,872,165 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `fe4bcd532f7d1aaf019dc27061714b51c32f215ffcd18d444128f961cb129abc` and asset `78a45bd2632f82b8b093bd1a31fd18e86abf692dba7b05a128fe9f69684ce54c` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.83.3 release

- Version 0.83.3 (164), source `a9d035aa84db1d14185b78c0a36d1b2cd1c92557`. Zap-feed actions inside referenced note cards; account avatar and onboarding control aligned to the feed’s right content edge.
- Signed release build passed in 1m 19s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `eeb6751852c1a43e2e1b535e634eaa94d683f672d1391b128c27b8ba2c896117`; 15,872,165 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `41d41f634091253eb9560f991d1189fe37661780073314d80d27da591543b9d0` and asset `8218c4dfe06a10125c0728ce0c22807393687e50d449616b178c310ba42405c4` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.83.4 release

- Version 0.83.4 (165), source `494003cfd820027e209e479fc20e5e09ce487dfd`. Zap-feed names beside avatars, centered amount, timestamp above recipient, greater entry spacing, and border-independent account avatar alignment.
- Final signed release build passed in 58s. No tests or emulators run. Packaged version, embedded source and signing certificate verified; mapping archived.
- APK SHA-256 `8e51d6f305fa849a442c566ee2fba8328bcfffab5446273ca0662e282711b576`; 15,872,165 bytes. GitHub asset digest and source/tag refs on GitHub and both GRASP mirrors match.
- Signed Zapstore release `9cef4c5b01b28eb2b93ca10d16093a9bda2247a773bf48d0782578e3ed935a54` and asset `06f6680ea0abf59916134aa1dc43acafef2a37e4253196b54c07f4719f1f4fb8` retrieved and verified; hash, size, versionCode and certificate match. CDN HTTP 200 with matching size. No website deployment.

## 2026-10-19 — 0.84.0 release

- Version 0.84.0 (166), source `dc3482e7c668668235cfbcfab8e85afc506b6b39`. Hashtag readers discover profile-style content categories, including related hashtags, and retain per-tab positions and optional author scope.
- Final signed release build passed in 1m 17s after extracting hashtag discovery from the feed snapshot to stay below Kotlin's method-size limit. No tests or emulators run. APK package/version and signing certificate verified; mapping archived.
- APK SHA-256 `77fda889f3d9398bde468563639a5e3637f48583c031ea6337ccc27ca59c3c43`; 15,872,165 bytes. GitHub asset digest matches. Source and tag refs match on GitHub and both GRASP mirrors.
- Zapstore release `a6d5227d5527769d450c397e2009bc78138f427814ce4e844c211963a6dd238d` and asset `474d3f1a9331e3a32a5208dc6a66c5a07e92628dc50b3fbe2b4cef9d3e78b7b2` retrieved; hash, size, versionCode and certificate match. No website deployment.

## 2026-10-19 — 0.85.0 release

- Version 0.85.0 (167), source `766459f8cdfea4c7263c7ac1967bd69a610a84c8`. Hashtag follow/unfollow with fresh NIP-51 interests discovery and private-entry preservation; profile/hashtag tabs attached to the toolbar; profile title shown only after the header name scrolls away; posts/replies at 21-bit PoW and reactions at 12 bits.
- Signed release build passed in 1m 24s after correcting a stale startup-state reference. No tests or emulators run. APK package/version and signing certificate verified; mapping archived.
- APK SHA-256 `96b22bae073aae358a6defaf014b706f223ff0457a2580ada78812980910f5c7`; 15,874,553 bytes. GitHub asset digest matches. Source and tag refs match on GitHub and both GRASP mirrors.
- Zapstore release `d151c88ad70793c8a3ea277fcc393f2e92c3303d7d4d078a3abc19b6a77900a3` and asset `6be316ef1cf834bfd70a9bea893c00b5cfa1656c442b505806bdd0a7e33fa2d1` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200 and matching content length. No website deployment.

## 2026-10-20 — 0.85.1 release

- Version 0.85.1 (168), source `26745a0394f04e6682169feb29e011f852d93596`. Profile tabs return below the profile information and use a sticky list header while browsing.
- Signed release build passed in 1m 18s. No tests or emulators run. APK package/version and signing certificate verified; mapping archived.
- APK SHA-256 `e82c4972011887baa04de73f8b9f7fdbbace419a3159b5ea6cd319da26bd77cb`; 15,890,937 bytes. GitHub asset digest matches. Source and tag refs match on GitHub and both GRASP mirrors.
- Zapstore release `10722363d8d458a1f8efff6ccc413b893044f9487679dede5a487182cd10f0e5` and asset `1c98512e3a024485a35a15826f388bec508eed1b9c505711650c29124e259338` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200 and matching content length. No website deployment.

## 2026-10-20 — 0.85.2 release

- Version 0.85.2 (169), source `91a8b1ee479191101aabf8a205c5faff7c9dacf1`. Profile bars follow scroll distance and Replies follows Posts.
- Signed release build passed in 1m 21s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `4317afc866d461d852ff4141107a4439235d25049712d66fb6a70b64399cacbd`; 15,890,937 bytes. GitHub asset digest matches. ngit push completed for source and tag.
- Zapstore release `0d4a4ed604808540b089fb9e62495d3f5cdbadab488021793991a0c225861bbf` and asset `639ac54fa2c0deea129a3dfef530c5dad290c6a680e3aae5bd273811aa5f9783` retrieved; hash, size, versionCode and certificate match. No website deployment.

## 2026-10-20 — 0.85.3 release

- Version 0.85.3 (170), source `82c214e7d85737e001aaae84a0706256e6a3982d`. Retained per-target reply/zap evidence and successful receipt verification; simplified muted replies with tappable author names.
- Signed release build passed in 1m 20s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `7b06a69c2acb7f63c6f590259f819cb73566ced40284a74d14444b1df11d1c22`; 15,890,409 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `050b5e97d331cc3fd17a579af98fd92f820f31fdcdd3051d3c5a60bd5de84d99` and asset `fbef0e220310f7d0529767800b347da82e7db3ca07969497073c54f5ed6a778d` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.85.4 release

- Version 0.85.4 (171), source `004219069aab7f30af694e274b6a508a930fa3a5`. Scroll-driven hiding throughout reader bars and home controls, with saved fractional visibility.
- Signed release build passed in 1m 17s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `878dd7d2b0ea1b660a2538c595ee39869e2352c0656682f4c529727af6c4bedb`; 15,874,025 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `7acb0a743dc70617b7d0fd0cb93f380526ab62eafbd309d44b7f6b6fdd29f215` and asset `b39db03a7ce809e5c26f35f24e21c4f63c0819e9bb45745081b9564857c97e11` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.85.5 release

- Version 0.85.5 (172), source `79573cd332fb6eeb27a8b016b37893bc5efe1ff8`. Selected hashtag requests precede optional discovery; scoped cache updates display incoming hashtag/profile events independently of the broader feed snapshot.
- Signed release build passed in 1m 23s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `88dac031af442cb394425c7dddf62681791f8f4956372bbc4253c4d6834470b9`; 15,874,025 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `3d7262d2243b33f96f5257445e742a75f222e3fff530511f15d81a7063392bbe` and asset `0beecc8d31c2cae940b740d283853fb507ced5f8d7d49288afae5b5245e4a4a6` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.86.0 release

- Version 0.86.0 (173), source `a218a397bcc46d87e424f2490a122f07cc1306b5`. Existing public/private muted-word filtering and read-only Settings list; thread replies remain individually revealable.
- Signed release build passed in 1m 32s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `3269eedf7a2e9799807b17c53a914c8a3943e65e080b27f84d39ba839c1d9b9b`; 15,891,953 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `0031fdac07a17a9d7bda81749cfccdcc2e74631e75fb04417336192f7c61cec5` and asset `f8d2e386660ac923deb6445ea2a46cfd24ce0978cfd6b6087a87fe8512aa7810` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.87.0 release

- Version 0.87.0 (174), source `71dc834c5af8dff0233d2f0dd646f5847a05a199`. Private muted-word additions and public/private removals with fresh-list guards, plus timestamp long-press copying.
- Final signed release build passed in 1m 5s after adding a legacy mute-set scope guard. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `fb1fd9aa2a1cb5f7521dd6757c27e273f14bf5deb52627319ee4002fe0da40af`; 15,893,353 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `a794789fddbec54cc66b1dc2808edadc0405edb90f86e59a8d49099d9bca62df` and asset `8751bb02a1e5ab987ae59c526e5cc81550d61ef50717d169484be517f1242586` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.88.0 release

- Version 0.88.0 (175), source `e6826ba2bd0a609a7f7c94c8d7a0e6f2f1e506d6`. Local notification text/sender search and optimistic, queued muted-word editing.
- Signed release build passed in 1m 20s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `2318d5e9f1a40e7300ed5897fa073d19e47945f09d18a0ed66d42d948896a1e9`; 15,894,309 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `26c49462b38dd1fa6acc40602f125798dbf3b60b71afaf0c85576c7fda94d613` and asset `a62b3a2cab1e94bc1d24dc3fb48e57376b4e66580091cc3410ef7491ce287801` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.88.1 release

- Version 0.88.1 (176), source `3db05ce524d22009106bbf64fb39a993a3a28266`. Dedicated account-list discovery on launch/login and Settings/own-profile access, immediate private mute state updates, icon-only empty/retry states, and no notification search placeholder.
- Final signed release build passed in 1m 19s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `276d6399c20235eee872ff915f9e3cc125f8e188aa0eb0df9adfea05c2edae96`; 15,894,737 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `cdb3f61194593549c9cdc81db0d6e005d515c72eab2a4a3d78c17582e7df359c` and asset `beb950f6b7865226c38dce1b99a05bf5dd4a5c306e300265f1a26299c74e3219` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.88.2 release

- Version 0.88.2 (177), source `ba610ca28cd8a3394f788eab8311366426aac794`. Publication error messages reopen the failed post/reply draft; the copy icon retains full diagnostics. Muted-word input uses the requested example.
- Signed release build passed in 1m 19s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `908f3789299f7f5b6acb08d0129c12af2a6c5de1d763c4540baaa7c23cdd6a3d`; 15,911,541 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `f87ceb934732a31e24f8e48a125f903d19927998c31c0c784ed73c62b151bef8` and asset `6bbb6f079017cf46d8f0b70cc5294c2fdd67813a13ac6b796fbf9cad67d344d2` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment for this release.

## 2026-10-20 — 0.89.0 release

- Version 0.89.0 (178), source `24dbd2eb6b3185af2bab488f463483fdb49abbe0`. External-keyboard Left/Right navigation in the full-screen image gallery, with picker focus preserved and bounded page animations.
- Signed release build passed in 1m 17s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `f82bf0cdc33a127021e937649a8ee30ac24ca7dd30fd33564a799ef9180d00a2`; 15,911,545 bytes. GitHub asset digest matches. Source and tag push through ngit completed.
- Zapstore release `e016a639cc8f043c9aed80742e384de524447ceb3e79de15f6d293435feb441c` and asset `cdd2638623c68041ce5e1802240aa41463a790ee0962d9a1d8ada2ebf9e3ddfc` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.90.0 release

- Version 0.90.0 (179), source `19b831ec52d93538c0bda4322b76075101228e51`. Video lock-screen/notification playback controls and consistent font-outline speed icons.
- Signed release build passed in 1m 22s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `104f2bb5b3d0c7b969f5ba62646073254cc68bd516dc767994222f95c132b844`; 15,899,588 bytes. GitHub asset digest matches. GitHub and both GRASP mirrors report the release source for master and v0.90.0.
- Zapstore release `121b1a813bc6bc0f17b3bb4306ae1c3cb96f68b1760ec02c49a33058796b85e2` and asset `803e3449254adfce6bd447ad7a3bbb5d9c2edf5e1a5881a570ebe1f4bf2df172` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.90.1 release

- Version 0.90.1 (180), source `1aa3c061b4443850d33a1cbc26929b3fc5c7b3b8`. Video lock-screen artwork uses thumbnails, cached playback frames, author avatars, and a Feather fallback.
- Signed release build passed in 1m 20s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `8512e5df6bcb4d683c86d41d26c2d13274fc488101cfca0912eb65e6107612b3`; 15,915,972 bytes. GitHub asset digest matches. GitHub and both GRASP mirrors report the release source for master and v0.90.1.
- Zapstore release `05ed2f3789d03e019cad4fca84e6c6656c84a73a8cd687f2b0e9e32e1bd74650` and asset `439be993c5ecd5b2ca807462b04cbdac71bbb58f813f8b32515c9fcd35a40a56` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.91.0 release

- Version 0.91.0 (181), source `6c12120da9bbbe2151edb88625fb336575e73006`. Inline long-note chevrons and an icon-first, cache-backed details panel with tappable reaction/repost/zap avatars and observed relay hosts. Reviewed Wisp's ReactionDetailsSection and PostCard for the interaction pattern.
- Final signed release build passed in 1m 24s after replacing unavailable Material icons with existing Feather resources. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `a84fdbcc77d270f1ec6fe1329ce852ba8a05447a1f763c4f8ecf88fbb860d370`; 15,920,156 bytes. GitHub asset digest matches. GitHub and both GRASP mirrors report the release source for master and v0.91.0.
- Zapstore release `b2c4fa8e8f6afa58160ed7a612dc8bb6d63f99230e0a1409b315c6da26e05eac` and asset `3f8f50a7d229dabf1ea442e9296e74e0d36498e6656432c198f26d71d6beb3b7` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-20 — 0.91.1 release

- Version 0.91.1 (182), source `34f0498470af3d49d7f8a781e83f84e1f966769d`. Home starts with Photos and Videos available; note detail avatar cells and row spacing are tighter. Zapstore screenshots are dark-first, feather gallery second, light-last.
- Final signed release build passed in 1m 14s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `e3fe0637a219551026f7fc04ba7a267ea265a8818c7784da1bb49d481314d45a`; 15,920,152 bytes. GitHub asset digest matches. GitHub and both GRASP mirrors report the release source for master and v0.91.1.
- Zapstore release `b7b20d7a311fc4cad2062e091f047f7de87e4b5861ee5553612f7d56c5fc94cc` and asset `beb28fdc8ec54bcc7108961cdb922f961ca308021031a9fc16328ee11bc9a52e` retrieved; hash, size, versionCode and certificate match. CDN HTTP 200. Listing image order matches all 16 local screenshot hashes. No website deployment.


## 2026-10-20 — 0.91.2 release

- Version 0.91.2 (183), source `746015e2b841db2295a9d39069e3b55a389f0bc7`. Earlier cached feed/notification display, retained feed rows, startup notification protection, and resilient snapshot updates.
- Signed release build passed in 1m 24s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `5fd8567b339f7afca15ea038ffa14e625c226a2409938d4f00807ef84ec1808d`; 15,920,156 bytes. GitHub digest matches; GitHub and both GRASP mirrors match the release source and tag.
- Zapstore release `21cbf1cb99c334510510c45b09e7b6ceba3bebd7376a9e868cdc47282fc74b61` and asset `73c4d4e553a4d8c9f764bc38e12f2945b163fdd8b298b38cf1b3f8745354b138` retrieved on retry; publisher, hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.


## 2026-10-20 — 0.92.0 release

- Version 0.92.0 (184), source `66b88d396fce13efc5c0bf4fddc33553256b5a8a`. Persistent observed relay provenance, independent details loading, split recipient displays and NIP-57 payment allocation with per-recipient journals. Visible author metadata takes priority over optional participants.
- Final signed build passed in 1m 6s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `0368b0763d877462e2246b835989a4ad5eeb31fbd935926f139e600033d11780`; 15,937,588 bytes. GitHub digest matches. GitHub and both GRASP mirrors matched release source and tag.
- Zapstore release `920e5fd1b4af92772bef131f698e13d20c5276f24ff51ba5a77a2bdc2b243b5a` and asset `5ac889e262944520de71d7736831e5f786f349721b7487060f6b746fecab35f4` retrieved; publisher, hash, size, versionCode and certificate match. CDN HTTP 200.

## 2026-10-20 — Website NIP-05 identity

- Separate explicit website request: deployed `/.well-known/nostr.json`, mapping `_` to Feather’s public key. Live endpoint returned HTTP 200 without redirects, JSON content type, and `Access-Control-Allow-Origin: *`.

## 2026-10-20 — 0.92.1 release

- Version 0.92.1 (185), source `37bfa565ffe152ebb9739c522d3664d3160b0ec9`. Approved zap split icon combines a bolt and sideways branching arrows with a clear gap, in note details and the zap picker.
- Signed release build passed in 1m 17s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `0a8d5a1ce7b7098aaf81b99064d9b99d43a9732a3fbee92d16b5bc2878733625`; 15,937,812 bytes. GitHub digest matches. GitHub and both GRASP mirrors match the release source and tag.
- Zapstore release `614eb82fece13cfe59fb95405f183a126b20dc42ccabbbf17235fe1ca8ab897c` and asset `97cd77425853e4854ee3e6c739a6e538d03d80e6ea0952f9df0a646a0f445fcb` retrieved; publisher, hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.93.0 release

- Version 0.93.0 (186), source `631e73da00cc6dd0bdbacd0bb474c61fb07c980a`. Local five-event duplicate-reply detection in cached threads, revealable spam placeholders, and declared client names in note details. Own/followed accounts and short messages are exempt; content is retained and no mute events or extra relay requests are made.
- Signed release build passed in 1m 23s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `6bef4f5d42535686b43aa9fc3614edf3556f5cbdd43d3113119355fea6f7e332`; 15,939,412 bytes. GitHub digest matches; GitHub and both GRASP mirrors match source and tag.
- Zapstore release `966733c16cf604f945428afe0cfda440762dc910085fc02819f709cbbeadf691` and asset `3a652a68312088f98d038d930238de957aace74c35f270d03445387e17b87cfa` retrieved; publisher, hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.93.1 release

- Version 0.93.1 (187), source `585082855d5c96988e3c8d2db80a4b7e3ab71d95`. Emoji hashtags in search, note text and publication; client attribution right-aligned beside observed relays.
- Signed release build passed in 1m 18s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `8a492ee796b05af5e5ccd3b0dffb549e8db091038e5736aab30758f85a57d44b`; 15,939,408 bytes. GitHub digest matches; GitHub and both GRASP mirrors match source and tag.
- Zapstore release `5e1bc5ea44b04b53360fd6f8d58619a02b3942996e0cfa9c4b6e6c5bee7fa8b0` and asset `9b026561fe05c152e5a308505b1cddb77b1dccca1c9855ecc9fc7b7fc6b1868e` retrieved; publisher, hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.93.2 release

- Version 0.93.2 (188), source `a6eb65c2545455351594f9188e59e7826360396b`. Bundled logos for 31 clients/publishing apps, muted and positioned after the client name. Explicit aliases and generic fallback; source/asset notices included.
- Signed release build passed in 1m 18s. No tests or emulators run. Package/version and signing certificate verified; mapping archived.
- APK SHA-256 `60f98b3f4f9bec2d56f45c61ec41b68b4ec2cb74b284be1c21bac833ad2e0aed`; 16,176,401 bytes. GitHub digest matches; GitHub and both GRASP mirrors match source and tag.
- Zapstore release `75968ed364e72a14ca4cad723c3e0080f425dcea23405a15bcd444820f8102cf` and asset `5bd0b67ccc41d8e5fd8223dbff9e84530bb6049a173957e3284ab9a6e6c04528` retrieved; publisher, hash, size, versionCode and certificate match. CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.94.0 release

- Version 0.94.0 (189), source `52bae4e5e926dbb91dde4db31610d0605235b297`. Bundled client destinations: 20 native Zapstore listings and 11 official website/repository links, tappable from client names and icons.
- Signed release build passed in 1m 21s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `6dd1e0e9e03adfde70fab0189bec3cea239ef726fcd8f386231d601207be0e92`; 16,193,225 bytes. GitHub asset digest matches; GitHub and both GRASP mirrors carry the release tag at the source commit.
- Zapstore release `3ec0151fcc0c79006ad5cfc74bdd0517d74a9fcd3455cfe4aa5e514de1b8548d` and asset `7d28905d82f5adf8d67c3f5412af67c1d376bc5e0f1fef67793c67a873bcd081` retrieved. Release references the matching asset hash/version; CDN HTTP 200.
- Website interview publication is a separate, explicitly requested change, outside the app release source.

## 2026-10-21 — 0.94.1 release

- Version 0.94.1 (190), source `313016ea6856b5027f96d779a729d2b5b8214f54`. Independent bounded action backlogs; four concurrent social operations and two concurrent zap operations, with per-note ordering and serialized signer requests. Synchronized payment journal operations and reference-counted publication routing. No automatic retry of uncertain payments.
- Signed release build passed in 1m 23s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `849c0ea3958395806625211bf3eb33f65cb7bb57bfc6c90dec0358feb71c4cde`; 16,176,845 bytes. GitHub asset digest matches; release tag pushed to GitHub and both GRASP mirrors.
- Zapstore release `4e13a14db3e916c4960d7f13cc45fb3a4c8aa4249e1d863770bc9c3ed398fa27` references asset `b7e5a82dd90b43b8674f35d19e94e041f8067f22eb9daa70397f80ef0655b013`; matching APK hash, size, versionCode and certificate. CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.94.2 release

- Version 0.94.2 (191), source `b1eb63781caa32efcc2741d914d103454db56246`. Silent block-height failures, date fallback without spinner, secondary Mempool provider and shared failure cooldown. Successful values remain usable if optional disk persistence fails.
- Signed release build passed in 1m 20s. No tests or emulators run. Package/version and expected certificate verified; mapping archived.
- APK SHA-256 `fd1c9ebf2c07378f2a66aa4ba5ad895131050d32c0d225a7eea6a0be1ffbb009`; 16,193,229 bytes. GitHub asset digest matches. Source and tag pushed through ngit.
- Zapstore release `363dd15d364326eb3eed952ec82bb0ce0f12c08ad797957f705f004844d5c216` references asset `5b8937884e99904987039e788e1d1b5c09d2813f123b4477e7021f4ec6ad4545`, with matching hash and versionCode; CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.94.3 release

- Version 0.94.3 (192), source `10b83d3e4ca5c0ea934e4c86c83cb2bf7d869687`. Profile banner beneath a transparent toolbar with floating back/settings controls, readable scrolled name, and tabs pinned below controls.
- Signed release build passed in 1m 22s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `c78c7ab38e2c07ab3fbdd24043a1596d1ddce5e55a7aad77d5f195b5201b5106`; 16,193,229 bytes. GitHub asset digest matches. Source and tag pushed through ngit.
- Zapstore release `67894ddc0821e977bbd4901aa3afd812f9ab593493aa793acbf74ba20a434395` references asset `d7bc5feb66cdc47d60971b0529254bb30055f53dbf3dfc3a6d3be2386562b0c2`; hash, size and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-21 — 0.94.4 release

- Version 0.94.4 (193), source `b51dc2c25568e5667c4045d16b5dbf8ef3a0c2ae`. Restore the exact pre-0.94.3 profile toolbar and sticky-tab implementation.
- Signed release build passed in 1m 19s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `bbb9c9b6ac1693c3e33d73533c0fd6cbf2ae08ff1a1aae00e56edf243c4ebe5a`; 16,193,229 bytes. GitHub asset digest matches. Source and tag pushed through ngit.
- Zapstore release `c46961bad0ea98c9595bed7809c73d809f9f59455bc8bf58844506db00ab9396` references asset `2e4c01828043100428b01497e2311815db601a033f5a5fb901a0ba26a68867e3`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.95.0 release

- Version 0.95.0 (194), source `df348322556ecfed9fdb33e44793bddc83b56c3b`. Notification-row private account muting, settings account list and profile unmute action, plus Copy/Open in another app/Share profile toolbar actions.
- Signed release build passed in 1m 27s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `12eb106989b1f5415970930f56fdf972a45f312b96c2db9ecc49695f81e3eb6a`; 16,196,113 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `46f37fc71f9c12e413e234d2cb6a40a11c3ee0c6354023d5b45a6ad4d0e0896b` references asset `055f6264782f3733f679f93347028210e2e78e50fe6acdace1ffba589304d356`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.95.1 release

- Version 0.95.1 (195), source `436924f1659b7185e76e45b2434cfac4a3b34ffa`. Avatar-sized muted-red notification mute control and muted profiles included in search with a subdued indicator and direct profile navigation.
- Signed release build passed in 1m 20s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `5ad763ed2d0bc7943e17663fbd09af09b04ecaa42c6c153cec79cd3ac4041ef4`; 16,196,545 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `3cb39649f1e3184335710e5a8fef084530a33f68b2df304f6df3090062e1a52b` references asset `3b72bdaf228e1a8f0f8d1de3bd2d6e3e887d5eb1272f78fb4dcf3f21c73da4e0`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.95.2 release

- Version 0.95.2 (196), source `a134ca9f75a6c028832f77a8a3b5c967ba6f2ea1`. Unmute first in the profile action row; compact notification mute control beside the avatar without extra vertical layout padding.
- Signed release build passed in 1m 20s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `7e5cc51076807bfe1b1601808146d651a76e7a8be5d34f51c94f59fca30693dc`; 16,196,549 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `38975b8f160fb1a62de63fa5e01f99c37907564d1a88de814a7886ef38d5c27c` references asset `90316719114115ff5cabb65ce7184180ba79257b17107cdeb8696d5a40893f64`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.96.0 release

- Version 0.96.0 (197), source `b4935232c8462e31b6be22c5ad159d3738a31d8a`. Hold-to-confirm unmuting with a charging/draining ring and consistent subdued red account-mute indicators.
- Signed release build passed in 1m 22s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `c192affd28a5560a9f52f2dbd4fd846e2f7cea5f240798a657952dba70b41715`; 16,196,933 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `627c2132131e0f66f367e1e24cf0db2596eceeb21178dbb5140fec1debc8a61b` references asset `18b7ab9eebb85a69210066e6735a111de9b00ae9aedadeda0d93ede0128e8a51`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.97.0 release

- Version 0.97.0 (198), source `d8322cfd25000ee7a7d30dda20e71970c776b60a`. Explicit public NIP-56 account reports from notification moderation controls.
- Signed release build passed in 1m 26s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `6c907bdc4f551e4cef0b154bc3b57e964a63ceeb7271a8c8fadfeea4910177fa`; 16,220,377 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `31b53ca84369865cfc948cf592ed1ada4ad43e908dff24c4a1970abd459328f2` references asset `124aa7019e41c17836c2ac0e655bfc607b35ce2f8224458fd5ec52f35f0fa091`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.98.0 release

- Version 0.98.0 (199), source `0e8d809527930a5ec191dc036b32bfa840523bf0`. Long-press author avatars to reveal mute and report controls.
- Signed release build passed in 1m 19s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `5f53fd5404b542a784bae4d42398cd060a352e4864fef7c7f578f1200b27007d`; 16,220,377 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `c289847c78089d0ca7594f9257a0495962f28680f04e946789e11b6375926d62` references asset `a1981f8fe88e31ea48020f281caa9522f459262e8a0636cd1c748b584ff58ad2`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.98.1 release

- Version 0.98.1 (200), source `b254b1f31e1039d11618eebb1fb51c653b54abdf`. Report-sheet avatar loading and layout polish; name long-press moderation and tighter avatar spacing.
- Signed release build passed in 1m 19s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `4477f66f22cfd9f9fe23875c33a26a0df0f8fd8f8e5a4f015686f91db7a2c7b9`; 16,220,397 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `49bed25d509cd50862c10c2d5196fcf72abdba0c45e1290dfe84ee5b48cf1787` references asset `b33a24be9c46a1129696d8a47588b726d16e18587f7c231998cc5caf7ec44d4e`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.98.2 release

- Version 0.98.2 (201), source `d0e1426ed36974ef1343a5eefe92734c8b4d1f9e`. Outside-tap moderation dismissal, consolidated profile unmute, signer-handoff timeout fix, and Spam / scam label.
- Signed release build passed in 1m 24s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `ad3686846203e1931c1ea584658b20553ef90f0046ab9da50be3934cf074e007`; 16,220,929 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `c1ed5fe81c3d39e68301c6cb39f649307bab2e10c3271c5a02cc0e459c673afa` references asset `3066d1a2b2ef43ac6a33e24404d36bd0775501c93057d7ac091c38c1d388856d`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-22 — 0.98.3 release

- Version 0.98.3 (202), source `e261cddf88107e0a3d7b3a891ffd0e014728defc`. One-tap profile mute/unmute toggle with a stronger red active state and controls retained after toggling.
- Signed release build passed in 1m 9s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `7960427b6cd4bcbb06d37a48447e159b514e93f920de637b6fb094fa4ce008b2`; 16,221,037 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `8596d09ab88a3ecb2d494ebf7c483b54af6a1ed93b0844a9605257310b460c45` references asset `6dd9fd6367c2f80d2dd0f00b717ac8f2ac549c4df402b606d56e822c3c2643d4`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-23 — 0.98.4 release

- Version 0.98.4 (203), source `edb01c52e2a1cc3ee20c574855a6200d38c84a57`. Tighten ordinary note line spacing from 26sp to 24sp; retain focused-note typography.
- Signed release build passed in 1m 18s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `1dd5d7fbaef419772a4b10f928c0864a3fe9384c7124598a5325c1fec4393f7d`; 16,221,033 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `4fc74233ffdf453c63513135bd844e0bb490936247d95938c6d824b921bf6f4a` references asset `7e0fe407a80d3ef27d130897fb697453483050644993138450ab14eb9d4a1f1b`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-23 — 0.98.5 release

- Version 0.98.5 (204), source `e00da05774527c852c62448077ec73248cc2306a`. Preserve relative media/quote ordering in notes.
- Signed release build passed in 1m 19s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `8480f6949eabdb2062d71b2a127d90bdc0390254ca9a2871a8c723e9a46ffc9d`; 16,221,037 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `f76cfe3311ec4d17a373f6b4a8240a11eee768248b603f88366bb35eae1a646b` references asset `22b528e71892c8b91d8705caadbed1c0d843e6e10435dec6ed5cc8bc01615d4a`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-23 — 0.98.6 release

- Version 0.98.6 (205), source `39deb55806b40609e37222b05425030d6a864fff`. Confirmed reposts queue original-event rebroadcast to the same relays, with two background workers and a bounded queue. Protected events are excluded; failures are silent.
- Signed release build passed in 1m 24s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `6364307fc722de315f364cb632e00adac7563efe6114458f775167fe73cc3d58`; 16,221,037 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `bcd0f3abaff0996b19d70c05b31687b95551a27206e12ecdfba45e237cd3921d` references asset `f9a30a038da35e68349fbc8adce4949fd55142dd6ad170538f5e5bb06bdbe8ae`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-23 — 0.98.7 release

- Version 0.98.7 (206), source `c31f258fbd0ed0737815b292ec61ab6b00b1e21f`. Bundled official Nostrich icon and in-app Zapstore destination.
- Signed release build passed in 1m 15s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `f07d47f57a3c060168f2eb8e0a52bef80e3e800c7be6f74479630cbacc7f5be0`; 16,227,659 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `4c7e756fa6ca263bcc1ac7c203cbf9ec501e2d34bca86a9cd174ea1e60978a04` references asset `bce556bbb08cc4c445be1a097b05cf3de64d3410f710bce7bbd490e57868c332`; hash, size, certificate and versionCode match. CDN HTTP 200. Publication completed after restarting the stale signing request once Amber returned online. No website deployment.

## 2026-10-23 — 0.99.0 release

- Version 0.99.0 (207), source `4348241a15004f6a1f0a9272d41e77511df25e92`. Inline linked audio with existing background playback and system controls.
- Initial release compilation found an R8 non-local-return issue; replaced the early return with a conditional branch. Signed release build then passed in 1m 5s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `324e9b441cf1c86470177f1242745acc1cf6956309f9ffcfaacf2dedd8f52a31`; 16,227,663 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `9cf6e025a1e1de599d5d1d6e72a5239df734da314901f59b6146624ba7c8dbfb` references asset `0e3ee60712ae7b5f5788626d174d110d6841b125fe628ba33db65661c7daf478`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-24 — 0.100.0 release

- Version 0.100.0 (208), source `5256f4d3f38e4fe11df6bac8318154619d16e00a`. Own-note/reply deletion through hold-to-confirm avatar controls; AI slop reporting using NIP-56 other; icons alongside reporting reasons. Release prepared October 23, publication verification completed October 24.
- Signed release build passed in 1m 25s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `9e244d1de831c1768175d86c13880756d73a1ee65d037bd163d03e54cb4cd68b`; 16,231,147 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `12bcd828ecc0a2d956fc2b169b75581791c23fd01a8225f93cf5fcadf4b24b03` references asset `f60964fbf0264181297526992a649e1f8093b7592b670dd0f5ce956b4d7b1041`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-24 — 0.100.1 release

- Version 0.100.1 (209), source `2f858b59e69ff2ded2bb92e6ae2ad3625e33848e`. Halo official client icon and website destination.
- Signed release build passed in 1m 18s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `5c91ab45eb011b8a7544d3b5a933c83264465c7ead1f75bdb5f03bbcf9960a49`; 16,234,897 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `c26b380c4e58d06ebd5d79f54ec665d8a73c5cc59b7fb05ea3bcf6aa991f09e0` references asset `d2973ce46ccf432991cec7bde3c323e6332976d46d037dda3b3ffb1f94e20bc9`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-25 — 1.0.0 release

- Version 1.0.0 (210), source `69ed494abc469e88959b5636a43eeb4ab4096a8a`. Follow/Unfollow in revealed avatar/name actions, including notifications; action-button diameters match avatars. First-list confirmation is handled once by the active page.
- Signed release build passed in 1m 22s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `8daa1037d6e6f70f871ce0f93f9884203cca20e8da2703118b99751b580dba07`; 16,234,889 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag. GitHub CLI network requests timed out; publication completed through the GitHub REST API.
- Zapstore release `4a54fb69476432fe9d778dbd4e0d98d7756562b805859786074ce19fb6b11470` references asset `12c532a051895b4c61b37668dba9f7e7217160435a0c36d21b117d9b02415aac`; hash, size, certificate and versionCode match. CDN HTTP 200. No website deployment.

## 2026-10-25 — 1.1.0 release

- Version 1.1.0 (211), source `9dd3ed6cf79ff51d3f38ec844ddd0651ec1221d2`. Durable signed-post/reply outbox, foreground reconnect retries, immediate local display, flipping muted hourglass, and Follow/Report/Mute action order. Background WorkManager delivery remains deferred.
- Signed release build passed; final rebuild after deletion/reconnect guards passed in 1m 21s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `fd85538e8591781e530ff7e5d74dca11adda7023f7ab787d1771cec977d1d0a8`; 16,252,409 bytes. GitHub asset digest matches. Both GRASP mirrors verified at release source and tag.
- Zapstore release `ebdbc52c0e855dd1f14633c340432dd6a3a00b9b900035c2e2f03617e6c98469` references asset `a4dc359541d729194b2557ac21e9fb90121496c0eff2c8647e822e58a87bbae1`; hash, size, certificate and versionCode match. CDN HTTP 200. Initial publisher exited without a new release; cache-bypassed retry hit a zsp NIP-46 closed-channel panic. The next retry published successfully and was verified from the relay. No website deployment.

## 2026-10-25 — 1.1.1 release

- Version 1.1.1 (212), source `be17c093c5a0af9979a3595cf97b194e3e07fdc4`. Reconcile queued posts with verified relay echoes and saved relay provenance; wake delivery on enqueue/reconnection, with two concurrent deliveries and queued-parent ordering. Keep the hourglass stationary offline and between retries.
- Signed release build passed in 1m 23s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `23571951bb79c1e2399175a887ca5e919ee0b0dca1d15129dad7f5461d06bd9a`; 16,252,409 bytes. GitHub asset digest matches. Both GRASP mirrors verified at the release source and tag.
- Zapstore release `d43e7f14f5972b80a7a47ef00c44b92f7e85bf72e3d6f03a2d48fab0fea6c21a` references asset `e2b64a1d84c89aaa1ee88c92c2b6290f7b34f79663b974a3b6f883ebb03bf53b`; hash, size, certificate and versionCode match. Publication required retries; the CDN initially returned 404 after metadata publication, then returned HTTP 200. Downloaded the complete CDN APK and verified its SHA-256. No website deployment.

## 2026-10-25 — 1.2.0 release

- Version 1.2.0 (213), source `dbcac02b612e21ca4d27974b0264e3fa80d7b9ef`. Add network-constrained Android WorkManager delivery for signed posts/replies, sharing the foreground loop’s two slots and dependency ordering. Pause on logout, recover on login/startup, and close publishing-only connections when the execution window ends. Android battery scheduling and force-stop restrictions still apply.
- Signed release build passed in 1m 28s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `849e976179aa0928f528b600de079235f1c087d5c9d9876dd7d8db52940ec996`; 16,356,106 bytes. GitHub asset digest matches; SHA256SUMS uploaded. GitHub and both GRASP mirrors verified at the release source/tag.
- Zapstore release `04cc942b832177ac5264fc3854d156cf963c9e9c205cbb94c2c476808869cb2e` references asset `df9a2435ac707175851d8f59ccc6fc6e1d58e45c6776e2693d33d853d934d01c`; hash, size, certificate and versionCode match. Publisher reported success before public availability; repeated publication and rebroadcast signed catalog events. Complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.3.0 release

- Version 1.3.0 (214), source `9429a0ccceb807ab3786cd2c7b9fdbcaf0a9b540`. Queue signed reactions, reposts and social undo requests for offline/background delivery; atomically replace queued originals with undo events, preserve optimistic state, and guard journals against late confirmations. Keep two shared delivery slots. Also move note/profile Copy and Open in app into an icon-only overflow beside Share.
- Signed release build passed in 50s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `465d720849e93f96cfedc0fdd27661c4b0a7f26d416df5dd34d54f916530efe2`; 16,372,954 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release source/tag.
- Zapstore release `2cdfc29b2593d61a26fb6e4087792f1e1c73a6f581b4e12caa19e58abba06f39` references asset `02f4b6c387e19253745b135a6ce6578a3537c53fd2a566f4fb91013e10af7a2b`; hash, size, certificate and versionCode match. Interactive publisher uploaded the APK; rebroadcast signed catalog events after the initial read-back returned no release. Complete CDN APK downloaded and SHA-256 verified. No website deployment.


## 2026-10-25 — 1.4.0 release

- Version 1.4.0 (215), source `acbb7769442f9f6d9767ae971e19cea238105d59`. Horizontally draggable navigation pill with bounded flick and snapping; labeled note/profile/gallery overflow actions.
- Signed release build passed in 36s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `e25ad32c0b00fe5b86bcb941e6d0648c15fbbe28479fb8e6a3154fdac3a7c796`; 16,372,954 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `5ff80cb481cc9a60f2632f15cbcb15f7f129a934878d1c491e5a58ecf46fa60a` references asset `5b7684b0f6625e23c4de125c584b9dbd93881e3405d1418f8601f2336c4d76b4`; hash, size, certificate and versionCode match. Interactive APK upload completed; rebroadcast signed catalog events after initial publication was not returned by the relay. Complete CDN APK downloaded and SHA-256 verified. No website deployment.
- Subsequent GitWorkshop issue-sharing and issue-status changes are unreleased and were not part of this build.

## 2026-10-25 — 1.5.0 release

- Version 1.5.0 (216), source `a9bafaa84806c285b4a0273cc9dc9f078ccac8d2`. GitWorkshop issue sharing, status icons in lists/details, and Markdown issue-body Nostr embeds.
- Signed release build passed in 1m 17s after bundling the closed-status icon and moving issue-status calculation/update out of the oversized feed snapshot method. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `89b39207ef7268ca02b2a8dcde6ac097e8dc14e85e0aace56f25b64cece44403`; 16,375,374 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `1f71c3f81385319bcb4c546849c72e2f4c18031379e96a8fa5e33e0bb9ca3aec` references asset `0baca76f1ba38ea0d5078b14ab5f2b5f6e9be91967931b4ae5fe8ecf5d4ff847`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events after the initial read-back found no release. Complete CDN APK downloaded and SHA-256 verified. No website deployment.
- The podcast-player deduplication reported during publication is committed separately and remains unreleased.

## 2026-10-25 — 1.5.1 release

- Version 1.5.1 (217), source `c3fabb35906c3e8c30bfc319c437099ed544a4dc`. Deduplicate podcast players within each note by resolved audio URL, preserving distinct episodes and existing metadata request limits.
- Signed release build passed in 48s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `24c808b728f99583d7f05b9dc2f30e6499bef08e26c54ad36f601a31eba217c8`; 16,375,374 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `8286a361f8f286d3d22567e0121058b44ef93883e2007003a3837912ed64d26f` references asset `0ceedf66a9a8290ed661ea8c1ec84198b07579690b738907b8712b2ee6169172`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events after initial publication was not returned by the relay. Complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.6.0 release

- Version 1.6.0 (218), source `c5470f001212bb4684fff3a4eb382fda5fa75e00`. Native repository issue browser and repository-specific reporting; align report composer controls and show the signed-in avatar.
- Signed release build passed in 41s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `8127537e6f10a88bd4971d7a72d5b73941128864c306375dabaaf01977bd43e3`; 16,395,198 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `5281fbe6d92f85cc801fab1675be0a664b688a8689ca41366ff881844149e922` references asset `b91fda1f85798b1654f0618e4c1b27dafbd4fef05c71a020f068280210af2529`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events after initial publication was not returned by the relay. Complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.6.1 release

- Version 1.6.1 (219), source `3ea2b24784144da0437e9b3e3b24b54f8e8f6249`. Article note embeds and profile mention labels; navigation-pill grace period after paging.
- Signed release build passed in 30s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `4c02bbc0f3d1e8f5f0c9f31f05c1ca2e3cd68d6c7f91edd7d7b45edbd074ff43`; 16,395,202 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `c1e564775e93f446da427491ed318a9e63f7e6ea4e16ebbc80687730c6b79b48` references asset `8a4dccf15f56706a0ffe38c70ae373b52477967b7a83c77a10dc8f832e968e34`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events after initial publication was not returned by the relay. Complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.7.0 release

- Version 1.7.0 (220), source `067760f2cce5e55073b662d21bca462673725dfa`. Offline Translator local AIDL integration for inline note/article translations and selected-passage translation sheets. Separate app and downloaded models required.
- Signed release build passed in 31s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `f9a853955708c1ced423199d6e3924f863bd6a56e592a167b60f4172274ceac4`; 16,416,909 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `a81d8dc982330747b1e72e3d50befd7279e728659dd1380fe301b5080bc01cbc` references asset `abd632e7e0b68646bec6f4f8e5af16af905ecd7b9bb764270bac75ea0e476277`; hash, size, certificate and versionCode match. Retried a broken-pipe upload and transient CDN 404; rebroadcast signed catalog events. Complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.7.1 release

- Version 1.7.1 (221), source `3420e754f5809806f41624d0e7075c8548b6a6f5`. Translation control left of timestamp; bug/feature icon beside the repository name in the composer.
- Signed release build passed in 29s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `8b052dbb5423e41abda4cf194360114698669bc83d3f8dd855f3231be68d9e34`; 16,416,909 bytes. GitHub upload required a network retry; asset digest matches and SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag after retrying a relay timeout.
- Zapstore release `f160d5fdd98968cec0a16dd5cd8cd4bd605a34d787cb9b73530548903c3e4961` references asset `71fdf4c27066794841c513ffc7ed0afa8ca4652f71cd17a6551651c529affc05`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events. Complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.7.2 release

- Release source/tag `d9b8a65d1050b7c07d598af9b9bf30c6c6146240`; versionCode 222.
- Signed release build passed in 28s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `d4e2b81bdbe6f0239e371fff790a2395fc4972147f20851c7029ff3945d0b705`; 16,416,909 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `746ed50c2b33932fe47552fa48ba014f8ea78e3a7b6f6d8ee7e33220b3020fbe` references asset `223922c4f386cc322c69813f6f2e21edac6d2a7da806265a911f70cdb6d9d7e5`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-25 — 1.8.0 release

- Release source/tag `a70d5cf3b135a33e3d06193c2026820f0bb8bf13`; versionCode 223.
- Signed release build passed in 33s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `65619157029a07984819cd200a0c8033b61dc4f37c973d58aac8da6984e3b21e`; 16,418,677 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `4c8f934ca2791d335d8de4d36ef43b28085d8c288811c730df998d27caa80691` references asset `6d2c76e0ffc4c9ed5a2ca3fe76bc20dfff17adf0d492d8e2be599527ea382ecc`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.
- Save-all-images icon fix was committed after this APK was published and remains unreleased.

## 2026-10-26 — 1.8.1 release

- Release source/tag `edbfa70b8fba376b45c1a648b6ca8d3e73742e97`; versionCode 224.
- Signed release build passed in 29s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `c805a6d310556a514714392f5793f14e6486cf50d2c9b05739d0c6b54b618813`; 16,418,877 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `8f45f97e582517b2705e221ee638381bf936e0aaee80c1b28a47fb636d8a9575` references asset `2884ce5bc4e0cedda1a86dd9350afb20c428d4293192bb6e8bacd76885f6d009`; hash, size, certificate and versionCode match. Retried a broken-pipe upload with the same APK, rebroadcast signed catalog events, then downloaded and SHA-256 verified the complete CDN APK. No website deployment.

## 2026-10-26 — 1.8.2 release

- Release source/tag `f3bb794990834c25c8d7fdfb0495956da0895175`; versionCode 225.
- Signed release build passed in 28s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `d2bffe0432b86b0057932287734b764332648098cf05c3ea01682256cc3036f7`; 16,418,877 bytes. GitHub upload required a broken-pipe retry; asset digest matches and SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `bc558ce71a6dfa1ba6bda66c4c97003d0a99e55f3082ca6b4225c541e810a1ad` references asset `2b710c74afd7b630c1c4541452237f0d7a9e73bf618e1af01500cdd943a01de3`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-26 — 1.8.3 release

- Release source/tag `6841350ac76ffd4093dccfaabe906ae4e3ca27cf`; versionCode 226.
- Signed release build passed in 1m 24s after correcting a callback return type caught by compilation. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `c8608e0225dbf74fece1e29284d3ae96aab72639704706a9b0e8ff32d3fadcf8`; 16,418,877 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `32c0e3e742cb69e537e47a292657c54f69340010c494cb567b58abbc3bbe4417` references asset `c2478e524bbe8b5f86a6da893e1c0b362b59cc2ecbfd3c38ecd4db22b5e2e3d9`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-27 — 1.8.4 release

- Release source/tag `247c29f82dcd2153f1858d72ac3715c520c51068`; versionCode 227.
- Signed release build passed in 1m 30s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `13ac050bf31a8b2cb1908154e465908d0ea6f9d073feb227203f9fd7a66b184d`; 16,419,353 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag after retrying a relay timeout.
- Zapstore release `011ccb897641c6a02d89a8b8b63db943336ddee5e15cd4f5f3b84c9b3817b967` references asset `84d9f8d53033430f832426e93a95fa539e26d6c1bca5a8cf7d83f60c92e59bd4`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-27 — 1.8.5 release

- Release source/tag `5a7a252e51c6cc24fcaee77869a287167abed8d4`; versionCode 228.
- Signed release build passed in 1m 21s. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `dbe0333859c0c9b9962e3c9327a497c73879a1bcdaebb2e629657559fc0949bc`; 16,419,237 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `c4439caff7435c79a5d0f50f69f2669eb05a3031b54ee3758ab39be784f494b7` references asset `98da0ed654190da60af7fa44b106807e526e27bad53134d123dbfef5069b9a5d`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.

## 2026-10-30 — 1.8.6 release

- Release source/tag `0d8fcb13ef37dbc1f7316642ea42884ff72d1a6a`; versionCode 229.
- Signed release build passed in 1m 27s after correcting Android-context references caught by compilation. No tests or emulators run. Package/version and expected signing certificate verified; mapping archived.
- APK SHA-256 `a1529b64909a2a2af584e104daa8be8dec8f8c03fe546385877f8f8a183fa9aa`; 16,419,237 bytes. GitHub asset digest matches; SHA256SUMS uploaded. Both GRASP mirrors verified at the release tag.
- Zapstore release `01d2fd2fe6f48157cede4f65aad9f105cfb11625ea07529a9838df2024ebf8e4` references asset `7200d286c29ecd29d1edce75875715ebc5fb773806c61e93eb81bdcf6c41e2d4`; hash, size, certificate and versionCode match. Rebroadcast signed catalog events; complete CDN APK downloaded and SHA-256 verified. No website deployment.
