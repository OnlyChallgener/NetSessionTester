# NetSessionTester v0.9.9 build 62

本版继续保持 `versionName = 0.9.9`，`versionCode = 62`。

## FD / Socket 上限闪退保护

- 新增 FD 保护预留：接近 `/proc/self/limits` 的 `Max open files` 前提前停止新增连接。
- 新增堆内存保护：可用堆内存低于保护阈值时提前停止，避免 OutOfMemory 附近闪退。
- `openOne()` 增加 last-chance 保护：`SocketException / OutOfMemoryError / 其他 Throwable` 均会归类为错误结果，不再直接冲垮测试协程。
- `openBatch()` 增加批次保护：单批最多 512 个并发任务，避免极端参数导致协程/FD 瞬时冲击过大。
- 触发 FD 或资源保护后仍统一走 `releaseAndFinalize()`：先 UI 已释放，再后台 close socket，再保存历史。
- 后台 close 异常不会影响 UI 已释放状态，也不会阻断历史保存。

## 发布建议

Release tag 建议使用：

```text
v0.9.9-62
```

APK 文件名建议：

```text
NetSessionTester-v0.9.9-build62-signed.apk
```

`update.json` 已同步为 `versionCode = 62`。
