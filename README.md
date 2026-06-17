# NetSessionTester v0.5 React + Kotlin

宽带会话测试器：React 前端 + Kotlin 原生 TCP 核心。

## 架构

- 前端 UI：React + CSS，打包到 Android assets，通过 WebView 加载。
- 原生核心：Kotlin，负责 TCP 会话保持、IPv4/IPv6 解析、前台服务、日志、历史、CSV 导出。
- JSBridge：React 调用 Kotlin，Kotlin 推送实时统计到 React。

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

- 支持仅 IPv4、仅 IPv6、IPv4 / IPv6 分别测试。
- TCP 会话保持测试，成功连接会持续保持。
- 测试统计：活动、失败、总计、新增、CPS。
- 失败原因细分：超时、拒绝、端口耗尽、FD 上限、网络不可达等。
- 日志与检测历史集中在“日志”页。
- 支持隐私打码、导出 CSV、清理日志/历史、自动保存设置。
- 支持前台服务，降低长时间测试被系统回收概率。

## GitHub Actions

上传完整项目后进入 Actions，运行 `Build Android APK`。构建完成后下载 Artifact：

`NetSessionTester-v0.5-react-kotlin-debug-apk`

## 注意

请只测试自有 VPS、内网设备、路由器或已授权服务器。不要对公共网站进行高会话测试。
