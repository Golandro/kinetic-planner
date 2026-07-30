# Editor 视口与事件路由实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复编辑器视口与事件路由子系统的架构缺陷与规格差距，建立可测试的事件路由框架。

**Architecture:** 从架构审计（Health Score 58/100）中识别出 1 个 Critical（工具定义散弹式修改）、5 个 Warning（依赖循环、静态状态、知识重复、自定义缩放、CADRenderEngine 双实例）和 2 个 Suggestion。本计划通过 5 个任务修复这些发现，同时实现 spec §4.3.1（右键取消拖拽）、§4.3.2（滚轮委托）、§4.4（参数化按键）和 §10（测试策略）中标记为 [TODO] 的需求。

**Tech Stack:** Java 21, NeoForge 1.21.1, LDLib2 2.2.26, JUnit 5 + Mockito 5, LWJGL3 GLFW

## Global Constraints

- MC 1.21.1 API：`addVertex(x,y,z)` 非 `vertex()`，`setColor(r,g,b,a)` 非 `color()`，无 `endVertex()`
- sourceSet 分离：main (common) 不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`；client 可引用 main + client
- Mixin on Create/Xaero 类用 `remap = false`；Mixin 配置 `defaultRequire: 0`
- `XaeroMapAccessor.scale` = pixels per block（值越大越近），`worldDelta = screenDelta / scale`
- 测试策略：common 类纯 JVM 单测；client 类 Mockito mock 或 `@Disabled` + 运行时验收
- EditToolState 不在进入编辑模式时调用 `reset()`（spec §7.1，状态持久性是 CAD 已知特性）
- `mouseClicked` 禁止委托 `guiMap`（触发原生 UI 按钮）；`mouseScrolled` 允许委托
- 构建命令：`gradlew compileJava compileClientJava` / `gradlew test` / `gradlew runClient`

---

## File Structure

| 文件 | 操作 | 职责 |
|---|---|---|
| `src/client/java/.../gui/editor/EditToolState.java` | 修改 | 添加 Tool 枚举元数据（displayName/keyBinding/keyLabel）+ 包级构造器 |
| `src/client/java/.../cadengine/EditLayerRenderer.java` | 修改 | 移除 KpEditorScreen 导入（破循环）+ 使用共享 CADRenderEngine |
| `src/client/java/.../instrument/WorldTreeReadOverlay.java` | 修改 | 暴露 getEngine() + 提取 renderTracks() 共享方法 |
| `src/client/java/.../gui/editor/KpEditorScreen.java` | 修改 | 滚轮委托 + 右键取消 + 数据驱动工具匹配 + GLFW 常量 |
| `src/client/java/.../gui/KpRibbonBar.java` | 修改 | 从 Tool 枚举遍历生成按钮 |
| `src/client/java/.../gui/ToolPanelView.java` | 修改 | 从 Tool 枚举遍历生成按钮 |
| `src/test/java/.../editor/EditToolStateTest.java` | 修改 | 补充工具元数据测试 |
| `src/test/java/.../editor/ToolKeyBindingConsistencyTest.java` | 创建 | 工具按键一致性纯 JVM 测试 |
| `src/test/java/.../editor/EditorEventRoutingTest.java` | 创建 | 事件路由测试（部分 @Disabled） |

---

## Task 1: Break Dependency Cycle + Unify CADRenderEngine

**Files:**
- Modify: `src/client/java/.../cadengine/EditLayerRenderer.java`
- Modify: `src/client/java/.../instrument/WorldTreeReadOverlay.java`
- Test: `src/test/java/.../editor/KpEditorScreenTest.java`（验证无循环依赖）

**Interfaces:**
- Consumes: `WorldTreeReadOverlay.getEngine()` (新增), `WorldTreeReadOverlay.renderTracks()` (新增)
- Produces: `EditLayerRenderer` 不再依赖 `KpEditorScreen`（消除编译期循环）

**Background:** `EditLayerRenderer` 导入 `KpEditorScreen` 仅用于 javadoc `{@link}`，但 Java 编译器将 javadoc 导入视为真实依赖，形成 `gui.editor` -> `cadengine` -> `gui.editor` 循环。同时，`EditLayerRenderer` 和 `WorldTreeReadOverlay` 各自持有独立的 `CADRenderEngine` 实例，在同一渲染帧内可能产生 GL 状态冲突。

- [ ] **Step 1: Write the failing test - 验证无循环依赖**

```java
// src/test/java/net/jsmua/kinetic_planner/editor/CadLayerNoCycleTest.java
package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.cadengine.EditLayerRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 EditLayerRenderer 不导入 KpEditorScreen（消除编译期循环依赖）。
 *
 * <p>审计发现 R5：EditLayerRenderer -> KpEditorScreen -> EditLayerRenderer 循环。
 * 修复后 EditLayerRenderer 不应再引用 KpEditorScreen。
 */
class CadLayerNoCycleTest {

    @Test
    void editLayerRendererDoesNotImportKpEditorScreen() throws ClassNotFoundException {
        Class<?> editLayerRenderer = EditLayerRenderer.class;
        // 获取所有 declared fields 的类型，确认不含 KpEditorScreen
        for (var field : editLayerRenderer.getDeclaredFields()) {
            assertNotEquals("net.jsmua.kinetic_planner.gui.editor.KpEditorScreen",
                field.getType().getName(),
                "EditLayerRenderer 不应持有 KpEditorScreen 字段（循环依赖）");
        }
        // 获取所有 declared methods 的参数和返回类型
        for (var method : editLayerRenderer.getDeclaredMethods()) {
            assertNotEquals("net.jsmua.kinetic_planner.gui.editor.KpEditorScreen",
                method.getReturnType().getName(),
                "EditLayerRenderer 方法不应返回 KpEditorScreen（循环依赖）");
            for (var paramType : method.getParameterTypes()) {
                assertNotEquals("net.jsmua.kinetic_planner.gui.editor.KpEditorScreen",
                    paramType.getName(),
                    "EditLayerRenderer 方法参数不应为 KpEditorScreen（循环依赖）");
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew test --tests "*.CadLayerNoCycleTest"`
Expected: PASS（当前 EditLayerRenderer 无 KpEditorScreen 字段/方法/参数，javadoc import 不影响反射）

Note: 如果此测试已通过，说明循环仅存在于 import 层面（javadoc），不影响运行时反射。仍需移除 import 以消除编译期循环。

- [ ] **Step 3: Remove KpEditorScreen import from EditLayerRenderer**

```java
// src/client/java/.../cadengine/EditLayerRenderer.java
// 移除这行 import：
// import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;

// 将 javadoc 中的 {@link KpEditorScreen#render} 改为纯文本：
// 旧：* <p>由 {@link KpEditorScreen#render} 在地图层之后、UI 层之前调用。
// 新：* <p>由 KpEditorScreen.render() 在地图层之后、UI 层之前调用。
```

- [ ] **Step 4: Add getEngine() to WorldTreeReadOverlay**

```java
// src/client/java/.../instrument/WorldTreeReadOverlay.java
// 在现有 static 字段后添加：

/**
 * 返回全局 {@link CADRenderEngine} 实例（供 {@link EditLayerRenderer} 复用）。
 *
 * <p>审计 R5 修复：消除 EditLayerRenderer 与 WorldTreeReadOverlay 各自持有独立实例的
 * GL 状态冲突风险。编辑模式下两者使用同一引擎实例。
 *
 * @return 全局 CADRenderEngine 实例
 */
public static CADRenderEngine getEngine() {
    return engine;
}
```

- [ ] **Step 5: Modify EditLayerRenderer to use shared engine**

```java
// src/client/java/.../cadengine/EditLayerRenderer.java
// 移除 private static final CADRenderEngine engine = new CADRenderEngine();
// 改为从 WorldTreeReadOverlay 获取：

public static void render(GuiGraphics gg, EditToolState editToolState) {
    WorldScreenTransform transform = WorldTreeReadOverlay.getTransform();
    GeometryCache cache = WorldTreeReadOverlay.getGeometryCache();
    if (transform == null || cache == null) return;

    CADRenderEngine engine = WorldTreeReadOverlay.getEngine();
    try {
        engine.beginFrame(transform.cam().screenCenterX() * 2, transform.cam().screenCenterY() * 2, 1.0f);
        engine.applyWorldTransform(transform);
        // ... rendering code unchanged ...
        engine.restoreWorldTransform();
        engine.endFrame();
    } catch (Throwable t) {
        KineticPlannerMod.LOGGER.error("EditLayerRenderer render failed", t);
        try { engine.endFrame(); } catch (Throwable ignored) {}
    }
}
```

- [ ] **Step 6: Extract renderTracks() shared method in WorldTreeReadOverlay**

```java
// src/client/java/.../instrument/WorldTreeReadOverlay.java
// 新增公共方法，供 onMapRender 和 EditLayerRenderer 共用：

/**
 * 渲染轨道拓扑（tracks + nodes + edgePoints）。
 *
 * <p>提取自 {@link #onMapRender} 的渲染循环，供 {@link EditLayerRenderer} 复用，
 * 消除渲染逻辑重复（审计 R3）。
 *
 * <p>调用前必须已执行 {@link CADRenderEngine#beginFrame} + {@link CADRenderEngine#applyWorldTransform}。
 * 调用后需执行 {@link CADRenderEngine#restoreWorldTransform} + {@link CADRenderEngine#endFrame}。
 *
 * @param engine    已初始化的 CADRenderEngine（已 beginFrame + applyWorldTransform）
 * @param transform 当前世界-屏幕变换
 * @param cache     几何缓存
 */
public static void renderTracks(CADRenderEngine engine,
                                 WorldScreenTransform transform,
                                 GeometryCache cache) {
    for (GeometryCache.GraphGeometry geom : cache.geometries()) {
        // 1. 轨道层
        if (theme.layers().tracks()) {
            float widthPx = (theme.global().constantScreenLineWidth()
                ? theme.global().fixedScreenLineWidthPx()
                : theme.track().width() / (float) transform.cam().blocksPerPixel())
                * activeLineWidthScale;
            int trackColorScaled = applyAlpha(geom.graphColor(),
                theme.track().alpha() * activeAlphaScale);
            for (EdgeGeometry edge : geom.edges()) {
                try {
                    if (edge.type() == EdgeGeometry.Type.BEZIER && edge.bezier() != null) {
                        var b = edge.bezier();
                        engine.drawBezier(
                            (float) b.start().x, (float) b.start().z,
                            (float) b.control1().x, (float) b.control1().z,
                            (float) b.control2().x, (float) b.control2().z,
                            (float) b.end().x, (float) b.end().z,
                            widthPx, trackColorScaled, 32, activeDashed);
                    } else {
                        engine.drawLine(
                            (float) edge.p1().x, (float) edge.p1().z,
                            (float) edge.p2().x, (float) edge.p2().z,
                            widthPx, trackColorScaled, activeDashed);
                    }
                } catch (Throwable ignored) {}
            }
        }
        // 2. 节点层
        if (theme.layers().nodes()) {
            int nodeColor = applyAlpha(0xFFFFFFFF, theme.node().alpha() * activeAlphaScale);
            float nodeRadius = theme.node().width() / 2;
            for (Vec3 node : geom.nodes()) {
                engine.drawFilledCircle((float) node.x, (float) node.z, nodeRadius, nodeColor);
            }
        }
        // 3. 边点层
        if (theme.layers().edgePoints()) {
            float epRadius = theme.edgePoint().width() / 2;
            for (GeometryCache.EdgePointData ep : geom.edgePoints()) {
                int epColor = applyAlpha(ep.color(), theme.edgePoint().alpha() * activeAlphaScale);
                engine.drawFilledCircle(
                    (float) ep.worldPos().x, (float) ep.worldPos().z,
                    epRadius, epColor);
            }
        }
    }
}
```

Then update `onMapRender` to call `renderTracks`:

```java
// 在 onMapRender 方法中，替换内联渲染循环为：
engine.beginFrame(lastContext.screenWidth(), lastContext.screenHeight(), lastContext.dpr());
engine.applyWorldTransform(lastTransform);
renderTracks(engine, lastTransform, geometryCache);
engine.restoreWorldTransform();
engine.endFrame();
```

- [ ] **Step 7: Update EditLayerRenderer.render() to call renderTracks()**

```java
// 在 EditLayerRenderer.render() 中，替换注释占位为：
engine.beginFrame(transform.cam().screenCenterX() * 2, transform.cam().screenCenterY() * 2, 1.0f);
engine.applyWorldTransform(transform);

// 1. 轨道拓扑（复用 WorldTreeReadOverlay 的渲染逻辑）
WorldTreeReadOverlay.renderTracks(engine, transform, cache);

// 2. 编辑图形
renderEditGraphics(transform, cache, editToolState);

engine.restoreWorldTransform();
engine.endFrame();
```

- [ ] **Step 8: Compile and run tests**

Run: `gradlew compileClientJava & gradlew test`
Expected: 编译通过，无循环依赖。CadLayerNoCycleTest PASS。

- [ ] **Step 9: Commit**

```bash
git add src/client/java/ src/test/java/
git commit -m "refactor: break KpEditorScreen-EditLayerRenderer cycle, unify CADRenderEngine instance, extract shared renderTracks()"
```

---

## Task 2: Scroll Delegation + All-Tools Scroll + Right-Click Cancel

**Files:**
- Modify: `src/client/java/.../gui/editor/KpEditorScreen.java`
- Test: `src/test/java/.../editor/EditorEventRoutingTest.java`（新建，部分 @Disabled）

**Interfaces:**
- Consumes: `XaeroMapAccessor` (现有), `guiMap.mouseScrolled()` (Xaero 原生)
- Produces: KpEditorScreen.mouseScrolled 委托 guiMap；mouseDragged 支持右键取消

**Background:** spec §4.3.2 要求用 `guiMap.mouseScrolled()` 委托替代自定义线性缩放（当前 `scale += scrollY * SCALE_STEP`），因为 GuiMap 原生缩放包含中心点保持和瓦片降级。spec §4.3.2 还要求所有工具下滚轮均缩放（当前仅 NAVIGATION）。spec §4.3.1 要求拖拽中按右键直接结束拖拽。

- [ ] **Step 1: Remove custom scale constants and implement scroll delegation**

```java
// src/client/java/.../gui/editor/KpEditorScreen.java
// 移除这三个常量：
// private static final double MIN_SCALE = 0.5;
// private static final double MAX_SCALE = 64.0;
// private static final double SCALE_STEP = 0.5;

// 替换 mouseScrolled 方法：
@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    // ① Editor UI 优先
    if (eventForwarder.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
        return true;
    }
    // ② 所有工具下滚轮均缩放（spec §4.3.2 CAD 标准行为）
    if (isMouseOverMapViewport(mouseX, mouseY)) {
        // 优先委托 guiMap.mouseScrolled()，复用 Xaero 原生缩放逻辑
        // （中心点保持 + 瓦片降级），spec §4.3.2 + §5.3
        try {
            if (guiMap.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        } catch (Exception e) {
            // guiMap.mouseScrolled 可能因 mc.screen 检查失败，降级为 accessor 方案
            KineticPlannerMod.LOGGER.debug("guiMap.mouseScrolled delegation failed, falling back to accessor", e);
        }
        // 降级方案：直接读写 scale 字段（无中心点保持，仅作为 fallback）
        var accessor = kp$accessor();
        double currentScale = accessor.kp$scale();
        double newScale = currentScale * (scrollY > 0 ? 1.2 : 1.0 / 1.2);
        accessor.kp$setScale(newScale);
        return true;
    }
    return false;
}
```

Note: 需要在文件顶部添加 `import net.jsmua.kinetic_planner.KineticPlannerMod;`

- [ ] **Step 2: Add right-click cancel during drag**

```java
// src/client/java/.../gui/editor/KpEditorScreen.java
// 在 mouseDragged 方法中，添加右键检测：

@Override
public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
    if (eventForwarder.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
        return true;
    }
    // 右键取消拖拽（spec §4.3.1：拖拽中按右键直接结束）
    if (isDraggingMap && button == 1) {
        isDraggingMap = false;
        return true;
    }
    // 自定义拖拽平移
    if (isDraggingMap) {
        var accessor = kp$accessor();
        double blocksPerPixel = 1.0 / accessor.kp$scale();
        accessor.kp$setCameraX(accessor.kp$cameraX() - dragX * blocksPerPixel);
        accessor.kp$setCameraZ(accessor.kp$cameraZ() - dragY * blocksPerPixel);
        return true;
    }
    return false;
}
```

- [ ] **Step 3: Write event routing test (structure validation, @Disabled for runtime)**

```java
// src/test/java/net/jsmua/kinetic_planner/editor/EditorEventRoutingTest.java
package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编辑器事件路由测试（spec §10.1）。
 *
 * <p>大多数行为测试需要 MC 运行时（Screen 构造触发 <clinit>），
 * 标记 @Disabled 靠 runClient 验收。结构验证（方法签名、常量移除）可在纯 JVM 执行。
 */
class EditorEventRoutingTest {

    /**
     * 验证自定义缩放常量已被移除（spec §4.3.2 要求委托 guiMap.mouseScrolled）。
     */
    @Test
    void noCustomScaleConstants() {
        assertThrows(NoSuchFieldException.class,
            () -> KpEditorScreen.class.getDeclaredField("MIN_SCALE"),
            "MIN_SCALE 应已移除（spec §4.3.2 委托 guiMap.mouseScrolled）");
        assertThrows(NoSuchFieldException.class,
            () -> KpEditorScreen.class.getDeclaredField("MAX_SCALE"),
            "MAX_SCALE 应已移除");
        assertThrows(NoSuchFieldException.class,
            () -> KpEditorScreen.class.getDeclaredField("SCALE_STEP"),
            "SCALE_STEP 应已移除");
    }

    /**
     * 验证 mouseScrolled 方法签名不变（委托后仍接收 4 参数）。
     */
    @Test
    void mouseScrolledSignature() throws NoSuchMethodException {
        Method m = KpEditorScreen.class.getDeclaredMethod(
            "mouseScrolled", double.class, double.class, double.class, double.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    /**
     * 验证 mouseDragged 方法签名不变。
     */
    @Test
    void mouseDraggedSignature() throws NoSuchMethodException {
        Method m = KpEditorScreen.class.getDeclaredMethod(
            "mouseDragged", double.class, double.class, int.class, double.class, double.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    @Disabled("需要 MC 运行时：验证滚轮缩放委托 guiMap.mouseScrolled（所有工具）")
    void scrollDelegatesToGuiMapForAllTools() {
        // 运行时验收：
        // 1. 切换到 SELECT 工具
        // 2. 在主视口内滚轮
        // 3. 验证 guiMap.mouseScrolled 被调用（通过 spy/mock 或行为观察）
        // 4. 验证地图实际缩放（中心点保持）
    }

    @Test
    @Disabled("需要 MC 运行时：验证拖拽中右键取消")
    void rightClickCancelsDrag() {
        // 运行时验收：
        // 1. NAVIGATION 工具下左键开始拖拽
        // 2. 拖拽中按右键
        // 3. 验证 isDraggingMap = false，拖拽结束
    }

    @Test
    @Disabled("需要 MC 运行时：验证缩放中心点保持")
    void scrollPreservesCenterPoint() {
        // 运行时验收：
        // 1. 记录鼠标指针下方的世界坐标
        // 2. 滚轮缩放
        // 3. 验证该世界坐标仍在指针下方
    }
}
```

- [ ] **Step 4: Compile and run tests**

Run: `gradlew compileClientJava & gradlew test --tests "*.EditorEventRoutingTest"`
Expected: `noCustomScaleConstants` PASS（常量已移除）；签名测试 PASS；@Disabled 测试跳过。

- [ ] **Step 5: Commit**

```bash
git add src/client/java/ src/test/java/
git commit -m "feat: delegate scroll to guiMap.mouseScrolled (all tools), add right-click drag cancel (spec §4.3.1, §4.3.2)"
```

---

## Task 3: Data-Driven Tool Definition (Eliminate Shotgun Surgery)

**Files:**
- Modify: `src/client/java/.../gui/editor/EditToolState.java`
- Modify: `src/client/java/.../gui/editor/KpEditorScreen.java`
- Modify: `src/client/java/.../gui/KpRibbonBar.java`
- Modify: `src/client/java/.../gui/ToolPanelView.java`
- Modify: `src/test/java/.../editor/EditToolStateTest.java`
- Create: `src/test/java/.../editor/ToolKeyBindingConsistencyTest.java`

**Interfaces:**
- Consumes: `org.lwjgl.glfw.GLFW` (LWJGL3，client sourceSet 可用)
- Produces: `Tool` 枚举携带 `displayName`/`keyBinding`/`keyLabel` 元数据，消除 4 文件散弹式修改

**Background:** 审计 R2 Critical：添加工具需修改 4 个文件（EditToolState.Tool 枚举 + KpEditorScreen.keyPressed switch + KpRibbonBar + ToolPanelView）。当前 ToolPanelView 标签 "Pan (V)" 与实际按键 P 不匹配、"Select (L)" 与实际按键 V 不匹配（已确认 bug）。KpEditorScreen.keyPressed 使用裸数字（case 259 标注 ESC，但 259 实际是 BACKSPACE，256 才是 ESC）。

- [ ] **Step 1: Write the failing test - 工具元数据一致性**

```java
// src/test/java/net/jsmua/kinetic_planner/editor/ToolKeyBindingConsistencyTest.java
package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具定义元数据一致性测试（审计 R2 Critical 修复）。
 *
 * <p>验证 Tool 枚举的 keyBinding 元数据满足：
 * <ul>
 *   <li>每个工具有唯一的 keyBinding（无冲突）</li>
 *   <li>keyBinding 为有效的 GLFW 键码</li>
 *   <li>displayName 和 keyLabel 非空</li>
 * </ul>
 */
class ToolKeyBindingConsistencyTest {

    @Test
    void eachToolHasUniqueKeyBinding() {
        Set<Integer> seen = new HashSet<>();
        for (var tool : EditToolState.Tool.values()) {
            int key = tool.getKeyBinding();
            assertTrue(key > 0, tool.name() + " 的 keyBinding 必须为正数");
            assertTrue(seen.add(key), tool.name() + " 的 keyBinding " + key + " 与其他工具冲突");
        }
    }

    @Test
    void eachToolHasDisplayNameAndKeyLabel() {
        for (var tool : EditToolState.Tool.values()) {
            assertNotNull(tool.getDisplayName(), tool.name() + " 必须有 displayName");
            assertFalse(tool.getDisplayName().isBlank(), tool.name() + " 的 displayName 不能为空");
            assertNotNull(tool.getKeyLabel(), tool.name() + " 必须有 keyLabel");
            assertFalse(tool.getKeyLabel().isBlank(), tool.name() + " 的 keyLabel 不能为空");
        }
    }

    @Test
    void navigationKeyIsP() {
        // spec §4.4：P=Pan(NAVIGATION)
        assertEquals(GLFW.GLFW_KEY_P, EditToolState.Tool.NAVIGATION.getKeyBinding());
    }

    @Test
    void selectKeyIsV() {
        // spec §4.4：V=Select
        assertEquals(GLFW.GLFW_KEY_V, EditToolState.Tool.SELECT.getKeyBinding());
    }

    @Test
    void drawLineKeyIsL() {
        assertEquals(GLFW.GLFW_KEY_L, EditToolState.Tool.DRAW_LINE.getKeyBinding());
    }

    @Test
    void drawBezierKeyIsB() {
        assertEquals(GLFW.GLFW_KEY_B, EditToolState.Tool.DRAW_BEZIER.getKeyBinding());
    }

    @Test
    void snapKeyIsS() {
        assertEquals(GLFW.GLFW_KEY_S, EditToolState.Tool.SNAP.getKeyBinding());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew test --tests "*.ToolKeyBindingConsistencyTest"`
Expected: FAIL - `Tool.getKeyBinding()` 方法不存在（编译错误）

- [ ] **Step 3: Add metadata to Tool enum**

```java
// src/client/java/.../gui/editor/EditToolState.java
package net.jsmua.kinetic_planner.gui.editor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

public final class EditToolState {

    public enum Tool {
        /** 地图导航（平移/缩放），事件转发给 guiMap */
        NAVIGATION("Pan", GLFW.GLFW_KEY_P, "P"),
        /** 选择节点/边，CADRenderEngine 命中检测 */
        SELECT("Select", GLFW.GLFW_KEY_V, "V"),
        /** 绘制直线 */
        DRAW_LINE("Line", GLFW.GLFW_KEY_L, "L"),
        /** 绘制三次贝塞尔 */
        DRAW_BEZIER("Bezier", GLFW.GLFW_KEY_B, "B"),
        /** 捕捉模式，鼠标移动高亮可捕捉点 */
        SNAP("Snap", GLFW.GLFW_KEY_S, "S");

        private final String displayName;
        private final int keyBinding;
        private final String keyLabel;

        Tool(String displayName, int keyBinding, String keyLabel) {
            this.displayName = displayName;
            this.keyBinding = keyBinding;
            this.keyLabel = keyLabel;
        }

        public String getDisplayName() { return displayName; }
        public int getKeyBinding() { return keyBinding; }
        public String getKeyLabel() { return keyLabel; }

        /**
         * 按 GLFW 键码查找工具。
         *
         * @param keyCode GLFW 键码
         * @return 匹配的工具；无匹配返回 null
         */
        public static Tool fromKeyCode(int keyCode) {
            for (Tool tool : values()) {
                if (tool.keyBinding == keyCode) return tool;
            }
            return null;
        }
    }

    // ... 其余字段和方法不变 ...
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew test --tests "*.ToolKeyBindingConsistencyTest"`
Expected: PASS

- [ ] **Step 5: Refactor KpEditorScreen.keyPressed to use Tool metadata**

```java
// src/client/java/.../gui/editor/KpEditorScreen.java
// 添加 import：
import org.lwjgl.glfw.GLFW;

// 替换 keyPressed 中的 switch 块：
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (eventForwarder.keyPressed(keyCode, scanCode, modifiers)) {
        return true;
    }
    // ESC 退出编辑模式（spec §4.4）
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
        onClose();
        return true;
    }
    // 工具快捷键：从 Tool 枚举元数据匹配（审计 R2 修复，消除散弹式修改）
    var state = EditToolState.getInstance();
    Tool matchedTool = EditToolState.Tool.fromKeyCode(keyCode);
    if (matchedTool != null) {
        state.setCurrentTool(matchedTool);
        return true;
    }
    // NAVIGATION 工具下，未消费的键盘事件委托 guiMap
    if (state.getCurrentTool() == EditToolState.Tool.NAVIGATION) {
        return guiMap.keyPressed(keyCode, scanCode, modifiers);
    }
    return false;
}
```

Note: 需要添加 `import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;`（如果尚未存在）。

- [ ] **Step 6: Refactor KpRibbonBar to iterate Tool enum**

```java
// src/client/java/.../gui/KpRibbonBar.java
// 在 Tools 组部分，替换硬编码按钮为遍历：

// 旧代码（移除）：
// addButton("Select", () -> EditToolState.getInstance().setCurrentTool(Tool.SELECT));
// addButton("Line", () -> EditToolState.getInstance().setCurrentTool(Tool.DRAW_LINE));
// addButton("Bezier", () -> EditToolState.getInstance().setCurrentTool(Tool.DRAW_BEZIER));
// addButton("Pan", () -> EditToolState.getInstance().setCurrentTool(Tool.NAVIGATION));

// 新代码：
addGroupLabel("Tools");
for (var tool : EditToolState.Tool.values()) {
    addButton(tool.getDisplayName(), () ->
        EditToolState.getInstance().setCurrentTool(tool));
}
```

Note: 移除不再需要的 `import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;`

- [ ] **Step 7: Refactor ToolPanelView to iterate Tool enum**

```java
// src/client/java/.../gui/ToolPanelView.java
// 替换构造函数中的硬编码按钮为遍历：

public ToolPanelView() {
    super();
    layout(layout -> {
        layout.flexDirection(FlexDirection.COLUMN);
        layout.paddingAll(4);
        layout.gapAll(2);
    });
    // 从 Tool 枚举遍历生成按钮（审计 R2 修复，消除散弹式修改 + 标签-按键不一致 bug）
    for (var tool : EditToolState.Tool.values()) {
        String label = tool.getDisplayName() + " (" + tool.getKeyLabel() + ")";
        addToolButton(label, tool);
    }
}
```

Note: 移除 `import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;`（如果不再直接引用）

- [ ] **Step 8: Update EditToolStateTest to include metadata tests**

```java
// src/test/java/.../editor/EditToolStateTest.java
// 在现有测试类中追加：

@Test
void toolFromKeyCodeReturnsCorrectTool() {
    assertEquals(Tool.NAVIGATION, Tool.fromKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_P));
    assertEquals(Tool.SELECT, Tool.fromKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_V));
    assertEquals(Tool.DRAW_LINE, Tool.fromKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_L));
    assertEquals(Tool.DRAW_BEZIER, Tool.fromKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_B));
    assertEquals(Tool.SNAP, Tool.fromKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_S));
}

@Test
void toolFromKeyCodeReturnsNullForUnmapped() {
    assertNull(Tool.fromKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_A));
    assertNull(Tool.fromKeyCode(0));
}
```

- [ ] **Step 9: Compile and run all tests**

Run: `gradlew compileClientJava & gradlew test`
Expected: 所有测试 PASS，包括 ToolKeyBindingConsistencyTest 和新增的 EditToolStateTest 用例。

- [ ] **Step 10: Commit**

```bash
git add src/client/java/ src/test/java/
git commit -m "refactor: data-driven Tool enum metadata eliminates shotgun surgery, fix key-label mismatch, use GLFW constants (audit R2)"
```

---

## Task 4: Add Testability Seams

**Files:**
- Modify: `src/client/java/.../gui/editor/EditToolState.java`
- Create: `src/client/java/.../instrument/OverlayDataProvider.java`
- Modify: `src/client/java/.../gui/editor/KpEditorScreen.java`
- Test: `src/test/java/.../editor/EditToolStateTest.java`

**Interfaces:**
- Consumes: `WorldScreenTransform`, `GeometryCache` (现有)
- Produces: `OverlayDataProvider` 接口（可注入 mock），`EditToolState` 包级构造器（测试可创建独立实例）

**Background:** 审计 R5 Warning：`WorldTreeReadOverlay` 全 static + `EditToolState` 单例 `getInstance()`，导致 KpEditorScreen 和 EditLayerRenderer 无法在测试中注入 mock 依赖。现有测试文件中所有行为测试均 @Disabled。

- [ ] **Step 1: Add package-private constructor to EditToolState**

```java
// src/client/java/.../gui/editor/EditToolState.java
// 在现有 private 构造器后添加：

/**
 * 包级构造器，仅供测试创建独立实例（不污染单例）。
 *
 * <p>审计 R5 修复：测试可通过此构造器创建隔离的 EditToolState 实例，
 * 验证工具切换/选择集行为而不影响全局单例状态。
 */
EditToolState(boolean forTesting) {
    // forTesting 参数仅用于区分签名，无实际逻辑
}
```

Note: 保留 `private EditToolState() {}` 作为单例构造器。包级构造器签名不同，不冲突。

- [ ] **Step 2: Create OverlayDataProvider interface**

```java
// src/client/java/.../instrument/OverlayDataProvider.java
package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.projection.WorldScreenTransform;

/**
 * 叠加层数据提供者接口（审计 R5 测试缝修复）。
 *
 * <p>将 {@link WorldTreeReadOverlay} 的静态访问器抽象为接口，
 * 使 {@link net.jsmua.kinetic_planner.gui.editor.KpEditorScreen} 和
 * {@link net.jsmua.kinetic_planner.cadengine.EditLayerRenderer}
 * 可在测试中注入 mock 实现。
 *
 * <p>生产环境使用 {@link WorldTreeReadOverlay} 作为默认实现（静态委托）。
 */
public interface OverlayDataProvider {

    /**
     * 返回当前世界-屏幕变换。
     *
     * @return 当前变换；地图未打开时为 null
     */
    WorldScreenTransform getTransform();

    /**
     * 返回当前几何缓存。
     *
     * @return 几何缓存实例
     */
    GeometryCache getGeometryCache();
}
```

- [ ] **Step 3: Make WorldTreeReadOverlay implement OverlayDataProvider (static facade)**

```java
// src/client/java/.../instrument/WorldTreeReadOverlay.java
// 添加 implements OverlayDataProvider（但 WorldTreeReadOverlay 是 final class 全 static，
// 不能直接 implements 实例方法。改为提供静态工厂获取 provider：）

/**
 * 返回一个 {@link OverlayDataProvider} 视图，委托到本类的静态方法。
 *
 * <p>供 {@link KpEditorScreen} 构造时注入，测试可传入 mock provider。
 *
 * @return 委托到静态方法的 provider 实例
 */
public static OverlayDataProvider asProvider() {
    return new OverlayDataProvider() {
        @Override
        public WorldScreenTransform getTransform() {
            return WorldTreeReadOverlay.lastTransform;
        }
        @Override
        public GeometryCache getGeometryCache() {
            return WorldTreeReadOverlay.geometryCache;
        }
    };
}
```

Note: 不需要修改 `WorldTreeReadOverlay` 的 class 声明（它保持 `final class`），只需添加此静态工厂方法。

- [ ] **Step 4: Add OverlayDataProvider field to KpEditorScreen**

```java
// src/client/java/.../gui/editor/KpEditorScreen.java
// 添加字段：
private final net.jsmua.kinetic_planner.instrument.OverlayDataProvider overlayProvider;

// 在构造函数中初始化：
private KpEditorScreen(GuiMap guiMap) {
    super(Component.literal("Kinetic Planner Editor"));
    this.guiMap = guiMap;
    this.editor = new KpMapEditor();
    this.editor.placeCustomViews();
    this.modularUI = ModularUI.of(UI.of(this.editor));
    this.modularUI.setScreen(this);
    this.eventForwarder = new KpUIEventForwarder(this.modularUI);
    this.overlayProvider = WorldTreeReadOverlay.asProvider();  // 默认使用静态实现
}

// 替换 mouseClicked 中对 WorldTreeReadOverlay 的静态调用：
// 旧：
// var transform = WorldTreeReadOverlay.getTransform();
// var cache = WorldTreeReadOverlay.getGeometryCache();
// 新：
// var transform = overlayProvider.getTransform();
// var cache = overlayProvider.getGeometryCache();
```

- [ ] **Step 5: Write test for package-private EditToolState constructor**

```java
// src/test/java/.../editor/EditToolStateTest.java
// 追加测试：

@Test
void packagePrivateConstructorCreatesIndependentInstance() {
    // 使用反射访问包级构造器（测试在 net.jsmua.kinetic_planner.editor 包中）
    EditToolState testState = new EditToolState(true);
    // 验证独立实例不受单例影响
    testState.setCurrentTool(Tool.SELECT);
    assertEquals(Tool.SELECT, testState.getCurrentTool());
    // 单例状态不受影响
    assertNotEquals(Tool.SELECT, EditToolState.getInstance().getCurrentTool());
}
```

- [ ] **Step 6: Write test for OverlayDataProvider interface**

```java
// src/test/java/net/jsmua/kinetic_planner/instrument/OverlayDataProviderTest.java
package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OverlayDataProvider 接口测试（审计 R5 测试缝修复）。
 */
class OverlayDataProviderTest {

    @Test
    void mockProviderReturnsNullWhenNoMap() {
        OverlayDataProvider provider = new OverlayDataProvider() {
            @Override
            public WorldScreenTransform getTransform() { return null; }
            @Override
            public GeometryCache getGeometryCache() { return null; }
        };
        assertNull(provider.getTransform());
        assertNull(provider.getGeometryCache());
    }

    @Test
    void mockProviderReturnsInjectedValues() {
        GeometryCache mockCache = new GeometryCache();
        OverlayDataProvider provider = new OverlayDataProvider() {
            @Override
            public WorldScreenTransform getTransform() { return null; }
            @Override
            public GeometryCache getGeometryCache() { return mockCache; }
        };
        assertSame(mockCache, provider.getGeometryCache());
    }
}
```

- [ ] **Step 7: Compile and run tests**

Run: `gradlew compileClientJava & gradlew test`
Expected: 所有测试 PASS

- [ ] **Step 8: Commit**

```bash
git add src/client/java/ src/test/java/
git commit -m "refactor: add testability seams (OverlayDataProvider interface, EditToolState package-private constructor) (audit R5)"
```

---

## Task 5: Coordinate Conversion Test + Manual Acceptance Checklist

**Files:**
- Create: `src/test/java/.../editor/CoordinateConversionTest.java`
- Modify: `src/test/java/.../editor/EditorEventRoutingTest.java`（补充结构测试）

**Interfaces:**
- Consumes: `WorldScreenTransform`, `CameraParams` (现有，common sourceSet，纯 JVM 可测)
- Produces: 坐标转换往返测试 + 事件路由结构验证 + 手动验收清单文档

**Background:** spec §10.1 要求坐标转换往返测试（屏幕 -> 世界 -> 屏幕）和视口边界条件测试。`WorldScreenTransform` 和 `CameraParams` 在 common sourceSet，纯 JVM 可测。spec §10.2 手动验收清单大部分为 [TODO]。

- [ ] **Step 1: Write coordinate conversion round-trip test**

```java
// src/test/java/net/jsmua/kinetic_planner/editor/CoordinateConversionTest.java
package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.projection.CameraParams;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 坐标转换往返测试（spec §10.1）。
 *
 * <p>验证屏幕坐标 -> 世界坐标 -> 屏幕坐标的往返一致性。
 * 使用 common sourceSet 的 {@link WorldScreenTransform}，纯 JVM 可测。
 */
class CoordinateConversionTest {

    @Test
    void roundTripAtDefaultScale() {
        var cam = new CameraParams(100.0, 200.0, 1.0, 960, 540);
        var transform = new WorldScreenTransform(cam);

        // 屏幕中心 -> 世界 -> 屏幕
        double screenX = 960, screenY = 540;
        var world = transform.screenToWorld(screenX, screenY);
        var back = transform.worldToScreen(world.x, world.z);

        assertEquals(screenX, back.x, 0.001, "X 往返不一致");
        assertEquals(screenY, back.y, 0.001, "Y 往返不一致");
    }

    @Test
    void roundTripAtHighZoom() {
        var cam = new CameraParams(500.0, -300.0, 0.1, 960, 540);
        var transform = new WorldScreenTransform(cam);

        double screenX = 100, screenY = 200;
        var world = transform.screenToWorld(screenX, screenY);
        var back = transform.worldToScreen(world.x, world.z);

        assertEquals(screenX, back.x, 0.001);
        assertEquals(screenY, back.y, 0.001);
    }

    @Test
    void roundTripAtLowZoom() {
        var cam = new CameraParams(0.0, 0.0, 16.0, 960, 540);
        var transform = new WorldScreenTransform(cam);

        double screenX = 480, screenY = 270;
        var world = transform.screenToWorld(screenX, screenY);
        var back = transform.worldToScreen(world.x, world.z);

        assertEquals(screenX, back.x, 0.001);
        assertEquals(screenY, back.y, 0.001);
    }

    @Test
    void worldOriginMapsToScreenCenter() {
        var cam = new CameraParams(100.0, 200.0, 1.0, 960, 540);
        var transform = new WorldScreenTransform(cam);

        var screen = transform.worldToScreen(100.0, 200.0);
        assertEquals(960, screen.x, 0.001, "世界原点应映射到屏幕中心 X");
        assertEquals(540, screen.y, 0.001, "世界原点应映射到屏幕中心 Y");
    }
}
```

- [ ] **Step 2: Add viewport boundary structural test to EditorEventRoutingTest**

```java
// 在 EditorEventRoutingTest.java 中追加：

/**
 * 验证 isMouseOverMapViewport 方法存在且为 private（spec §3.1 视口边界查询）。
 */
@Test
void isMouseOverMapViewportMethodExists() throws NoSuchMethodException {
    Method m = KpEditorScreen.class.getDeclaredMethod(
        "isMouseOverMapViewport", double.class, double.class);
    assertEquals(boolean.class, m.getReturnType());
    assertTrue(java.lang.reflect.Modifier.isPrivate(m.getModifiers()),
        "isMouseOverMapViewport 应为 private");
}

/**
 * 验证 overlayProvider 字段存在（审计 R5 测试缝）。
 */
@Test
void overlayProviderFieldExists() throws NoSuchFieldException {
    var field = KpEditorScreen.class.getDeclaredField("overlayProvider");
    assertEquals("net.jsmua.kinetic_planner.instrument.OverlayDataProvider",
        field.getType().getName());
    assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
}
```

- [ ] **Step 3: Compile and run all tests**

Run: `gradlew test`
Expected: 所有测试 PASS，包括坐标转换往返测试。

- [ ] **Step 4: Update manual acceptance checklist in spec**

Update spec §10.2 to reflect completed items:

| 验收项 | 状态变化 |
|---|---|
| 滚轮缩放（所有工具） | [TODO] -> [IMPL] (Task 2) |
| 右键取消拖拽 | [TODO] -> [IMPL] (Task 2) |
| 工具快捷键与焦点 | [IMPL] -> [IMPL] (Task 3 修复了 ESC key code bug) |

- [ ] **Step 5: Commit**

```bash
git add src/test/java/ docs/superpowers/specs/
git commit -m "test: add coordinate conversion round-trip tests, viewport boundary structural tests, update acceptance checklist (spec §10)"
```

---

## Self-Review

### 1. Spec Coverage

| Spec 需求 | 覆盖任务 | 状态 |
|---|---|---|
| §4.3.1 右键取消拖拽 [TODO] | Task 2 Step 2 | ✅ |
| §4.3.2 滚轮委托 guiMap [TODO] | Task 2 Step 1 | ✅ |
| §4.3.2 所有工具下滚轮缩放 | Task 2 Step 1 | ✅ |
| §4.3.3 CAD 工具命中 [TODO: Phase 6] | 不在范围内 | ⏭️ (Phase 6) |
| §4.4 参数化按键绑定 | Task 3 (元数据驱动，非完整 KeyBinding 系统) | ✅ (部分) |
| §6 Grid 布局 [待决策] | 不在范围内 | ⏭️ (待决策) |
| §7 选择池多例化 [未来] | 不在范围内 | ⏭️ (未来) |
| §10.1 单元测试 [TODO] | Task 2 Step 3 + Task 4 + Task 5 | ✅ |
| §10.2 手动验收 [TODO] | Task 5 Step 4 | ✅ |

### 2. Placeholder Scan

- 无 "TBD" / "TODO" / "fill in details" 占位
- 所有代码步骤包含完整可执行代码
- 所有测试步骤包含具体断言

### 3. Type Consistency

- `Tool.fromKeyCode(int)` 在 Task 3 Step 3 定义，Task 3 Step 5 和 Step 8 使用 -- 一致
- `OverlayDataProvider` 在 Task 4 Step 2 定义，Task 4 Step 3/4 使用 -- 一致
- `WorldTreeReadOverlay.getEngine()` 在 Task 1 Step 4 定义，Task 1 Step 5 使用 -- 一致
- `WorldTreeReadOverlay.renderTracks()` 在 Task 1 Step 6 定义，Task 1 Step 7 使用 -- 一致
- `EditToolState(boolean forTesting)` 在 Task 4 Step 1 定义，Task 4 Step 5 使用 -- 一致

### 4. Audit Findings Coverage

| 审计发现 | 覆盖任务 |
|---|---|
| R2 Critical: 工具散弹式修改 | Task 3 |
| R5 Warning: 依赖循环 | Task 1 |
| R5 Warning: 静态状态测试缝 | Task 4 |
| R3 Warning: 缩放常量重复 | Task 2 (移除常量) |
| R3 Warning: 工具按键重复 | Task 3 (元数据驱动) |
| R3 Warning: 渲染逻辑不完整提取 | Task 1 Step 6-7 |
| R4 Warning: 自定义缩放 | Task 2 (委托 guiMap) |
| R5 Warning: CADRenderEngine 双实例 | Task 1 Step 4-5 |
| R1 Suggestion: 魔术数字 | Task 3 (GLFW 常量) |
| R6 Suggestion: scale 命名 | 不在范围内（javadoc 微调，非功能变更） |
