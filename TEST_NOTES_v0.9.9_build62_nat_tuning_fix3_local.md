# NetSessionTester v0.9.9 build62 NAT tuning fix3 local

本版本为个人测试包，不用于正式发布。

## 调整内容

- NAT 对称型显示调整为 `NAT4 / 对称型`。
- 多个基础 STUN 节点全部失败时，不再显示 NAT4，而显示 `UDP受限 / 无法判断`。
- `stun.hot-chilli.net` 在部分网络不可用，已移到 STUN 列表末尾作为兜底，不再作为优先节点。
- 增加更多基础 STUN 节点：
  - stun.miwifi.com
  - stun.voipstunt.com
  - stun.voipbuster.com
  - stun.internetcalls.com
  - stun.voip.aebc.com
  - stun.fitauto.ru
  - stun.cloudflare.com
  - stun.syncthing.net
  - stun.l.google.com
  - stun1.l.google.com
  - stun.hot-chilli.net
- STUN 响应增加 transaction id 校验，减少串包/迟到回包导致的误判。
- 基础 STUN 成功但可用节点不足以确认对称型时，优先显示 `NAT3 / 端口受限型`，并将映射行为标记为 `端口变化待确认`。

## 判断规则

- 任意基础 STUN 成功：说明 UDP 基础可用，不再判定为 UDP 完全受限。
- 3 个及以上 STUN 成功且外部端口变化：显示 `NAT4 / 对称型`。
- STUN 成功但端口保持：显示 `NAT3 / 端口受限型`。
- STUN 成功但成功节点不足，端口变化证据不足：显示 `NAT3 / 端口受限型`，置信度降低。
- 所有基础 STUN 均失败：显示 `UDP受限 / 无法判断`。
