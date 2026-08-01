# Kinetic Planner UI 组件样式-行为分离设计

> **状态**：设计稿 v2（基于 LDLib2 运行时能力审计重写，等待实现计划）
> **基于**：2026-08-01 讨论结论 + LDLib2 样式体系源码审计
> **目标**：将 LDLib2 UI 组件按功能区重组，行为类与样式分离，样式通过 LDLib2 原生机制（资源包自动扫描 + 程序化 `Stylesheet.parse` + `StylesheetManager` 热重载）加载，token 由 Java 常量驱动。

---

## 0. v2 修订摘要

本版基于 LDLib2 样式体系源码审计，对 v1 做了以下根本性修正：

| 维度 | v1 方案 | v2 方案 | 修正理由 |
|---|---|---|---|
| **样式生成** | MC Data Generator（`GatherDataEvent`）构建期生成静态 `.lss` | 程序化 `Stylesheet.parse(String)` + 资源包自动扫描 | Data Generator 生成死文件，无法热重载；`StylesheetManager` 已支持运行时热重载（F3+T），`Stylesheet.parse` 已被 `KpStylesheet` 验证可行 |
| **token 机制** | token class（`.kp-color-bg-panel`）组合标识 class | Java 常量 + `String.formatted` 插值（沿用 `KpStylesheet` 模式） | LSS 无 CSS 变量的痛点已被 `String.formatted` 解决；token class 要求组件组合标识 class，反而增加耦合 |
| **token 归属** | `src/main/java`（common sourceSet） | `src/client/java`（client sourceSet） | GUI 设计 token 是渲染关注点；common 层不应持有颜色/间距常量 |
| **包结构** | 一组件一包 + 每组件一个 StyleProvider | 按功能区聚合（editor/ ribbon/ config/ widgets/），共享样式归 theme LSS | ribbon 多个组件共享 `kp-ribbon-tool`；强制一组件一包导致共享样式重复 |
| **响应式绑定** | 未提及 | 附录章节说明 LDLib2 `IBindable`/`DataBindingBuilder` 能力 | 用户提示"LDLib2 像 Vue2"指向此能力，应纳入设计视野 |

### 0.1 LDLib2 样式体系关键事实（审计依据）

以下结论来自 LDLib2 源码审计（`2026-07-27-ldlib2-api-verification.md` §3 + 本次专项探索）：

1. **`StylesheetManager`（`gui.ui.style.StylesheetManager`）**
   - 实现 `ResourceManagerReloadListener`，自动扫描所有资源包中 `assets/<namespace>/lss/*.lss` 文件并合并。
   - **支持热重载**：资源包 reload（F3+T）时 `onResourceManagerReload` 重新解析全部 LSS，原地更新 `packStylesheets`，并 `notifyEnginesReload()` 通知所有活跃 `StyleEngine`。
   - `StyleEngine.scheduleFullReload()` 将所有元素标记为脏，下一帧 `calculateStyle` 对每个脏元素重新匹配规则、diff 新旧 `StyleRule`、仅在实际变化时更新。**这是真正的运行时动态能力。**

2. **`Stylesheet.parse(String)`（`gui.ui.style.Stylesheet`）**
   - 可在任意时刻从字符串解析 `Stylesheet` 对象。
   - 现有 `KpStylesheet.create()` 已用此 API + `String.formatted(token常量)` 程序化生成样式表，通过 `UI.of(root, stylesheet)` 应用。
   - 支持 `addRule` / `removeRule` / `merge` 运行时增删规则。

3. **运行时样式 API**
   - `StylesheetManager.INSTANCE.registerBuiltinStylesheet(ResourceLocation, Stylesheet)`：程序化注册全局样式表。
   - `StyleEngine.addStylesheet` / `removeStylesheet`：运行时增删样式表。
   - `UIElement.addLocalStylesheet(Stylesheet)` / `addLocalStylesheet(String)`：子树作用域样式。

4. **LSS 能力限制**（已被现有代码规避）
   - 不支持 CSS 变量（`var(--x)`）→ 用 Java 常量 + `String.formatted` 插值。
   - 不支持 `border` / `padding` / `margin` 简写 → 用 `sdf(...)` / `padding-all` 等。
   - 支持 `:hover` / `:focus` / `:disabled` 伪类（框架自动管理 `__hovered__` / `__focused__` / `__disabled__` 类）。

5. **LDLib2 响应式绑定体系**（详见附录 §11）
   - `IBindable<T>` / `IDataConsumer<T>` / `IDataProvider<T>` / `DataBindingBuilder` 提供控件级数据→UI 自动更新。
   - `SyncValue` 基于 tick 轮询脏检查实现 C/S 同步。
   - **与样式分离正交**，但理解此能力有助于后续 UI 状态管理设计。

---

## 1. 背景与问题

当前 `src/client/java/.../gui/` 下的 UI 组件存在以下耦合：

1. **样式硬编码在 Java 中**：大量 `layout(...)`、`backgroundTexture(...)`、`paddingAll(...)` 与组件行为写在一起（审计统计：`KpConfigUIFactory` 14 处、`RibbonToggleButton` 6 处、`ToolPanelView` / `KpMapEditor` / `GroupPanel` / `RibbonBuilder` / `RibbonTabViewAdapter` / `ToolWidgetFactory` / `ConfigStepperRow` / `ConfigToggleRow` 各 2–5 处）。
2. **样式定义分散**：`kp.lss`（资源包静态文件）覆盖 Ribbon + ToolPanel；`KpStylesheet.create()`（程序化）覆盖配置面板。两套 token 常量各自维护（`kp.lss` 注释内联色值；`KpStylesheet` 暴露 `int` 常量），且 `KpIconButton` 还有一份独立的 `ACCENT_COLOR` 硬编码。
3. **组件间样式依赖容器背景**：例如 `ToolPanelView` 拖到 `centerWindow` 后丢失背景，因为样式未自包含。
4. **token 重复定义**：`KpStylesheet.KP_ACCENT`（`0xFF7C57D4`）与 `KpIconButton.ACCENT_COLOR`（`0xFF7C57D4`）数值相同但各自独立，`kp.lss` 中 `#FF7C57D4` 又是第三处。

本设计借鉴 Web 组件化思路（样式集中管理 + token 单一来源），同时保留 LDLib2 的命令式对象树本质。

---

## 2. 设计目标

1. **行为-样式分离**：Java 类只负责结构、事件、状态；颜色、间距、背景等外观由 LSS 定义。
2. **token 单一来源**：颜色/间距等设计 token 以 Java 常量形式集中管理（`KpTheme`），所有 LSS 通过 `String.formatted` 引用，禁止在 LSS 字符串或 Java 内联样式中硬编码色值。
3. **资源包驱动 + 程序化双轨**：主体样式放资源包 `assets/kinetic_planner/lss/*.lss` 被 `StylesheetManager` 自动扫描（支持 F3+T 热重载）；需要 token 插值的样式由程序化 `Stylesheet.parse` 生成。
4. **按功能区组织**：保留现有 `editor/` `ribbon/` `config/` `widgets/` 功能区划分，不强制"一组件一包"。
5. **共享样式归 theme**：跨组件复用的样式规则（如 `kp-ribbon-tool` 被 `Button`/`QatBar`/`RibbonToggleButton` 共用）归入 `theme.lss`。

---

## 3. 架构概述

```
┌─────────────────────────────────────────────────────────────┐
│  client sourceSet                                           │
│  gui/                                                        │
│  ├── theme/                         ← 主题 token + LSS 工厂  │
│  │   ├── KpTheme.java               ← 颜色/间距 token 常量   │
│  │   └── KpThemeStylesheet.java      ← 程序化 LSS 生成+注册  │
│  ├── editor/                                                │
│  │   ├── ...（行为类，不变）                                 │
│  ├── ribbon/                                                │
│  │   ├── ...（行为类，不变）                                 │
│  ├── config/                                                │
│  │   ├── KpConfigUIFactory.java     ← 删除内联样式           │
│  │   ├── KpStylesheet.java          ← 合并到 KpThemeStylesheet│
│  │   └── ...                                                │
│  └── widgets/                                               │
│      └── ...                                                │
├─────────────────────────────────────────────────────────────┤
│  资源包（src/main/resources 或 src/client/resources）        │
│  assets/kinetic_planner/lss/                                │
│  ├── ribbon.lss          ← Ribbon 静态规则（无 token 插值）  │
│  ├── editor.lss          ← ToolPanel + MapPlaceholder       │
│  └── config.lss          ← 配置面板静态规则                  │
└─────────────────────────────────────────────────────────────┘
                              │
              程序化 Stylesheet.parse（token 插值）
                              ▼
              StylesheetManager 自动扫描资源包 LSS
              + KpThemeStylesheet 程序化注册
              → StyleEngine 运行时匹配（支持 F3+T 热重载）
```

### 3.1 双轨样式加载策略

| 轨道 | 机制 | 适用场景 | 热重载 |
|---|---|---|---|
| **资源包静态 LSS** | `.lss` 文件放 `assets/kinetic_planner/lss/`，`StylesheetManager` 自动扫描 | 无需 token 插值的纯结构/状态规则（如 `flex-direction`、`__on__` 状态） | ✅ F3+T |
| **程序化 LSS** | `KpThemeStylesheet.create()` 用 `Stylesheet.parse(String)` + `String.formatted` 生成 | 需要 token 插值的规则（如颜色 `background: rect(#%08X)`） | ⚠️ 需重新调用 `registerBuiltinStylesheet` |

**决策原则**：能写纯静态 LSS 的优先写静态文件（享受热重载）；必须插值 token 的才走程序化。token 插值规则集中在 `KpThemeStylesheet`，不在各组件类中散落。

---

## 4. Theme Token 系统

### 4.1 KpTheme.java

位于 `src/client/java/net/jsmua/kinetic_planner/gui/theme/KpTheme.java`（**client sourceSet**，GUI 渲染关注点）。

```java
package net.jsmua.kinetic_planner.gui.theme;

/**
 * KP 设计 token 常量。所有 LSS 颜色/间距的唯一来源。
 *
 * <p>供 {@link KpThemeStylesheet} 通过 {@code String.formatted} 插值进 LSS 字符串。
 * 禁止在组件 Java 类或 LSS 文件中硬编码色值。
 */
public final class KpTheme {
    private KpTheme() {}

    // ===== Background =====
    public static final int PANEL_BG       = 0xFF2C2C34;  // 面板底色（不透明深灰）
    public static final int PANEL_BG_TRANS = 0xF228282C;  // 面板底色（95% 不透明）
    public static final int TOOLTIP_BG     = 0xE61E1F22;

    // ===== Border / Highlight =====
    public static final int PANEL_BORDER    = 0xFF000000;
    public static final int PANEL_HIGHLIGHT = 0x40FFFFFF;
    public static final int SEPARATOR       = 0xFF4B5563;

    // ===== Accent =====
    public static final int ACCENT        = 0xFF7C57D4;  // KP 紫
    public static final int ACCENT_DIM    = 0x807C57D4;
    public static final int DANGER        = 0xFFFFAD60;  // Create 橙（熔断标记）

    // ===== Text =====
    public static final int TEXT_PRIMARY   = 0xFFF3EFE0;
    public static final int TEXT_SECONDARY = 0xFF9CA3AF;
    public static final int TEXT_MUTED     = 0xFF8B8B93;

    // ===== Button =====
    public static final int BUTTON_BG       = 0x803D3D42;
    public static final int BUTTON_HOVER    = 0xB04A4A52;
    public static final int BUTTON_PRESSED  = 0x807C57D4;

    // ===== Row hover =====
    public static final int ROW_HOVER = 0x14FFFFFF;

    // ===== Spacing =====
    public static final int GAP_SM     = 2;
    public static final int PADDING_SM = 2;
    public static final int PADDING_MD = 4;
    public static final int PADDING_LG = 8;
}
```

**与 v1 的区别**：
- v1 用 `String`（`"#ff2c2c34"`）作为 token 类型 → v2 用 `int`（`0xFF2C2C34`），因为 `String.formatted("#%08X", int)` 是已验证的插值模式（见 `KpStylesheet.java`）。
- v1 放 common sourceSet → v2 放 client sourceSet，符合关注点归属。
- v1 的 token class（`.kp-color-bg-panel`）方案废弃 → v2 用 `String.formatted` 直接插值，无需组合标识 class。

### 4.2 KpThemeStylesheet.java

合并现有 `KpStylesheet.java` 的职责，扩展为全组件的 token 插值 LSS 工厂。

```java
package net.jsmua.kinetic_planner.gui.theme;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.resources.ResourceLocation;

/**
 * 程序化生成需要 token 插值的 LSS 样式表，并注册到 StylesheetManager。
 *
 * <p>分工：
 * <ul>
 *   <li>本类：所有需要 {@code #%08X} 色值插值的规则</li>
 *   <li>资源包 {@code assets/kinetic_planner/lss/*.lss}：无需插值的纯结构/状态规则</li>
 * </ul>
 *
 * <p>调用 {@link #register()} 在客户端 setup 阶段注册为 builtin stylesheet，
 * 支持 F3+T 热重载时重新解析。
 */
public final class KpThemeStylesheet {

    private static final ResourceLocation THEME_RL =
        ResourceLocation.fromNamespaceAndPath("kinetic_planner", "theme_colors");

    private KpThemeStylesheet() {}

    /**
     * 生成含 token 插值的主题 LSS 样式表。
     *
     * <p>所有颜色值通过 {@code String.formatted("#%08X", KpTheme.XXX)} 插值，
     * 确保色值单一来源。
     */
    public static Stylesheet create() {
        String lss = """
            // ===== 颜色 token 规则（需要插值，无法放静态 LSS） =====

            // 面板根容器：圆角 + 1px 黑边
            .kp-panel {
                background: sdf(#%08X, 4, 1, #%08X);
            }
            .kp-panel label { color: #%08X; }

            // 按钮通用
            .kp-base-button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }

            // Ribbon 工具按钮（Button / Toggle / QAT 共用）
            .kp-ribbon-tool {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tool.__on__ {
                base-background: rect(#%08X);
                color: #%08X;
            }

            // Ribbon Tab
            .kp-ribbon-tab {
                base-background: rect(#00000000);
                hover-background: rect(#14555560);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tab.__selected__ {
                base-background: rect(#%08X);
                color: #%08X;
            }

            // ToolPanel
            .kp-tool-panel {
                background: rect(#%08X);
            }
            .kp-tool-button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }

            // 配置面板
            .kp-title-row { background: sdf(#%08X, 2, 1, #%08X); }
            .kp-config-row:hover { background: rect(#%08X); }
            .kp-text-secondary { color: #%08X; }
            .kp-title { color: #%08X; }
            .kp-tab-fused { color: #%08X; }

            // 分隔符
            .kp-ribbon-separator { background: rect(#%08X); }
            .kp-ribbon-qat-empty { color: #%08X; }
            """.formatted(
                // panel
                KpTheme.PANEL_BG_TRANS, KpTheme.PANEL_BORDER,
                KpTheme.TEXT_PRIMARY,
                // base button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED, KpTheme.TEXT_PRIMARY,
                // ribbon tool
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED, KpTheme.TEXT_PRIMARY,
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // ribbon tab
                KpTheme.ACCENT, KpTheme.TEXT_SECONDARY,
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // tool panel
                KpTheme.PANEL_BG,
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED, KpTheme.TEXT_PRIMARY,
                // config
                KpTheme.PANEL_BG_TRANS, KpTheme.PANEL_HIGHLIGHT,
                KpTheme.ROW_HOVER,
                KpTheme.TEXT_SECONDARY,
                KpTheme.TEXT_PRIMARY,
                KpTheme.DANGER,
                // separator / qat empty
                KpTheme.SEPARATOR,
                KpTheme.SEPARATOR
            );
        return Stylesheet.parse(lss);
    }

    /**
     * 注册为 builtin stylesheet。
     *
     * <p>在客户端 setup 阶段调用一次。StylesheetManager 在资源包 reload 时
     * 会保留此注册（builtin 不随资源包清除），并通知 StyleEngine 重新匹配。
     */
    static void register() {
        StylesheetManager.INSTANCE.registerBuiltinStylesheet(THEME_RL, create());
    }
}
```

---

## 5. 组件行为规范

### 5.1 行为主类规范

- **不加硬编码样式**：不在 Java 中调用 `layout(...)` 设置 padding/gap/background，除非与父容器布局契约强相关（如 `widthPercent(100)` 纯结构性约束可保留，但颜色/背景类一律走 LSS）。
- **必须声明组件 class**：构造函数中调用 `addClass("kp-<component>")`。
- **事件行为可注入**：重要行为通过构造函数参数或 setter 注入，减少对其他单例的依赖。
- **布局契约例外**：`flexDirection` / `widthPercent` / `height` 等纯结构性布局可在 Java 中保留（与父容器布局契约强相关），但 padding/gap/background 等外观属性必须走 LSS。

**示例（ToolPanelView 迁移后）**：

```java
public class ToolPanelView extends View {
    public ToolPanelView() {
        super();
        addClass("kp-tool-panel");
        // 纯结构布局可保留（COLUMN 方向是组件契约）
        layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
        // padding/gap/background 已移至 LSS .kp-tool-panel
        for (var tool : EditToolState.Tool.values()) {
            String label = tool.getDisplayName() + " (" + tool.getKeyLabel() + ")";
            addToolButton(label, tool);
        }
    }

    private void addToolButton(String label, EditToolState.Tool tool) {
        Button btn = new Button();
        btn.addClass("kp-tool-button");
        btn.setText(label);
        btn.setOnClick(event -> EditToolState.getInstance().setCurrentTool(tool));
        addChild(btn);
    }
}
```

### 5.2 纯静态 LSS 规范（资源包文件）

无需 token 插值的纯结构/状态规则放 `assets/kinetic_planner/lss/*.lss`，享受 F3+T 热重载。

**`editor.lss` 示例**：

```lss
// ToolPanel 纯结构规则（颜色在 KpThemeStylesheet 中插值）
.kp-tool-panel {
    padding-all: 4;
    gap-all: 2;
}
.kp-tool-button {
    padding-horizontal: 4;
    padding-vertical: 2;
}

// Layout helpers
.kp-fill-width { width: 100%; flex-grow: 1; }
.kp-fill-height { height: 100%; flex-grow: 1; }
.kp-transparent-bg { background: rect(#00000000); }
```

**`ribbon.lss` 示例**：

```lss
.kp-ribbon-bar {
    padding-all: 2;
    gap-all: 2;
}
.kp-ribbon-content {
    flex-direction: row;
    align-items: center;
    height: 44;
    gap-all: 4;
    overflow-x: scroll;
}
.kp-ribbon-group {
    flex-direction: column;
    align-items: center;
    padding-all: 4;
    gap-all: 2;
    min-width: 48;
}
.kp-ribbon-group-label {
    margin-top: 2;
    height: 9;
}
.kp-ribbon-tool-row {
    flex-direction: row;
    align-items: center;
    gap-all: 2;
}
.kp-ribbon-tool-label {
    height: 9;
}
.kp-ribbon-qat {
    flex-direction: row;
    align-items: center;
    gap-all: 2;
    padding-horizontal: 2;
}
.kp-ribbon-separator {
    width: 1;
    height: 16;
    margin-horizontal: 2;
}
```

> **颜色规则不在静态 LSS 中**：`background: rect(#ff2c2c34)` 等含色值规则由 `KpThemeStylesheet.create()` 插值生成。静态 LSS 只管 flex/padding/gap/width/height 等结构属性。

---

## 6. 样式注册流程

### 6.1 客户端初始化注册

在 `KineticPlannerClient` 的客户端 setup 事件中注册程序化主题样式表：

```java
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class KineticPlannerClient {
    // ...

    @SubscribeEvent
    public static void onClientSetup(ClientPlayerNetworkEvent.LoggingIn event) {
        // 注册程序化主题样式表（含 token 插值的颜色规则）
        KpThemeStylesheet.register();
    }
}
```

> **注意**：`StylesheetManager` 在客户端启动时已初始化，`registerBuiltinStylesheet` 注册的样式表会被 `StyleEngine` 自动纳入匹配。资源包中的静态 `.lss` 文件由 `StylesheetManager` 自动扫描，无需注册。

### 6.2 资源包 LSS 文件

```
src/main/resources/assets/kinetic_planner/lss/
├── ribbon.lss      ← Ribbon 结构规则（flex/padding/gap/尺寸）
├── editor.lss      ← ToolPanel + MapPlaceholder + layout helpers
└── config.lss      ← 配置面板结构规则
```

- 现有 `kp.lss` 的结构规则拆分到上述三个文件，颜色规则移入 `KpThemeStylesheet`。
- 现有 `kp.lss` 删除（其内容被 ribbon.lss + editor.lss + KpThemeStylesheet 取代）。

### 6.3 热重载验证

- **静态 LSS**：修改 `ribbon.lss` 后按 F3+T，`StylesheetManager.onResourceManagerReload` 重新解析，`StyleEngine` 重新匹配，UI 即时更新。
- **程序化 LSS**：修改 `KpTheme.java` token 值后需重新调用 `KpThemeStylesheet.register()`。可扩展 `register()` 为幂等（先 `removeBuiltinStylesheet` 再注册），但开发期一般通过重启客户端生效。

---

## 7. 组件迁移映射

| 当前文件 | 迁移动作 | 样式去向 |
|---|---|---|
| `gui/ToolPanelView.java` | 移除 `layout(padding/gap)` 内联；保留 `flexDirection(COLUMN)` | `editor.lss` + `KpThemeStylesheet`（颜色） |
| `gui/MapPlaceholderView.java` | 保留 `getStyle().backgroundTexture(EMPTY)`（透明链刻意不走 LSS） | 不迁移 |
| `gui/ribbon/RibbonBar.java` | 移除 `layout(padding/gap)`；保留 `flexDirection(COLUMN)` + `widthPercent` | `ribbon.lss` |
| `gui/ribbon/QatBar.java` | 移除 `layout` 内联 | `ribbon.lss` + `KpThemeStylesheet`（颜色） |
| `gui/ribbon/internal/GroupPanel.java` | 移除 `layout` 内联 | `ribbon.lss` |
| `gui/ribbon/internal/RibbonBuilder.java` | 移除 content `layout(height)`；保留 flexDirection | `ribbon.lss` |
| `gui/ribbon/internal/RibbonTabViewAdapter.java` | 移除 `layout` 内联 | `ribbon.lss` |
| `gui/ribbon/internal/ToolWidgetFactory.java` | 移除 `layout` 内联 | `ribbon.lss` |
| `gui/ribbon/internal/toggle/RibbonToggleButton.java` | 移除 `configureLayout` 的 `paddingAll`；保留 flexDirection + 尺寸（ToolSize 契约） | `ribbon.lss` |
| `gui/config/KpConfigUIFactory.java` | 移除全部 `layout(padding/gap)` + `style(backgroundTexture)` 内联 | `config.lss` + `KpThemeStylesheet`（颜色） |
| `gui/config/KpStylesheet.java` | 合并到 `KpThemeStylesheet`，删除本文件 | `KpThemeStylesheet` |
| `gui/widgets/KpIconButton.java` | `ACCENT_COLOR` 硬编码改为引用 `KpTheme.ACCENT` | 不迁移（自绘 POJO 不走 LSS） |
| `gui/widgets/ConfigStepperRow.java` | 移除 `layout` 内联 | `config.lss` |
| `gui/widgets/ConfigToggleRow.java` | 移除 `layout` 内联 | `config.lss` |
| `gui/editor/KpMapEditor.java` | `menuContainer.layout` 保留（Editor 布局契约）；`top.getLayout().height(60)` 保留 | 不迁移 |

### 7.1 布局保留原则

以下 `layout(...)` 调用**不迁移**到 LSS，因为它们是组件与父容器的布局契约：

- `flexDirection(...)`：组件内部排列方向是结构契约
- `widthPercent(100)` / `flexGrow(1)`：填充父容器契约
- `height(60)` / `height(44)`：固定尺寸约束（如 Ribbon header 60px = 20 header + 40 content）
- `positionType(ABSOLUTE)` + `right/top`：配置面板浮窗定位契约

以下 `layout(...)` 调用**迁移**到 LSS：

- `paddingAll(...)` / `paddingHorizontal(...)` / `paddingVertical(...)`
- `gapAll(...)`
- `alignItems(...)` / `justifyContent(...)`（外观对齐，非结构契约）

### 7.2 自绘 POJO 例外

`KpIconButton` / `KpGearButton` / `KpEditButton` 是自绘 POJO（不继承 `UIElement`），样式由 `gg.fill` 等绘制代码决定。它们的颜色常量应引用 `KpTheme`，但不走 LSS 体系。

---

## 8. 兼容性说明

- **允许重新设计 API**：本次重构可调整构造函数签名、类名、包路径。
- **静态 `kp.lss` 拆分删除**：内容拆分到 `ribbon.lss` + `editor.lss` + `config.lss`（结构）和 `KpThemeStylesheet`（颜色），原文件删除。
- **`KpStylesheet.java` 合并删除**：其 token 常量迁入 `KpTheme`，LSS 生成逻辑迁入 `KpThemeStylesheet`。
- **Lang 文件保留**：`lang/en_us.json`、`lang/zh_cn.json` 继续放在 `src/main/resources/`。
- **非 UIElement 组件**：`KpGearButton`、`KpEditButton` 是自绘 POJO，不继承 `UIElement`，样式由代码绘制决定，颜色常量引用 `KpTheme`。
- **Data Generator 不用于 LSS**：`build.gradle` 中 `runData` 配置保留（用于未来其他数据生成需求），但本次重构不使用 Data Generator 生成 LSS 文件。`src/generated/resources/` 不产生 LSS 输出。

---

## 9. 测试策略

1. **编译测试**：`gradlew compileClientJava compileJava` 必须通过。
2. **样式加载测试**：`gradlew runClient`，检查日志无 LSS 解析错误。
3. **运行时验收**：`gradlew runClient`，检查：
   - 编辑器/Ribbon/配置面板样式与重构前一致
   - ToolPanelView 拖入 centerWindow 后仍保持自身背景与按钮样式
   - 无 LSS 解析错误日志
4. **热重载验收**：运行时修改 `ribbon.lss` 的 padding 值，按 F3+T，确认 UI 即时更新（验证 `StylesheetManager` 热重载生效）。
5. **token 一致性验收**：确认 `KpIconButton.ACCENT_COLOR` 已改为 `KpTheme.ACCENT` 引用，全局搜索无 `0xFF7C57D4` 等裸色值散落。

---

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 静态 LSS 与程序化 LSS 规则冲突 | 明确分工：静态 LSS 只管结构（flex/padding/gap/尺寸），程序化 LSS 只管颜色/背景；同一 class 的同一属性不同时出现在两处 |
| 跨组件共享样式归属 | 共享样式（`kp-ribbon-tool` 等）归入 `KpThemeStylesheet`（颜色）+ `ribbon.lss`（结构），不归属任何单一组件 |
| 程序化 LSS 不支持 F3+T 热重载 | 颜色 token 变更频率低；开发期改 token 重启客户端即可；静态 LSS（padding/gap 等高频调整项）享受热重载 |
| 重构范围大，回归风险高 | 按功能区分子任务（ribbon → editor → config → widgets），每个任务独立 `runClient` 验收 |
| `KpEditorScreen` 未显式传入 stylesheet | `UI.of(this.editor)` 不传 stylesheet 时，`StyleEngine` 仍会匹配 `StylesheetManager` 管理的全局样式表（资源包 LSS + builtin stylesheet），所以 Ribbon/Editor UI 能获得样式。配置面板单独 `UI.of(root, stylesheet)` 是历史遗留，迁移后应统一依赖全局注册 |

---

## 11. 附录：LDLib2 响应式绑定能力概览

用户提示"LDLib2 像 Vue2"指向 LDLib2 的响应式数据绑定体系。本节说明其能力，作为样式分离的配套增强方向（**本次重构不实施**，仅记录供后续设计参考）。

### 11.1 核心接口（`com.lowdragmc.lowdraglib2.gui.sync.bindings`）

| 接口 | 职责 | 类比 Vue2 |
|---|---|---|
| `IDataProvider<T>` | 可被监听的数据源，`registerListener(Consumer<T>)` 返回 `ISubscription` | `data` 中的响应式属性 |
| `IDataConsumer<T>` | 消费者侧，`bindDataSource(IDataProvider)` | 组件 watch/computed |
| `IDataSource<T>` | 最底层 getter/setter 对，`IDataSource.of(setter, getter)` | `props` 传递 |
| `IBindable<T>` | UIElement 实现，`bind(IBinding)` 接入同步通道 | `v-model` 绑定 |
| `IObservable<T>` / `IObserver<T>` | 纯本地观察者模式 | `$watch` |

### 11.2 运行时机制

核心是 `SyncValue` + `SimpleBinding`：
- `SyncValue` 持有 `SyncValueHolder`，通过 `hasChanged()` 脏检查 + `update()` 轮询 `valueProvider`。
- `SimpleBinding` 在构造时根据 `isRemote` 决定监听方向：客户端监听 → `setRemoteValue`（C2S）、服务端监听 → `setServerValue`（S2C）。
- 基于 `SyncStrategy`（`ALWAYS` / `CHANGED_PERIODIC` / `NONE`）的 tick 轮询同步。

**与 Vue2 的本质区别**：LDLib2 的响应式基于 Minecraft C/S 网络同步的脏检查 + tick 轮询，不是 Vue2 的依赖追踪/虚拟 DOM。用户"像 Vue2"的说法只在"数据变化能自动推送 UI"这一点上成立。

### 11.3 控件级自动更新

`Label`（实现 `IBindable<Component>, IDataConsumer<Component>`）：
```java
// 官方示例
new Label().bind(DataBindingBuilder.componentS2C(() -> Component.literal("...")).build())
```
`Label.bindDataSource` 内部 `dataProvider.registerListener(this::setText, true)` --**数据变化时自动调 `setText` 更新 UI**。

`DataBindingBuilder` 提供类型化工厂：`bool/intVal/string/component/itemStack/...` 及 `...S2C`（只读）/`...C2S`（只写）变体。

### 11.4 当前项目未使用响应式绑定

审计确认：KP 项目 GUI 层 **完全未 import** `gui.sync.bindings.*`，所有 UI 是纯命令式 + 手动 `setText` + 事件监听。后续若需 UI 状态自动同步（如配置面板实时反映 provider 状态变化），可引入 `DataBindingBuilder`。

### 11.5 `@Data` 注解澄清

LDLib2 中看到的 `@Data` 是标准 `lombok.Data`（生成 getter/setter/equals/hashCode/toString + `staticConstructor`），**不是** LDLib2 自定义注解，与数据同步/响应式无关。

---

## 12. 待实现计划产出

下一步：基于本设计稿 v2，使用 `writing-plans` skill 生成按功能区分任务的实现计划。

建议任务划分：
1. 创建 `gui/theme/KpTheme.java` + `KpThemeStylesheet.java`，合并 `KpStylesheet` token
2. 拆分 `kp.lss` → `ribbon.lss` + `editor.lss` + `config.lss`，颜色规则移入 `KpThemeStylesheet`
3. 迁移 Ribbon 组件（RibbonBar / QatBar / GroupPanel / RibbonBuilder / RibbonTabViewAdapter / ToolWidgetFactory / RibbonToggleButton）
4. 迁移 Editor 组件（ToolPanelView）
5. 迁移 Config 组件（KpConfigUIFactory / ConfigStepperRow / ConfigToggleRow）
6. 迁移自绘 POJO（KpIconButton / KpGearButton / KpEditButton）颜色常量引用 `KpTheme`
7. 注册 `KpThemeStylesheet.register()` 到客户端 setup
8. 删除 `KpStylesheet.java` + `kp.lss`，全量 `runClient` 验收
