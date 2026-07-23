# Kinetic Planner P1.0 - 地图模组独立配置 + 嵌入式 UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每个地图模组（Xaero / JourneyMap）提供独立配置（enabled / priority / 视觉参数），先以 CLI 命令提供完整配置能力，再实现嵌入到全屏地图界面内部的自绘 UI（不用 Cloth Config）。同时提供隐藏 Create 自带信号边组叠加层的配置（仅在本模组 UI 启用时生效）。

**Architecture:** 两阶段推进：(A) 数据模型 + KPConfig 嵌套段 + Dispatcher 过滤（含 priority 排序）+ WorldTreeReadOverlay 视觉切换 + CLI 命令注册 + 隐藏 Create Track Map Mixin；(B) 自绘齿轮按钮 + 配置面板 Screen + Xaero Mixin 注入。每阶段独立可验收。

**Tech Stack:** Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21 / Create 6.0.10 / JUnit 5 / Mockito 5

---

## Global Constraints

- Minecraft 1.21.1，NeoForge 21.1.235，Java 21（toolchain）
- mod_id: `kinetic_planner`，mod_group_id: `net.jsmua.kinetic_planner`
- sourceSet 分离：`main`（common）不引用 `net.minecraft.client.*`；`client` 可引用全部
- Mixin 配置文件为单一 `kinetic_planner.mixins.json`（`client` 数组），无 MixinPlugin
- Mixin 配置 `defaultRequire: 0`，无 MixinPlugin
- NeoForge 1.21.1 `ModConfigSpec` 不支持运行时动态段，预定义常见 modId 段
- NeoForge 1.21.1 客户端命令：`RegisterClientCommandsEvent.getDispatcher()` 返回 `CommandDispatcher<CommandSourceStack>`
- MC 1.21.1 VertexConsumer API：`addVertex` / `setColor` / `setNormal`，无 `endVertex`
- Shell 是 `cmd.exe`，多命令用 `&` 或 `&&` 分隔
- **UI 嵌入到全屏地图界面内部**（不是 NeoForge mod 配置屏幕），纯客户端
- **JourneyMap Mixin 推迟**：P1.0 仅让 JM provider 出现在 CLI/UI 中，`isMapOpen` 仍返回 false（占位）
- **Create F3 Debug 不干涉**：Create 的 `TrackGraphVisualizer.debugViewGraph`（F3 调试图视图）是 Create 自有调试功能，本模组不隐藏它。仅隐藏 `visualiseSignalEdgeGroups`（信号边组叠加层）。

---

## 关键决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| 配置存储 | 单一 TOML + 嵌套 `[provider.<modId>]` 子段 | NeoForge ModConfigSpec 原生支持嵌套 push/pop；避免多文件管理复杂度 |
| UI 位置 | 嵌入到全屏地图界面内部 | 用户要求；类似 Xaero 自身设置按钮；纯客户端，不走 NeoForge mod config screen |
| UI 框架 | 自绘 Screen + Button Widget（不用 Cloth Config） | Cloth Config 是 mod 列表用的；地图内 UI 需要半透明叠加 + 鼠标交互 |
| 实现顺序 | CLI 先，UI 后 | 先用 CLI 验证配置机制可用，再投入 UI 开发；CLI 可独立验收 |
| JM Mixin | 推迟 | Xaero 更常用，先做 Xaero；JM 仅在 CLI/UI 中显示，`isMapOpen` 仍返回 false |
| 配置默认值 | 各 provider 自己声明 `defaultConfig()` | 解耦，避免 KPConfig 硬编码各 provider 默认值 |
| 已知 modId 段 | 预定义 xaeroworldmap / journeymap | ModConfigSpec 不支持运行时动态段；这两个是当前生态主流 |
| 隐藏 Create Track Map | 仅隐藏 `visualiseSignalEdgeGroups`，不干涉 `debugViewGraph` | F3 调试图视图是 Create 自有调试功能；信号边组叠加层与 KP 叠加层功能重叠 |
| hideCreateTrackMap 配置位置 | `[overlay]` 段下 | 与 `OVERLAY_ENABLED` 同段；逻辑：overlay 启用 + hideCreateTrackMap=true 时隐藏 |
| priority 字段 | 在 `tick()` 中按 priority 排序后遍历 | 用户要求实现排序；provider 仅 2 个，排序开销可忽略 |
| Task 顺序 | A1(KPConfig) -> A2(Dispatcher) -> A3(Interface) -> ... | 消除跨 Task 编译依赖：KPConfig 先于 Dispatcher，Dispatcher 先于 Interface |

---

## 架构总览

### 数据流

```
启动时:
  MapOverlayProvider 实现（Xaero/JM）注册到 MapOverlayDispatcher
  各 provider 的 defaultConfig() 注册到 ProviderConfigRegistry
  KPConfig 加载 TOML，按 modId 合并配置（TOML > 默认值）
  CreateTrackVisualizerHiderMixin 注册（拦截 visualiseSignalEdgeGroups）

每 tick:
  MapOverlayDispatcher.tick() 按 priority 排序，过滤 disabled provider
  首个 enabled + isMapOpen 的 provider 暴露 activeProviderModId()
  WorldTreeReadOverlay 读取 active provider 的 ProviderConfig
  视觉参数（lineWidthScale / alphaScale）应用到 Theme
  CreateTrackVisualizerHiderMixin 检查 OVERLAY_ENABLED + HIDE_CREATE_TRACK_MAP
    -> 若都为 true，cancel Create 的 visualiseSignalEdgeGroups

CLI:
  /kp provider list/enable/disable/set/get/reset
  /kp overlay hide-create [true|false]
  -> 修改 KPConfig 内存值 -> NeoForge 自动写盘
  -> 触发 WorldTreeReadOverlay 重建 Theme
```

### sourceSet 归属

| sourceSet | 新增/变更类 |
|---|---|
| main (common) | `ProviderConfig`, `ProviderConfigRegistry` |
| client | `MapOverlayProvider`(改), `XaeroMapOverlayProvider`(改), `JourneyMapOverlayProvider`(改), `MapOverlayDispatcher`(改), `KPConfig`(改), `KPCommands`(改), `OverlayControl`(改), `WorldTreeReadOverlay`(改), `ProviderConfigControl`(新), `CreateTrackVisualizerHiderMixin`(新), `MapGearButtonWidget`(新, Phase B), `ProviderConfigScreen`(新, Phase B), `XaeroMapGearButtonMixin`(新, Phase B) |

---

## File Structure

### 新建文件

| 路径 | sourceSet | 阶段 | 职责 |
|---|---|---|---|
| `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfig.java` | main | A | provider 配置 record（modId/displayName/enabled/priority/视觉参数） |
| `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfigRegistry.java` | main | A | 注册/查询 provider 默认配置 |
| `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java` | client | A | provider 配置状态管理（CRUD + 应用到渲染层） |
| `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigTest.java` | test | A | ProviderConfig record 单测 |
| `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigRegistryTest.java` | test | A | Registry 注册/查询单测 |
| `src/client/java/net/jsmua/kinetic_planner/mixin/CreateTrackVisualizerHiderMixin.java` | client | A | Mixin 拦截 Create 的 `visualiseSignalEdgeGroups` |
| `src/client/java/net/jsmua/kinetic_planner/config/MapGearButtonWidget.java` | client | B | 自绘齿轮按钮 widget |
| `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigScreen.java` | client | B | 嵌入式配置面板 Screen |
| `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java` | client | B | Mixin 注入齿轮按钮渲染到 Xaero GuiMap |

### 修改文件

| 路径 | 修改内容 |
|---|---|
| `src/client/java/.../mapadapter/MapOverlayProvider.java` | 接口扩展：`displayName()` / `defaultConfig()` |
| `src/client/java/.../mapadapter/XaeroMapOverlayProvider.java` | 实现 displayName / defaultConfig |
| `src/client/java/.../mapadapter/JourneyMapOverlayProvider.java` | 实现 displayName / defaultConfig（占位） |
| `src/client/java/.../mapadapter/MapOverlayDispatcher.java` | priority 排序 + enabled 过滤 + 暴露 activeProviderModId/registeredProviders |
| `src/client/java/.../config/KPConfig.java` | 添加 `[provider.*]` 嵌套段 + `[overlay].hideCreateTrackMap` + getProviderConfig/setProviderEnabled/setProviderParam |
| `src/client/java/.../config/KPCommands.java` | 添加 `/kp provider` 子命令树 + `/kp overlay hide-create` |
| `src/client/java/.../config/OverlayControl.java` | 暴露 `isHideCreateTrackMap()` / `setHideCreateTrackMap()` |
| `src/client/java/.../instrument/WorldTreeReadOverlay.java` | 根据 active provider 应用视觉参数 scale |
| `src/client/java/.../KineticPlannerClient.java` | 启动时注册 provider 默认配置到 Registry |
| `src/main/resources/kinetic_planner.mixins.json` | `client` 数组添加 `CreateTrackVisualizerHiderMixin` |

---

## Phase A: CLI 命令（先做）

### Task A1: ProviderConfig record + ProviderConfigRegistry（main，含单测）

**Files:**
- Create: `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfig.java`
- Create: `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfigRegistry.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigTest.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigRegistryTest.java`

**Interfaces:**
- Produces: `ProviderConfig` record（纯数据，可单测）
- Produces: `ProviderConfigRegistry.register(modId, defaultConfig)` / `getDefault(modId)` / `getRegisteredModIds()` / `clear()`（测试用）

- [ ] **Step 1: 写 ProviderConfig 失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigTest.java`：

```java
package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigTest {

    @Test
    void defaultValueProducesExpectedFields() {
        ProviderConfig config = ProviderConfig.defaultValue(
            "xaeroworldmap", "Xaero's World Map");
        assertEquals("xaeroworldmap", config.modId());
        assertEquals("Xaero's World Map", config.displayName());
        assertTrue(config.enabled());
        assertEquals(0, config.priority());
        assertEquals(1.0f, config.lineWidthScale(), 1e-6f);
        assertEquals(1.0f, config.alphaScale(), 1e-6f);
        assertFalse(config.dashed());
    }

    @Test
    void withEnabledReturnsNewInstance() {
        ProviderConfig original = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfig modified = original.withEnabled(false);
        assertFalse(modified.enabled());
        assertTrue(original.enabled()); // 原 record 不变
    }

    @Test
    void withLineWidthScaleReturnsNewInstance() {
        ProviderConfig original = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfig modified = original.withLineWidthScale(2.0f);
        assertEquals(2.0f, modified.lineWidthScale(), 1e-6f);
        assertEquals(1.0f, original.lineWidthScale(), 1e-6f);
    }

    @Test
    void equalsAndHashCodeWork() {
        ProviderConfig c1 = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfig c2 = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}
```

- [ ] **Step 2: 写 ProviderConfigRegistry 失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigRegistryTest.java`：

```java
package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigRegistryTest {

    @AfterEach
    void cleanup() {
        ProviderConfigRegistry.clear();
    }

    @Test
    void registerAddsModId() {
        ProviderConfig config = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfigRegistry.register("xaeroworldmap", config);
        assertTrue(ProviderConfigRegistry.getRegisteredModIds().contains("xaeroworldmap"));
    }

    @Test
    void getDefaultReturnsRegisteredConfig() {
        ProviderConfig config = ProviderConfig.defaultValue("journeymap", "JourneyMap");
        ProviderConfigRegistry.register("journeymap", config);
        ProviderConfig got = ProviderConfigRegistry.getDefault("journeymap");
        assertNotNull(got);
        assertEquals("journeymap", got.modId());
    }

    @Test
    void getDefaultReturnsNullForUnknownModId() {
        ProviderConfig got = ProviderConfigRegistry.getDefault("unknown");
        assertNull(got);
    }

    @Test
    void getRegisteredModIdsIsImmutableSnapshot() {
        ProviderConfigRegistry.register("xaeroworldmap",
            ProviderConfig.defaultValue("xaeroworldmap", "Xaero"));
        Set<String> ids = ProviderConfigRegistry.getRegisteredModIds();
        assertEquals(1, ids.size());
        // 注册更多不影响已返回的快照
        ProviderConfigRegistry.register("journeymap",
            ProviderConfig.defaultValue("journeymap", "JourneyMap"));
        assertEquals(1, ids.size());
    }
}
```

- [ ] **Step 3: 实现 ProviderConfig**

创建 `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfig.java`：

```java
package net.jsmua.kinetic_planner.data;

/**
 * 地图模组 provider 的配置 record。
 *
 * <p>每个 MapOverlayProvider 实例对应一份 ProviderConfig，存储在
 * {@code config/kineticplanner-client.toml} 的 {@code [provider.<modId>]} 段。
 * 视觉参数（lineWidthScale / alphaScale / dashed）作为全局 Theme 的
 * scale/override，不全量替换 Theme。
 *
 * @param modId          provider 对应的 mod ID（如 "xaeroworldmap"）
 * @param displayName    UI 显示名（如 "Xaero's World Map"）
 * @param enabled        是否启用（false 时 dispatcher 跳过该 provider）
 * @param priority       优先级（数字越小越优先；0 表示最高优先）
 * @param lineWidthScale 线宽缩放因子（1.0 = 用全局 Theme 宽度）
 * @param alphaScale     透明度缩放因子（1.0 = 用全局 Theme alpha）
 * @param dashed         是否改用虚线渲染（覆盖 Theme.track.dashed；P1.1 生效）
 */
public record ProviderConfig(
    String modId,
    String displayName,
    boolean enabled,
    int priority,
    float lineWidthScale,
    float alphaScale,
    boolean dashed
) {
    /**
     * 创建默认配置。
     *
     * @param modId       provider mod ID
     * @param displayName UI 显示名
     * @return 默认配置：enabled=true / priority=0 / scale=1.0 / dashed=false
     */
    public static ProviderConfig defaultValue(String modId, String displayName) {
        return new ProviderConfig(modId, displayName, true, 0, 1.0f, 1.0f, false);
    }

    /** 返回启用状态变更后的新实例（record 不可变）。 */
    public ProviderConfig withEnabled(boolean newEnabled) {
        return new ProviderConfig(modId, displayName, newEnabled, priority,
            lineWidthScale, alphaScale, dashed);
    }

    /** 返回线宽缩放变更后的新实例。 */
    public ProviderConfig withLineWidthScale(float newScale) {
        return new ProviderConfig(modId, displayName, enabled, priority,
            newScale, alphaScale, dashed);
    }

    /** 返回 alpha 缩放变更后的新实例。 */
    public ProviderConfig withAlphaScale(float newScale) {
        return new ProviderConfig(modId, displayName, enabled, priority,
            lineWidthScale, newScale, dashed);
    }

    /** 返回虚线状态变更后的新实例。 */
    public ProviderConfig withDashed(boolean newDashed) {
        return new ProviderConfig(modId, displayName, enabled, priority,
            lineWidthScale, alphaScale, newDashed);
    }
}
```

- [ ] **Step 4: 实现 ProviderConfigRegistry**

创建 `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfigRegistry.java`：

```java
package net.jsmua.kinetic_planner.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provider 默认配置注册表。
 *
 * <p>启动时各 {@link net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider}
 * 实现将自己的默认配置注册到此处。{@code KPConfig} 加载 TOML 后按 modId 合并：
 * TOML 值 > Registry 默认值。
 *
 * <p>纯 JVM（main sourceSet），无客户端依赖，可单测。
 */
public final class ProviderConfigRegistry {

    private ProviderConfigRegistry() {}

    /** 按 modId 索引的默认配置。LinkedHashMap 保持注册顺序。 */
    private static final Map<String, ProviderConfig> DEFAULTS = new LinkedHashMap<>();

    /**
     * 注册一个 provider 的默认配置。
     *
     * @param modId         provider mod ID
     * @param defaultConfig 默认配置
     */
    public static void register(String modId, ProviderConfig defaultConfig) {
        DEFAULTS.put(modId, defaultConfig);
    }

    /**
     * 查询某 provider 的默认配置。
     *
     * @param modId provider mod ID
     * @return 默认配置，或 null 如果未注册
     */
    public static ProviderConfig getDefault(String modId) {
        return DEFAULTS.get(modId);
    }

    /**
     * 返回所有已注册 modId 的不可变快照。
     *
     * @return modId 集合（快照，后续注册不影响返回值）
     */
    public static Set<String> getRegisteredModIds() {
        return Set.copyOf(DEFAULTS.keySet());
    }

    /**
     * 清空注册表（仅测试用）。
     */
    static void clear() {
        DEFAULTS.clear();
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.data.ProviderConfigTest" --tests "net.jsmua.kinetic_planner.data.ProviderConfigRegistryTest"`
预期：7 个测试 PASS

- [ ] **Step 6: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: add ProviderConfig record and ProviderConfigRegistry for per-provider config"
```

---

### Task A2: KPConfig 扩展 -- per-provider 嵌套段 + hideCreateTrackMap（client）

> **依赖**：Task A1（ProviderConfig / ProviderConfigRegistry）
> **被依赖**：Task A3（Dispatcher 读 KPConfig.getProviderConfig）、Task A7（Mixin 读 KPConfig.HIDE_CREATE_TRACK_MAP）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java`

**Interfaces:**
- Produces: `KPConfig.getProviderConfig(modId) : ProviderConfig` -- 合并 TOML + 默认值
- Produces: `KPConfig.setProviderEnabled(modId, enabled)` -- 修改运行时配置（NeoForge 自动写盘）
- Produces: `KPConfig.setProviderParam(modId, param, value) : boolean` -- 通用参数设置
- Produces: `KPConfig.HIDE_CREATE_TRACK_MAP` -- `[overlay]` 段配置项

- [ ] **Step 1: 添加 hideCreateTrackMap 到 [overlay] 段**

在 `KPConfig.java` 的 static 块中，`builder.push("overlay")` 之后添加：

```java
        OVERLAY_ENABLED = builder.define("enabled", true);
        HIDE_CREATE_TRACK_MAP = builder.define("hideCreateTrackMap", true);
```

在字段声明区 `OVERLAY_ENABLED` 之后添加：

```java
    public static final ModConfigSpec.BooleanValue HIDE_CREATE_TRACK_MAP;
```

- [ ] **Step 2: 添加 per-provider 配置字段**

在 `KPConfig.java` 的 static 块中，在 `builder.push("debug")` 之前添加：

```java
        // [provider.xaeroworldmap]
        builder.push("provider").push("xaeroworldmap");
        PROVIDER_XAERO_ENABLED = builder.define("enabled", true);
        PROVIDER_XAERO_PRIORITY = builder.defineInRange("priority", 0, 0, 100);
        PROVIDER_XAERO_LINE_WIDTH_SCALE = builder.defineInRange("lineWidthScale", 1.0, 0.1, 10.0);
        PROVIDER_XAERO_ALPHA_SCALE = builder.defineInRange("alphaScale", 1.0, 0.0, 1.0);
        PROVIDER_XAERO_DASHED = builder.define("dashed", false);
        builder.pop().pop();

        // [provider.journeymap]
        builder.push("provider").push("journeymap");
        PROVIDER_JM_ENABLED = builder.define("enabled", true);
        PROVIDER_JM_PRIORITY = builder.defineInRange("priority", 1, 0, 100);
        PROVIDER_JM_LINE_WIDTH_SCALE = builder.defineInRange("lineWidthScale", 1.0, 0.1, 10.0);
        PROVIDER_JM_ALPHA_SCALE = builder.defineInRange("alphaScale", 1.0, 0.0, 1.0);
        PROVIDER_JM_DASHED = builder.define("dashed", false);
        builder.pop().pop();
```

在字段声明区（`DEBUG_DISABLE_GL_STATE_GUARD` 之后）添加：

```java
    // [provider.xaeroworldmap]
    public static final ModConfigSpec.BooleanValue PROVIDER_XAERO_ENABLED;
    public static final ModConfigSpec.IntValue PROVIDER_XAERO_PRIORITY;
    public static final ModConfigSpec.DoubleValue PROVIDER_XAERO_LINE_WIDTH_SCALE;
    public static final ModConfigSpec.DoubleValue PROVIDER_XAERO_ALPHA_SCALE;
    public static final ModConfigSpec.BooleanValue PROVIDER_XAERO_DASHED;

    // [provider.journeymap]
    public static final ModConfigSpec.BooleanValue PROVIDER_JM_ENABLED;
    public static final ModConfigSpec.IntValue PROVIDER_JM_PRIORITY;
    public static final ModConfigSpec.DoubleValue PROVIDER_JM_LINE_WIDTH_SCALE;
    public static final ModConfigSpec.DoubleValue PROVIDER_JM_ALPHA_SCALE;
    public static final ModConfigSpec.BooleanValue PROVIDER_JM_DASHED;
```

import 块添加：

```java
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
```

- [ ] **Step 3: 添加 getProviderConfig / setProviderEnabled / setProviderParam 方法**

在 `KPConfig` 类末尾（`toTheme()` 方法之后）添加：

```java
    /**
     * 查询指定 provider 的配置（合并 TOML 值与默认值）。
     *
     * <p>已知 modId（xaeroworldmap / journeymap）读 TOML 段；
     * 未知 modId 读 {@link ProviderConfigRegistry} 默认值。
     *
     * @param modId provider mod ID
     * @return 配置；未知 modId 且未注册返回 null
     */
    public static ProviderConfig getProviderConfig(String modId) {
        ProviderConfig defaultConfig = ProviderConfigRegistry.getDefault(modId);
        String displayName = defaultConfig != null ? defaultConfig.displayName() : modId;

        return switch (modId) {
            case "xaeroworldmap" -> new ProviderConfig(
                modId, displayName,
                PROVIDER_XAERO_ENABLED.get(),
                PROVIDER_XAERO_PRIORITY.get(),
                PROVIDER_XAERO_LINE_WIDTH_SCALE.get().floatValue(),
                PROVIDER_XAERO_ALPHA_SCALE.get().floatValue(),
                PROVIDER_XAERO_DASHED.get());
            case "journeymap" -> new ProviderConfig(
                modId, displayName,
                PROVIDER_JM_ENABLED.get(),
                PROVIDER_JM_PRIORITY.get(),
                PROVIDER_JM_LINE_WIDTH_SCALE.get().floatValue(),
                PROVIDER_JM_ALPHA_SCALE.get().floatValue(),
                PROVIDER_JM_DASHED.get());
            default -> defaultConfig; // 未知 modId 返回注册表默认值（可能为 null）
        };
    }

    /**
     * 设置 provider 的 enabled 状态。
     *
     * @param modId   provider mod ID
     * @param enabled 是否启用
     * @return true 如果设置成功（modId 已知）
     */
    public static boolean setProviderEnabled(String modId, boolean enabled) {
        return switch (modId) {
            case "xaeroworldmap" -> { PROVIDER_XAERO_ENABLED.set(enabled); yield true; }
            case "journeymap" -> { PROVIDER_JM_ENABLED.set(enabled); yield true; }
            default -> false;
        };
    }

    /**
     * 设置 provider 的视觉参数。
     *
     * @param modId  provider mod ID
     * @param param  参数名（lineWidthScale / alphaScale / dashed / priority）
     * @param value  字符串形式的新值
     * @return true 如果设置成功（modId 已知 + 参数名合法 + 值合法）
     */
    public static boolean setProviderParam(String modId, String param, String value) {
        try {
            return switch (modId) {
                case "xaeroworldmap" -> setXaeroParam(param, value);
                case "journeymap" -> setJmParam(param, value);
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean setXaeroParam(String param, String value) {
        return switch (param) {
            case "lineWidthScale" -> { PROVIDER_XAERO_LINE_WIDTH_SCALE.set(Double.parseDouble(value)); yield true; }
            case "alphaScale" -> { PROVIDER_XAERO_ALPHA_SCALE.set(Double.parseDouble(value)); yield true; }
            case "dashed" -> { PROVIDER_XAERO_DASHED.set(Boolean.parseBoolean(value)); yield true; }
            case "priority" -> { PROVIDER_XAERO_PRIORITY.set(Integer.parseInt(value)); yield true; }
            default -> false;
        };
    }

    private static boolean setJmParam(String param, String value) {
        return switch (param) {
            case "lineWidthScale" -> { PROVIDER_JM_LINE_WIDTH_SCALE.set(Double.parseDouble(value)); yield true; }
            case "alphaScale" -> { PROVIDER_JM_ALPHA_SCALE.set(Double.parseDouble(value)); yield true; }
            case "dashed" -> { PROVIDER_JM_DASHED.set(Boolean.parseBoolean(value)); yield true; }
            case "priority" -> { PROVIDER_JM_PRIORITY.set(Integer.parseInt(value)); yield true; }
            default -> false;
        };
    }
```

- [ ] **Step 4: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 提交（暂不提交，等 A3 完成后合并提交）**

---

### Task A3: MapOverlayDispatcher 扩展 -- priority 排序 + enabled 过滤 + activeProvider 暴露（client）

> **依赖**：Task A2（KPConfig.getProviderConfig）
> **被依赖**：Task A4（Provider 接口调用 registeredProviders）、Task A5（activeProviderModId）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayDispatcher.java`

**Interfaces:**
- Produces: `MapOverlayDispatcher.registeredProviders() : List<MapOverlayProvider>`
- Produces: `MapOverlayDispatcher.activeProviderModId() : Optional<String>`

- [ ] **Step 1: 暴露 registeredProviders**

修改 `MapOverlayDispatcher.java`，添加 accessor：

```java
    /**
     * 返回所有已注册 provider 的不可变快照。
     *
     * @return provider 列表（快照）
     */
    public static List<MapOverlayProvider> registeredProviders() {
        return List.copyOf(PROVIDERS);
    }
```

import 块添加（如未有）：

```java
import net.jsmua.kinetic_planner.config.KPConfig;
import java.util.Comparator;
```

- [ ] **Step 2: 添加 activeProviderModId 字段 + priority 排序 + 过滤逻辑**

替换现有 `tick()` 方法为：

```java
    /** 当前帧激活的 provider modId，null 表示无地图打开。 */
    private static String activeProviderModId;

    /**
     * 每 tick 调用，检测地图是否打开并捕获上下文。
     *
     * <p>按 priority 排序后遍历（数字越小越优先），跳过 disabled provider
     * （读 {@link KPConfig#getProviderConfig}）。
     */
    public static void tick() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            currentContext = null;
            activeProviderModId = null;
            return;
        }
        // 按 priority 排序（数字越小越优先），provider 仅 2 个，排序开销可忽略
        List<MapOverlayProvider> sorted = PROVIDERS.stream()
            .sorted(Comparator.comparingInt(p -> {
                var config = KPConfig.getProviderConfig(p.modId());
                return config != null ? config.priority() : Integer.MAX_VALUE;
            }))
            .toList();
        for (MapOverlayProvider p : sorted) {
            if (FAILED.contains(p.modId())) continue;
            // 读 per-provider enabled 配置
            var config = KPConfig.getProviderConfig(p.modId());
            if (config != null && !config.enabled()) continue;
            try {
                if (p.isMapOpen(screen)) {
                    currentContext = p.captureContext(screen);
                    activeProviderModId = p.modId();
                    return;
                }
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", p.modId(), t);
                FAILED.add(p.modId());
            }
        }
        currentContext = null;
        activeProviderModId = null;
    }

    /**
     * 返回当前激活的 provider modId。
     *
     * @return {@link Optional} 包含 modId，无地图打开时为空
     */
    public static Optional<String> activeProviderModId() {
        return Optional.ofNullable(activeProviderModId);
    }
```

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS（含 ProviderConfig + Registry 共 7 个新测试）

- [ ] **Step 5: 提交（合并 A2/A3）**

```bash
git add -A
git commit -m "feat: add per-provider config (KPConfig nested segments + dispatcher priority sort + enabled filter)"
```

---

### Task A4: MapOverlayProvider 接口扩展 + 各实现补全（client）

> **依赖**：Task A1（ProviderConfig）、Task A3（registeredProviders）
> **被依赖**：Task A6（ProviderConfigControl 调用 registeredProviders）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayProvider.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapOverlayProvider.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/JourneyMapOverlayProvider.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`

**Interfaces:**
- Produces: `MapOverlayProvider.displayName() : Component`
- Produces: `MapOverlayProvider.defaultConfig() : ProviderConfig`

- [ ] **Step 1: 扩展 MapOverlayProvider 接口**

在 `MapOverlayProvider.java` 末尾添加两个新方法（在 `modId()` 之后）：

```java
    /**
     * 返回该 provider 的本地化显示名，用于 CLI 输出和 UI 显示。
     *
     * @return {@link Component} 显示名（如 "Xaero's World Map"）
     */
    Component displayName();

    /**
     * 返回该 provider 的默认配置。
     *
     * <p>由 {@link net.jsmua.kinetic_planner.data.ProviderConfigRegistry}
     * 在启动时收集，{@link KPConfig} 加载时与 TOML 值合并。
     *
     * @return 默认 {@link ProviderConfig}
     */
    ProviderConfig defaultConfig();
```

并在 import 块添加：

```java
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
```

- [ ] **Step 2: 实现 XaeroMapOverlayProvider.displayName/defaultConfig**

在 `XaeroMapOverlayProvider.java` 中添加方法实现（在 `modId()` 方法之后）：

```java
    @Override
    public Component displayName() {
        return Component.literal("Xaero's World Map");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("xaeroworldmap", "Xaero's World Map");
    }
```

并在 import 块添加：

```java
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
```

- [ ] **Step 3: 实现 JourneyMapOverlayProvider.displayName/defaultConfig**

在 `JourneyMapOverlayProvider.java` 中添加方法实现（在 `modId()` 方法之后）：

```java
    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public ProviderConfig defaultConfig() {
        // JM Mixin 推迟，但 defaultConfig 仍注册以便出现在 CLI/UI
        return ProviderConfig.defaultValue("journeymap", "JourneyMap");
    }
```

并在 import 块添加：

```java
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
```

- [ ] **Step 4: KineticPlannerClient 启动时注册默认配置**

修改 `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`，在 `onClientSetup` 中注册：

```java
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");

        // 注册各 provider 的默认配置
        for (MapOverlayProvider p : MapOverlayDispatcher.registeredProviders()) {
            ProviderConfigRegistry.register(p.modId(), p.defaultConfig());
        }
    }
```

import 块添加：

```java
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
```

- [ ] **Step 5: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: extend MapOverlayProvider interface with displayName/defaultConfig + registry registration"
```

---

### Task A5: WorldTreeReadOverlay 视觉切换 -- 根据 active provider 应用 scale（client）

> **依赖**：Task A3（activeProviderModId）、Task A2（getProviderConfig）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`

**Interfaces:**
- Consumes: `MapOverlayDispatcher.activeProviderModId()` / `KPConfig.getProviderConfig(modId)`
- Produces: 渲染时根据 active provider 的 lineWidthScale / alphaScale 调整视觉

- [ ] **Step 1: 在 onClientTick 中记录 active provider config**

修改 `WorldTreeReadOverlay.java`，添加字段：

```java
    /** 当前 active provider 的视觉 scale，每 tick 更新。 */
    private static float activeLineWidthScale = 1.0f;
    private static float activeAlphaScale = 1.0f;
    private static boolean activeDashed = false;
```

在 `onClientTick` 末尾（`geometryCache.update` 之后）添加：

```java
        // 更新 active provider 视觉 scale
        MapOverlayDispatcher.activeProviderModId().ifPresentOrElse(
            modId -> {
                var pc = KPConfig.getProviderConfig(modId);
                if (pc != null) {
                    activeLineWidthScale = pc.lineWidthScale();
                    activeAlphaScale = pc.alphaScale();
                    activeDashed = pc.dashed();
                }
            },
            () -> {
                activeLineWidthScale = 1.0f;
                activeAlphaScale = 1.0f;
                activeDashed = false;
            }
        );
```

import 块添加（如未有）：

```java
import net.jsmua.kinetic_planner.config.KPConfig;
```

- [ ] **Step 2: 在 onMapRender 中应用 scale**

修改 `onMapRender` 方法中"轨道层"部分：

将：
```java
                if (theme.layers().tracks()) {
                    float widthPx = theme.global().constantScreenLineWidth()
                        ? theme.global().fixedScreenLineWidthPx()
                        : theme.track().width() / (float) lastTransform.cam().blocksPerPixel();
                    for (EdgeGeometry edge : geom.edges()) {
```

替换为：
```java
                if (theme.layers().tracks()) {
                    float widthPx = (theme.global().constantScreenLineWidth()
                        ? theme.global().fixedScreenLineWidthPx()
                        : theme.track().width() / (float) lastTransform.cam().blocksPerPixel())
                        * activeLineWidthScale;
                    int trackColorScaled = applyAlpha(trackColor, theme.track().alpha() * activeAlphaScale);
                    for (EdgeGeometry edge : geom.edges()) {
```

将内层 `engine.drawLine(..., widthPx, trackColor)` 改为 `engine.drawLine(..., widthPx, trackColorScaled)`。

类似地，"节点层"和"边点层"也应用 alphaScale（不改宽度）。

> **注意**：`activeDashed` 字段当前被读取但未应用到 CADRenderEngine（dashed 渲染逻辑推迟到 P1.1）。

- [ ] **Step 3: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: apply per-provider visual scale (lineWidth/alpha) in WorldTreeReadOverlay"
```

---

### Task A6: CLI 命令 -- /kp provider 子命令树 + /kp overlay hide-create（client）

> **依赖**：Task A4（registeredProviders）、Task A2（KPConfig）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/OverlayControl.java`

**Interfaces:**
- Produces: `ProviderConfigControl.listProviders() : String` -- 列出所有 provider 状态
- Produces: `ProviderConfigControl.enable(modId) / disable(modId) : boolean`
- Produces: `ProviderConfigControl.setParam(modId, param, value) : boolean`
- Produces: `ProviderConfigControl.get(modId, param) : String`
- Produces: `ProviderConfigControl.reset(modId) : boolean`
- Produces: `OverlayControl.isHideCreateTrackMap() : boolean` / `setHideCreateTrackMap(boolean)`
- Produces: `/kp provider` 6 个子命令 + `/kp overlay hide-create`

- [ ] **Step 1: 扩展 OverlayControl**

在 `OverlayControl.java` 中添加：

```java
    /**
     * 查询是否隐藏 Create 的信号边组叠加层。
     *
     * @return {@code true} 如果配置为隐藏且叠加层启用
     */
    public static boolean isHideCreateTrackMap() {
        return KPConfig.HIDE_CREATE_TRACK_MAP.get();
    }

    /**
     * 设置是否隐藏 Create 的信号边组叠加层。
     *
     * @param hide 是否隐藏
     */
    public static void setHideCreateTrackMap(boolean hide) {
        KPConfig.HIDE_CREATE_TRACK_MAP.set(hide);
    }
```

- [ ] **Step 2: 实现 ProviderConfigControl**

创建 `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provider 配置状态管理器。
 *
 * <p>封装 per-provider 配置的 CRUD 操作，命令层通过此类操作配置，
 * 不直接访问 {@link KPConfig} 或 {@link WorldTreeReadOverlay}。
 *
 * <p>修改配置后自动通过 {@link OverlayControl#reload()} 重建 Theme 并应用。
 */
public final class ProviderConfigControl {

    private ProviderConfigControl() {}

    /**
     * 列出所有已注册 provider 及其状态。
     *
     * @return 格式化字符串，每行一个 provider
     */
    public static String listProviders() {
        Optional<String> active = MapOverlayDispatcher.activeProviderModId();
        List<String> lines = new ArrayList<>();
        lines.add("[KP] Map Providers:");
        for (MapOverlayProvider p : MapOverlayDispatcher.registeredProviders()) {
            ProviderConfig config = KPConfig.getProviderConfig(p.modId());
            String status = config != null && config.enabled() ? "ON" : "OFF";
            String activeMark = active.map(a -> a.equals(p.modId()) ? " *" : "  ").orElse("  ");
            lines.add(String.format("%s %-15s [%s] pri=%d lineWidth=%.2f alpha=%.2f dashed=%s(P1.1)",
                activeMark, p.modId(), status,
                config != null ? config.priority() : 0,
                config != null ? config.lineWidthScale() : 1.0f,
                config != null ? config.alphaScale() : 1.0f,
                config != null && config.dashed()));
        }
        return String.join("\n", lines);
    }

    /**
     * 启用指定 provider。
     *
     * @param modId provider mod ID
     * @return true 如果设置成功
     */
    public static boolean enable(String modId) {
        if (!isKnownModId(modId)) return false;
        boolean ok = KPConfig.setProviderEnabled(modId, true);
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 禁用指定 provider。
     *
     * @param modId provider mod ID
     * @return true 如果设置成功
     */
    public static boolean disable(String modId) {
        if (!isKnownModId(modId)) return false;
        boolean ok = KPConfig.setProviderEnabled(modId, false);
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 设置 provider 的视觉参数。
     *
     * @param modId  provider mod ID
     * @param param  参数名（lineWidthScale / alphaScale / dashed / priority）
     * @param value  字符串形式的值
     * @return true 如果设置成功
     */
    public static boolean setParam(String modId, String param, String value) {
        if (!isKnownModId(modId)) return false;
        boolean ok = KPConfig.setProviderParam(modId, param, value);
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 查询 provider 的配置或单个参数。
     *
     * @param modId provider mod ID
     * @param param 参数名（null 表示查询全部）
     * @return 格式化字符串
     */
    public static String get(String modId, String param) {
        ProviderConfig config = KPConfig.getProviderConfig(modId);
        if (config == null) {
            return "[KP] Unknown provider: " + modId;
        }
        if (param == null || param.isEmpty()) {
            return String.format("[KP] %s | enabled=%s | priority=%d | lineWidth=%.2f | alpha=%.2f | dashed=%s(P1.1)",
                modId, config.enabled(), config.priority(),
                config.lineWidthScale(), config.alphaScale(), config.dashed());
        }
        return switch (param) {
            case "enabled" -> "[KP] " + modId + ".enabled = " + config.enabled();
            case "priority" -> "[KP] " + modId + ".priority = " + config.priority();
            case "lineWidthScale" -> "[KP] " + modId + ".lineWidthScale = " + config.lineWidthScale();
            case "alphaScale" -> "[KP] " + modId + ".alphaScale = " + config.alphaScale();
            case "dashed" -> "[KP] " + modId + ".dashed = " + config.dashed() + " (P1.1)";
            default -> "[KP] Unknown param: " + param + " (valid: enabled/priority/lineWidthScale/alphaScale/dashed)";
        };
    }

    /**
     * 重置 provider 配置为默认值。
     *
     * @param modId provider mod ID
     * @return true 如果重置成功
     */
    public static boolean reset(String modId) {
        ProviderConfig def = ProviderConfigRegistry.getDefault(modId);
        if (def == null) return false;
        boolean ok = KPConfig.setProviderEnabled(modId, def.enabled())
            && KPConfig.setProviderParam(modId, "priority", String.valueOf(def.priority()))
            && KPConfig.setProviderParam(modId, "lineWidthScale", String.valueOf(def.lineWidthScale()))
            && KPConfig.setProviderParam(modId, "alphaScale", String.valueOf(def.alphaScale()))
            && KPConfig.setProviderParam(modId, "dashed", String.valueOf(def.dashed()));
        if (ok) OverlayControl.reload();
        return ok;
    }

    private static boolean isKnownModId(String modId) {
        return MapOverlayDispatcher.registeredProviders().stream()
            .anyMatch(p -> p.modId().equals(modId));
    }
}
```

> **注意**：`dashed` 参数在 CLI 输出中标注 `(P1.1)`，因为该字段当前被读取但未应用到 CADRenderEngine。

- [ ] **Step 3: 在 KPCommands 添加 /kp provider 子命令树 + /kp overlay hide-create**

修改 `KPCommands.java`，在 `register` 方法中 `.then(Commands.literal("debug")...)` 之前添加：

```java
            .then(Commands.literal("provider")
                .then(Commands.literal("list")
                    .executes(KPCommands::providerList))
                .then(Commands.literal("enable")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerEnable)))
                .then(Commands.literal("disable")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerDisable)))
                .then(Commands.literal("set")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .then(Commands.argument("param", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.word())
                                .executes(KPCommands::providerSet)))))
                .then(Commands.literal("get")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerGetAll)
                        .then(Commands.argument("param", StringArgumentType.word())
                            .executes(KPCommands::providerGetParam))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerReset))))
```

在 `overlay` 子命令树中（`status` 之后）添加 `hide-create`：

```java
                .then(Commands.literal("hide-create")
                    .executes(KPCommands::overlayHideCreateToggle)
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(KPCommands::overlayHideCreateSet)))
```

在类末尾添加处理器方法：

```java
    // === /kp provider -- 地图模组 provider 配置 ===

    private static int providerList(CommandContext<CommandSourceStack> ctx) {
        String output = ProviderConfigControl.listProviders();
        ctx.getSource().sendSuccess(() ->
            Component.literal(output), false);
        return 1;
    }

    private static int providerEnable(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.enable(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Provider " + modId + " enabled"), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Unknown provider: " + modId +
                    ". Use /kp provider list to see available providers."));
            return 0;
        }
    }

    private static int providerDisable(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.disable(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Provider " + modId + " disabled"), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Unknown provider: " + modId));
            return 0;
        }
    }

    private static int providerSet(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String param = StringArgumentType.getString(ctx, "param");
        String value = StringArgumentType.getString(ctx, "value");
        if (ProviderConfigControl.setParam(modId, param, value)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] " + modId + "." + param + " = " + value), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Failed to set " + modId + "." + param +
                    " = " + value + " (unknown provider/param or invalid value)"));
            return 0;
        }
    }

    private static int providerGetAll(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String output = ProviderConfigControl.get(modId, null);
        ctx.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    private static int providerGetParam(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String param = StringArgumentType.getString(ctx, "param");
        String output = ProviderConfigControl.get(modId, param);
        ctx.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    private static int providerReset(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.reset(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Provider " + modId + " reset to default"), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Unknown provider: " + modId));
            return 0;
        }
    }

    // === /kp overlay hide-create -- 隐藏 Create 信号边组叠加层 ===

    private static int overlayHideCreateToggle(CommandContext<CommandSourceStack> ctx) {
        boolean newVal = !OverlayControl.isHideCreateTrackMap();
        OverlayControl.setHideCreateTrackMap(newVal);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Hide Create Track Map: " + (newVal ? "ON" : "OFF")), false);
        return 1;
    }

    private static int overlayHideCreateSet(CommandContext<CommandSourceStack> ctx) {
        String value = StringArgumentType.getString(ctx, "value");
        boolean hide = Boolean.parseBoolean(value);
        OverlayControl.setHideCreateTrackMap(hide);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Hide Create Track Map: " + (hide ? "ON" : "OFF")), false);
        return 1;
    }
```

更新 KPCommands 类的 javadoc 命令树注释，添加：

```
 * /kp provider list           -- 列出所有 provider 及状态
 * /kp provider enable <modId> -- 启用某 provider
 * /kp provider disable <modId>-- 禁用某 provider
 * /kp provider set <modId> <param> <value> -- 设置 provider 参数
 * /kp provider get <modId> [param] -- 查询 provider 配置
 * /kp provider reset <modId>  -- 重置 provider 为默认
 * /kp overlay hide-create [true|false] -- 切换/设置隐藏 Create 信号边组叠加层
```

- [ ] **Step 4: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 6: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: add /kp provider CLI commands + /kp overlay hide-create"
```

---

### Task A7: 隐藏 Create Track Map -- CreateTrackVisualizerHiderMixin（client）

> **依赖**：Task A2（KPConfig.HIDE_CREATE_TRACK_MAP / OVERLAY_ENABLED）
>
> **背景**：Create 6.0.10 的 `TrackGraphVisualizer` 有两个静态方法：
> - `visualiseSignalEdgeGroups(TrackGraph)` -- 手持信号物品时在 3D 世界绘制信号边组彩色线条
> - `debugViewGraph(TrackGraph, boolean)` -- F3 调试模式 + Create 配置 `showTrackGraphOnF3` 时绘制完整轨道图
>
> **本 Task 仅隐藏 `visualiseSignalEdgeGroups`**（信号边组叠加层），不干涉 `debugViewGraph`（F3 调试图视图是 Create 自有调试功能）。
>
> **调用链**：`TrackTargetingClient.clientTick()` -> `GlobalRailwayManager.tickSignalOverlay()` -> `TrackGraphVisualizer.visualiseSignalEdgeGroups()`

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mixin/CreateTrackVisualizerHiderMixin.java`
- Modify: `src/main/resources/kinetic_planner.mixins.json`

**Interfaces:**
- Produces: 当 `OVERLAY_ENABLED && HIDE_CREATE_TRACK_MAP` 为 true 时，cancel `visualiseSignalEdgeGroups` 的执行

- [ ] **Step 1: 实现 CreateTrackVisualizerHiderMixin**

创建 `src/client/java/net/jsmua/kinetic_planner/mixin/CreateTrackVisualizerHiderMixin.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphVisualizer;
import net.jsmua.kinetic_planner.config.KPConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隐藏 Create 的信号边组叠加层（TrackGraphVisualizer.visualiseSignalEdgeGroups）。
 *
 * <p>当 KP 叠加层启用且配置 {@code hideCreateTrackMap=true} 时，cancel 该方法，
 * 完全跳过 Create 在 3D 世界中绘制信号边组彩色线条。
 *
 * <p>不干涉 {@code debugViewGraph}（F3 调试图视图）-- 那是 Create 自有调试功能。
 *
 * <p>注意：{@code remap = false} 因为目标类属于 Create mod（非 MC 原生类）。
 */
@Mixin(value = TrackGraphVisualizer.class, remap = false)
public class CreateTrackVisualizerHiderMixin {

    @Inject(method = "visualiseSignalEdgeGroups",
            at = @At("HEAD"), cancellable = true)
    private static void kp$hideSignalEdgeGroups(TrackGraph graph, CallbackInfo ci) {
        if (KPConfig.OVERLAY_ENABLED.get() && KPConfig.HIDE_CREATE_TRACK_MAP.get()) {
            ci.cancel();
        }
    }
}
```

- [ ] **Step 2: 注册 mixin 到 kinetic_planner.mixins.json**

在 `src/main/resources/kinetic_planner.mixins.json` 的 `client` 数组中添加：

```json
"CreateTrackVisualizerHiderMixin"
```

修改后的 `client` 数组：

```json
"client": [
    "TrackGraphAccessor",
    "XaeroMapAccessor",
    "XaeroMapRenderHook",
    "CreateTrackVisualizerHiderMixin"
],
```

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add CreateTrackVisualizerHiderMixin to hide Create signal edge group overlay"
```

---

### Phase A 验收清单

- [ ] `/kp provider list` 显示 xaeroworldmap + journeymap 两个 provider，含状态/priority/scale/dashed(P1.1)
- [ ] `/kp provider disable xaeroworldmap` 后，再次 `/kp provider list` 显示 `[OFF]`，无地图叠加渲染
- [ ] `/kp provider enable xaeroworldmap` 恢复
- [ ] `/kp provider set xaeroworldmap priority 5` 后，provider 列表中 priority 变化
- [ ] `/kp provider set xaeroworldmap lineWidthScale 2.0` 后视觉线宽加倍
- [ ] `/kp provider set xaeroworldmap alphaScale 0.5` 后视觉半透明
- [ ] `/kp provider get xaeroworldmap` 显示完整配置
- [ ] `/kp provider get xaeroworldmap dashed` 显示单个参数（标注 P1.1）
- [ ] `/kp provider reset xaeroworldmap` 恢复默认
- [ ] 配置写入 `config/kineticplanner-client.toml`，重启游戏保留
- [ ] 未知 modId（如 `/kp provider enable foo`）报错提示
- [ ] 未知 param（如 `/kp provider set xaeroworldmap foo bar`）报错提示
- [ ] `/kp overlay hide-create` 切换隐藏 Create 信号边组叠加层
- [ ] KP overlay 启用 + hideCreateTrackMap=true 时，手持信号物品不显示 Create 的信号边组线条
- [ ] KP overlay 禁用时，手持信号物品正常显示 Create 的信号边组线条（不受 hideCreateTrackMap 影响）
- [ ] Create F3 调试图视图（showTrackGraphOnF3）不受影响，始终正常工作

---

## Phase B: 嵌入式 UI（CLI 验证后做）

> Phase B 是 Phase A 之上的视觉层，依赖 Phase A 的所有数据模型与命令实现。
> Phase B 仅针对 Xaero 实现（JM Mixin 推迟）。

### 技术方案：Screen 共存机制

MC 1.21.1 中同一时间只能有一个 `Screen` 处于 active 状态。嵌入式配置面板的方案：

- **不切换 Screen**：配置面板不作为独立 `Screen` 显示，而是通过 Mixin 在 `GuiMap.render` 末尾直接绘制
- `XaeroMapGearButtonMixin` 持有一个 `ProviderConfigScreen` 实例（不是 `Screen` 子类，而是普通渲染器）
- 齿轮按钮点击时切换 `configPanelVisible` 标志
- `GuiMap.render` Mixin 中：若 `configPanelVisible`，在地图之上绘制半透明背景 + 参数控件
- 鼠标事件通过 `GuiMap.mouseClicked` Mixin 路由到配置面板控件
- 关闭面板时设置 `configPanelVisible = false`，回到纯地图交互

### Task B1: MapGearButtonWidget -- 自绘齿轮按钮（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/MapGearButtonWidget.java`

**Interfaces:**
- Produces: `MapGearButtonWidget`（自绘 AbstractButton 子类，鼠标点击触发回调）
- Produces: `MapGearButtonWidget.render(GuiGraphics, int, int, float)` -- 渲染齿轮图标
- Produces: `MapGearButtonWidget.onClick()` -- 打开配置面板

- [ ] **Step 1: 实现 MapGearButtonWidget**

`MapGearButtonWidget` 继承 `net.minecraft.client.gui.components.AbstractButton`，在 `renderWidget` 中用 `GuiGraphics.blit` 绘制齿轮图标（用 MC 内置图标或自绘几何），鼠标悬停显示 tooltip "Kinetic Planner Config"。

具体实现推迟到 Phase B 启动时细化（Phase A 验收通过后再写代码）。

- [ ] **Step 2: 构建验证 + 提交**

略

---

### Task B2: ProviderConfigScreen -- 嵌入式配置面板渲染器（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigScreen.java`

**Interfaces:**
- Produces: `ProviderConfigScreen`（非 Screen 子类，纯渲染器：render / mouseClicked / keyPressed）

- [ ] **Step 1: 实现 ProviderConfigScreen**

`ProviderConfigScreen` 不是 `Screen` 子类，而是一个普通渲染器类，由 `XaeroMapGearButtonMixin` 在 `GuiMap.render` 末尾调用：
- `render(GuiGraphics, int mouseX, int mouseY, float partialTicks)` -- 绘制半透明背景 + provider Tab + 参数控件
- `mouseClicked(double mouseX, double mouseY, int button)` -- 处理鼠标点击
- `keyPressed(int keyCode, int scanCode, int modifiers)` -- 处理 ESC 关闭
- 半透明背景（`GuiGraphics.fill` 黑色 alpha=180）
- 顶部 provider 选择 Tab（每个已注册 provider 一个）
- 中部参数区：enabled toggle / lineWidthScale slider / alphaScale slider / dashed toggle / hideCreateTrackMap toggle
- 底部 "Reset" / "Close" 按钮
- 修改实时通过 `ProviderConfigControl` 应用（与 CLI 共用 control 层）

具体实现推迟到 Phase B 启动时细化。

- [ ] **Step 2: 构建验证 + 提交**

略

---

### Task B3: XaeroMapGearButtonMixin -- Mixin 注入齿轮按钮 + 配置面板（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java`
- Modify: `src/main/resources/kinetic_planner.mixins.json`（在 `client` 数组添加新 mixin）

**Interfaces:**
- Consumes: 现有 `XaeroMapAccessor`（access GuiMap 字段）
- Produces: 在 Xaero GuiMap.render 末尾渲染齿轮按钮 + 配置面板 + 路由鼠标/键盘事件

- [ ] **Step 1: 实现 XaeroMapGearButtonMixin**

`@Mixin(GuiMap.class)` + 两个 `@Inject`：
- `@Inject(method = "render", at = @At("RETURN"))` -- 在 `XaeroMapRenderHook` 之后：
  - 实例化/复用 `MapGearButtonWidget` 并定位到地图右上角
  - 调用 `widget.render(guiGraphics, mouseX, mouseY, partialTicks)`
  - 若 `configPanelVisible`，调用 `ProviderConfigScreen.render(...)`
- `@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)` -- 鼠标事件路由：
  - 若配置面板可见且点击在面板内，`ci.cancel()` 并路由到 `ProviderConfigScreen.mouseClicked`
  - 若点击齿轮按钮，切换 `configPanelVisible`

- [ ] **Step 2: 注册 mixin 到 kinetic_planner.mixins.json**

在 `client` 数组中添加（注意：文件名是 `kinetic_planner.mixins.json`，不是 `kinetic_planner.client.mixins.json`）：
```json
"XaeroMapGearButtonMixin"
```

- [ ] **Step 3: 构建验证 + 提交**

略

---

### Phase B 验收清单

- [ ] Xaero 全屏地图右上角显示齿轮按钮
- [ ] 鼠标悬停齿轮按钮显示 tooltip "Kinetic Planner Config"
- [ ] 点击齿轮按钮弹出半透明配置面板（地图仍可见）
- [ ] 配置面板顶部显示 provider Tab（xaeroworldmap / journeymap）
- [ ] 切换 Tab 显示对应 provider 的参数
- [ ] 修改 enabled toggle 实时生效（禁用 Xaero 后地图叠加消失）
- [ ] 修改 lineWidthScale slider 实时生效（视觉线宽变化）
- [ ] 配置面板包含 hideCreateTrackMap toggle
- [ ] 点击 "Close" 按钮关闭面板，回到地图
- [ ] JM Tab 显示但 `isMapOpen` 仍返回 false（占位，Mixin 推迟）

---

## Self-Review

**1. Spec coverage:**
- P1.0-1: ProviderConfig + Registry -> Task A1 ✓
- P1.0-2: KPConfig 嵌套段 + hideCreateTrackMap -> Task A2 ✓
- P1.0-3: MapOverlayDispatcher priority 排序 + enabled 过滤 -> Task A3 ✓
- P1.0-4: MapOverlayProvider 接口扩展 -> Task A4 ✓
- P1.0-5: WorldTreeReadOverlay 视觉切换 -> Task A5 ✓
- P1.0-6: CLI 命令 + hide-create -> Task A6 ✓
- P1.0-7: 隐藏 Create Track Map -> Task A7 ✓
- P1.0-8: 嵌入式 UI -> Task B1/B2/B3（Phase B，大纲） ✓

**2. Task 依赖链（无循环）：**
- A1（ProviderConfig + Registry）-> 无依赖
- A2（KPConfig）-> 依赖 A1
- A3（Dispatcher）-> 依赖 A2（KPConfig.getProviderConfig）
- A4（Provider 接口）-> 依赖 A1（ProviderConfig）、A3（registeredProviders）
- A5（WorldTreeReadOverlay）-> 依赖 A3（activeProviderModId）、A2（getProviderConfig）
- A6（CLI 命令）-> 依赖 A4（registeredProviders）、A2（KPConfig）
- A7（Hide Create Mixin）-> 依赖 A2（KPConfig.HIDE_CREATE_TRACK_MAP / OVERLAY_ENABLED）

**3. Type consistency:**
- `ProviderConfig` record：Task A1 定义，A2/A4/A5/A6 引用 ✓
- `ProviderConfigRegistry.register/getDefault/getRegisteredModIds`：Task A1 定义，A2/A4/A6 引用 ✓
- `MapOverlayProvider.displayName/defaultConfig`：Task A4 定义，A4 各实现补全 ✓
- `MapOverlayDispatcher.registeredProviders/activeProviderModId`：Task A3 定义，A4/A5/A6 引用 ✓
- `KPConfig.getProviderConfig/setProviderEnabled/setProviderParam`：Task A2 定义，A3/A5/A6 引用 ✓
- `KPConfig.HIDE_CREATE_TRACK_MAP`：Task A2 定义，A6（OverlayControl）/ A7（Mixin）引用 ✓
- `ProviderConfigControl.listProviders/enable/disable/setParam/get/reset`：Task A6 定义，A6 命令调用 ✓
- `OverlayControl.isHideCreateTrackMap/setHideCreateTrackMap`：Task A6 定义，A6 命令调用 ✓

**4. 已知限制:**
- KPConfig 仅预定义 xaeroworldmap / journeymap 两个 modId 段；第三方地图模组（FTB Chunks / VoxelMap）需后续扩展
- Phase B 嵌入式 UI 仅 Xaero 实现；JM 推迟
- `activeDashed` 字段当前在 WorldTreeReadOverlay 中读取但未应用到 CADRenderEngine（dashed 渲染逻辑推迟到 P1.1）；CLI 输出中已标注 `(P1.1)`
- TOML 写盘由 NeoForge 自动处理，但运行时修改 `setProviderParam` 后需触发 `OverlayControl.reload()` 才能应用到渲染层
- CreateTrackVisualizerHiderMixin 仅隐藏 `visualiseSignalEdgeGroups`，不干涉 `debugViewGraph`（F3 调试图视图）
- Mixin 使用 `remap = false`（目标类属于 Create mod，非 MC 原生类）
- `displayName()` 返回硬编码 `Component.literal(...)`，i18n 推迟到后续
- 未来可扩展：Mixin 设计支持后续添加更多 Create 可视化拦截点

**5. 与命令树设计规格的关系:**
- 本 plan 的 `/kp provider` 6 个子命令对应规格 §"provider" 域的初版子集
- `/kp overlay hide-create` 是本 plan 新增命令，对应规格中未预定义的 overlay 扩展
- 规格中后续命令（如 `/kp provider priority <modId> <n>`）推迟到 P1.1

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-23-kinetic-planner-p1.0-provider-config.md`.

**推荐执行顺序：**
1. **Phase A 串行执行**：Task A1 -> A2 -> A3 -> A4 -> A5 -> A6 -> A7（A2/A3 可合并提交）
2. **Phase A 验收**：手动跑游戏验证 16 项验收清单
3. **Phase B 启动决策**：Phase A 验收通过后，根据用户需求决定是否立即启动 Phase B（嵌入式 UI）或先做 P1.1（矢量绘制打磨）

**两种执行方式：**
1. **Subagent-Driven（推荐）** - 每 Task 派一个新 subagent，task 间 review
2. **Inline Execution** - 当前会话用 executing-plans，批量执行 + checkpoint

哪种方式？
