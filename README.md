# NetSessionTester v0.9.9 build84 performance release

这版是发布用性能版：优先保证速度、稳定、不闪退。

核心策略：

- 目标CPS固定执行，不再做 128 动态调速。
- 默认目标CPS 1000，内部 100ms tick 发射。
- 测试页主图只画会话数蓝线，CPS只显示文字。
- 测试期间关闭 Ping 监控和公网/STUN刷新，避免抢占网络和 UI。
- 总计统一为成功 + 失败。
- NAT信息恢复显示 NAT置信度。
- 保留 FD 保护，接近 Android Socket/FD 上限立即收尾释放。

编译：

```bash
./gradlew clean
./gradlew assembleRelease
```
