# NetSessionTester V1.1.15 build133 compilefix 自测包

本包为 build133 编译修复版，不建议直接发布。

## 修复
- 修复 `MainActivity.kt:6017`：Canvas 文本绘制中 `Paint.color` 与 Compose `Color` 参数重名导致 `val cannot be reassigned` 和类型不匹配。
- 修复 `MainActivity.kt:9599`：MPAndroidChart `YAxis` 设置位置 API 改为 `setPosition(...)`。

## 保留
- 小图标改为自定义 Compose Canvas 绘制，不再依赖 Material 图标表达语义。
- Ping 图表切换为 MPAndroidChart 渲染。
- Ping 图表保留实线/虚线/丢包标记逻辑。
- Ping 间隔变更时自动推荐超时，超时仍可手动修改。
