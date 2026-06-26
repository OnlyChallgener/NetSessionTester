# Changelog

## V1.0.7 build107

正式修复版。

- versionName 更新为 V1.0.7。
- versionCode 更新为 107。
- 修复运营商 ASN 识别把地址字段显示为运营商的问题。
- NAT1 判定收紧：需 6/6 STUN 节点、2轮稳定、公网IP和端口一致。
- 端口保持但验证不足时显示 NAT3 / 端口保持型，不再全部判为 NAT1。
- 版本信息弹窗补齐 V1.0.x 日志。
- Ping 参数卡去掉多余标题。
- 协议框统一为 OutlinedTextField 风格。
- 卡片拖动排序增加更高阈值和节流，减少一闪即逝的跳动。
