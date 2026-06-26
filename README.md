# NetSessionTester

当前版本：V1.0.6-internal build106

NetSessionTester 是一款 Android TCP 会话测试、NAT 诊断、Ping 质量监控工具。

## 本版自测重点

- 设置页目标与模式合并，减少卡片数量。
- 网络信息卡支持折叠，减少首页空间占用。
- Ping 日志支持持久化保存最近 5 次测试。
- Ping 日志弹窗采用 OneUI 卡片式汇总与折叠记录。
- NAT 检测采用更清晰的兼容口径：多节点端口保持显示 NAT1 / 全锥形，同时保留过滤行为验证状态。
- 首页和设置页卡片支持长按拖动排序。

## 构建产物

GitHub Actions 生成：

```text
NetSessionTester-V1.0.6-internal-build106-signed.apk
```

此版本为自测版，不建议作为正式 Release 推送给普通用户。
