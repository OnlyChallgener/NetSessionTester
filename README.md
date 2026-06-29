# NetSessionTester

## V1.1.15 build122

- versionName: V1.1.15
- versionCode: 122
- Release tag: v1.1.15-122
- APK name: NetSessionTester-V1.1.15-build122-signed.apk

### 发布重点

- 修复首页网络信息轻量刷新时延迟显示“不可用”。
- 延迟检测独立于完整 NAT/STUN 手动诊断，避免首页刷新触发 NAT 误判。
- 轻量延迟优先检测国内稳定 IP，失败后回退 TCP 连接延迟。
- 保留 build121 的按钮圆角点击修复、MTU 与漫游测试自测功能。


## V1.1.14 build115

- versionName: V1.1.14
- versionCode: 115
- 首页 NSLookup / Tracket 入口卡片统一为网络信息小卡尺寸与字号。
- 保留 build114 的下载横幅关闭修复、NSLookup 自定义 DNS、Tracket 默认 30 跳。

# NetSessionTester

当前版本：V1.1.3 build113

宽带会话测试器，支持 TCP 会话测试、IPv4/IPv6 分别测试、独立 Ping、高频 Ping、网络信息展示和手动 NAT 诊断。

## V1.1.3 重点

- NAT 检测改为手动诊断，不再在网络信息里主动误判。
- 支持 RFC5780 / RFC3489 两种检测方式。
- STUN 服务器支持多条配置，检测时按顺序自动顺延。
- 检测过程显示当前服务器和测试阶段，便于对照 NatTypeTester / nat_type_detector。
- STUN 地址不写端口时默认 3478。

## Release

Tag: v1.1.3-113
APK: NetSessionTester-V1.1.3-build113-signed.apk

## V1.1.3 build113 hotfix

同版本紧急修复：NAT 手动诊断自定义 STUN 服务器列表会持久化保存，退出 App 后不再恢复默认。


## V1.1.14 build114

发布版：NSLookup 自定义 DNS、DNS 历史、Tracket 默认 30 跳、入口小卡片遮挡修复。
