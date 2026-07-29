---
description: 项目包结构参考
alwaysApply: true
enabled: true
updatedAt:
provider:
---
# 包结构

> 本文件由 AGENTS.md 拆分而来，提供完整的包结构参考。最后更新：2026-07-29。

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
│   ├── ProviderConfigBinding.java      provider 配置读写策略接口（common）
│   └── ProviderConfigRegistry.java     provider 默认配置注册表（common，内部用 KPRegistry）
├── projection/                         投影变换层（common，纯数学）
│   ├── Vec2d.java
│   ├── CameraParams.java
│   ├── WorldRect.java
│   └── WorldScreenTransform.java
├── registry/                           通用注册表框架（common，纯 JVM）
│   ├── KPId.java                       模组内部标识符 record（namespace:path）
│   └── KPRegistry.java                 带冻结生命周期的泛型注册表
├── cadengine/                          渲染引擎 + 主题
│   ├── Theme.java                      主题数据 record（common）
│   ├── ThemeSerializer.java            Gson JSON 序列化（common）
│   ├── LineGeometry.java               三角形带展开纯数学（common）
│   ├── BezierTessellator.java          贝塞尔采样纯数学（common）
│   ├── CADRenderEngine.java            Blaze3D 渲染封装（client）
│   └── GLStateGuard.java               RenderSystem 状态管理（client）
├── config/                             配置中介层（main: IKPConfig + client: KPConfig/控制类）
│   ├── IKPConfig.java                  配置中介接口（main，纯 Java 无 client 依赖）
│   ├── KPConfig.java                   NeoForge ModConfigSpec TOML，implements IKPConfig（内部用 BINDINGS map 替代 switch）
│   ├── ModConfigSpecConfigBinding.java 通用 ProviderConfigBinding 实现（封装 ModConfigSpec value holder）
│   ├── KpClientState.java              配置面板可见性 + 编辑模式全局状态
│   ├── OverlayControl.java             叠加层状态管理（依赖 IKPConfig）
│   ├── ProviderConfigControl.java      provider 配置 CRUD（依赖 IKPConfig）
│   └── ThemeManager.java               主题管理（扫描/加载/切换/重载，依赖 IKPConfig）
├── command/                            命令体系（main: 树定义 + client: 处理器实现）
│   ├── KpCommandHandlers.java          命令处理器接口（main，27 方法）
│   ├── KPCommandTree.java              命令树构建器（main，纯定义无 GUI 依赖）
│   └── KPClientCommands.java           处理器实现（client，implements KpCommandHandlers）
├── gui/                                GUI 层（client）
│   ├── config/                         配置面板 UI
│   │   ├── KpConfigUIFactory.java      LDLib2 面板组装（行构建委托 widgets）
│   │   ├── KpStylesheet.java           KP 紫 accent LSS 主题
│   │   ├── KpGearButton.java           自绘齿轮按钮（extends KpIconButton）
│   │   └── KPClothConfigScreen.java    Cloth Config GUI
│   ├── editor/                         编辑模式
│   │   ├── EditToolState.java          编辑会话状态（工具/选择集）
│   │   ├── KpEditorScreen.java         编辑模式 Screen 壳
│   │   ├── KpMapEditor.java            LDLib2 Editor 子类
│   │   ├── KpEditButton.java           自绘编辑按钮（extends KpIconButton）
│   │   └── EditorCommands.java         /kp edit /exit 命令处理器（GUI 依赖）
│   ├── event/                          事件基础设施
│   │   └── KpUIEventForwarder.java     ModularUIWidget 事件转发封装
│   ├── widgets/                        可复用 UI 组件
│   │   ├── KpIconButton.java           自绘图标按钮抽象基类
│   │   ├── ConfigToggleRow.java        配置面板 Toggle 行构建器
│   │   └── ConfigStepperRow.java       配置面板步进器行构建器 + clamp 纯函数
│   ├── KpRibbonBar.java                Editor Ribbon 栏
│   ├── MapPlaceholderView.java         Editor 中心区透明占位
│   └── ToolPanelView.java             Editor 左侧工具面板
├── mapadapter/                         地图适配层（client）
│   ├── MapOverlayProvider.java         接口（displayName/defaultConfig）
│   ├── MapOverlayContext.java          上下文 record
│   ├── MapOverlayDispatcher.java       分发 + 熔断 + priority 排序（initProviders 从工厂注册表懒初始化）
│   ├── MapProviderFactory.java         provider 工厂接口（modId/displayName/defaultConfig/isAvailable/create）
│   ├── MapProviderRegistry.java        客户端工厂注册表（基于 KPRegistry，client setup 冻结）
│   ├── XaeroMapProviderFactory.java    Xaero 工厂实现
│   ├── JourneyMapMapProviderFactory.java  JM 工厂实现
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
| main (common) | 20 | 纯 JVM，含 IKPConfig + 命令树定义 + registry 框架 |
| client | 52 | GUI/渲染/适配器/Mixin/命令处理器 |
| server | 0 | 占位（P1 编辑引擎填充） |
| test | 27 | 134 个 @Test（15 个 @Disabled） |
| **合计** | **99** | |

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
