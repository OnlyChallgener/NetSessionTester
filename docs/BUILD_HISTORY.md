# Build History

This file keeps short historical notes for local self-test and release-fix builds.
Formal user-facing release notes stay in `CHANGELOG.md`; current validation notes stay in `TEST_NOTES_current.md`.

## v1.0.22 release line

- build157: multiplatform unification and Compose Desktop overhaul. Implemented desktop 4-Tab top navigation (Concurrent Session Hold, Standalone TCP Ping, Underload Ping, Test History), dual-axis tight auto-range charts with zoom and pan gestures, follow-cursor tooltip with dynamic viewport boundary clamping, 8-metric dashboard grid, DesktopUpdateDialog with changelog modal, default failure limit adjusted to 200 across platforms, animated connection release progress, WAF/anti-DDoS detection heuristics, iOS Liquid Glass native tab bar via Apple UIKit, and robust lifecycle cancellation handling.

## v1.0.21 release line

- build156: uncap 2,000 CPS limitation (broadened to 20,000 CPS), expanded IO dispatcher parallelism from 256 to 512, scaled maxPending window to 20,000, and adapted in-flight failure guard for high-CPS launching. Archived self-test items: 5000 CPS target launch verification, smooth token issuance without early in-flight drop, connection trend charts, 4-Tab bottom navigation, independent tools center (Bufferbloat, iPerf3, WiFi roaming), RFC5780 NAT manual diagnosis, secondary clear confirmations with 10s undo, Tracket pause/resume/cancel, and Android 10~16 permission behavior.
- build155: official release of v1.0.21. Stabilized 4-Tab architecture with dedicated Tools center, recalibrated WiFi roaming under Performance, removed redundant Dual-Net, updated iPerf3 default targets to 192.168.5.1, fixed connection test crash/stall and missing chart line when failing/finishing, fortified stop button lifecycle, and unified line chart terminology.
- build154: beta4 testing build with 4-Tab bottom navigation, Tools center layout, public IPv4 detection overhaul, and modal target selectors.

## v1.0.21 beta line

- build153: 4-Tab bottom navigation with dedicated Tools center, slimmed-down Settings, accelerated domestic public IPv4 detection, manual NAT STUN presets, and target selection modals for Bufferbloat, DualNet, and iPerf3.
- build152: added 7-day history storage for Bufferbloat, Dual Network, and iPerf3 with daily folding and swipe-to-delete; fixed iPerf3 layout truncation; optimized NAT with domestic STUN pool and Double NAT/CGNAT/UPnP detection; optimized MTU with heuristic fast search, TCP MSS cross-validation, and gaming console advice.
- build151: added Dual Network (5G vs WiFi) concurrent real-time comparison test with Network.bindSocket and built-in iPerf3 bandwidth throughput testing client.
- build150: introduced Bufferbloat rating diagnostic with unloaded/loaded queue delay evaluation and 5G/4G cellular RF diagnostics for base station frequency bands and signal parameters.

## v1.0.20 release line

- build149: official release of v1.0.20 containing thread isolation for connection tester and ping monitor, 2KB socket buffer optimization with SO_REUSEADDR, channel-based result collection, and smoothed token scheduling.
- build148: verified thread isolation, minimal socket buffers, and channel collection in self-test.

## v1.0.18 release line

- build147: unified the visible version number, refreshed the in-app recent-version list, separated STUN text editing gestures from card deletion, and completed NAT/roaming history fixes.

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
