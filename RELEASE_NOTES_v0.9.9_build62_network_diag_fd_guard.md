# NetSessionTester v0.9.9 build 62

本版基于上一版 FD Guard 代码包继续修改，版本名仍保持 v0.9.9。

## 重点更新

1. 继续保留统一释放收尾
- 失败上限、FD 上限、手动停止、强制释放、网络切换、解析失败等路径继续统一走 releaseAndFinalize。
- 释放流程保持“先 UI 显示已释放，再后台 close socket”。
- 保留 generation/epoch 防 late socket 回流机制。

2. 继续保留 FD 安全保护
- 接近系统 FD 上限前主动停止新增连接。
- 单批连接保持安全上限，降低极限测试闪退风险。
- Too many open files、SocketException、OutOfMemory 等异常继续转成安全收尾。

3. Ping 默认保持 500ms
- 按当前讨论结果，Ping 采样默认仍为 500ms。
- 不引入复杂自动降频逻辑，避免意外改变用户体验。

4. 网络信息卡表达优化
- 网络信息增加 NAT 类型、映射行为、过滤行为、DNS 诊断、IPv6 状态、置信度和诊断建议。
- IPv4 NAT 与 IPv6 状态分开表达，避免“有 IPv6 但显示 NAT3”的误解。
- 检测到 VPN/代理时，明确提示 NAT/IPv6/出口结果仅供参考。

5. DNS / IPv6 诊断优化
- 默认仍以系统 DNS 为主。
- 系统 DNS 查不到 AAAA 时，使用阿里/腾讯 DNS 做轻量诊断。
- 支持提示 AdGuard Home、代理 DNS、路由器策略可能导致的 AAAA 过滤。
- 检测到 198.18.0.0/15 Fake-IP 时提示 DNS 可能被代理接管。

6. 网络信息刷新优化
- 保留手动刷新，手动刷新执行完整检测。
- 增加 10 秒轻量刷新，只更新基础网络诊断信息，不频繁跑完整公网/NAT检测。

## 说明

这版重点是提高状态表达和极限场景稳定性，不改变 v0.9.9 的主功能逻辑。
