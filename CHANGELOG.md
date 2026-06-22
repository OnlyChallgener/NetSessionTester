# Changelog

## v0.9.9 build84 Performance Release - Ping 保留版

- versionCode = 84
- versionName = v0.9.9
- 固定目标 CPS，取消 128 动态 CPS / 自适应调速。
- 100ms 分片发射，减少卡顿和调度堆积。
- 取消 CPS 曲线，主图只保留会话数蓝线。
- 保留 Ping 图表刷新，采用独立协程和秒级聚合。
- 总计统一为成功 + 失败。
- 网络信息卡片将运营商恢复为 NAT 置信度。
- 保留 FD / 失败兜底保护，避免接近 32500 FD 后闪退。
