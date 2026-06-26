# NetSessionTester

当前版本：V1.0.8 build108

宽带会话测试器，支持 TCP 会话测试、IPv4/IPv6 分别测试、NAT/公网映射检测、独立 Ping、Ping 响应日志、历史记录与导出。

## V1.0.8 重点修复

- 修复 Ping 仅 IPv4 模式在部分 Android ROM 全程 timeout 的问题。
- IPv4 Ping 不再强依赖 `ping -4`，改为先解析 IPv4 地址后使用普通 ping。
- 自动 Ping 会明确选择 IPv4 或 IPv6，并显示实际协议。
- 修复 Ping 失败时可能显示 0ms 的问题。
- Ping 图表显示正在 Ping 的目标地址和实际协议。
- 目标地址栏和 Ping 地址栏支持最近 5 条历史，可点选和删除。
- 保持 V1.0.7 的 NAT 判定、运营商识别、卡片拖动和高性能会话测试核心。

## Release APK

`NetSessionTester-V1.0.8-build108-signed.apk`
