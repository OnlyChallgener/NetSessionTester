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

## V1.1.3 build113 same-version refine - net tools UI
- 仅修复首页 NSLookup / Tracket 两个入口小卡片文字遮挡，不改网络信息其它卡片。
- NSLookup 页面改为配置页风格：本机 DNS 固定显示，解析记录上限改为 15 条，记录卡片点击复制、左滑小按钮删除。
- Tracket 页面改为追踪配置风格：支持 IP 策略、跳数、超时配置，追踪过程实时显示。
- “分析记录/分析历史”统一改为“追踪历史”，历史上限 15 条，缩略显示，右上角展开后支持文本选择复制。
- 左滑删除按钮缩小为浅红小胶囊样式，减少占位。
