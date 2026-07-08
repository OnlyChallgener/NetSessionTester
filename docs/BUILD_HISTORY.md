# Build History

This file keeps short historical notes for local self-test and release-fix builds.
Formal user-facing release notes stay in `CHANGELOG.md`; current validation notes stay in `TEST_NOTES_current.md`.

## v0.9.9 local self-test line

- build65: added "ignore this version" behavior for update prompts and fixed bottom navigation text clipping.
- build66: replaced fixed FD guards with dynamic `/proc/self/fd` and `/proc/self/limits` checks, with staged warning/protection/hard-stop thresholds.
- build67: strengthened public STUN NAT detection by reusing a UDP socket, matching transaction IDs, running two validation rounds, and labeling filtering as multi-node inference.
- build68: added failed-download retry controls, cancel/background download actions, manual CPS limit behavior, and a CPS right-axis session chart.

## V1.1.14 self-test line

- build116: added MTU detection, roaming test, NSLookup DNS switching, NAT shortcut navigation, and rounded shadow fixes.
- build117: refined MTU/PMTU, WiFi roaming charts, roaming summary cards, and rounded Surface click states.
- build118: added roaming network-event listening, roaming history, stop-time summary saving, chart padding, and sampling presets.
- build119: reorganized MTU detection into local MTU, ICMP path, TCP business reachability, and reserved application-layer PLPMTUD.
- build120: validated chart axis padding, simplified MTU modes, and removed square click-state artifacts from several shortcut controls.
- build121: replaced bottom navigation and multiple filter/mode controls with consistent rounded click areas.

## V1.1.15 release and self-test line

- build122: released latency-card refresh fixes so home refresh uses lightweight latency probing while NAT remains a manual diagnostic.
- build122 compile fix: fixed a Kotlin ping latency regex escape issue and clarified ConnectivityManager event labels.
- build123: preserved network-info expansion and scroll position when returning from NSLookup, Tracket, MTU, and roaming pages.
- build124: rebuilt the Ping chart as a latency waveform with automatic high/low frequency display, loss markers, drag history, and tap details.
- build125: tightened Ping stats and chart state reset, added jitter/duration cards, and fixed stale "live" status after stop.
- build126: made connection-count testing the primary control, synchronized Ping monitoring, improved low-latency Y-axis ranges, and moved Ping history to a secondary page.
- build127: capped Ping history, added delete/count/storage display, synchronized connection-test Ping targets, and improved local low-latency chart scaling.
- build128: added TCP Socket Ping for high-frequency probing, fixed infinite high-frequency Ping behavior, and improved ICMP streaming fallback.
- build144: separated internal/external packet loss display, added roaming quality scoring, refined roaming chart axes and AP switch lines, and clarified connection-count trend charts.
- build145: cleaned root historical notes into `docs/`, added Tracket pause/resume/cancel handling, and tightened route-trace process cleanup.
