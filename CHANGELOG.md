# NetSessionTester v0.9.9 build87

- versionCode = 87
- versionName = v0.9.9
- 取消失败数小曲线，只保留会话数蓝线与失败区间文字。
- 取消低会话 3s/5s 无增长终止，收尾只看失败分段阈值与 FD 保护。
- 已释放后 CPS 卡片改为平均CPS，避免显示最后时刻 0/s。
- 保留固定目标CPS、Ping图表刷新、失败兜底600、FD 32360保护。
