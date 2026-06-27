# V1.1.2-internal build112 NAT full-state selftest

- 重写手动 NAT 检测状态机，严格对齐 RFC3489 Test I / Test II / Test I' / Test III 分支流程。
- RFC5780 过滤测试与映射测试完全隔离，过滤测试使用独立 socket，避免先向备用地址发包导致 NAT3 被误判 NAT1。
- NAT1 / 全锥形必须由连续两次独立 Filtering Test II 成功证明。
- RFC3489 / RFC5780 响应必须匹配 transaction id、预期来源 IP 和预期端口。
- RFC5780 不支持备用地址时降级 RFC8489，过滤行为显示未验证。
