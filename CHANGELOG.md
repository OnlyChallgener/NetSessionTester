# Changelog

## v0.9.9 build72 local
- 修复 CPS 曲线断点，恢复连续橙线。
- 提高 Running / FD 接近区 CPS 下限，避免高连接数阶段长期低 CPS 慢跑。
- 32200+ 进入限时顶部确认，TopConfirm CPS 固定 16~32。
- FD 附近确认不再被零散增长反复重置，减少高连接数测试长尾。

## v0.9.9 build71 local
- 增加低容量快速确认和增长效率判断。
- 手动新增值作为手动 CPS 上限。

## v0.9.9 build70 local
- 顶部确认 CPS 调整到 16~32。
