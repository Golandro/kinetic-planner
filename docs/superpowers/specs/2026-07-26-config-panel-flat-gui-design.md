# KP 地图内嵌配置面板 - 扁平 GUI 设计规格

> 日期：2026-07-26
> 状态：✅ 已实现（代码完成，待运行时验收）
> 范围：`ProviderConfigScreen`（Xaero / JourneyMap 全屏地图内嵌配置面板）的视觉与交互重设计。
> 本文定义**设计决策、规格与 TDD 要求**，不含逐步实现细节。
>
> 修订记录：
> - 2026-07-26 初版（Catnip）
> - 2026-07-27 改用 LDLib2（嵌入式能力 + 控件完整度 + 横向扩展性三者最优，且与 §1 约束无冲突）
> - 2026-07-27b API 审计修正（ModularUIWidget 不继承 AbstractWidget 等）+ 架构组件细化 + TDD 要求融入
> - 2026-07-27c 实现完成：5 个新 client 类 + 2 个重写类 + 18 个新 @Test 全部通过；运行时验收待 `gradlew runClient`

## 1. 背景与目标

现有面板为纯文本拼装：`[ X ]` 文本复选框、刺眼的蓝色块 Tab、数值只读（须改用 `/kp provider set` 命令）、固定高度留白、无边框无层次。

目标：
- 达到现代扁平 GUI（SolidWorks / FreeCAD 设置面板）水准：真实控件、状态可感知、操作即时生效。
- 视觉上与 Create 模组 UI 同源，同时确立 KP 自身品牌识别（KP 紫 accent）。
- 面板保持嵌入式（渲染于地图 Screen 之上），不引入独立 Screen。

非目标：
- 不改动面板对外交互契约（打开/关闭方式、Mixin 挂载点）。
- 不覆盖 Cloth Config 独立配置屏（二者职责不同）。

## 2. UI 库决策

**采用 LDLib2**（LowDragMC 的 Minecraft 模组开发库，2.2.x 系列）。

理由：
- **嵌入式能力**：提供 `ModularHudLayer`（NeoForge HUD 层 `LayeredDraw.Layer` 实现）与 `ModularUIWidget.render(GuiGraphics, ...)` 纯函数式渲染 API，可直接在 KP Mixin 拿到的外部 `GuiGraphics` 上绘制，**无需接管 Screen**，与 §1 非目标"Mixin 挂载点不变"完全对齐。
- **控件完整度**：原生提供 `Toggle`（复选框，继承 `BindableUIElement<Boolean>`）、`TabView`+`Tab`（文本 Tab + 选中下划线）、`Selector`/`NumberConfigurator`（步进器）、`ScrollerView`（滚动）等 spec §4 全部控件，无需自写。
- **横向扩展性**：`UIElement` 非 final + protected 钩子（`onAdded`/`onRemoved`/`onLayoutChanged`）+ `BindableUIElement<T>` 泛型数据绑定基类 + LSS 样式表（`Stylesheet`/`StyleRule`/`SelectorType`），KP 可注入 KP 紫 accent 主题并子类化自定义控件。
- **布局引擎**：Taffy（Rust 实现的 CSS Flexbox/Grid 子集），通过 `LayoutStyle` fluent API 配置，远超 Catnip/Modern UI 的手动布局。
- **事件系统**：DOM 风格 `UIEventDispatcher`（capture/bubble 双阶段）+ `UIEvents` 标准事件类型。

> **API 审计修正（2026-07-27b）**：`ModularUIWidget` 是 `ModularUI` 的**内部类**，直接 `implements GuiEventListener`/`Renderable`/`NarratableEntry`/`IModularUIHolder`，**不继承 `AbstractWidget`**。因此所有事件（mouseClicked/mouseMoved/keyPressed 等）必须手动转发，不存在"自动接收 Screen 事件"能力。事件转发封装见 §9.2 `KpUIEventForwarder`。

约束：
- 玩家侧新增 LDLib2 依赖（CurseForge/Modrinth 分发，与 Modern UI 同等代价，但 LDLib2 不破坏 spec 约束）。
- LDLib2 许可证 LGPL-3.0，与 KP 的 MIT 兼容（动态链接不传染），分发时需保留源码可获取性声明。
- LDLib2 Mixin 配置用 `defaultRequire: 1`（强依赖自身 Mixin），与 KP `defaultRequire: 0` 模式无冲突，但需确认 `ui.ScreenMixin`/`ui.ContainerEventHandlerMixin` 不与 KP Mixin 竞争同一注入点（运行时验证）。
- LDLib2 字体使用 MC 原生 `Font`（无 SDF），与 Catnip 同级；若未来需要 SDF 字体，仍可软依赖 Modern UI（dev 环境验证）。

### 2.1 否决备选

| 备选 | 否决理由 |
|---|---|
| **Catnip**（Create 6 UI 库） | 嵌入式能力达标但控件最少（仅 `BoxElement`/`BoxWidget` 等原语），需自写 6-8 个控件（200-300 行）；LDLib2 控件完整度更高，是 Catnip 的纯上位替代 |
| **Modern UI** | 必须接管 Screen（`UIManager` 单 Screen 约束 + `ViewRootImpl` 绑定自有 framebuffer），与 §1 非目标"Mixin 挂载点不变"根本对立；详见 `2026-07-26-modern-ui-embedded-rendering-research.md` |
| **Owo UI** | NeoForge 需 Forgified Fabric API 整套运行时依赖，与 Create/Catnip 字体管线潜在冲突；嵌入式 API（`createWithoutScreen`）存在但代价高于 LDLib2 |
| **Elementa / Modern UI P4a 透明覆盖层** | 破坏 §1 非目标"Mixin 挂载点不变"，且配置面板打开期间 map 控件不可操作（UX 退步） |

### 2.2 关键 API 锚点（已审计）

> 以下 API 签名经 LDLib2 源码（`G:\Mods\LDLib2`）验证。

- `ModularHudLayer`（`com.lowdragmc.lowdraglib2.gui.hud`）：`@FunctionalInterface` 接口，继承 `LayeredDraw.Layer`，`render(GuiGraphics, DeltaTracker)` 直接绘制 ModularUI。KP 不直接使用此类（用于 HUD 层注册），但参考其 `render` 实现了解 `ModularUIWidget.render` 调用方式。
- `ModularUI.of(UI ui)` / `ModularUI.of(UI ui, Player player)`：构建 UI 树。参数类型为 `UI`（`UI.of(rootElement)` 创建）。
- `ModularUI.getWidget()`：返回 `ModularUIWidget`（`ModularUI` 内部类，**非 `AbstractWidget` 子类**）。延迟初始化。其 `render(GuiGraphics, int mouseX, int mouseY, float partialTick)` 接受任意 `GuiGraphics`。
- `ModularUIWidget` 事件方法（全部需手动转发）：
  - `mouseMoved(double, double)` / `mouseClicked(double, double, int) : boolean` / `mouseReleased(double, double, int) : boolean`
  - `mouseDragged(double, double, int, double, double) : boolean` / `mouseScrolled(double, double, double, double) : boolean`
  - `keyPressed(int, int, int) : boolean` / `keyReleased(int, int, int) : boolean` / `charTyped(char, int) : boolean`
- `UIElement`：UI 树节点基类，含 `layout(Consumer<LayoutStyle>)` / `style(Consumer<BasicStyle>)` / `addChild(UIElement)` fluent API。
  > **注意**：`style()` 参数为 `Consumer<BasicStyle>`（非 `Consumer<Style>`），`BasicStyle` 继承 `Style`。
- `Toggle`（`com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle`）：继承 `BindableUIElement<Boolean>`，无参构造 `new Toggle()`，`setOn(boolean)` / `getValue() : Boolean`。
- `TabView`/`Tab`/`Button`/`Label`：现成控件（API 细节在实现时查阅 LDLib2 源码）。
- `Stylesheet`/`StylesheetManager`：LSS 样式表系统，可注册 KP 主题。

> **未完全验证项**（实现时需查阅 LDLib2 源码确认）：
> - `TabView`/`Tab` 的构造方式与选中回调 API
> - `Selector`/`NumberConfigurator` 是否存在及其 API
> - LSS 是否支持 CSS 变量（`var()`）和 `:hover` 伪类选择器
> - `ColorRectTexture`/`ColorBorderTexture`/`SDFRectTexture`/`TextTexture`/`UIResourceTexture`/`TransformTexture` 纹理类 API
> - `IGuiTexture.setColor()` 运行时 tint 方法

## 3. 设计系统（Design Tokens）

### 3.1 配色

| Token | 值 | 用途 | LDLib2 落地 |
|---|---|---|---|
| `panelBg` | `0xF228282C` | 面板底（95% 不透明深灰） | `SDFRectTexture`/`ColorRectTexture` 背景 |
| `panelBorder` | `0xFF000000`，顶部 1px `0x40FFFFFF` 高光 | flat-raised 立体边 | `ColorBorderTexture` 或 `SDFRectTexture` 边框 |
| `accent` | `0xFF7C57D4`（KP 紫） | 选中 Tab 下划线、勾选态、控件 hover | LSS 样式表 `--kp-accent` 变量，`ToggleStyle.MARK_BACKGROUND` |
| `textPrimary` | `0xFFF3EFE0`（Create 米白） | 标题、label | `TextTexture` 颜色 / `Label` 默认色 |
| `textSecondary` | `0xFF8B8B93` | 提示、单位 | `Label` 次级样式 |
| `danger` | `0xFFFFAD60`（Create 橙） | `[FUSED]` 熔断标记 | LSS 样式表 `--kp-danger` 变量，应用到 Tab `Style` 的 `color` 属性 |

### 3.2 LSS 样式表

KP 主题通过 LDLib2 LSS（`Stylesheet`/`StyleRule`/`SelectorType`）注册，集中管理配色与控件样式：

```css
/* KP 主题变量（若 LSS 支持 CSS 变量） */
:root {
    --kp-accent: #FF7C57D4;
    --kp-danger: #FFFFAD60;
    --kp-panel-bg: #F228282C;
    --kp-text-primary: #FFF3EFE0;
    --kp-text-secondary: #FF8B8B93;
}

/* Tab 选中下划线 */
.kp-tab-selected { border-bottom: 2px solid var(--kp-accent); }

/* 熔断 Tab */
.kp-tab-fused { color: var(--kp-danger); }

/* Toggle 勾选态 */
.kp-toggle-checked { background-color: var(--kp-accent); }

/* 行 hover 高亮 */
.kp-config-row:hover { background-color: #14FFFFFF; }
```

> **降级策略**：若 LDLib2 LSS 不支持 CSS 变量或 `:hover` 伪类，降级为内联 `style(Consumer<BasicStyle>)` 设置颜色，hover 通过 `UIEvents.MOUSE_ENTER`/`MOUSE_LEAVE` 事件手动切换。实现时先验证 LSS 能力，再决定落地方式。

### 3.3 图标系统（SolidWorks / FreeCAD 惯例）

- 16x16 扁平单色线稿图标，1px 线宽、统一视觉重心，集中于一张 atlas。
- **状态用渲染时着色（tint）表达，而非多张贴图**：
  normal `0xFFC8C8C8` / hover `0xFFFFFFFF` / active KP紫 / disabled 40% alpha。
- 图标清单：`gear`、`check`、`minus`、`plus`、`close`、`link`、`layers`。
- LDLib2 落地：`UIResourceTexture`（资源路径纹理）+ `TransformTexture`（着色变换）+ `IGuiTexture.setColor()` 运行时 tint。

### 3.4 网格与排版

- 4px 基数间距阶梯（4/8/12/16），行高 16px，MC 原生 9px 字体。
- 投影：右下偏移 2px、20% 黑（扁平投影惯例）。
- LDLib2 落地：`LayoutStyle.paddingAll(4/8/12/16)` + `gapAll(4)`；Taffy 引擎自动处理 Flexbox 间距。

## 4. 布局规格

### 4.1 面板定位

- **位置**：屏幕右上角，距右边缘 4px，距顶部 24px（齿轮按钮下方）。
- **宽度**：200px（固定）。
- **高度**：auto（按内容自适应，Taffy `height: auto`）。
- **计算**：`panelX = screenWidth - 200 - 4`，`panelY = 24`。
- 面板坐标在每次 `render` 时根据当前 `getGuiScaledWidth/Height` 重新计算，适配屏幕尺寸变化与 GUI Scale 切换。

### 4.2 面板布局

```
┌────────────────────────────────┐
│ ⚙ Kinetic Planner           [×]│  ← 标题栏 18px；下方 accent 40% 分隔线
│ xaeroworldmap   journeymap     │  ← 文本 Tab；选中项底部 2px KP紫下划线（弃色块）
│ ────────────────────────────── │
│  Enabled                  [☑]  │  ← label 左 / 控件右的对齐列，行高 16
│  Priority             [−] 0 [+]│  ← 步进器，点击即生效
│  Line Width        [−] 1.00 [+]│
│  Alpha             [−] 1.00 [+]│
│  Dashed                   [ ]  │
│  Show Create Track Map    [☑]  │  ← 语义：true = 显示 Create 叠加层
│ ────────────────────────────── │
│  提示文本（次级色）              │
└────────────────────────────────┘
```

### 4.3 LDLib2 UI 树结构

```
UIElement (root, position=absolute, x=panelX, y=panelY, w=200, h=auto)
├── layout: paddingAll(8).gapAll(4).display(FLEX).flexDirection(COLUMN)
├── UIElement (标题栏 row)
│   ├── layout: flexDirection(ROW).justifyContent(SPACE_BETWEEN)
│   ├── UIElement (gear icon + Label "Kinetic Planner")
│   └── Button (close [×])
├── TabView (provider tabs)
│   ├── Tab "xaeroworldmap"
│   └── Tab "journeymap"
├── UIElement (配置项列表 column)
│   ├── UIElement (row): Label "Enabled" + Toggle
│   ├── UIElement (row): Label "Priority" + Button[−] + Label[value] + Button[+]
│   ├── UIElement (row): Label "Line Width" + Button[−] + Label[value] + Button[+]
│   ├── UIElement (row): Label "Alpha" + Button[−] + Label[value] + Button[+]
│   ├── UIElement (row): Label "Dashed" + Toggle
│   └── UIElement (row): Label "Show Create Track Map" + Toggle
└── Label (提示文本, textSecondary)
```

- 复选框：`Toggle` 14x14、1px 边框；勾选 = KP紫填充 + 米白 `check` 图标（通过 `ToggleStyle.MARK_BACKGROUND` 设置）。
- 步进器：`Button[−]` + `Label`（值显示）+ `Button[+]` 组合。点击 `[+]` 调用 `computeSteppedValue(current, step, true, min, max)`，点击 `[−]` 调用 `computeSteppedValue(current, step, false, min, max)`，然后更新 Label 并调用 `ProviderConfigControl.setParam()`。
- **Tab 切换数据流**：`Tab.onSelected` 回调中，清空配置项列表 column 的子节点，用 `buildProviderConfigRows(newModId)` 重建。重建时从 `KPConfig.getProviderConfig(modId)` 读取当前值初始化控件状态。

## 5. 交互规格

### 5.1 即时生效

复选框、步进器点击**即时生效**（移除「Use /kp provider set」提示；命令仍保留作为脚本途径）。

数据流：
```
控件回调 -> ProviderConfigControl.setParam(modId, param, value)
         -> KPConfig.setProviderParam(modId, param, value)  // ModConfigSpec.defineInRange 自动 clamp
         -> OverlayControl.reload()                          // 重建 Theme
         -> WorldTreeReadOverlay 更新渲染参数
```

> 此链路在 LDLib2 事件回调中同步执行，无异步延迟。

### 5.2 步进器 clamp 域

与 `KPConfig` 的 `defineInRange` 保持一致：

| 参数 | min | max | step | 类型 |
|---|---|---|---|---|
| `priority` | 0 | 100 | 1 | int |
| `lineWidthScale` | 0.1 | 10.0 | 0.05 | float |
| `alphaScale` | 0.0 | 1.0 | 0.05 | float |

步进器在 UI 层 clamp 后再传给 `ProviderConfigControl`，保证显示值与配置值一致。clamp 逻辑提取为纯函数 `KpConfigUIFactory.computeSteppedValue()`，可单元测试（见 §10）。

### 5.3 行 hover

- 8% 白底高亮（通过 LSS `:hover` 选择器若 LDLib2 支持，否则用 `UIEvents.MOUSE_ENTER`/`MOUSE_LEAVE` 事件手动切换 `BasicStyle.background`）。
- 参数行悬停显示说明 tooltip（`Tooltips` 数据类 + 事件监听）。

### 5.4 面板关闭

- `[×]` 按钮和外部齿轮按钮均可关闭面板（调用 `KpClientState.setConfigPanelVisible(false)`）。
- 不拦截 Esc（Esc 交给地图 Screen 处理）。

### 5.5 熔断状态

- 熔断（`[FUSED]`）provider 的 Tab 置为 danger 色（`--kp-danger`）。
- 该 provider 的配置项控件禁用（`UIElement.setEnabled(false)`）。

### 5.6 命名语义统一

> **修正**：spec 原版引用 `hideCreateTrackMap`，实际代码为 `showCreateTrackMap`。统一为 `showCreateTrackMap`（true = 显示 Create 叠加层），与 `KPConfig.SHOW_CREATE_TRACK_MAP` 和 `OverlayControl.isShowCreateTrackMap()` 一致。

## 6. 验收标准

- 悬停齿轮按钮只出现「Kinetic Planner」一条提示（Create 提示已被 Mixin 封杀）。
- 面板内所有开关/数值改动即时反映到地图叠加层，无需命令。
- Xaero 与 JourneyMap 两侧视觉与行为一致。
- 与 Modern UI 共存时字体渲染无异常（LDLib2 用 MC 原生 Font，与 Modern UI SDF 字体并行无冲突）。
- LDLib2 Mixin 与 KP Mixin 无注入点竞争（运行时检查 Mixin 应用日志）。
- 屏幕尺寸变化 / GUI Scale 切换后面板位置正确适配。
- 步进器数值在 clamp 域内显示，超出域时自动 clamp。
- `showCreateTrackMap` toggle 语义正确（true = 显示，false = 隐藏）。

## 7. 关联

- Mixin 封杀设计（Create 按钮/Toast/叠加层接管）：见 `.codebuddy/memory/MEMORY.md`「接管 Create Track Map」。
- 前置设计：`2026-07-20-kinetic-planner-phase0-design.md`。
- Modern UI 嵌入式调研（否决依据）：`2026-07-26-modern-ui-embedded-rendering-research.md`。
- 现有实现计划（Tasks 1-4/9 保留，Tasks 5-8 被 spec 替代）：`2026-07-25-kinetic-planner-p0.5-phaseb-p1.1.md`。

## 8. LDLib2 集成细节

### 8.1 依赖声明

`build.gradle` 新增：

```gradle
repositories {
    maven { url = "https://maven.firstdark.dev/snapshots" }  // LDLib2
}

dependencies {
    implementation("com.lowdragmc.ldlib2:ldlib2-neoforge-${minecraft_version}:2.2.26:all")
}
```

`gradle.properties` 新增：

```properties
ldlib2_version = 2.2.26
```

`neoforge.mods.toml` 新增依赖声明：

```toml
[[dependencies.kinetic_planner]]
modId = "ldlib2"
type = "required"
side = "CLIENT"
versionRange = "[2.2.0,)"
ordering = "AFTER"
relationship = "required"
```

> **修正**：`side = "CLIENT"`（非 `"BOTH"`），因为 LDLib2 UI 类仅在客户端使用。

### 8.2 Mixin 挂载点（保持不变）

KP 现有 Mixin 注入点 `Xaero's GuiMap.render(gg, ...)` 的 `@At("RETURN")` 保持不变。在 Mixin 中通过 `KpUIEventForwarder` 调用 LDLib2 渲染与事件转发：

```java
@Mixin(GuiMap.class)
public class XaeroMapGearButtonMixin {

    @Unique
    private static KpUIEventForwarder kp$forwarder;

    @Unique
    private static KpGearButton kp$gearButton;

    @Inject(method = "render", at = @At("RETURN"))
    private void kp$renderConfigPanel(GuiGraphics gg, int mouseX, int mouseY,
                                       float partialTicks, CallbackInfo ci) {
        if (!OverlayControl.isEnabled()) return;
        kp$ensureInit();
        // 齿轮按钮始终渲染
        kp$gearButton.render(gg, mouseX, mouseY);
        // 配置面板仅在可见时渲染
        if (KpClientState.isConfigPanelVisible()) {
            kp$forwarder.render(gg, mouseX, mouseY, partialTicks);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kp$handleMouseClick(double mouseX, double mouseY, int button,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!OverlayControl.isEnabled()) return;
        kp$ensureInit();
        // 先检查齿轮按钮
        if (kp$gearButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        // 再检查配置面板（仅在可见时）
        if (KpClientState.isConfigPanelVisible()
            && kp$forwarder.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static void kp$ensureInit() {
        if (kp$forwarder == null) {
            var modularUI = KpConfigUIFactory.create();
            var window = Minecraft.getInstance().getWindow();
            modularUI.init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            kp$forwarder = new KpUIEventForwarder(modularUI);
        }
        // 屏幕尺寸变化时 re-init
        kp$forwarder.checkResize(
            Minecraft.getInstance().getWindow().getGuiScaledWidth(),
            Minecraft.getInstance().getWindow().getGuiScaledHeight());
        if (kp$gearButton == null) {
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            kp$gearButton = new KpGearButton(screenW - 20, 4,
                () -> KpClientState.toggleConfigPanel());
        }
    }
}
```

> **注意**：`mouseClicked` 方法签名需运行时验证 Xaero `GuiMap` 中的实际签名。Mixin `defaultRequire: 0` 保证签名不匹配时不崩溃。
>
> **其他事件转发**（在 Mixin 中按需添加 `@Inject`）：
> - `mouseReleased`/`mouseDragged`/`mouseScrolled`：转发到 `kp$forwarder`
> - `keyPressed`/`keyReleased`/`charTyped`：若面板有焦点控件（如 TextField），需在 `Screen` 级别 Mixin 转发
> - `mouseMoved`：在 `render` 中调用 `kp$forwarder.mouseMoved(mouseX, mouseY)` 更新 hover 状态

### 8.3 统一验收标准

> 采用全量实现后统一验证策略（不做分层 PoC）。以下标准在 `gradlew runClient` 运行时统一验收。

**编译与测试前置检查：**
1. `gradlew compileClientJava` 编译通过（LDLib2 依赖解析正确）
2. `gradlew test` 全部测试 PASS（含 §10 TDD 新增测试）
3. `gradlew build` 完整构建通过

**运行时验收：**
1. 在 Xaero GuiMap 打开后，右上角显示齿轮按钮
2. 点击齿轮按钮切换 KP 配置面板可见性
3. 面板使用 LDLib2 `Toggle`/`TabView`/`Button`/`Label` 控件，视觉为 KP 紫色 accent + 深灰底
4. `Toggle` 切换 -> map 上的叠加层实时变化（如 Enabled = false 时叠加层消失）
5. `TabView` 切换 provider（xaeroworldmap <-> journeymap），配置项随 provider 切换更新
6. 步进器（`Button[-]`/`Button[+]`）点击 -> 数值即时变化且 clamp 在合理域
7. 鼠标在配置面板区域内被面板消费（不穿透到 map）；区域外放行给 map（不破坏 map 拖动/缩放）
8. 屏幕尺寸变化 / GUI Scale 切换后面板位置正确适配
9. 与 Modern UI 共存时字体渲染正常（dev 环境验证）
10. LDLib2 Mixin 与 KP Mixin 无注入点竞争（无 Mixin 应用失败日志）
11. JM 全屏地图工具栏有 "KP" 按钮，点击后配置面板行为与 Xaero 一致

### 8.4 已知风险与缓解

| 风险 | 概率 | 缓解 |
|---|---|---|
| LDLib2 `ui.ScreenMixin` 与 KP Mixin 竞争 | 低 | 运行时检查 Mixin 应用日志；必要时调整 KP Mixin 优先级 |
| 事件转发遗漏导致控件不响应 | 中 | `KpUIEventForwarder` 封装全部 `GuiEventListener` 方法；参考 `ModularUIScreen` 的事件路由实现 |
| `ModularUIWidget.render` 在外部 gg 上调用时布局未计算 | 低 | `ModularUIWidget.render` 内部自动调用 `calculateStyleAndLayout()`，无需手动触发 |
| LDLib2 版本升级破坏 API | 低 | 锁定 `ldlib2_version = 2.2.26`；升级前在 dev 环境验证 |
| Taffy 布局引擎在 1.21.1 NeoForge 运行期异常 | 低 | Taffy 是 LDLib2 内置 Rust 库，已在 LDLib2 测试环境验证 |
| LSS 不支持 CSS 变量或 `:hover` 伪类 | 中 | 降级为内联 `style(Consumer<BasicStyle>)` + 事件监听手动切换样式 |
| `TabView`/`Tab` API 与 spec 假设不符 | 中 | 实现时查阅 LDLib2 源码，必要时自建 Tab 容器（`UIElement` row + 手动选中态） |
| `ModularUIWidget.mouseClicked` 在面板外返回 true（UI 树覆盖全屏） | 中 | 在 `KpUIEventForwarder` 中添加面板区域检查，面板外点击不消费事件 |
| Xaero `GuiMap.mouseClicked` 方法签名不匹配 | 低 | Mixin `defaultRequire: 0` 容错；运行时验证签名 |

## 9. 新增架构组件

### 9.1 `KpConfigUIFactory`

**文件**：`src/client/java/.../config/KpConfigUIFactory.java`

**职责**：构建 LDLib2 `UIElement` 树、注册 LSS 样式表、绑定 `ProviderConfig` 数据到控件回调。

```
KpConfigUIFactory
├── create() : ModularUI
│   ├── 注册 KpStylesheet（KP 紫主题 LSS）
│   ├── 构建 UIElement 树（§4.3 的树结构）
│   ├── 创建 TabView + 每 provider 配置项列表
│   └── 返回 ModularUI.of(UI.of(rootElement))
├── buildProviderConfigRows(modId) : UIElement
│   ├── Toggle(enabled) -> ProviderConfigControl.enable/disable
│   ├── Stepper(priority) -> ProviderConfigControl.setParam("priority", ...)
│   ├── Stepper(lineWidthScale) -> ProviderConfigControl.setParam("lineWidthScale", ...)
│   ├── Stepper(alphaScale) -> ProviderConfigControl.setParam("alphaScale", ...)
│   ├── Toggle(dashed) -> ProviderConfigControl.setParam("dashed", ...)
│   └── Toggle(showCreateTrackMap) -> OverlayControl.setShowCreateTrackMap
├── registerStylesheet()
│   └── 注册 --kp-accent / --kp-danger / --kp-panel-bg 等 LSS 规则
└── computeSteppedValue(current, step, increment, min, max) : float  [static, 纯函数]
    └── clamp + step 逻辑，可单元测试
```

**数据绑定策略**：控件回调直接调用 `ProviderConfigControl` / `OverlayControl`，不通过 `BindableUIElement` 双向绑定。KP 配置是单向写入 + `OverlayControl.reload()` 触发，无需双向同步。控件初始值从 `KPConfig.getProviderConfig(modId)` 读取。

**Tab 切换**：`Tab.onSelected` 回调清空配置项列表 column 子节点，用 `buildProviderConfigRows(newModId)` 重建。

### 9.2 `KpUIEventForwarder`

**文件**：`src/client/java/.../config/KpUIEventForwarder.java`

**职责**：封装 `ModularUIWidget` 的全部事件方法转发，供 Xaero Mixin 和 JM Event 共用，避免代码重复。

```
KpUIEventForwarder
├── constructor(ModularUI ui)
├── render(GuiGraphics, int mouseX, int mouseY, float partialTick)
│   └── ui.getWidget().render(...)
├── mouseMoved(double, double)
│   └── ui.getWidget().mouseMoved(...)
├── mouseClicked(double, double, int) : boolean
│   └── ui.getWidget().mouseClicked(...)
├── mouseReleased(double, double, int) : boolean
├── mouseDragged(double, double, int, double, double) : boolean
├── mouseScrolled(double, double, double, double) : boolean
├── keyPressed(int, int, int) : boolean
├── keyReleased(int, int, int) : boolean
├── charTyped(char, int) : boolean
└── checkResize(int width, int height)
    └── 若尺寸变化，调用 ui.init(width, height) 重新初始化
```

> Xaero Mixin 和 JM Event 各持有一个 `KpUIEventForwarder` 实例（或共享同一实例），调用相同接口。

### 9.3 `KpClientState`

**文件**：`src/client/java/.../config/KpClientState.java`

**职责**：管理配置面板可见性状态（静态，Xaero/JM 共享）。

```java
public final class KpClientState {
    private static boolean configPanelVisible = false;

    public static boolean isConfigPanelVisible() { return configPanelVisible; }
    public static void setConfigPanelVisible(boolean v) { configPanelVisible = v; }
    public static void toggleConfigPanel() { configPanelVisible = !configPanelVisible; }
}
```

### 9.4 `KpGearButton`

**文件**：`src/client/java/.../config/KpGearButton.java`

**职责**：地图右上角的自绘齿轮按钮（独立于 LDLib2 UI 树），点击切换面板可见性。

保留自绘方案（不使用 LDLib2 `Button`），因为齿轮按钮在面板外部，不需要 LDLib2 布局引擎。外观与现有 `MapGearButtonWidget` 一致：16x16 半透明背景 + 白色齿轮轮廓 + hover tooltip。

### 9.5 `KpStylesheet`

**文件**：`src/client/java/.../config/KpStylesheet.java`

**职责**：定义并注册 KP 主题 LSS 样式表（§3.2）。在 `KpConfigUIFactory.create()` 中调用。

### 9.6 删除的旧文件

| 文件 | 处理 | 原因 |
|---|---|---|
| `MapGearButtonWidget.java` | 删除 | 被 `KpGearButton.java` 替代 |
| `ProviderConfigScreen.java` | 删除 | 被 `KpConfigUIFactory.java` + `ModularUI` 替代 |

## 10. TDD 要求

> 遵循 Red-Green-Refactor 循环：先写测试（Red），再实现使测试通过（Green），然后重构（Refactor）。
> 测试在实现对应功能**之前**编写。

### 10.1 可测试组件矩阵

| 组件 | sourceSet | 测试方式 | 测试文件 |
|---|---|---|---|
| `KpConfigUIFactory.computeSteppedValue()` | client | 纯函数单元测试（无 MC 依赖） | `KpConfigUIFactoryTest.java` |
| `KpClientState` | client | 纯状态单元测试（无 MC 依赖） | `KpClientStateTest.java` |
| `KpUIEventForwarder` | client | Mockito mock `ModularUI`/`ModularUIWidget` | `KpUIEventForwarderTest.java` |
| `KpConfigUIFactory.create()` | client | Mockito mock LDLib2 UIElement（验证树结构） | `KpConfigUIFactoryTest.java` |

> `computeSteppedValue` 和 `KpClientState` 虽在 client sourceSet，但不含 MC import，可在纯 JVM 环境测试。

### 10.2 测试用例定义

#### `KpConfigUIFactoryTest` - 步进器 clamp 逻辑

**先于实现编写，验证 `computeSteppedValue` 纯函数：**

```java
@Test
void stepIncrement_withinRange() {
    assertEquals(1.05f, KpConfigUIFactory.computeSteppedValue(1.0f, 0.05f, true, 0.1f, 10.0f), 1e-6f);
}

@Test
void stepIncrement_clampToMax() {
    assertEquals(10.0f, KpConfigUIFactory.computeSteppedValue(9.98f, 0.05f, true, 0.1f, 10.0f), 1e-6f);
}

@Test
void stepDecrement_clampToMin() {
    assertEquals(0.1f, KpConfigUIFactory.computeSteppedValue(0.12f, 0.05f, false, 0.1f, 10.0f), 1e-6f);
}

@Test
void stepIncrement_priorityInteger() {
    assertEquals(5, KpConfigUIFactory.computeSteppedValue(4, 1, true, 0, 100));
}

@Test
void stepDecrement_priorityClampToZero() {
    assertEquals(0, KpConfigUIFactory.computeSteppedValue(0, 1, false, 0, 100));
}

@Test
void stepIncrement_alphaClampToOne() {
    assertEquals(1.0f, KpConfigUIFactory.computeSteppedValue(0.98f, 0.05f, true, 0.0f, 1.0f), 1e-6f);
}
```

#### `KpClientStateTest` - 面板可见性状态

```java
@Test
void defaultInvisible() {
    KpClientState.setConfigPanelVisible(false);
    assertFalse(KpClientState.isConfigPanelVisible());
}

@Test
void toggleChangesState() {
    KpClientState.setConfigPanelVisible(false);
    KpClientState.toggleConfigPanel();
    assertTrue(KpClientState.isConfigPanelVisible());
    KpClientState.toggleConfigPanel();
    assertFalse(KpClientState.isConfigPanelVisible());
}
```

#### `KpUIEventForwarderTest` - 事件转发（Mockito）

```java
@Test
void mouseClicked_forwardsToWidget() {
    ModularUI mockUI = mock(ModularUI.class);
    ModularUI.ModularUIWidget mockWidget = mock(ModularUI.ModularUIWidget.class);
    when(mockUI.getWidget()).thenReturn(mockWidget);
    when(mockWidget.mouseClicked(10, 20, 0)).thenReturn(true);

    var forwarder = new KpUIEventForwarder(mockUI);
    assertTrue(forwarder.mouseClicked(10, 20, 0));
    verify(mockWidget).mouseClicked(10, 20, 0);
}

@Test
void checkResize_reInitOnSizeChange() {
    ModularUI mockUI = mock(ModularUI.class);
    var forwarder = new KpUIEventForwarder(mockUI);

    forwarder.checkResize(100, 100);  // 首次设置
    forwarder.checkResize(200, 100);  // 宽度变化
    verify(mockUI, times(1)).init(200, 100);
}

@Test
void checkResize_noReInitOnSameSize() {
    ModularUI mockUI = mock(ModularUI.class);
    var forwarder = new KpUIEventForwarder(mockUI);

    forwarder.checkResize(100, 100);
    forwarder.checkResize(100, 100);
    verify(mockUI, never()).init(anyInt(), anyInt());
}
```

> **注意**：`ModularUIWidget` 是 `ModularUI` 的内部类，Mockito mock 时需注意内部类 mock 语法。若 mock 受限，标注 `@Disabled` 并在注释中说明原因。

### 10.3 测试执行顺序（TDD 流程）

每个组件遵循以下循环：

1. **Red**：编写测试用例，运行 `gradlew test` 确认编译失败（类/方法不存在）
2. **Green**：实现最小代码使测试通过
3. **Refactor**：重构实现代码，确保测试仍通过

组件实现顺序（依赖链）：
```
KpClientState (无依赖)
  -> KpConfigUIFactory.computeSteppedValue (纯函数，无依赖)
  -> KpUIEventForwarder (依赖 ModularUI)
  -> KpConfigUIFactory.create() (依赖 LDLib2 UI 控件)
  -> KpGearButton (无依赖)
  -> XaeroMapGearButtonMixin (依赖以上全部)
  -> JM Event 订阅 (依赖以上全部)
```

### 10.4 运行时验收（手动测试）

以下行为无法通过单元测试覆盖，需 `gradlew runClient` 手动验收（见 §8.3 统一验收标准）：
- 视觉正确性（配色、布局、字体）
- 事件消费边界（面板内消费、面板外放行）
- 即时生效（控件 -> 叠加层实时变化）
- Modern UI 共存
- Mixin 兼容性

## 11. 完整实现目标清单

> **实现状态：** 全部代码目标完成（2026-07-27）。A/B/C/D/E/F1/F2 已通过；F3 运行时验收待 `gradlew runClient` 手动执行（spec §8.3）。

### A. LDLib2 集成基础

| # | 目标 | 验证 | 状态 |
|---|---|---|---|
| A1 | `build.gradle` 添加 LDLib2 依赖 + maven 仓库 | `gradlew compileClientJava` | ✅ |
| A2 | `neoforge.mods.toml` 添加 LDLib2 依赖声明（`side = "CLIENT"`） | 编译检查 | ✅ |
| A3 | `gradle.properties` 添加 `ldlib2_version` | 编译检查 | ✅ |

### B. UI 核心组件（TDD）

| # | 目标 | 测试 | sourceSet | 状态 |
|---|---|---|---|---|
| B1 | `KpClientState` - 面板可见性状态 | `KpClientStateTest` (3 tests) | client | ✅ |
| B2 | `KpConfigUIFactory.computeSteppedValue()` - 步进器 clamp 纯函数 | `KpConfigUIFactoryTest` (6 tests) | client | ✅ |
| B3 | `KpUIEventForwarder` - 事件转发封装 | `KpUIEventForwarderTest` (9 tests, Mockito) | client | ✅ |
| B4 | `KpConfigUIFactory.create()` - UI 树构建 + 数据绑定 + LSS 注册 | 手动验收 | client | ✅ |
| B5 | `KpStylesheet` - LSS 样式表定义与注册 | 手动验收 | client | ✅ |
| B6 | `KpGearButton` - 自绘齿轮按钮 | 手动验收 | client | ✅ |

### C. Xaero 集成

| # | 目标 | 验证 | 状态 |
|---|---|---|---|
| C1 | `XaeroMapGearButtonMixin` - 注入 render + mouseClicked（通过 `KpUIEventForwarder`） | `gradlew build` + 运行时 | ✅ |
| C2 | `kinetic_planner.mixins.json` 注册新 Mixin | 编译检查 | ✅ |
| C3 | 其他事件转发（mouseReleased/mouseScrolled 等，按需） | 运行时 | 🟡 按需（首批只转发 render + mouseClicked） |

### D. JM 集成

| # | 目标 | 验证 | 状态 |
|---|---|---|---|
| D1 | `KineticPlannerJMPlugin` 扩展 - 订阅 FullscreenEventRegistry 事件 | `gradlew compileClientJava` | ✅ |
| D2 | JM 事件通过 `KpUIEventForwarder` 转发 | 运行时 | ✅ |
| D3 | JM `ADDON_BUTTON_DISPLAY_EVENT` 添加 KP 按钮 | 运行时 | ✅ |

### E. 旧代码清理

| # | 目标 | 验证 | 状态 |
|---|---|---|---|
| E1 | 删除 `MapGearButtonWidget.java` | 编译检查（无引用残留） | ✅ |
| E2 | 删除 `ProviderConfigScreen.java` | 编译检查（无引用残留） | ✅ |

### F. 验收

| # | 目标 | 验证 | 状态 |
|---|---|---|---|
| F1 | `gradlew test` 全部测试 PASS（含 TDD 新增测试） | CI | ✅ 60 @Test 全 PASS |
| F2 | `gradlew build` 完整构建通过 | CI | ✅ BUILD SUCCESSFUL |
| F3 | 运行时验收（§8.3 全部标准） | `gradlew runClient` 手动 | 🔲 待执行 |

## 12. 与现有计划的关系

现有计划 `2026-07-25-kinetic-planner-p0.5-phaseb-p1.1.md` 的 9 个 Task：

| Task | 状态 | 处理 |
|---|---|---|
| T1 (JM Plugin) | 已完成 | 保留 |
| T2 (JM Provider) | 已完成 | 保留 |
| T3 (熔断器) | 已完成 | 保留 |
| T4 (reset-circuit 命令) | 已完成 | 保留 |
| T5 (MapGearButtonWidget) | 旧代码存在 | **删除**，由 `KpGearButton` 替代（§9.4） |
| T6 (ProviderConfigScreen) | 旧代码存在 | **删除**，由 `KpConfigUIFactory` 替代（§9.1） |
| T7 (XaeroMapGearButtonMixin) | 未开始 | **重写**，使用 `KpUIEventForwarder`（§8.2） |
| T8 (JM 事件订阅) | 未开始 | **重写**，使用 `KpUIEventForwarder`（§D） |
| T9 (dashed 渲染) | 未开始 | 保留（独立于 UI，不在此 spec 范围） |

> T1-T4 和 T9 在现有计划中定义，不受 spec 影响。T5-T8 由 spec 重新定义，实现计划中应使用 spec 的架构组件替代。
