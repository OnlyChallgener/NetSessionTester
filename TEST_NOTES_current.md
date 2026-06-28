# V1.1.14 build114 测试要点

1. 版本检查：确认 App 显示 V1.1.14，versionCode 为 114。
2. 首页检查：只观察 NSLookup / Tracket 两个入口小卡片，确认标题和副标题不遮挡；其它网络信息卡片不应变化。
3. NSLookup：确认本机 DNS 固定显示，仅作为展示；解析历史里不显示本机 DNS。
4. 自定义 DNS：DNS1 默认 223.5.5.5，DNS2 默认 2400:3200::1；两个输入框都能填写 IPv4 / IPv6 地址。
5. DNS 历史：DNS1 / DNS2 各自最多保存 3 条，下拉可选择，单条可删除。
6. NSLookup 解析：分别测试 全部 / A / AAAA；点击解析记录应复制完整结果；左滑记录应出现小删除按钮并可删除。
7. Tracket：默认跳数应为 30，开始追踪后应实时显示追踪过程。
8. 追踪历史：最多保存 15 条，默认缩略，展开后可长按选择复制，左滑可删除。

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

## V1.1.14 build114 hotfix 自测重点
1. 模拟 update.json 中 apkUrl 返回 404，顶部横幅显示“可能 GitHub 还未发布 release 包或 APK 文件名不一致”。
2. 下载失败横幅可点击右侧 × 关闭。
3. 下载失败横幅可左右滑动关闭。
4. 点击横幅进入下载弹窗后，点右上角 × 会同时关闭弹窗并清除失败横幅。
5. 首页 NSLookup / Tracket 入口字号不再过大，不遮挡副标题。
6. versionName 仍为 V1.1.14，versionCode 仍为 114。
