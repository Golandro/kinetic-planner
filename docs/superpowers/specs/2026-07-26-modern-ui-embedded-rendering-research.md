# Modern UI 嵌入式渲染可行性调研报告

> 日期：2026-07-26
> 状态：调研结论（用于决策路径选择）
> 范围：评估 Modern UI（BloCamLimb）能否用于在 KP Mixin 注入的外部 Screen `GuiGraphics` 之上做嵌入式 UI 渲染。
> 关联：`2026-07-26-config-panel-flat-gui-design.md`、`STATUS.md`。

---

## 0. 执行摘要（先看结论）

**结论：Modern UI 不适合 KP 的嵌入场景，推荐 Path C（Catnip + 自定义控件）。**

核心原因（一句话）：Modern UI 的整个 View 树通过 `ViewRootImpl` 绑定到其自有的 `Canvas`/`Surface`（Arc3D + BLASTFramebuffer），而 `Canvas` 与 MC 的 `GuiGraphics` 是**两套并行、互不相交的渲染管线**；框架未公开任何"把一棵 View 子树栅格化到给定 `GuiGraphics`"的 API，KP 想要在 Xaero/JourneyMap 的 `GuiGraphics` 上叠画控件的目标在 Modern UI 的架构下没有官方支持路径。

三条路径的裁决：

| 路径 | 是否可行 | 主要代价 | 建议 |
|---|---|---|---|
| **A. Modern UI** | ❌ 不支持嵌入渲染到外部 `GuiGraphics` | 需深度 Mixin/反射改写渲染管线，维护成本极高，且违反其 `@ApiStatus.Internal` 边界 | **否决** |
| **B. Owo UI + Forgified Fabric** | ✅ 有 `OwoUIAdapter` 嵌入式 API | 引入 Forgified Fabric API（FFAPI）整套运行时依赖；与 Create/Catnip 字体管线可能冲突 | 备选（仅在 Catnip 不可用时） |
| **C. Catnip + 自定义控件** | ✅ 渲染原语天然适配 `GuiGraphics` | 需要自己写复选框/步进器等基础控件 | **采纳** |

下文以原始 10 个问题为骨架给出具体证据。

---

## 1. 是否支持 MC 1.21.1 NeoForge

**支持。**

- 仓库 `BloCamLimb/ModernUI-MC` 的 README 兼容矩阵明确列出：

  | Minecraft | 状态 | 最新 Modern UI | 加载器 |
  |---|---|---|---|
  | 1.21 ~ 1.21.1 | ⚠️ Legacy | 🟢 3.13.0.1 | NeoForge, Forge, Fabric |

- KP `build.gradle` 当前依赖 `maven.modrinth:modern-ui:3.12.0.2`（开发软依赖），`STATUS.md` 也已锁定 3.12.0.2；3.13.0.1 是同一线 1.21.1 的更新版，可平滑升级。
- Mixin 配置 `mixins.modernui-neoforge.json` 存在 `neoforge` 源集，证明 NeoForge 是一等公民。
- **不确定项**：3.13.0.x 与 3.12.0.x 在 API 表面（如 `GuiGraphicsExtractor` 重命名）有差异；KP 当前 dev 环境锁的是 3.12.0.2，需注意版本-API 对应。但这不影响"嵌入不可行"的结论。

## 2. 是否能嵌入渲染到外部 Screen 的 `GuiGraphics`

**不能。这是本调研最关键的负面结论。**

### 2.1 渲染管线的根本架构

Modern UI 的渲染分两条独立管线：

1. **MC 原生管线**：`GuiGraphics` → `RenderType` → `BufferSource` → MC 着色器（`GameRenderer::getPositionColorShader` 等）
2. **Modern UI 管线**：`ViewRootImpl` → `WindowGroup`/`View` 树 → `Canvas`（`ArcCanvas`）→ Arc3D `Granite` 渲染器 → 自有 `BLASTFramebuffer`/`Surface`

`UIManager` 源码（`icyllis.modernui.mc.UIManager`）显示：

```java
// 摘自 UIManager（@ApiStatus.Internal）
protected volatile ViewRootImpl mRoot;
protected WindowGroup mDecor;
private GlTexture_Wrapped mLayerTexture;          // Modern UI 自己的图层纹理
private GpuTexture mLayerTexture_Vulkan;
// ...
@RenderThread public static void initializeRenderer() {
    Core.requireImmediateContext();  // Arc3D 上下文，与 MC RenderSystem 并行
}
```

`ViewRootImpl` 持有的 `mSurface` 是 Arc3D 的 `Surface`（即 `BLASTFramebuffer` 的封装），不是 MC 的 `GuiGraphics`。View 树通过 `Canvas` 绘制到这个 surface，再由 `UIManager` 在帧末把 `mLayerTexture` 一次性 blit 到 MC 主 framebuffer 上。

### 2.2 Mixin 注入点不支持"嵌入"

`mixins.modernui-neoforge.json` 中针对 `Screen`/`GuiGraphics` 的 Mixin 主要做两件事：

- `MixinScreen#renderBackgroundInWorld`（`@Redirect`）：把 `Screen.renderBackground` 中的 `fillGradient` 调用重定向到 `BlurHandler.drawScreenBackground`，实现背景模糊/暗化。
- `MixinGuiGraphics`：调整 `RenderSystem` 状态、注入文本布局钩子。

`BlurHandler.drawScreenBackground(@Nonnull GuiGraphicsExtractor gr, ...)` 的确接收 `GuiGraphics` 参数，但它做的是"在 `GuiGraphics` 上做最底层的 `fill` / `blur` 操作"——**没有任何 View 树参与**。这只能算"Modern UI 修改 MC 的背景渲染"，不能算"Modern UI 的控件嵌入到 `GuiGraphics`"。

### 2.3 没有 public API

- `UIManager`、`ViewRootImpl`、`WindowGroup` 全部标注 `@ApiStatus.Internal`。
- `Canvas` 是抽象类，由 `ArcCanvas` 实现，绑定到 `RecordingContext`/`Surface`，构造需 Arc3D 上下文。
- 文档与 javadoc 中均无"render(View, GuiGraphics)"或"OwoUIAdapter 式的 createWithoutScreen"等价 API。
- 即使强行反射调用 `View.draw(Canvas)`，得到的也是绘制到 Modern UI 内部 surface 的指令，不会出现在 KP 拿到的那张 `GuiGraphics` 上。

### 2.4 与 KP 嵌入场景的对齐情况

KP 的注入点是 `Xaero's GuiMap.render(gg, ...)` 的 `@At("RETURN")`，拿到的是 `gg` 这张 `GuiGraphics`。要在它上面画 Modern UI 控件，必须满足以下任一条件：

| 条件 | Modern UI 是否满足 |
|---|---|
| (a) 提供"把 View 子树画到给定 `GuiGraphics`"的 API | ❌ |
| (b) 提供把 View 子树画到任意 `PoseStack`/`BufferSource` 的 API | ❌ |
| (c) 提供"View 子树 → `GuiGraphics` render layer"的桥接 | ❌ |
| (d) 让 MC 把 Modern UI 的整层 `mLayerTexture` 在指定时机 blit 到 `gg` | ⚠️ 理论可行但要 Mixin Modern UI 自身，且 `mLayerTexture` 是全屏图层、不是局部面板，与"嵌入面板"语义不符 |

**结论**：Modern UI 的设计哲学是"接管整屏"，不是"在别人屏里塞一块"。它要么整体替代 Screen（`MuiScreen`），要么不出现。

## 3. 组件库

**组件丰富，但与嵌入能力无关。**

`icyllis.modernui.widget` 包提供的组件（部分）：

- `Button`、`CheckBox`、`RadioButton`、`Switch`
- `SeekBar`、`AbsSeekBar`（滑块）
- `EditText`（含 Undo/Redo、Unicode 词迭代器）
- `Spinner`（下拉）、`AbsListView`/`ListView`（虚拟化列表）
- `ExpandableListView`、`GridView`
- `ScrollView`、`HorizontalScrollView`
- `TabHost`/`TabWidget`、`Toolbar`
- `DatePicker`、`TimePicker`、`CalendarView`
- `Toast`、`Tooltip`、`PopupMenu`、`ContextMenu`

KP 配置面板需要的"复选框 / 步进器 / Tab / tooltip"全部有现成实现。**但所有这些控件都继承自 `View`，必须挂在 `ViewRootImpl` 下、经 `Canvas` 渲染**——见第 2 节，无法直接服务于嵌入式场景。

## 4. 布局系统

完整对齐 Android View 体系：

- `LinearLayout`（horizontal/vertical）
- `FrameLayout`、`GridLayout`、`RelativeLayout`
- `CoordinatorLayout`、`AbsoluteLayout`（已弃用）
- `LayoutParams` 体系（`match_parent`/`wrap_content`/权重）
- `onMeasure`/`onLayout` 二阶段测量
- `requestLayout`/`invalidate` 失效传递

布局能力一流，但同样**只在 Modern UI 的 View 树内有效**，对外部 `GuiGraphics` 无意义。

## 5. 事件系统

- 输入：`UIManager` 在 `RenderThread`/`UiThread` 上接收 MC 的 `KeyboardHandler`/`MouseHandler` 事件，通过 `MuiModApi.addOnPreKeyInputListener` 等钩子分发到 `ViewRootImpl`，再走 Android 风格的 `dispatchTouchEvent`/`dispatchKeyEvent` 链。
- 滚动：`OnScrollListener`、`View.onScrollChanged`。
- 焦点：`View.onFocusChanged`、`FocusFinder`。
- 生命周期：`LifecycleOwner`/`LifecycleRegistry`、`FragmentController`。

**关键问题**：事件分发由 `UIManager` 在 Screen 切换时挂载（`onScreenChange` → `BlurHandler.blur(newScreen)`）。当 KP 在 Xaero 的 `GuiMap` 上做 Mixin 注入时，**Modern UI 并不知道这层 Screen 的存在**（除非把它包成 `MuiScreen`，那等于把整屏让给 Modern UI），事件无法路由到任何 Modern UI View。这与渲染问题同构：Modern UI 不接受"作为别人的子层"。

## 6. 依赖模型

- **Maven 坐标**（1.21.1 NeoForge）：
  - Core：`dev.icyllis:modernui-core:3.13.0.x`（≥3.13.0）或 `icyllis.modernui:ModernUI-Core:3.12.0.x`（≤3.12.0）
  - MC 桥：`icyllis.modernui:ModernUI-NeoForge:1.21.1-3.13.0.+`
  - 可选扩展：`ModernUI-Markflow`（≥3.12.0 必需）
- **运行时分发**：玩家侧需要单独安装 Modern UI jar（CurseForge/Modrinth），不能像 Catnip 那样"借 Create 的便车"。
- **Gradle 写法**：官方推荐 `compileOnly` + `additionalRuntimeClasspath` + 排除一堆传递依赖（slf4j、log4j、jsr305、icu4j、fastutil）。这套排除清单本身就是"运行时由玩家 jar 提供"的信号。
- **KP 现状**：`build.gradle` 用 `localImplementation("maven.modrinth:modern-ui:3.12.0.2")`，仅在开发环境验证字体兼容性，不发布到玩家侧。这与"嵌入 UI 框架"所需的强依赖模型不符。

## 7. 许可证

- **Modern UI（含 ModernUI-MC、ModernUI-Core）**：LGPL-3.0-or-later。
- 与 KP 的 MIT 兼容（动态链接、不传染）。
- 但 LGPL 要求：分发时必须保留源码可获取性声明、允许用户替换库本身。对 KP 这种"不打包 Modern UI、只声明可选依赖"的模式无影响；若改成强依赖需注意 LGPL 合规。

## 8. 与 Create / Catnip 共存

- **字体管线**：Modern UI 替换 MC 的 `Font` 实现（`TextLayoutEngine`、`GlyphManager`），用 SDF + HarfBuzz 取代 vanilla 位图字体。KP `STATUS.md` 与多份 plan 记录"与 Modern UI 共存时字体渲染无异常"——这是**字体层面的兼容**，不代表 UI 层兼容。
- **Catnip 冲突点**：Catnip 的 `TextRenderUtil`/`GuiGameElement` 假设 MC 原生 `Font` 行为；Modern UI 的 SDF 字体在像素对齐、阴影、行高上与 vanilla 有差异，Catnip 的 layout 计算可能出现 1-2px 偏移。属"视觉抖动"，非崩溃。
- **事件/渲染 Mixin 冲突**：Modern UI 的 `MixinGuiGraphics` 与 Catnip 对 `GuiGraphics` 的扩展可能竞争同一注入点（如 `flushIfUnmanaged`、`renderText`）。`defaultRequire: 0` 让双方都能加载，但运行时行为未必一致——这是共存的最大不确定性。
- **Create 关系**：Create 不依赖 Modern UI，反之亦然；二者无官方协作。Catnip 的存在恰好说明 Create 团队选择"自建轻量原语"而非"接入 Modern UI"，侧面印证 Modern UI 不适合嵌入场景。

## 9. 代码示例（验证负面结论）

### 9.1 Modern UI 没有嵌入 API 的代码证据

`UIManager`（`@ApiStatus.Internal`）的关键字段：

```java
protected volatile ViewRootImpl mRoot;          // 整个 View 树的唯一根
protected WindowGroup mDecor;                    // 顶层 Window
private GlTexture_Wrapped mLayerTexture;         // Modern UI 的全屏图层纹理
// 渲染入口 onRenderFrame 由 MuiModApi.addOnRenderFrameListener 注册
// 内部走 mRoot.performTraversal() → Canvas → Arc3D → mLayerTexture
```

`BlurHandler.drawScreenBackground`（唯一接收外部 `GuiGraphics` 的公开方法）：

```java
public void drawScreenBackground(@Nonnull GuiGraphicsExtractor gr, int x1, int y1, int x2, int y2) {
    if (minecraft.level == null) {
        gr.fill(x1, y1, x2, y2, 0xFF191919);   // 纯 fill，没有 View 参与
    } else {
        if (mBlurring) gr.blurBeforeThisStratum();
        // ... 再 fill 一个渐变
    }
}
```

可以看到：**唯一接到 `GuiGraphics` 的接口里只调用 `gr.fill` / `gr.blur`，不渲染任何 View**。这是"框架没有嵌入能力"的直接代码证据。

### 9.2 假想嵌入方案的不可行性

如果强行尝试"在 KP Mixin 里反射拿 `UIManager.mRoot`，调用 `mRoot.mSurface.getCanvas()` 再 blit 到 `gg`"：

1. `mRoot`、`mSurface`、`mLayerTexture` 均为 `@ApiStatus.Internal`，版本间无稳定性承诺。
2. `mLayerTexture` 是全屏图层，blit 上来会盖住整个 `gg`，不是面板区域。
3. View 树的事件/布局未走 `mRoot.performTraversal()`，blit 出来是上一帧的陈旧图像。
4. 需要额外 Mixin Modern UI 的 `onRenderFrame` 时机，使其在 KP Mixin 的 RETURN 之前完成 traversal——这是反向耦合，破坏 Modern UI 的帧调度。

**结论：理论上可拼凑，实践中不可维护。**

### 9.3 Owo UI 的对照（证明嵌入 API 长什么样）

Owo UI 的 `io.wispforest.owo.ui.core.OwoUIAdapter` 提供：

```java
// 创建一个不绑定 Screen 的 adapter，可手动驱动渲染
public static OwoUIAdapter<T> createWithoutScreen(
    Consumer<OwoUIAdapter<T>> adapterInitializer);
// 在任意 GuiGraphics 上手动渲染整棵组件树
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);
// 手动转发事件
public boolean mouseClicked(double mouseX, double mouseY, int button);
public boolean mouseScrolled(double mouseX, double mouseY, double amount);
```

这是"嵌入式 UI"应有的形态：组件树自己持有，`render(GuiGraphics)` 接受外部画布，事件由宿主 Screen 转发。**Modern UI 没有任何等价物**，这是两条路径在架构层面的本质差距。

## 10. 诚实的不确定性声明

为避免误导，以下结论的**确信度**分层：

| 结论 | 确信度 | 依据 |
|---|---|---|
| Modern UI 支持 1.21.1 NeoForge | 🟢 高 | README 兼容矩阵 + KP 现有 dev 依赖 |
| Modern UI 没有公开嵌入 API | 🟢 高 | `UIManager`/`ViewRoot` 全 `@ApiStatus.Internal`、javadoc 无此 API、`BlurHandler` 只用 `gr.fill` |
| Modern UI 渲染走自有 framebuffer | 🟢 高 | `mLayerTexture`/`BLASTFramebuffer` 字段 + Arc3D 引擎设计 |
| 反射拼凑"理论可行但不可维护" | 🟡 中 | 基于代码结构推断；未实际编写 PoC，可能存在未发现的内部钩子 |
| Owo UI 有 `createWithoutScreen` | 🟢 高 | javadoc 与多处第三方使用示例确认 |
| Owo UI 在 NeoForge 需 FFAPI | 🟢 高 | README 明示；FFAPI 是 NeoForge 跑 Fabric API 模组的官方桥 |
| Catnip 是 Create 6 的 UI 库 | 🟢 高 | KP 多份 plan/spec 已采用 |
| Modern UI 与 Catnip 字体层无致命冲突 | 🟡 中 | KP 现有验收记录"字体渲染无异常"，但未做深度像素级回归 |
| Modern UI 与 Catnip 的 `MixinGuiGraphics` 无竞争 | 🔴 低 | 未做二进制级 Mixin 注入点对比；这是潜在风险，建议若 Modern UI 升级到 3.13.0.x 时单独回归 |

## 11. 与 Owo UI、Catnip 的横向对比

| 维度 | Modern UI | Owo UI | Catnip |
|---|---|---|---|
| 嵌入到外部 `GuiGraphics` | ❌ | ✅ `createWithoutScreen` + `render(gg)` | ✅ 渲染原语本就基于 `GuiGraphics` |
| 组件库完整度 | 🟢 最完整（Android View 体系） | 🟡 中等（自研组件） | 🔴 最少（基础原语，需自写控件） |
| 布局系统 | 🟢 Android Layout 全套 | 🟢 自研布局（`Layouts`/`Positioning`） | 🔴 手动布局 |
| 事件系统 | 🟢 完整（但需 Screen 接管） | 🟢 完整（手动转发友好） | 🟡 手动 hit-test |
| 文本渲染 | 🟢 SDF + HarfBuzz（最美） | 🟡 借 MC `Font` | 🟡 借 MC `Font` |
| MC 1.21.1 NeoForge | ✅ 3.13.0.1 | ✅ 0.12.15-beta.12+1.21 | ✅ 随 Create 6.0.x |
| 玩家侧新增依赖 | 必装 Modern UI jar | 必装 owo-lib + FFAPI | 无（借 Create） |
| 许可证 | LGPL-3.0-or-later | MIT | Create 的 LGPL（Catnip 同包） |
| 与 Create 共存风险 | 🟡 字体/Mixin 潜在冲突 | 🟢 低 | 🟢 零（同源） |
| KP 开发成本 | 极高（要 Mixin 改其内部） | 中（FFAPI 引入 + PoC） | 中（自写 6-8 个控件） |
| 长期维护 | 受 Modern UI 版本节奏约束 | 受 Owo + FFAPI 双重约束 | 与 Create 版本同生命周期 |

## 12. 最终建议

**采纳 Path C：Catnip + 自定义控件。**

理由排序：

1. **架构契合**：Catnip 的渲染原语（`GuiGameElement`、`TextRenderUtil`、`BoxElement` 等）本来就是"接受 `GuiGraphics` 做即时模式绘制"，与 KP Mixin 拿到 `gg` 的注入点天然对齐。
2. **零新依赖**：Create 已是 KP 硬依赖，玩家侧无需额外安装；分发体积不变。
3. **品牌一致性**：Catnip 的视觉语言与 Create 同源，KP 紫作为 accent 自然叠加，符合 `2026-07-26-config-panel-flat-gui-design.md` 的设计目标。
4. **维护成本可控**：KP 配置面板只需 6-8 个控件（复选框、步进器、Tab、tooltip），手写量在 200-300 行，远低于"绕过 Modern UI 内部架构"的成本。
5. **Modern UI 仍保留为软依赖**：仅用于字体兼容验证（dev 环境），不进入玩家侧。这与 `STATUS.md` 现状一致，无需变更。

**何时考虑 Path B（Owo UI）**：仅当未来 KP 配置面板扩展到需要复杂树形/数据表格/拖拽重排等 Catnip 原语无法覆盖的场景时，再评估 Owo UI。届时需先做 PoC：FFAPI + Create + Catnip 三方共存回归。

**永不考虑 Path A（Modern UI 嵌入）**：除非 Modern UI 未来发布 `createWithoutScreen` 等价 API 或公开 `ViewRenderer.render(View, GuiGraphics)`，否则其架构哲学与 KP 嵌入需求根本对立。

---

## 附：关键证据来源

- `BloCamLimb/ModernUI-MC` master 分支：
  - `README.md`（兼容矩阵、Gradle 配置、许可证）
  - `neoforge/src/main/resources/mixins.modernui-neoforge.json`（Mixin 清单）
  - `common/src/main/java/icyllis/modernui/mc/UIManager.java`（`@ApiStatus.Internal`、`ViewRootImpl`/`mLayerTexture`）
  - `common/src/main/java/icyllis/modernui/mc/BlurHandler.java`（仅用 `gr.fill`/`gr.blur`）
  - `common/src/main/java/icyllis/modernui/mc/mixin/MixinScreen.java`（背景渲染重定向）
- `icyllis.modernui.widget` javadoc（组件清单）
- KP 项目内：
  - `build.gradle`（Modern UI dev 依赖）
  - `STATUS.md`（软依赖定位）
  - `docs/superpowers/specs/2026-07-26-config-panel-flat-gui-design.md`（设计目标与"Modern UI 嵌入过重"的既有判断）
- Owo UI：`io.wispforest.owo.ui.core.OwoUIAdapter` javadoc（`createWithoutScreen` / `render(GuiGraphics)` API 存在性证据）
