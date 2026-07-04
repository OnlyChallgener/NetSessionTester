# CHANGELOG

## V1.1.15 build133 compilefix
- 修复 GitHub Actions 编译失败：NetGlyph 内 Canvas 绘制文字时 `Paint.color` 与 Compose `Color` 参数重名，改为 `this.color = color.toArgb()`。
- 修复 MPAndroidChart YAxis API 调用：`axisLeft.position` 改为 `axisLeft.setPosition(...)`。
- 保留 build133 自定义 Compose Canvas 图标体系。
- 保留 Ping 图表 MPAndroidChart 渲染、实线/虚线/丢包标记逻辑。
- 保留 Ping 超时自动推荐和可手动覆盖。
