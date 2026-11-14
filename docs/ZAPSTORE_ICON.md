# Zapstore catalog icon mismatch — 2026-11-14

Feather 1.10.9 (242) is published, but Zapstore's catalog still distributes an unrelated purple bird as its icon. This is reproducible without the Android client.

## Evidence

- Signed application event: `7b72f5adc3c8ae22c23365990e95418cf5c0a38ffa9ffde461614ffcacb8196f`.
- Its `icon` URL is `https://cdn.zapstore.dev/5a549269d47ed441f1e6346106171b1023966b146ed7931d875cc1ff0428263f.webp`. Download verified against its SHA-256; visually correct blue feather and white stem.
- Release event: `633f793b04855f12f6efd0125dc9975551700d18c45703504c79bfd6eb11ea16`.
- APK asset: `e7c737ba69a76ac324ceb5e403482db390c62a181f1dd2a654a40963114fd071`. Complete CDN download matches `39eaf837f4b3d7fb6aa1de3092612beba5fa0d7a0a7203f44e51a0c933f5ca1a`. zsp 2.0.1 extraction matches the canonical 192px launcher bitmap pixel for pixel.
- The catalog delta `GET https://relay.zapstore.dev/v1/bundle?from=1078` (to epoch 1079) contains the new application event and release **and** `org.dergigi.feather/icon.webp` with SHA-256 `3e6ed5fd4354fb44a3de9964864f3dac31af4ab637c6c83d1590549d619247ac`. That image is the purple bird, identical to epoch 1078's artifact.
- The public app page likewise shows 1.10.9 with the purple bird.

## What is established

The new metadata and APK reached the catalog. Changing the image URL and releasing a new APK did not replace the processed image. This is not solely a stale Android or browser cache. The earlier APK-extractor problem is fixed; the remaining failure is in the server-produced artifact. Its precise production cause is not established without server logs/state.

## Upstream investigation

Inspect the stored icon bytes and `icon_from` memo for `org.dergigi.feather`, the processing report for the release above, and failures downloading the icon during admission. Compare the deployed processor with the public source:

- [Image refresh decision](https://github.com/zapstore/steroid/blob/main/internal/catalog/decide.go)
- [Image fetch and storage](https://github.com/zapstore/steroid/blob/main/internal/catalog/enrich.go)
- [Images-only repair](https://github.com/zapstore/steroid/blob/main/internal/catalog/images.go)
- [Catalog artifact selection](https://github.com/zapstore/relay/blob/main/pkg/catalog/artifacts.go)

The operator has `steroid --images-only org.dergigi.feather` and forced enrichment available. A repaired icon must also reach a new catalog snapshot/delta; changing only the stored row does not establish that Android clients receive it. No upstream report or operator command has been sent/run on the server.
