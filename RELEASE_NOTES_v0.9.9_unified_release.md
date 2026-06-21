# NetSessionTester v0.9.9 unified release/finalize patch

本代码包基于 `NetSessionTester_v0.9.9_update_netinfo_release_fix_signed` 修改，版本号保持 `v0.9.9`。

## 核心改动

1. 所有终止场景统一进入 `releaseAndFinalize()`：
   - 测试完成
   - 失败上限
   - FD / Socket 上限
   - 手动停止
   - 强制释放
   - 网络环境变化
   - 解析失败 / 测试中断

2. 收尾顺序固定：
   - 停止新增连接
   - generation / releaseEpoch +1
   - 取消运行任务、Ping、网络监听
   - 快照并清空 heldSockets
   - UI 立即显示 “已释放”，activeSessions 立即归零
   - 后台多线程 close socket 快照
   - 再保存历史
   - 写日志和本地提示

3. `TcpTester` 新增：
   - `detachForRelease()`：只负责 releaseEpoch +1、快照 heldSockets、立刻清空。
   - `closeDetachedSockets()`：后台分块并发关闭快照 socket。
   - `release()` 保留兼容旧调用。

4. late socket 防回流：
   - `openBatch/openOne` 增加 `expectedEpoch` 判断。
   - 释放开始后，新建成功但晚回来的 socket 会被立即 close，不会再 add 回 heldSockets。

5. 历史保存策略：
   - 失败上限、FD 上限、手动停止、网络变化、正常完成保存历史。
   - 强制释放只清空 UI 和连接，不强制创建新历史。
   - 保存历史异常只写日志，不影响释放结果。

## GitHub 构建

上传整个项目到 GitHub 后，Actions 会生成：

`NetSessionTester-v0.9.9-release-signed-apk`
