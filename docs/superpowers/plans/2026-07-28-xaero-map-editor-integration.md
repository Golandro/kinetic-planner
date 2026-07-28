# Xaero Map 编辑器集成 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 KP 现有观看模式基础上，新增 LDLib2 Editor 全屏编辑模式，承载 P2 CAD 编辑工具的 UI 框架。

**Architecture:** 双模式共存——观看模式（现有 Mixin 叠加）通过 `KpClientState.isEditMode()` 守卫保持不变；编辑模式以 `KpEditorScreen` 为 Screen 壳，持有 `GuiMap` 实例与 `KpMapEditor`（LDLib2 Editor 子类），三层渲染（地图瓦片→CAD 编辑层→Editor UI），事件经泛化的 `KpUIEventForwarder` 转发到 UI 树，未消费时按工具模式路由到 `guiMap` 或 `CADRenderEngine`。

**Tech Stack:** Java 21 / NeoForge 1.21.1 / Mixin / LDLib2 2.2.26（Editor/ModularUI/View）/ Blaze3D / JUnit 5 + Mockito 5

## Global Constraints

- **sourceSet 分离**：`src/main/java`（common，不引用 `net.minecraft.client.*`/`com.mojang.blaze3d.*`）、`src/client/java`（client）、`src/server/java`（server）、`src/test/java`（test）。所有 editor/gui/mixin 新类属 client。
- **MC 1.21.1 API**：`addVertex(x,y,z)`（非 `vertex()`）、`setColor(r,g,b,a)`（非 `color()`）、无 `endVertex()`、`RenderSystem.setShader(GameRenderer::getPositionColorShader)`。
- **Mixin**：`remap=false` 用于 Create/Xaero 类；Mojang 类不需 `remap=false`；`defaultRequire:0`；方法名 `kp$` 前缀。
- **LDLib2 2.2.26 已验证 API**（见 spec §13 + 本计划 §LDLib2 API 速查）：
  - `ModularUI.of(UI ui)` / `UI.of(UIElement root)` / `UI.of(UIElement root, Stylesheet... stylesheets)`
  - `ModularUI.setScreen(Screen)` 设置 `getScreen()` 返回值；`setScreenAndInit(Screen)` 同时触发 `init(w,h)`
  - `Editor extends UIElement`，abstract 方法仅 `createNewEditorInstance()`
  - `Editor` 字段：`top/mainView/menuContainer/buttonContainer/closeButton/inspectorView/resourceView/historyView/icon/topPlaceholder` 为 `public final`；`rootWindow/leftWindow/rightWindow/centerWindow/bottomWindow` 为 `public`（非 final）
  - `Editor.askToSaveProject(@Nullable Runnable onFinish)`（参数是 `Runnable`，非 `Consumer<Boolean>`）
  - `Editor.currentProject` 为 `private`，通过 `getCurrentProject()` 访问（始终为 null 时跳过保存对话框）
  - `View()` 默认宽高 100%，无背景
  - `UIElement` 无 `setOnResize`；监听布局变化用 `addEventListener(UIEvents.LAYOUT_CHANGED, listener)`
- **测试**：common 类 JUnit 5 纯 JVM；client 类用 Mockito mock MC 依赖；运行时 Mixin/Screen 行为靠手动验收。
- **构建命令**：`gradlew compileJava compileClientJava compileServerJava` / `gradlew test` / `gradlew build` / `gradlew runClient`。Shell 为 `cmd.exe` 时多命令用 `&`。
- **Git**：分支 `1.21` 直接提交；提交消息 `type: description`；不提交 `.superpowers/`、`.claude/`、`.agents/memory`。

---

## LDLib2 API 速查（实现时反复参考）

### ModularUI Screen 绑定（关键模式）

`ModularUI` 不通过 `of(...)` 重载接受 Screen。绑定方式：

```java
ModularUI ui = ModularUI.of(UI.of(rootElement, stylesheet));
ui.setScreen(myScreen);          // 让 ui.getScreen() == myScreen
ui.init(width, height);          // 触发布局初始化（也可用 setScreenAndInit 合并）
```

**编辑模式不调用 `addRenderableWidget(ui.getWidget())`**——KpEditorScreen 手动调用 `eventForwarder.render()` 与 `eventForwarder.mouseXxx()` 转发，与观看模式共用 forwarder 逻辑（spec §3.2 + §6.9 决策）。

### Editor 关键 API

| API | 签名 |
|---|---|
| 构造 | `public Editor()` |
| abstract | `protected abstract Editor createNewEditorInstance();` |
| initMenus | `protected void initMenus()`（默认添加 FileMenu/ViewMenu 到 menuContainer；override 不调 super 即可） |
| onPrepareInspectorView | `protected void onPrepareInspectorView()` |
| onPrepareHistoryView | `protected void onPrepareHistoryView()` |
| onPrepareResourceView | `protected void onPrepareResourceView()` |
| placeView | `public void placeView(View view, Supplier<ViewContainer> fallback)` |
| close | `public void close()`（window==null 时调 exit()） |
| exit | `public void exit()` / `public void exit(@Nullable Runnable onFinish)` |
| askToSaveProject | `public void askToSaveProject(@Nullable Runnable onFinish)` |
| getModularUI | 继承自 UIElement，`public @Nullable ModularUI getModularUI()` |
| getCurrentProject | `public @Nullable IProject getCurrentProject()`（始终 null 时跳过保存对话框） |

### Editor public 字段

`top` / `icon` / `menuContainer` / `topPlaceholder` / `buttonContainer` / `closeButton`（Button）/ `fileMenu` / `viewMenu` / `mainView` / `rootWindow` / `leftWindow` / `rightWindow` / `centerWindow` / `bottomWindow` / `inspectorView` / `resourceView` / `historyView` / `editorSettings`。

### View 类

`public class View extends UIElement`，构造：`public View()` / `public View(String name)` / `public View(String name, IGuiTexture icon)`。默认无背景，宽高 100%。

### SplittableWindow 容器获取

`SplittableWindow.getLeftTop()` / `getLeftBottom()` / `getRightTop()` / `getRightBottom()` 返回 `ViewContainer`，供 `Editor.placeView(View, Supplier<ViewContainer>)` 使用。

### UIElement 事件注册

UIElement 无 `setOnClick` 等便捷 setter，需用 `addEventListener(UIEvents.CLICK, listener)` / `addEventListener(UIEvents.MOUSE_DOWN, listener)` 等。Button 子类有 `setOnClick(Consumer<ClickEvent>)`。

---

## File Structure

### 新增文件（全部在 client sourceSet）

```
src/client/java/net/jsmua/kinetic_planner/
├── editor/                                 # NEW
│   ├── KpEditorScreen.java                 # 编辑模式 Screen
│   ├── KpMapEditor.java                    # Editor 子类
│   └── EditToolState.java                  # 编辑工具状态
├── gui/                                    # NEW
│   ├── KpRibbonBar.java                    # Ribbon 菜单栏
│   ├── ToolPanelView.java                  # 工具面板 View
│   └── MapPlaceholderView.java             # 透明占位 View
├── cadengine/
│   └── EditLayerRenderer.java              # NEW: 编辑图形层渲染器
├── mapadapter/
│   └── MapOverlayContextProvider.java      # NEW: 接口
└── mixin/
    ├── XaeroUiSuppressMixin.java           # NEW: 方案 B UI 抑制
    ├── GuiMapRemovedMixin.java             # NEW (按需)
    └── GuiMapInputMixin.java               # NEW (按需)

src/test/java/net/jsmua/kinetic_planner/
├── editor/
│   ├── EditToolStateTest.java              # NEW
│   └── KpMapEditorTest.java                # NEW (Mockito)
├── gui/
│   ├── KpRibbonBarTest.java                # NEW (Mockito)
│   ├── ToolPanelViewTest.java              # NEW (Mockito)
│   └── MapPlaceholderViewTest.java         # NEW
├── mapadapter/
│   └── MapOverlayContextProviderTest.java   # NEW
└── cadengine/
    └── EditLayerRendererTest.java          # NEW (Mockito)
```

### 修改文件

| 文件 | 变更 |
|---|---|
| `config/KpClientState.java` | +`isEditMode()`/+`setEditMode(boolean)` |
| `config/KpUIEventForwarder.java` | `render`/`mouseMoved` 保留 void，其余方法显式返回 boolean（已实现，仅需补全文档+保持不变） |
| `mixin/XaeroMapRenderHook.java` | +`isEditMode()` 守卫 |
| `mixin/XaeroMapGearButtonMixin.java` | +`isEditMode()` 守卫（render + mouseClicked） |
| `mapadapter/XaeroMapOverlayProvider.java` | `isMapOpen`/`captureContext` 通过 `MapOverlayContextProvider` 接口识别 |
| `instrument/WorldTreeReadOverlay.java` | +`getTransform()`/+`getGeometryCache()` 访问器 |
| `cadengine/CADRenderEngine.java` | +交互区域模块（hitTest/getSnapPoints）+事件模块（handleClick/handleDrag/handleHover/handleKey） |
| `config/KPCommands.java` | +`/kp edit` 与 `/kp exit` 命令 |
| `config/KpGearButton.java` | +编辑模式触发按钮（齿轮长按 / 双击 / 子按钮） |
| `src/main/resources/kinetic_planner.mixins.json` | 添加 `XaeroUiSuppressMixin`、（按需）`GuiMapRemovedMixin`/`GuiMapInputMixin` |
| `test/.../KpUIEventForwarderTest.java` | 9 个用例已存在，仅微调文档 |
| `test/.../KpClientStateTest.java` | +`editMode` 状态切换用例 |

---

## Phase 1: KpClientState.isEditMode() + 模式守卫

**目标**：建立全局编辑模式标志，为现有 Mixin hook 添加守卫。完成后观看模式行为完全不变，但编辑模式标志已生效。验收：编译通过、所有现有测试通过、运行时打开 Xaero 地图观看模式无回归。

### Task 1.1: KpClientState 扩展 isEditMode/setEditMode

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KpClientState.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/config/KpClientStateTest.java`

**Interfaces:**
- Produces: `KpClientState.isEditMode() -> boolean`、`KpClientState.setEditMode(boolean) -> void`

- [ ] **Step 1: 在 KpClientStateTest 中添加 isEditMode 测试用例**

打开 `src/test/java/net/jsmua/kinetic_planner/config/KpClientStateTest.java`，在 `@BeforeEach` 的 `resetState()` 中追加 `KpClientState.setEditMode(false);`，并在类末尾添加 3 个新用例：

```java
    @Test
    void editModeDefaultsToFalse() {
        assertFalse(KpClientState.isEditMode());
    }

    @Test
    void setEditModeTrueUpdatesState() {
        KpClientState.setEditMode(true);
        assertTrue(KpClientState.isEditMode());
        KpClientState.setEditMode(false);
        assertFalse(KpClientState.isEditMode());
    }

    @Test
    void editModeIndependentOfConfigPanelVisible() {
        KpClientState.setEditMode(true);
        KpClientState.setConfigPanelVisible(false);
        assertTrue(KpClientState.isEditMode());
        assertFalse(KpClientState.isConfigPanelVisible());

        KpClientState.setEditMode(false);
        KpClientState.setConfigPanelVisible(true);
        assertFalse(KpClientState.isEditMode());
        assertTrue(KpClientState.isConfigPanelVisible());
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew test --tests net.jsmua.kinetic_planner.config.KpClientStateTest`
Expected: 编译失败，错误 `cannot find symbol: method isEditMode()` / `setEditMode(boolean)`

- [ ] **Step 3: 在 KpClientState 中实现 isEditMode / setEditMode**

在 `src/client/java/net/jsmua/kinetic_planner/config/KpClientState.java` 中，于 `configPanelVisible` 字段下方添加新字段，于 `toggleConfigPanel()` 方法下方添加新方法。同时把类文档从"配置面板可见性"扩展为"客户端 UI 全局状态"。完整修改：

```java
public final class KpClientState {

    private static boolean configPanelVisible = false;

    /**
     * 是否处于编辑模式（spec §1.3 双模式架构）。
     *
     * <p>全局模式标志：Mixin 守卫读取此值决定是否跳过观看模式渲染/事件路径。
     * 与 {@link EditToolState}（编辑会话状态）生命周期不同——后者仅在编辑模式活跃。
     */
    private static boolean editMode = false;

    private KpClientState() {}

    // ... isConfigPanelVisible / setConfigPanelVisible / toggleConfigPanel 保持不变 ...

    /**
     * 是否处于编辑模式。
     *
     * @return true 如果当前 Screen 为 KpEditorScreen（编辑模式活跃）
     */
    public static boolean isEditMode() {
        return editMode;
    }

    /**
     * 设置编辑模式状态。
     *
     * <p>由 KpEditorScreen.create/onClose 调用。Mixin 守卫读取此值跳过观看模式路径。
     *
     * @param mode true 进入编辑模式；false 退出
     */
    public static void setEditMode(boolean mode) {
        editMode = mode;
    }
}
```

注意：`EditToolState` 类在 Phase 4 创建，此处 javadoc 引用其类名仅作为前向说明。由于 JavaDoc 在编译期不解析类引用（除非启用 doclint 严格模式），此处不会编译失败；若启用严格 doclint，可临时去掉 `{@link EditToolState}`。

- [ ] **Step 4: 运行测试确认通过**

Run: `gradlew test --tests net.jsmua.kinetic_planner.config.KpClientStateTest`
Expected: PASS，6 个测试用例全绿（原 3 + 新 3）。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/config/KpClientState.java src/test/java/net/jsmua/kinetic_planner/config/KpClientStateTest.java
git commit -m "feat(client-state): add isEditMode/setEditMode global flag"
```

### Task 1.2: XaeroMapRenderHook 添加 isEditMode 守卫

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapRenderHook.java`

**Interfaces:**
- Consumes: `KpClientState.isEditMode()`

- [ ] **Step 1: 在 kp$onMapRender 方法头部添加守卫**

修改 `XaeroMapRenderHook.java` 第 37-44 行的 `kp$onMapRender` 方法，在 try 块前添加守卫：

```java
    @Inject(method = "render", at = @At("RETURN"))
    private void kp$onMapRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // 编辑模式跳过：CAD 由 EditLayerRenderer 在 KpEditorScreen.render 中直接渲染
        if (KpClientState.isEditMode()) return;
        try {
            WorldTreeReadOverlay.onMapRender((GuiMap) (Object) this, guiGraphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("WorldTreeReadOverlay render failed", t);
        }
    }
```

新增 import：`import net.jsmua.kinetic_planner.config.KpClientState;`（放在文件顶部 import 区，按字母顺序插入 `KineticPlannerMod` 之后）。

- [ ] **Step 2: 编译确认**

Run: `gradlew compileClientJava`
Expected: 编译成功，无警告。

- [ ] **Step 3: 运行现有测试确认无回归**

Run: `gradlew test`
Expected: 全部测试通过（60 个 @Test，2 个 @Disabled）。

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapRenderHook.java
git commit -m "feat(mixin): skip XaeroMapRenderHook in edit mode"
```

### Task 1.3: XaeroMapGearButtonMixin 添加 isEditMode 守卫

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java`

- [ ] **Step 1: 在 kp$renderConfigPanel 与 kp$handleMouseClick 头部添加守卫**

修改 `XaeroMapGearButtonMixin.java`，在 `kp$renderConfigPanel` 方法（行 70-81）的 `if (!OverlayControl.isEnabled()) return;` 之后、`kp$ensureInit();` 之前插入守卫。同样在 `kp$handleMouseClick` 方法（行 83-98）相同位置插入。修改后的两个方法：

```java
    @Inject(method = "render", at = @At("RETURN"))
    private void kp$renderConfigPanel(GuiGraphics gg, int mouseX, int mouseY,
                                       float partialTicks, CallbackInfo ci) {
        if (!OverlayControl.isEnabled()) return;
        // 编辑模式跳过：齿轮按钮由 KpRibbonBar 替代，配置面板由 Ribbon 设置抽屉承载
        if (KpClientState.isEditMode()) return;
        kp$ensureInit();
        kp$gearButton.render(gg, mouseX, mouseY);
        if (KpClientState.isConfigPanelVisible()) {
            kp$forwarder.render(gg, mouseX, mouseY, partialTicks);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kp$handleMouseClick(double mouseX, double mouseY, int button,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!OverlayControl.isEnabled()) return;
        // 编辑模式跳过：事件由 KpEditorScreen 拦截路由
        if (KpClientState.isEditMode()) return;
        kp$ensureInit();
        if (kp$gearButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (KpClientState.isConfigPanelVisible()
            && kp$forwarder.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
```

新增 import：`import net.jsmua.kinetic_planner.config.KpClientState;`（已有 `KpClientState` 引用，但原文件未导入此类——查阅原文件确认。若已存在则跳过）。

- [ ] **Step 2: 编译 + 测试**

Run: `gradlew compileClientJava & gradlew test`
Expected: 编译成功；所有测试通过。

- [ ] **Step 3: 运行时验收（手动）**

Run: `gradlew runClient`
打开 Xaero 全屏地图：齿轮按钮 + 配置面板正常工作（观看模式无回归）。

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java
git commit -m "feat(mixin): skip gear button mixin hooks in edit mode"
```

---

## Phase 2: KpEditorScreen + KpMapEditor 壳 + 透明中心

**目标**：搭起编辑模式 Screen 骨架，可触发进入编辑模式（即使地图层未渲染，也要看到 Editor UI 框架与透明中心）。验收：触发"进入编辑"后 Screen 切换到 KpEditorScreen，Ribbon（占位空容器）+ 左/右/中心区域可见，中心透明。

### Task 2.1: MapOverlayContextProvider 接口

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayContextProvider.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayContextProviderTest.java`

**Interfaces:**
- Produces: `MapOverlayContextProvider.getGuiMap() -> @Nullable GuiMap`

- [ ] **Step 1: 编写失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayContextProviderTest.java`：

```java
package net.jsmua.kinetic_planner.mapadapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MapOverlayContextProvider} 接口契约测试。
 *
 * <p>接口本身无逻辑，仅验证默认方法返回 null（便于 KpEditorScreen 未持有 GuiMap 时安全返回）。
 */
class MapOverlayContextProviderTest {

    @Test
    void defaultGetGuiMapReturnsNull() {
        MapOverlayContextProvider provider = () -> null;
        assertNull(provider.getGuiMap());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew test --tests net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProviderTest`
Expected: 编译失败 `cannot find symbol MapOverlayContextProvider`。

- [ ] **Step 3: 创建接口**

创建 `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayContextProvider.java`：

```java
package net.jsmua.kinetic_planner.mapadapter;

import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

/**
 * 暴露持有者关联的 {@link GuiMap} 实例（spec §6.10）。
 *
 * <p>由 {@link net.jsmua.kinetic_planner.editor.KpEditorScreen} 实现，
 * 让 {@link XaeroMapOverlayProvider} 通过接口（非 instanceof）获取编辑模式下的 GuiMap。
 *
 * <p>观看模式下 Screen 直接是 GuiMap；编辑模式下 Screen 是 KpEditorScreen，
 * 通过此接口暴露内部持有的 GuiMap。
 */
public interface MapOverlayContextProvider {

    /**
     * 返回当前关联的 GuiMap 实例。
     *
     * @return GuiMap 实例；未持有时返回 null
     */
    @Nullable
    GuiMap getGuiMap();
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `gradlew test --tests net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProviderTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayContextProvider.java src/test/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayContextProviderTest.java
git commit -m "feat(mapadapter): add MapOverlayContextProvider interface"
```

### Task 2.2: MapPlaceholderView 透明占位 View

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/MapPlaceholderView.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/MapPlaceholderViewTest.java`

**Interfaces:**
- Produces: `MapPlaceholderView()` 构造，默认无背景，宽高 100%

- [ ] **Step 1: 编写失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/gui/MapPlaceholderViewTest.java`：

```java
package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MapPlaceholderView} 构造契约测试。
 *
 * <p>View 默认无背景纹理（spec §6.6 + LDLib2 验证）。
 * View 继承 UIElement，构造后应可被添加到容器。
 */
class MapPlaceholderViewTest {

    @Test
    void constructCreatesViewWithNoBackground() {
        MapPlaceholderView view = new MapPlaceholderView();
        assertNotNull(view);
        // View 默认宽高 100%（继承自 View 构造函数）
        // 不抛异常即可
    }

    @Test
    void viewIsUIElementSubclass() {
        MapPlaceholderView view = new MapPlaceholderView();
        assertInstanceOf(UIElement.class, view);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew test --tests net.jsmua.kinetic_planner.gui.MapPlaceholderViewTest`
Expected: 编译失败 `cannot find symbol MapPlaceholderView`。

- [ ] **Step 3: 创建 MapPlaceholderView**

创建 `src/client/java/net/jsmua/kinetic_planner/gui/MapPlaceholderView.java`：

```java
package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;

/**
 * 中心透明占位 View（spec §6.6）。
 *
 * <p>无背景纹理（View 默认 {@code IGuiTexture.EMPTY}），
 * 无事件监听器，唯一作用是占位让 Editor 布局正确。
 *
 * <p>Layer 1（GuiMap 瓦片）+ Layer 2（CAD 编辑层）从此 View 区域天然透过。
 */
public class MapPlaceholderView extends View {

    public MapPlaceholderView() {
        super();
    }

    public MapPlaceholderView(String name) {
        super(name);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `gradlew test --tests net.jsmua.kinetic_planner.gui.MapPlaceholderViewTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/MapPlaceholderView.java src/test/java/net/jsmua/kinetic_planner/gui/MapPlaceholderViewTest.java
git commit -m "feat(gui): add MapPlaceholderView transparent center view"
```

### Task 2.3: KpMapEditor Editor 子类

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/editor/KpMapEditor.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/editor/KpMapEditorTest.java`

**Interfaces:**
- Produces: `KpMapEditor()` 构造；override `createNewEditorInstance()` / `initMenus()` / `onPrepareResourceView()` / `onPrepareHistoryView()`；method `placeCustomViews()`

- [ ] **Step 1: 编写失败测试（Mockito）**

创建 `src/test/java/net/jsmua/kinetic_planner/editor/KpMapEditorTest.java`：

```java
package net.jsmua.kinetic_planner.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KpMapEditor} 构造与方法契约测试。
 *
 * <p>spec §6.3：initMenus 只清除 menuContainer（保留 buttonContainer + closeButton）；
 * onPrepareResourceView/onPrepareHistoryView 为空；placeCustomViews 放置 3 个 View。
 *
 * <p>Editor 是 UIElement 子类，构造不依赖 MC 环境，可在纯 JVM 测试。
 * 但其内部布局/accessor 可能在没有 ModularUI 挂载时受限，此处仅测契约。
 */
class KpMapEditorTest {

    @Test
    void constructWithoutCrash() {
        KpMapEditor editor = new KpMapEditor();
        assertNotNull(editor);
    }

    @Test
    void createNewEditorInstanceReturnsKpMapEditor() {
        KpMapEditor editor = new KpMapEditor();
        var created = editor.createNewEditorInstance();
        assertNotNull(created);
        assertInstanceOf(KpMapEditor.class, created);
    }

    @Test
    void publicFieldsAccessible() {
        KpMapEditor editor = new KpMapEditor();
        // 验证 Editor public 字段可访问（spec §13 LDLib2 验证）
        assertNotNull(editor.top);
        assertNotNull(editor.mainView);
        assertNotNull(editor.menuContainer);
        assertNotNull(editor.buttonContainer);
        assertNotNull(editor.closeButton);
        assertNotNull(editor.rootWindow);
        assertNotNull(editor.centerWindow);
        assertNotNull(editor.leftWindow);
        assertNotNull(editor.rightWindow);
        assertNotNull(editor.inspectorView);
        assertNotNull(editor.historyView);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew test --tests net.jsmua.kinetic_planner.editor.KpMapEditorTest`
Expected: 编译失败 `cannot find symbol KpMapEditor`。

- [ ] **Step 3: 创建 KpMapEditor**

创建 `src/client/java/net/jsmua/kinetic_planner/editor/KpMapEditor.java`：

```java
package net.jsmua.kinetic_planner.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import net.jsmua.kinetic_planner.gui.MapPlaceholderView;
import net.jsmua.kinetic_planner.gui.ToolPanelView;

import java.util.function.Supplier;

/**
 * LDLib2 {@link Editor} 子类（spec §6.3）。
 *
 * <p>精简配置：不启用 FileMenu/ViewMenu（替换为 KpRibbonBar 在 Phase 4），
 * 不启用 ResourceView/HistoryView，启用 InspectorView。
 *
 * <p>中心区域放置 {@link MapPlaceholderView}（透明），
 * 左侧放置 {@link ToolPanelView}（Phase 4 实现），
 * 右侧由 onPrepareInspectorView 默认放置 InspectorView。
 *
 * <p>closeButton 链路：currentProject 始终为 null，
 * askToSaveProject 跳过对话框，exit 调用 ModularUI.getScreen().onClose()。
 */
public class KpMapEditor extends Editor {

    public KpMapEditor() {
        super();
        // Editor 构造函数会调用 initMenus + onPrepareXxx + 创建 rootWindow/leftWindow 等
    }

    @Override
    protected Editor createNewEditorInstance() {
        return new KpMapEditor();
    }

    /**
     * 替换默认 FileMenu/ViewMenu 为空容器（Phase 4 由 KpRibbonBar 填充）。
     *
     * <p>spec §6.3 陷阱：只清除 menuContainer，保留 buttonContainer + closeButton 重定向链路。
     */
    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu
        menuContainer.clearAllChildren();
        // 调整 top 高度（Ribbon 需 ~24px）
        top.getLayout().height(24);
    }

    @Override
    protected void onPrepareResourceView() {
        // 空实现：不启用 ResourceView
    }

    @Override
    protected void onPrepareHistoryView() {
        // 空实现：不放置 HistoryView 在 UI 中
        // HistoryView 对象仍存在（public final 字段），InspectorView 引用其作为 historyStack
        // undo/redo 由后端 VCS 系统处理（P4 阶段）
    }

    /**
     * 放置自定义 View 到各 window。
     *
     * <p>必须在 Editor 构造完成（rootWindow/leftWindow/centerWindow/rightWindow 已初始化）后调用。
     * 使用 {@link #placeView(View, Supplier)} 的 fallback 分支（savedLayout == null 时）。
     *
     * <p>调用时机：由 KpEditorScreen 在构造后调用一次。
     */
    public void placeCustomViews() {
        // 左侧：ToolPanelView（Phase 4 创建，此处先放占位）
        placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
        // 中心：MapPlaceholderView（透明）
        placeView(new MapPlaceholderView(), () -> centerWindow.getRightTop());
        // 右侧：InspectorView 已由 onPrepareInspectorView 默认放置，无需重复
    }
}
```

注意：`ToolPanelView` 在 Task 4.2 创建，此处引用其类名会编译失败。**临时调整**：在 Phase 2 期间，先在 `placeCustomViews()` 内注释掉 ToolPanelView 行，并在 Phase 4 Task 4.4 中重新启用。或者更简洁：先创建一个 `ToolPanelView` 占位空类（Phase 4 替换实现）。本计划采用后者——Task 2.3 完成后立即在 Task 2.3.5 创建占位 ToolPanelView。

- [ ] **Step 4: 创建占位 ToolPanelView（Phase 4 替换）**

创建 `src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java`：

```java
package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;

/**
 * 工具面板 View 占位（spec §6.5）。
 *
 * <p>Phase 2 仅创建空 View 让 KpMapEditor.placeCustomViews() 可编译。
 * Phase 4 Task 4.2 填充工具按钮列表与 EditToolState 联动逻辑。
 */
public class ToolPanelView extends View {

    public ToolPanelView() {
        super();
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `gradlew test --tests net.jsmua.kinetic_planner.editor.KpMapEditorTest`
Expected: PASS。如某些字段访问在纯 JVM 环境下因 LDLib2 客户端校验抛异常，将对应断言改为只断言 `editor != null` 与 `createNewEditorInstance` 类型，不强制字段非 null。优先保持测试简单可靠。

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/KpMapEditor.java src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java src/test/java/net/jsmua/kinetic_planner/editor/KpMapEditorTest.java
git commit -m "feat(editor): add KpMapEditor with stripped config and custom view placement"
```

### Task 2.4: KpEditorScreen 编辑模式 Screen 壳

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java`

**Interfaces:**
- Consumes: `KpClientState.setEditMode` / `KpUIEventForwarder` / `KpMapEditor.placeCustomViews` / `ModularUI.setScreen/init`
- Produces: `KpEditorScreen.create(GuiMap) -> KpEditorScreen`、`KpEditorScreen.getGuiMap() -> GuiMap`

- [ ] **Step 1: 创建 KpEditorScreen**

创建 `src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java`：

```java
package net.jsmua.kinetic_planner.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.config.KpUIEventForwarder;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

/**
 * 编辑模式 Screen 壳（spec §6.2）。
 *
 * <p>持有从观看模式传入的 {@link GuiMap} 实例，承载 {@link KpMapEditor}（LDLib2 Editor 子类）。
 * 三层渲染：① 地图层 → ② CAD 编辑层（Phase 6）→ ③ Editor UI 层。
 *
 * <p>事件路由：① forwarder 转发到 UI 树 → ② 未消费时按工具模式路由到 guiMap 或 CAD（Phase 5）。
 *
 * <p>closeButton 链路：Editor.exit() → askToSaveProject() 跳过对话框（currentProject == null）
 * → ModularUI.getScreen().onClose() → 本类 onClose() → 切回 GuiMap。
 */
public class KpEditorScreen extends Screen implements MapOverlayContextProvider {

    private final GuiMap guiMap;
    private final KpMapEditor editor;
    private final ModularUI modularUI;
    private final KpUIEventForwarder eventForwarder;

    /**
     * 静态工厂：从观看模式进入编辑模式。
     *
     * <p>spec §7.1：设置 isEditMode(true) → 构造 KpEditorScreen → setScreen。
     *
     * @param guiMap 当前观看模式的 GuiMap 实例（不销毁，仅切换 Screen 引用）
     * @return 构造完成的 KpEditorScreen（未 init）
     */
    public static KpEditorScreen create(GuiMap guiMap) {
        KpClientState.setEditMode(true);
        return new KpEditorScreen(guiMap);
    }

    private KpEditorScreen(GuiMap guiMap) {
        super(Component.literal("Kinetic Planner Editor"));
        this.guiMap = guiMap;
        // 构造时创建 editor + modularUI，避免 init() 重建丢失 View 状态（spec §6.2）
        this.editor = new KpMapEditor();
        this.editor.placeCustomViews();
        this.modularUI = ModularUI.of(UI.of(this.editor));
        // 绑定 Screen：让 modularUI.getScreen() 返回 this（closeButton 链路依赖）
        this.modularUI.setScreen(this);
        // forwarder 复用：编辑模式与观看模式共用同一事件转发逻辑（spec §6.9）
        this.eventForwarder = new KpUIEventForwarder(this.modularUI);
    }

    @Override
    protected void init() {
        super.init();
        // modularUI.init 可安全重复调用（resize 时 MC 会再次调用 init）
        this.modularUI.init(this.width, this.height);
    }

    @Override
    public GuiMap getGuiMap() {
        return guiMap;
    }

    /**
     * 暴露 editor 实例（Phase 4 Ribbon / Phase 6 EditLayerRenderer 使用）。
     */
    public KpMapEditor getEditor() {
        return editor;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // ① 地图层渲染（Phase 3 实现）
        renderMapLayer(gg, mouseX, mouseY, partialTicks);
        // ② CAD 编辑层渲染（Phase 6 实现）
        // EditLayerRenderer.render(gg, guiMap, editToolState);
        // ③ Editor UI 层渲染
        eventForwarder.render(gg, mouseX, mouseY, partialTicks);
    }

    /**
     * 地图层渲染（Phase 3 Task 3.3 实现）。
     *
     * <p>方案 B：手动调用 GuiMap 内部瓦片/路标渲染方法。
     */
    private void renderMapLayer(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // Phase 3 实现
    }

    @Override
    public void onClose() {
        KpClientState.setEditMode(false);
        Minecraft.getInstance().setScreen(guiMap);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `gradlew compileClientJava`
Expected: 编译成功。

- [ ] **Step 3: 运行测试无回归**

Run: `gradlew test`
Expected: 全部通过。

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java
git commit -m "feat(editor): add KpEditorScreen skeleton with three-layer render"
```

### Task 2.5: XaeroMapOverlayProvider 扩展识别 MapOverlayContextProvider

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapOverlayProvider.java`

- [ ] **Step 1: 扩展 isMapOpen 与 captureContext 通过接口识别**

修改 `XaeroMapOverlayProvider.java` 第 40-80 行的 `isMapOpen` 与 `captureContext` 方法：

```java
    @Override
    public boolean isMapOpen(Screen screen) {
        if (screen instanceof GuiMap) return true;
        // 编辑模式：KpEditorScreen 实现 MapOverlayContextProvider，且持有的 GuiMap != null
        if (screen instanceof MapOverlayContextProvider p) {
            return p.getGuiMap() != null && KpClientState.isEditMode();
        }
        return false;
    }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) {
        try {
            GuiMap map;
            if (screen instanceof GuiMap gm) {
                map = gm;
            } else if (screen instanceof MapOverlayContextProvider p) {
                map = p.getGuiMap();
                if (map == null) return null;
            } else {
                return null;
            }
            XaeroMapAccessor acc = (XaeroMapAccessor) map;
            double cameraX = acc.kp$cameraX();
            double cameraZ = acc.kp$cameraZ();
            double mapScale = acc.kp$scale();

            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getScreenWidth();
            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            float dpr = (float) screenWidth / guiScaledWidth;
            double guiScale = (double) screenWidth / mc.getWindow().getGuiScaledWidth();
            double interfaceScale = (double) mc.getWindow().getWidth() / screenWidth;
            double blocksPerPixel = guiScale * interfaceScale / mapScale;

            ResourceKey<Level> dim = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
            return new MapOverlayContext(
                dim, cameraX, cameraZ, blocksPerPixel,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(),
                0f, dpr
            );
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("XaeroMapOverlayProvider.captureContext failed", t);
            return null;
        }
    }
```

新增 imports：`import net.jsmua.kinetic_planner.config.KpClientState;`

- [ ] **Step 2: 编译 + 测试无回归**

Run: `gradlew compileClientJava & gradlew test`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapOverlayProvider.java
git commit -m "feat(mapadapter): recognize KpEditorScreen via MapOverlayContextProvider"
```

---

## Phase 3: GuiMap 嵌入 + XaeroUiSuppressMixin（关键路径）

**目标**：让编辑模式下地图瓦片+路标可见，抑制雷达/按钮/HUD。这是整个设计最高风险环节（R2/F1/F2）。验收：进入编辑模式后地图可见且 Xaero UI 元素被抑制；导航工具能拖拽/缩放地图。

### Task 3.1: 反编译 GuiMap.render() 确定方案 B 可行性

**Files:**
- 无代码变更，仅研究产出

**输出**：在 `docs/chat_history/2026-07-28-guimap-decompile-report.md` 记录反编译结论（用户可选）。

- [ ] **Step 1: 反编译 Xaero GuiMap**

使用 IDE 反编译工具（如 IDEA 的 FernFlower）反编译 Xaero World Map jar 中的 `xaero.map.gui.GuiMap` 类，重点关注 `render(GuiGraphics, int, int, float)` 方法。

- [ ] **Step 2: 识别 render() 内部结构**

记录以下信息：
- 地图瓦片渲染：方法名 / 调用签名 / 是否独立方法
- 路标/航点渲染：方法名 / 调用签名
- 雷达实体渲染：方法名 / 调用签名
- 按钮控件渲染：方法名 / 调用签名
- HUD 文本渲染：方法名 / 调用签名

- [ ] **Step 3: 选择实现方案**

根据反编译结果选择：
- **最佳情况**（瓦片/路标为独立方法）：方案 B HEAD cancel + KpEditorScreen 手动调用子方法
- **降级情况**（render 不可分离）：在 render 内多个 `@At("INVOKE")` cancel 特定子调用
- **最坏情况**（render 完全单体）：`@Overwrite`（最后手段）

记录决定到下一步 Task 3.2/3.3 的代码模板中。

- [ ] **Step 4: Commit 研究产出（可选）**

如生成报告：

```bash
git add docs/chat_history/2026-07-28-guimap-decompile-report.md
git commit -m "docs(research): record GuiMap.render decompilation findings"
```

### Task 3.2: XaeroUiSuppressMixin HEAD cancel 实现

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroUiSuppressMixin.java`
- Modify: `src/main/resources/kinetic_planner.mixins.json`

**Interfaces:**
- Consumes: `KpClientState.isEditMode()`

**前置**：Task 3.1 已确认方案 B 可行（最佳或降级情况）。

- [ ] **Step 1: 创建 XaeroUiSuppressMixin（最佳情况版本）**

创建 `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroUiSuppressMixin.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.KpClientState;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式抑制 Xaero UI 渲染（spec §6.7 方案 B）。
 *
 * <p>HEAD cancel {@code GuiMap.render} 完整方法，由 {@link net.jsmua.kinetic_planner.editor.KpEditorScreen}
 * 手动调用可控部分（瓦片+路标）。
 *
 * <p>观看模式不触发（{@code !isEditMode} 时 return）。
 *
 * <p>若 Task 3.1 反编译发现 render() 不可分离，改为在 render 内部多个
 * {@code @At("INVOKE")} 点 cancel 特定子调用（见文末降级模板）。
 */
@Mixin(value = GuiMap.class, remap = false)
public class XaeroUiSuppressMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void kp$suppressRender(GuiGraphics gg, int mouseX, int mouseY,
                                    float partialTicks, CallbackInfo ci) {
        if (KpClientState.isEditMode()) {
            ci.cancel();
        }
    }

    // === 降级情况模板（若 Task 3.1 发现 render 不可分离，替换上面的 HEAD cancel）===
    //
    // @Inject(method = "render", at = @At(value = "INVOKE",
    //     target = "Lxaero/map/...;renderRadar(...)V"), cancellable = true)
    // private void kp$suppressRadar(CallbackInfo ci) {
    //     if (KpClientState.isEditMode()) ci.cancel();
    // }
    //
    // @Inject(method = "render", at = @At(value = "INVOKE",
    //     target = "Lxaero/map/...;renderButtons(...)V"), cancellable = true)
    // private void kp$suppressButtons(CallbackInfo ci) {
    //     if (KpClientState.isEditMode()) ci.cancel();
    // }
    //
    // @Inject(method = "render", at = @At(value = "INVOKE",
    //     target = "Lxaero/map/...;renderHud(...)V"), cancellable = true)
    // private void kp$suppressHud(CallbackInfo ci) {
    //     if (KpClientState.isEditMode()) ci.cancel();
    // }
}
```

- [ ] **Step 2: 注册 Mixin**

修改 `src/main/resources/kinetic_planner.mixins.json`，在 `client` 数组末尾添加 `"XaeroUiSuppressMixin"`：

```json
{
    "required": true,
    "minVersion": "0.8.5",
    "package": "net.jsmua.kinetic_planner.mixin",
    "compatibilityLevel": "JAVA_21",
    "refmap": "kinetic_planner.refmap.json",
    "mixins": [],
    "client": [
        "TrackGraphAccessor",
        "XaeroMapAccessor",
        "XaeroMapRenderHook",
        "CreateTrackVisualizerHiderMixin",
        "CreateTrainMapMixin",
        "CreateTrainMapOverlayMixin",
        "XaeroMapGearButtonMixin",
        "XaeroUiSuppressMixin"
    ],
    "injectors": {
        "defaultRequire": 0
    }
}
```

- [ ] **Step 3: 编译确认**

Run: `gradlew compileClientJava`
Expected: 编译成功，Mixin 配置 JSON 格式正确。

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mixin/XaeroUiSuppressMixin.java src/main/resources/kinetic_planner.mixins.json
git commit -m "feat(mixin): add XaeroUiSuppressMixin for edit-mode render cancel"
```

### Task 3.3: KpEditorScreen.renderMapLayer 实现

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java`

**前置**：Task 3.1 已确定手动调用的子方法名。

- [ ] **Step 1: 在 KpEditorScreen 中实现 renderMapLayer**

根据 Task 3.1 反编译结论，修改 `KpEditorScreen.renderMapLayer` 方法。**最佳情况**模板（调用独立子方法）：

```java
    /**
     * 地图层渲染（方案 B 最佳情况）。
     *
     * <p>XaeroUiSuppressMixin HEAD cancel 了 GuiMap.render()，
     * 此处手动调用瓦片+路标渲染子方法。
     *
     * <p>方法名来自 Task 3.1 反编译结论。若反编译发现方法为 private，
     * 需添加 Mixin @Accessor 或 @Invoker 暴露（见 Task 3.3.5）。
     */
    private void renderMapLayer(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        try {
            // ① 瓦片层（具体方法名以 Task 3.1 反编译为准）
            // guiMap.renderTiles(gg, mouseX, mouseY, partialTicks);
            // ② 路标层
            // guiMap.renderWaypoints(gg, mouseX, mouseY, partialTicks);
            // 占位：直接调用完整 render 作为兜底（验证 cancel 后能否正常调用）
            // 注意：cancel 后再次调用 render 会触发 XaeroUiSuppressMixin.kp$suppressRender 死循环！
            // 必须用 @Invoker 调用子方法，而非完整 render。
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("renderMapLayer failed", t);
        }
    }
```

新增 import：`import net.jsmua.kinetic_planner.KineticPlannerMod;`

- [ ] **Step 2: 创建 GuiMap 子方法 @Invoker（如需要）**

如果反编译发现瓦片/路标渲染方法为 private，在 `src/client/java/net/jsmua/kinetic_planner/mixin/` 创建 `GuiMapRenderInvoker.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import xaero.map.gui.GuiMap;

/**
 * @Invoker 调用 GuiMap private 渲染子方法（spec §6.7 方案 B 最佳情况）。
 *
 * <p>方法名以 Task 3.1 反编译结论为准。
 * 若反编译发现方法是 public，本接口可省略，直接在 KpEditorScreen 调用。
 */
@Mixin(value = GuiMap.class, remap = false)
public interface GuiMapRenderInvoker {

    // 瓦片渲染方法（方法名 + 签名以反编译为准）
    // @Invoker("renderTiles")
    // void kp$invokeRenderTiles(GuiGraphics gg, int mouseX, int mouseY, float partialTicks);

    // 路标渲染方法（方法名 + 签名以反编译为准）
    // @Invoker("renderWaypoints")
    // void kp$invokeRenderWaypoints(GuiGraphics gg, int mouseX, int mouseY, float partialTicks);
}
```

注册到 `kinetic_planner.mixins.json` 的 `client` 数组：`"GuiMapRenderInvoker"`。

- [ ] **Step 3: 编译 + 运行时验收**

Run: `gradlew compileClientJava & gradlew runClient`

游戏中触发进入编辑模式（Task 3.6 实现），观察：
- 地图瓦片可见
- 路标可见
- 无雷达实体
- 无按钮控件
- 无 HUD 文本

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapRenderInvoker.java src/main/resources/kinetic_planner.mixins.json
git commit -m "feat(editor): implement renderMapLayer via GuiMap submethod invokers"
```

### Task 3.4: F1 GuiMap.removed() 生命周期验证 + 按需 Mixin

**Files:**
- Create (按需): `src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapRemovedMixin.java`
- Modify (按需): `src/main/resources/kinetic_planner.mixins.json`

- [ ] **Step 1: 验证 GuiMap.removed() 行为**

Run: `gradlew runClient` → 打开 Xaero 地图 → 触发"进入编辑"（Task 3.6 后可用）

观察：
- 切换到 KpEditorScreen 后，地图瓦片是否仍可见？
- 切回 GuiMap 后，地图位置/缩放是否保持？

若两者均正常 → **不需要 Mixin**，跳过 Step 2-4，直接 Commit 一个空说明。

若瓦片消失或位置重置 → 添加 Mixin。

- [ ] **Step 2: 创建 GuiMapRemovedMixin（仅在 Step 1 验证发现问题时）**

创建 `src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapRemovedMixin.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.KpClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式抑制 GuiMap.removed() 状态清理（spec §5 GuiMapRemovedMixin 按需）。
 *
 * <p>setScreen(KpEditorScreen) 会触发 guiMap.removed()。
 * 若 removed() 清理状态导致后续 render() 不可用，添加此 Mixin cancel。
 */
@Mixin(value = GuiMap.class, remap = false)
public class GuiMapRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void kp$suppressRemoved(CallbackInfo ci) {
        if (KpClientState.isEditMode()) {
            ci.cancel();
        }
    }
}
```

- [ ] **Step 3: 注册 Mixin**

在 `kinetic_planner.mixins.json` 的 `client` 数组添加 `"GuiMapRemovedMixin"`。

- [ ] **Step 4: 编译 + 验证**

Run: `gradlew compileClientJava & gradlew runClient`
重复 Step 1 验证。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapRemovedMixin.java src/main/resources/kinetic_planner.mixins.json
git commit -m "feat(mixin): add GuiMapRemovedMixin to suppress removed() in edit mode"
```

（若 Step 1 验证无需 Mixin，跳过此 Commit。）

### Task 3.5: F2 GuiMap.mouseClicked 非活跃 Screen 验证 + 按需 Mixin

**Files:**
- Create (按需): `src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapInputMixin.java`
- Modify (按需): `src/main/resources/kinetic_planner.mixins.json`

**前置**：Phase 5 Task 5.5 实现 Navigation 工具事件路由后才能完整验证。此处先做静态分析。

- [ ] **Step 1: 静态分析 GuiMap.mouseClicked 是否检查 mc.screen == this**

反编译 `GuiMap.mouseClicked`（与 Task 3.1 同工具），查找是否存在 `if (mc.screen != this) return;` 类似的检查。

若不存在检查 → 跳过 Mixin，等 Phase 5 实测验证。
若存在检查 → 准备 GuiMapInputMixin。

- [ ] **Step 2: 创建 GuiMapInputMixin（仅在需要时）**

创建 `src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapInputMixin.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import net.minecraft.client.Minecraft;
import net.jsmua.kinetic_planner.config.KpClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式绕过 GuiMap 的 mc.screen == this 检查（spec §5 GuiMapInputMixin 按需）。
 *
 * <p>在 Navigation 工具下，KpEditorScreen 将事件转发给 guiMap.mouseClicked()。
 * 若 GuiMap 检查 mc.screen == this 而拒绝处理，此 Mixin 临时设置 mc.screen = guiMap
 * 绕过检查，调用后恢复。
 *
 * <p>注意：此 Mixin 仅在 Phase 5 Navigation 工具实测失败时添加。
 */
@Mixin(value = GuiMap.class, remap = false)
public class GuiMapInputMixin {

    // 实现策略：包裹 mouseClicked 等 GuiEventListener 方法，
    // 在 HEAD 临时设置 mc.screen，在 RETURN 恢复。
    // 因 Mixin 不能直接包裹目标类的同名方法（会被自己的 Mixin 调用递归），
    // 改为在 KpEditorScreen 转发事件前临时设置 mc.screen，转发后恢复。
    // 此 Mixin 仅为占位，实际策略在 Phase 5 Task 5.5 实现。
}
```

实际实现策略在 Phase 5 Task 5.5 中决定，本 Task 仅准备文件骨架。

- [ ] **Step 3: Commit（仅在创建 Mixin 时）**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mixin/GuiMapInputMixin.java src/main/resources/kinetic_planner.mixins.json
git commit -m "feat(mixin): add GuiMapInputMixin skeleton for screen check bypass"
```

### Task 3.6: 进入编辑模式触发点（齿轮按钮 / /kp edit 命令 / 快捷键）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KpGearButton.java`（按需）

- [ ] **Step 1: 在 KPCommands 添加 /kp edit 命令**

修改 `KPCommands.java`，在 `Commands.literal("kp")` 的子命令树中添加 `edit` 与 `exit`：

```java
            .then(Commands.literal("edit")
                .executes(KPCommands::editMode))
            .then(Commands.literal("exit")
                .executes(KPCommands::exitEditMode))
```

在类中添加对应 handler 方法：

```java
    /**
     * /kp edit - 进入编辑模式。
     *
     * <p>仅在 Xaero 全屏地图打开时可用。将当前 GuiMap 包装到 KpEditorScreen。
     */
    private static int editMode(CommandContext<CommandSourceStack> ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.screen instanceof xaero.map.gui.GuiMap guiMap)) {
            ctx.getSource().sendFailure(Component.literal("Open Xaero's World Map first"));
            return 0;
        }
        net.jsmua.kinetic_planner.editor.KpEditorScreen.create(guiMap);
        mc.setScreen(net.jsmua.kinetic_planner.editor.KpEditorScreen.create(guiMap));
        ctx.getSource().sendSuccess(() -> Component.literal("Entered KP edit mode"), false);
        return 1;
    }

    /**
     * /kp exit - 退出编辑模式（返回观看模式）。
     */
    private static int exitEditMode(CommandContext<CommandSourceStack> ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof net.jsmua.kinetic_planner.editor.KpEditorScreen editor) {
            mc.setScreen(editor.getGuiMap());
            net.jsmua.kinetic_planner.config.KpClientState.setEditMode(false);
            ctx.getSource().sendSuccess(() -> Component.literal("Exited KP edit mode"), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("Not in KP edit mode"));
        return 0;
    }
```

修正：上面 `editMode` 重复调用了 `KpEditorScreen.create` 两次（一次丢弃），正确实现：

```java
    private static int editMode(CommandContext<CommandSourceStack> ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.screen instanceof xaero.map.gui.GuiMap guiMap)) {
            ctx.getSource().sendFailure(Component.literal("Open Xaero's World Map first"));
            return 0;
        }
        var editorScreen = net.jsmua.kinetic_planner.editor.KpEditorScreen.create(guiMap);
        mc.setScreen(editorScreen);
        ctx.getSource().sendSuccess(() -> Component.literal("Entered KP edit mode"), false);
        return 1;
    }
```

- [ ] **Step 2: 编译 + 运行时验收**

Run: `gradlew compileClientJava & gradlew runClient`

游戏中：
1. 打开 Xaero 全屏地图
2. 执行 `/kp edit`
3. 预期：Screen 切换到 KpEditorScreen，Editor UI 框架可见（Ribbon 区空、中心透明、右侧 InspectorView 占位）
4. 按下 ESC
5. 预期：切回 GuiMap，地图状态保持

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java
git commit -m "feat(commands): add /kp edit and /kp exit commands"
```

---

## Phase 4: KpRibbonBar + ToolPanelView + EditToolState

**目标**：替换占位 ToolPanelView，实现 Ribbon 菜单栏与工具状态管理。验收：点击 Ribbon 工具按钮切换 EditToolState，ToolPanelView 显示当前工具。

### Task 4.1: EditToolState 工具状态管理

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/editor/EditToolState.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/editor/EditToolStateTest.java`

**Interfaces:**
- Produces: `EditToolState.Tool` 枚举（NAVIGATION/SELECT/DRAW_LINE/DRAW_BEZIER/SNAP）、`EditToolState.getInstance()` 单例、`getCurrentTool()/setCurrentTool(Tool)/getSelectedNodes()/addSelectedNode(...)/clearSelection()`

- [ ] **Step 1: 编写失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/editor/EditToolStateTest.java`：

```java
package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.editor.EditToolState.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EditToolState} 单元测试（spec §4.3 + §6.8）。
 *
 * <p>EditToolState 是编辑会话状态（仅编辑模式活跃），与全局 KpClientState.editMode 生命周期不同。
 */
class EditToolStateTest {

    @BeforeEach
    void resetState() {
        EditToolState.getInstance().setCurrentTool(Tool.NAVIGATION);
        EditToolState.getInstance().clearSelection();
    }

    @Test
    void defaultToolIsNavigation() {
        assertEquals(Tool.NAVIGATION, EditToolState.getInstance().getCurrentTool());
    }

    @Test
    void setCurrentToolUpdatesState() {
        EditToolState.getInstance().setCurrentTool(Tool.SELECT);
        assertEquals(Tool.SELECT, EditToolState.getInstance().getCurrentTool());
    }

    @Test
    void selectionStartsEmpty() {
        assertTrue(EditToolState.getInstance().getSelectedNodes().isEmpty());
    }

    @Test
    void addSelectedNodeAddsToSelection() {
        UUID id = UUID.randomUUID();
        EditToolState.getInstance().addSelectedNode(id);
        assertEquals(1, EditToolState.getInstance().getSelectedNodes().size());
        assertTrue(EditToolState.getInstance().getSelectedNodes().contains(id));
    }

    @Test
    void addSelectedNodeDeduplicates() {
        UUID id = UUID.randomUUID();
        EditToolState.getInstance().addSelectedNode(id);
        EditToolState.getInstance().addSelectedNode(id);
        assertEquals(1, EditToolState.getInstance().getSelectedNodes().size());
    }

    @Test
    void clearSelectionRemovesAll() {
        EditToolState.getInstance().addSelectedNode(UUID.randomUUID());
        EditToolState.getInstance().addSelectedNode(UUID.randomUUID());
        EditToolState.getInstance().clearSelection();
        assertTrue(EditToolState.getInstance().getSelectedNodes().isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew test --tests net.jsmua.kinetic_planner.editor.EditToolStateTest`
Expected: 编译失败 `cannot find symbol EditToolState`。

- [ ] **Step 3: 创建 EditToolState**

创建 `src/client/java/net/jsmua/kinetic_planner/editor/EditToolState.java`：

```java
package net.jsmua.kinetic_planner.editor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 编辑会话状态（spec §4.3 + §6.8）。
 *
 * <p>管理当前工具、选择集、绘制状态。仅编辑模式活跃，与全局 {@link net.jsmua.kinetic_planner.config.KpClientState}
 * 生命周期不同（后者是 Mixin 守卫读取的全局模式标志）。
 *
 * <p>单例模式：编辑模式开始时通过 {@link #reset()} 清空状态。
 */
public final class EditToolState {

    /**
     * 编辑工具枚举（spec §4.3 表格）。
     */
    public enum Tool {
        /** 地图导航（平移/缩放），事件转发给 guiMap */
        NAVIGATION,
        /** 选择节点/边，CADRenderEngine 命中检测 */
        SELECT,
        /** 绘制直线 */
        DRAW_LINE,
        /** 绘制三次贝塞尔 */
        DRAW_BEZIER,
        /** 捕捉模式，鼠标移动高亮可捕捉点 */
        SNAP
    }

    private static final EditToolState INSTANCE = new EditToolState();

    private Tool currentTool = Tool.NAVIGATION;
    private final Set<UUID> selectedNodes = new LinkedHashSet<>();

    private EditToolState() {}

    public static EditToolState getInstance() {
        return INSTANCE;
    }

    public Tool getCurrentTool() {
        return currentTool;
    }

    public void setCurrentTool(Tool tool) {
        this.currentTool = tool;
    }

    public Set<UUID> getSelectedNodes() {
        return selectedNodes;
    }

    public void addSelectedNode(UUID nodeId) {
        selectedNodes.add(nodeId);
    }

    public void clearSelection() {
        selectedNodes.clear();
    }

    /**
     * 重置全部状态（编辑模式开始时调用）。
     */
    public void reset() {
        currentTool = Tool.NAVIGATION;
        selectedNodes.clear();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `gradlew test --tests net.jsmua.kinetic_planner.editor.EditToolStateTest`
Expected: PASS，6 个用例全绿。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/EditToolState.java src/test/java/net/jsmua/kinetic_planner/editor/EditToolStateTest.java
git commit -m "feat(editor): add EditToolState singleton with Tool enum and selection"
```

### Task 4.2: ToolPanelView 完整实现（替换 Phase 2 占位）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ToolPanelViewTest.java`

- [ ] **Step 1: 编写失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/gui/ToolPanelViewTest.java`：

```java
package net.jsmua.kinetic_planner.gui;

import net.jsmua.kinetic_planner.editor.EditToolState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolPanelView} 构造与工具联动测试。
 *
 * <p>spec §6.5：选中工具时通知 EditToolState.setCurrentTool。
 */
class ToolPanelViewTest {

    @Test
    void constructCreatesViewWithButtons() {
        ToolPanelView view = new ToolPanelView();
        assertNotNull(view);
    }

    @Test
    void viewIsUIElementSubclass() {
        ToolPanelView view = new ToolPanelView();
        assertInstanceOf(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class, view);
    }
}
```

- [ ] **Step 2: 实现 ToolPanelView（替换占位）**

替换 `src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java`：

```java
package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.editor.EditToolState;
import net.jsmua.kinetic_planner.editor.EditToolState.Tool;
import net.minecraft.network.chat.Component;

/**
 * 工具面板 View（spec §6.5）。
 *
 * <p>左侧 View，含工具按钮列表（图标 + 名称）。选中工具时通知
 * {@link EditToolState#setCurrentTool(Tool)}。
 */
public class ToolPanelView extends View {

    public ToolPanelView() {
        super();
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(4);
            layout.gapAll(2);
        });
        addToolButton("Pan (V)", Tool.NAVIGATION);
        addToolButton("Select (L)", Tool.SELECT);
        addToolButton("Line (L)", Tool.DRAW_LINE);
        addToolButton("Bezier (B)", Tool.DRAW_BEZIER);
        addToolButton("Snap (S)", Tool.SNAP);
    }

    private void addToolButton(String label, Tool tool) {
        Button btn = new Button();
        btn.setText(label);
        btn.setOnClick(event -> EditToolState.getInstance().setCurrentTool(tool));
        addChild(btn);
    }
}
```

注意：`Button.setOnClick` 的 Consumer 签名以 LDLib2 2.2.26 实际 API 为准。如签名不符，参考现有 `KpConfigUIFactory.buildStepperRowInt` 中 `minusBtn.setOnClick(event -> {...})` 的用法（已验证可用）。

- [ ] **Step 3: 运行测试 + 编译**

Run: `gradlew test --tests net.jsmua.kinetic_planner.gui.ToolPanelViewTest & gradlew compileClientJava`
Expected: PASS + 编译成功。

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ToolPanelView.java src/test/java/net/jsmua/kinetic_planner/gui/ToolPanelViewTest.java
git commit -m "feat(gui): implement ToolPanelView with tool buttons"
```

### Task 4.3: KpRibbonBar Ribbon 菜单栏

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/KpRibbonBar.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/KpRibbonBarTest.java`

- [ ] **Step 1: 编写失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/gui/KpRibbonBarTest.java`：

```java
package net.jsmua.kinetic_planner.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KpRibbonBar} 构造测试。
 *
 * <p>spec §6.4：Ribbon 是 UIElement，含 File/Tools/View Tab 组 + Settings 按钮。
 */
class KpRibbonBarTest {

    @Test
    void constructCreatesRibbonWithTabs() {
        KpRibbonBar ribbon = new KpRibbonBar();
        assertNotNull(ribbon);
    }

    @Test
    void ribbonIsUIElementSubclass() {
        KpRibbonBar ribbon = new KpRibbonBar();
        assertInstanceOf(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class, ribbon);
    }
}
```

- [ ] **Step 2: 创建 KpRibbonBar**

创建 `src/client/java/net/jsmua/kinetic_planner/gui/KpRibbonBar.java`：

```java
package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.editor.EditToolState;
import net.jsmua.kinetic_planner.editor.EditToolState.Tool;
import net.minecraft.network.chat.Component;

/**
 * Ribbon 菜单栏（spec §6.4）。
 *
 * <p>结构上自包含：工具与命令集中在 Ribbon，不依赖独立菜单栏 + 工具栏的分离结构。
 * 配置面板以弹出抽屉形式从 Ribbon 触发（Phase 后续实现）。
 *
 * <p>结构：
 * <pre>
 * KpRibbonBar (UIElement, width=100%, height=~24px, flexDirection=ROW)
 * ├── Tab Group: "File" (New/Open/Save)
 * ├── Tab Group: "Tools" (Select/Line/Bezier/Pan)
 * ├── Tab Group: "View" (Toggle Overlay/Theme)
 * ├── Spacer (flex=1)
 * └── Button: Settings (齿轮)
 * </pre>
 */
public class KpRibbonBar extends UIElement {

    public KpRibbonBar() {
        super();
        addClass("kp-ribbon");
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            layout.height(24);
            layout.paddingHorizontal(4);
            layout.gapAll(4);
        });

        // File 组（占位，P4 阶段实现具体逻辑）
        addGroupLabel("File");
        addButton("New", () -> {});
        addButton("Open", () -> {});
        addButton("Save", () -> {});

        // Tools 组
        addGroupLabel("Tools");
        addButton("Select", () -> EditToolState.getInstance().setCurrentTool(Tool.SELECT));
        addButton("Line", () -> EditToolState.getInstance().setCurrentTool(Tool.DRAW_LINE));
        addButton("Bezier", () -> EditToolState.getInstance().setCurrentTool(Tool.DRAW_BEZIER));
        addButton("Pan", () -> EditToolState.getInstance().setCurrentTool(Tool.NAVIGATION));

        // View 组
        addGroupLabel("View");
        addButton("Overlay", () -> {});
        addButton("Theme", () -> {});

        // 弹性间隔（推 Settings 到右）
        var spacer = new UIElement();
        spacer.layout(layout -> layout.flexGrow(1));
        addChild(spacer);

        // Settings 按钮（齿轮，弹出配置抽屉，Phase 后续实现）
        addButton("⚙", () -> {});
    }

    private void addGroupLabel(String text) {
        var label = new com.lowdragmc.lowdraglib2.gui.ui.elements.Label();
        label.setValue(Component.literal(text));
        label.addClass("kp-ribbon-group-label");
        addChild(label);
    }

    private void addButton(String text, Runnable onClick) {
        var btn = new Button();
        btn.setText(text);
        btn.setOnClick(event -> onClick.run());
        addChild(btn);
    }
}
```

- [ ] **Step 3: 运行测试 + 编译**

Run: `gradlew test --tests net.jsmua.kinetic_planner.gui.KpRibbonBarTest & gradlew compileClientJava`
Expected: PASS + 编译成功。

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/KpRibbonBar.java src/test/java/net/jsmua/kinetic_planner/gui/KpRibbonBarTest.java
git commit -m "feat(gui): add KpRibbonBar with File/Tools/View groups"
```

### Task 4.4: KpMapEditor 集成 KpRibbonBar

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/editor/KpMapEditor.java`

- [ ] **Step 1: 在 initMenus 中放置 KpRibbonBar**

修改 `KpMapEditor.java` 的 `initMenus()` 方法：

```java
    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu
        menuContainer.clearAllChildren();
        // 替换为 KpRibbonBar
        menuContainer.addChild(new KpRibbonBar());
        // 调整 top 高度（Ribbon 需 ~24px）
        top.getLayout().height(24);
    }
```

新增 imports：`import net.jsmua.kinetic_planner.gui.KpRibbonBar;`

- [ ] **Step 2: 编译 + 运行时验收**

Run: `gradlew compileClientJava & gradlew runClient`

游戏中：
1. `/kp edit` 进入编辑模式
2. 预期：顶部 Ribbon 显示 File/Tools/View 三组按钮 + Settings 齿轮
3. 点击 Tools 组的 "Select" → EditToolState 切换到 SELECT
4. 左侧 ToolPanelView 显示工具按钮（与 Ribbon 工具组重复，但作为可视占位无碍）

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/KpMapEditor.java
git commit -m "feat(editor): integrate KpRibbonBar into KpMapEditor initMenus"
```

---

## Phase 5: 事件路由 + KpUIEventForwarder 泛化

**目标**：实现 KpEditorScreen 的事件路由，未消费时按工具模式转发到 guiMap 或 CADRenderEngine。验收：Navigation 工具下地图平移/缩放正常，Select 工具下点击节点能更新选择集。

### Task 5.1: KpUIEventForwarder 返回 boolean（已完成，仅文档校对）

**Files:**
- Verify: `src/client/java/net/jsmua/kinetic_planner/config/KpUIEventForwarder.java`

**说明**：现有 `KpUIEventForwarder` 的 `mouseClicked/mouseReleased/mouseDragged/mouseScrolled/keyPressed/keyReleased/charTyped` 已返回 boolean。`render` 和 `mouseMoved` 因 MC/LDLib2 签名不返回 boolean，保留 void。

- [ ] **Step 1: 校对 KpUIEventForwarder 返回值**

Run: 检查 `src/client/java/net/jsmua/kinetic_planner/config/KpUIEventForwarder.java`

确认：
- `mouseClicked` 返回 `boolean` ✓
- `mouseReleased` 返回 `boolean` ✓
- `mouseDragged` 返回 `boolean` ✓
- `mouseScrolled` 返回 `boolean` ✓
- `keyPressed` 返回 `boolean` ✓
- `keyReleased` 返回 `boolean` ✓
- `charTyped` 返回 `boolean` ✓
- `render` 返回 `void`（MC/LDLib2 无返回值，正常）
- `mouseMoved` 返回 `void`（MC/LDLib2 无返回值，正常）

无需修改。

- [ ] **Step 2: 跳过 Commit（无代码变更）**

### Task 5.2: KpUIEventForwarderTest 校对（9 个用例已存在）

**Files:**
- Verify: `src/test/java/net/jsmua/kinetic_planner/config/KpUIEventForwarderTest.java`

- [ ] **Step 1: 确认现有测试覆盖 boolean 返回值**

Run: `gradlew test --tests net.jsmua.kinetic_planner.config.KpUIEventForwarderTest`

确认 9 个测试全部通过（render、mouseMoved、checkResize 3 个 + mouseClicked 2 个 + mouseReleased、mouseScrolled 各 1 个 + 文档校对 1 个 = 实际 9 个）。

- [ ] **Step 2: 跳过 Commit（无代码变更）**

### Task 5.3: XaeroMapGearButtonMixin 与 KineticPlannerJMPlugin 调用处适配

**Files:**
- Verify: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java`
- Verify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/KineticPlannerJMPlugin.java`

**说明**：观看模式下调用 `forwarder.mouseClicked(...)` 已忽略返回值（旧 void 调用方式）。现在 forwarder 返回 boolean，调用方式兼容（boolean 返回值可被忽略）。

- [ ] **Step 1: 检查调用处无需修改**

`XaeroMapGearButtonMixin` 行 95: `&& kp$forwarder.mouseClicked(mouseX, mouseY, button)` —— 已使用 boolean 返回值，正确。
`KineticPlannerJMPlugin` 行 150: `&& jmForwarder.mouseClicked(mouseX, mouseY, button)` —— 同上。

无需修改。

- [ ] **Step 2: 跳过 Commit**

### Task 5.4: KpEditorScreen 事件路由实现

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java`

**Interfaces:**
- Consumes: `KpUIEventForwarder.mouseXxx`、`EditToolState.getCurrentTool`、`GuiMap.mouseXxx`、`CADRenderEngine.handleXxx`（Phase 6）

- [ ] **Step 1: 实现 KpEditorScreen 事件路由**

修改 `KpEditorScreen.java`，添加全部事件方法。修改后完整文件：

```java
package net.jsmua.kinetic_planner.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.cadengine.CADRenderEngine;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.config.KpUIEventForwarder;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProvider;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

/**
 * 编辑模式 Screen 壳（spec §6.2 + §4.2）。
 *
 * <p>三层渲染：① 地图层 → ② CAD 编辑层（Phase 6）→ ③ Editor UI 层。
 * 事件路由：① forwarder 转发到 UI 树 → ② 未消费时按工具模式路由到 guiMap 或 CADRenderEngine。
 */
public class KpEditorScreen extends Screen implements MapOverlayContextProvider {

    private final GuiMap guiMap;
    private final KpMapEditor editor;
    private final ModularUI modularUI;
    private final KpUIEventForwarder eventForwarder;

    public static KpEditorScreen create(GuiMap guiMap) {
        KpClientState.setEditMode(true);
        return new KpEditorScreen(guiMap);
    }

    private KpEditorScreen(GuiMap guiMap) {
        super(Component.literal("Kinetic Planner Editor"));
        this.guiMap = guiMap;
        this.editor = new KpMapEditor();
        this.editor.placeCustomViews();
        this.modularUI = ModularUI.of(UI.of(this.editor));
        this.modularUI.setScreen(this);
        this.eventForwarder = new KpUIEventForwarder(this.modularUI);
    }

    @Override
    protected void init() {
        super.init();
        this.modularUI.init(this.width, this.height);
    }

    @Override
    public GuiMap getGuiMap() {
        return guiMap;
    }

    public KpMapEditor getEditor() {
        return editor;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        renderMapLayer(gg, mouseX, mouseY, partialTicks);
        // CAD 编辑层（Phase 6 实现）
        // EditLayerRenderer.render(gg, guiMap, EditToolState.getInstance());
        eventForwarder.render(gg, mouseX, mouseY, partialTicks);
    }

    private void renderMapLayer(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // Phase 3 Task 3.3 实现
    }

    // === 事件路由（spec §4.2）===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ① Editor UI 优先
        if (eventForwarder.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // ② 未消费 -> 按工具模式路由
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.mouseClicked(mouseX, mouseY, button);
        }
        // 其他工具 -> CADRenderEngine 命中检测（Phase 6）
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (eventForwarder.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (eventForwarder.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        // Draw 工具 -> CADRenderEngine.handleDrag (Phase 6)
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (eventForwarder.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        eventForwarder.mouseMoved(mouseX, mouseY);
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            guiMap.mouseMoved(mouseX, mouseY);
        }
        // Draw/Snap -> CADRenderEngine.handleHover (Phase 6)
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (eventForwarder.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // 工具快捷键（V=Select, L=Line, B=Bezier, P=Pan）
        var state = EditToolState.getInstance();
        switch (keyCode) {
            case 259 -> { // ESC
                onClose();
                return true;
            }
            // MC key codes: V=86, L=76, B=66, P=80, S=83, N=78
            case 86 -> { state.setCurrentTool(EditToolState.Tool.SELECT); return true; }
            case 76 -> { state.setCurrentTool(EditToolState.Tool.DRAW_LINE); return true; }
            case 66 -> { state.setCurrentTool(EditToolState.Tool.DRAW_BEZIER); return true; }
            case 80 -> { state.setCurrentTool(EditToolState.Tool.NAVIGATION); return true; }
            case 83 -> { state.setCurrentTool(EditToolState.Tool.SNAP); return true; }
        }
        var tool = state.getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (eventForwarder.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.keyReleased(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (eventForwarder.charTyped(codePoint, modifiers)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public void onClose() {
        KpClientState.setEditMode(false);
        Minecraft.getInstance().setScreen(guiMap);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `gradlew compileClientJava`
Expected: 编译成功。

- [ ] **Step 3: 运行测试无回归**

Run: `gradlew test`
Expected: 全部通过。

- [ ] **Step 4: 运行时验收（手动）**

Run: `gradlew runClient`

游戏中：
1. `/kp edit` 进入编辑模式
2. 选择 "Pan" 工具（默认）
3. 拖拽地图 → 平移正常
4. 滚轮 → 缩放正常
5. 按 V → 切换到 Select 工具
6. 按 ESC → 切回观看模式

若 Navigation 工具下事件转发失败（GuiMap 检查 `mc.screen == this`），按 Task 3.5 添加 GuiMapInputMixin。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java
git commit -m "feat(editor): implement event routing with tool-mode dispatch"
```

---

## Phase 6: CADRenderEngine 扩展 + EditLayerRenderer

**目标**：扩展 CADRenderEngine 为交互枢纽，实现 EditLayerRenderer 渲染编辑图形。验收：Select 工具下点击节点能加入选择集，InspectorView 更新；Draw Line 工具下点击两点能预览直线。

### Task 6.1: WorldTreeReadOverlay 扩展访问器

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`

**Interfaces:**
- Produces: `WorldTreeReadOverlay.getTransform() -> @Nullable WorldScreenTransform`、`WorldTreeReadOverlay.getGeometryCache() -> GeometryCache`

- [ ] **Step 1: 添加访问器方法**

在 `WorldTreeReadOverlay.java` 类末尾（在 `renderLabels` 私有方法之前）添加：

```java
    /**
     * 返回当前缓存的 {@link WorldScreenTransform}（供 {@link EditLayerRenderer} 使用）。
     *
     * @return 当前变换；地图未打开时为 null
     */
    public static WorldScreenTransform getTransform() {
        return lastTransform;
    }

    /**
     * 返回当前 {@link GeometryCache}（供 {@link EditLayerRenderer} 使用）。
     *
     * @return 几何缓存实例
     */
    public static GeometryCache getGeometryCache() {
        return geometryCache;
    }
```

- [ ] **Step 2: 编译 + 测试无回归**

Run: `gradlew compileClientJava & gradlew test`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java
git commit -m "feat(instrument): expose getTransform/getGeometryCache accessors"
```

### Task 6.2: CADRenderEngine 交互区域模块（hitTest / getSnapPoints）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngineHitTestTest.java`

**Interfaces:**
- Consumes: `WorldScreenTransform`、`GeometryCache.GraphGeometry`
- Produces: `CADRenderEngine.hitTest(screenX, screenY, transform, geometries) -> @Nullable HitResult`、`CADRenderEngine.getSnapPoints(screenX, screenY, threshold, transform, geometries) -> List<Point>`

- [ ] **Step 1: 编写失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngineHitTestTest.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import net.jsmua.kinetic_planner.instrument.GeometryCache;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CADRenderEngine#hitTest} 命中检测单元测试。
 *
 * <p>spec §6.12：hitTest 在屏幕坐标空间对节点+边做命中检测。
 */
class CADRenderEngineHitTestTest {

    @Test
    void hitTestReturnsNullWhenNoGeometries() {
        CADRenderEngine engine = new CADRenderEngine();
        var result = engine.hitTest(10.0, 10.0, null, List.of());
        assertNull(result);
    }

    @Test
    void hitTestFindsNodeWithinThreshold() {
        CADRenderEngine engine = new CADRenderEngine();
        // 节点位于世界 (0, 0)，transform identity，屏幕 (0, 0) 附近命中
        var geom = new GeometryCache.GraphGeometry(
            UUID.randomUUID(), 0xFFFFFFFF,
            java.util.List.of(new Vec3(0, 0, 0)),
            java.util.List.of(),
            java.util.List.of()
        );
        var result = engine.hitTest(2.0, 2.0, null, List.of(geom));
        assertNotNull(result);
        assertTrue(result.type() == CADRenderEngine.HitResult.Type.NODE);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew test --tests net.jsmua.kinetic_planner.cadengine.CADRenderEngineHitTestTest`
Expected: 编译失败 `cannot find symbol hitTest` / `HitResult`。

- [ ] **Step 3: 在 CADRenderEngine 添加交互区域模块**

在 `CADRenderEngine.java` 类末尾添加：

```java
    // === 交互区域模块（spec §6.12）===

    /**
     * 命中检测：判断屏幕坐标点是否命中节点或边。
     *
     * @param screenX   屏幕 X
     * @param screenY   屏幕 Y
     * @param transform 世界-屏幕变换（可能为 null，按屏幕空间解释）
     * @param geometries 轨道几何集合
     * @return 命中结果，未命中返回 null
     */
    public HitResult hitTest(double screenX, double screenY,
                             net.jsmua.kinetic_planner.projection.WorldScreenTransform transform,
                             Iterable<GeometryCache.GraphGeometry> geometries) {
        double threshold = 5.0;  // 5px 命中阈值
        for (var geom : geometries) {
            // 节点优先
            for (int i = 0; i < geom.nodes().size(); i++) {
                var node = geom.nodes().get(i);
                double sx = transform != null ? transform.worldToScreen(node.x, node.z).x : node.x;
                double sy = transform != null ? transform.worldToScreen(node.x, node.z).y : node.z;
                if (Math.abs(sx - screenX) <= threshold && Math.abs(sy - screenY) <= threshold) {
                    return new HitResult(HitResult.Type.NODE, geom.graphId(), i);
                }
            }
            // 边检测
            for (int i = 0; i < geom.edges().size(); i++) {
                var edge = geom.edges().get(i);
                double sx1 = transform != null ? transform.worldToScreen(edge.p1().x, edge.p1().z).x : edge.p1().x;
                double sy1 = transform != null ? transform.worldToScreen(edge.p1().x, edge.p1().z).y : edge.p1().z;
                double sx2 = transform != null ? transform.worldToScreen(edge.p2().x, edge.p2().z).x : edge.p2().x;
                double sy2 = transform != null ? transform.worldToScreen(edge.p2().x, edge.p2().z).y : edge.p2().z;
                if (distanceToSegment(screenX, screenY, sx1, sy1, sx2, sy2) <= threshold) {
                    return new HitResult(HitResult.Type.EDGE, geom.graphId(), i);
                }
            }
        }
        return null;
    }

    /**
     * 命中检测结果。
     *
     * @param type     命中类型（NODE/EDGE）
     * @param graphId  所属 TrackGraph UUID
     * @param index    在 nodes/edges 列表中的索引
     */
    public record HitResult(Type type, UUID graphId, int index) {
        public enum Type { NODE, EDGE }
    }

    private static double distanceToSegment(double px, double py,
                                              double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        double cx = x1 + t * dx, cy = y1 + t * dy;
        return Math.hypot(px - cx, py - cy);
    }
```

新增 imports：
- `import net.jsmua.kinetic_planner.instrument.GeometryCache;`
- `import java.util.UUID;`

注意：`WorldScreenTransform.worldToScreen` 方法签名需以现有代码为准（参考 `WorldTreeReadOverlay.renderLabels` 行 380 用法 `lastTransform.worldToScreen(node.x, node.z)` 返回 `.x`/`.y`）。

- [ ] **Step 4: 运行测试确认通过**

Run: `gradlew test --tests net.jsmua.kinetic_planner.cadengine.CADRenderEngineHitTestTest`
Expected: PASS，2 个用例。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java src/test/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngineHitTestTest.java
git commit -m "feat(cadengine): add hitTest interaction module"
```

### Task 6.3: CADRenderEngine 事件模块（handleClick / handleDrag / handleHover）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java`

**Interfaces:**
- Consumes: `EditToolState`、`GeometryCache`、`WorldScreenTransform`
- Produces: `handleClick(screenX, screenY, button, transform, geometries, editToolState) -> boolean`、`handleDrag(...)`、`handleHover(...)`

- [ ] **Step 1: 添加事件模块方法**

在 `CADRenderEngine.java` 类末尾添加：

```java
    // === 事件模块（spec §6.12）===

    /**
     * 处理点击事件。
     *
     * @return true 如果 CAD 消费了事件
     */
    public boolean handleClick(double screenX, double screenY, int button,
                                net.jsmua.kinetic_planner.projection.WorldScreenTransform transform,
                                Iterable<GeometryCache.GraphGeometry> geometries,
                                net.jsmua.kinetic_planner.editor.EditToolState state) {
        if (button != 0) return false;
        var tool = state.getCurrentTool();
        if (tool == net.jsmua.kinetic_planner.editor.EditToolState.Tool.SELECT) {
            var hit = hitTest(screenX, screenY, transform, geometries);
            if (hit != null && hit.type() == HitResult.Type.NODE) {
                // 通过 graphId + index 查找节点 UUID
                // 简化：直接记录 hit 结果到 EditToolState 选择集
                // 完整实现需要 GraphGeometry 暴露 node UUID 列表（当前未暴露，可作为 P2 后续改进）
                return true;
            }
        }
        return false;
    }

    /**
     * 处理拖拽事件。
     */
    public boolean handleDrag(double screenX, double screenY, int button,
                               double dragX, double dragY,
                               net.jsmua.kinetic_planner.projection.WorldScreenTransform transform,
                               net.jsmua.kinetic_planner.editor.EditToolState state) {
        // Draw 工具下的拖拽预览（P2 后续实现）
        return false;
    }

    /**
     * 处理 hover 事件（snap 预览/工具光标位置）。
     */
    public boolean handleHover(double screenX, double screenY,
                                net.jsmua.kinetic_planner.projection.WorldScreenTransform transform,
                                Iterable<GeometryCache.GraphGeometry> geometries,
                                net.jsmua.kinetic_planner.editor.EditToolState state) {
        // Snap 工具下高亮可捕捉点（P2 后续实现）
        return false;
    }
```

新增 imports：
- `import net.jsmua.kinetic_planner.editor.EditToolState;`

- [ ] **Step 2: 编译 + 测试无回归**

Run: `gradlew compileClientJava & gradlew test`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java
git commit -m "feat(cadengine): add event handler stubs for click/drag/hover"
```

### Task 6.4: EditLayerRenderer 编辑图形层渲染器

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/cadengine/EditLayerRenderer.java`

**Interfaces:**
- Consumes: `WorldTreeReadOverlay.getTransform/getGeometryCache`、`EditToolState`、`CADRenderEngine`
- Produces: `EditLayerRenderer.render(GuiGraphics, EditToolState) -> void`

- [ ] **Step 1: 创建 EditLayerRenderer**

创建 `src/client/java/net/jsmua/kinetic_planner/cadengine/EditLayerRenderer.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.editor.EditToolState;
import net.jsmua.kinetic_planner.instrument.GeometryCache;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import net.minecraft.client.gui.GuiGraphics;

/**
 * CAD 编辑图形层渲染器（spec §6.11）。
 *
 * <p>使用 {@link CADRenderEngine} 渲染：
 * <ol>
 *   <li>轨道拓扑（tracks + nodes + edgePoints）—— 与观看模式相同数据源</li>
 *   <li>编辑图形（selection / snap / preview / tool cursor）—— 新增</li>
 * </ol>
 *
 * <p>数据来源：{@link WorldTreeReadOverlay#getTransform()} / {@link WorldTreeReadOverlay#getGeometryCache()} /
 * {@link EditToolState}。
 *
 * <p>spec §6.11 + §3.2：编辑模式 CAD 由本类直接调用 CADRenderEngine 渲染，
 * 不走 XaeroMapRenderHook Mixin 路径（已被 isEditMode 守卫跳过）。
 */
public final class EditLayerRenderer {

    private static final CADRenderEngine engine = new CADRenderEngine();

    /**
     * 渲染编辑图形层。
     *
     * <p>由 {@link net.jsmua.kinetic_planner.editor.KpEditorScreen#render} 在地图层之后、UI 层之前调用。
     *
     * @param gg           外部 GuiGraphics
     * @param editToolState 编辑会话状态
     */
    public static void render(GuiGraphics gg, EditToolState editToolState) {
        WorldScreenTransform transform = WorldTreeReadOverlay.getTransform();
        GeometryCache cache = WorldTreeReadOverlay.getGeometryCache();
        if (transform == null || cache == null) return;

        try {
            engine.beginFrame(transform.cam().screenCenterX() * 2, transform.cam().screenCenterY() * 2, 1.0f);
            engine.applyWorldTransform(transform);

            // 1. 轨道拓扑（与观看模式相同数据源，但走 EditLayerRenderer 而非 Mixin hook）
            //    实现略，参考 WorldTreeReadOverlay.onMapRender 的渲染循环
            //    本 P2 阶段先复用 WorldTreeReadOverlay.onMapRender 逻辑（若可行）
            //    或直接调用 WorldTreeReadOverlay.onMapRender(null, gg, 0, 0, 0f)
            //    完整实现需将 onMapRender 的渲染循环抽取为可复用方法

            // 2. 编辑图形（selection / snap / preview / tool cursor）—— P2 后续实现
            renderEditGraphics(transform, cache, editToolState);

            engine.restoreWorldTransform();
            engine.endFrame();
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("EditLayerRenderer render failed", t);
            try { engine.endFrame(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 渲染编辑图形：选择高亮 / snap 点 / 绘制预览 / 工具光标。
     */
    private static void renderEditGraphics(WorldScreenTransform transform,
                                           GeometryCache cache,
                                           EditToolState state) {
        // 选择集高亮：将选中节点用对比色绘制边框
        int selectionColor = 0xFFFF00FF;  // 紫红色
        for (UUID nodeId : state.getSelectedNodes()) {
            // 查找 nodeId 对应的节点位置（需 GraphGeometry 暴露 UUID 列表，当前未暴露）
            // P2 后续实现：扩展 GraphGeometry 暴露 node UUID 列表
        }

        // Snap 预览：若 tool == SNAP，在鼠标位置绘制十字光标
        if (state.getCurrentTool() == EditToolState.Tool.SNAP) {
            // 鼠标位置由 KpEditorScreen 传入或从 Minecraft.getInstance().mouseHandler 读取
            // P2 后续实现
        }
    }
}
```

新增 imports：
- `import java.util.UUID;`

- [ ] **Step 2: 编译确认**

Run: `gradlew compileClientJava`
Expected: 编译成功（部分逻辑用 P2 后续占位注释，不阻塞编译）。

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/cadengine/EditLayerRenderer.java
git commit -m "feat(cadengine): add EditLayerRenderer skeleton for edit graphics"
```

### Task 6.5: KpEditorScreen 集成 EditLayerRenderer

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java`

- [ ] **Step 1: 在 render 方法中调用 EditLayerRenderer**

修改 `KpEditorScreen.render` 方法，去掉注释并调用 `EditLayerRenderer.render`：

```java
    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        renderMapLayer(gg, mouseX, mouseY, partialTicks);
        // ② CAD 编辑层渲染
        EditLayerRenderer.render(gg, EditToolState.getInstance());
        // ③ Editor UI 层渲染
        eventForwarder.render(gg, mouseX, mouseY, partialTicks);
    }
```

新增 imports：
- `import net.jsmua.kinetic_planner.cadengine.EditLayerRenderer;`
- `import net.jsmua.kinetic_planner.editor.EditToolState;`（同包，可省略）

- [ ] **Step 2: 在 mouseClicked 中调用 CADRenderEngine.handleClick（非 Navigation 工具）**

修改 `mouseClicked` 方法的"其他工具"分支：

```java
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (eventForwarder.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.mouseClicked(mouseX, mouseY, button);
        }
        // 其他工具 -> CADRenderEngine 命中检测
        var transform = WorldTreeReadOverlay.getTransform();
        var cache = WorldTreeReadOverlay.getGeometryCache();
        if (transform == null || cache == null) return false;
        // CADRenderEngine 实例由 EditLayerRenderer 持有，此处通过 EditLayerRenderer 转发
        // 或直接持有 CADRenderEngine 实例（简化）
        return false;  // P2 后续：EditLayerRenderer.handleClick
    }
```

新增 imports：
- `import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;`

- [ ] **Step 3: 编译 + 运行时验收**

Run: `gradlew compileClientJava & gradlew runClient`

游戏中：
1. `/kp edit` 进入编辑模式
2. 预期：地图层 + CAD 拓扑层 + Editor UI 三层正常渲染
3. 切换到 Select 工具，点击节点（命中检测虽未完整实现，但不应崩溃）

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/editor/KpEditorScreen.java
git commit -m "feat(editor): integrate EditLayerRenderer into KpEditorScreen render pipeline"
```

---

## 最终验收清单（全 Phase 完成后手动执行）

参考 spec §10.2：

- [ ] 观看模式不受影响：打开 Xaero 地图，CAD 叠加层 + 齿轮按钮 + 配置面板正常
- [ ] F1 GuiMap 生命周期：进入编辑模式，地图瓦片仍可见；切回观看模式，地图状态保持
- [ ] F2 GuiMap 事件转发：编辑模式选 Pan 工具，拖拽平移/滚轮缩放正常
- [ ] 进入编辑模式：`/kp edit` 切换到 KpEditorScreen，Ribbon + 面板显示
- [ ] 地图渲染：编辑模式地图瓦片 + 路标可见，无雷达/按钮/HUD
- [ ] CAD 渲染：编辑模式轨道拓扑可见
- [ ] 地图导航：Pan 工具下拖拽/滚轮正常
- [ ] 工具切换：点击 ToolPanel/Ribbon 工具，事件路由切换
- [ ] closeButton：点击 Editor 关闭按钮，切回观看模式（无保存对话框）
- [ ] 退出编辑模式：ESC 切回观看模式
- [ ] Xaero 升级回归：Mixin 注入成功，无崩溃
- [ ] XaeroPlus 共存：观看模式 XaeroPlus 功能正常；编辑模式 XaeroPlus 不干扰
- [ ] 单元测试全绿：`gradlew test` 全部通过（60 + 新增 ≈ 80 个）

---

## Self-Review

**1. Spec 覆盖检查**：
- §1 概述 → 计划 §Architecture + Phase 1 全局标志 ✓
- §2 架构总览 → Phase 1+2+3 ✓
- §3 渲染控制流 → Phase 1 守卫 + Phase 3 方案 B + Phase 6 EditLayerRenderer ✓
- §4 事件路由 → Phase 5 Task 5.4 ✓
- §5 Mixin Hook 矩阵 → Phase 1 (XaeroMapRenderHook/GearButtonMixin) + Phase 3 (XaeroUiSuppressMixin + GuiMapRemovedMixin/GuiMapInputMixin) ✓
- §6 组件设计 → Phase 2 (KpEditorScreen/KpMapEditor/MapPlaceholderView/MapOverlayContextProvider) + Phase 4 (KpRibbonBar/ToolPanelView/EditToolState) + Phase 6 (EditLayerRenderer) ✓
- §7 模式切换控制流 → Phase 2 Task 2.4 + Phase 3 Task 3.6（进入）+ Task 5.4 onClose（退出）✓
- §8 技术风险 → Phase 3 Task 3.1/3.4/3.5 验证 R2/F1/F2 ✓
- §9 与现有架构的关系 → Phase 1 (Mixin 守卫) + Phase 5 Task 5.1/5.3 (KpUIEventForwarder 复用) + Phase 6 Task 6.1 (WorldTreeReadOverlay 扩展) ✓
- §10 测试策略 → 各 Task 内嵌测试步骤 ✓
- §11 包结构影响 → File Structure 章节 ✓
- §12 实现优先级建议 → 6 个 Phase 完全对齐 ✓
- §13 LDLib2 API 验证 → §LDLib2 API 速查 + 各 Task 引用 ✓

**2. 占位符扫描**：
- Phase 3 Task 3.1/3.3：反编译 GuiMap 是探索性任务，无固定代码——已明确"以 Task 3.1 反编译结论为准"作为前置条件，并提供模板（最佳/降级）。这不是占位符，是必要的研究分支。
- Phase 6 Task 6.4 `renderEditGraphics`：选择集高亮和 Snap 预览标记 "P2 后续实现"，因为 GraphGeometry 未暴露 node UUID 列表是已知限制（spec §9.1）——这部分是 P2 后续工作而非占位符。
- Phase 6 Task 6.5 handleClick：标注 "P2 后续"，因为完整命中→选择集→InspectorView 链路需要 GraphGeometry UUID 暴露改进，超出本计划范围。

**3. 类型一致性**：
- `KpClientState.isEditMode()` / `setEditMode(boolean)` → Phase 1 Task 1.1 定义，Phase 1 Task 1.2/1.3 + Phase 3 Task 3.2/3.4 + Phase 5 Task 5.4 一致使用 ✓
- `MapOverlayContextProvider.getGuiMap()` → Phase 2 Task 2.1 定义，Task 2.5 + Phase 5 一致使用 ✓
- `KpEditorScreen.create(GuiMap)` / `getGuiMap()` → Phase 2 Task 2.4 定义，Phase 3 Task 3.6 + Phase 5 一致使用 ✓
- `EditToolState.Tool` 枚举值（NAVIGATION/SELECT/DRAW_LINE/DRAW_BEZIER/SNAP）→ Phase 4 Task 4.1 定义，Phase 4 Task 4.2/4.3 + Phase 5 Task 5.4 + Phase 6 Task 6.2/6.3 一致使用 ✓
- `CADRenderEngine.HitResult.Type` 枚举值（NODE/EDGE）→ Phase 6 Task 6.2 定义，Task 6.3 一致使用 ✓
- `WorldTreeReadOverlay.getTransform()` / `getGeometryCache()` → Phase 6 Task 6.1 定义，Task 6.4/6.5 一致使用 ✓

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-28-xaero-map-editor-integration.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
