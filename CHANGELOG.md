# Changelog

## V1.1.2-internal build112 NAT RFC兼容自测

- 修复 RFC5780 检测过于死板的问题。
- 增加 CHANGED-ADDRESS 兼容读取。
- RFC5780 不完整时自动降级到 RFC8489 基础结果。
- RFC8489 结果显示“过滤行为未验证”，避免误判 NAT1。
- RFC3489 模式独立处理，不强制 OTHER-ADDRESS。
- STUN 服务器地址支持省略端口，默认使用 3478。
- 修正过滤行为判定，普通 Binding 响应不再被当成过滤开放。
