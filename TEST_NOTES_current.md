# V1.1.16 build146 自测重点

## 版本与发版文件

1. APP 版本应显示 `versionName=V1.1.16`、`versionCode=146`。
2. 根目录只保留当前长期文档：`README.md`、`CHANGELOG.md`、`TEST_NOTES_current.md`。
3. 历史 build 说明应在 `docs/BUILD_HISTORY.md` 中查看，后续发版模板应使用 `docs/RELEASE_TEMPLATE.md`。
4. `update.json` 应指向当前正式发布版本；本次未创建 release 时不要求更新。

## 路由追踪

1. 进入 设置 -> 网络信息 -> Tracket 路由追踪，点击开始追踪后应逐跳显示结果。
2. 追踪中再次点击主按钮，应进入暂停状态，按钮显示“继续追踪”，已完成节点不清空。
3. 暂停 5-10 秒期间不应继续新增 hop；页面应保留当前结果。
4. 点击继续追踪后，应从当前进度继续追加后续 hop。
5. 点击停止追踪、返回页面、切换网络或把 APP 切到后台，追踪应安全停止，不应残留后台探测。
6. 对无法解析、系统 ping 不可用、网络不可用等情况，应显示错误行或停止提示，不应闪退。

## 稳定性回归

1. Ping 测试开始、停止、再次开始后，图表和统计不应接续旧会话。
2. 连接数测试开始时，Ping 目标应同步为连接数测试地址。
3. 漫游测试在 WiFi 权限拒绝、非 WiFi 网络、后台/锁屏时应给出提示或安全停止。
4. 长时间测试后历史记录数量仍应受限，页面滚动和图表拖动不应明显卡顿。
5. Android 10、Android 13、Android 15/16 设备上分别检查权限提示、通知权限和前台服务行为。

## 漫游事件详情

1. 漫游事件应区分 AP切换、频段切换、AP+频段切换、重新连接四类。
2. 频段显示应按 frequency 映射为 2.4G、5G、6G、未知；事件详情应尽量显示 `oldBssid · oldBand → newBssid · newBand`。
3. 模式名称应显示稳定、标准、极速；说明文案应分别为“长时间观察，结果更稳”“日常推荐，兼顾精度和稳定”“高频捕捉切换断流，短时测试”。
4. 模式卡片/超时区域仍显示自动匹配的 Ping 超时时间：稳定 1000ms、标准 700ms、极速 500ms，不显示底层网关/外网探测间隔。
5. Wi-Fi 快照间隔应为稳定 200ms、标准 100ms、极速 25ms；网关 Ping 间隔应为稳定 500ms、标准 300ms、极速 200ms；外网辅助 Ping 间隔应为稳定 1000ms、标准 1000ms、极速 500ms。
6. 极速 25ms 仅表示 Wi-Fi 快照目标调度间隔，实际精度会受 Android 调度、WiFiInfo 缓存和设备负载影响，不应承诺真实 25ms 级确认精度。
7. 高频采样下 UI 应保持节流刷新，不应随 25/100/200ms Wi-Fi 快照或 200/300/500ms 网关 Ping 全页面高频刷新；图表最多绘制抽稀后的 300 个点，不应明显卡顿。
8. 事件详情应显示“AP确认窗口约xms / 断流未检测到或断流<Ping采样间隔或断流约x.xs”，不再出现“时长约x秒”，也不得写成真实无线层漫游耗时。
9. 极速模式下单个网关 timeout/loss 可显示 `断流<200ms`；连续多个 timeout/loss 可显示 `断流约400ms`、`断流约600ms` 等估算值。
10. 未发生 Ping timeout/loss 的漫游事件，应显示“断流未检测到”，即使 AP确认窗口较长也不应误报真实断流。
11. AP确认窗口只基于 Wi-Fi 快照时间线，旧稳定 BSSID/频段最后一次采样到新稳定 BSSID/频段第一次采样；发生网关 Ping 连续 timeout/loss 后恢复时，应优先按网关 Ping 计算业务断流；外网 Ping 只作公网链路辅助观察。
12. 历史记录、实时详情卡、信号图点击弹窗中的漫游事件文案应保持一致，并包含前后 5 秒 RSSI、网关延迟、速率变化的简短结论。
13. 长期停留 2.4G 且弱信号、低速率、Ping 抖动或丢包时，可提示“疑似粘连频段”；没有可靠扫描结果时不得写成附近一定有更优 AP。
14. 页面说明应明确：未 root Android 普通 APP 无法读取驱动级 roam complete 事件，AP确认窗口只表示 Android Wi-Fi 快照采样确认。
15. 如设备不支持流式 ICMP Ping，应出现“回退单次 Ping”的网络事件提示；回退路径应串行执行，上一轮未完成时不新增任务，不能把 skipped/busy 计为丢包。
16. 高频分离采样下，NotScheduled / BusySkipped / Unknown 不应计入内网或外网丢包率分子和分母。
17. Ping 图红点只应代表明确 timeout/loss；NotScheduled / BusySkipped 不应画红点，也不应强制断开网关线或外网线。
18. 内网丢包 0%、外网丢包 0% 时，Ping 图不应因为网关/外网采样间隔不同而持续断点。
19. 漫游事件断流耗时应只基于连续明确 timeout/loss；采样缺口、未调度或 busy skipped 不应形成“断流约xxx”。
20. 事件详情中的“连丢X”应代表最长连续网关 timeout/loss run 的丢包数，不是前后大窗口的总丢包数。
21. `断流约600ms` 时，“连丢”数量应与当前网关 Ping 间隔大致匹配，例如极速 200ms 下约 3 个左右；不应再出现 `断流约503ms · 丢包11` 这类口径冲突。
22. 多次 AP 往返切换时，后一条事件不应重复吃到上一条事件的 timeout run；从弱信号切回好信号且无连续网关 timeout 时，不应显示 6s 级断流。
23. 流式 ICMP Ping 退出或被系统调度打断时，不应把剩余未输出的 maxCount 样本回填为超时；只有真实 seq 跳变才可推断丢包。
24. Wi-Fi 原始样本需包含 BSSID、SSID、RSSI、frequency、linkSpeed、supplicantState、elapsedRealtimeNs；AP检测和 AP确认窗口必须来自这些 Wi-Fi 快照时间戳，不得由 Ping timeout 反推。
25. Ping 原始样本需包含 sendNs、recvNs、seq、target、rtt/result；只有 TIMEOUT 计入丢包，ERROR、SKIPPED_BUSY、NOT_SCHEDULED、Unknown 不计入丢包率，也不画红点。
26. 业务断流应使用事件前 1500ms 到后 3000ms 内包含 TIMEOUT 的最大 OK→OK 间隔；连续快速 AP 切换时，相邻事件窗口不得重复统计同一段 timeout。
27. 延迟趋势基线应使用最近 10 个 OK 网关 Ping 的中位数；如果 Wi-Fi 或 Ping 实际最大采样间隔超过目标 3 倍，应显示采样抖动偏大的可信度提示。
28. Ping 图、RSSI 图必须按 elapsedRealtimeNs 升序绘制；图表虚线连接、抽稀、平滑和动态 Y 轴不得反向影响事件、断流、丢包或评分统计。


## 文档清理验证

1. 根目录不应再新增 `README_selftest_buildXXX.md`。
2. 根目录不应再新增 `TEST_NOTES_vX_buildXXX.md`。
3. 每次发版前先更新 `CHANGELOG.md` 与 `TEST_NOTES_current.md`，必要时把历史摘要追加到 `docs/BUILD_HISTORY.md`。
