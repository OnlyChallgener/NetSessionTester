# NetSessionTester v0.9.9 build 67 local test

本地自测版，不用于推送更新。

## NAT 方案A：公共 STUN 增强版

- 保留 6 主 + 2 备 STUN 节点。
- 使用同一个 UDP DatagramSocket 复用本地端口进行多节点探测，避免每个 STUN 节点新建 socket 导致映射判断不稳定。
- 默认执行 2 轮复测：第一轮主节点，主节点不足时启用备用；第二轮只复测首轮成功节点，减少失败节点拖慢解析。
- 使用随机 Transaction ID，并按响应 Transaction ID 匹配请求，避免多节点响应串包误判。
- NAT 结果诊断增加“复测2轮稳定/有波动”和“已启用备用节点”提示。
- 过滤行为标注为“多节点推断”，避免把公共 STUN 结果当成 RFC5780 完整确认。

## 继承 build66

- 动态 FD 保护：根据 /proc/self/fd 与 /proc/self/limits 判断剩余 FD。
- FD 剩余 900/500/300 分段预警、保护、硬停。
- active >= 32600 仅作为兜底保护。
- 智能 CPS、释放图形进度、更新忽略、底部栏适配等逻辑保持。
