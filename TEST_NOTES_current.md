# V1.1.15 build145 自测重点

## 版本与发版文件

1. APP 版本应显示 `versionName=V1.1.15`、`versionCode=145`。
2. 根目录只保留当前长期文档：`README.md`、`CHANGELOG.md`、`TEST_NOTES_current.md`。
3. 历史 build 说明应在 `docs/BUILD_HISTORY.md` 中查看，后续发版模板应使用 `docs/RELEASE_TEMPLATE.md`。
4. `update.json` 应指向 `v1.1.15-145` 与 build145 APK。

## 路由追踪

1. 进入 设置 -> 网络信息 -> Tracket 路由追踪，点击开始追踪后应逐跳显示结果。
2. 追踪中再次点击主按钮，应进入暂停状态，按钮显示“继续追踪”，已完成节点不清空。
3. 暂停 5-10 秒期间不应继续新增 hop；页面应保留当前结果。
4. 点击继续追踪后，应从当前进度继续追加后续 hop。
5. 点击停止追踪、返回页面、切换网络或把 APP 切到后台，追踪应安全停止，不应残留后台探测。
6. 对无法解析、系统 ping 不可用、网络不可用等情况，应显示错误行或停止提示，不应闪退。

## 稳定性回归

1. Ping 测试开始、停止、再次开始后，图表和统计不应接续旧会话。
2. 连接数测试开始时，Ping 目标应同步为连接数测试地址。
3. 漫游测试在 WiFi 权限拒绝、非 WiFi 网络、后台/锁屏时应给出提示或安全停止。
4. 长时间测试后历史记录数量仍应受限，页面滚动和图表拖动不应明显卡顿。
5. Android 10、Android 13、Android 15/16 设备上分别检查权限提示、通知权限和前台服务行为。

## 文档清理验证

1. 根目录不应再新增 `README_selftest_buildXXX.md`。
2. 根目录不应再新增 `TEST_NOTES_vX_buildXXX.md`。
3. 每次发版前先更新 `CHANGELOG.md` 与 `TEST_NOTES_current.md`，必要时把历史摘要追加到 `docs/BUILD_HISTORY.md`。
