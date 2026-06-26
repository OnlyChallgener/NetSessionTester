# V1.0.8 build108 自测重点

1. 顶部版本号应显示 V1.0.8。
2. GitHub Actions 产物应为 NetSessionTester-V1.0.8-build108-signed.apk。
3. Ping 选择仅 IPv4，测试路由器内网 IPv4、公网 IPv4，不能因 `ping -4` 不兼容而全程 timeout。
4. Ping 自动模式应显示 AUTO · IPv6 或 AUTO · IPv4，不能出现 0ms 异常。
5. 无公网 IPv4、有公网 IPv6 时，AUTO 应优先走可用 IPv6。
6. Ping 图表上方应显示当前目标和实际协议。
7. 目标地址栏和 Ping 目标地址栏最近 5 条历史可点选、可删除。
8. NAT 判定、会话测试核心、FD 32360 保护保持不变。
