# NetSessionTester v0.9.9 build88

- versionCode = 88
- versionName = v0.9.9
- 改为流水线持续发射，避免整批等待慢连接导致 9000-10000 区间卡住。
- 6000 以上会话失败上限改为 360。
- 继续取消失败小曲线、CPS曲线和 3s/5s 无增长终止。
- 保留 FD 32360 保护、Ping 图表刷新、顶部下载横幅和平均CPS显示。
