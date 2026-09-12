# 多平台修复与 iOS 迁移说明

本轮是代码实现与 Python 静态校验，尚未完成 Kotlin/Native/JVM 编译、安装包验证或真机验收。未在本地安装或运行 Gradle、Xcode、Android SDK 等环境，也未触发远程构建。

## 架构与范围

- Android 保留原 Kotlin/Jetpack Compose 应用和现有工具。没有重写安卓 UI 或测量引擎；清理了已移除入口的双网对测残余。不能据此声称安卓全部功能经过重新验证。
- Windows/macOS 使用 Kotlin/JVM + Compose Desktop，网络传输使用 Java NIO。这是 Kotlin 调用 JVM 系统库，不是把整个项目改写成 Java。两个桌面平台只提供连接数、独立 TCP Ping、连接数与 Ping 联动。
- iOS 使用 Kotlin/Native + Compose 内容页，网络传输使用 POSIX。原生 UIKit 承载四个标签页，无 JVM 运行时。
- `commonMain/core` 共享 CPS 调度、Ping 统计以及 DNS/STUN/ICMP 报文编码规则；操作系统传输和权限留在平台层。桌面与 iOS 采用共享测量规则，安卓仍保留现有实现。
- Compose UI 依赖只放在 iOS/JVM source set，避免本轮桌面/iOS UI 库升级改变安卓原有 Compose UI 依赖。Kotlin 插件仍是工程统一版本，安卓编译需后续回归。

桌面发行流程改为随应用携带 Java 运行时，用户无需另外安装 Java；这符合 [Compose 原生发行包机制](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)。打包配置已更新，旧版依赖外部 Java 的 C# 启动器、fat JAR 打包任务和未使用的旧图表模型已删除。安装包尚未实际生成验证。

## 图表和测量修复

原实现把亚秒样本归入整数秒，相同横坐标连接出了竖线；新一轮测试还可能带入上一轮统计。联动模式反复采集旧 Ping 值且忽略失败，会画出没有新样本支持的水平线。

现在按真实采样事件、单调时钟和毫秒横坐标绘图。启动时重置统计；连接数和 Ping 各自成线；失败探测留缺口与失败标记；IPv4/IPv6 切换不连线。连续成功样本数值确实相同时，水平线仍是合理结果，不人为制造波动。

| 指标 | 本轮定义 |
| --- | --- |
| 主测试 Ping | TCP 建连耗时；主引擎在计时前解析 DNS。不是 ICMP Echo RTT |
| Ping 失败率 | 已完成探测中的超时、拒绝、路由等失败占比；取消不算失败 |
| 抖动 | 相邻成功探测的绝对延迟差均值；失败打断相邻关系 |
| 目标 CPS | 每秒发起连接尝试的目标速率；调度停顿后不会补发无限突发 |
| 显示 CPS | 最近统计窗口内的成功建连速率；不保证等于目标速率 |
| 成功目标 | 累计成功握手数；对端关闭后不无限补连追逐目标 |
| 当前连接 | 仍由客户端持有且未观察到关闭的连接；健康检查有采样间隔 |
| 联动诊断 | 建连压力下的 TCP 延迟对照；未验证带宽饱和，不给 Bufferbloat 等级 |

桌面和 iOS 修复了取消期间的资源释放、重复运行隔离、地址族回退、对端关闭后的活动计数、快速失败时的最终统计。iOS 页面切换或退后台会停止测试并释放保留连接；新任务必须等旧任务结束。15 秒内未清理完成会提示稍后重试，不启动重叠探测。系统同步 DNS 查询不能被保证立即中断。

## Windows/macOS 历史与 4-Tab 架构

桌面端顶部导航栏现已统一为 4-Tab 架构（`DesktopTab`：并发压测、独立 Ping、联动诊断、测试历史），不破坏底层 `AppMode` 枚举定义，同时为历史页面提供原生一级导航入口。沿用 330dp 左栏、12dp 主间距、白色圆角卡片和现有配色；右侧直接复用实时页面的指标卡、双轴图表与日志行。最新迭代补充了鼠标滚轮与拖拽缩放平移 (Zoom & Pan)、自适应紧凑量程 (Tight Auto-Range) 与光标吸附 Tooltip。

- 左侧：最近 100 次记录、目标/结果搜索、模式筛选。
- 右侧：目标、时间、耗时、结束状态和参数；结束时指标、真实曲线、事件日志。
- 开始新测试时清空本轮日志；完成、失败或手动停止后保存。关闭窗口先结束当前测试并等待保存。
- 支持单条/当前列表 CSV 导出、单条删除和确认清空。历史里的活动连接数是结束时快照，不是实时连接状态。
- JSON 存于用户应用数据目录；文件原子替换，损坏文件备份，读写失败在界面提示。尚未执行真实桌面 UI 或重启验证。

## iOS 工具迁移

按用户最新范围，蜂窝工参、WiFi 扫描不做；WiFi 漫游、双网对测暂缓，不引入定位权限或 WiFi entitlement。以下为已接入的核心工具，不代表所有细节与安卓相同，也不代表已在 iPhone 实测通过。

| 功能 | 实现与边界 |
| --- | --- |
| 连接数 / Ping / 联动 | 主测试页；IPv4、IPv6、双栈轮测；开始、停止、保留、释放 |
| NSLookup | 系统解析及指定 DNS 的 UDP A/AAAA 查询；截断响应明确提示，未实现 TCP DNS 回退 |
| NAT | STUN 公网映射；服务器支持时进行 RFC5780 映射/过滤检查；能力不足不猜 NAT 类型 |
| IPv6 | 接口地址、AAAA 解析、IPv6 TCP 可达性 |
| Traceroute | IPv4/IPv6 ICMP 逐跳探测；系统禁止 socket 或目标不响应时明确报告 |
| MTU/PMTU | 非分片探测、Echo/Too Big 响应；超时不直接当作 MTU 过大或确定结果 |
| iPerf3 | TCP 单流上行/下行、服务端结果；不宣称 UDP、多流、认证模式已实现 |
| 负载延迟 | 空载与 12 路 TCP 握手压力对照；全部失败则无结论，不输出伪造延迟或评分 |
| 历史 | 最近 100 次测试，含停止/失败记录、明细查看、CSV 分享、清空 |
| 设置 | 默认目标/端口/CPS/成功目标/失败上限/超时/Ping 周期/地址族/保留连接，本机持久化 |


## Liquid Glass：按 Apple 官方方式接入

原生 `UITabBarController` 承载“测试、工具、历史、设置”。保留系统默认外观，不覆盖标签栏背景；iOS 26 SDK 构建后由系统提供 Liquid Glass，旧系统保持标准标签栏。图表、表单和日志作为内容层保持清晰表面。

依据及对应决策：

1. [Apple HIG — Materials](https://developer.apple.com/design/human-interface-guidelines/materials)：Liquid Glass 主要用于导航和控件层。因此没有给整张图表或所有卡片叠加玻璃。
2. [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)：标准系统组件随新 SDK 采用新设计；避免自定义背景干扰。因此采用原生标签栏，而不是用透明渐变模拟玻璃。
3. [WWDC25 — Build a UIKit app with the new design](https://developer.apple.com/videos/play/wwdc2025/284/)：使用系统导航适配新设计。标签栏由系统处理外观和辅助功能；内容区域使用安全区域避让。

构建配置选择 Xcode 26.0、Kotlin 2.2.21、Compose Multiplatform 1.9.3。Kotlin 对 Xcode 26 的兼容说明见 [Kotlin 2.2.20/2.2.21 更新说明](https://kotlinlang.org/docs/whatsnew2220.html)。原生入口通过 [Objective-C 类型导出](https://kotlinlang.org/docs/native-objc-interop.html) 调用 `NSTAppFactory`，不再以不受支持的 C 对象返回符号猜测方式启动。

[CMP-9827](https://youtrack.jetbrains.com/issue/CMP-9827) 有该 Compose/Kotlin 组合下的页面销毁崩溃报告；维护者曾表示无法复现，记录没有可追溯的最小稳定修复版本，本轮未据此盲目升级。需把页面销毁纳入原生验收。

系统玻璃并未在本机渲染验收，需确认 iPhone/iPad、深浅色、降低透明度、提高对比度、大字体和旋转后的实际表现。

## 验证与交付边界

本地只运行 Python：

```text
python -m unittest discover -s docs/validation -p 'test_*.py' -v
```

本轮共 43 项通过，包括 Kotlin 源结构/调用契约、任务资源管理契约、桌面历史存储、已移除功能残留、协议样例、独立测量规则样例和打包 plist 检查。另使用 Python 解析工作流 YAML。这些检查**不执行 Kotlin 或 Objective-C，不验证原生类型签名，也不证明真实网络结果正确**。

必须保留的后续验收：

- 各平台编译及依赖解析、Android 原有功能回归；Windows 无 Java 的干净机器、macOS 应用启动/退出。
- iOS Framework/原生入口链接、页面切换/销毁、权限拒绝、退后台、连续开始停止、ICMP socket 可用性、iPerf3 服务端互通。
- 使用相同目标 IP、地址族、端口、周期和网络，与安卓对照；独立测量与有连接压力时分开比较。
- iOS 临时签名 IPA 没有开发者 provisioning profile，不能视为直接可安装发行包。macOS 包尚未签名公证。
