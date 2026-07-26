# Kinetic Planner P0.5 + P1 Phase B + P1.1 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 JourneyMap 地图适配器（官方 API，无 Mixin）、精细化熔断器、嵌入式 UI（Xaero + JM 双路齿轮按钮+配置面板）、dashed 虚线渲染。

**Architecture:** 三条并行 Track：(A) JM Plugin API 适配器 -> JM Provider 实现；(B) 熔断器时间戳改造 -> provider reset-circuit 命令；(C) UI 组件（GearButton + ConfigScreen）-> Xaero Mixin 注入 + JM API 事件注入。dashed 渲染在 Phase B 后紧跟。

**Tech Stack:** Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21 / Create 6.0.10 / JourneyMap API 2.0.0 / JUnit 5 / Mockito 5

---

## Global Constraints

- Minecraft 1.21.1，NeoForge 21.1.235，Java 21（toolchain）
- mod_id: `kinetic_planner`，mod_group_id: `net.jsmua.kinetic_planner`
- sourceSet 分离：`main`（common）不引用 `net.minecraft.client.*`；`client` 可引用全部
- Mixin 配置文件为单一 `kinetic_planner.mixins.json`（`client` 数组），`defaultRequire: 0`，无 MixinPlugin
- NeoForge 1.21.1 客户端命令：`RegisterClientCommandsEvent.getDispatcher()` 返回 `CommandDispatcher<CommandSourceStack>`
- MC 1.21.1 VertexConsumer API：`addVertex` / `setColor` / `setNormal`，无 `endVertex`
- **JM API 是软依赖**：JM API 类在编译期可用（`localImplementation`），运行期仅 JM 安装时可用。所有 JM API 引用必须在 `ModList.get().isLoaded("journeymap")` 守卫之后
- **`@JourneyMapPlugin` 注解**：由 JM 自动发现并加载，JM 不在时不加载该类
- **UI 嵌入到全屏地图界面内部**（不是 NeoForge mod 配置屏幕），纯客户端
- **adapter 域合并到 provider 域**：不新建 `/kp adapter` 命令树，在 provider 域添加 `reset-circuit` 子命令

---

## 关键决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| JM 适配器方案 | JM Plugin API（`IClientPlugin` + `@JourneyMapPlugin`） | 官方 API，稳定完整，无需 Mixin |
| JM isMapOpen | `api.getUIState(Context.UI.Fullscreen).active` | UIState.active 直接反映全屏地图是否打开 |
| JM captureContext | `state.mapCenter` -> cameraX/Z, `1/state.blockSize` -> blocksPerPixel | blockSize = zoom/512.0 = 每方块像素数，取倒数得 blocksPerPixel |
| adapter 域 | 合并到 provider 域，新增 `/kp provider reset-circuit` | 避免与 provider 域命令冗余 |
| 熔断器 | `Map<String, Long>` 时间戳 + 200 tick 自动重试 | 支持自动恢复 + 手动重置 |
| Phase B UI | Xaero Mixin + JM API 双路 | 两地图模组都有齿轮按钮，体验一致 |
| Phase B 渲染器 | `ProviderConfigScreen` 纯渲染器（非 Screen 子类） | 不能切换 Screen，在地图之上直接绘制 |
| JM 事件 | `FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT` | 提供 GuiGraphics + mouseX/Y，在地图渲染后、按钮前调用 |
| P1.1 dashed | 三角形带分段，`LineGeometry.buildDashedLineSegments` | 沿线段方向按 dash:gap 分段，每段生成三角形带 |

---

## 架构总览

### JM 适配器数据流

```
JM 安装时:
  JM 扫描 @JourneyMapPlugin -> 加载 KineticPlannerJMPlugin -> initialize(IClientAPI)
  -> api 引用保存在 static 字段

每 tick:
  MapOverlayDispatcher.tick() -> JourneyMapOverlayProvider.isMapOpen(screen)
    -> ModList.get().isLoaded("journeymap") 守卫
    -> KineticPlannerJMPlugin.getApi().getUIState(Context.UI.Fullscreen).active
  -> captureContext()
    -> state.mapCenter -> cameraX/Z
    -> 1/state.blockSize -> blocksPerPixel
    -> state.dimension -> 维度

JM 未安装时:
  isJourneyMapLoaded() 返回 false -> isMapOpen 返回 false -> 正常跳过
```

### 熔断器数据流

```
每 tick:
  MapOverlayDispatcher.tick()
    -> 对每个 provider 检查 FAILED map
    -> 若 FAILED 且未过冷却 (200 tick) -> 跳过
    -> 若 FAILED 且已过冷却 -> 移除记录，自动重试
    -> 若 isMapOpen/captureContext 抛异常 -> FAILED.put(modId, currentTick)

/kp provider reset-circuit [modId]:
  -> MapOverlayDispatcher.resetCircuitBreaker(modId)
  -> 清除 FAILED 记录
```

### Phase B UI 数据流

```
Xaero 全屏地图:
  XaeroMapGearButtonMixin @Inject(GuiMap.render, RETURN)
    -> MapGearButtonWidget.render(gg, mouseX, mouseY)
    -> 若 configPanelVisible: ProviderConfigScreen.render(gg, mouseX, mouseY)
  XaeroMapGearButtonMixin @Inject(GuiMap.mouseClicked, HEAD, cancellable)
    -> MapGearButtonWidget.mouseClicked() -> 切换 configPanelVisible
    -> 若 configPanelVisible: ProviderConfigScreen.mouseClicked()

JM 全屏地图:
  FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT -> 添加 "KP Config" 按钮
  FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT -> ProviderConfigScreen.render()
  FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT (PRE) -> ProviderConfigScreen.mouseClicked()
```

### sourceSet 归属

| sourceSet | 新增/变更类 |
|---|---|
| client | `KineticPlannerJMPlugin`(新), `JourneyMapOverlayProvider`(改), `MapOverlayDispatcher`(改), `KPCommands`(改), `ProviderConfigControl`(改), `MapGearButtonWidget`(新), `ProviderConfigScreen`(新), `XaeroMapGearButtonMixin`(新), `LineGeometry`(改, P1.1) |

---

## File Structure

### 新建文件

| 路径 | sourceSet | 阶段 | 职责 |
|---|---|---|---|
| `src/client/java/.../mapadapter/KineticPlannerJMPlugin.java` | client | T1 | JM API 插件入口，持有 IClientAPI 引用 |
| `src/client/java/.../config/MapGearButtonWidget.java` | client | T5 | 自绘齿轮按钮（纯渲染+事件，非 Widget 子类） |
| `src/client/java/.../config/ProviderConfigScreen.java` | client | T6 | 嵌入式配置面板渲染器（非 Screen 子类） |
| `src/client/java/.../mixin/XaeroMapGearButtonMixin.java` | client | T7 | Mixin 注入齿轮按钮到 Xaero GuiMap |

### 修改文件

| 路径 | 修改内容 | Task |
|---|---|---|
| `JourneyMapOverlayProvider.java` | 实现 isMapOpen + captureContext（JM API） | T2 |
| `MapOverlayDispatcher.java` | 熔断器时间戳改造 + resetCircuitBreaker + isCircuitBroken | T3 |
| `KPCommands.java` | 添加 `/kp provider reset-circuit` 命令 | T4 |
| `ProviderConfigControl.java` | listProviders 输出增加熔断状态 + resetCircuit 方法 | T4 |
| `KineticPlannerJMPlugin.java` | T8 扩展：订阅 FullscreenEventRegistry 事件 | T8 |
| `kinetic_planner.mixins.json` | client 数组添加 XaeroMapGearButtonMixin | T7 |
| `LineGeometry.java` | 新增 buildDashedLineSegments 方法 | T9 |
| `CADRenderEngine.java` | drawLine 增加 dashed 重载 | T9 |
| `WorldTreeReadOverlay.java` | drawLine 调用传入 dashed 参数 | T9 |

---

## Task 1: KineticPlannerJMPlugin -- JM API 插件入口（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mapadapter/KineticPlannerJMPlugin.java`

**Interfaces:**
- Produces: `KineticPlannerJMPlugin.getApi() : @Nullable IClientAPI` -- 供 JourneyMapOverlayProvider 调用
- Produces: `KineticPlannerJMPlugin.getModId() : String` -- 返回 "kinetic_planner"

- [ ] **Step 1: 实现 KineticPlannerJMPlugin**

创建 `src/client/java/net/jsmua/kinetic_planner/mapadapter/KineticPlannerJMPlugin.java`：

```java
package net.jsmua.kinetic_planner.mapadapter;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.jsmua.kinetic_planner.KineticPlannerMod;

import javax.annotation.Nullable;

/**
 * JourneyMap 客户端插件入口。
 *
 * <p>通过 {@code @JourneyMapPlugin} 注解由 JM 自动发现并加载。
 * JM 不安装时此类不会被加载，因此可以安全引用 JM API 类型。
 *
 * <p>在 {@link #initialize} 中接收 {@link IClientAPI} 实例并保存在 static 字段，
 * 供 {@link JourneyMapOverlayProvider} 通过 {@link #getApi()} 获取。
 *
 * <p>Phase B 扩展：在 initialize 中订阅 FullscreenEventRegistry 事件，
 * 实现 JM 全屏地图上的齿轮按钮和配置面板。
 */
@JourneyMapPlugin(apiVersion = "2.0.0-SNAPSHOT")
public class KineticPlannerJMPlugin implements IClientPlugin {

    @Nullable
    private static IClientAPI api;

    @Override
    public String getModId() {
        return KineticPlannerMod.MODID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        KineticPlannerMod.LOGGER.info("[KP] JourneyMap API initialized");
    }

    /**
     * 返回 JM 客户端 API 实例。
     *
     * <p>仅在 JM 安装且插件已初始化时非 null。
     * 调用方应先检查 {@code ModList.get().isLoaded("journeymap")}。
     *
     * @return IClientAPI 实例，或 null 如果 JM 未安装/未初始化
     */
    @Nullable
    public static IClientAPI getApi() {
        return api;
    }
}
```

- [ ] **Step 2: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: add KineticPlannerJMPlugin as JourneyMap API entry point"
```

---

## Task 2: JourneyMapOverlayProvider 实现 -- isMapOpen + captureContext（client）

> **依赖**：Task 1（KineticPlannerJMPlugin.getApi）
> **关键**：JM API 类型引用必须在 `isJourneyMapLoaded()` 守卫之后，防止 JM 未安装时 NoClassDefFoundError

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/JourneyMapOverlayProvider.java`

**Interfaces:**
- Consumes: `KineticPlannerJMPlugin.getApi()` / `IClientAPI.getUIState(Context.UI.Fullscreen)` / `UIState.active/mapCenter/blockSize/dimension`
- Produces: `JourneyMapOverlayProvider.isMapOpen()` 返回真实值（不再恒 false）
- Produces: `JourneyMapOverlayProvider.captureContext()` 返回 `MapOverlayContext`

- [ ] **Step 1: 重写 JourneyMapOverlayProvider**

将整个文件替换为：

```java
package net.jsmua.kinetic_planner.mapadapter;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.util.UIState;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import javax.annotation.Nullable;

/**
 * JourneyMap 的 {@link MapOverlayProvider} 实现。
 *
 * <p>通过 JM 官方 API（{@link IClientAPI#getUIState}）检测全屏地图状态并捕获上下文。
 * 无需 Mixin。
 *
 * <h2>JM API 字段映射</h2>
 * <ul>
 *   <li>{@code state.active} -> 是否全屏地图打开</li>
 *   <li>{@code state.mapCenter} (BlockPos) -> cameraBlockX / cameraBlockZ</li>
 *   <li>{@code 1 / state.blockSize} -> blocksPerPixel（blockSize = zoom/512，即每方块像素数）</li>
 *   <li>{@code state.dimension} -> 维度</li>
 * </ul>
 *
 * <h2>软依赖守卫</h2>
 * <p>所有 JM API 类型引用在 {@link #isJourneyMapLoaded()} 守卫之后。
 * JM 未安装时 {@code isJourneyMapLoaded()} 返回 false，不触发 JM API 类加载。
 */
public class JourneyMapOverlayProvider implements MapOverlayProvider {

    private static Boolean jmLoaded;

    /**
     * 检查 JourneyMap 是否已安装。
     * 缓存结果避免每 tick 重复查询。
     */
    private static boolean isJourneyMapLoaded() {
        if (jmLoaded == null) {
            jmLoaded = ModList.get().isLoaded("journeymap");
        }
        return jmLoaded;
    }

    @Override
    public boolean isMapOpen(Screen screen) {
        if (!isJourneyMapLoaded()) return false;
        return jmIsMapOpen();
    }

    private boolean jmIsMapOpen() {
        try {
            IClientAPI api = KineticPlannerJMPlugin.getApi();
            if (api == null) return false;
            UIState state = api.getUIState(Context.UI.Fullscreen);
            return state != null && state.active;
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("JM isMapOpen failed", t);
            return false;
        }
    }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) {
        if (!isJourneyMapLoaded()) return null;
        return jmCaptureContext();
    }

    @Nullable
    private MapOverlayContext jmCaptureContext() {
        try {
            IClientAPI api = KineticPlannerJMPlugin.getApi();
            if (api == null) return null;
            UIState state = api.getUIState(Context.UI.Fullscreen);
            if (state == null || !state.active) return null;

            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            float dpr = (float) mc.getWindow().getScreenWidth() / screenWidth;

            // blockSize = zoom / 512.0，表示一个方块在屏幕上的像素宽度
            // blocksPerPixel = 1 / blockSize（每像素代表多少方块）
            double blocksPerPixel = state.blockSize > 0 ? 1.0 / state.blockSize : 1.0;

            return new MapOverlayContext(
                state.dimension,
                state.mapCenter != null ? state.mapCenter.getX() : 0,
                state.mapCenter != null ? state.mapCenter.getZ() : 0,
                blocksPerPixel,
                screenWidth, screenHeight,
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(),
                0f,
                dpr
            );
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("JM captureContext failed", t);
            return null;
        }
    }

    @Override
    public String modId() { return "journeymap"; }

    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("journeymap", "JourneyMap");
    }
}
```

- [ ] **Step 2: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 运行测试验证无回归**

运行：`gradlew test`
预期：全部 42 个测试 PASS

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: implement JourneyMapOverlayProvider with JM API (isMapOpen + captureContext)"
```

---

## Task 3: 熔断器精细化 -- MapOverlayDispatcher 改造（client）

> **依赖**：无
> **变更**：`FAILED` 从 `Set<String>` 改为 `Map<String, Long>`（modId -> 熔断 tick 时间戳）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayDispatcher.java`

**Interfaces:**
- Produces: `MapOverlayDispatcher.resetCircuitBreaker(@Nullable String modId)` -- 手动重置熔断
- Produces: `MapOverlayDispatcher.isCircuitBroken(String modId) : boolean` -- 查询熔断状态
- Produces: `MapOverlayDispatcher.getFailedProviders() : Set<String>` -- 获取已熔断 modId 集合

- [ ] **Step 1: 改造 FAILED 数据结构和 tick() 方法**

在 `MapOverlayDispatcher.java` 中，做以下修改：

1. 将 `FAILED` 字段改为 `Map<String, Long>`：

```java
    /** 已熔断的 provider modId -> 熔断时的游戏时间（tick）。 */
    private static final Map<String, Long> FAILED = new HashMap<>();

    /** 熔断冷却时间（tick），到期后自动重试一次。200 tick = 10 秒。 */
    private static final long CIRCUIT_BREAK_COOLDOWN = 200;
```

2. 在 `tick()` 方法的 for 循环中，将 `if (FAILED.contains(p.modId())) continue;` 替换为熔断检查+自动重试逻辑：

```java
        long currentTick = Minecraft.getInstance().level != null
            ? Minecraft.getInstance().level.getGameTime()
            : 0;

        for (MapOverlayProvider p : sorted) {
            String modId = p.modId();
            // 检查熔断状态
            Long failedAt = FAILED.get(modId);
            if (failedAt != null) {
                long elapsed = currentTick - failedAt;
                if (elapsed < CIRCUIT_BREAK_COOLDOWN) continue;
                // 冷却到期，自动重试
                FAILED.remove(modId);
                KineticPlannerMod.LOGGER.info("Circuit breaker for {} expired, retrying", modId);
            }
```

3. 在 catch 块中，将 `FAILED.add(p.modId())` 改为 `FAILED.put(modId, currentTick)`：

```java
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", modId, t);
                FAILED.put(modId, currentTick);
            }
```

4. 添加 import（如未有）：

```java
import java.util.Map;
import java.util.HashMap;
import javax.annotation.Nullable;
```

- [ ] **Step 2: 添加 resetCircuitBreaker / isCircuitBroken / getFailedProviders 方法**

在 `MapOverlayDispatcher` 类末尾（`activeProviderModId()` 之后）添加：

```java
    /**
     * 重置熔断状态。
     *
     * @param modId provider mod ID；null 表示重置全部
     */
    public static void resetCircuitBreaker(@Nullable String modId) {
        if (modId != null) {
            FAILED.remove(modId);
        } else {
            FAILED.clear();
        }
    }

    /**
     * 查询指定 provider 是否处于熔断状态。
     *
     * @param modId provider mod ID
     * @return true 如果该 provider 当前被熔断
     */
    public static boolean isCircuitBroken(String modId) {
        return FAILED.containsKey(modId);
    }

    /**
     * 返回所有当前被熔断的 provider modId 集合（快照）。
     *
     * @return 不可变 modId 集合
     */
    public static Set<String> getFailedProviders() {
        return Set.copyOf(FAILED.keySet());
    }
```

- [ ] **Step 3: 更新类 JavaDoc**

将类 JavaDoc 中的熔断机制描述更新为：

```java
 * <h2>熔断机制</h2>
 * <p>当某个 provider 在 {@code isMapOpen} 或 {@code captureContext} 中抛出异常时，
 * 其 modId 和当前游戏 tick 被记录到 {@link #FAILED} 映射，后续 tick 在冷却期内跳过该 provider。
 * 冷却期（{@value #CIRCUIT_BREAK_COOLDOWN} tick = 10 秒）到期后自动重试一次。
 * 重试仍失败则重新记录熔断时间，延长冷却。
 *
 * <p>可通过 {@link #resetCircuitBreaker(String)} 手动清除熔断状态，
 * 或通过命令 {@code /kp provider reset-circuit [modId]} 触发。
```

- [ ] **Step 4: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: refine circuit breaker with timestamp + auto-retry + manual reset"
```

---

## Task 4: provider reset-circuit 命令 + list 输出增加熔断状态（client）

> **依赖**：Task 3（MapOverlayDispatcher.resetCircuitBreaker / isCircuitBroken / getFailedProviders）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java`

**Interfaces:**
- Produces: `/kp provider reset-circuit [modId]` -- 重置指定或全部 provider 的熔断状态
- Produces: `ProviderConfigControl.listProviders()` 输出增加 `[FUSED]` 标记
- Produces: `ProviderConfigControl.resetCircuit(@Nullable String modId) : boolean`

- [ ] **Step 1: 在 ProviderConfigControl 添加 resetCircuit 方法**

在 `ProviderConfigControl.java` 的 `reset` 方法之后添加：

```java
    /**
     * 重置 provider 的熔断状态。
     *
     * @param modId provider mod ID；null 表示重置全部
     * @return true 如果有熔断被重置
     */
    public static boolean resetCircuit(@Nullable String modId) {
        var failed = MapOverlayDispatcher.getFailedProviders();
        if (modId != null) {
            boolean wasBroken = failed.contains(modId);
            MapOverlayDispatcher.resetCircuitBreaker(modId);
            return wasBroken;
        } else {
            boolean hadAny = !failed.isEmpty();
            MapOverlayDispatcher.resetCircuitBreaker(null);
            return hadAny;
        }
    }
```

添加 import：

```java
import java.util.Map;
```

- [ ] **Step 2: 修改 listProviders 输出增加熔断状态**

在 `ProviderConfigControl.listProviders()` 中，将每行输出改为包含熔断标记。找到当前的 `lines.add(String.format(...))` 行，替换为：

```java
            String fusedMark = MapOverlayDispatcher.isCircuitBroken(p.modId()) ? " [FUSED]" : "";
            lines.add(String.format("%s %-15s [%s] pri=%d lineWidth=%.2f alpha=%.2f dashed=%s%s",
                activeMark, p.modId(), status,
                config != null ? config.priority() : 0,
                config != null ? config.lineWidthScale() : 1.0f,
                config != null ? config.alphaScale() : 1.0f,
                config != null && config.dashed() ? "true" : "false",
                fusedMark));
```

注意：移除了 `dashed=%s(P1.1)` 中的 `(P1.1)` 后缀（P1.1 在本轮实现后 dashed 将生效）。

- [ ] **Step 3: 在 KPCommands 添加 reset-circuit 子命令**

在 `KPCommands.java` 的 `register()` 方法中，在 `provider` 子命令树的 `reset` 之后添加：

```java
                .then(Commands.literal("reset-circuit")
                    .executes(KPCommands::providerResetCircuitAll)
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerResetCircuitOne)))
```

在类末尾（`providerReset` 方法之后）添加处理器：

```java
    /**
     * {@code /kp provider reset-circuit}：重置所有 provider 的熔断状态。
     */
    private static int providerResetCircuitAll(CommandContext<CommandSourceStack> ctx) {
        boolean hadAny = ProviderConfigControl.resetCircuit(null);
        if (hadAny) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] All circuit breakers reset"), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] No circuit breakers to reset"), false);
        }
        return 1;
    }

    /**
     * {@code /kp provider reset-circuit <modId>}：重置指定 provider 的熔断状态。
     */
    private static int providerResetCircuitOne(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.resetCircuit(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Circuit breaker reset for " + modId), false);
            return 1;
        } else {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] No circuit breaker for " + modId), false);
            return 0;
        }
    }
```

- [ ] **Step 4: 更新命令树注释**

在 `KPCommands` 类的 JavaDoc 命令树注释中，在 `reset` 行之后添加：

```
 * /kp provider reset-circuit [modId] -- 重置熔断状态（省略 modId 重置全部）
```

- [ ] **Step 5: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 6: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 7: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "feat: add /kp provider reset-circuit command + FUSED status in provider list"
```

---

## Task 5: MapGearButtonWidget -- 自绘齿轮按钮（client）

> **依赖**：无（纯 UI 组件）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/MapGearButtonWidget.java`

**Interfaces:**
- Produces: `MapGearButtonWidget(int x, int y, Runnable onClick)` -- 构造器
- Produces: `MapGearButtonWidget.render(GuiGraphics, int mouseX, int mouseY)` -- 渲染齿轮按钮
- Produces: `MapGearButtonWidget.mouseClicked(double, double, int) : boolean` -- 鼠标点击处理
- Produces: `MapGearButtonWidget.isHovered() : boolean`

- [ ] **Step 1: 实现 MapGearButtonWidget**

创建 `src/client/java/net/jsmua/kinetic_planner/config/MapGearButtonWidget.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 自绘齿轮按钮，嵌入到地图全屏界面右上角。
 *
 * <p>不是 MC {@code AbstractWidget} 子类（因为不在 Screen.addWidget 体系中），
 * 而是纯渲染+事件处理类，由 Mixin/Event 手动调用 {@link #render} 和 {@link #mouseClicked}。
 *
 * <p>外观：16x16 像素半透明圆角方形背景 + 白色齿轮轮廓（自绘几何）。
 * 鼠标悬停时背景变亮，显示 tooltip "Kinetic Planner"。
 */
public class MapGearButtonWidget {

    private static final int SIZE = 16;
    private static final int BG_COLOR = 0x80000000;
    private static final int BG_HOVER_COLOR = 0xB0404040;
    private static final int GEAR_COLOR = 0xFFFFFFFF;

    private final int x;
    private final int y;
    private final Runnable onClick;
    private boolean hovered;

    public MapGearButtonWidget(int x, int y, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.onClick = onClick;
    }

    /**
     * 渲染齿轮按钮。
     */
    public void render(GuiGraphics gg, int mouseX, int mouseY) {
        hovered = isInside(mouseX, mouseY);

        // 半透明背景
        int bgColor = hovered ? BG_HOVER_COLOR : BG_COLOR;
        gg.fill(x, y, x + SIZE, y + SIZE, bgColor);

        // 齿轮图标：简化为 8 条线段从中心向外辐射 + 中心圆
        int cx = x + SIZE / 2;
        int cy = y + SIZE / 2;
        int outerR = 5;
        int innerR = 2;

        // 用 fill 画粗线段模拟齿轮齿（8 个齿，每 45 度一个）
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            int x1 = (int) (cx + Math.cos(angle) * innerR);
            int y1 = (int) (cy + Math.sin(angle) * innerR);
            int x2 = (int) (cx + Math.cos(angle) * outerR);
            int y2 = (int) (cy + Math.sin(angle) * outerR);
            // 用 1px 宽 fill 模拟线段
            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int maxX = Math.max(x1, x2) + 1;
            int maxY = Math.max(y1, y2) + 1;
            gg.fill(minX, minY, maxX, maxY, GEAR_COLOR);
        }

        // 中心圆（2x2 填充）
        gg.fill(cx - 1, cy - 1, cx + 1, cy + 1, GEAR_COLOR);

        // 悬停 tooltip
        if (hovered) {
            gg.renderTooltip(Minecraft.getInstance().font,
                Component.literal("Kinetic Planner"), mouseX, mouseY);
        }
    }

    /**
     * 处理鼠标点击。
     *
     * @return true 如果点击在按钮内（消费事件）
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInside((int) mouseX, (int) mouseY)) {
            onClick.run();
            return true;
        }
        return false;
    }

    public boolean isHovered() {
        return hovered;
    }

    private boolean isInside(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }
}
```

- [ ] **Step 2: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: add MapGearButtonWidget for map-embedded gear button"
```

---

## Task 6: ProviderConfigScreen -- 嵌入式配置面板渲染器（client）

> **依赖**：Task 5（MapGearButtonWidget）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigScreen.java`

**Interfaces:**
- Produces: `ProviderConfigScreen` 构造器，接收 `MapGearButtonWidget`
- Produces: `ProviderConfigScreen.renderPanel(GuiGraphics, int, int, float)` -- 渲染配置面板
- Produces: `ProviderConfigScreen.handleMouseClick(double, double, int) : boolean` -- 鼠标事件路由
- Produces: `ProviderConfigScreen.isVisible() / setVisible(boolean)` -- 面板可见性

- [ ] **Step 1: 实现 ProviderConfigScreen**

创建 `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigScreen.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 嵌入式配置面板渲染器（非 {@code Screen} 子类）。
 *
 * <p>由 Xaero Mixin 或 JM 事件在地图渲染后调用 {@link #renderPanel}。
 * 面板覆盖在地图之上，半透明背景 + provider Tab + 参数控件。
 *
 * <p>控件交互通过 {@link #handleMouseClick} 路由，不依赖 MC Widget 体系。
 * 修改参数实时通过 {@link ProviderConfigControl} / {@link OverlayControl} 应用。
 */
public class ProviderConfigScreen {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 160;
    private static final int BG_COLOR = 0xC0202020;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_WIDTH = 80;

    private final MapGearButtonWidget gearButton;
    private boolean visible = false;
    private String selectedProviderModId;

    // 控件区域定义（相对于面板左上角）
    private int panelX, panelY;

    public ProviderConfigScreen(MapGearButtonWidget gearButton) {
        this.gearButton = gearButton;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible && selectedProviderModId == null) {
            // 默认选中第一个 provider
            List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
            if (!providers.isEmpty()) {
                selectedProviderModId = providers.get(0).modId();
            }
        }
    }

    public void toggle() {
        setVisible(!visible);
    }

    /**
     * 渲染配置面板。仅在 {@link #visible} 时绘制。
     */
    public void renderPanel(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // 面板定位：右上角，齿轮按钮下方
        panelX = screenWidth - PANEL_WIDTH - 4;
        panelY = 24;

        // 半透明背景
        gg.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BG_COLOR);

        int cursorY = panelY + 4;

        // 标题
        gg.drawString(Minecraft.getInstance().font,
            Component.literal("Kinetic Planner Config"),
            panelX + 6, cursorY, 0xFFFFFFFF);
        cursorY += 14;

        // Provider Tab 行
        List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
        int tabX = panelX + 4;
        for (MapOverlayProvider p : providers) {
            boolean selected = p.modId().equals(selectedProviderModId);
            int tabBg = selected ? 0xFF4040C0 : 0x80404040;
            gg.fill(tabX, cursorY, tabX + TAB_WIDTH, cursorY + TAB_HEIGHT, tabBg);
            String label = p.modId();
            if (MapOverlayDispatcher.isCircuitBroken(p.modId())) {
                label += " [FUSED]";
            }
            gg.drawString(Minecraft.getInstance().font,
                Component.literal(label),
                tabX + 3, cursorY + 4, 0xFFFFFFFF);
            tabX += TAB_WIDTH + 2;
        }
        cursorY += TAB_HEIGHT + 4;

        // 选中 provider 的参数
        if (selectedProviderModId != null) {
            ProviderConfig config = KPConfig.getProviderConfig(selectedProviderModId);
            if (config != null) {
                // enabled toggle
                String enabledText = "[ " + (config.enabled() ? "X" : " ") + " ] Enabled";
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(enabledText),
                    panelX + 6, cursorY, 0xFFFFFFFF);
                cursorY += 12;

                // priority
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal("Priority: " + config.priority()),
                    panelX + 6, cursorY, 0xFFCCCCCC);
                cursorY += 12;

                // lineWidthScale
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(String.format("Line Width: %.2f", config.lineWidthScale())),
                    panelX + 6, cursorY, 0xFFCCCCCC);
                cursorY += 12;

                // alphaScale
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(String.format("Alpha: %.2f", config.alphaScale())),
                    panelX + 6, cursorY, 0xFFCCCCCC);
                cursorY += 12;

                // dashed
                String dashedText = "[ " + (config.dashed() ? "X" : " ") + " ] Dashed";
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(dashedText),
                    panelX + 6, cursorY, 0xFFFFFFFF);
                cursorY += 12;

                // hideCreateTrackMap
                boolean hideCreate = OverlayControl.isHideCreateTrackMap();
                String hideCreateText = "[ " + (hideCreate ? "X" : " ") + " ] Hide Create Track Map";
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(hideCreateText),
                    panelX + 6, cursorY, 0xFFFFFFFF);
                cursorY += 12;

                // 提示
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal("Use /kp provider set for changes"),
                    panelX + 6, cursorY, 0xFF808080);
            }
        }

        // Close 提示
        gg.drawString(Minecraft.getInstance().font,
            Component.literal("Click gear to close"),
            panelX + 6, panelY + PANEL_HEIGHT - 12, 0xFF808080);
    }

    /**
     * 处理鼠标点击事件。
     *
     * <p>仅在面板可见时处理。点击面板内时消费事件，不传递给地图。
     *
     * @return true 如果事件被消费
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;

        // 检查是否点击在面板内
        if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

            // Provider Tab 点击
            int tabY = panelY + 18; // 标题下方
            if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
                int tabX = panelX + 4;
                for (MapOverlayProvider p : providers) {
                    if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH) {
                        selectedProviderModId = p.modId();
                        return true;
                    }
                    tabX += TAB_WIDTH + 2;
                }
            }

            // enabled toggle 点击
            ProviderConfig config = selectedProviderModId != null
                ? KPConfig.getProviderConfig(selectedProviderModId) : null;
            if (config != null) {
                int toggleY = tabY + TAB_HEIGHT + 4;
                // enabled
                if (mouseY >= toggleY && mouseY < toggleY + 12) {
                    ProviderConfigControl.enable(selectedProviderModId);
                    if (config.enabled()) ProviderConfigControl.disable(selectedProviderModId);
                    return true;
                }
                toggleY += 36; // 跳过 priority + lineWidth + alpha
                // dashed
                if (mouseY >= toggleY && mouseY < toggleY + 12) {
                    ProviderConfigControl.setParam(selectedProviderModId, "dashed",
                        String.valueOf(!config.dashed()));
                    return true;
                }
                toggleY += 12;
                // hideCreateTrackMap
                if (mouseY >= toggleY && mouseY < toggleY + 12) {
                    OverlayControl.setHideCreateTrackMap(!OverlayControl.isHideCreateTrackMap());
                    return true;
                }
            }

            // 面板内其他区域：消费但不做操作
            return true;
        }

        return false;
    }
}
```

- [ ] **Step 2: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: add ProviderConfigScreen embedded config panel renderer"
```

---

## Task 7: XaeroMapGearButtonMixin -- Xaero 地图齿轮按钮注入（client）

> **依赖**：Task 5（MapGearButtonWidget）、Task 6（ProviderConfigScreen）
> **注意**：`remap = false` 因为目标是 Xaero 类

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java`
- Modify: `src/main/resources/kinetic_planner.mixins.json`

**Interfaces:**
- Produces: 在 Xaero `GuiMap.render` RETURN 注入齿轮按钮 + 配置面板渲染
- Produces: 在 Xaero `GuiMap.mouseClicked` HEAD 注入鼠标事件路由（cancellable）

- [ ] **Step 1: 实现 XaeroMapGearButtonMixin**

创建 `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapGearButtonMixin.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.MapGearButtonWidget;
import net.jsmua.kinetic_planner.config.ProviderConfigScreen;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * Mixin 注入齿轮按钮和配置面板到 Xaero 全屏地图。
 *
 * <p>在 {@code GuiMap.render} RETURN 注入齿轮按钮渲染 + 配置面板渲染。
 * 在 {@code GuiMap.mouseClicked} HEAD 注入鼠标事件路由（cancellable）。
 *
 * <p>{@code remap = false} 因为目标类属于 Xaero mod（非 MC 原生类）。
 * Mixin 配置 {@code defaultRequire: 0}，Xaero 未安装时不崩溃。
 */
@Mixin(value = GuiMap.class, remap = false)
public class XaeroMapGearButtonMixin {

    @Unique
    private static MapGearButtonWidget kp$gearButton;

    @Unique
    private static ProviderConfigScreen kp$configScreen;

    /**
     * 初始化 UI 组件（懒加载）。
     */
    @Unique
    private static void kp$ensureInit() {
        if (kp$gearButton == null) {
            int screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
            kp$gearButton = new MapGearButtonWidget(screenW - 20, 4, () -> {
                kp$ensureConfigScreen();
                kp$configScreen.toggle();
            });
        }
        // 更新按钮位置（屏幕尺寸可能变化）
        int screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        // MapGearButtonWidget 的 x 位置在构造时固定，这里通过重建适配
    }

    @Unique
    private static void kp$ensureConfigScreen() {
        if (kp$configScreen == null && kp$gearButton != null) {
            kp$configScreen = new ProviderConfigScreen(kp$gearButton);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kp$renderGearButton(GuiGraphics gg, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!OverlayControl.isEnabled()) return;
        kp$ensureInit();
        kp$gearButton.render(gg, mouseX, mouseY);
        kp$ensureConfigScreen();
        kp$configScreen.renderPanel(gg, mouseX, mouseY, partialTicks);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kp$handleMouseClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!OverlayControl.isEnabled()) return;
        kp$ensureInit();
        // 先检查配置面板
        kp$ensureConfigScreen();
        if (kp$configScreen.handleMouseClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        // 再检查齿轮按钮
        if (kp$gearButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
```

> **注意**：`mouseClicked` 方法在 Xaero `GuiMap` 中的签名需运行时验证。
> MC 1.21.1 `Screen.mouseClicked` 签名为 `(double, double, int) : boolean`。
> 若 Xaero 使用不同签名，需调整 `@Inject` 参数类型。
> Mixin `defaultRequire: 0` 保证签名不匹配时不崩溃。

- [ ] **Step 2: 注册 Mixin**

在 `src/main/resources/kinetic_planner.mixins.json` 的 `client` 数组中添加 `"XaeroMapGearButtonMixin"`：

```json
"client": [
    "TrackGraphAccessor",
    "XaeroMapAccessor",
    "XaeroMapRenderHook",
    "CreateTrackVisualizerHiderMixin",
    "XaeroMapGearButtonMixin"
],
```

- [ ] **Step 3: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add XaeroMapGearButtonMixin to inject gear button + config panel into Xaero map"
```

---

## Task 8: JM 齿轮按钮注入 -- FullscreenEventRegistry 事件订阅（client）

> **依赖**：Task 1（KineticPlannerJMPlugin）、Task 5（MapGearButtonWidget）、Task 6（ProviderConfigScreen）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/KineticPlannerJMPlugin.java`

**Interfaces:**
- Consumes: `FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT` -- 添加 KP 按钮到 JM 工具栏
- Consumes: `FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT` -- 渲染齿轮按钮 + 配置面板
- Consumes: `FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT` -- 鼠标点击路由

- [ ] **Step 1: 扩展 KineticPlannerJMPlugin，订阅 JM 事件**

在 `KineticPlannerJMPlugin.java` 中做以下修改：

1. 添加 import：

```java
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.fullscreen.ThemeButtonDisplay;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.jsmua.kinetic_planner.config.MapGearButtonWidget;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.jsmua.kinetic_planner.config.ProviderConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
```

2. 添加 UI 组件字段：

```java
    @Nullable
    private static MapGearButtonWidget jmGearButton;

    @Nullable
    private static ProviderConfigScreen jmConfigScreen;
```

3. 在 `initialize` 方法中添加事件订阅：

```java
    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        KineticPlannerMod.LOGGER.info("[KP] JourneyMap API initialized");

        // 订阅 JM 全屏地图事件
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(
            KineticPlannerMod.MODID, this::onAddonButtonDisplay);
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(
            KineticPlannerMod.MODID, this::onFullscreenRender);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(
            KineticPlannerMod.MODID, this::onFullscreenMapClick);
    }
```

4. 添加事件处理方法：

```java
    /**
     * JM 工具栏按钮显示事件 -- 添加 "KP" 按钮到 JM 右侧面板。
     */
    private void onAddonButtonDisplay(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        ThemeButtonDisplay display = event.getThemeButtonDisplay();
        display.addThemeButton(
            "KP",
            "KP",
            ResourceLocation.fromNamespaceAndPath(KineticPlannerMod.MODID, "textures/gui/gear.png"),
            (button) -> {
                ensureUiComponents();
                if (jmConfigScreen != null) {
                    jmConfigScreen.toggle();
                }
            }
        );
    }

    /**
     * JM 全屏地图渲染事件 -- 在地图渲染后、按钮前渲染齿轮按钮 + 配置面板。
     */
    private void onFullscreenRender(FullscreenRenderEvent event) {
        if (!OverlayControl.isEnabled()) return;
        ensureUiComponents();
        if (jmGearButton == null || jmConfigScreen == null) return;

        GuiGraphics gg = event.getGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        float partialTicks = event.getPartialTicks();

        jmGearButton.render(gg, mouseX, mouseY);
        jmConfigScreen.renderPanel(gg, mouseX, mouseY, partialTicks);
    }

    /**
     * JM 全屏地图鼠标点击事件 -- 路由到配置面板和齿轮按钮。
     */
    private void onFullscreenMapClick(FullscreenMapEvent.ClickEvent event) {
        if (!OverlayControl.isEnabled()) return;
        if (event.getStage() != FullscreenMapEvent.Stage.PRE) return;
        ensureUiComponents();
        if (jmGearButton == null || jmConfigScreen == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int button = event.getButton();

        // 先检查配置面板
        if (jmConfigScreen.handleMouseClick(mouseX, mouseY, button)) {
            event.setCanceled(true);
            return;
        }
        // 再检查齿轮按钮
        if (jmGearButton.mouseClicked(mouseX, mouseY, button)) {
            event.setCanceled(true);
        }
    }

    /**
     * 懒加载 UI 组件。
     */
    private static void ensureUiComponents() {
        if (jmGearButton == null) {
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            jmGearButton = new MapGearButtonWidget(screenW - 20, 4, () -> {
                if (jmConfigScreen != null) jmConfigScreen.toggle();
            });
        }
        if (jmConfigScreen == null && jmGearButton != null) {
            jmConfigScreen = new ProviderConfigScreen(jmGearButton);
        }
    }
```

> **注意**：`ResourceLocation.fromNamespaceAndPath` 是 MC 1.21.1 API。
> `event.setCanceled(true)` 基于 `FullscreenMapEvent.ClickEvent` 的 `isCancellable() == (stage == PRE)`。
> `ThemeButtonDisplay.addThemeButton` 的 icon 参数需要一个 ResourceLocation，如果没有实际纹理文件，
> 可以用一个不存在的路径（JM 会显示无图标的按钮）。运行时验证按钮是否正常显示。

- [ ] **Step 2: 构建验证**

运行：`gradlew compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: subscribe to JM FullscreenEventRegistry for gear button + config panel on JM map"
```

---

## Task 9: dashed 渲染 -- CADRenderEngine 虚线支持（P1.1）

> **依赖**：Task 6（ProviderConfigScreen 中的 dashed toggle）
> **修改**：`LineGeometry` 新增虚线分段方法；`CADRenderEngine` 新增 dashed 重载；`WorldTreeReadOverlay` 传递 dashed 参数

**Files:**
- Modify: `src/main/java/net/jsmua/kinetic_planner/cadengine/LineGeometry.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`

**Interfaces:**
- Produces: `LineGeometry.buildDashedSegments(x1, y1, x2, y2, dashLen, gapLen) : float[][]` -- 分段坐标数组
- Produces: `CADRenderEngine.drawLine(x1, y1, x2, y2, widthPx, color, dashed)` -- dashed 重载
- Produces: `WorldTreeReadOverlay` 渲染时根据 `activeDashed` 传入 dashed 参数

- [ ] **Step 1: 写 LineGeometry 虚线分段测试**

创建或修改 `src/test/java/net/jsmua/kinetic_planner/cadengine/LineGeometryDashedTest.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineGeometryDashedTest {

    @Test
    void horizontalLineProducesCorrectDashCount() {
        // 10 像素线段，dash=2, gap=1 -> 3 dashes (0-2, 3-5, 6-8), 最后 9 不够一个 dash
        float[][] segments = LineGeometry.buildDashedSegments(0, 0, 10, 0, 2, 1);
        assertEquals(3, segments.length);
        // 第一段: 0->2
        assertEquals(0f, segments[0][0], 1e-6f);
        assertEquals(0f, segments[0][1], 1e-6f);
        assertEquals(2f, segments[0][2], 1e-6f);
        assertEquals(0f, segments[0][3], 1e-6f);
    }

    @Test
    void zeroLengthReturnsEmptyArray() {
        float[][] segments = LineGeometry.buildDashedSegments(5, 5, 5, 5, 2, 1);
        assertEquals(0, segments.length);
    }

    @Test
    void verticalLineCorrectSegments() {
        float[][] segments = LineGeometry.buildDashedSegments(0, 0, 0, 6, 2, 1);
        assertEquals(2, segments.length);
        // 第一段: (0,0) -> (0,2)
        assertEquals(0f, segments[0][0], 1e-6f);
        assertEquals(0f, segments[0][1], 1e-6f);
        assertEquals(0f, segments[0][2], 1e-6f);
        assertEquals(2f, segments[0][3], 1e-6f);
        // 第二段: (0,3) -> (0,5)
        assertEquals(0f, segments[1][0], 1e-6f);
        assertEquals(3f, segments[1][1], 1e-6f);
        assertEquals(0f, segments[1][2], 1e-6f);
        assertEquals(5f, segments[1][3], 1e-6f);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.cadengine.LineGeometryDashedTest"`
预期：FAIL（`buildDashedSegments` 方法不存在）

- [ ] **Step 3: 实现 LineGeometry.buildDashedSegments**

在 `LineGeometry.java` 末尾添加：

```java
    /**
     * 将线段按 dash/gap 模式分段，用于虚线渲染。
     *
     * @param x1      起点 x
     * @param y1      起点 y
     * @param x2      终点 x
     * @param y2      终点 y
     * @param dashLen 每段实线长度（像素）
     * @param gapLen  每段间隔长度（像素）
     * @return 分段数组，每段 [x1, y1, x2, y2]；无线段时返回空数组
     */
    public static float[][] buildDashedSegments(
            float x1, float y1, float x2, float y2,
            float dashLen, float gapLen) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float totalLen = (float) Math.sqrt(dx * dx + dy * dy);
        if (totalLen < 1e-6f || dashLen <= 0) {
            return new float[0][];
        }

        float unitX = dx / totalLen;
        float unitY = dy / totalLen;
        float cycleLen = dashLen + gapLen;

        java.util.List<float[]> segments = new java.util.ArrayList<>();
        float pos = 0;
        while (pos < totalLen) {
            float dashEnd = Math.min(pos + dashLen, totalLen);
            segments.add(new float[]{
                x1 + unitX * pos, y1 + unitY * pos,
                x1 + unitX * dashEnd, y1 + unitY * dashEnd
            });
            pos += cycleLen;
        }
        return segments.toArray(new float[0][]);
    }
```

- [ ] **Step 4: 运行测试验证通过**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.cadengine.LineGeometryDashedTest"`
预期：3 个测试 PASS

- [ ] **Step 5: 在 CADRenderEngine 添加 dashed drawLine 重载**

在 `CADRenderEngine.java` 的 `drawLine` 方法之后添加：

```java
    /**
     * 绘制虚线段（三角形展开 + dash/gap 分段）。
     *
     * @param dashed   是否虚线
     * @param dashLen  实线段长度（像素），默认 4
     * @param gapLen   间隔长度（像素），默认 2
     */
    public void drawLine(float x1, float y1, float x2, float y2,
                         float widthPx, int color, boolean dashed,
                         float dashLen, float gapLen) {
        if (!inFrame) return;
        if (!dashed) {
            drawLine(x1, y1, x2, y2, widthPx, color);
            return;
        }
        float[][] segments = LineGeometry.buildDashedSegments(x1, y1, x2, y2, dashLen, gapLen);
        for (float[] seg : segments) {
            drawLine(seg[0], seg[1], seg[2], seg[3], widthPx, color);
        }
    }

    /**
     * 绘制虚线段（使用默认 dash=4, gap=2）。
     */
    public void drawLine(float x1, float y1, float x2, float y2,
                         float widthPx, int color, boolean dashed) {
        drawLine(x1, y1, x2, y2, widthPx, color, dashed, 4f, 2f);
    }
```

- [ ] **Step 6: 在 WorldTreeReadOverlay 中传递 dashed 参数**

在 `WorldTreeReadOverlay.java` 中，找到轨道层渲染中调用 `engine.drawLine(...)` 的地方，将：

```java
engine.drawLine(startX, startY, endX, endY, widthPx, trackColorScaled);
```

替换为：

```java
engine.drawLine(startX, startY, endX, endY, widthPx, trackColorScaled, activeDashed);
```

> **注意**：需要确认 `WorldTreeReadOverlay` 中 `activeDashed` 字段已存在（P1.0 Phase A Task A5 已添加）。
> 如果 `drawBezier` 也需要支持 dashed，类似地在 `drawBezier` 中按 tessellated 段分段。

- [ ] **Step 7: 更新 ProviderConfigControl 移除 (P1.1) 标注**

在 `ProviderConfigControl.java` 中，将 `dashed=%s(P1.1)` 中的 `(P1.1)` 后缀移除（dashed 现在已生效）。
找到所有 `(P1.1)` 出现的地方并移除。

- [ ] **Step 8: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 9: 运行全部测试**

运行：`gradlew test`
预期：全部测试 PASS（含新增 3 个 dashed 测试）

- [ ] **Step 10: 完整构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 11: 提交**

```bash
git add -A
git commit -m "feat: implement dashed line rendering in CADRenderEngine + LineGeometry.buildDashedSegments"
```

---

## P0.5 + Phase B + P1.1 验收清单

### P0.5 验收

- [ ] JM 全屏地图打开时，`/kp debug overlay-anchors` 输出 JM 相机参数
- [ ] JM 地图上叠加层跟随平移/缩放
- [ ] JM 未安装时无崩溃，`/kp provider list` 显示 journeymap 但 isMapOpen=false
- [ ] `/kp provider list` 显示熔断状态 `[FUSED]`（若 provider 异常）
- [ ] `/kp provider reset-circuit` 清除熔断状态
- [ ] `/kp provider reset-circuit xaeroworldmap` 清除指定 provider 熔断
- [ ] 熔断后 200 tick（10 秒）自动重试

### Phase B 验收

- [ ] Xaero 全屏地图右上角显示齿轮按钮
- [ ] 鼠标悬停齿轮按钮显示 tooltip "Kinetic Planner"
- [ ] 点击齿轮按钮弹出半透明配置面板
- [ ] 配置面板顶部显示 provider Tab（xaeroworldmap / journeymap）
- [ ] 切换 Tab 显示对应 provider 参数
- [ ] 点击 enabled toggle 实时切换 provider 启用状态
- [ ] 点击 dashed toggle 实时切换虚线渲染
- [ ] 点击 hideCreateTrackMap toggle 实时切换
- [ ] 再次点击齿轮按钮关闭面板
- [ ] JM 全屏地图工具栏有 "KP" 按钮
- [ ] 点击 JM "KP" 按钮弹出配置面板
- [ ] JM 配置面板操作与 Xaero 一致

### P1.1 验收

- [ ] `/kp provider set xaeroworldmap dashed true` 后轨道线变为虚线
- [ ] `/kp provider set xaeroworldmap dashed false` 后恢复实线
- [ ] 虚线在缩放时保持视觉效果
- [ ] `/kp provider list` 中 dashed 参数不再标注 `(P1.1)`

---

## Self-Review

**1. Spec coverage:**
- P0.5 JM 适配器 -> Task 1 (Plugin) + Task 2 (Provider) ✓
- P0.5 熔断器 -> Task 3 ✓
- P0.5 adapter 命令 -> Task 4 (合并到 provider 域) ✓
- P1 Phase B 齿轮按钮 -> Task 5 (Widget) ✓
- P1 Phase B 配置面板 -> Task 6 (Screen) ✓
- P1 Phase B Xaero 注入 -> Task 7 (Mixin) ✓
- P1 Phase B JM 注入 -> Task 8 (Event) ✓
- P1.1 dashed -> Task 9 ✓

**2. Task 依赖链（无循环）：**
- T1 (JM Plugin) -> 无依赖
- T2 (JM Provider) -> 依赖 T1
- T3 (熔断器) -> 无依赖
- T4 (命令) -> 依赖 T3
- T5 (GearButton) -> 无依赖
- T6 (ConfigScreen) -> 依赖 T5
- T7 (Xaero Mixin) -> 依赖 T5, T6
- T8 (JM Event) -> 依赖 T1, T5, T6
- T9 (dashed) -> 依赖 T6（dashed toggle）

**3. Type consistency:**
- `KineticPlannerJMPlugin.getApi()` : `@Nullable IClientAPI` -> T2/T8 引用 ✓
- `MapOverlayDispatcher.resetCircuitBreaker(@Nullable String)` / `isCircuitBroken(String)` / `getFailedProviders()` : `Set<String>` -> T4 引用 ✓
- `MapGearButtonWidget(int, int, Runnable)` + `render(GuiGraphics, int, int)` + `mouseClicked(double, double, int) : boolean` -> T6/T7/T8 引用 ✓
- `ProviderConfigScreen(MapGearButtonWidget)` + `renderPanel(GuiGraphics, int, int, float)` + `handleMouseClick(double, double, int) : boolean` + `toggle()` / `setVisible(boolean)` / `isVisible()` -> T7/T8 引用 ✓
- `LineGeometry.buildDashedSegments(float, float, float, float, float, float) : float[][]` -> T9 CADRenderEngine 引用 ✓
- `CADRenderEngine.drawLine(float×4, float, int, boolean)` / `drawLine(float×4, float, int, boolean, float, float)` -> T9 WorldTreeReadOverlay 引用 ✓

**4. 已知限制与运行时验证项：**
- Xaero `GuiMap.mouseClicked` 方法签名需运行时验证（Mixin defaultRequire:0 容错）
- JM `ThemeButtonDisplay.addThemeButton` 的 icon ResourceLocation 如果无实际纹理文件，按钮可能显示为空白
- JM `FullscreenMapEvent.ClickEvent.setCanceled()` 需验证是否可取消 PRE 阶段事件
- `MapGearButtonWidget` 齿轮图标是简化自绘（8 条 fill 线段），视觉效果需运行时确认
- `ProviderConfigScreen` 的 toggle 点击区域计算基于固定行高，若字体大小变化可能需要调整
- JM `UIState.blockSize` 与 `blocksPerPixel` 的倒数关系需运行时确认

**5. 与命令树设计规格的关系:**
- adapter 域 4 条命令合并为 provider 域的 `reset-circuit` 1 条命令 -> 偏离规格 §5.3，需更新规格
- `/kp provider list` 输出增加 `[FUSED]` 标记 -> 规格未预定义，属扩展
- Phase B UI 在规格中未详细定义（仅 plan 中有大纲）-> 本 plan 细化

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-25-kinetic-planner-p0.5-phaseb-p1.1.md`.

**推荐执行顺序：**
1. **Track A 串行**：T1 -> T2（JM 适配器，2 个 Task）
2. **Track B 串行**：T3 -> T4（熔断器+命令，2 个 Task）
3. **Track C 串行**：T5 -> T6 -> T7（Xaero UI，3 个 Task）
4. **T8**（JM UI，依赖 Track A + Track C）
5. **T9**（dashed，依赖 Track C）
6. **统一运行时验收**：`gradlew runClient` 验证全部验收清单

Track A/B/C 可并行启动。

**两种执行方式：**
1. **Subagent-Driven（推荐）** - 每 Task 派一个新 subagent，task 间 review
2. **Inline Execution** - 当前会话用 executing-plans，批量执行 + checkpoint

哪种方式？
