# NetSessionTester

NetSessionTester 是一款 Android 网络测试工具，支持连接数测试、Ping 监控、NAT 诊断、NSLookup、MTU 检查、WiFi 漫游监测以及 Traceroute 路由追踪。

当前发布候选版本：`V1.1.16 build146`。

## 发布说明结构

- `CHANGELOG.md`：针对已发布或发布候选版本的正式变更日志。
- `TEST_NOTES_current.md`：当前构建版本的验证清单。
- `docs/BUILD_HISTORY.md`：过往本地自测及发布修复构建版本的简要时间线。
- `docs/RELEASE_TEMPLATE.md`：用于未来发布准备的模板。

请勿添加新的根目录文件（如 `README_selftest_buildXXX.md` 或 `TEST_NOTES_vX_buildXXX.md`）。请将当前的验证详情添加到 `TEST_NOTES_current.md` 中；当构建版本不再是最新时，请将旧版本的构建摘要归档至 `docs/BUILD_HISTORY.md`。

## 构建说明

- 包名和签名配置保持不变。
- `versionCode` 在 `app/build.gradle.kts` 中进行管理。
- 公开更新元数据在 `update.json` 中进行管理。
- 本次检出（checkout）可能不包含 Gradle Wrapper；若本地环境缺少 Gradle，请使用 Android Studio 或配置好的 CI 构建环境。
