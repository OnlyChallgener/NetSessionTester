# NetSessionTester v0.9.9 build79 local test

本地自测版，不推送更新。

## 本版重点

- 失败数硬卡 300，达到后立即停止新增。
- TCP 连接超时固定 3000ms，不再动态变化。
- 手动 CPS 优先级继续提高：用户设置 800/1000/1500 后，普通失败率、增长效率、顶部确认不再改目标 CPS。
- pending 余量继续放大，降低手动 CPS 周期性掉到 0 的概率。
- 优化高 CPS 下 pending 回收逻辑，减少 filter/removeAll/toSet 带来的卡顿。
- activeCount 不再每次扫描并 removeAll 3 万个 socket，降低测试收尾和 IPv4/IPv6 切换卡顿。
- /proc/self/limits 的最大 FD 读取做缓存，减少高连接数阶段额外开销。

## 测试重点

1. 手动设置 800/1000 时，CPS 曲线是否更接近设定值，是否不再频繁掉到底。
2. 失败数是否不会超过 300。
3. IPv6 高容量测试是否能快速接近 FD 上限。
4. 测试收尾、释放连接、IPv4 切 IPv6 是否更流畅。
