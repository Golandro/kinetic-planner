# Kinetic Planner P0 Refactor & Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** sourceSet 重构（添加 server sourceSet）+ 命令注册迁移到客户端命令 + 提取 OverlayControl/ThemeManager + 修复现有 5 条命令 + 新增 9 条 P0 命令，共 14 个命令节点。

**Architecture:** 三阶段推进：(1) sourceSet 基础设施 + WorldTreeReadOverlay 增强，(2) ThemeManager + OverlayControl 提取，(3) 命令注册迁移 + KPCommands 完整重写。每阶段独立编译验证。

**Tech Stack:** Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21 / Create 6.0.10 / Cloth Config 15.0.140 / JUnit 5 / Mockito 5

## Global Constraints

- Minecraft 1.21.1，NeoForge 21.1.235，Java 21（toolchain）
- mod_id: `kinetic_planner`，mod_group_id: `net.jsmua.kinetic_planner`，包名 `net.jsmua.kinetic_planner`
- sourceSet 分离：`main`（common）不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`；`client` 可引用全部；`server`（新增）可引用 main + 服务端 API
- Mixin 配置 `defaultRequire: 0`，无 MixinPlugin
- MC 1.21.1 VertexConsumer API：`addVertex` / `setColor` / `setNormal`，无 `endVertex`
- Shell 是 `cmd.exe`，多命令用 `&` 或 `&&` 分隔
- NeoForge 1.21.1 客户端命令：`RegisterClientCommandsEvent` 在 `net.neoforged.neoforge.client.event` 包，`getDispatcher()` 返回 `CommandDispatcher<CommandSourceStack>`（与 `RegisterCommandsEvent` 类型相同）
- `ClientCommandSourceStack` 继承 `CommandSourceStack`，`sendSuccess(Supplier<Component>, boolean)` 签名不变

---

## File Structure

### 新建文件

| 路径 | sourceSet | 职责 |
|---|---|---|
| `src/server/java/.gitkeep` | server | 占位（P0 server sourceSet 为空） |
| `src/client/java/net/jsmua/kinetic_planner/config/OverlayControl.java` | client | 叠加层状态管理（开关/重载/状态） |
| `src/client/java/net/jsmua/kinetic_planner/config/ThemeManager.java` | client | 主题管理（扫描/加载/切换/重载/重置） |
| `src/test/java/net/jsmua/kinetic_planner/config/ThemeManagerTest.java` | test | ThemeManager 纯方法单测 |

### 修改文件

| 路径 | 修改内容 |
|---|---|
| `build.gradle` | 添加 server sourceSet + 配置 |
| `src/client/java/.../KineticPlannerClient.java` | 命令注册迁移到 RegisterClientCommandsEvent |
| `src/client/java/.../config/KPCommands.java` | 完整重写：14 命令 + 委托 Manager |
| `src/client/java/.../instrument/WorldTreeReadOverlay.java` | 添加 OVERLAY_ENABLED 检查 + 统计/诊断方法 |

---

## Task 1: sourceSet 重构 -- 添加 server sourceSet

**Files:**
- Modify: `build.gradle`
- Create: `src/server/java/.gitkeep`

**Interfaces:**
- Produces: `sourceSets.server`（Gradle 配置就绪，内容为空）

- [ ] **Step 1: 创建 server sourceSet 目录**

创建 `src/server/java/.gitkeep`（空文件，确保目录被 Git 跟踪）。

- [ ] **Step 2: 修改 build.gradle -- 添加 server sourceSet**

在 `sourceSets` 块中，在 `client` 块之后添加 `server` 块。将：

```groovy
sourceSets {
    main {
        java {
            // common 源码：不含 client 包
            exclude 'net/jsmua/kinetic_planner/client/**'
        }
    }
    client {
        java {
            srcDir 'src/client/java'
        }
        resources {
            srcDir 'src/client/resources'
        }
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
}
```

替换为：

```groovy
sourceSets {
    main {
        java {
            // common 源码：不含 client 包
            exclude 'net/jsmua/kinetic_planner/client/**'
        }
    }
    client {
        java {
            srcDir 'src/client/java'
        }
        resources {
            srcDir 'src/client/resources'
        }
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
    server {
        java {
            srcDir 'src/server/java'
        }
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
}
```

- [ ] **Step 3: 修改 build.gradle -- 添加 server configurations**

在 `configurations` 块中添加 server 配置。将：

```groovy
configurations {
    clientImplementation.extendsFrom implementation
    clientCompileOnly.extendsFrom compileOnly
    clientRuntimeOnly.extendsFrom runtimeOnly
}
```

替换为：

```groovy
configurations {
    clientImplementation.extendsFrom implementation
    clientCompileOnly.extendsFrom compileOnly
    clientRuntimeOnly.extendsFrom runtimeOnly
    serverImplementation.extendsFrom implementation
    serverCompileOnly.extendsFrom compileOnly
    serverRuntimeOnly.extendsFrom runtimeOnly
}
```

- [ ] **Step 4: 修改 build.gradle -- neoForge mods block 添加 server**

在 `neoForge.mods` 块中添加 server sourceSet 绑定。将：

```groovy
    mods {
        // define mod <-> source bindings
        "${mod_id}" {
            sourceSet(sourceSets.main)
            sourceSet(sourceSets.client)
        }
    }
```

替换为：

```groovy
    mods {
        // define mod <-> source bindings
        "${mod_id}" {
            sourceSet(sourceSets.main)
            sourceSet(sourceSets.client)
            sourceSet(sourceSets.server)
        }
    }
```

- [ ] **Step 5: 修改 build.gradle -- jar 包含 server output**

将：

```groovy
jar {
    from sourceSets.client.output
}
```

替换为：

```groovy
jar {
    from sourceSets.client.output
    from sourceSets.server.output
}
```

- [ ] **Step 6: 修改 build.gradle -- test 可访问 server sourceSet**

将：

```groovy
// 让测试能访问 client sourceSet
sourceSets.test.compileClasspath += sourceSets.client.output
sourceSets.test.runtimeClasspath += sourceSets.client.output
```

替换为：

```groovy
// 让测试能访问 client 和 server sourceSet
sourceSets.test.compileClasspath += sourceSets.client.output
sourceSets.test.runtimeClasspath += sourceSets.client.output
sourceSets.test.compileClasspath += sourceSets.server.output
sourceSets.test.runtimeClasspath += sourceSets.server.output
```

- [ ] **Step 7: 修改 build.gradle -- idea sourceDirs 添加 server**

将：

```groovy
idea {
    module {
        downloadSources = true
        downloadJavadoc = true
        sourceDirs += sourceSets.client.java.srcDirs
    }
}
```

替换为：

```groovy
idea {
    module {
        downloadSources = true
        downloadJavadoc = true
        sourceDirs += sourceSets.client.java.srcDirs
        sourceDirs += sourceSets.server.java.srcDirs
    }
}
```

- [ ] **Step 8: 构建验证**

运行：`gradlew compileJava compileClientJava compileServerJava`
预期：BUILD SUCCESSFUL（server sourceSet 为空，编译应通过）

- [ ] **Step 9: 运行现有测试验证无回归**

运行：`gradlew test`
预期：全部 29 个测试 PASS（2 个 @Disabled）

- [ ] **Step 10: 提交**

```bash
git add -A
git commit -m "refactor: add server sourceSet to Gradle configuration"
```

---

## Task 2: WorldTreeReadOverlay 增强 -- OVERLAY_ENABLED 检查 + 统计/诊断方法

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`

**Interfaces:**
- Consumes: `KPConfig.OVERLAY_ENABLED`（现有）
- Produces: `WorldTreeReadOverlay.getGraphCount()` / `getNodeCount()` / `getEdgeCount()` / `getEdgePointCount()`
- Produces: `WorldTreeReadOverlay.dumpData()`
- Produces: `WorldTreeReadOverlay.getLayerCounts()`
- Produces: `WorldTreeReadOverlay.getOverlayAnchors()`

- [ ] **Step 1: 添加 OVERLAY_ENABLED 检查**

在 `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java` 中，在 `onMapRender` 方法的开头（`if (lastContext == null || lastTransform == null) return;` 之前）添加配置检查。

在文件顶部添加 import：

```java
import net.jsmua.kinetic_planner.config.KPConfig;
```

将 `onMapRender` 方法的开头：

```java
    public static void onMapRender(Object guiMap, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (lastContext == null || lastTransform == null) return;
```

替换为：

```java
    public static void onMapRender(Object guiMap, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!KPConfig.OVERLAY_ENABLED.get()) return;
        if (lastContext == null || lastTransform == null) return;
```

- [ ] **Step 2: 添加统计方法**

在 `WorldTreeReadOverlay` 类中（在 `applyAlpha` 方法之前）添加以下统计方法：

```java
    /**
     * 当前缓存的轨道图数量。
     */
    public static int getGraphCount() {
        return geometryCache.geometries().size();
    }

    /**
     * 当前缓存的节点总数。
     */
    public static int getNodeCount() {
        int count = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            count += geom.nodes().size();
        }
        return count;
    }

    /**
     * 当前缓存的边总数。
     */
    public static int getEdgeCount() {
        int count = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            count += geom.edges().size();
        }
        return count;
    }

    /**
     * 当前缓存的边点总数。
     */
    public static int getEdgePointCount() {
        int count = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            count += geom.edgePoints().size();
        }
        return count;
    }

    /**
     * 将当前维度 TrackGraph 数据转储到日志。
     */
    public static void dumpData() {
        KineticPlannerMod.LOGGER.info("[KP] === TrackGraph Dump ===");
        if (lastContext == null) {
            KineticPlannerMod.LOGGER.info("[KP] No map context (map not open)");
            return;
        }
        KineticPlannerMod.LOGGER.info("[KP] Dimension: {}", lastContext.dimension().location());
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            KineticPlannerMod.LOGGER.info("[KP] Graph {} | color={}",
                geom.graphId(), String.format("%08X", geom.graphColor()));
            KineticPlannerMod.LOGGER.info("[KP]   Nodes: {}", geom.nodes().size());
            for (Vec3 node : geom.nodes()) {
                KineticPlannerMod.LOGGER.info("[KP]     ({}, {}, {})",
                    node.x, node.y, node.z);
            }
            KineticPlannerMod.LOGGER.info("[KP]   Edges: {}", geom.edges().size());
            for (EdgeGeometry edge : geom.edges()) {
                KineticPlannerMod.LOGGER.info("[KP]     {} | {} -> {}",
                    edge.type(), edge.p1(), edge.p2());
            }
            KineticPlannerMod.LOGGER.info("[KP]   EdgePoints: {}", geom.edgePoints().size());
        }
        KineticPlannerMod.LOGGER.info("[KP] === End Dump ===");
    }

    /**
     * 各图层对象计数字符串。
     */
    public static String getLayerCounts() {
        int tracks = 0, nodes = 0, edgePoints = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            tracks += geom.edges().size();
            nodes += geom.nodes().size();
            edgePoints += geom.edgePoints().size();
        }
        return String.format("[KP] Layers | Tracks: %d | Nodes: %d | EdgePoints: %d",
            tracks, nodes, edgePoints);
    }

    /**
     * 叠加层锚点诊断字符串。
     */
    public static String getOverlayAnchors() {
        if (lastContext == null || lastTransform == null) {
            return "[KP] No map context (map not open)";
        }
        return String.format(
            "[KP] Anchors | CamX: %.1f | CamZ: %.1f | BPP: %.4f | CenterX: %d | CenterY: %d | ScreenW: %d | ScreenH: %d | DPR: %.2f | Dim: %s",
            lastTransform.cam().cameraBlockX(),
            lastTransform.cam().cameraBlockZ(),
            lastTransform.cam().blocksPerPixel(),
            lastTransform.cam().screenCenterX(),
            lastTransform.cam().screenCenterY(),
            lastContext.screenWidth(),
            lastContext.screenHeight(),
            lastContext.dpr(),
            lastContext.dimension().location());
    }
```

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "fix: add OVERLAY_ENABLED check in onMapRender, add stats/dump/diagnostic methods"
```

---

## Task 3: ThemeManager -- 主题管理（client，含纯方法单测）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/ThemeManager.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/config/ThemeManagerTest.java`

**Interfaces:**
- Consumes: `ThemeSerializer.serialize/deserialize`（main，现有）, `Theme.defaultValue()`（main，现有）, `KPConfig.THEME_ACTIVE`（client，现有）, `WorldTreeReadOverlay.setTheme()`（client，Task 2 增强）
- Produces: `ThemeManager.listThemeNames(Path): List<String>` -- 纯方法
- Produces: `ThemeManager.loadThemeFile(Path, String): Theme` -- 纯方法
- Produces: `ThemeManager.getThemesDir(): Path` -- 运行时路径
- Produces: `ThemeManager.setTheme(String): boolean` -- 设置主题
- Produces: `ThemeManager.reload()` -- 重载当前主题
- Produces: `ThemeManager.reset()` -- 重置为默认

- [ ] **Step 1: 写 ThemeManager 纯方法失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/config/ThemeManagerTest.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.cadengine.ThemeSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThemeManagerTest {

    @Test
    void listThemeNamesReturnsDefaultWhenDirEmpty(@TempDir Path tempDir) {
        List<String> names = ThemeManager.listThemeNames(tempDir);
        assertTrue(names.contains("default"));
        assertEquals(1, names.size());
    }

    @Test
    void listThemeNamesReturnsDefaultWhenDirNotExists(@TempDir Path tempDir) {
        List<String> names = ThemeManager.listThemeNames(tempDir.resolve("nonexistent"));
        assertTrue(names.contains("default"));
        assertEquals(1, names.size());
    }

    @Test
    void listThemeNamesFindsJsonFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("dark.json"), "{\"name\":\"dark\"}");
        Files.writeString(tempDir.resolve("light.json"), "{\"name\":\"light\"}");
        Files.writeString(tempDir.resolve("notjson.txt"), "ignore me");
        List<String> names = ThemeManager.listThemeNames(tempDir);
        assertTrue(names.contains("default"));
        assertTrue(names.contains("dark"));
        assertTrue(names.contains("light"));
        assertEquals(3, names.size());
    }

    @Test
    void loadThemeFileReturnsNullWhenNotFound(@TempDir Path tempDir) {
        Theme theme = ThemeManager.loadThemeFile(tempDir, "nonexistent");
        assertNull(theme);
    }

    @Test
    void loadThemeFileReturnsThemeWhenFound(@TempDir Path tempDir) throws IOException {
        Theme original = Theme.defaultValue();
        String json = ThemeSerializer.serialize(original);
        Files.writeString(tempDir.resolve("default.json"), json);
        Theme loaded = ThemeManager.loadThemeFile(tempDir, "default");
        assertNotNull(loaded);
        assertEquals("default", loaded.name());
        assertEquals(original.track().width(), loaded.track().width(), 1e-6f);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.config.ThemeManagerTest"`
预期：编译失败（`ThemeManager` 不存在）

- [ ] **Step 3: 实现 ThemeManager**

创建 `src/client/java/net/jsmua/kinetic_planner/config/ThemeManager.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.cadengine.ThemeSerializer;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 主题管理器：扫描/加载/切换/重载/重置主题。
 *
 * <p>纯方法（{@link #listThemeNames} / {@link #loadThemeFile}）接受 {@link Path} 参数，
 * 可在 JUnit 5 中用 {@code @TempDir} 单测。副作用方法（{@link #setTheme} /
 * {@link #reload} / {@link #reset}）操作 {@link KPConfig} 和 {@link WorldTreeReadOverlay}。
 *
 * <p>主题 JSON 文件存放在 {@code config/kineticplanner/themes/<name>.json}。
 * "default" 主题始终可用，不依赖文件。
 */
public final class ThemeManager {

    private ThemeManager() {}

    /**
     * 主题文件目录的运行时路径。
     */
    public static Path getThemesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("kineticplanner/themes");
    }

    /**
     * 扫描目录中的 .json 主题文件，返回主题名列表（含 "default"）。
     *
     * <p>纯方法：不依赖运行时状态，可单测。
     *
     * @param themesDir 主题目录路径（可以不存在）
     * @return 排序后的主题名列表，始终包含 "default"
     */
    public static List<String> listThemeNames(Path themesDir) {
        List<String> names = new ArrayList<>();
        names.add("default");
        if (Files.isDirectory(themesDir)) {
            try (Stream<Path> files = Files.list(themesDir)) {
                files
                    .filter(f -> f.toString().endsWith(".json"))
                    .forEach(f -> {
                        String fileName = f.getFileName().toString();
                        names.add(fileName.substring(0, fileName.length() - 5));
                    });
            } catch (IOException ignored) {
                // 目录读取失败，仅返回 default
            }
        }
        return names.stream().distinct().sorted().toList();
    }

    /**
     * 从目录加载指定名称的主题 JSON 文件。
     *
     * <p>纯方法：不依赖运行时状态，可单测。
     *
     * @param themesDir 主题目录路径
     * @param name      主题名（不含 .json 后缀）
     * @return 反序列化的 Theme，或 null 如果文件不存在/解析失败
     */
    public static Theme loadThemeFile(Path themesDir, String name) {
        Path file = themesDir.resolve(name + ".json");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            return ThemeSerializer.deserialize(json);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 设置当前主题。
     *
     * <p>"default" 使用 {@link Theme#defaultValue()}，其他名称从 JSON 文件加载。
     * 成功时更新 {@link KPConfig#THEME_ACTIVE} 并应用到 {@link WorldTreeReadOverlay}。
     *
     * @param name 主题名
     * @return true 如果主题加载成功
     */
    public static boolean setTheme(String name) {
        if ("default".equals(name)) {
            KPConfig.THEME_ACTIVE.set("default");
            WorldTreeReadOverlay.setTheme(Theme.defaultValue());
            return true;
        }
        Theme theme = loadThemeFile(getThemesDir(), name);
        if (theme != null) {
            KPConfig.THEME_ACTIVE.set(name);
            WorldTreeReadOverlay.setTheme(theme);
            return true;
        }
        return false;
    }

    /**
     * 从磁盘重载当前主题。
     *
     * <p>如果当前主题是 "default"，重置为默认值。
     * 否则从 JSON 文件重新加载。如果文件已删除，回退到默认。
     */
    public static void reload() {
        String currentName = KPConfig.THEME_ACTIVE.get();
        if ("default".equals(currentName)) {
            WorldTreeReadOverlay.setTheme(Theme.defaultValue());
            return;
        }
        Theme theme = loadThemeFile(getThemesDir(), currentName);
        if (theme != null) {
            WorldTreeReadOverlay.setTheme(theme);
        } else {
            // 文件不存在了，回退到默认
            KPConfig.THEME_ACTIVE.set("default");
            WorldTreeReadOverlay.setTheme(Theme.defaultValue());
        }
    }

    /**
     * 重置为默认主题。
     */
    public static void reset() {
        KPConfig.THEME_ACTIVE.set("default");
        WorldTreeReadOverlay.setTheme(Theme.defaultValue());
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.config.ThemeManagerTest"`
预期：5 个测试 PASS

- [ ] **Step 5: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: add ThemeManager with testable pure methods for theme scanning/loading"
```

---

## Task 4: OverlayControl -- 叠加层状态管理（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/OverlayControl.java`

**Interfaces:**
- Consumes: `KPConfig.OVERLAY_ENABLED` / `KPConfig.toTheme()`（client，现有）, `WorldTreeReadOverlay` 统计方法（Task 2）
- Produces: `OverlayControl.isEnabled()` / `enable()` / `disable()` / `toggle()`
- Produces: `OverlayControl.reload()` -- 重载 Theme（从 config 重建）
- Produces: `OverlayControl.status()` -- 返回状态字符串

- [ ] **Step 1: 实现 OverlayControl**

创建 `src/client/java/net/jsmua/kinetic_planner/config/OverlayControl.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;

/**
 * 叠加层状态管理器。
 *
 * <p>封装叠加层开关状态、重载逻辑和状态查询。
 * 命令层通过此类操作叠加层，不直接访问 {@link KPConfig} 或 {@link WorldTreeReadOverlay}。
 */
public final class OverlayControl {

    private OverlayControl() {}

    /**
     * 叠加层是否启用。
     */
    public static boolean isEnabled() {
        return KPConfig.OVERLAY_ENABLED.get();
    }

    /**
     * 启用叠加层。
     */
    public static void enable() {
        KPConfig.OVERLAY_ENABLED.set(true);
    }

    /**
     * 禁用叠加层。
     */
    public static void disable() {
        KPConfig.OVERLAY_ENABLED.set(false);
    }

    /**
     * 切换叠加层开关。
     */
    public static void toggle() {
        KPConfig.OVERLAY_ENABLED.set(!isEnabled());
    }

    /**
     * 重载叠加层：从当前配置值重建 Theme 并应用。
     *
     * <p>注意：NeoForge 1.21.1 不支持运行时 TOML 文件重载。
     * 此方法从内存中的配置值重建 Theme。如需从磁盘重载配置，
     * 请使用 NeoForge 的配置管理或重启游戏。
     */
    public static void reload() {
        WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
    }

    /**
     * 返回叠加层状态字符串（用于命令输出）。
     */
    public static String status() {
        boolean enabled = isEnabled();
        int graphCount = WorldTreeReadOverlay.getGraphCount();
        if (graphCount == 0) {
            return String.format("[KP] Overlay: %s | Map: closed (no data)",
                enabled ? "ON" : "OFF");
        }
        int nodeCount = WorldTreeReadOverlay.getNodeCount();
        int edgeCount = WorldTreeReadOverlay.getEdgeCount();
        return String.format("[KP] Overlay: %s | Graphs: %d | Nodes: %d | Edges: %d",
            enabled ? "ON" : "OFF", graphCount, nodeCount, edgeCount);
    }
}
```

- [ ] **Step 2: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: add OverlayControl for overlay state management"
```

---

## Task 5: 命令注册迁移 + KPCommands 完整重写

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java`

**Interfaces:**
- Consumes: `OverlayControl`（Task 4）, `ThemeManager`（Task 3）, `WorldTreeReadOverlay` 统计/诊断方法（Task 2）
- Consumes: `RegisterClientCommandsEvent`（NeoForge 1.21.1，`net.neoforged.neoforge.client.event`）
- Produces: 14 个命令节点（overlay 5 + theme 4 + debug 4 + root 1）

- [ ] **Step 1: 迁移 KineticPlannerClient 命令注册**

修改 `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`。

将 import：
```java
import net.neoforged.neoforge.event.RegisterCommandsEvent;
```
替换为：
```java
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
```

将方法：
```java
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        KPCommands.register(event.getDispatcher());
    }
```
替换为：
```java
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        KPCommands.register(event.getDispatcher());
    }
```

- [ ] **Step 2: 重写 KPCommands**

将 `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java` 的全部内容替换为：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.StringArgument;
import net.minecraft.network.chat.Component;

/**
 * /kp 命令体系注册（P0 完整版）。
 *
 * <p>命令树：
 * <pre>
 * /kp                         -- 综合概览
 * /kp overlay toggle          -- 切换叠加开关
 * /kp overlay enable          -- 显式开启
 * /kp overlay disable         -- 显式关闭
 * /kp overlay reload          -- 重载配置 + 主题
 * /kp overlay status          -- 查看状态
 * /kp theme list              -- 列出可用主题
 * /kp theme set <name>        -- 切换到指定主题
 * /kp theme reload            -- 从磁盘重载当前主题
 * /kp theme reset             -- 重置为默认主题
 * /kp debug stats             -- 渲染统计
 * /kp debug dump              -- 转储 TrackGraph 数据到日志
 * /kp debug layer-count       -- 各图层对象计数
 * /kp debug overlay-anchors   -- 叠加层锚点诊断
 * </pre>
 *
 * <p>通过 {@link RegisterClientCommandsEvent} 注册为客户端命令。
 * 命令逻辑委托 {@link OverlayControl} / {@link ThemeManager} / {@link WorldTreeReadOverlay}。
 */
public final class KPCommands {

    private KPCommands() {}

    /**
     * 注册命令（由 RegisterClientCommandsEvent 调用）。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kp")
            .executes(KPCommands::overview)
            .then(Commands.literal("overlay")
                .then(Commands.literal("toggle")
                    .executes(KPCommands::overlayToggle))
                .then(Commands.literal("enable")
                    .executes(KPCommands::overlayEnable))
                .then(Commands.literal("disable")
                    .executes(KPCommands::overlayDisable))
                .then(Commands.literal("reload")
                    .executes(KPCommands::overlayReload))
                .then(Commands.literal("status")
                    .executes(KPCommands::overlayStatus)))
            .then(Commands.literal("theme")
                .then(Commands.literal("list")
                    .executes(KPCommands::themeList))
                .then(Commands.literal("set")
                    .then(Commands.argument("name", StringArgument.word())
                        .executes(KPCommands::themeSet)))
                .then(Commands.literal("reload")
                    .executes(KPCommands::themeReload))
                .then(Commands.literal("reset")
                    .executes(KPCommands::themeReset)))
            .then(Commands.literal("debug")
                .then(Commands.literal("stats")
                    .executes(KPCommands::debugStats))
                .then(Commands.literal("dump")
                    .executes(KPCommands::debugDump))
                .then(Commands.literal("layer-count")
                    .executes(KPCommands::debugLayerCount))
                .then(Commands.literal("overlay-anchors")
                    .executes(KPCommands::debugOverlayAnchors)))
        );
    }

    // === /kp (root) ===

    private static int overview(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(OverlayControl.status()), false);
        return 1;
    }

    // === /kp overlay ===

    private static int overlayToggle(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.toggle();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay " + (OverlayControl.isEnabled() ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int overlayEnable(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.enable();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay enabled"), false);
        return 1;
    }

    private static int overlayDisable(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.disable();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay disabled"), false);
        return 1;
    }

    private static int overlayReload(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Config reloaded and theme rebuilt"), false);
        return 1;
    }

    private static int overlayStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(OverlayControl.status()), false);
        return 1;
    }

    // === /kp theme ===

    private static int themeList(CommandContext<CommandSourceStack> ctx) {
        var themes = ThemeManager.listThemeNames(ThemeManager.getThemesDir());
        String current = KPConfig.THEME_ACTIVE.get();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Themes: " + String.join(", ", themes) +
                " (active: " + current + ")"), false);
        return 1;
    }

    private static int themeSet(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgument.getString(ctx, "name");
        if (ThemeManager.setTheme(name)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Theme set to: " + name), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Theme not found: " + name +
                    ". Use /kp theme list to see available themes."));
            return 0;
        }
    }

    private static int themeReload(CommandContext<CommandSourceStack> ctx) {
        ThemeManager.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reloaded: " + KPConfig.THEME_ACTIVE.get()), false);
        return 1;
    }

    private static int themeReset(CommandContext<CommandSourceStack> ctx) {
        ThemeManager.reset();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reset to default"), false);
        return 1;
    }

    // === /kp debug ===

    private static int debugStats(CommandContext<CommandSourceStack> ctx) {
        int graphs = WorldTreeReadOverlay.getGraphCount();
        int nodes = WorldTreeReadOverlay.getNodeCount();
        int edges = WorldTreeReadOverlay.getEdgeCount();
        int edgePoints = WorldTreeReadOverlay.getEdgePointCount();
        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format(
                "[KP] Stats | Graphs: %d | Nodes: %d | Edges: %d | EdgePoints: %d",
                graphs, nodes, edges, edgePoints)), false);
        return 1;
    }

    private static int debugDump(CommandContext<CommandSourceStack> ctx) {
        WorldTreeReadOverlay.dumpData();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] TrackGraph data dumped to console log"), false);
        return 1;
    }

    private static int debugLayerCount(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(WorldTreeReadOverlay.getLayerCounts()), false);
        return 1;
    }

    private static int debugOverlayAnchors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(WorldTreeReadOverlay.getOverlayAnchors()), false);
        return 1;
    }
}
```

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS（含 ThemeManagerTest 5 个新测试）

- [ ] **Step 5: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: migrate to client command registration, rewrite KPCommands with 14 P0 commands"
```

---

## Self-Review

**1. Spec coverage:**
- P0-refactor: sourceSet 重构 -> Task 1 ✓
- P0-refactor: IWorldEditAccess 接口定义 -> 推迟到 P1（P0 不使用，YAGNI）
- P0-fix: 修复 overlay toggle（渲染层检查 OVERLAY_ENABLED）-> Task 2 ✓
- P0-fix: 修复 overlay reload（重建 Theme）-> Task 4/5 ✓
- P0-fix: 修复 theme reload（独立实现）-> Task 3/5 ✓
- P0-fix: 修复 theme list（扫描目录）-> Task 3/5 ✓
- P0-fix: 修复 debug stats（实际统计）-> Task 2/5 ✓
- P0-fix: 新增 overlay enable/disable/status -> Task 4/5 ✓
- P0-fix: 新增 theme set/reset -> Task 3/5 ✓
- P0-fix: 新增 debug dump/layer-count/overlay-anchors -> Task 2/5 ✓
- P0-fix: 迁移客户端命令注册 -> Task 5 ✓
- P0-fix: 提取 OverlayControl + ThemeManager -> Task 3/4 ✓

**2. Placeholder scan:**
- 无 "TBD"/"TODO"/"fill in details" 占位
- `overlay reload` 的 NeoForge TOML 重载限制已标注（OverlayControl.reload javadoc）
- 所有命令均有完整实现代码

**3. Type consistency:**
- `OverlayControl.isEnabled/enable/disable/toggle/reload/status` -- Task 4 定义，Task 5 调用 ✓
- `ThemeManager.listThemeNames(Path)/loadThemeFile(Path,String)/getThemesDir()/setTheme(String)/reload()/reset()` -- Task 3 定义，Task 5 调用 ✓
- `WorldTreeReadOverlay.getGraphCount/getNodeCount/getEdgeCount/getEdgePointCount/dumpData/getLayerCounts/getOverlayAnchors` -- Task 2 定义，Task 4/5 调用 ✓
- `KPCommands.register(CommandDispatcher<CommandSourceStack>)` -- Task 5 定义，Task 5 Step 1 调用 ✓
- `RegisterClientCommandsEvent.getDispatcher()` 返回 `CommandDispatcher<CommandSourceStack>` -- 与 KPCommands.register 签名匹配 ✓

**4. 已知限制:**
- `overlay reload` 不支持运行时 TOML 文件重载（NeoForge 1.21.1 限制），仅从内存配置值重建 Theme
- server sourceSet 在 P0 为空（P1 编辑引擎实现时填充）
- IWorldEditAccess 接口推迟到 P1（P0 不使用）
- Config screen 注册 API TODO 保留（不在 P0 范围内）

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-23-kinetic-planner-p0-refactor-fix.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
