# Kinetic Planner

Minecraft 1.21.1 NeoForge 模组，在全屏地图（Xaero's World Map / JourneyMap）上以 CAD 思路叠加显示机械动力（Create）铁路拓扑。使用 Blaze3D 三角形带做矢量渲染。

## 依赖

| 依赖 | 版本 | 类型 |
|---|---|---|
| Minecraft | 1.21.1 | 锁定 |
| NeoForge | 21.1.235 | required |
| Java | 21 (Zulu 21.0.11) | toolchain |
| Create | 6.0.10+ | required |
| Xaero's World Map | 1.0+ | optional（Mixin 注入目标） |
| JourneyMap | 1.21.1-6.0.0-alpha+ | optional（占位，Mixin 推迟） |
| Cloth Config | 15.0.140 | 打包（Cloth Config GUI） |

> Ponder / Flywheel / Registrate 由 Create 传递依赖。开发环境额外包含 Xaero's Minimap、Modern UI、Create: Steam 'n' Rails 用于兼容性验证。

## 构建

```bash
# 编译（common + client + server 三个 sourceSet）
gradlew compileJava compileClientJava compileServerJava

# 运行测试（42 个 @Test，2 个 @Disabled）
gradlew test

# 完整构建（编译 + 测试 + jar）
gradlew build

# 启动游戏客户端
gradlew runClient
```

- Gradle wrapper 9.6.1，Java 21
- 优先使用 `GRADLE_USER_HOME` 作为 Gradle 用户目录
- Shell 为 `cmd.exe` 时多命令用 `&` 或 `&&` 分隔

## 架构

三 sourceSet 分离：

```
src/
├── main/java/          common（纯 JVM，不引用 client/blaze3d）
├── client/java/        client（GUI/渲染/适配器/Mixin/命令）
├── server/java/        server（P1 编辑引擎服务端实例，当前为空）
└── test/java/          test（可访问全部 sourceSet）
```

### 包结构

```
net.jsmua.kinetic_planner
├── KineticPlannerMod.java          @Mod 主类（common）
├── KineticPlannerClient.java       @Mod(dist=CLIENT) 客户端入口
├── data/                           数据访问层
│   ├── IRailwayDataAccess.java     只读接口（common）
│   ├── StubRailwayDataAccess.java  测试桩（common）
│   ├── RailwayDataAccess.java      生产实现（client）
│   ├── EdgeGeometry.java           边几何描述符（common）
│   ├── ProviderConfig.java         provider 配置 record（common）
│   └── ProviderConfigRegistry.java provider 默认配置注册表（common）
├── projection/                     投影变换层（common，纯数学）
│   ├── Vec2d.java
│   ├── CameraParams.java
│   ├── WorldRect.java
│   └── WorldScreenTransform.java
├── cadengine/                      渲染引擎 + 主题
│   ├── Theme.java                  主题数据 record（common）
│   ├── ThemeSerializer.java        Gson JSON 序列化（common）
│   ├── LineGeometry.java           三角形带展开纯数学（common）
│   ├── BezierTessellator.java      贝塞尔采样纯数学（common）
│   ├── CADRenderEngine.java        Blaze3D 渲染封装（client）
│   └── GLStateGuard.java           RenderSystem 状态管理（client）
├── config/                         配置/命令/GUI（client）
│   ├── KPConfig.java               NeoForge ModConfigSpec TOML
│   ├── KPCommands.java             /kp 命令注册（~22 节点）
│   ├── KPClothConfigScreen.java    Cloth Config GUI
│   ├── OverlayControl.java         叠加层状态管理
│   ├── ThemeManager.java           主题管理（扫描/加载/切换/重载）
│   └── ProviderConfigControl.java  provider 配置 CRUD
├── mapadapter/                     地图适配层（client）
│   ├── MapOverlayProvider.java     接口
│   ├── MapOverlayContext.java      上下文 record
│   ├── MapOverlayDispatcher.java   分发 + 熔断 + priority 排序
│   ├── XaeroMapOverlayProvider.java Xaero 实现
│   └── JourneyMapOverlayProvider.java 占位
├── instrument/                     叠加层编排（client）
│   ├── WorldTreeReadOverlay.java   顶层编排器
│   ├── GeometryCache.java          几何缓存 + 脏检测
│   └── EdgePointColorResolver.java 边点颜色委托
└── mixin/                          Mixin（client）
    ├── TrackGraphAccessor.java     @Accessor connectionsByNode
    ├── XaeroMapAccessor.java       @Accessor GuiMap 字段
    ├── XaeroMapRenderHook.java     @Inject GuiMap.render
    └── CreateTrackVisualizerHiderMixin.java  隐藏 Create 信号边组叠加层
```

## /kp 命令体系

```
/kp                              -- 综合概览
/kp overlay toggle               -- 切换叠加开关
/kp overlay enable               -- 显式开启
/kp overlay disable              -- 显式关闭
/kp overlay reload               -- 重载配置 + 主题
/kp overlay status               -- 查看状态
/kp overlay hide-create [bool]   -- 切换/设置隐藏 Create 信号边组叠加层
/kp theme list                   -- 列出可用主题
/kp theme set <name>             -- 切换到指定主题
/kp theme reload                 -- 从磁盘重载当前主题
/kp theme reset                  -- 重置为默认主题
/kp provider list                -- 列出所有 provider 及状态
/kp provider enable <modId>      -- 启用某 provider
/kp provider disable <modId>     -- 禁用某 provider
/kp provider set <modId> <p> <v> -- 设置 provider 参数
/kp provider get <modId> [param] -- 查询 provider 配置
/kp provider reset <modId>       -- 重置 provider 为默认
/kp debug stats                  -- 渲染统计
/kp debug dump                   -- 转储 TrackGraph 数据到日志
/kp debug layer-count            -- 各图层对象计数
/kp debug overlay-anchors        -- 叠加层锚点诊断
```

## 当前状态

Phase 0a + 0b + P0 重构 + P1.0（Phase A）代码完成，42 个测试通过（2 个 @Disabled），待运行时验收。

详见 [STATUS.md](STATUS.md)。

## 许可证

MIT License, Copyright (c) 2026 Golandro。详见 [LICENSE](LICENSE)。
