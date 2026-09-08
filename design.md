# NetSessionTester 设计规范 (Design Specification)

> **设计哲学**：严格遵循 Apple Human Interface Guidelines (HIG) 最新美学，融合 **iOS 26 / macOS Tahoe** 设计语言，打造兼具极简美感与专业性能的高级网络工具体验。  
> 核心原则：**极简干净、分层通透、8pt网格、统一圆角、柔和弹簧动效、无障碍降级保障**。

---

## 目录
1. [设计总则与红线规范](#1-设计总则与红线规范)
2. [材质系统：轻微毛玻璃与微量液态玻璃](#2-材质系统轻微毛玻璃与微量液态玻璃)
3. [色彩系统与深浅模式自适应](#3-色彩系统与深浅模式自适应)
4. [排版与文字清晰度规范](#4-排版与文字清晰度规范)
5. [8pt 空间网格与统一圆角体系](#5-8pt-空间网格与统一圆角体系)
6. [动效与触感：物理弹簧动效规范](#6-动效与触感物理弹簧动效规范)
7. [折线图与数据可视化美学](#7-折线图与数据可视化美学)
8. [多端统一与分级渲染架构 (CMP Multiplatform)](#8-多端统一与分级渲染架构-cmp-multiplatform)
9. [无障碍降级与性能兜底保障](#9-无障碍降级与性能兜底保障)

---

## 1. 设计总则与红线规范

### 1.1 核心设计理念
- **Content First（内容第一）**：网络性能测试是精密工程工具，界面绝不喧宾夺主。界面元素应如同精心打磨的无色光学镜片，让数据（CPS、丢包率、延迟、折线趋势）清晰浮现。
- **Layered Translucency（分层通透）**：利用有限的 2~3 个视觉深度层级（Background -> Canvas Base -> Floating Glass Surface -> Modal Sheet）呈现纵深感，杜绝扁平沉闷，但严控视觉杂乱。
- **Natural Tactility（真实微触感）**：控件具备接近物理世界微小惯性与阻尼反馈，交互自然平稳。

### 1.2 严格禁止项（红线）
❌ **严禁重阴影**：禁止使用大不透明度、黑色直投的生硬阴影。  
❌ **严禁粗分割线**：禁止使用 >1dp 的粗分割线或大反差边框。优先使用留白空间（Whitespace）和微弱底色差自然分界。  
❌ **严禁高饱和度荧光色与彩虹渐变**：严禁过度饱和的霓虹灯效、夸张的多色彩虹渐变光斑。玻璃只允许极微弱单色或自然冷暖微光折射。  
❌ **严禁廉价塑料质感**：禁止大反光白块、假高光贴图、过度模糊导致的浑浊污斑。  
❌ **严禁混乱尺寸**：禁止脱离 8pt 网格使用任意奇数或随意定义的间距与内边距。  
❌ **严禁文字被背景吞没**：任何毛玻璃表面必须保证文字可读性，WCAG AA/AAA 对比度要求为绝对底线。

---

## 2. 材质系统：轻微毛玻璃与微量液态玻璃

### 2.1 玻璃物理特性指标
| 属性 | 规范值 | 设计意图 |
| :--- | :--- | :--- |
| **模糊半径 (Blur Radius)** | `16.dp ~ 24.dp` | 适度散焦背景元素，保留背景色彩晕染但消除形状干扰 |
| **材质底色透光度 (Surface Alpha)** | 浅色: `0.70 ~ 0.82` / 深色: `0.65 ~ 0.75` | 避免玻璃过透导致底层文字重叠；避免过厚导致失去通透感 |
| **倒角微高光边框 (Specular Bevel Border)** | `0.5.dp ~ 1.0.dp` 线性渐变 | 模拟真实精密玻璃倒角。顶端入射光微亮，底端渐隐融入底色 |
| **色散控制 (Dispersion)** | **极低 (<0.02)** | 杜绝产生彩虹边缘（Chromatic Aberration），呈现冷透如水质感 |
| **噪点质感 (Micro Grain)** | `1.5% ~ 2.0%` 微量高斯噪点 | 消除大范围模糊在移动端 OLED / LCD 上的色彩色阶断层 (Color Banding) |

### 2.2 玻璃边框高光画笔公式 (Specular Border Brush)
```kotlin
// 浅色模式玻璃边缘高光
val LightGlassBorder = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = 0.60f), // 顶部入射高光
    0.3f to Color.White.copy(alpha = 0.20f),
    1.0f to Color.Black.copy(alpha = 0.04f)  // 底部微暗收敛
)

// 深色模式玻璃边缘高光
val DarkGlassBorder = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = 0.24f), // 顶部边缘微反光
    0.4f to Color.White.copy(alpha = 0.08f),
    1.0f to Color.White.copy(alpha = 0.02f)
)
```

### 2.3 弥散环境阴影 (Ambient Diffuse Shadow)
替换传统直投阴影，采用双层微阴影：
- **接触微阴影 (Key Shadow)**：Offset `(0.dp, 1.dp)`，Blur `2.dp`，`Color.Black.copy(alpha = 0.03f)`。
- **环境弥散阴影 (Ambient Shadow)**：Offset `(0.dp, 8.dp)`，Blur `24.dp`，`Color.Black.copy(alpha = 0.06f)`。

---

## 3. 色彩系统与深浅模式自适应

遵循 Apple iOS 26 / macOS Tahoe 动态语义色彩体系，所有色彩均自动感知系统深浅色切换：

| 语义色彩 Token | 浅色模式 (Light) | 深色模式 (Dark) | 适用场景 |
| :--- | :--- | :--- | :--- |
| `SystemBackground` | `#F2F2F7` (iOS 灰白底) | `#000000` (Pure Black / 极夜黑) | 顶层根画布背景 |
| `GlassSurfaceBase` | `#FFFFFF` @ 75% Alpha | `#1C1C1E` @ 70% Alpha | 毛玻璃卡片与容器底色 |
| `GlassSurfaceElevated` | `#FFFFFF` @ 88% Alpha | `#2C2C2E` @ 80% Alpha | 弹窗、ActionSheet、悬浮顶底栏 |
| `LabelPrimary` | `#000000` @ 100% | `#FFFFFF` @ 100% | 核心指标（当前CPS、关键数值） |
| `LabelSecondary` | `#3C3C43` @ 60% Alpha | `#EBEBF5` @ 60% Alpha | 次要标签、单位说明、表单描述 |
| `LabelTertiary` | `#3C3C43` @ 30% Alpha | `#EBEBF5` @ 30% Alpha | 占位符、微小辅助文本、禁用态 |
| `SystemAccentBlue` | `#007AFF` (经典蓝) | `#0A84FF` (明澈蓝) | 主操作按钮、进行中折线、高亮状态 |
| `SystemSuccessGreen` | `#34C759` (翠竹绿) | `#30D158` (薄荷绿) | 测试成功数、健康指标、正常状态 |
| `SystemWarningOrange` | `#FF9500` (琥珀橙) | `#FF9F0A` (亮暖橙) | 延迟偏高、丢包预警、重试提示 |
| `SystemCriticalRed` | `#FF3B30` (朱砂红) | `#FF453A` (亮珊瑚红) | 测试失败、异常断开、停止操作 |
| `SeparatorHairline` | `#3C3C43` @ 12% Alpha | `#545458` @ 20% Alpha | 0.5dp 极细微隐形分割线 |

---

## 4. 排版与文字清晰度规范

### 4.1 字体家族
- **苹果生态 (iOS / macOS)**：优先使用系统原生 **SF Pro**（标题与正文）与 **SF Mono**（网络端口、IP 地址、数据包字节数等定宽数据）。
- **非苹果平台 (Android / Windows)**：使用高质量无衬线体作为优雅对齐降级（Android: Roboto / Inter；Windows: Segoe UI Variable）。

### 4.2 文字层级与间距体系
| 层级 Token | 字号 (Size) | 行高 (LineHeight) | 字重 (Weight) | 字符间距 (Tracking) | 适用场景 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `MetricMega` | 36.sp | 42.sp | Bold / SemiBold | -0.5.sp | 测试大盘实时并发数字、核心 CPS |
| `TitleLarge` | 22.sp | 28.sp | SemiBold | -0.2.sp | 页面主标题、大卡片标题 |
| `Headline` | 17.sp | 22.sp | SemiBold | -0.1.sp | 列表组标题、设置项名称 |
| `BodyRegular` | 15.sp | 20.sp | Normal (Regular) | 0.sp | 正文内容、诊断状态说明 |
| `LabelMono` | 13.sp | 16.sp | Medium | 0.2.sp | IP 地址、DNS 结果、RTT 毫秒数 (等宽) |
| `Caption` | 12.sp | 16.sp | Regular | 0.sp | 辅助说明、时间戳、版本信息 |

---

## 5. 8pt 空间网格与统一圆角体系

整个界面的间距、尺寸、触控靶心与外形严格对齐 **8pt 网格（8-Point Grid System）**。

### 5.1 空间间距规范
- **4.dp (0.5x)**：紧密关联元素（图标与对应小红点、文本与单位后缀）。
- **8.dp (1x)**：紧凑间隙（卡片内部紧邻的两个状态胶囊）。
- **16.dp (2x)**：标准间距（标准卡片内边距 Padding、相邻表单控件间隙）。
- **24.dp (3x)**：模块间距（卡片与卡片之间的垂直落差、页面水平外边距）。
- **32.dp (4x)**：大区块间距（标题栏与主体内容区、大图表与控制面板之间）。

### 5.2 统一圆角规范 (Continuous Smooth Corner Radii)
统一采用 Apple 风格的连续平滑圆角曲率（Squircle / Continuous Corner）：
- **`RadiusSmall (8.dp)`**：小型标签胶囊、状态小标、小型输入框。
- **`RadiusMedium (14.dp)`**：次级卡片、操作按钮、工具项卡片。
- **`RadiusLarge (20.dp)`**：核心主卡片（测试仪表盘、折线图大卡片）。
- **`RadiusModal (28.dp)`**：底部抽屉 Sheet、主模态弹窗。
- **`RadiusPill (999.dp)`**：搜索栏、过滤切换胶囊（Segmented Control）。

---

## 6. 动效与触感：物理弹簧动效规范

摒弃机械的线性（Linear）或普通缓动（Ease-in-out），全面拥抱**基于真实物理弹簧（Spring Physics）**的自然微动效。

### 6.1 弹簧参数配置
```kotlin
object AppleSpringSpecs {
    // 快速响应（微交互：按钮微缩放、Tab 切换选中指示器）
    val Snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy, // 0.75f 微小回弹
        stiffness = Spring.StiffnessMediumLow         // 400f
    )

    // 平稳沉着（大组件进出场：Sheet 展开、弹窗浮现、页面切换）
    val Smooth = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,  // 1.0f 无多余晃动，极度平滑
        stiffness = Spring.StiffnessLow               // 200f
    )
}
```

### 6.2 交互行为规范
- **压感微缩放 (Press Scale)**：所有可点击的毛玻璃卡片与主按钮，按下时平滑缩放至 `0.975f`，释放时伴随微弱弹力平滑复原。
- **触觉反馈 (Haptics)**：
  - 开始/停止测试：`ImpactFeedback.Medium`
  - 达到峰值/完成测试：`NotificationFeedback.Success`
  - 切换 Segment / 点击 Tab：`SelectionFeedback` 细微机械触感。

---

## 7. 折线图与数据可视化美学

网络压测的折线图是核心视觉焦点，必须遵循以下美学准则：

### 7.1 线条与流光渐变
- **主趋势折线**：线宽固定为 `2.2.dp`，采用抗锯齿圆滑贝塞尔曲线或平滑折线。
- **底部液态光晕填充 (Liquid Glow Fill)**：折线下方绘制垂直渐变透明区域：
  - 顶部紧贴折线处：`SystemAccentBlue.copy(alpha = 0.22f)`
  - 中段过渡：`SystemAccentBlue.copy(alpha = 0.08f)`
  - 底部完全收敛：`Color.Transparent`
- **网格线与刻度**：仅保留横向 3~4 条极微弱参考基准线（`0.5.dp`，`SeparatorHairline`），杜绝密密麻麻的棋盘网格。

### 7.2 动态呼吸峰值标线 (Peak Indicator)
- 历史最高点与当前在飞点采用双层同心圆：
  - 内层：`3.dp` 实心纯白点。
  - 外层：`8.dp` 带 `0.25f` 透明度的强调色呼吸光晕环。

---

## 8. 多端统一与分级渲染架构 (CMP Multiplatform)

支持 **Android、iOS、macOS、Windows** 四大平台，界面与逻辑 90% 共享，图形与材质层自动适配各平台最高质量的硬件着色管线：

```
┌────────────────────────────────────────────────────────────────────────┐
│               Compose Multiplatform 共享层 (commonMain)                 │
│         Apple HIG 设计规范 / 8pt 网格 / 语义色彩 / 状态机与并发调度       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────┐           ┌───────────────┐           ┌───────────────┐
│      iOS      │           │     macOS     │           │ Android & Win │
│   (iosMain)   │           │ (desktopMain) │           │ (androidMain/ │
│               │           │               │           │  desktopMain) │
├───────────────┤           ├───────────────┤           ├───────────────┤
│ 原生液态玻璃   │           │ NSVisualEffect│           │ Haze 引擎 /   │
│ UIBlurEffect  │           │ Window 磨砂   │           │ RenderEffect  │
│ Metal 加速    │           │ 原生红绿灯     │           │ Skia 高级毛玻璃│
└───────────────┘           └───────────────┘           └───────────────┘
```

### 8.1 各平台渲染策略
1. **iOS**：利用 `UIKitView` 桥接原生 `UIVisualEffectView(style: .systemThinMaterial)` 或 Metal Compute Shader，达到与 iOS 原生应用无缝融合的 120Hz ProMotion 顺滑度。
2. **macOS**：集成 `NSVisualEffectView`，利用系统级磨砂窗口材质（Window Background Blurring），完美支持原生浅深色自适应和交通灯标题栏。
3. **Windows**：在 Compose Desktop (Skiko) 驱动下，利用 Skia 离屏多通道高斯模糊 Shader 实现媲美 Windows 11 Mica / Acrylic 的高级毛玻璃卡片质感。
4. **Android**：
   - Android 12+ (API 31+)：使用原生 `RenderEffect.createBlurEffect()` 硬件着色；
   - Android 11 及以下：使用 `Haze` 库并自动降级为高保真半透微噪点卡片，确保帧率稳定 60/120fps。

---

## 9. 无障碍降级与性能兜底保障

为确保极端设备与特殊生理需求用户的卓越体验，系统严格内置降级通道：

### 9.1 辅助功能：降低透明度 (Reduce Transparency)
当用户在系统设置开启“减少动态效果”或“降低透明度”时：
- 所有动态流光背景、毛玻璃模糊效果**立即自动关闭**；
- 毛玻璃卡片平滑切换为**实体不透明高阶表面（Solid Elevated Surface）**；
- 边缘渐变高光边框平滑切换为 `1.dp` 纯净实色边框（`SeparatorHairline`）；
- 保证对比度提升至 WCAG AAA 级别（>7:1）。

### 9.2 低性能与省电模式自动降级 (Battery Saver Fallback)
- 当系统处于省电模式（Power Save Mode）或帧率持续低于 45fps 时：
  - 折线图实时动态微光特效暂停；
  - 背景模糊计算降级为单次静态缓存采样（Snapshot Caching），杜绝重复重绘与发热。

---

> 本规范将作为后续各平台 UI 实现与验收的权威基准。
