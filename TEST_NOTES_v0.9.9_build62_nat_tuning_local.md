# v0.9.9 build62 本地测试版：NAT 调优

本包仅用于个人测试，不建议直接发布。

## 改动

1. 首页标题副文案调整
- `TCP 会话保持 · IPv4 / IPv6 分别测试` 改为 `TCP 会话测试 · IPv4 / IPv6 分别测试`。

2. NAT 检测调优
- NAT 检测从单一 STUN 服务器改为多 STUN 基础探测。
- 内置基础 STUN 节点：
  - stun.miwifi.com:3478
  - stun.hot-chilli.net:3478
  - stun.cloudflare.com:3478
  - stun.syncthing.net:3478
  - stun.l.google.com:19302
  - stun1.l.google.com:19302
- 只要任意基础 STUN Binding 成功，就不再判定为 `NAT4 / UDP受限`。
- 基础 STUN 成功但过滤较严格时，显示为 `NAT3 / 端口受限型`。
- 多 STUN 外部端口不一致时，显示为 `NAT3 / 对称型`。
- 只有基础 STUN 全部失败时，才显示 `NAT4 / UDP受限`。

3. NAT 诊断说明优化
- 诊断文案增加 STUN 节点成功数量，例如 `STUN节点 2/5 成功`。
- 区分“基础 STUN 成功”和“UDP 完全受限”，避免把端口受限网络误判为 NAT4。

## 未改动

- versionName 仍为 0.9.9。
- versionCode 仍为 62。
- 不修改发布地址和自动更新逻辑。
- FD Guard、统一释放、Ping AUTO、DNS 国内/国外备用诊断保持上一版逻辑。
