# Changelog

## V1.1.3 build113 hotfix

- 同版本紧急修复：NAT 手动诊断的 STUN 服务器列表现在会持久化保存。
- 修复退出 App 后 RFC5780 / RFC3489 自定义服务器恢复默认的问题。
- 自定义服务器会自动规范化为 host:port，不填写端口时默认 3478。
- 版本号保持 V1.1.3 / build113 不变。

## V1.1.3 build113

- NAT 手动诊断支持多个 STUN 服务器自动顺延。
- NAT 检测过程显示当前服务器和测试阶段。
- 继续收紧 RFC3489 / RFC5780 状态机。

## V1.1.3 same-version hotfix - net tools
- 网络信息卡片新增 NSLookup / Tracket 两个并排小入口卡片。
- 新增 NSLookup 二级页：读取本机 DNS、解析 A/AAAA、保存最近 10 条记录、左滑删除。
- 新增 Tracket 二级页：TTL Ping 方式追踪目标 IP 路由、保存最近 10 条分析记录、左滑删除。
- 设置卡片拖动排序加入 Lazy item placement 动画，降低换位闪跳感。
