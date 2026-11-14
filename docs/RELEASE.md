# Release checklist

A release is complete when both the signed GitHub APK and Zapstore publication exist.

- Version name follows SemVer; Android versionCode always increases.
- Keep a Changelog entry includes date, user-visible changes, and important limitations.
- Build the optimized signed release APK. Run additional checks only when requested or needed to investigate a concrete issue.
- Commit the release source and version before the final build so its embedded Git commit links to that exact source. Record verification evidence afterward.
- Verify APK signature using Android build-tools `apksigner verify --verbose --print-certs`.
- Commit with Conventional Commits, tag `vX.Y.Z`, push master and tag.
- Push through the Nostr `origin` remote to update the GRASP copies and listed GitHub mirror; see [the repository workflow](NGIT.md). Verify both GRASP copies and GitHub have the release commit and tag. Use the `github` remote explicitly when a GitHub-only operation is intended.
- Publish signed APK plus SHA256SUMS to GitHub.
- Use zsp 2.x. Run `scripts/zapstore-publish.sh` after the GitHub release is published, or pass an existing signed APK as the first argument. The script never builds an APK.
- zsp reads the ignored, owner-only `.env` remote-signer connection and uses the existing NIP-C1 claim; it does not read APK keystore credentials. Claim setup/renewal is separate from publication.
- To refresh metadata for an existing release, pass `--overwrite-release --overwrite-app-event`. Check the returned JSON status and verify the catalog listing.

Do not update or deploy the website as part of app releases. Keep its links pointed at `https://github.com/dergigi/feather/releases/latest` and `https://zapstore.dev/apps/org.dergigi.feather`, without a version label. Further website work is deferred until explicitly requested.

Use the same publisher identity and local upload signing configuration as boris-android, as requested. Signing secrets are never tracked. The public certificate fingerprint is recorded with each release's verification evidence.
