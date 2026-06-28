# Labprobe
## v0.9.15 buildfix9

- versionCode = 54。
- 增加 Gradle sourceSets 保护：即使 GitHub 仓库里残留旧的 `app/src/main/java` 文件，也不会参与当前 Labprobe 构建。
- 继续保留 Actions 构建前删除旧 Java 源码目录的步骤，避免 `TestForegroundService.kt` 遗留文件导致 Release 编译失败。
 v0.9.15 hotfix7

## v0.9.15 hotfix8

- versionCode = 53
- DNS 解析运营商识别统一使用快速前缀判断，IPv6 结果不再一直显示未知。
- UDP 模板新增默认目标和端口，切换模板会自动填入，右上角支持恢复默认。
- SSH 执行结果保留 6 条，支持左滑删除、长按复制、点击查看完整输出。


- versionCode: 52
- 新增路由追踪功能，可追踪域名解析后的 IPv4 / IPv6 路径。
- 修复 DNS / TCP / UDP / NAT / SSH 参数框过矮导致文字被遮挡的问题，统一双列控件高度和图标尺寸。
- NAT 检测配置卡片改为科技蓝双列布局，与其他工具页观感一致。
- SSH 命令下拉栏保存最近 6 条命令。
- SSH 执行结果最多保留 5 条记录，最新在上，点击卡片复制真实输出内容，不复制返回码。

# Labprobe v0.9.15

极客网探 Labprobe Android 客户端，Kotlin + Jetpack Compose 单文件工程。

## v0.9.15 hotfix6

- versionCode = 51
- 版本弹窗增加检测更新按钮。
- 工具页 IPv4/IPv6 状态胶囊可跳转 DNS 解析。
- 运营商状态增加 IPv6 前缀快速推断。
- DNS / TCP端口 / UDP探测 / SSH 页面统一科技蓝紧凑布局。

## v0.9.15 本次重点

- 延迟测试从单一 Ping 升级为 ICMP / TCP Connect / HTTP HEAD / HTTP GET。
- 支持 IPv6 优先、IPv4 优先、仅 IPv6、仅 IPv4，以及 DNS A/AAAA 优先策略。
- Ping/延迟测试页面按 One UI 风格重做参数区：科技蓝小图标、标题缩小、卡片高度压缩。
- 曲线 X 轴使用真实耗时；图表 1 秒聚合展示，原始数据实时采集。
- Y 轴最小刻度为 30ms，尖峰与丢包单独标记。
- 图表右上角显示真实采样率，避免只看设置间隔误判。
- 历史记录弹窗保存最近 10 次测试汇总，显示占用空间，可折叠查看。
- 工具页卡片支持整张点击进入。

## GitHub Actions

上传到 GitHub 后，Actions 会构建 debug APK：

```bash
gradle :app:assembleDebug --stacktrace
```

APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 版本

- versionName: 0.9.15
- versionCode: 49


## v0.9.15-hotfix1
- 修复 Kotlin 字符串插值：真实采样率文本使用 `${rate}`，避免 `rate次` 被识别为变量。
- 修复 OkHttp Dns 固定地址解析：改为 `object : Dns`，避免接口构造错误。
- versionCode 更新至 46，便于 GitHub Actions 重新打包。


## v0.9.15-hotfix2
- 延迟测试页面标题再缩小一号，卡片标题更轻，减少拥挤。
- 图表卡片标题改为“延迟”，去掉省略号和 X/Y 轴小字备注。
- Y 轴最多显示 5 个点位；低延迟场景固定展示 0 / 30 / 60 / 90 / 120。
- 延迟图表高度增加到 222dp，最高点位更靠上，显示更舒展。
- 停止按钮启用态改为科技蓝，不再使用墨绿色。
- ICMP 高频采样增加取消时进程强回收，并为 IPv6 增加 ping6 到 ping -6 的回退。
- versionCode 更新至 47，便于 GitHub Actions 重新打包。

## v0.9.15 hotfix3

本版新增 NAT 行为检测：使用 STUN UDP 按 RFC3489 传统 TEST 1/2/3/4 展示基础映射、换地址回包、换端口回包和映射一致性。工具页同时改为 One UI 2 列磁贴布局，移除“整张卡片可直接进入”提示，并加入网络状态概览卡。

注意：完整 NAT 分类需要 STUN 服务器支持 Changed/Other Address；普通公共 STUN 若只支持基础 Binding，APP 会只给出基础映射和低可信度结果，不会硬判。

## v0.9.15 hotfix4 - 固定签名

- `versionCode` 更新至 49。
- `app/build.gradle.kts` 新增 `labprobeUpload` 签名配置。
- GitHub Actions 支持从 Secrets 解码固定 keystore，`debug` 与 `release` 可使用同一签名。
- 未配置 Secrets 时仍会构建 debug APK，但会在 Actions 中提示：默认 debug 签名可能导致后续无法覆盖安装。
- 新增 `SIGNING_SETUP.md` 和 `signing.properties.example`，用于本地与 GitHub 固定签名配置。

注意：如果手机里已安装旧随机签名 APK，第一次切换到固定签名版时可能仍需卸载一次；之后保留同一 keystore 并递增 `versionCode` 即可覆盖安装。

## v0.9.15 hotfix5 · 网络状态 / NAT / UDP 重构

- 工具页网络状态卡显示 IPv4 出口、IPv6 地址、NAT 类型、运营商、本地 IP、优先级。
- NAT 检测支持 RFC5780 / STUN RFC8489 行为发现与 RFC3489 TEST 1-4 双模式。
- NAT 服务器每类最多保存 10 个，失败按顺序重测。
- NAT 记录最多保存 50 条，支持左滑删除。
- 端口测试与 UDP 探测已拆成独立页面，避免两个入口共用同一逻辑。
- versionCode = 50。


## v0.9.15 buildfix10 / versionCode 55

- Ping 延迟图表顶部工具栏缩矮：标题、图标、真实采样率/丢包胶囊和历史按钮更小，图表区域保持高度。
- SSH 完整执行结果弹窗支持局部选择复制，右下角仍保留完整复制。
- SSH 结果左滑删除状态自动回收，避免删除按钮常驻。
- 路由追踪实时显示逐跳过程，新增最多 15 条追踪历史，支持展开/收起、复制、左滑删除。

