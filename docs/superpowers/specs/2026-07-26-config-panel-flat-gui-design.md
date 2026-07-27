# KP 地图内嵌配置面板 — 扁平 GUI 设计规格（建议）

> 日期：2026-07-26
> 状态：建议（待评审）
> 范围：`ProviderConfigScreen`（Xaero / JourneyMap 全屏地图内嵌配置面板）的视觉与交互重设计。
> 本文只定义**设计决策与规格**，不含实现细节。
>
> 修订记录：
> - 2026-07-26 初版（Catnip）
> - 2026-07-27 改用 LDLib2（嵌入式能力 + 控件完整度 + 横向扩展性三者最优，且与 §1 约束无冲突）

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
- **控件完整度**：原生提供 `Toggle`（复选框）、`TabView`+`Tab`（文本 Tab + 选中下划线）、`Selector`/`NumberConfigurator`（步进器）、`ScrollerView`（滚动）等 spec §4 全部控件，无需自写。
- **横向扩展性**：`UIElement` 非 final + protected 钩子（`onAdded`/`onRemoved`/`onLayoutChanged`）+ `BindableUIElement<T>` 泛型数据绑定基类 + LSS 样式表（`Stylesheet`/`StyleRule`/`SelectorType`），KP 可注入 KP 紫 accent 主题并子类化自定义控件。
- **布局引擎**：Taffy（Rust 实现的 CSS Flexbox/Grid 子集），通过 `LayoutStyle` fluent API 配置，远超 Catnip/Modern UI 的手动布局。
- **事件系统**：DOM 风格 `UIEventDispatcher`（capture/bubble 双阶段）+ `UIEvents` 标准事件类型，`ModularUIWidget` 作为 `AbstractWidget` 子类自动接收 Screen 事件，HUD 层模式下可手动转发。

约束：
- 玩家侧新增 LDLib2 依赖（CurseForge/Modrinth 分发，与 Modern UI 同等代价，但 LDLib2 不破坏 spec 约束）。
- LDLib2 许可证 LGPL-3.0，与 KP 的 MIT 兼容（动态链接不传染），分发时需保留源码可获取性声明。
- LDLib2 Mixin 配置用 `defaultRequire: 1`（强依赖自身 Mixin），与 KP `defaultRequire: 0` 模式无冲突，但需确认 `ui.ScreenMixin`/`ui.ContainerEventHandlerMixin` 不与 KP Mixin 竞争同一注入点（PoC 阶段验证）。
- LDLib2 字体使用 MC 原生 `Font`（无 SDF），与 Catnip 同级；若未来需要 SDF 字体，仍可软依赖 Modern UI（dev 环境验证）。

### 2.1 否决备选

| 备选 | 否决理由 |
|---|---|
| **Catnip**（Create 6 UI 库） | 嵌入式能力达标但控件最少（仅 `BoxElement`/`BoxWidget` 等原语），需自写 6-8 个控件（200-300 行）；LDLib2 控件完整度更高，是 Catnip 的纯上位替代 |
| **Modern UI** | 必须接管 Screen（`UIManager` 单 Screen 约束 + `ViewRootImpl` 绑定自有 framebuffer），与 §1 非目标"Mixin 挂载点不变"根本对立；详见 `2026-07-26-modern-ui-embedded-rendering-research.md` |
| **Owo UI** | NeoForge 需 Forgified Fabric API 整套运行时依赖，与 Create/Catnip 字体管线潜在冲突；嵌入式 API（`createWithoutScreen`）存在但代价高于 LDLib2 |
| **Elementa / Modern UI P4a 透明覆盖层** | 破坏 §1 非目标"Mixin 挂载点不变"，且配置面板打开期间 map 控件不可操作（UX 退步） |

### 2.2 关键 API 锚点

- `ModularHudLayer`（`com.lowdragmc.lowdraglib2.gui.hud`）：NeoForge HUD 层注册，`render(GuiGraphics, DeltaTracker)` 直接绘制 ModularUI。
- `ModularUI.of(UI.of(rootElement))`：构建 UI 树。
- `ModularUI.getWidget()`：返回 `ModularUIWidget`（`AbstractWidget` 子类），其 `render(GuiGraphics, int mouseX, int mouseY, float partialTick)` 接受任意 `GuiGraphics`。
- `UIElement`：UI 树节点基类，含 `layout(Consumer<LayoutStyle>)`/`style(Consumer<Style>)`/`addChild(UIElement)` fluent API。
- `Toggle`/`TabView`/`Selector`/`Button`/`Label`：现成控件。
- `Stylesheet`/`StylesheetManager`：LSS 样式表，可注册 KP 主题（accent = KP 紫 `0xFF7C57D4`）。

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

### 3.2 图标系统（SolidWorks / FreeCAD 惯例）

- 16×16 扁平单色线稿图标，1px 线宽、统一视觉重心，集中于一张 atlas。
- **状态用渲染时着色（tint）表达，而非多张贴图**：
  normal `0xFFC8C8C8` / hover `0xFFFFFFFF` / active KP紫 / disabled 40% alpha。
- 图标清单：`gear`、`check`、`minus`、`plus`、`close`、`link`、`layers`。
- LDLib2 落地：`UIResourceTexture`（资源路径纹理）+ `TransformTexture`（着色变换）+ `IGuiTexture.setColor()` 运行时 tint。

### 3.3 网格与排版

- 4px 基数间距阶梯（4/8/12/16），行高 16px，MC 原生 9px 字体。
- 投影：右下偏移 2px、20% 黑（扁平投影惯例）。
- LDLib2 落地：`LayoutStyle.paddingAll(4/8/12/16)` + `gapAll(4)`；Taffy 引擎自动处理 Flexbox 间距。

## 4. 布局规格

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
│  Create Track Map         [☑]  │
│ ────────────────────────────── │
│  提示文本（次级色）              │
└────────────────────────────────┘
```

LDLib2 UI 树结构（建议）：

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
│   ├── UIElement (row): Label "Line Width" + Stepper
│   ├── UIElement (row): Label "Alpha" + Stepper
│   ├── UIElement (row): Label "Dashed" + Toggle
│   └── UIElement (row): Label "Create Track Map" + Toggle
└── Label (提示文本, textSecondary)
```

- 复选框：`Toggle` 14×14、1px 边框；勾选 = KP紫填充 + 米白 `check` 图标（通过 `ToggleStyle.MARK_BACKGROUND` 设置）。
- 面板高度按内容自适应（Taffy `height: auto`）；宽度维持 200px（`width: 200px`）。
- 步进器：`Button[−]` + `Label`（值显示）+ `Button[+]` 组合，或自定义 `BindableUIElement<Integer>` 子类。

## 5. 交互规格

- 复选框、步进器点击**即时生效**（移除「Use /kp provider set」提示；命令仍保留作为脚本途径）。
- 行 hover：8% 白底高亮（通过 `UIElement` 监听 `UIEvents.MOUSE_ENTER`/`MOUSE_LEAVE` 事件动态切换 `Style.background` 纹理，或用 LSS `:hover` 选择器若 LDLib2 支持）；参数行悬停显示说明 tooltip（`Tooltips` 数据类 + `HoverTooltips` 事件监听）。
- 步进器数值 clamp 在合理域（Alpha 0–1、Line Width 0.1–4，步进 0.05；Priority 整数步进 1）。
- `[×]` 与齿轮按钮均可关闭面板；不拦截 Esc。
- 熔断（`[FUSED]`）provider 的 Tab 置为 danger 色，控件禁用（`UIElement.setEnabled(false)` 动态切换样式，或监听 `UIEvents` 中的启用状态变化事件）。

## 6. 验收标准

- 悬停齿轮按钮只出现「Kinetic Planner」一条提示（Create 提示已被 Mixin 封杀）。
- 面板内所有开关/数值改动即时反映到地图叠加层，无需命令。
- Xaero 与 JourneyMap 两侧视觉与行为一致。
- 与 Modern UI 共存时字体渲染无异常（LDLib2 用 MC 原生 Font，与 Modern UI SDF 字体并行无冲突）。
- LDLib2 Mixin 与 KP Mixin 无注入点竞争（PoC 阶段验证 `ui.ScreenMixin`/`ui.ContainerEventHandlerMixin`）。

## 7. 关联

- Mixin 封杀设计（Create 按钮/Toast/叠加层接管）：见 `.codebuddy/memory/MEMORY.md`「接管 Create Track Map」。
- 前置设计：`2026-07-20-kinetic-planner-phase0-design.md`。
- Modern UI 嵌入式调研（否决依据）：`2026-07-26-modern-ui-embedded-rendering-research.md`。

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
side = "BOTH"
versionRange = "[2.2.0,)"
ordering = "AFTER"
relationship = "required"
```

### 8.2 Mixin 挂载点（保持不变）

KP 现有 Mixin 注入点 `Xaero's GuiMap.render(gg, ...)` 的 `@At("RETURN")` 保持不变。在 Mixin 中直接调用 LDLib2 渲染：

```java
@Mixin(GuiMap.class)
public class KpGuiMapMixin {
    @Unique
    private ModularUI kpConfigUI;

    @Inject(method = "render", at = @At("RETURN"))
    private void kp$renderConfigPanel(GuiGraphics gg, int mouseX, int mouseY,
                                       float partialTicks, CallbackInfo ci) {
        if (kpConfigUI == null) {
            kpConfigUI = KpConfigUIFactory.create();
            var window = Minecraft.getInstance().getWindow();
            kpConfigUI.init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
        if (KpClientState.isConfigPanelVisible()) {
            // 事件转发（HUD 层模式）
            kpConfigUI.getWidget().mouseMoved(mouseX, mouseY);
            kpConfigUI.getWidget().render(gg, mouseX, mouseY, partialTicks);
        }
    }
}
```

事件转发需手动处理（HUD 层模式下 `ModularUIWidget` 不自动接收事件）：
- `mouseMoved`/`mouseClicked`/`mouseReleased`/`mouseDragged`/`mouseScrolled`：在 Mixin 中转发到 `kpConfigUI.getWidget()`
- `keyPressed`/`keyReleased`/`charTyped`：若配置面板有焦点（如 `TextField`），需在 `Screen` 级别 Mixin 转发
- 鼠标事件优先级：先让 `kpConfigUI.getWidget().mouseClicked` 处理，若返回 false（未消费）则放行给 map

### 8.3 PoC 验收标准

1. 在 Xaero GuiMap 打开后，按齿轮键能切换 KP 配置面板可见性
2. 面板使用 LDLib2 `Toggle`/`TabView`/`Button`/`Label` 控件，视觉为 KP 紫色 accent + 深灰底
3. `Toggle` 切换 → map 上的叠加层实时变化（如 Enabled = false 时叠加层消失）
4. `TabView` 切换 provider（xaeroworldmap ↔ journeymap），配置项随 provider 切换更新
5. 步进器（`Button[−]`/`Button[+]`）点击 → 数值即时变化且 clamp 在合理域
6. 鼠标在配置面板区域内被面板消费（不穿透到 map）；区域外放行给 map（不破坏 map 拖动/缩放）
7. 与 Modern UI 共存时字体渲染正常（dev 环境验证）
8. LDLib2 Mixin 与 KP Mixin 无注入点竞争（无 Mixin 应用失败日志）

### 8.4 已知风险与缓解

| 风险 | 概率 | 缓解 |
|---|---|---|
| LDLib2 `ui.ScreenMixin` 与 KP Mixin 竞争 | 低 | PoC 阶段检查 Mixin 应用日志；必要时调整 KP Mixin 优先级 |
| HUD 层模式下事件转发遗漏导致控件不响应 | 中 | 严格转发全部 `GuiEventListener` 方法；参考 `ModularUIScreen` 的事件路由实现 |
| `ModularUIWidget.render` 在外部 gg 上调用时布局未计算 | 低 | `ModularUIWidget.render` 内部自动调用 `calculateStyleAndLayout()`，无需手动触发 |
| LDLib2 版本升级破坏 API | 低 | 锁定 `ldlib2_version = 2.2.26`；升级前在 dev 环境验证 |
| Taffy 布局引擎在 1.21.1 NeoForge 运行期异常 | 低 | Taffy 是 LDLib2 内置 Rust 库，已在 LDLib2 测试环境验证 |
