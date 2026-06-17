# NetSessionTester v0.6.1 Compose UI

宽带会话测试器，Kotlin + Jetpack Compose 原生版。

## 默认参数

- 默认地址：www.baidu.com
- 端口：80
- 每批新增：100
- 间隔：500 ms
- 超时：3000 ms
- 失败停止：200
- 目标会话：65535
- 测试完成后保持连接：开启

## 功能

- TCP 会话保持测试。
- 支持仅 IPv4、仅 IPv6、IPv4 / IPv6 分别测试。
- 会话统计只显示：活动、失败、总计、新增、CPS。
- 失败原因统计，点“更多”显示详情。
- 最近日志，点“更多”进入日志页。
- 日志与检测历史归集到日志页。
- 历史记录点卡片显示单次详情。
- 支持隐私打码、CSV 导出、清理日志历史、自动保存设置。
- 支持前台服务，降低长时间保持连接被系统回收概率。

## 构建

上传完整项目后进入 GitHub Actions，运行 Build Android APK。
Artifact：NetSessionTester-v0.6.1-compose-ui-debug-apk

## v0.6.1 修复

- 增加 Material Icons，替换文字占位图标。
- 整体字号和卡片间距下调。
- 日志行显示更紧凑。
- 底部栏改为真实图标。
