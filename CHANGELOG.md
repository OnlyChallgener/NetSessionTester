# Changelog

## v0.9.9 build 81 local

- Restore high-concurrency TCP connect core closer to the v0.9.8 behavior.
- Use a dedicated high-parallelism connect dispatcher to avoid default Dispatchers.IO throttling during timeout/failure bursts.
- Keep fixed 3000ms TCP timeout.
- Keep segmented failure limits: <1000=120, <6000=200, <12000=360, >=12000=600.
- Keep manual CPS priority: ordinary failure-rate/growth checks do not silently lower the user target CPS.
- Keep FD hard protection and release UI.
