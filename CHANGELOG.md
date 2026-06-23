# v0.9.9-test93

- versionCode = 93
- versionName = v0.9.9-test93
- 非阻塞流水线固定 CPS 发射核心。
- pending 上限 CPS×4，1000..8000。
- 修复高 CPS 下整批 awaitAll 拖慢平均 CPS 的问题。
- 释放改用 SO_LINGER(0) 快速 close。
- 自测包不发布，update.json 仍指向 build88。
