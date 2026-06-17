# 宽带会话测试器 / Net Session Tester v0.2

这是一个 Android 原生 APP，用来测试当前宽带、路由器和运营商线路能稳定保持多少条 TCP 会话。

重点不是测速，也不是压测服务器性能，而是：

```text
慢慢建立 TCP 连接
成功后持续保持不关闭
实时统计当前活动会话、成功、失败、CPS
IPv4 / IPv6 分开测试
直到达到成功上限或失败上限
```

> 使用提醒：请只测试自己的 VPS、路由器、内网服务器或已获得授权的目标。不要对公共网站做高会话测试。

## 主要功能

- Kotlin + Jetpack Compose Android 原生 UI
- TCP 会话保持测试，而不是一次性并发压测
- 支持仅 IPv4、仅 IPv6、IPv4/IPv6 分别测试
- DNS A / AAAA 解析结果分开显示
- 目标成功会话数支持到 70000，可填 65535
- 每批新增连接数、间隔、超时、失败停止可配置
- 成功连接会持续保持，直到点击“释放连接”
- 实时显示：当前活动、最大稳定、成功、失败、总计、CPS
- 类似 Windows 工具的日志输出
- 历史记录保存到本机
- CSV 导出日志和摘要
- GitHub Actions 自动构建 Debug APK

## 推荐参数

普通测试：

```text
每批新增：16
间隔 ms：1000
超时 ms：3000
失败停止：200
目标成功会话数：65535
测试完成后保持连接：开启
```

如果手机发热、APP 卡顿、路由器异常，先把每批新增改小，比如 8 或 16。

## IPv4 / IPv6 说明

IPv4 测试更接近 NAT 会话表测试。
IPv6 通常没有传统 NAT，但路由器的 IPv6 防火墙也可能维护连接状态，所以也可以测试 IPv6 状态会话保持能力。

## GitHub 构建 APK

1. 上传完整项目到 GitHub 仓库根目录。
2. 打开 `Actions`。
3. 选择 `Build Android APK`。
4. 点击 `Run workflow`。
5. 构建完成后，在 `Artifacts` 下载 `NetSessionTester-v0.2-session-debug-apk`。
6. 解压后安装 APK。

## 项目结构

```text
app/src/main/java/com/demonv/netsessiontester/
├── MainActivity.kt                  UI 界面
├── network/TcpTester.kt             TCP 会话保持测试核心
├── model/TestModels.kt              数据模型
├── data/HistoryStore.kt             历史记录
└── util/CsvExporter.kt              CSV 导出
```

## 注意事项

- APP 不需要 Root。
- 长时间测试时请保持 APP 前台运行。
- 65535 是目标会话数，不是一口气同时创建 65535 个连接；APP 会按批次慢慢累加。
- 测试结果受手机、Wi-Fi、路由器、目标服务器、运营商线路共同影响。
