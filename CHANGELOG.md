# Changelog

## v0.9.9-test89

Self-test build, not for public release.

- versionCode 89, versionName v0.9.9-test89.
- Kept update.json at build88 to avoid unpublished release download 404.
- Corrected failure tiers: <1000=120, <6000=200, <12000=360, >=12000=600.
- Added soft failure confirmation for 6000-12000 tier to investigate 9000-10000 plateau.
- Expanded pending connect window to targetCps * 8, capped at 16000, still constrained by FD budget.
- Kept session chart simple: no CPS curve, no failure curve, no 3s/5s growth timeout.
