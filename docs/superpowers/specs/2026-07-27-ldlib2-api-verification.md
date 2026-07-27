# LDLib2 API Verification Findings

> 日期：2026-07-27
> 状态：已验证（5 个并行 search subagent 完成源码核对）
> 范围：spec §2.2「未完全验证项」的源码验证结果，用于指导实现。
> 源码位置：`G:\Mods\LDLib2\src\main\java`

## 1. TabView / Tab API（spec §2.2 + §4.3）

### 1.1 类位置
- `com.lowdragmc.lowdraglib2.gui.ui.elements.TabView` extends `UIElement`
- `com.lowdragmc.lowdraglib2.gui.ui.elements.Tab` extends `UIElement`
- 均标注 `@LDLRegister(name = "tab-view" / "tab", group = "container" / "utils", registry = "ldlib2:ui_element")`

### 1.2 关键 API
| 用途 | 方法签名 |
|---|---|
| 创建容器 | `new TabView()` 无参构造，默认 COLUMN_REVERSE 布局 |
| 添加 Tab + content | `TabView.addTab(Tab tab, UIElement content) : TabView` |
| 添加 Tab 到指定位置 | `TabView.addTab(Tab tab, UIElement content, int index) : TabView`（`index < 0` 视为追加） |
| 选中切换 | `TabView.selectTab(Tab tab) : TabView` |
| 清空所有 Tab | `TabView.clear() : TabView`（注意：不重置 `selectedTab`） |
| **Tab 选中回调** | `TabView.setOnTabSelected(Consumer<Tab> onTabSelected) : TabView` ← KP 主要用这个 |
| 单 Tab 选中回调 | `Tab.setOnTabSelected(Runnable)` / `setOnTabUnselected(Runnable)` |
| 设置 Tab 文本 | `Tab.setText(String)` / `Tab.setText(Component)` / `Tab.setDynamicText(Supplier<Component>)` |
| 取选中状态 | `TabView.getSelectedTab() : Tab`（Lombok `@Getter`）；**Tab 无 `isSelected()` 公开 getter** |

### 1.3 视觉样式钩子（spec §3.2 KP 紫 accent 下划线）
- `Tab` 内部有 `TabStyle` 类（extends `Style`），通过 `tab.getTabStyle()` 或 `tab.tabStyle(Consumer<TabStyle>)` 访问
- `TabStyle` 三个纹理属性：
  - `baseTexture(IGuiTexture)` — 默认态背景，默认 `Sprites.TAB_DARK`
  - `hoverTexture(IGuiTexture)` — hover 态背景，默认 `Sprites.TAB_WHITE`
  - `selectedTexture(IGuiTexture)` — 选中态背景，默认 `Sprites.TAB`
- **KP 实现选中下划线方案**：调用 `tab.getTabStyle().selectedTexture(new ColorBorderTexture(-1, 0xFF7C57D4))` 设置选中态为 1px 内描紫色边框（负 border = inset，覆盖底部模拟下划线）

### 1.4 重要行为注意
1. **首次 `addTab` 自动选中**：第一个 addTab 调用会触发 `selectTab`，进而触发 `onTabSelected` 回调一次。KP 必须在回调中防御首次触发，或在 addTab 之前完成 content 子树的初始构建。
2. **内部点击处理已自动接管**：`addTab` 内部注册了 `MOUSE_DOWN` 监听器，点击 Tab 自动调用 `selectTab`，KP 无需自己加点击监听。
3. **LDLib2 有 typo**：`Tab.java:195` 使用 `removeClass("_tab_selected_")` 但 `addClass("__tab_selected__")`，所以 `__tab_selected__` 类名在反选时不会正确移除。**用 `__selected__` 类做选中态样式**，那个类是正确添加/移除的。
4. **Tab content 默认不可见**：`addTab` 调用后，content 通过 `setDisplay(false)` 隐藏，选中时 `setDisplay(true)` 显示。所以 KP 在 content panel 内构建的子元素在选中前不会渲染。

## 2. Selector / NumberConfigurator（spec §4.3）

### 2.1 结论：无原生 `−/+` 按钮 stepper
LDLib2 中**没有** `Stepper` / `NumberInput` / `Slider` 等数值步进控件。`Selector<T>` 是泛型下拉，`NumberConfigurator` 是包装 `TextField` 的配置行（视觉是文本框，不是 `−/+` 按钮）。

### 2.2 实施决策
按 spec §4.3 + §9.1，**自行组合** `Button[−] + Label + Button[+]`。具体：
- `Button` 来自 `com.lowdragmc.lowdraglib2.gui.ui.elements.Button`
- `Label` 来自 `com.lowdragmc.lowdraglib2.gui.ui.elements.Label`，用 `setText(String)` 显示数值
- `Button.setOnClick(UIEventListener)` 中调用 `KpConfigUIFactory.computeSteppedValue(...)` 更新值，再 `label.setText(format(value))`，最后调用 `ProviderConfigControl.setParam(...)`

### 2.3 备选（不推荐）
若 KP 想用 LDLib2 原生数值输入：
- `TextField.setNumbersOnlyFloat(min, max)` + `setWheelDur(step)` 提供滚轮 + 拖拽步进，但视觉是文本框
- `NumberConfigurator.setRange(min, max).setWheel(step).setType(INTEGER/FLOAT)` 提供 label+text+copy/paste 的配置行
- 均不符合 spec §4.3 的 `−/+` 按钮组合要求

## 3. LSS CSS 特性支持（spec §3.2 + §8.4）

### 3.1 支持矩阵
| 特性 | 状态 | 备注 |
|---|---|---|
| CSS 变量 `--x` + `var(--x)` | **❌ 不支持** | 无任何解析逻辑；未知属性被静默丢弃 |
| `:hover` 伪类 | **✅ 支持** | 框架自动添加/移除 `__hovered__` 类，作用于元素及其所有祖先（CSS hover 传播语义） |
| `:focus` 伪类 | **✅ 支持** | 框架自动管理 `__focused__` 类 |
| `:disabled` 伪类 | **✅ 支持** | 框架自动管理 `__disabled__` 类 |
| 元素/class/id/通配选择器 | **✅ 支持** | `button` / `.panel` / `#save` / `*` |
| 后代选择器（空格） | **✅ 支持** | `div span` |
| 子选择器（`>`） | **✅ 支持** | `div > span` |
| `:not(...)` | **✅ 支持** | `:not(.x)` |
| `:host` / `:internal` 作用域 | **✅ 支持** | 子树作用域 |
| 属性选择器 `[attr=val]` | **❌ 不支持** | 不在词法中 |
| 伪元素 `::before` / `::after` | **❌ 不支持** | |
| 兄弟选择器 `+` / `~` | **❌ 不支持** | |
| `:root` 选择器 | **❌ 不真正支持** | 仅巧合匹配 `__root__` 类；不支持 CSS 变量 |

### 3.2 属性名映射（spec §3.2 修正）
| CSS 名 | LSS 是否支持 | LSS 正确写法 |
|---|---|---|
| `border` / `border-bottom` / `border-color` | **❌** | 用 `background: sdf(#color, radius, borderWidth, #borderColor)` |
| `background-color` | **❌** | `background: rect(#color)` 或 `background: #AARRGGBB`（简写） |
| `background` | ✅ | `background: sdf(...)` / `rect(...)` / `sprite(...)` / `group(...)` |
| `color` | ✅ | `color: #AARRGGBB;` （乘性 tint，作用于 background+overlay） |
| `opacity` | ✅ | `opacity: 0.5;` |
| `padding` 简写 | **❌** | 用 `padding-all: 4;` / `padding-horizontal: 8;` / `padding-vertical: 4;` / `padding-left/right/top/bottom: 4;` |
| `margin` 简写 | **❌** | 用 `margin-all: 4;` 等 |
| `gap` 简写 | **❌** | 用 `gap-all: 4;` / `gap-row: 4;` / `gap-column: 4;` |
| `width` / `height` | ✅ | `width: 200;` / `height: auto;` / `width: 100%;` |
| `flex-direction` / `flex-wrap` / `flex-grow` | ✅ | `flex-direction: row;` / `flex-grow: 1;` |
| `align-items` / `justify-content` | ✅ | `align-items: center;` / `justify-content: space-between;` |
| `position` | ✅ | `position: absolute;` / `position: relative;` |
| `left` / `top` / `right` / `bottom` | ✅ | `left: 4;` |
| `display` | ✅ | `display: flex;` / `display: none;` |
| `z-index` | ✅ | `z-index: 10;` |

### 3.3 颜色值语法
- `#RRGGBB`：`#1e1f22`
- `#AARRGGBB`：`#ff2c2c34`
- `rect(#color)`：纯色矩形纹理
- `sdf(#color, radius, [borderWidth, #borderColor])`：SDF 圆角矩形（**主要使用这个**）
- `sprite(namespace:path)`：MC 资源纹理
- `group(tex1, tex2, ...)`：分层纹理
- `empty`：无纹理

### 3.4 Stylesheet 注册路径
- **UI 级**：`UI.of(rootElement, Stylesheet...)` 或 `UI.of(rootElement, ResourceLocation...)`
- **全局内置**：`StylesheetManager.INSTANCE.registerBuiltinStylesheet(ResourceLocation, Stylesheet)`
- **资源包级**：在 `assets/kinetic_planner/lss/kp.lss` 放 LSS 文件，框架自动扫描
- **子树作用域**：`UIElement.addLocalStylesheet(Stylesheet)` 或 `addLocalStylesheet(String lssText)`

### 3.5 spec §3.2 + §8.4 修正
1. **CSS 变量降级方案**：用 Java 常量 + LSS 字符串插值。例如：
   ```java
   public static final int KP_ACCENT = 0xFF7C57D4;
   String lss = """
       .kp-tab-selected { background: sdf(#%08X, 0, 2, #%08X); }
       """.formatted(KP_ACCENT, KP_ACCENT);
   ```
2. **`:hover` 不需要降级**：spec §8.4 风险条目过时，`:hover` 已被框架支持，直接用 `.kp-config-row:hover { ... }`。
3. **`MOUSE_ENTER` / `MOUSE_LEAVE` 事件**：仅用于行为反应（tooltip、声音），不用于视觉 hover 状态。

## 4. 纹理类 API（spec §3.3）

### 4.1 类位置（全部在 `com.lowdragmc.lowdraglib2.gui.texture`）
| 类 | 包路径 | 用途 |
|---|---|---|
| `IGuiTexture` | `gui.texture` | 接口，含 `setColor(int)` 默认实现（no-op） |
| `TransformTexture` | `gui.texture` | 抽象基类，提供 `rotate` / `scale` / `transform`，**不是 tint 包装器** |
| `ColorRectTexture` | `gui.texture` | 纯色矩形 |
| `ColorBorderTexture` | `gui.texture` | 边框矩形（border 正=outset 外，负=inset 内） |
| `SDFRectTexture` | `gui.texture` | SDF 圆角矩形（主要用这个） |
| `TextTexture` | `gui.texture` | 文本渲染 |
| `SpriteTexture` | `gui.texture` | MC `ResourceLocation` 纹理（**spec §3.3 中的 `UIResourceTexture` 实际应为 `SpriteTexture`**） |
| `UIResourceTexture` | `gui.texture` | 编辑器内部用，**不是** MC `ResourceLocation` |
| `Icons` | `gui.texture` | 内置图标注册表，全是 `SpriteTexture` 实例 |

### 4.2 关键 API
| 类 | 构造/工厂 | 主要方法 |
|---|---|---|
| `ColorRectTexture` | `new ColorRectTexture(int color)` | `setColor(int) : ColorRectTexture` |
| `ColorBorderTexture` | `new ColorBorderTexture(int border, int color)` | `setBorder(int)`, `setColor(int)`（border 正=outset 负=inset） |
| `SDFRectTexture` | `new SDFRectTexture()` 或 `SDFRectTexture.of(int color)` | `setColor(int)`, `setBorderColor(int)`, `setRadius(float)`, `setRadius(Vector4f)`, `setStroke(float)` |
| `TextTexture` | `new TextTexture(String, int)` | **`updateText(String)`（非 `setText`）**, `setColor(int)`, `setDropShadow(boolean)`, `setWidth(int)` |
| `SpriteTexture` | `SpriteTexture.of(ResourceLocation)` / `SpriteTexture.of(String)` | `setColor(int) : SpriteTexture`, `copy() : SpriteTexture` |

### 4.3 Tint 机制（spec §3.3 关键修正）
**两种 tint 路径**：
1. **Mechanism A：`IGuiTexture.setColor(int)`（推荐，可靠）** — 直接修改纹理实例的 color 字段。
   - **重要**：`Icons.*` 字段是共享单例，必须 `icon.copy().setColor(stateColor)` 避免污染单例
2. **Mechanism B：`UIElement.style.color(int)` + `elementColor`（仅对 SDFRectTexture 生效）**
   - `BasicStyle.color(int)` 设置 `PropertyRegistry.COLOR`（默认 -1 = 无 tint）
   - **只有 `SDFRectTexture` 重写了 `drawInternal(GUIContext, ...)` 并读取 `context.elementColor`**，其他纹理不响应
   - 框架在 draw 期间 `RenderSystem.setShaderColor(...)` 应用 tint，但 `SpriteTexture` 等不读取它

### 4.4 spec §3.3 修正
1. **`UIResourceTexture` 改为 `SpriteTexture`**：用 `SpriteTexture.of(ResourceLocation.fromNamespaceAndPath("kinetic_planner", "textures/gui/gear.png"))` 加载 MC 资源纹理
2. **`TextTexture.setText` 改为 `updateText`**：API 名修正
3. **图标 tint 实现**：使用 `icon.copy().setColor(stateColor)`，再 `element.style.backgroundTexture(tintedIcon)` 重新应用
4. **`ColorBorderTexture` border 符号**：正=outset（外凸），负=inset（内陷）。KP panel 边框用正值（外凸 1px 高光），Tab 选中下划线用负值（内陷 1px 底边）

## 5. UIElement / ModularUI / Widget API（spec §2.2 + §9）

### 5.1 类位置
- `com.lowdragmc.lowdraglib2.gui.ui.UIElement` — UI 树节点基类
- `com.lowdragmc.lowdraglib2.gui.ui.UI` — UI 容器，`@Data(staticConstructor = "of")`
- `com.lowdragmc.lowdraglib2.gui.ui.ModularUI` — UI 工厂
- `com.lowdragmc.lowdraglib2.gui.ui.ModularUI.ModularUIWidget` — **非静态内部类**，实现 `GuiEventListener, NarratableEntry, Renderable, IModularUIHolder`
- `com.lowdragmc.lowdraglib2.gui.ui.elements.{Toggle, Button, Label}` — 基础控件
- `com.lowdragmc.lowdraglib2.gui.ui.style.{LayoutStyle, BasicStyle, Style}` — 样式

### 5.2 ModularUI 构建
```java
UIElement root = new UIElement();  // 构建树
// ... addChild / layout / style ...
ModularUI ui = ModularUI.of(UI.of(root, stylesheet1, stylesheet2));
ModularUIWidget widget = ui.getWidget();  // 客户端 only
widget.render(gg, mouseX, mouseY, partialTick);  // Mixin 调用入口
ui.init(screenWidth, screenHeight);  // 屏幕尺寸变化时重新初始化
```

### 5.3 UIElement fluent API
| 方法 | 用途 |
|---|---|
| `addChild(UIElement) : UIElement` | 添加子元素 |
| `addChildren(UIElement...) : UIElement` | 批量添加 |
| `layout(Consumer<LayoutStyle>) : UIElement` | 配置布局（**服务端短路**，仅客户端生效） |
| `style(Consumer<BasicStyle>) : UIElement` | 配置样式（**服务端短路**） |
| `setId(String) : UIElement` | 设置 ID |
| `addClass(String) / removeClass(String) : UIElement` | 添加/移除 CSS 类 |
| `setActive(boolean) / setVisible(boolean) : UIElement` | 启用/可见性 |
| `addEventListener(String, UIEventListener) : UIElement` | 注册事件监听 |

### 5.4 ModularUIWidget 事件方法（全部已确认签名匹配 spec §2.2）
| 方法 | 签名 |
|---|---|
| `mouseClicked` | `boolean mouseClicked(double, double, int)` |
| `mouseReleased` | `boolean mouseReleased(double, double, int)` |
| `mouseMoved` | `void mouseMoved(double, double)` |
| `mouseScrolled` | `boolean mouseScrolled(double, double, double, double)` |
| `mouseDragged` | `boolean mouseDragged(double, double, int, double, double)` |
| `keyPressed` | `boolean keyPressed(int, int, int)` |
| `keyReleased` | `boolean keyReleased(int, int, int)` |
| `charTyped` | `boolean charTyped(char, int)` |
| `render` | `void render(GuiGraphics, int, int, float)` ← Mixin 调用入口 |

### 5.5 Toggle API
| 方法 | 用途 |
|---|---|
| `new Toggle()` | 无参构造，默认 `direction=ROW, height=14, padding=1` |
| `setOn(boolean) : Toggle` | 设置开关状态 |
| `getValue() : Boolean` | 取当前状态 |
| `setOnToggleChanged(BooleanConsumer) : Toggle` | 注册变化监听 |
| `registerValueListener(Consumer<Boolean>) : ISubscription` | 注册监听，返回 unsubscribe 句柄 |
| `setText(String/Component) : Toggle` | 设置 label 文本 |

### 5.6 Button API（**关键修正**）
- **`Button.setOnClick(Runnable)` 不存在** — 实际签名是 `setOnClick(UIEventListener)`，`UIEventListener` 是 `@FunctionalInterface` 接收 `UIEvent`
- **包装 Runnable**：`button.setOnClick(event -> runnable.run())`
- 内部已经处理 `MOUSE_DOWN` 事件 + 播放点击声音 + 检查 `isActive()`

### 5.7 Label API
- `Label` extends `TextElement`，实现 `IBindable<Component>, IDataConsumer<Component>`
- `new Label()` 默认文本 "Label"，高度 9px
- `setText(Component/String) : TextElement` — **返回 TextElement 不是 Label**，链式调用需要 cast 或用 `setValue(Component) : Label`
- `Label.setValue(Component)` 是 chainable 的

### 5.8 LayoutStyle 关键方法
- `flexDirection(FlexDirection)` — `ROW` / `COLUMN` / `ROW_REVERSE` / `COLUMN_REVERSE`
- `justifyContent(AlignContent)` — `FLEX_START` / `CENTER` / `FLEX_END` / `SPACE_BETWEEN` / `SPACE_AROUND` / `SPACE_EVENLY`
- `alignItems(AlignItems)` / `alignContent(AlignContent)` / `alignSelf(AlignItems)`
- `paddingAll(float)` / `paddingHorizontal(float)` / `paddingVertical(float)` / `paddingLeft/Right/Top/Bottom(float)`
- `marginAll(float)` 等同模式
- `gapAll(float)` / `gapRow(float)` / `gapColumn(float)`
- `width(float)` / `height(float)` / `widthAuto()` / `heightAuto()` / `widthPercent(float)` / `heightPercent(float)`
- `minWidth(float)` / `maxWidth(float)` / `minHeight(float)` / `maxHeight(float)`
- `display(TaffyDisplay)` — `FLEX` / `NONE`
- `positionType(TaffyPosition)` — `RELATIVE` / `ABSOLUTE`
- `left(float)` / `top(float)` / `right(float)` / `bottom(float)`

### 5.9 BasicStyle 关键方法（**关键修正**）
- `backgroundTexture(IGuiTexture) : BasicStyle` — 设置背景纹理
- `background(IGuiTexture) : BasicStyle` — `backgroundTexture` 别名
- `overlayTexture(IGuiTexture) : BasicStyle` — 顶层覆盖纹理（drawn after children）
- `overlay(IGuiTexture) : BasicStyle` — `overlayTexture` 别名
- `color(int) : BasicStyle` — ARGB tint（仅 `SDFRectTexture` 响应；其他纹理不响应）
- `opacity(float) : BasicStyle`
- `zIndex(int) : BasicStyle`
- `tooltips(Tooltips)` / `tooltips(Component...)` / `tooltips(String...)`
- `overflowVisible(boolean) : BasicStyle`
- **`BasicStyle` 无 `border(...)` 方法** — 边框用 `ColorBorderTexture` 作为 `backgroundTexture`

## 6. spec 修正清单

基于上述验证，spec 文档需要修正的点：

| spec 章节 | 原假设 | 修正 |
|---|---|---|
| §3.2 | 使用 `--kp-accent` CSS 变量 | 改用 Java 常量 + LSS 字符串插值 |
| §3.2 | `:hover` 降级为事件监听 | `:hover` 已支持，直接使用 |
| §3.2 | `border-bottom: 2px solid var(--kp-accent)` | 改用 `background: sdf(#color, 0, 2, #borderColor)` 或 `ColorBorderTexture` |
| §3.2 | `background-color: var(--kp-accent)` | 改用 `background: rect(#color)` 或 `background: #AARRGGBB` |
| §3.3 | `UIResourceTexture` 加载 MC 资源 | 改用 `SpriteTexture.of(ResourceLocation)` |
| §3.3 | `TextTexture.setText(String)` | 改用 `TextTexture.updateText(String)` |
| §3.3 | `TransformTexture` 用于 tint | tint 实际通过 `setColor(int)` 或 `style.color(int)`（仅 SDFRectTexture 响应后者） |
| §3.3 | `Icons.*` 直接 `setColor` | 必须 `icon.copy().setColor(stateColor)` 避免污染单例 |
| §8.4 | "LSS 不支持 CSS 变量或 `:hover`" | 修正为 "LSS 不支持 CSS 变量；`:hover` 已支持" |
| §9.1 | `Button.setOnClick(Runnable)` | 改用 `button.setOnClick(event -> runnable.run())` 包装 |
| §9.1 | `BasicStyle.border(...)` | 不存在，用 `ColorBorderTexture` 作为 `backgroundTexture` |
| §4.3 | Tab 切换清空 column 重建 | 用 `TabView.setOnTabSelected(Consumer<Tab>)`，content panel 已自带显示/隐藏，KP 只需在每个 Tab 的 content panel 内构建对应 provider 的配置行 |

## 7. 实施提示

1. **避免首次 `addTab` 触发的回调副作用**：要么在回调中检查 `tab.getContent()` 是否非空（首次触发时已设置），要么在 addTab 之前先把 content 构建好。
2. **`Button.setOnClick` 包装**：所有 Button click 处理用 `setOnClick(event -> myHandler.run())` 形式。
3. **图标 tint 不用 `style.color`**：`SpriteTexture` 不读取 `elementColor`。每次状态变化时 `setBackground(icon.copy().setColor(newState))`。
4. **LSS 文件位置**：放在 `src/main/resources/assets/kinetic_planner/lss/kp.lss`，框架自动扫描；或在 `KpStylesheet.register()` 中通过 `StylesheetManager.INSTANCE.registerBuiltinStylesheet(...)` 程序化注册。
5. **服务端短路**：`UIElement.layout(...)` 和 `style(...)` 在服务端不执行 consumer。KP 配置面板是纯客户端，不受影响，但注意不要在 layout/style lambda 中放有副作用的代码。
6. **`ModularUIWidget` 是非静态内部类**：Mockito mock 时需要用 `mock(ModularUI.ModularUIWidget.class)` 语法（spec §10.2 已正确指出）。
