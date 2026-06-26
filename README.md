# NetSessionTester

当前版本：V1.0.7 build107

宽带会话测试器：支持 TCP 会话测试、IPv4/IPv6 分别测试、NAT/STUN 诊断、独立 Ping、Ping 响应日志、网络信息检测与更新检测。

## V1.0.7 重点修复

- 修复运营商识别误显示 `No. 1, Jin Rong Street` 等地址字段的问题。
- 收紧 NAT1 / 全锥形判定，避免端口保持就全部判为 NAT1。
- 版本信息弹窗补齐 V1.0.x 日志。
- Ping 参数卡去掉多余标题，保留独立 Ping 简介和开关。
- 协议选择框统一为输入框样式，与间隔、超时、次数保持一致。
- 优化卡片长按拖动排序节奏，降低闪跳、重叠和视觉突变风险。

## GitHub Actions 产物

`NetSessionTester-V1.0.7-build107-signed.apk`
