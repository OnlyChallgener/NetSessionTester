# Changelog

## v0.9.9-test91

- Fixed target CPS scheduling: target CPS is no longer treated as per-tick batch size.
- Re-enabled scheduler interval setting in the session parameters UI.
- Replaced “智能调速” running label with fixed CPS wording.
- Throttled stats/UI updates to 1 second to avoid flickering session counts.
- Kept ping chart refresh, FD 32360 protection, and failure interval text.
