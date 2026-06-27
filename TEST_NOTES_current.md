# V1.1.2-internal build112 NAT RFC兼容自测

本包为自测版，不发布。重点修复 NAT 手动检测流程：

- RFC5780 优先读取 OTHER-ADDRESS，同时兼容 RFC3489 的 CHANGED-ADDRESS。
- RFC5780 若服务器只支持基础 Binding，会自动降级显示 RFC8489 结果。
- RFC8489 降级结果显示公网映射、映射行为，过滤行为显示未验证。
- RFC3489 模式走独立兼容流程，不再强制 OTHER-ADDRESS。
- STUN 服务器地址不带端口时自动补 3478。
- 修正过滤行为判定：只有 CHANGE-REQUEST 成功回包才算开放/地址受限/端口受限，普通 Binding 不再误判 NAT1。

建议自测：

1. RFC5780 + stunserver2025.stunprotocol.org:3478
2. RFC5780 + stun.miwifi.com
3. RFC5780 + stun.voip.aebc.com
4. RFC3489 + stun.voip.aebc.com
5. 不带端口输入，确认自动补 3478。
