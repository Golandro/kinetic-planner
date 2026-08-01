# 组件样式-行为分离实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 LDLib2 UI 组件的内联样式迁移到 LSS 文件 + 程序化 `KpThemeStylesheet`，建立 token 单一来源 `KpTheme`，删除分散的 `kp.lss` 和 `KpStylesheet.java`。

**Architecture:** 双轨样式加载--静态 LSS 文件（结构规则，`StylesheetManager` 自动扫描 + F3+T 热重载）+ 程序化 `Stylesheet.parse`（颜色规则，`String.formatted` 插值 `KpTheme` 常量）。`KpThemeStylesheet.register()` 在 `FMLClientSetupEvent` 注册为 builtin stylesheet。

**Tech Stack:** Java 21, LDLib2 (`Stylesheet`, `StylesheetManager`, `UIElement`, `StyleEngine`), NeoForge 1.21.1, Gradle

## Global Constraints

- **sourceSet 分离硬规则**：`KpTheme.java` 和 `KpThemeStylesheet.java` 放 `src/client/java`（GUI 渲染关注点），不引用 `net.minecraft.client.*` 以外的 client 专属 API 时可放 common，但颜色/间距是渲染关注点 -> client sourceSet
- **LSS 无 CSS 变量**：颜色用 `String.formatted("#%08X", KpTheme.XXX)` 插值，不用 `var(--x)`
- **LSS 不支持简写**：用 `padding-all` / `gap-all` / `padding-horizontal` 等，不用 `padding` / `gap` 简写
- **LSS 颜色格式**：`#AARRGGBB`（如 `#ff2c2c34`），纯色背景用 `background: rect(#color)`，圆角边框用 `background: sdf(#color, radius, borderWidth, #borderColor)`
- **layout 保留原则**：`flexDirection` / `widthPercent` / `flexGrow` / `flexShrink` / `width` / `height` 保留在 Java 中（布局契约）；`paddingAll` / `gapAll` / `alignItems` / `justifyContent` 迁移到 LSS（外观属性）
- **编译验证命令**：`.\gradlew.bat compileClientJava compileJava`（PowerShell）
- **Button.setOnClick**：签名是 `setOnClick(UIEventListener)`，包装 Runnable 用 `setOnClick(event -> runnable.run())`
- **不允许 Data Generator 生成 LSS**：样式通过资源包静态 LSS + 程序化 `Stylesheet.parse` 加载
- **KpIconButton 是自绘 POJO**：不继承 `UIElement`，颜色通过 `GuiGraphics.fill(int)` 绘制，不走 LSS；颜色常量改为引用 `KpTheme`

---

### Task 1: 创建 KpTheme + KpThemeStylesheet 骨架 + 注册

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/theme/KpTheme.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/theme/KpThemeStylesheet.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java` (onClientSetup 方法, ~L60-94)

**Interfaces:**
- Consumes: 无（首个任务）
- Produces: `KpTheme`（token 常量类，后续所有任务引用）、`KpThemeStylesheet.create()` / `KpThemeStylesheet.register()`（后续任务填充颜色规则）

- [ ] **Step 1: 创建 KpTheme.java**

```java
package net.jsmua.kinetic_planner.gui.theme;

/**
 * KP 设计 token 常量。所有 LSS 颜色/间距的唯一来源。
 *
 * <p>供 {@link KpThemeStylesheet} 通过 {@code String.formatted} 插值进 LSS 字符串。
 * 禁止在组件 Java 类或 LSS 文件中硬编码色值。
 *
 * <p>放在 client sourceSet：颜色/间距是 GUI 渲染关注点。
 */
public final class KpTheme {
    private KpTheme() {}

    // ===== Background =====
    public static final int PANEL_BG          = 0xFF2C2C34;
    public static final int PANEL_BG_TRANS    = 0xF228282C;
    public static final int TOOLTIP_BG        = 0xE61E1F22;

    // ===== Border / Highlight =====
    public static final int PANEL_BORDER      = 0xFF000000;
    public static final int PANEL_HIGHLIGHT   = 0x40FFFFFF;
    public static final int SEPARATOR         = 0xFF4B5563;

    // ===== Accent =====
    public static final int ACCENT            = 0xFF7C57D4;
    public static final int ACCENT_DIM        = 0x807C57D4;
    public static final int DANGER            = 0xFFFFAD60;

    // ===== Text =====
    public static final int TEXT_PRIMARY      = 0xFFF3EFE0;
    public static final int TEXT_SECONDARY    = 0xFF9CA3AF;
    public static final int TEXT_MUTED        = 0xFF8B8B93;

    // ===== Button =====
    public static final int BUTTON_BG         = 0x803D3D42;
    public static final int BUTTON_HOVER      = 0xB04A4A52;
    public static final int BUTTON_PRESSED    = 0x807C57D4;

    // ===== Row hover =====
    public static final int ROW_HOVER         = 0x14FFFFFF;

    // ===== Transparent =====
    public static final int TRANSPARENT       = 0x00000000;

    // ===== Tab hover =====
    public static final int TAB_HOVER_BG      = 0x14555560;

    // ===== Icon button (self-drawn POJO) =====
    public static final int ICON_BUTTON_BG    = 0x80000000;
    public static final int ICON_BUTTON_HOVER = 0xB0404040;
    public static final int ICON_COLOR        = 0xFFFFFFFF;

    // ===== Spacing =====
    public static final int GAP_SM     = 2;
    public static final int PADDING_SM = 2;
    public static final int PADDING_MD = 4;
    public static final int PADDING_LG = 8;
}
```

- [ ] **Step 2: 创建 KpThemeStylesheet.java 骨架**

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
 * <p>调用 {@link #register()} 在客户端 setup 阶段注册为 builtin stylesheet。
 */
public final class KpThemeStylesheet {

    private static final ResourceLocation THEME_RL =
        ResourceLocation.fromNamespaceAndPath("kinetic_planner", "theme_colors");

    private KpThemeStylesheet() {}

    /**
     * 生成含 token 插值的主题 LSS 样式表。
     *
     * <p>颜色值通过 {@code String.formatted("#%08X", KpTheme.XXX)} 插值。
     * Task 2 将填充完整的颜色规则。
     */
    public static Stylesheet create() {
        String lss = """
            // KpThemeStylesheet - 颜色规则将在 Task 2 填充
            """.stripIndent();
        return Stylesheet.parse(lss);
    }

    /**
     * 注册为 builtin stylesheet。
     *
     * <p>在客户端 setup 阶段调用一次。StylesheetManager 在资源包 reload 时
     * 会保留此注册，并通知 StyleEngine 重新匹配。
     */
    public static void register() {
        StylesheetManager.INSTANCE.registerBuiltinStylesheet(THEME_RL, create());
    }
}
```

- [ ] **Step 3: 在 KineticPlannerClient.onClientSetup 中注册**

读取 `KineticPlannerClient.java`，找到 `onClientSetup` 方法。在方法末尾（`event.enqueueWork` 之前或之后均可）添加注册调用：

```java
// 注册程序化主题样式表（含 token 插值的颜色规则）
KpThemeStylesheet.register();
```

同时添加 import：
```java
import net.jsmua.kinetic_planner.gui.theme.KpThemeStylesheet;
```

- [ ] **Step 4: 编译验证**

Run: `.\gradlew.bat compileClientJava compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/theme/KpTheme.java src/client/java/net/jsmua/kinetic_planner/gui/theme/KpThemeStylesheet.java src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java
git commit -m "feat(theme): 创建 KpTheme token 常量 + KpThemeStylesheet 骨架 + 注册到 onClientSetup"
```

---

### Task 2: 创建静态 LSS 文件 + 填充颜色规则 + 删除 kp.lss

**Files:**
- Create: `src/main/resources/assets/kinetic_planner/lss/ribbon.lss`
- Create: `src/main/resources/assets/kinetic_planner/lss/editor.lss`
- Create: `src/main/resources/assets/kinetic_planner/lss/config.lss`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/theme/KpThemeStylesheet.java` (填充 create() 颜色规则)
- Delete: `src/main/resources/assets/kinetic_planner/lss/kp.lss`

**Interfaces:**
- Consumes: `KpTheme`（Task 1）、`KpThemeStylesheet.register()`（Task 1）
- Produces: 三个静态 LSS 文件 + `KpThemeStylesheet.create()` 完整颜色规则

- [ ] **Step 1: 创建 ribbon.lss（Ribbon 结构规则）**

从 `kp.lss` 提取 Ribbon 相关的结构规则（flex/padding/gap/尺寸），不含颜色：

```lss
// KP Ribbon 结构规则（颜色由 KpThemeStylesheet 程序化生成）
// LSS 不支持 CSS 变量 / border-* 简写 / padding 简写; 颜色用 #AARRGGBB.

// ===== Ribbon 根容器 =====
.kp-ribbon-bar {
    padding-all: 2;
    gap-all: 2;
}

// ===== 工具组面板 =====
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

// ===== 工具按钮 =====
.kp-ribbon-tool {
    padding-horizontal: 4;
    padding-vertical: 2;
}
.kp-ribbon-tool-label {
    height: 9;
}

// ===== QAT =====
.kp-ribbon-qat {
    flex-direction: row;
    align-items: center;
    gap-all: 2;
    padding-horizontal: 2;
}

// ===== 分隔符 =====
.kp-ribbon-separator {
    width: 1;
    height: 16;
    margin-horizontal: 2;
}
```

- [ ] **Step 2: 创建 editor.lss（ToolPanel + layout helpers 结构规则）**

```lss
// KP Editor 结构规则（颜色由 KpThemeStylesheet 程序化生成）

// ===== 工具面板 =====
.kp-tool-panel {
    padding-all: 4;
    gap-all: 2;
}
.kp-tool-button {
    padding-horizontal: 4;
    padding-vertical: 2;
}

// ===== Layout helpers =====
.kp-fill-width {
    width: 100%;
    flex-grow: 1;
}
.kp-fill-height {
    height: 100%;
    flex-grow: 1;
}
.kp-transparent-bg {
    background: rect(#00000000);
}
```

- [ ] **Step 3: 创建 config.lss（配置面板结构规则）**

从 `KpStylesheet.create()` 提取结构规则（不含颜色）：

```lss
// KP 配置面板结构规则（颜色由 KpThemeStylesheet 程序化生成）

.kp-panel {
    padding-all: 8;
    gap-all: 4;
}
.kp-title-row {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding-bottom: 4;
    margin-bottom: 2;
}
.kp-config-row {
    padding-horizontal: 4;
    padding-vertical: 2;
}
.kp-stepper {
    align-items: center;
    justify-content: flex-end;
}
.kp-panel button {
    padding-horizontal: 0;
    padding-vertical: 0;
}
```

- [ ] **Step 4: 填充 KpThemeStylesheet.create() 完整颜色规则**

将 `KpThemeStylesheet.create()` 替换为包含所有颜色规则的完整版本。颜色规则来自 `kp.lss`（Ribbon/ToolPanel 颜色）和 `KpStylesheet.create()`（配置面板颜色），全部用 `KpTheme` 常量插值：

```java
    public static Stylesheet create() {
        String lss = """
            // ===== Ribbon 颜色规则 =====

            .kp-ribbon-bar {
                background: rect(#%08X);
            }
            .kp-ribbon-tab {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tab.__selected__ {
                base-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tab.__hovered__ {
                color: #%08X;
            }
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
            .kp-ribbon-tool-label {
                color: #%08X;
            }
            .kp-ribbon-group-label {
                color: #%08X;
            }
            .kp-ribbon-qat-empty {
                color: #%08X;
            }
            .kp-ribbon-separator {
                background: rect(#%08X);
            }

            // ===== ToolPanel 颜色规则 =====

            .kp-tool-panel {
                background: rect(#%08X);
            }
            .kp-tool-button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }

            // ===== 配置面板颜色规则 =====

            .kp-panel {
                background: sdf(#%08X, 4, 1, #%08X);
            }
            .kp-panel label {
                color: #%08X;
            }
            .kp-title-row {
                background: sdf(#%08X, 2, 1, #%08X);
            }
            .kp-config-row:hover {
                background: rect(#%08X);
            }
            .kp-panel button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-panel button:disabled {
                color: #%08X;
            }
            .kp-panel .__toggle_button__ {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
            }
            .kp-panel tab {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-panel tab.__selected__ {
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-tab-fused {
                color: #%08X;
            }
            .kp-panel tab.__selected__.kp-tab-fused {
                color: #%08X;
            }
            .kp-text-secondary {
                color: #%08X;
            }
            .kp-title {
                color: #%08X;
            }
            """.formatted(
                // ribbon bar
                KpTheme.PANEL_BG,
                // ribbon tab
                KpTheme.TRANSPARENT, KpTheme.TAB_HOVER_BG, KpTheme.ACCENT, KpTheme.TEXT_SECONDARY,
                // ribbon tab selected
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // ribbon tab hovered
                KpTheme.TEXT_PRIMARY,
                // ribbon tool
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.ACCENT_DIM, KpTheme.TEXT_PRIMARY,
                // ribbon tool on
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // ribbon tool label
                KpTheme.TEXT_PRIMARY,
                // ribbon group label
                KpTheme.TEXT_SECONDARY,
                // ribbon qat empty
                KpTheme.SEPARATOR,
                // ribbon separator
                KpTheme.SEPARATOR,
                // tool panel
                KpTheme.PANEL_BG,
                // tool button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.ACCENT_DIM, KpTheme.TEXT_PRIMARY,
                // config panel
                KpTheme.PANEL_BG_TRANS, KpTheme.PANEL_BORDER,
                // config panel label
                KpTheme.TEXT_PRIMARY,
                // config title row
                KpTheme.PANEL_BG_TRANS, KpTheme.PANEL_HIGHLIGHT,
                // config row hover
                KpTheme.ROW_HOVER,
                // config panel button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED, KpTheme.TEXT_PRIMARY,
                // config panel button disabled
                KpTheme.TEXT_SECONDARY,
                // config toggle button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED,
                // config tab
                KpTheme.TRANSPARENT, KpTheme.ROW_HOVER, KpTheme.ACCENT, KpTheme.TEXT_SECONDARY,
                // config tab selected
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // tab fused
                KpTheme.DANGER,
                // tab selected fused
                KpTheme.DANGER,
                // text secondary
                KpTheme.TEXT_SECONDARY,
                // title
                KpTheme.TEXT_PRIMARY
            );
        return Stylesheet.parse(lss);
    }
```

- [ ] **Step 5: 删除 kp.lss**

删除文件 `src/main/resources/assets/kinetic_planner/lss/kp.lss`。其内容已被 `ribbon.lss` + `editor.lss`（结构）和 `KpThemeStylesheet`（颜色）完全取代。

- [ ] **Step 6: 编译验证**

Run: `.\gradlew.bat compileClientJava compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/assets/kinetic_planner/lss/ribbon.lss src/main/resources/assets/kinetic_planner/lss/editor.lss src/main/resources/assets/kinetic_planner/lss/config.lss src/client/java/net/jsmua/kinetic_planner/gui/theme/KpThemeStylesheet.java
git rm src/main/resources/assets/kinetic_planner/lss/kp.lss
git commit -m "refactor(lss): 拆分 kp.lss 为 ribbon/editor/config.lss + 颜色规则移入 KpThemeStylesheet"
```

---

### Task 3: 迁移 Ribbon 组件内联样式

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/QatBar.java` (L29-32)
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java` (L26-29, L45-48)
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonTabViewAdapter.java` (L93-97)
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java` (L95-98)
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButton.java` (L100-132)

**Interfaces:**
- Consumes: `ribbon.lss`（Task 2，含 `align-items` / `padding` 规则）
- Produces: 无（内部迁移）

**迁移规则：** 移除 `alignItems(...)` / `paddingAll(...)` / `paddingHorizontal(...)` / `paddingVertical(...)` 调用（已由 `ribbon.lss` 覆盖）。保留 `flexDirection(...)` / `width(...)` / `height(...)` / `widthPercent(...)` / `flexGrow(...)` / `flexShrink(...)`。

- [ ] **Step 1: 迁移 QatBar.java**

将 L29-32 的 layout lambda 从：
```java
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
        });
```
改为：
```java
        layout(layout -> layout.flexDirection(FlexDirection.ROW));
```

如果 `AlignItems` import 因此变为未使用，移除该 import。

- [ ] **Step 2: 迁移 GroupPanel.java**

将 L26-29（kp-ribbon-group）从：
```java
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
        });
```
改为：
```java
        layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
```

将 L45-48（kp-ribbon-tool-row）从：
```java
        toolRow.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
        });
```
改为：
```java
        toolRow.layout(layout -> layout.flexDirection(FlexDirection.ROW));
```

如果 `AlignItems` import 变为未使用，移除。

- [ ] **Step 3: 迁移 RibbonTabViewAdapter.java**

将 L93-97（header layout）从：
```java
        header.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
        });
```
改为：
```java
        header.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.widthPercent(100);
        });
```

注意：`alignItems(CENTER)` 已由 `.kp-ribbon-bar` 的子元素样式覆盖。但 header 元素没有 `kp-` class，需要确认 `ribbon.lss` 中是否有对应规则。如果没有，给 header 添加一个 class 或在 `ribbon.lss` 中用后代选择器覆盖。

检查：header 是 TabView 的 `tabHeaderContainer`。由于它没有 kp- class，`alignItems` 需要保留或通过后代选择器处理。**决定：保留此处的 `alignItems(CENTER)`，因为它作用于 TabView 内部容器而非 kp- 组件。**

实际上修正 Step 3：**不迁移 RibbonTabViewAdapter.java**，因为 header 是 TabView 内部容器，没有 kp- class，LSS 无法匹配。保留原样。

- [ ] **Step 4: 迁移 ToolWidgetFactory.java**

将 L95-98（SMALL 按钮）从：
```java
            button.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(4);
            });
```
改为：
```java
            button.layout(layout -> layout.height(18));
```

`paddingHorizontal(4)` 已由 `.kp-ribbon-tool { padding-horizontal: 4; }` 覆盖（button 有 `kp-ribbon-tool` class）。

- [ ] **Step 5: 迁移 RibbonToggleButton.java**

将 L100-106（LARGE toggle）从：
```java
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.CENTER);
                layout.width(40);
                layout.height(38);
                layout.paddingAll(1);
            });
```
改为：
```java
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.COLUMN);
                layout.width(40);
                layout.height(38);
            });
```

将 L122-127（SMALL toggle）从：
```java
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.height(22);
                layout.paddingAll(1);
            });
```
改为：
```java
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.height(22);
            });
```

将 L128-132（SMALL button）从：
```java
            button.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(4);
                layout.paddingVertical(1);
            });
```
改为：
```java
            button.layout(layout -> layout.height(18));
```

注意：toggle 元素没有 `kp-ribbon-tool` class（只有内部 button 有）。`alignItems(CENTER)` 和 `paddingAll(1)` 作用于 toggle 容器。需要在 `ribbon.lss` 中添加 toggle 的样式规则。

在 `ribbon.lss` 末尾添加：
```lss
// ===== Toggle 容器（RibbonToggleButton 内部 Toggle） =====
.kp-ribbon-tool-toggle {
    align-items: center;
    padding-all: 1;
}
```

然后在 `RibbonToggleButton.java` 构造函数中给 toggle 添加 class：
```java
toggle.addClass("kp-ribbon-tool-toggle");
```

注意：toggle 是 `Toggle` 类型，`addClass` 方法继承自 `UIElement`，确认 `Toggle extends UIElement`（API 验证文档 §5.5 确认）。在构造函数中 `toggle` 变量可用时添加。

- [ ] **Step 6: 编译验证**

Run: `.\gradlew.bat compileClientJava compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/QatBar.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButton.java src/main/resources/assets/kinetic_planner/lss/ribbon.lss
git commit -m "refactor(ribbon): 迁移内联 alignItems/padding 到 LSS"
```

---

### Task 4: 迁移 ToolPanelView 内联样式

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java` (L21-25)

**Interfaces:**
- Consumes: `editor.lss`（Task 2，含 `.kp-tool-panel { padding-all: 4; gap-all: 2; }`）
- Produces: 无

- [ ] **Step 1: 迁移 ToolPanelView layout**

将 L21-25 从：
```java
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(4);
            layout.gapAll(2);
        });
```
改为：
```java
        layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
```

`paddingAll(4)` 和 `gapAll(2)` 已由 `editor.lss` 中 `.kp-tool-panel { padding-all: 4; gap-all: 2; }` 覆盖。

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat compileClientJava compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java
git commit -m "refactor(editor): 迁移 ToolPanelView 内联 padding/gap 到 LSS"
```

---

### Task 5: 迁移 Config 组件内联样式 + KpConfigUIFactory 改造

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/config/KpConfigUIFactory.java` (L42-47, L109-121)
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/widgets/ConfigStepperRow.java` (L106-148)
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/widgets/ConfigToggleRow.java` (L35-52)
- Modify: `src/main/resources/assets/kinetic_planner/lss/config.lss` (添加 widget 结构规则)

**Interfaces:**
- Consumes: `config.lss`（Task 2）、`KpThemeStylesheet`（Task 2，颜色规则已就位）
- Produces: 无

- [ ] **Step 1: 在 config.lss 中添加 widget 结构规则**

在 `config.lss` 末尾追加：

```lss

// ===== ConfigToggleRow =====
.kp-config-row {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    width: 100%;
}
.kp-toggle {
    padding-all: 1;
}

// ===== ConfigStepperRow =====
.kp-stepper {
    flex-direction: row;
    align-items: center;
    justify-content: flex-end;
    gap-all: 2;
}
.kp-config-row button {
    padding-all: 0;
}
```

注意：`.kp-config-row` 已在 Task 2 的 `config.lss` 中定义了 `padding-horizontal` / `padding-vertical`。此处追加 `flex-direction` / `align-items` / `justify-content` / `width`。需要合并到同一规则块中，避免重复选择器。

将 Task 2 创建的 `config.lss` 中的 `.kp-config-row` 规则更新为：
```lss
.kp-config-row {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding-horizontal: 4;
    padding-vertical: 2;
}
```

同样更新 `.kp-stepper`：
```lss
.kp-stepper {
    flex-direction: row;
    align-items: center;
    justify-content: flex-end;
    gap-all: 2;
}
```

并添加 `.kp-toggle` 和 `.kp-config-row button` 规则。

- [ ] **Step 2: 迁移 KpConfigUIFactory.java**

修改 `create()` 方法（L42-47），不再传入 `KpStylesheet.create()`：
```java
    public static ModularUI create() {
        UIElement root = buildPanelRoot();
        var ui = UI.of(root);
        return ModularUI.of(ui);
    }
```

移除 `KpStylesheet` import 和 `Stylesheet` import（如果不再使用）。

修改 `buildPanelRoot()`（L52-62），移除 `paddingAll(8)` 和 `gapAll(4)`（已由 `config.lss` 的 `.kp-panel` 覆盖），保留结构布局：
```java
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(PANEL_RIGHT_MARGIN);
            layout.top(PANEL_TOP);
            layout.width(PANEL_WIDTH);
            layout.heightAuto();
            layout.display(TaffyDisplay.FLEX);
            layout.flexDirection(FlexDirection.COLUMN);
        });
```

修改 `buildTitleBar()`（L70-100），移除 titleBar 的 `alignItems` / `justifyContent` / `widthPercent`（已由 `config.lss` 的 `.kp-title-row` 覆盖），移除 title 的 `flex(1)` / `height(9)`（保留 height 是尺寸约束，但 flex(1) 是布局契约应保留），移除 closeButton 的 `paddingAll(0)`（已由 `.kp-panel button { padding-horizontal: 0; padding-vertical: 0; }` 覆盖）：
```java
        var titleBar = new UIElement();
        titleBar.addClass("kp-title-row");
        // titleBar 布局由 config.lss .kp-title-row 覆盖

        var title = new Label();
        title.setValue(Component.literal("Kinetic Planner"));
        title.addClass("kp-title");
        title.layout(layout -> {
            layout.flex(1);
            layout.height(9);
        });

        var closeButton = new Button();
        closeButton.setText("×");
        closeButton.setOnClick(event -> KpClientState.setConfigPanelVisible(false));
        closeButton.layout(layout -> {
            layout.width(18);
            layout.height(14);
        });
```

修改 `buildHintLabel()`（L199-208），移除 `paddingTop(4)`（外观属性应迁移），保留 `widthPercent(100)`：
```java
        hint.layout(layout -> layout.widthPercent(100));
```
并在 `config.lss` 中添加 `.kp-text-secondary` 的 padding-top 规则。实际上 `.kp-text-secondary` 是文本 class 不是容器 class，应给 hint 元素同时添加 `kp-config-row` class 或新建一个 class。**决定：保留 hint 的 `paddingTop(4)`，因为它是单处使用的微调，不值得新建 LSS class。**

修改 `buildProviderTabView()`（L109-121），移除 `style(backgroundTexture(...))` 内联。将 tabHeaderContainer 和 tabContentContainer 的 style 调用改为引用 `KpTheme.PANEL_BG_TRANS`（因 TabView 内部容器无法添加 class，保留内联但引用 token）：

```java
        tabView.tabHeaderContainer(container -> container.style(style ->
            style.backgroundTexture(new ColorRectTexture(KpTheme.PANEL_BG_TRANS))
        ).layout(layout -> {
            layout.paddingHorizontal(3);
            layout.paddingVertical(2);
        }));

        tabView.tabContentContainer(container -> container.style(style ->
            style.backgroundTexture(new ColorRectTexture(KpTheme.PANEL_BG_TRANS))
        ).layout(layout -> {
            layout.paddingAll(4);
            layout.flexGrow(1);
        }));
```

添加 import：`import net.jsmua.kinetic_planner.gui.theme.KpTheme;`
移除 import：`import net.jsmua.kinetic_planner.gui.config.KpStylesheet;`（如果不再引用）
保留 import：`import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;`（仍在使用）

- [ ] **Step 3: 迁移 ConfigToggleRow.java**

将 L35-39（row layout）从：
```java
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.SPACE_BETWEEN);
            layout.widthPercent(100);
        });
```
改为（全部由 `config.lss` `.kp-config-row` 覆盖，移除整个 layout 调用）：
```java
        // row 布局由 config.lss .kp-config-row 覆盖
```

将 L49-52（toggle layout）从：
```java
        toggle.layout(layout -> {
            layout.width(TOGGLE_WIDTH);
            layout.height(14);
            layout.paddingAll(1);
        });
```
改为：
```java
        toggle.layout(layout -> {
            layout.width(TOGGLE_WIDTH);
            layout.height(14);
        });
```

将 L66-68（label layout）保留（width/height 是尺寸约束，保留）。

- [ ] **Step 4: 迁移 ConfigStepperRow.java**

将 L106-110（row layout）移除（同 ConfigToggleRow，由 `.kp-config-row` 覆盖）：
```java
        // row 布局由 config.lss .kp-config-row 覆盖
```

将 L119-124（stepper layout）从：
```java
        stepper.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.FLEX_END);
            layout.width(STEPPER_WIDTH);
            layout.gapAll(2);
        });
```
改为：
```java
        stepper.layout(layout -> layout.width(STEPPER_WIDTH));
```

将 L145-148（button layout）从：
```java
        button.layout(layout -> {
            layout.width(14);
            layout.height(14);
            layout.paddingAll(0);
        });
```
改为：
```java
        button.layout(layout -> {
            layout.width(14);
            layout.height(14);
        });
```

保留 L132-134（valueLabel layout width/height）和 L136-137（textStyle）。

- [ ] **Step 5: 编译验证**

Run: `.\gradlew.bat compileClientJava compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/config/KpConfigUIFactory.java src/client/java/net/jsmua/kinetic_planner/gui/widgets/ConfigStepperRow.java src/client/java/net/jsmua/kinetic_planner/gui/widgets/ConfigToggleRow.java src/main/resources/assets/kinetic_planner/lss/config.lss
git commit -m "refactor(config): 迁移 Config 组件内联样式到 LSS + KpConfigUIFactory 依赖全局样式表"
```

---

### Task 6: 迁移 KpIconButton 颜色常量 + 删除 KpStylesheet.java + 文档更新

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/widgets/KpIconButton.java` (L19-22)
- Delete: `src/client/java/net/jsmua/kinetic_planner/gui/config/KpStylesheet.java`
- Modify: `.agents/rules/package-structure.md` (更新包结构树 + 代码统计)

**Interfaces:**
- Consumes: `KpTheme`（Task 1）
- Produces: 无

- [ ] **Step 1: 迁移 KpIconButton 颜色常量**

将 L19-22 的硬编码色值常量改为引用 `KpTheme`：

```java
    protected static final int SIZE = 16;
    protected static final int BG_COLOR = KpTheme.ICON_BUTTON_BG;
    protected static final int BG_HOVER_COLOR = KpTheme.ICON_BUTTON_HOVER;
    protected static final int ICON_COLOR = KpTheme.ICON_COLOR;
    protected static final int ACCENT_COLOR = KpTheme.ACCENT;
```

添加 import：`import net.jsmua.kinetic_planner.gui.theme.KpTheme;`

注意：这些常量保持 `protected static final int` 类型不变（`KpTheme` 常量也是 `static final int`，编译期常量，可赋值给 `static final int` 字段）。子类 `KpGearButton` 和 `KpEditButton` 通过继承访问这些常量，不需要修改。

- [ ] **Step 2: 确认 KpStylesheet 无引用后删除**

搜索整个 `src/` 目录确认无 `KpStylesheet` 引用（Task 5 已移除 `KpConfigUIFactory` 中的引用）。

如果确认无引用，删除 `src/client/java/net/jsmua/kinetic_planner/gui/config/KpStylesheet.java`。

- [ ] **Step 3: 全局裸色值搜索**

搜索 `src/client/java/` 中是否还有 `0xFF7C57D4` / `0xff2c2c34` / `0x803d3d42` 等裸色值（排除 `KpTheme.java` 本身）。如果有，改为引用 `KpTheme` 常量。

搜索命令：`grep -rn "0x[Ff][Ff]7[Cc]57[Dd]4\|0x[Ff][Ff]2[Cc]2[Cc]34\|0x803[Dd]3[Dd]42" src/client/java/ --include="*.java"`

预期：只有 `KpTheme.java` 匹配。

- [ ] **Step 4: 编译验证**

Run: `.\gradlew.bat compileClientJava compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 更新 package-structure.md**

在 `.agents/rules/package-structure.md` 的包结构树中：
- 在 `gui/` 下添加 `theme/` 子目录：
  ```
  │   ├── theme/                        主题 token + LSS 工厂（client）
  │   │   ├── KpTheme.java              颜色/间距 token 常量
  │   │   └── KpThemeStylesheet.java    程序化 LSS 生成 + 注册
  ```
- 在 `gui/config/` 下删除 `KpStylesheet.java` 条目
- 更新代码统计表中 client Java 文件数（+2 新增 -1 删除 = +1）

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/widgets/KpIconButton.java .agents/rules/package-structure.md
git rm src/client/java/net/jsmua/kinetic_planner/gui/config/KpStylesheet.java
git commit -m "refactor(theme): KpIconButton 颜色常量引用 KpTheme + 删除 KpStylesheet.java + 更新包结构"
```
