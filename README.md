# NetSessionTester

当前版本：V1.1.2-internal build112 NAT strict-order selftest

本自测包修复 NAT 检测状态机顺序：过滤行为测试先于映射测试，避免提前向备用地址发包导致 NAT3 被误判成 NAT1。
