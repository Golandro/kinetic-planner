# Kinetic Planner UI 组件样式-行为分离设计

> **状态**：设计稿（等待实现计划）  
> **基于**：2026-08-01 讨论结论  
> **目标**：将 LDLib2 UI 组件按"一个组件一个包"重组，行为类与样式 Data Provider 分离，样式通过资源包系统加载，默认版本由 Data Generator 生成。

---

## 1. 背景与问题

当前 `src/client/java/.../gui/` 下的 UI 组件存在以下耦合：

1. **样式硬编码在 Java 中**：大量 `layout(...)`、`backgroundTexture(...)`、`paddingAll(...)` 与组件行为写在一起。
2. **静态 LSS 文件难以维护**：`kp.lss` 集中存放所有样式，新增组件时需要跨文件找位置。
3. **组件间样式依赖容器背景**：例如 `ToolPanelView` 拖到 `centerWindow` 后丢失背景，因为样式未自包含。
4. **无 Data Generator 基础设施**：`runData` 已配置，但项目没有任何数据提供者，资源生成流程未启用。

本设计借鉴 Web 组件化思路（每个组件一个目录 + 独立样式文件），同时保留 LDLib2 的命令式对象树本质。

---

## 2. 设计目标

1. **一个组件一个包**：每个自定义 `View` / `UIElement` 组件拥有独立包，包内含行为主类 + 样式 Data Provider。
2. **样式-行为分离**：Java 类只负责结构、事件、状态；颜色、间距、背景等外观由 LSS 定义。
3. **资源包驱动样式**：样式文件通过原版资源系统加载，支持用户/资源包覆盖。
4. **默认样式由 Data Generator 生成**：开发期运行 `gradlew runData` 生成默认 LSS，避免手写重复文件。
5. **Theme token 集中管理**：颜色/间距等设计 token 以 Java 常量形式存放，Provider 将其转换为 LSS token class。

---

## 3. 架构概述

```
┌─────────────────────────────────────────────────────────────┐
│  main sourceSet                                              │
│  ├── theme/KpTheme.java          ← 颜色/间距 token 常量      │
│  └── data/KpDataEventHandler.java ← 注册所有 StyleProvider   │
├─────────────────────────────────────────────────────────────┤
│  client sourceSet                                            │
│  gui/                                                        │
│  ├── editor/                                                 │
│  │   ├── toolpanel/                                          │
│  │   │   ├── ToolPanelView.java      ← 行为主类             │
│  │   │   └── ToolPanelStyleProvider.java ← Data Generator   │
│  │   ├── mapviewport/                                        │
│  │   │   ├── MapPlaceholderView.java                        │
│  │   │   └── MapPlaceholderStyleProvider.java               │
│  │   └── ...                                                │
│  ├── ribbon/                                                 │
│  │   ├── bar/                                                │
│  │   │   ├── RibbonBar.java                                 │
│  │   │   └── RibbonBarStyleProvider.java                    │
│  │   ├── qat/                                                │
│  │   ├── group/                                              │
│  │   ├── tool/                                               │
│  │   └── toggle/                                             │
│  └── components/widgets/                                     │
│      ├── iconbutton/                                         │
│      │   ├── KpIconButton.java                              │
│      │   └── KpIconButtonStyleProvider.java                 │
│      └── ...                                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
              src/generated/resources/assets/kinetic_planner/lss/
              ├── theme.lss          ← KpThemeStyleProvider 生成
              ├── editor/
              │   ├── toolpanel.lss
              │   └── mapviewport.lss
              ├── ribbon/
              │   ├── bar.lss
              │   ├── qat.lss
              │   └── ...
              └── components/
                  └── widgets/
                      └── iconbutton.lss
```

LDLib2 `StylesheetManager` 会自动扫描所有资源包中的 `assets/<namespace>/lss/*.lss`，生成后的文件与静态资源等价。

---

## 4. Theme Token 系统

### 4.1 KpTheme.java

位于 `src/main/java/net/jsmua/kinetic_planner/theme/KpTheme.java`（common sourceSet，不依赖 client）。

```java
public final class KpTheme {
    private KpTheme() {}

    // Background
    public static final String PANEL_BG      = "#ff2c2c34";
    public static final String TOOLTIP_BG    = "#e61e1f22";

    // Accent
    public static final String ACCENT        = "#FF7C57D4";
    public static final String ACCENT_DIM    = "#807c57d4";

    // Text
    public static final String TEXT_PRIMARY  = "#fff3efe0";
    public static final String TEXT_MUTED    = "#ff9ca3af";

    // Button
    public static final String BUTTON_BG     = "#803d3d42";
    public static final String BUTTON_HOVER  = "#b04a4a52";

    // Spacing
    public static final int GAP_SM = 2;
    public static final int PADDING_SM = 2;
    public static final int PADDING_MD = 4;
}
```

### 4.2 Theme LSS 输出

`KpThemeStyleProvider` 生成 `assets/kinetic_planner/lss/theme.lss`：

```lss
.kp-color-bg-panel    { background: rect(#ff2c2c34); }
.kp-color-bg-tooltip  { background: rect(#e61e1f22); }
.kp-color-accent      { background: rect(#FF7C57D4); }
.kp-color-text        { color: #fff3efe0; }
.kp-color-text-muted  { color: #ff9ca3af; }
```

组件样式通过**组合这些标识 class** 来应用颜色，而不是在组件 LSS 中硬编码色值。

---

## 5. 组件包规范

### 5.1 包内文件

每个组件包必须且只包含两类文件：

1. **行为主类**：继承 `View` / `UIElement`，负责结构、事件、状态。
2. **样式 Provider**：实现 `DataProvider`，生成该组件的 `.lss` 文件。

```
toolpanel/
├── ToolPanelView.java              ← 行为主类
└── ToolPanelStyleProvider.java     ← Data Generator
```

### 5.2 行为主类规范

- **不加硬编码样式**：不在 Java 中调用 `layout(...)` 设置 padding/gap/background，除非与父容器布局契约强相关（如 `widthPercent(100)`）。
- **必须声明组件 class**：构造函数中调用 `addClass("kp-<component>")`。
- **事件行为可注入**：重要行为通过构造函数参数或 setter 注入，减少对其他单例的依赖。

示例：

```java
public class ToolPanelView extends View {
    public ToolPanelView(Consumer<EditToolState.Tool> onToolSelected) {
        super();
        addClass("kp-tool-panel");
        for (var tool : EditToolState.Tool.values()) {
            addChild(createButton(tool, onToolSelected));
        }
    }

    private Button createButton(EditToolState.Tool tool, Consumer<EditToolState.Tool> callback) {
        Button btn = new Button();
        btn.addClass("kp-tool-button");
        btn.setText(tool.getDisplayName() + " (" + tool.getKeyLabel() + ")");
        btn.setOnClick(e -> callback.accept(tool));
        return btn;
    }
}
```

### 5.3 样式 Provider 规范

- 实现 `net.minecraft.data.DataProvider`。
- 输出路径：`assets/kinetic_planner/lss/<category>/<component>.lss`。
- 引用 `KpTheme` 常量生成 token class，不在 Provider 中硬编码色值。
- Provider 中只写该组件的样式规则。

示例：

```java
public class ToolPanelStyleProvider implements DataProvider {
    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        String lss = """
            .kp-tool-panel {
                flex-direction: column;
                padding-all: %d;
                gap-all: %d;
                background: rect(%s);
            }

            .kp-tool-button {
                base-background: rect(%s);
                hover-background: rect(%s);
                pressed-background: rect(%s);
                color: %s;
                padding-horizontal: 4;
                padding-vertical: 2;
            }
            """.formatted(
                KpTheme.PADDING_MD,
                KpTheme.GAP_SM,
                KpTheme.PANEL_BG,
                KpTheme.BUTTON_BG,
                KpTheme.BUTTON_HOVER,
                KpTheme.ACCENT_DIM,
                KpTheme.TEXT_PRIMARY
            );
        return save(output, lss, "assets/kinetic_planner/lss/editor/toolpanel.lss");
    }
}
```

---

## 6. Data Generator 注册

### 6.1 事件处理器

`src/main/java/net/jsmua/kinetic_planner/data/KpDataEventHandler.java`：

```java
@EventBusSubscriber(modid = KineticPlannerMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class KpDataEventHandler {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        // Theme token 必须先于组件样式生成
        generator.addProvider(true, new KpThemeStyleProvider(output));

        // Editor 组件
        generator.addProvider(true, new ToolPanelStyleProvider(output));
        generator.addProvider(true, new MapPlaceholderStyleProvider(output));

        // Ribbon 组件
        generator.addProvider(true, new RibbonBarStyleProvider(output));
        generator.addProvider(true, new QatBarStyleProvider(output));
        generator.addProvider(true, new GroupPanelStyleProvider(output));
        generator.addProvider(true, new RibbonToggleButtonStyleProvider(output));

        // Widgets
        generator.addProvider(true, new KpIconButtonStyleProvider(output));
    }
}
```

### 6.2 生成命令

```bash
gradlew runData
```

输出到 `src/generated/resources/`，该目录已被 `build.gradle` 加入 `sourceSets.main.resources`。

---

## 7. 组件迁移映射

| 当前文件 | 新包 | 新行为类 | 新 Provider |
|---|---|---|---|
| `gui/ToolPanelView.java` | `gui/editor/toolpanel/` | `ToolPanelView.java` | `ToolPanelStyleProvider.java` |
| `gui/MapPlaceholderView.java` | `gui/editor/mapviewport/` | `MapPlaceholderView.java` | `MapPlaceholderStyleProvider.java` |
| `gui/ribbon/RibbonBar.java` | `gui/ribbon/bar/` | `RibbonBar.java` | `RibbonBarStyleProvider.java` |
| `gui/ribbon/QatBar.java` | `gui/ribbon/qat/` | `QatBar.java` | `QatBarStyleProvider.java` |
| `gui/ribbon/internal/GroupPanel.java` | `gui/ribbon/group/` | `GroupPanel.java` | `GroupPanelStyleProvider.java` |
| `gui/ribbon/internal/toggle/RibbonToggleButton.java` | `gui/ribbon/toggle/` | `RibbonToggleButton.java` | `RibbonToggleButtonStyleProvider.java` |
| `gui/widgets/KpIconButton.java` | `gui/components/widgets/iconbutton/` | `KpIconButton.java` | `KpIconButtonStyleProvider.java` |
| `gui/config/KpStylesheet.java` | `gui/config/` 或删除 | 保留内联样式作为 Cloth Config 专用 | 不迁移 |
| `gui/ribbon/internal/RibbonTabViewAdapter.java` | `gui/ribbon/bar/` 或保留 | 保留为内部辅助类 | 不生成 LSS |

---

## 8. 兼容性说明

- **允许重新设计 API**：本次重构可调整构造函数签名、类名、包路径。
- **静态 `kp.lss` 删除**：所有样式由 Data Generator 生成，原 `src/main/resources/assets/kinetic_planner/lss/kp.lss` 删除。
- **Lang 文件保留**：`lang/en_us.json`、`lang/zh_cn.json` 继续放在 `src/main/resources/`。
- **非 UIElement 组件**：`KpGearButton`、`KpEditButton` 是自绘 POJO，不继承 `UIElement`，样式由代码绘制决定， Provider 中可生成辅助 LSS 或保持代码内样式。

---

## 9. 测试策略

1. **编译测试**：`gradlew compileClientJava compileJava` 必须通过。
2. **Data Generator 测试**：运行 `gradlew runData`，确认 `src/generated/resources/assets/kinetic_planner/lss/` 下生成预期文件。
3. **运行时验收**：`gradlew runClient`，检查：
   - 编辑器/Ribbon/配置面板样式与重构前一致
   - ToolPanelView 拖入 centerWindow 后仍保持自身背景与按钮样式
   - 无 LSS 解析错误日志

---

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| LSS 文件生成顺序影响覆盖 | LDLib2 自动合并同名 class，后加载的覆盖先加载的；必要时显式控制文件命名避免冲突 |
| 跨组件共享样式重复 | 共享样式抽到 `theme.lss` 的通用 class（如 `.kp-base-button`） |
| Data Generator 增加构建步骤 | 首次生成后提交到 `src/generated/resources/`，CI 不需要每次运行 `runData` |
| 重构范围大，回归风险高 | 按组件分任务，每个任务独立验证；保留 `runClient` 截图对比 |

---

## 11. 待实现计划产出

下一步：基于本设计稿，使用 `writing-plans` skill 生成按组件分任务的实现计划。
