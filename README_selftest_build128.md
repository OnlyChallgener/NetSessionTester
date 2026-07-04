# NetSessionTester V1.1.15 build128 自测说明

本包用于自测，不建议直接发布。

版本：
- versionName: V1.1.15
- versionCode: 128

## 重点改动

1. Ping 高频探测增加 TCP Socket 引擎。
   - 间隔小于 200ms 时，先探测目标常见 TCP 端口。
   - 可用端口包括 80、443、22、8080、8443、8000、5000、5001。
   - 找到可连接端口后，使用 TCP Socket connect 耗时作为高频延迟。
   - 普通 Android APP 无法使用 ICMP Raw Socket，TCP Socket 是无需 Root 的高频替代方案。

2. 高频无限 Ping 修复。
   - 之前无限模式下低于 200ms 仍可能走串行 ping，实际频率只有 5-8 次/s。
   - 现在高频无限模式会优先 TCP Socket；无 TCP 端口可用时回退系统 ping 流式探测。

3. ICMP 流式高频改进。
   - 系统 ping 流式探测支持无限模式分块执行。
   - ping 间隔下限从 30ms 改为 25ms。
   - 若系统 ping 不支持高频，会在日志里提示“高频受限”。

## 说明

- TCP Socket Ping 不是 ICMP Ping，更接近 TCP/HTTP 业务连通性延迟。
- 如果目标没有开放 TCP 端口，会自动回退 ICMP 系统 ping。
- 25ms 高频能否达到 40 次/s 取决于目标端口、设备性能、系统调度和网络条件。
