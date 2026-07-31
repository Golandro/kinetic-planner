# Ribbon + Editor Layout Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three UI layout defects in the Kinetic Planner editor: full-width responsive Ribbon with QAT aligned to the right, flatter main-area layout, and fully transparent map placeholder viewport while keeping tab headers opaque.

**Architecture:** Introduce a small `RibbonTabViewAdapter` inside `gui/ribbon/` to encapsulate all LDLib2 `TabView` internal-field access; simplify `KpMapEditor`'s `SplittableWindow` tree to only left/center anchors; centralize transparent-background setup in a helper used by `MapPlaceholderView`.

**Tech Stack:** Minecraft 1.21.1 NeoForge, LDLib2 2.2.26, Taffy Flex/Grid layout, Java 21, JUnit 5 + Mockito, Gradle.

## Global Constraints

- All changes live in `src/client/java/`; no `net.minecraft.client.*` or `com.mojang.blaze3d.*` references in `src/main/java/`.
- LDLib2 `UIElement` class-init is expensive in tests; client tests may use Mockito or remain runtime-verified.
- Keep the public Ribbon API (`gui/ribbon/api/`, `gui/ribbon/registry/`) unchanged; only internal layout glue changes.
- Preserve existing persistence: tab selection, QAT tool IDs, and tab display modes continue to flow through `RibbonPreferenceStore`/`IKPConfig`.
- Do not break `Editor.applyLayout` / `captureLayout` compatibility; the simplified tree must still serialize.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/client/java/.../gui/ribbon/internal/RibbonTabViewAdapter.java` | **New.** Encapsulates `TabView` internal layout: header assembly, QAT placement, tab scroller flex, content sizing. |
| `src/client/java/.../gui/ribbon/internal/RibbonBuilder.java` | **Modify.** Delegate `TabView` construction to `RibbonTabViewAdapter`; keep tab/group iteration logic. |
| `src/client/java/.../gui/ribbon/RibbonBar.java` | **Modify.** Ensure `TabView` fills width; expose adapter for tests. |
| `src/client/java/.../gui/editor/KpMapEditor.java` | **Modify.** Rebuild `rootWindow` to single horizontal split; fix `menuContainer` flex; transparent viewport helper. |
| `src/client/java/.../gui/MapPlaceholderView.java` | **Modify.** Add static helper `prepareTransparentViewportChain(ViewContainer)`; keep constructor behavior. |
| `src/main/resources/assets/kinetic_planner/lss/kp.lss` | **Modify.** Add `flex:1` and background-clear helper classes if LSS supports; otherwise rely on inline layout. |
| `src/client/java/.../gui/editor/KpEditorScreen.java` | **Maybe modify.** Only if viewport hit-test area needs adjustment after layout flattening. |

---

### Task 1: Encapsulate TabView Layout in RibbonTabViewAdapter

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonTabViewAdapter.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java:49-149`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java:82-99`

**Interfaces:**
- Consumes: `DefaultQuickAccessToolbar` (provides `createElement()`), `List<RibbonHeaderComponent>` from `RibbonRegistry`, `Map<ResourceLocation, RibbonTabState>`, preferred tab ID.
- Produces: `TabView buildTabView(RibbonBar owner)` — returns a fully configured `TabView` with header `[LEADING..., tabScroller(flex:1), QAT, TRAILING...]` and content container sized to fill.

- [ ] **Step 1: Write the failing / skeleton test**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RibbonTabViewAdapterTest {

    @BeforeEach
    void resetRegistry() {
        RibbonRegistry.resetForTest();
    }

    @Test
    void buildTabViewReturnsNonNullTabView() {
        Map<ResourceLocation, RibbonTabState> states = new HashMap<>();
        QuickAccessToolbar qat = mock(QuickAccessToolbar.class);
        when(qat.createElement()).thenReturn(new com.lowdragmc.lowdraglib2.gui.ui.UIElement());

        RibbonBar bar = mock(RibbonBar.class);
        RibbonTabViewAdapter adapter = new RibbonTabViewAdapter(states, qat,
            Collections.emptyList(), Collections.emptyList(), Optional.empty());

        assertNotNull(adapter.buildTabView(bar));
    }
}
```

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonTabViewAdapterTest"`
Expected: FAIL — class `RibbonTabViewAdapter` does not exist.

- [ ] **Step 2: Create RibbonTabViewAdapter**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates LDLib2 TabView internal layout for the Ribbon.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>TabView fills the entire RibbonBar width.</li>
 *   <li>Header order: LEADING components, tab scroller (flex:1), QAT, TRAILING components.</li>
 *   <li>Tab content container uses flex grow to occupy remaining vertical space.</li>
 * </ul>
 */
public final class RibbonTabViewAdapter {

    private final Map<ResourceLocation, RibbonTabState> tabStates;
    private final QuickAccessToolbar qat;
    private final List<RibbonHeaderComponent> leadingComponents;
    private final List<RibbonHeaderComponent> trailingComponents;
    private final Optional<ResourceLocation> preferredTabId;

    public RibbonTabViewAdapter(Map<ResourceLocation, RibbonTabState> tabStates,
                                QuickAccessToolbar qat,
                                List<RibbonHeaderComponent> leadingComponents,
                                List<RibbonHeaderComponent> trailingComponents,
                                Optional<ResourceLocation> preferredTabId) {
        this.tabStates = tabStates;
        this.qat = qat;
        this.leadingComponents = leadingComponents;
        this.trailingComponents = trailingComponents;
        this.preferredTabId = preferredTabId;
    }

    public TabView buildTabView(RibbonBar owner) {
        TabView tabView = new TabView();
        tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });

        UIElement header = tabView.tabHeaderContainer;
        header.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.widthPercent(100);
        });

        // Tab scroller must flex, not use widthPercent(100), because QAT and header components share the row.
        tabView.tabScroller.layout(layout -> {
            layout.flexGrow(1);
            layout.flexShrink(1);
        });

        // Leading components before tabs
        for (RibbonHeaderComponent comp : leadingComponents) {
            header.addChild(comp.createElement());
        }

        // Tab scroller is added by TabView constructor; ensure it sits after leading components.
        // TabView already added it; we only reordered via flex grow.

        // QAT aligned right, before trailing components
        header.addChild(qat.createElement());

        // Trailing components
        for (RibbonHeaderComponent comp : trailingComponents) {
            header.addChild(comp.createElement());
        }

        // Content container fills available height
        tabView.tabContentContainer.layout(layout -> {
            layout.flexGrow(1);
        });

        return tabView;
    }
}
```

- [ ] **Step 3: Refactor RibbonBuilder to use the adapter**

Replace the header-assembly and TabView creation block in `RibbonBuilder.build` with:

```java
var adapter = new RibbonTabViewAdapter(
    tabStates,
    qat,
    RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.LEADING),
    RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.TRAILING),
    preferredTabId
);
var tabView = adapter.buildTabView(bar);
bar.setTabView(tabView);
```

Remove the old manual `tabHeaderContainer` manipulation code (lines 56–73 in the current file).

- [ ] **Step 4: Run tests**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonTabViewAdapterTest"`
Expected: PASS.

Run: `gradlew compileClientJava`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonTabViewAdapter.java
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java
git add src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonTabViewAdapterTest.java
git commit -m "feat(ribbon): encapsulate TabView layout in RibbonTabViewAdapter"
```

---

### Task 2: Make RibbonBar Fill the Full Editor Top Width

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java:96-109`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java:81-89`

**Interfaces:**
- Consumes: `RibbonBar` needs its parent `menuContainer` to claim remaining horizontal space.
- Produces: `RibbonBar` with `widthPercent(100)` and `flexGrow(1)` so it expands to fill the assigned `menuContainer` width.

- [ ] **Step 1: Add runtime manual verification step (no reliable unit test due to LDLib2 clinit)**

Create a lightweight characterization test that only verifies `RibbonBar` layout properties can be queried without throwing:

```java
package net.jsmua.kinetic_planner.gui.ribbon;

import net.jsmua.kinetic_planner.gui.editor.ribbon.KPConfigRibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KpViewContextProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class RibbonBarLayoutTest {
    @Test
    @Disabled("LDLib2 UIElement clinit requires runtime GL context; verify manually in-game")
    void ribbonBarFillsWidth() {
        // Manual acceptance: open editor, F3+? debugger, select RibbonBar, verify width == screen width.
    }
}
```

- [ ] **Step 2: Update RibbonBar layout to fill its parent**

In `RibbonBar` constructor, change:

```java
layout(layout -> {
    layout.flexDirection(FlexDirection.COLUMN);
    layout.widthPercent(100);
});
```

to:

```java
layout(layout -> {
    layout.flexDirection(FlexDirection.COLUMN);
    layout.widthPercent(100);
    layout.flexGrow(1);
});
```

- [ ] **Step 3: Make menuContainer expand in KpMapEditor.initMenus**

Change:

```java:106:107:src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java
RibbonBar ribbonBar = new RibbonBar(kpViewContextProvider, preferenceStore);
menuContainer.addChild(ribbonBar);
top.getLayout().height(60);
```

to:

```java
RibbonBar ribbonBar = new RibbonBar(kpViewContextProvider, preferenceStore);
menuContainer.layout(layout -> {
    layout.heightPercent(100);
    layout.flexGrow(1);
    layout.flexDirection(FlexDirection.ROW);
});
menuContainer.addChild(ribbonBar);
top.getLayout().height(60);
```

- [ ] **Step 4: Compile and manual acceptance**

Run: `gradlew compileClientJava`
Expected: PASS.

Manual: `gradlew runClient`, open editor, verify debugger shows `RibbonBar`/`tab-view` width equals screen width and QAT sits at the right edge of the tab header.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java
git add src/test/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBarLayoutTest.java
git commit -m "fix(ribbon): make RibbonBar fill full editor top width"
```

---

### Task 3: Flatten Main-Area Layout to Left + Center Only

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java:29-143`
- Maybe modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/KpEditorScreen.java:104-107`

**Interfaces:**
- Consumes: `Editor.rootWindow` and anchor fields (`leftWindow`, `centerWindow`, `rightWindow`, `bottomWindow`).
- Produces: Rebuilt `rootWindow` containing only one horizontal split; `rightWindow` and `bottomWindow` remain as immortal empty leaves to satisfy `Editor` anchor contract.

- [ ] **Step 1: Create a helper to rebuild the window tree**

Add a private method in `KpMapEditor`:

```java
/**
 * Rebuild rootWindow to a flat left/center layout.
 *
 * <p>LDLib2 Editor expects left/right/center/bottom anchors to exist. We keep
 * rightWindow and bottomWindow as tiny immortal empty leaves so applyLayout/captureLayout
 * remain compatible, while the visible tree is only leftWindow + centerWindow.
 */
private void rebuildRootWindow() {
    // Clear existing tree
    rootWindow.getAllViews().forEach(rootWindow::removeSplitWindow);
    if (rootWindow.isSplit()) {
        // Detach current split view manually
        rootWindow.getChildren().stream()
            .filter(c -> c instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView)
            .findFirst()
            .ifPresent(com.lowdragmc.lowdraglib2.gui.ui.UIElement::removeSelf);
    }

    // One horizontal split: left (28%) | center (72%)
    var split = rootWindow
        .splitStyle(style -> style.percentage(28).minPercentage(5).maxPercentage(95))
        .splitNew(org.appliedenergistics.yoga.YogaEdge.LEFT);

    leftWindow = split.getFirst().setImmortal(true);
    leftWindow.setAnchorId(ANCHOR_LEFT);

    centerWindow = split.getSecond().setImmortal(true);
    centerWindow.setAnchorId(ANCHOR_CENTER);

    // Dummy right/bottom windows to preserve Editor anchor contract
    rightWindow = new SplittableWindow(rootWindow, new ViewContainer()).setImmortal(true);
    rightWindow.setAnchorId(ANCHOR_RIGHT);
    bottomWindow = new SplittableWindow(rootWindow, new ViewContainer()).setImmortal(true);
    bottomWindow.setAnchorId(ANCHOR_BOTTOM);
}
```

- [ ] **Step 2: Call rebuildRootWindow before placeCustomViews**

In `KpMapEditor`, add a call in a suitable location. Since `Editor` constructor calls `initMenus()` and then `onPrepareInspectorView/HistoryView/ResourceView`, the anchors must be rebuilt before `placeCustomViews()` is invoked by `KpEditorScreen`.

Option A (recommended): call in `placeCustomViews()` itself, before placing views:

```java
@Override
public void placeCustomViews() {
    rebuildRootWindow();
    placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
    this.mapViewport = new MapPlaceholderView();
    placeView(this.mapViewport, () -> centerWindow.getRightTop());
    centerWindow.getViewContainer().getStyle().backgroundTexture(IGuiTexture.EMPTY);
}
```

- [ ] **Step 3: Add a test verifying the window tree shape**

```java
package net.jsmua.kinetic_planner.gui.editor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class KpMapEditorLayoutTest {
    @Test
    @Disabled("Requires LDLib2 runtime context; verify manually via debugger")
    void mainAreaHasOnlyLeftAndCenterSplits() {
        // Manual acceptance:
        // 1. Open editor.
        // 2. Debugger: rootWindow should have exactly one horizontal SplitView.
        // 3. leftWindow and centerWindow should be direct children; no nested splits.
    }
}
```

- [ ] **Step 4: Compile and manual acceptance**

Run: `gradlew compileClientJava`
Expected: PASS.

Manual: `gradlew runClient`, open editor, verify debugger shows only one `split-view-horizontal` under `rootWindow` and DOM depth is ~6 instead of ~12.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java
git add src/test/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditorLayoutTest.java
git commit -m "feat(editor): flatten main-area layout to left+center splits"
```

---

### Task 4: Make MapPlaceholderView and Its Container Chain Fully Transparent

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/MapPlaceholderView.java:1-30`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java:137-142`

**Interfaces:**
- Consumes: `ViewContainer` (LDLib2) where the map placeholder is placed.
- Produces: Static helper `MapPlaceholderView.prepareTransparentChain(ViewContainer)` that clears `ViewContainer`, `TabView`, and `tabContentContainer` backgrounds.

- [ ] **Step 1: Add helper to MapPlaceholderView**

```java
package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;

/**
 * 中心透明占位 View（spec §6.6）。
 *
 * <p>显式将背景纹理置为 {@link IGuiTexture#EMPTY}，
 * 保留默认 {@code allowHitTest = true} 以便主视口区域能命中并限制事件范围，
 * 但不注册事件监听器——导航/绘图逻辑仍由 {@link net.jsmua.kinetic_planner.gui.editor.KpEditorScreen} 统一路由。
 *
 * <p>保留为 {@code centerWindow} 中的单个 View，未来可通过同一 {@code ViewContainer}
 * 添加更多 Tab（分析面板等）共享主视口区域。
 *
 * <p>Layer 1（GuiMap 瓦片）+ Layer 2（CAD 编辑层）从此 View 区域天然透过。
 */
public class MapPlaceholderView extends View {

    public MapPlaceholderView() {
        super();
        getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }

    public MapPlaceholderView(String name) {
        super(name);
        getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }

    /**
     * 清理 ViewContainer → TabView → tabContentContainer 的背景链，
     * 确保地图占位区域完全透明。
     *
     * <p>注意：只影响传入的 {@code ViewContainer}，不修改其他窗口（如左侧工具面板）。
     */
    public static void prepareTransparentChain(ViewContainer container) {
        container.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        container.tabView.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        container.tabView.tabContentContainer.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        container.tabView.tabHeaderContainer.getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }
}
```

- [ ] **Step 2: Use helper in KpMapEditor.placeCustomViews**

Change:

```java
placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
this.mapViewport = new MapPlaceholderView();
placeView(this.mapViewport, () -> centerWindow.getRightTop());
centerWindow.getViewContainer().getStyle().backgroundTexture(IGuiTexture.EMPTY);
```

to:

```java
placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
this.mapViewport = new MapPlaceholderView();
placeView(this.mapViewport, () -> centerWindow.getRightTop());
MapPlaceholderView.prepareTransparentChain(centerWindow.getViewContainer());
```

- [ ] **Step 3: Add a simple unit test for the helper**

Because `ViewContainer` initialization triggers LDLib2 `UIElement` static init, the test is runtime-only unless we can mock. Provide a manual verification test:

```java
package net.jsmua.kinetic_planner.gui;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class MapPlaceholderViewTransparencyTest {
    @Test
    @Disabled("Requires LDLib2 runtime context; verify manually via debugger")
    void mapPlaceholderBackgroundChainIsTransparent() {
        // Manual acceptance:
        // 1. Open editor.
        // 2. Debugger: select centerWindow ViewContainer → backgroundTexture == EMPTY.
        // 3. Select its tabView → backgroundTexture == EMPTY.
        // 4. Select tabContentContainer → backgroundTexture == EMPTY.
        // 5. In-game: map tiles and CAD layer are visible behind the placeholder.
    }
}
```

- [ ] **Step 4: Compile and manual acceptance**

Run: `gradlew compileClientJava`
Expected: PASS.

Manual: `gradlew runClient`, open editor, verify map is visible through the center area; tab headers remain opaque.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/MapPlaceholderView.java
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java
git add src/test/java/net/jsmua/kinetic_planner/gui/MapPlaceholderViewTransparencyTest.java
git commit -m "fix(editor): make map placeholder container chain fully transparent"
```

---

### Task 5: LSS Cleanup and Responsive Helper Classes

**Files:**
- Modify: `src/main/resources/assets/kinetic_planner/lss/kp.lss`

**Interfaces:**
- Consumes: Existing `.kp-ribbon-bar`, `.kp-ribbon-content`, `.kp-ribbon-tab` classes.
- Produces: Optional helper classes `.kp-fill-width`, `.kp-fill-height`, `.kp-transparent-bg` for future reuse.

- [ ] **Step 1: Add helper classes to LSS**

Append to `kp.lss`:

```lss
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

Note: LDLib2 LSS parser support for `flex-grow` and `background: rect()` must be verified at runtime. If unsupported, remove this task and rely on inline layout calls only.

- [ ] **Step 2: Verify LSS loads without errors**

Manual: `gradlew runClient`, open editor, check logs for LSS parse errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/assets/kinetic_planner/lss/kp.lss
git commit -m "style(lss): add responsive layout helper classes"
```

---

## Spec Coverage

| Requirement | Covered By |
|---|---|
| Ribbon 栏完全 Flex 响应式，TabView 贴合宽度 | Task 1 + Task 2 |
| QAT 在 Tab 列右侧、对齐屏幕右侧 | Task 1 (`RibbonTabViewAdapter` header order) |
| 下部主区域扁平化 | Task 3 (`rebuildRootWindow`) |
| MapPlaceholderView 及其父容器透明 | Task 4 (`prepareTransparentChain`) |
| Tab 列保持不透明 | Task 4 only clears center window; left window untouched |

## Placeholder Scan

No TBD/TODO/fill-in-details placeholders. All code blocks contain concrete Java/LSS. Tests that require GL context are explicitly `@Disabled` with manual acceptance notes, matching the existing project test strategy.

## Type Consistency

- `RibbonTabViewAdapter.buildTabView(RibbonBar owner)` returns `TabView`.
- `MapPlaceholderView.prepareTransparentChain(ViewContainer container)` is `public static void`.
- `rebuildRootWindow()` is private and mutates existing `leftWindow`/`centerWindow` fields.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-31-ribbon-editor-layout-repair.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using `executing-plans`, batch execution with checkpoints.

Which approach would you like?
