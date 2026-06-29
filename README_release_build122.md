# NetSessionTester V1.1.15 build122 发布说明

- versionName: V1.1.15
- versionCode: 122
- Release tag: v1.1.15-122
- APK 文件名：NetSessionTester-V1.1.15-build122-signed.apk

## 重点修复

- 首页网络信息轻量刷新时，延迟从完整 NAT/STUN 诊断中拆出，单独执行轻量检测。
- 正常网络下延迟应显示 ms，不应长期显示“不可用”。
- NAT 类型仍保持手动诊断，不由首页刷新自动误判。

## 发布后验证

1. 手机安装后应用版本为 V1.1.15 build 122。
2. 首页点击刷新，延迟卡片能显示 ms。
3. NAT 类型仍为待检测，点击 NAT 诊断后才进入测试。
4. update.json 指向 v1.1.15-122 的 Release APK。
