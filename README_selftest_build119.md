# NetSessionTester V1.1.14 build119 自测说明

本包为自测版本，不建议直接发 Release。

## 重点改动

- MTU 检测改为四合一结构：本地 MTU、ICMP 路径、TCP 业务、应用层 PLPMTUD。
- 本地 MTU 读取当前 active network 的 LinkProperties / NetworkInterface，仅作为本机链路上限参考。
- ICMP 路径继续使用 ping + 二分法，显示每一步探测过程。
- TCP 业务模式默认连接目标 443 端口，验证真实 TCP/HTTPS 业务可达；当前 Java 实现给出基于本地 MTU 的 MSS 估算，后续可接 NDK TCP_MAXSEG 精确读取。
- 应用层 PLPMTUD 需要 UDP/QUIC Echo 测试节点配合，当前版本先做预留说明，不误判公共网站。

## 自测建议

1. WiFi 下测试 `www.qq.com`，综合模式。
2. 分别测试 IPv4优先 / IPv6优先。
3. 切换到 TCP 模式确认 443 连接是否成功。
4. 切换到本地模式确认本地 MTU 是否能读取。
5. 在 ICMP 模式确认二分探测过程是否正常展示。
