# Changelog

## v0.9.9 build84 performance-fix2

- Restore operator display in Network Info card and status chip.
- Migrate legacy saved 128 CPS to 1000 CPS for the performance release.
- Change session open core from 100ms small-batch-await mode to high-speed batch window mode.
- Keep session chart as session-count-only blue line; CPS remains text-only.
- Keep Ping chart refresh enabled.
- Add failure interval summary below the session chart.
- Keep FD protection to avoid Android FD/socket-limit crashes.


## v0.9.9 build84 performance-final

- 右下角卡片保持 NAT置信度，底部标签显示运营商。
- 固定目标 CPS，取消 128 动态 CPS，默认起步 200/s。
- FD 硬保护保持 32360 左右：0 失败跑到安全线立即释放。
- 恢复按会话数分段失败硬终止：0-1000=120，1000-6000=200，6000-12000=300，12000+=600。
- 仅低会话阶段保留无增长终止；中高会话不再附加增长判断，避免误杀峰值。
