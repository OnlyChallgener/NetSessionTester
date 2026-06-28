# V1.1.3 build113 hotfix 测试重点

1. 打开 NAT 类型检测，RFC5780 添加多个 STUN 服务器，退出 App 后重新进入，应保留自定义列表。
2. 切换 RFC3489，添加/删除服务器，退出 App 后重新进入，应保留自定义列表。
3. 输入不带端口的服务器，例如 stun.miwifi.com，保存后应规范化为 stun.miwifi.com:3478。
4. 版本号仍为 V1.1.3，versionCode 仍为 113。

## Net tools smoke test
- 从网络信息卡片点击 NSLookup / Tracket，可进入二级页面；系统返回键和左上角返回均回到 APP 设置页。
- NSLookup 解析成功/失败都写入最近 10 条记录，左滑露出删除按钮。
- Tracket 使用 ping TTL 轻量追踪，部分 Android ROM 可能不返回中间跳点，此时会显示超时但不闪退。
- 本机 DNS 读取来自 ConnectivityManager.getLinkProperties(activeNetwork).dnsServers；它不是 A/AAAA 解析结果本身。

## Net tools refine smoke test
- 首页网络信息卡片只确认 NSLookup / Tracket 两个入口不再文字遮挡；IPv4、IPv6、NAT、运营商布局不要变化。
- 进入 NSLookup，页面顶部“本机DNS”应固定显示当前系统 DNS，不随解析成功/失败结果变化。
- NSLookup 记录最多 15 条；点击记录卡片应复制完整解析结果；左滑单条记录露出小型删除按钮并可删除。
- 进入 Tracket，开始追踪后应实时追加显示“追踪过程”。
- Tracket 历史标题应为“追踪历史”；最多 15 条；默认缩略显示，点击右上角箭头展开；展开后长按文本可选择复制。
