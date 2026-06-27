# V1.1.2-internal build112 NAT strict state machine selftest

重点自测：

1. RFC5780 + stunserver2025.stunprotocol.org:3478
2. RFC5780 + stun.miwifi.com
3. RFC5780 + stun.voip.aebc.com
4. RFC3489 + stun.voip.aebc.com

预期：

- 同一手机、同一网络、同一 STUN 服务器下，应尽量对齐 NatTypeTester / nat_type_detector。
- 若 filtering test 只收到原服务器响应，不能判“开放”。
- Change IP + Change Port 成功必须来自备用 IP + 备用端口。
- Change Port 成功必须来自原 IP + 不同端口。
- 端口保持 + 端口受限应显示 NAT3 / 端口受限型。
