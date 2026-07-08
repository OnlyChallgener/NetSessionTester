# NetSessionTester

NetSessionTester is an Android network test utility for connection-count testing, Ping monitoring, NAT diagnostics, NSLookup, MTU checks, WiFi roaming observation, and Tracket route tracing.

Current release candidate: `V1.1.15 build145`.

## Release Notes Layout

- `CHANGELOG.md`: formal version change log for released or release-candidate builds.
- `TEST_NOTES_current.md`: current build validation checklist.
- `docs/BUILD_HISTORY.md`: compact timeline of older local self-test and release-fix builds.
- `docs/RELEASE_TEMPLATE.md`: template for future release preparation.

Do not add new root-level files such as `README_selftest_buildXXX.md` or `TEST_NOTES_vX_buildXXX.md`. Add current validation details to `TEST_NOTES_current.md`, and fold older build summaries into `docs/BUILD_HISTORY.md` after the build is no longer current.

## Build Notes

- Package name and signing configuration are unchanged.
- `versionCode` is managed in `app/build.gradle.kts`.
- Public update metadata is managed in `update.json`.
- This checkout may not include a Gradle wrapper; use Android Studio or the configured CI build environment when local Gradle is unavailable.
