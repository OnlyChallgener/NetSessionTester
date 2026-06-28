# v0.9.15 buildfix10

- versionCode: 55
- 压缩 Ping 延迟卡片顶部工具栏，不再占用过多图表空间。
- SSH 详情弹窗支持局部选择复制；复制输出按钮仍复制完整真实输出。
- 修复 SSH 记录左滑删除按钮常驻问题。
- 路由追踪新增实时过程显示与 15 条历史记录，历史支持展开、复制、左滑删除。

# Labprobe v0.9.15 buildfix9

- versionCode: 54
- 修复 GitHub 仓库残留旧 `app/src/main/java/com/demonv/.../TestForegroundService.kt` 时仍被编译的问题。
- Gradle 现在只编译 `app/src/main/kotlin` 当前源码目录，旧 Java 目录即使未被网页上传删除也不会影响构建。

# Labprobe v0.9.15 / hotfix8

- versionCode: 53
- 修复 DNS 解析运营商识别：IPv6 前缀 240e/2408/2409/240a 立即显示电信/联通/移动。
- UDP 探测模板切换会自动填入默认目标和端口：STUN、DNS、NTP、UDP 空包。
- UDP 探测页右上角新增恢复当前模板默认设置按钮。
- SSH 执行结果最多保留 6 条。
- SSH 执行结果卡片支持左滑单条删除、长按复制真实输出、点击弹出完整输出卡片。
- 固定签名配置继续保留，GitHub Secrets 配好后可覆盖安装。
