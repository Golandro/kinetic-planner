# GuiMap.render() 反编译分析报告 — 方案 B 可行性核实

> 任务：Task 3.1（Phase 3 研究）
> 日期：2026-07-28
> 方法：`javap -c -p -l`（Zulu 21.0.11），未使用 IDE FernFlower
> 目标类：`xaero.map.gui.GuiMap`（Xaero's World Map，curse.maven artifact `xaeros-world-map-317780:7401095`）

## 1. Jar 定位

| 依赖 | 文件路径 |
|---|---|
| Xaero's World Map | `H:\.cache\gradle\caches\modules-2\files-2.1\curse.maven\xaeros-world-map-317780\7401095\aeabbcb21eb5c143802687323c341d1895b6123d\xaeros-world-map-317780-7401095.jar` |
| XaeroLib（父类 ScreenBase） | `H:\.cache\gradle\caches\modules-2\files-2.1\xaero.lib\xaerolib-neoforge-1.21\1.0.42\60115b1ccff9e1639e1808b37fe4b50bc7f604ee\xaerolib-neoforge-1.21-1.0.42.jar` |

环境变量 `GRADLE_USER_HOME=H:\.cache\gradle`（系统已配置，未额外覆盖）。
javap：`D:\Program Files\Java\zulu-21\bin\javap.exe`。

## 2. GuiMap 类结构

`GuiMap extends ScreenBase implements IRightClickableElement`。

### 与 render 相关的方法清单

| 方法签名（GuiMap） | 可见性 | 说明 |
|---|---|---|
| `render(GuiGraphics, int, int, float)` | public | **主渲染方法，单体巨型** |
| `renderBackground(GuiGraphics, int, int, float)` | public | **空实现**（`0: return`） |
| `renderPreDropdown(GuiGraphics, int, int, float)` | protected | 覆盖 ScreenBase；处理菜单打开后的 post-map-render |
| `hoveredElementTooltipHelper(...)` | private | Tooltip 构造 |
| `renderLoadingScreen(GuiGraphics)` | private | 加载中画面 |
| `renderMessageScreen(GuiGraphics, String)` | private | 消息画面（1 参） |
| `renderMessageScreen(GuiGraphics, String, String)` | private | 消息画面（2 参） |
| `drawArrowOnMap / drawFarArrowOnMap / drawDotOnMap / drawObjectOnMap` | public | 单体箭头/点绘制（在 render() 内被调用） |
| `renderTooltips(GuiGraphics, int, int, float)` | protected（继承自 ScreenBase） | Tooltip 渲染 |

### ScreenBase（父类）相关方法

ScreenBase 自身也声明了 `render`、`renderBackground`、`renderPreDropdown`、`renderTooltips`。
**`GuiMap.render()` 内通过 `invokespecial ScreenBase.render` 调用父类渲染**（渲染按钮/widgets，并以虚分派调用 `renderPreDropdown`）。

## 3. render() 方法分析

### 规模

- 字节码反汇编：**约 5902 行**（文件行 2119–8021）
- INVOKE 指令：**635 条**
- 内含 3 个 lambda（`lambda$render$11/12/13`），说明有显著内联逻辑

### render() 内部调用序列（按字节码偏移顺序）

| 阶段 | 偏移范围（约） | 内容 | 是否独立方法 |
|---|---|---|---|
| GL 初始化 | 0–500 | `flush`/`glGetError`/`_clearColor`/`ensureShaders`/`setShaderColor`；读取 config、mapProcessor、dimension | 内联 |
| 相机/缩放 | 500–3900 | `MapProcessor`/`MapWorld`/`MapDimension` 状态；`changeZoom`；`SlowingAnimation`；`GuiMapSwitching.preMapRender` | 内联 + 少量自方法 |
| **瓦片/区域渲染** | **3900–8800** | `MapProcessor.getLeafMapRegion` → `MapRegion.getChunk` → `MapTileChunk.getTile` → `MapTile.getBlock` → `LeveledRegion.getTexture`；`MapDimension.getLayeredMapRegions`；GL 纹理参数（`glTexParameteri`）、FBO、`TextureManager.bindForSetup` | **完全内联，无 `renderTiles()` 方法** |
| 玩家箭头 | 7300–8000 | `drawFarArrowOnMap` / `drawArrowOnMap`（自方法，可独立调用） | 公有方法 |
| HUD 文本 | 8100–8760 | 多次 `MapRenderHelper.drawCenteredStringWithBackground`（坐标/维度/高亮信息） | 内联静态调用 |
| 路标菜单 | 8870 | `SupportXaeroMinimap.renderWaypointsMenu(...)` | 委托静态类 |
| 雷达/玩家菜单 | 9044 | `PlayerTrackerMenuRenderer.renderMenu(...)` | 委托静态类 |
| 路标集合切换 | 9247 | `SupportXaeroMinimap.drawSetChange(...)` | 委托 |
| PAC 领地 | 9286 | `SupportOpenPartiesAndClaims.onMapRender(...)` | 委托 |
| 加载/消息画面 | 9299–9416 | `renderLoadingScreen` / `renderMessageScreen`（条件分支） | **独立 private 方法** |
| 维度切换文本 | 9438 | `GuiMapSwitching.renderText(...)` | 委托 |
| **super.render** | **9498** | `invokespecial ScreenBase.render` → 渲染按钮/widgets + 虚分派 `renderPreDropdown` | **父类方法** |
| 右键菜单 | 9517 | `GuiRightClickMenu.render(...)` | 委托 |
| Tooltip | 9545 | `renderTooltips(...)` + `hoveredElementTooltipHelper` + `Tooltip.drawBox` | 委托 + 自方法 |
| 消息框 | 9742–9765 | `MessageBoxRenderer.render(...)` | 委托 |
| 收尾 | 9828 | `MapRenderHelper.restoreDefaultShaderBlendState` | 静态调用 |

### renderPreDropdown 内容（独立分析）

`GuiMap.renderPreDropdown`（文件行 8035–8100，~65 行）：
1. `invokespecial ScreenBase.renderPreDropdown`（super 调用）
2. 若 `waypointMenu==true`：`WaypointMenuRenderer.postMapRender(...)`
3. 若 `playersMenu==true`：`PlayerTrackerMenuRenderer.postMapRender(...)`
4. `GuiMapSwitching.postMapRender(...)`

**renderPreDropdown 由 `ScreenBase.render` 调用**（不在 GuiMap.render 中直接调用），
即它处于 `super.render()` 调用链内。任何对 `super.render()` 的拦截都会连带影响 renderPreDropdown。

### renderBackground

`GuiMap.renderBackground` 为空（`0: return`），无实际渲染。

## 4. 各 UI 元素的可独立调用性

| 元素 | 是否独立方法 | 可直接调用？ | 可单独抑制？ |
|---|---|---|---|
| 地图瓦片 | ❌ 内联于 render() | **否** | **否**（无单一 INVOKE 可定位） |
| 玩家箭头 | ✅ `drawArrowOnMap` 等公有方法 | 是 | 是（@WrapOperation） |
| HUD 文本 | ❌ 内联静态调用 `MapRenderHelper.drawCenteredStringWithBackground` | 否（多次内联调用） | 部分（需 ordinal 区分多次调用） |
| 路标菜单 | ✅ `SupportXaeroMinimap.renderWaypointsMenu` | 是 | 是（@WrapOperation/@Redirect） |
| 雷达/玩家 | ✅ `PlayerTrackerMenuRenderer.renderMenu` | 是 | 是 |
| 加载/消息画面 | ✅ `renderLoadingScreen`/`renderMessageScreen`（private） | 否（private） | 是（@WrapOperation） |
| 按钮/控件 | ✅ `ScreenBase.render`（super） | 是 | 是，但**连带抑制 renderPreDropdown** |
| 右键菜单 | ✅ `GuiRightClickMenu.render` | 是 | 是 |
| Tooltip | ✅ `renderTooltips` | 是 | 是 |

## 5. 三方案可行性评估

### 方案 B（HEAD cancel + 手动调用子方法）— ❌ 不可行

- 瓦片渲染**完全内联**于 render()，无 `renderTiles()` 可调用。
- 在 HEAD `@Inject(cancellable=true)` 取消 render() 后，将**丢失全部瓦片渲染**——无法通过手动调用任何子方法恢复地图底图。
- 仅能手动恢复：路标菜单、雷达、按钮（super.render）、右键菜单、Tooltip 等叠加元素，但地图底图不可恢复。
- **结论：仅当 Phase 3 意图完全用 KP 自绘替代 Xaero 地图（不显示瓦片）时才适用**——这通常与"叠加层"目标矛盾。

### 方案 B'（多 @At("INVOKE") 抑制）— ✅ 可行（降级方案，推荐）

- 使用 **MixinExtras `@WrapOperation`**（NeoForge 1.21.1 已内含 MixinExtras，无需额外依赖）对特定 INVOKE 目标做条件跳过：
  - 抑制路标：wrap `SupportXaeroMinimap.renderWaypointsMenu`
  - 抑制雷达：wrap `PlayerTrackerMenuRenderer.renderMenu`
  - 抑制右键菜单：wrap `GuiRightClickMenu.render`
  - 抑制 HUD 文本：wrap `MapRenderHelper.drawCenteredStringWithBackground`（需 ordinal）
  - 抑制按钮：wrap `super.render` —— **注意连带抑制 renderPreDropdown**（路标/玩家 post-render 菜单）
- **无法抑制内联瓦片渲染**（无单一 INVOKE 可定位）。
- 注意：`@Inject(cancellable=true)` 在 `@At("INVOKE")` 会取消**整个剩余 render()**，而非仅跳过该子调用。要"跳过单一子调用"必须用 `@WrapOperation`/`@Redirect`，不能用 cancellable inject。
- **结论：适合"选择性隐藏叠加 UI 元素，保留瓦片底图"的场景。**

### 方案 C（@Overwrite）— ⚠️ 最后手段

- 可覆盖整个 render()，理论上可做任意修改。
- 但 render() 有 **5902 行字节码 / 635 条 INVOKE**，人工重建极易出错。
- **跨 Xaero 版本极度脆弱**（STATUS.md 已将 `GuiMap.render` Mixin 注入点列为高风险）。
- **结论：仅当方案 B' 无法满足需求（例如必须抑制瓦片渲染本身）时才考虑。**

## 6. 推荐策略

**推荐方案 B'（降级方案）**，理由：

1. 方案 B 因瓦片内联而不可行——这是本次核实的关键发现。
2. 方案 B' 能用 `@WrapOperation` 精准抑制路标/雷达/右键菜单/HUD 等委托式子调用，且不触碰瓦片渲染主循环，风险可控。
3. 方案 C 风险过高，与项目"Xaero 版本未锁、注入点高脆弱"的现状冲突。

**前提确认**：Phase 3 的目标是"选择性抑制 Xaero UI 元素以避免与 KP 叠加层/嵌入式 UI 冲突"，而非"移除瓦片底图"。若实际需要抑制瓦片本身，则需重新评估（方案 C 或不在 Xaero 屏幕内渲染）。

## 7. 注意事项与风险

- **MixinExtras 可用性**：NeoForge 1.21.1 内含 MixinExtras（`com.llamalad7.mixinextras`），`@WrapOperation` 可直接使用。项目当前未使用该注解（现有 Mixin 均为 `@Inject`/`@Accessor`），需在 `kinetic_planner.mixins.json` 配置中确认 `compatibilityLevel`（建议 `JAVA_21`）并确保 MixinExtras 注解处理器在编译期可用（NeoForge 默认提供）。
- **super.render 连带效应**：对 `ScreenBase.render` 的 `@WrapOperation` 会跳过 renderPreDropdown，可能影响路标/玩家菜单的 post-render。若需保留这些，应改为更细粒度（wrap renderPreDropdown 内的具体调用，但 renderPreDropdown 在父类调用链中，定位需 `@At("INVOKE")` 指向 `ScreenBase.render` 内部——较复杂）。替代方案：不动 super.render，而是在 renderPreDropdown 内用单独 Mixin 处理。
- **HUD 文本多调用**：`MapRenderHelper.drawCenteredStringWithBackground` 在 render() 内被调用 ~8 次（坐标、维度名、维度类型、高亮等），`@WrapOperation` 默认匹配全部，需用 `ordinal`/`slice` 精确靶向。
- **版本敏感性**：所有 INVOKE 目标（`SupportXaeroMinimap.renderWaypointsMenu` 等）签名依赖 Xaero 内部 API。STATUS.md 已将 Xaero 注入点列为高风险未锁版本。建议每个被 wrap 的目标都设置 `require = 0`（MixinExtras 的 `@WrapOperation` 也支持 `require`），并在日志中输出熔断标记。

## 8. 数据复现命令

```bash
# 反汇编 GuiMap（需两个 jar 在 classpath）
javap -c -p -l \
  -classpath "<xwm.jar>;<xlib.jar>" \
  xaero.map.gui.GuiMap > guimap-bytecode.txt

# 查看 ScreenBase 父类方法
javap -p -classpath "<xlib.jar>" xaero.lib.client.gui.ScreenBase
```
