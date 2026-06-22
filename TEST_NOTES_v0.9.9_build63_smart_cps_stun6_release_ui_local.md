# NetSessionTester v0.9.9 build63 local test notes

本版基于 `NetSessionTester_v0.9.9_build62_local_cps_fixed_batch120` 修改，仍保持 `versionName = 0.9.9`，`versionCode = 63`。

## 主要改动

1. NAT/STUN 检测
   - 改为 6 个主 STUN + 2 个备用 STUN。
   - 主节点：cloudflare、miwifi、voipstunt、voipbuster、internetcalls、aebc。
   - 备用节点：fitauto、qq。
   - 移除 `stun.syncthing.net`。
   - 主节点成功 5 个可提前结束；主节点不足 4 个才启用备用。

2. 连接数测试
   - `batchSize` 字段改作“起始 CPS”使用，默认 120。
   - 根据最近 3 秒成功率/失败率动态加速或降速。
   - CPS 上限按连接数分段：200 / 400 / 700 / 1000。
   - 新增无增长确认，减少尾部 1-2 CPS 或 10 CPS 长时间拖尾。
   - 连续失败和累计失败改为按峰值动态计算。

3. FD 保护
   - FD 保护线固定到 32500 附近。
   - 接近安卓 FD 上限时主动停止新增，避免进程 FD 被打满后 UI 不同步或闪退。

4. 释放和 UI 同步
   - 新增 `RunPhase` 状态机。
   - 新增 `ReleaseUiState`。
   - 释放阶段不再提前显示“已释放”。
   - 分批关闭 Socket，并显示圆环进度、释放速度、预计剩余时间和步骤状态。
   - 释放完成后才恢复开始按钮。

5. 渲染和日志
   - 曲线继续 1 秒采样，最多保留 180 个点。
   - 运行日志限制为 1 秒一条统计日志。
   - 释放日志也限制为 1 秒一条进度日志。
   - UI 风格保持 Material3 + One UI 的大圆角、轻阴影、柔和色块。

## 预期效果

- NAT 解析多数情况下 1.5 - 2.5 秒完成。
- 1500/2000 网络减少尾部拖延。
- 32760 FD 上限网络预计 55 - 75 秒触发保护停止。
- 32500 左右连接释放目标 3 - 6 秒，偏慢 6 - 10 秒。
- 释放中开始按钮保持禁用，避免重复开始导致状态错乱。

## 未做事项

- 测试任务主体仍在当前 UI 协程体系内，ForegroundService 主要负责通知保活。
- 如需更强后台稳定性，下一步建议把 TestEngine 移入 ForegroundService 或单例 TestController。
