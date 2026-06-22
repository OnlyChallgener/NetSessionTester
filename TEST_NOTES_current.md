# v0.9.9 build88 测试重点

1. 安装 build87 后检测更新，应提示 build88；安装 build88 后不应反复提示。
2. Release Tag 必须为 v0.9.9-88，APK 文件名必须为 NetSessionTester-v0.9.9-build88-signed.apk。
3. 400/500/800/1000 CPS 复测 IPv6，观察是否仍固定卡在 9000-10000。
4. 6000 以上失败达到 360 时应释放；FD 到约 32360 应立即释放。
5. 已释放后 CPS 卡片应显示平均CPS，而不是最后一秒 0/s。
