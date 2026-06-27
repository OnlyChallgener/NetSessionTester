# V1.1.3 build113 自测建议

## NAT

1. RFC5780 + stunserver2025.stunprotocol.org:3478
2. RFC5780 + stun.miwifi.com
3. RFC5780 + stun.voip.aebc.com
4. RFC3489 + stun.voip.aebc.com
5. 添加多个服务器，故意填一个错误服务器在第一条，确认会自动顺延到下一条。
6. 观察检测进度是否显示 Test I / Test II / Test I' / Test III 或 Filtering / Mapping。

## Ping

- 30ms × 300次，确认高频 Ping 正常。
- 图表继续显示趋势，不再被异常值拉平。
