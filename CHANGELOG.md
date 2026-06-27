# Changelog

## V1.1.2-internal build112 NAT strict state machine selftest

- 修复 RFC5780 过滤测试误判：Change IP + Change Port 成功必须来自 OTHER-ADDRESS / CHANGED-ADDRESS。
- 修复 RFC5780 Change Port 判断：响应必须来自原服务器 IP 的不同端口。
- 修复 RFC3489 Test II / Test III 成功条件：不再把任意响应当成开放或地址受限。
- 保留 CHANGED-ADDRESS 兼容读取，支持 stun.miwifi.com、stun.voip.aebc.com 等服务器。
- 保留无端口自动补 3478。
- 目标：对齐 NatTypeTester / nat_type_detector 的 NAT3 判定口径。
