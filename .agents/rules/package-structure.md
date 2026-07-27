---
description: 项目包结构参考
alwaysApply: true
enabled: true
updatedAt:
provider:
---
# 包结构

> 本文件由 AGENTS.md 拆分而来，提供完整的包结构参考。最后更新：2026-07-27。

## 三 sourceSet 架构

```
src/
├── main/java/          common（纯 JVM，不引用 client/blaze3d）
├── client/java/        client（GUI/渲染/适配器/Mixin/命令）
├── server/java/        server（P1 编辑引擎服务端实例，当前为空）
└── test/java/          test（可访问全部 sourceSet）
```

## 完整包结构

```
net.jsmua.kinetic_planner
├── KineticPlannerMod.java              @Mod 主类（common）
├── KineticPlannerClient.java           @Mod(dist=CLIENT) 客户端入口
├── data/                               数据访问层
│   ├── IRailwayDataAccess.java         只读接口（common）
│   ├── StubRailwayDataAccess.java      测试桩（common）
│   ├── RailwayDataAccess.java          生产实现（client）
│   ├── EdgeGeometry.java               边几何描述符（common）
│   ├── ProviderConfig.java             provider 配置 record（common）
│   └── ProviderConfigRegistry.java     provider 默认配置注册表（common）
├── projection/                         投影变换层（common，纯数学）
│   ├── Vec2d.java
│   ├── CameraParams.java
│   ├── WorldRect.java
│   └── WorldScreenTransform.java
├── cadengine/                          渲染引擎 + 主题
│   ├── Theme.java                      主题数据 record（common）
│   ├── ThemeSerializer.java            Gson JSON 序列化（common）
│   ├── LineGeometry.java               三角形带展开纯数学（common）
│   ├── BezierTessellator.java          贝塞尔采样纯数学（common）
│   ├── CADRenderEngine.java            Blaze3D 渲染封装（client）
│   └── GLStateGuard.java               RenderSystem 状态管理（client）
├── config/                             配置/命令/GUI（client）
│   ├── KPConfig.java                   NeoForge ModConfigSpec TOML
│   ├── KPCommands.java                 /kp 命令注册（~22 节点）
│   ├── KPClothConfigScreen.java        Cloth Config GUI
│   ├── OverlayControl.java             叠加层状态管理
│   ├── ThemeManager.java               主题管理（扫描/加载/切换/重载）
│   ├── ProviderConfigControl.java      provider 配置 CRUD
│   ├── KpClientState.java              配置面板可见性共享状态（Phase B）
│   ├── KpConfigUIFactory.java          LDLib2 UIElement 树构建 + 步进器纯函数（Phase B）
│   ├── KpStylesheet.java               KP 紫 accent LSS 主题（Phase B）
│   ├── KpUIEventForwarder.java         ModularUIWidget 事件转发封装（Phase B）
│   └── KpGearButton.java               自绘齿轮按钮（Phase B，替代 MapGearButtonWidget）
├── mapadapter/                         地图适配层（client）
│   ├── MapOverlayProvider.java         接口（displayName/defaultConfig）
│   ├── MapOverlayContext.java          上下文 record
│   ├── MapOverlayDispatcher.java       分发 + 熔断 + priority 排序
│   ├── XaeroMapOverlayProvider.java    Xaero 实现
│   ├── JourneyMapOverlayProvider.java  JM 实现（isMapOpen/captureContext）
│   └── KineticPlannerJMPlugin.java     JM 客户端插件入口（@JourneyMapPlugin）
├── instrument/                         叠加层编排（client）
│   ├── WorldTreeReadOverlay.java       顶层编排器
│   ├── GeometryCache.java              几何缓存 + 脏检测
│   └── EdgePointColorResolver.java     边点颜色委托
├── compat/                             兼容层（client）
│   └── create/KPIntegration.java       Create 集成入口
└── mixin/                              Mixin（client）
    ├── TrackGraphAccessor.java             @Accessor connectionsByNode
    ├── XaeroMapAccessor.java               @Accessor GuiMap 字段
    ├── XaeroMapRenderHook.java             @Inject GuiMap.render（叠加层渲染）
    ├── XaeroMapGearButtonMixin.java        @Inject GuiMap.render + mouseClicked（齿轮按钮 + 配置面板，Phase B）
    ├── CreateTrackVisualizerHiderMixin.java  隐藏 Create 信号边组叠加层
    ├── CreateTrainMapMixin.java            Create Train Map 集成
    └── CreateTrainMapOverlayMixin.java     Create Train Map 叠加层
```

## 代码统计

| sourceSet | Java 文件 | 说明 |
|---|---|---|
| main (common) | 14 | 纯 JVM，不引用 client/blaze3d，可单测 |
| client | 32 | GUI/渲染/适配器/Mixin/命令 |
| server | 0 | 占位（P1 编辑引擎填充） |
| test | 16 | 60 个 @Test（2 个 @Disabled） |
| **合计** | **62** | |

## Mixin 清单

| Mixin | 目标类 | 类型 | 说明 |
|---|---|---|---|
| `TrackGraphAccessor` | `TrackGraph` (Create) | `@Accessor` | 访问 package-private `connectionsByNode` |
| `XaeroMapAccessor` | `GuiMap` (Xaero) | `@Accessor` | 访问相机字段 |
| `XaeroMapRenderHook` | `GuiMap` (Xaero) | `@Inject` | `render` RETURN 注入叠加层渲染 |
| `XaeroMapGearButtonMixin` | `GuiMap` (Xaero) | `@Inject` | `render` RETURN + `mouseClicked` HEAD 注入齿轮按钮 + LDLib2 配置面板（Phase B） |
| `CreateTrackVisualizerHiderMixin` | `TrackGraphVisualizer` (Create) | `@Inject` | 隐藏 `visualiseSignalEdgeGroups` |
| `CreateTrainMapMixin` | Create Train Map 类 | `@Inject` | Create Train Map 集成 |
| `CreateTrainMapOverlayMixin` | Create Train Map 类 | `@Inject` | Create Train Map 叠加层 |

所有 Mixin 使用 `remap = false`（目标类属于 Create/Xaero，非 MC 原生类）。Mixin 配置 `defaultRequire: 0`。
