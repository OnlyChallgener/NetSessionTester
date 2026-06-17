# 宽带会话测试器 / Net Session Tester

这是一个重新设计的 Android 原生项目，用于在手机上测试 IPv4 / IPv6 TCP 连接、并发会话承载、连接成功率和延迟统计。

> 使用提醒：请只测试自己的 VPS、路由器、内网服务器或已获得授权的目标。项目默认不内置公共网站目标，并在代码里限制最大并发，避免误用。

## 功能

- Kotlin + Jetpack Compose 原生 Android UI
- 自适应布局：窄屏单列，宽屏双列
- 支持自动 / 仅 IPv4 / 仅 IPv6
- TCP Connect 并发测试
- 自动递增并发
- 失败率阈值自动停止
- 显示成功率、失败率、平均延迟、P95、错误类型
- 历史记录保存到本机
- 当前测试结果导出 CSV
- 自适应 Launcher 图标
- GitHub Actions 自动构建 Debug APK

## GitHub 构建 APK

1. 新建 GitHub 仓库。
2. 把本项目所有文件上传到仓库根目录。
3. 打开仓库的 `Actions`。
4. 选择 `Build Android APK`。
5. 点击 `Run workflow`。
6. 构建完成后，在 `Artifacts` 下载 `NetSessionTester-debug-apk`。
7. 解压后安装 APK 到小米 14 Pro / 澎湃 OS。

## 本地构建

如果你本地安装了 Android Studio，可直接打开本项目。项目没有包含 Gradle Wrapper 二进制文件，GitHub Actions 会自动安装 Gradle 9.5.1。

本地命令行构建可执行：

```bash
gradle assembleDebug
```

## 重要参数

- 起始并发：默认 10
- 最大并发：默认 200，代码最大限制 1000
- 步长：默认 10
- 超时：默认 3000 ms
- 连接保持：默认 1000 ms
- 失败阈值：默认 0.25，代表失败率达到 25% 自动停止

## 后续可扩展

- 前台服务通知，适合长时间测试
- 图表曲线
- 保存多个测试配置
- Release 签名 APK / AAB
- 自建测试服务端
