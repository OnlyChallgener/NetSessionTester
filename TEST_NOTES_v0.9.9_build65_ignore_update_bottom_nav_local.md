# NetSessionTester v0.9.9 build65 local test

本地测试版，不更新 `update.json`，不用于推送更新。

## 改动

1. 更新弹窗新增“忽略本版”。
   - 忽略后记录当前 `versionCode`。
   - 自动检测不再弹出当前版本。
   - 手动点击“检测更新”仍会再次弹出。
   - 下一个更高 `versionCode` 会重新自动提醒。

2. 底部栏字体遮挡修复。
   - 不增加底部栏高度。
   - 去掉固定 86dp 宽度，改为三栏等宽自适应。
   - 内部选中胶囊高度改为 50dp，避免在 58dp 底栏内被垂直裁切。
   - 移除底栏内部 navigationBarsPadding，避免安全区 padding 挤压文字。

3. 保留 build64 的智能 CPS、FD 32000 保护、顶部确认止损、释放图形进度。
