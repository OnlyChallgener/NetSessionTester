# V1.0.4-internal build104 自测说明

## NAT

- 在 WiFi / 蜂窝网络分别刷新网络信息。
- 对比 My NAT、FastNet、checkmynat 的 NAT1 / NAT4 结果。
- 重点观察端口保持时是否显示 NAT1 / 全锥形。

## Ping

- 测试 223.5.5.5、119.29.29.29、www.baidu.com。
- 分别测试 1000ms / 500ms / 200ms 间隔。
- 检查响应日志是否记录成功、超时、高延迟。

## 会话

- 复测 200 / 500 / 1000 CPS，确认定速发射核心没有回退。
