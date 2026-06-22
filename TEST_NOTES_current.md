# NetSessionTester v0.9.9 build84 Performance Release - Ping 保留版

本版目标：性能强、不闪退、花费时间少，同时保留 Ping 图表刷新。

## 核心策略

- 固定目标 CPS 执行，不再使用 128 动态 CPS / 自适应调速。
- 内部 100ms 分片发射，降低 Android 调度抖动。
- 会话图只保留蓝色会话数曲线，取消 CPS 曲线和双 Y 轴。
- Ping 图表保留刷新：独立协程、秒级聚合，不参与会话发射调度。
- 总计按成功 + 失败统计。
- 运营商卡片改回 NAT 置信度。
- 保留 FD / 失败兜底保护，接近上限立即停止新增并统一释放。

## 编译建议

```bash
./gradlew clean
./gradlew assembleRelease
```
