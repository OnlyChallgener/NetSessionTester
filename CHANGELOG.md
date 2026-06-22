# Changelog

## v0.9.9 build79 local

- Optimize manual CPS execution and pending handling.
- Keep failure count capped at 300.
- Keep TCP timeout fixed at 3000ms.
- Reduce high-FD UI and cleanup stutter.
- Cache max open files and avoid heavy socket-list cleanup during active-count snapshots.

## v0.9.9 build78 local

- Cap failure count at 300.
- Fix TCP timeout at 3000ms.
- Keep manual CPS priority and dynamic FD protection.
