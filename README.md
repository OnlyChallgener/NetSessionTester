# NetSessionTester

当前版本：V1.1.2-internal build112 NAT strict state machine selftest

本自测包重点修复 NAT 手动检测的 RFC5780 / RFC3489 状态机：过滤行为必须校验 STUN 响应来源地址和端口，避免把普通 Binding 响应误判为过滤开放，从而把 NAT3 误判为 NAT1。
