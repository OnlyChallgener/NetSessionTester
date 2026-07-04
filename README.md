# NetSessionTester V1.1.15 build133 自测包

本包为自测版本，不建议直接发布。

## 重点
- 小图标改为自定义 Compose Canvas 绘制，不再依赖 Material 图标表达语义。
- Ping 图表切换为 MPAndroidChart 渲染，目标是改善高频曲线性能与拖动流畅度。
- Ping 图表保留实线/虚线/丢包标记逻辑，减少大片红底和碎线毛刺。
- 保留 build132 的超时 ms 自动联动逻辑：改间隔自动推荐超时，仍可手动修改。
