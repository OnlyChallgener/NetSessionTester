# Changelog

## V1.0.8 build108

- versionName 更新为 V1.0.8。
- versionCode 更新为 108。
- 修复 Ping IPv4 模式在部分 Android ROM 中全程 timeout 的问题。
- IPv4 Ping 改为先解析 A 记录/IPv4 literal，再使用普通 ping，不再使用 `ping -4`。
- IPv6 Ping 优先使用 ping6，必要时回退 ping -6。
- 自动 Ping 会先解析地址，明确显示 AUTO · IPv6 或 AUTO · IPv4。
- 修复 Ping 失败时可能显示 0ms 的问题，失败统一显示为空值/失败原因。
- Ping 图表显示当前目标、实际协议与最近 120 秒窗口。
- 目标与模式地址栏、Ping 目标地址栏新增最近 5 条历史，可点选和删除。
- 保持 V1.0.7 的 NAT 判定、运营商识别、卡片拖动排序和高性能定速发射核心不变。
