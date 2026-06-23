# v0.9.9-test93 自测说明

目标：验证非阻塞流水线发射是否提升高 CPS 性能，并改善释放速度。

关键变化：
- 去掉 openBatch/awaitAll 整批等待。
- Token Bucket 按真实时间补发固定 CPS。
- pending 窗口 = CPS × 4，限制 1000..8000。
- 停止时立即切 releaseEpoch 并取消 pending scope，未完成连接后续自关闭。
- close 使用 SO_LINGER(0)，释放更快。
- 保留调度间隔 ms。
- 继续不发布，update.json 保持 build88。

建议测试：
- 200 CPS / 100ms
- 500 CPS / 100ms
- 1000 CPS / 100ms
- 1000 CPS / 50ms
