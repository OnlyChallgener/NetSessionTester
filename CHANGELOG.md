# NetSessionTester V1.1.3 build113

## 主要更新

- NAT 手动诊断支持多个 STUN 服务器按顺序自动尝试。
- 当前服务器失败、超时或不支持当前模式时，会自动尝试下一个服务器。
- NAT 检测过程中显示当前服务器和测试阶段，例如 RFC3489 Test I/II/I'/III、RFC5780 Filtering/Mapping。
- 继续收紧 RFC3489 / RFC5780 状态机，避免普通 Binding 或非预期来源响应被误判为过滤开放。
- STUN 服务器地址不填写端口时默认使用 3478。
- 保留 V1.1.x 高频 Ping、图表动态区间和日志容量优化。
