# Changelog

## V1.1.2-internal build112
- NAT 检测从网络信息自动探测中独立出来，改为手动诊断。
- 网络信息继续保留 IPv4、IPv6、优先级、运营商等轻量信息。
- NAT 诊断只保留 IPv4 + UDP，支持 RFC5780 / RFC3489。
- RFC5780 默认服务器：stunserver2025.stunprotocol.org:3478。
- RFC3489 默认服务器：stun.voip.aebc.com:3478。
- STUN 服务器默认每种模式只给 1 个，可添加、删除。
- 图标改为原创 NAT/网络轨道风格，避免直接照抄。
