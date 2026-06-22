# Changelog

## v0.9.9 build77 local

- Fix manual CPS priority so user-set CPS is not silently throttled by ordinary protection logic.
- Stop low-capacity tests earlier instead of dragging through low CPS confirmation.
- Stop near FD limit directly in manual CPS mode to avoid long tails.
- Throttle UI stats snapshots to reduce release and IPv4/IPv6 transition jank.
