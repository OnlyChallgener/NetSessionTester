# v0.9.9 build62 本地测试版：CPS 调速 / 失败硬终止 / STUN 8 节点

本版本仅用于本地测试，不作为正式发布包。

## 改动

1. CPS 调速修正
- 10 CPS 只作为连续无成功时的短时保护，不再作为长期巡航速度。
- 连续批次无成功时短暂降到 10 CPS。
- 只要仍有成功连接，或 10 CPS 保护超过约 5 秒，会按 10 → 30 → 60 → 100 → 150 → 正常速度阶梯恢复。
- 正常情况下仍按用户设置的 batch / interval 运行。

2. 失败上限硬终止
- 失败数达到用户设置的“失败停”后，立即停止新增连接。
- 最终状态显示“失败上限”，随后走统一 releaseAndFinalize 收尾。
- 调速逻辑不再替代失败上限停止。

3. STUN 节点调整
- STUN 基础探测节点调整为 8 个。
- 移除部分网络明确不可用的 stun.hot-chilli.net。
- 当前 8 个节点：
  - stun.miwifi.com
  - stun.voipstunt.com
  - stun.voipbuster.com
  - stun.internetcalls.com
  - stun.voip.aebc.com
  - stun.fitauto.ru
  - stun.cloudflare.com
  - stun.syncthing.net

## 未改动

- 版本名仍为 v0.9.9。
- versionCode 仍为 62。
- 不修改发布和自动更新逻辑。
- FD Guard、统一释放、DNS 国内/国外备用诊断保持不变。
