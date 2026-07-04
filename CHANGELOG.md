# CHANGELOG

## V1.1.15 build133
- 新增自定义 Compose Canvas 图标体系，替换网络信息、目标模式、端口、NAT、DNS、NSLookup、MTU、漫游、Ping、连接数测试等小图标。
- NAT 类型图标支持 NAT1/NAT2/NAT3/NAT4 数字化防护墙显示。
- Ping 图表改为 MPAndroidChart View 渲染，开启硬件图层，减少高频绘制压力。
- Ping 折线按连续成功、少量缺口虚线桥接、长缺口断开、超时/丢包底部红点显示。
- 保留 Ping 超时自动推荐和可手动覆盖。
