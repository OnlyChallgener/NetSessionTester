# V1.1.14 build115

- 发布版本号更新为 V1.1.14 / build115，versionCode 115。
- 首页 NSLookup / Tracket 入口卡片改为与网络信息小卡一致的字号和尺寸：58dp 最小高度、9dp 内边距、MarkBox 图标、11sp 小标题、14sp 主文字。
- 保留 V1.1.14 build114 下载横幅可关闭修复、HTTP 404 说明优化、NSLookup 自定义 DNS、Tracket 默认 30 跳等功能。

# V1.1.14 build114

- 发布版本号更新为 V1.1.14 / build114。
- NSLookup 增加 DNS1 / DNS2 自定义解析服务器，默认分别为 223.5.5.5 和 2400:3200::1。
- DNS1 / DNS2 均支持 IPv4 / IPv6 地址；每个输入框保存最近 3 条，支持下拉选择和单条删除。
- 本机 DNS 只用于固定展示，不参与自定义解析，不写入 NSLookup 查询历史。
- NSLookup 解析历史最多保存 15 条，点击复制完整结果，左滑单条删除。
- Tracket 默认跳数调整为 30，追踪过程实时显示，追踪历史最多保存 15 条。
- 首页 NSLookup / Tracket 入口文字放大，修复入口小卡片文字遮挡。

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

## V1.1.14 build114 hotfix - download banner dismiss
- 修复更新下载失败横幅无法右滑/关闭的问题。
- 失败/完成状态横幅新增右侧关闭按钮，支持横向滑动关闭。
- 下载失败 HTTP 404 文案增加 GitHub Release 包未发布或 APK 文件名不一致提示。
- 修正 NSLookup / Tracket 首页入口字号，避免入口文字过大挤压。

### V1.1.14 build114 hotfix - shortcut font visual correction
- 再次下调首页 NSLookup / Tracket 两个入口卡片字号、图标和高度，修复视觉上仍偏大的问题。
- 版本号保持 V1.1.14 / build114 不变。

## V1.1.14 build116 self-test
- 新增网络信息固定入口：MTU检测、漫游测试。
- MTU检测支持 IPv4/IPv6/自动策略，使用二分法显示探测过程、结果和分析；IPv6 模式自动使用 AAAA 解析。
- 漫游测试支持路由器/外网/路由器+外网目标，采集 RSSI、协商速率、BSSID、延迟、丢包，并用曲线显示过程。
- NSLookup 新增“本机DNS / 自定义DNS”解析方式开关；本机DNS模式走系统解析，自定义DNS模式走 DNS1/DNS2。
- 自定义DNS查询优化：DNS1 空结果、超时或失败时会继续尝试 DNS2。
- NAT类型小卡片支持点击进入 NAT 诊断。
- 修复长按/点击卡片时部分阴影或水波纹呈方形的问题，入口卡片和网络信息卡片裁剪为圆角。

注意：此包为自测包，不建议直接发布 Release。
