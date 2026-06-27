# V1.1.3 build113 hotfix 测试重点

1. 打开 NAT 类型检测，RFC5780 添加多个 STUN 服务器，退出 App 后重新进入，应保留自定义列表。
2. 切换 RFC3489，添加/删除服务器，退出 App 后重新进入，应保留自定义列表。
3. 输入不带端口的服务器，例如 stun.miwifi.com，保存后应规范化为 stun.miwifi.com:3478。
4. 版本号仍为 V1.1.3，versionCode 仍为 113。
