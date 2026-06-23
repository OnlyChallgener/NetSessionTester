# NetSessionTester V1.0.3 build103

紧急修复：

- versionName: V1.0.3
- versionCode: 103
- 修复 V1.0.2 中 BuildConfig 引用导致的编译失败。
- 顶部版本徽标改为读取 PackageInfo.versionName，避免旧版本显示残留。
- 释放完成通知只走底部白色浮层，不再同时触发黑色 Snackbar。
- 保留长提示内容：本机已释放，路由器会话表可能延迟数秒下降。
- 不改动高性能定速发射核心。

发布：

- Tag: v1.0.3-103
- APK: NetSessionTester-V1.0.3-build103-signed.apk
