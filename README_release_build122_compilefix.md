# NetSessionTester V1.1.15 build122 编译修复包

- versionName: V1.1.15
- versionCode: 122
- Release tag: v1.1.15-122

## 修复

1. 修复 `MainActivity.kt:1174 Unsupported escape sequence`，将 ping 延迟解析正则改为 Kotlin raw string。
2. 漫游测试网络事件区文案改为：`网络事件 / 能力变化 / 链路变化`。
3. ConnectivityManager 事件显示更具体：
   - 网络事件：默认网络可用、网络丢失/可能切换
   - 能力变化：WiFi/蜂窝/VPN、Internet、验证状态、计费状态
   - 链路变化：接口、DNS 数量、MTU、IPv6 状态

## 发布

继续使用同一个 Release：`v1.1.15-122`。
APK 文件名保持：`NetSessionTester-V1.1.15-build122-signed.apk`。
