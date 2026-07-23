# Kinetic Planner - Brigadier 命令树设计规格（P0-P3）

**日期：** 2026-07-23
**模组：** Kinetic Planner（`kinetic_planner`）
**范围：** Phase 0 至 Phase 3 的完整 `/kp` 命令树编排、参数化方案、权限模型
**前置：** Phase 0b 代码实现完成（Task 1-10），待运行时验收（Task 11）
**关系：** 本规格替代 Phase 0 spec §4.8 和 Phase 0b spec §3.4 中的命令定义部分。原 5 条命令作为 P0 子集被本规格涵盖。

---

## 1. 设计决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| 命令注册策略 | 客户端命令为主，服务端命令为辅（OP） | 编辑引擎和 UI 全在客户端；物理服务端仅有引擎后端实例供 OP 调用；内置服务端不需要独立服务端引擎实例 |
| 交互模型 | CLI = 原子操作（坐标优先，WE/FAWE pos 回退），UI = 交互式 CAD | 命令具有原子性，每条执行完成一个完整操作；UI 层独立处理拾取/绘制/捕捉 |
| 版本控制模型 | Git 分支 + SVN 提交校验 | 规划分支 Git 式管理；提交时总是与实时 Create TrackGraph 做分层校验 |
| 命令树组织 | 功能域命名空间 + 高频别名 | 域边界 = 编辑引擎子系统边界 = API 模块边界 |
| 位置参数 | 统一 PositionSpec 解析器 | 支持坐标/关键字/节点引用/选择器/内联表达式 |
| 编辑原语 | extend（延伸）+ link（连线） | 对应 Create 轨道放置的两种基本模式 |

---

## 2. 架构总览

### 2.1 三组 sourceSet 架构

项目分为三个 sourceSet，对应三种运行角色：

```
┌─ sourceSet 架构 ────────────────────────────────────────────────┐
│                                                                 │
│  ┌─ main（共用库 + 编辑引擎）─────────────────────────────────┐ │
│  │  无头设计：不引用 net.minecraft.client.* / blaze3d.*       │ │
│  │  ┌─ 编辑引擎 ──────────────────────────────────────────┐  │ │
│  │  │ EditEngine / StagingTree / BranchManager            │  │ │
│  │  │ VersionControl / PositionResolver(解析) / SnapConfig │  │ │
│  │  │ OverlayControl(逻辑) / ThemeManager(逻辑)            │  │ │
│  │  └─────────────────────────────────────────────────────┘  │ │
│  │  ┌─ 共用库 ────────────────────────────────────────────┐  │ │
│  │  │ IRailwayDataAccess / EdgeGeometry / Vec2d            │  │ │
│  │  │ WorldScreenTransform / LineGeometry / BezierTessellator│  │ │
│  │  │ Theme / ThemeSerializer                              │  │ │
│  │  └─────────────────────────────────────────────────────┘  │ │
│  │  纯 JVM 可单测（JUnit 5）                                  │ │
│  └───────────────────────────────────────────────────────────┘ │
│                          ↑ 可引用                                │
│  ┌─ server（逻辑服务端）──────────┐ ┌─ client（GUI 工具）─────┐ │
│  │ 中心化功能                      │ │ UI 层                   │ │
│  │ - 编辑指令同步                  │ │ - CADRenderEngine       │ │
│  │ - 生存模式 QoL                  │ │ - WorldTreeReadOverlay  │ │
│  │ - 世界状态校验                  │ │ - MapAdapter (Xaero/JM) │ │
│  │ - server 域命令注册 (OP)        │ │ - Cloth Config GUI      │ │
│  │ - PositionResolver(服务端实现)  │ │ - client 域命令注册     │ │
│  │ - [Server] 用户编辑引擎逃生口   │ │ - PositionResolver(客户端实现)│ │
│  └────────────────────────────────┘ └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**运行时实例分布：**

```
┌─ 物理客户端 ──────────────────────────────────────────┐
│  client sourceSet:                                    │
│    UI 层 + 客户端命令注册 + 渲染 + 适配器              │
│  main sourceSet:                                      │
│    编辑引擎实例（有头：配合 UI 交互）                   │
│  网络层 ←→ 远程逻辑服务端（联机时）                     │
└───────────────────────────────────────────────────────┘

┌─ 物理服务端 ──────────────────────────────────────────┐
│  server sourceSet:                                    │
│    逻辑服务端：同步 / QoL / 世界状态校验               │
│    server 域命令注册（OP, CommandSourceStack）         │
│    PositionResolver 服务端实现（[Server] 用户上下文）   │
│  main sourceSet:                                      │
│    编辑引擎实例（无头逃生口：OP 可通过 server 域调用）   │
└───────────────────────────────────────────────────────┘

┌─ 内置服务端（单人）───────────────────────────────────┐
│  client sourceSet: UI + 客户端命令注册                 │
│  server sourceSet: 逻辑服务端（同进程）                │
│  main sourceSet: 编辑引擎（单实例，直接与逻辑服务端通信）│
│  不创建独立服务端引擎实例                               │
└───────────────────────────────────────────────────────┘
```

### 2.2 域命名空间与 sourceSet 归属

| 域 | 职责 | 引入阶段 | 命令逻辑(sourceSet) | 命令注册(sourceSet) |
|---|---|---|---|---|
| `overlay` | 叠加层控制 | P0 | main（逻辑） | client |
| `theme` | 主题管理 | P0 | main（逻辑） | client |
| `adapter` | 地图适配器管理 | P0.5 | client（依赖适配器） | client |
| `edit` | 编辑引擎原子操作 | P1 | main（引擎核心） | client + server |
| `staging` | 暂存树管理 | P1 | main（引擎核心） | client + server |
| `snap` | 捕捉设置 | P1 | main（逻辑） | client |
| `branch` | 分支管理 | P3 | main（引擎核心） | client + server |
| `vc` | 版本控制 | P3 | main（引擎核心） | client + server |
| `io` | 导入导出 | P4 预留 | main（引擎核心） | client + server |
| `debug` | 调试诊断 | P0 | main + client | client |
| `server` | 服务端中心化功能 | P1+ | server | server（OP） |

> `edit`/`staging`/`branch`/`vc`/`io` 域的命令逻辑在 `main`（编辑引擎），但命令注册在 `client`（ClientCommandSourceStack）和 `server`（CommandSourceStack, OP）两侧均有。物理客户端上仅 client 侧注册生效；物理服务端上仅 server 侧注册生效。

### 2.3 无头设计约束

`main` sourceSet 中的编辑引擎必须满足：

| 约束 | 说明 |
|---|---|
| 不引用 `net.minecraft.client.*` | 无 Minecraft 客户端类依赖 |
| 不引用 `com.mojang.blaze3d.*` | 无渲染类依赖 |
| 不引用 `net.neoforged.neoforge.client.*` | 无客户端事件依赖 |
| PositionResolver 解析与解析后分离 | 字符串 -> PositionSpec AST 在 main；PositionSpec -> Vec3 需运行时上下文，通过接口注入 |
| 世界交互通过接口 | 读写 Create TrackGraph 通过 `IRailwayDataAccess`（读）+ `IWorldEditAccess`（写，待定义）接口 |
| 纯 JVM 可单测 | JUnit 5，不依赖 MC 运行时 |

**PositionResolver 分层：**
- `main`：`PositionSpecParser`（纯解析，字符串 -> AST）+ `PositionSpec`（数据 record）+ `PositionResolver`（接口，AST -> Vec3）
- `client`：`ClientPositionResolver`（实现，注入玩家位置/准星/WE-FAWE 客户端 API）
- `server`：`ServerPositionResolver`（实现，注入 [Server] 用户位置/WE-FAWE 服务端 API）

---

## 3. PositionSpec 参数系统

### 3.1 设计目标

命令中的位置参数需要统一解析器，支持多种语义输入：
- 显式世界坐标（绝对/相对/局部）
- WE/FAWE 位置标记
- 玩家相关位置
- 已有节点引用
- 节点选择器（跨工作树过滤）
- Create 压缩坐标
- 内联表达式（基点 + 偏移）

### 3.2 语法规范

```
PositionSpec ::= SingleToken | CoordTriple | Expression

SingleToken  ::= Keyword | NodeRef | Selector | Compressed
Keyword      ::= 'pos1' | 'pos2' | 'here' | 'target'
NodeRef      ::= 'node:' Index | 'node:nearest' | 'node:' Name | 'node:graph:' GraphId ':' Index
Selector     ::= '@' Filter (',' Filter)*
Filter       ::= Key '=' Value
Compressed   ::= 'cnode:' Int ',' Int ',' Int

CoordTriple  ::= Component Component Component    (空格分隔，3 个 token)
Component    ::= Number | '~' Number? | '^' Number?

Expression   ::= Base '+' Offset | Base '-' Offset
Base         ::= SingleToken
Offset       ::= OffsetComponent ',' OffsetComponent ',' OffsetComponent    (逗号分隔，单 token)
OffsetComp   ::= Number | '~' Number? | '^' Number?
```

### 3.3 格式详解

#### 3.3.1 世界坐标

| 格式 | 示例 | 说明 |
|---|---|---|
| 绝对坐标 | `100 64 -200` | 3 个空格分隔的数字 |
| 相对坐标 | `~5 ~ ~-3` | `~` 前缀，相对命令基准点（CLI 中为玩家位置） |
| 局部坐标 | `^ ^5 ^` | `^` 前缀，相对玩家视线方向（右/上/前） |

> MC 原生 `Vec3Argument` 语法兼容。命令基准点：CLI 命令中 `~` 相对玩家位置；UI 操作中 `~` 相对地图光标位置。

#### 3.3.2 关键字

| 关键字 | 解析结果 | 说明 |
|---|---|---|
| `pos1` | WE/FAWE 第一选区点 | 需 WE/FAWE 客户端 API；不可用时回退服务端同步 |
| `pos2` | WE/FAWE 第二选区点 | 同上 |
| `here` | 玩家脚底坐标 | `Minecraft.getInstance().player.position()` |
| `target` | 准星瞄准方块坐标 | 射线检测，最大距离 64 格 |

#### 3.3.3 节点引用

| 格式 | 示例 | 说明 |
|---|---|---|
| `node:<index>` | `node:0` | 暂存树中索引为 0 的节点 |
| `node:nearest` | `node:nearest` | 距玩家最近的节点（全工作树搜索） |
| `node:<name>` | `node:CentralStation` | 关联到指定车站名的节点 |
| `node:graph:<gid>:<idx>` | `node:graph:abc123:3` | 指定图中指定索引的节点 |

> `node:nearest` 搜索范围：当前维度内的所有树（live + staging + 当前分支），返回最近的一个。

#### 3.3.4 节点选择器

跨工作树批量过滤，返回节点列表。用于 `list`/`remove` 等批量操作。

| 过滤键 | 值格式 | 示例 | 说明 |
|---|---|---|---|
| `dim` | 维度名 | `dim=overworld` | 按维度过滤 |
| `graph` | 图 ID | `graph=abc123` | 按 TrackGraph 过滤 |
| `type` | 节点类型 | `type=station` | station/signal/observer/junction |
| `radius` | 方块数 | `radius=50` | 距玩家的半径范围 |
| `tree` | 树标识 | `tree=staging` | live/staging/`<branch-name>`，默认 live |
| `name` | glob 模式 | `name=Central*` | 按车站名匹配 |
| `tag` | 标签名 | `tag=signal` | 按自定义标签过滤 |

**组合示例：**
```
@dim=overworld,type=station,radius=50
@tree=staging,name=Central*
@graph=abc123,type=signal
```

> 选择器返回多结果时，`remove` 等破坏性操作需二次确认（聊天栏点击确认）。

#### 3.3.5 Create 压缩坐标

| 格式 | 示例 | 说明 |
|---|---|---|
| `cnode:x,y,z` | `cnode:200,128,-400` | Create TrackNodeLocation 压缩格式 |

> 压缩公式：X/Z = `floor(round(x*2))`，Y = `floor(y)*2`。用于精确引用 Create 内部节点位置。

#### 3.3.6 内联表达式

基点 + 偏移量运算，结果为新的 Vec3。偏移量用逗号分隔（保持单 token）。

| 示例 | 等价含义 |
|---|---|
| `pos1+0,5,0` | pos1 上移 5 格 |
| `node:0+10,0,0` | node:0 向东偏移 10 格 |
| `here+~,5,~` | 玩家位置 Y+5（X/Z 用玩家相对偏移，实际为 0） |
| `node:nearest+^,^,^5` | 最近节点沿玩家视线前方 5 格 |

**运算规则：**
- `+`：基点坐标 + 偏移量（逐分量相加）
- `-`：基点坐标 - 偏移量
- 偏移量中**纯数字**：世界空间绝对增量（如 `5` = 该轴 +5 方块）
- 偏移量中 `~` 前缀：相对玩家位置的增量（`~5` = 玩家该轴坐标+5 的增量，`~` = 0 增量）
- 偏移量中 `^` 前缀：局部视线空间增量（`^5` = 沿前方 5 方块，`^` = 0 增量），自动转换为世界空间 delta 后相加

### 3.4 WE/FAWE 集成

| 场景 | 行为 |
|---|---|
| WE/FAWE 客户端模组已安装 | 直接从客户端 API 读取 pos1/pos2 |
| WE/FAWE 仅服务端 | 发送网络包请求服务端同步 pos1/pos2 |
| WE/FAWE 未安装 | `pos1`/`pos2` 解析失败，返回错误提示 |

**回退规则（坐标省略时）：**
- 单位置参数省略 -> 使用 `pos1`
- 双位置参数均省略 -> 第一个用 `pos1`，第二个用 `pos2`
- 三位置参数（如 extend 的 start/end/axis）-> `pos1`=start, `pos2`=end, axis 自动推断

### 3.5 解析优先级

PositionSpec 解析器按以下顺序尝试匹配：

1. **关键字** -- 精确匹配 `pos1`/`pos2`/`here`/`target`
2. **节点引用** -- 前缀 `node:`
3. **选择器** -- 前缀 `@`
4. **压缩坐标** -- 前缀 `cnode:`
5. **内联表达式** -- 包含 `+` 或 `-` 运算符且前半部分匹配 SingleToken
6. **世界坐标** -- MC 原生坐标语法（3 token，支持 `~`/`^`）

---

## 4. AxisSpec 参数

### 4.1 语法

```
AxisSpec ::= Cardinal | Vector | 'auto'
Cardinal ::= ('n'|'s'|'e'|'w' | 'ne'|'nw'|'se'|'sw'
             | 'north'|'south'|'east'|'west'
             | 'northeast'|'northwest'|'southeast'|'southwest')
             ['-' VerticalDir]
VerticalDir ::= 'up' | 'down'
Vector      ::= 'vec:' Number ',' Number ',' Number
```

### 4.2 解析

| 格式 | 示例 | 解析为 Vec3 方向 |
|---|---|---|
| 基本方位 | `north` | (0, 0, -1) |
| 对角方位 | `ne` | (0.707, 0, -0.707) |
| 带垂直 | `north-up` | (0, 1, -1) 归一化 |
| 向量 | `vec:0,1,0` | (0, 1, 0) |
| 自动 | `auto` | 由两端坐标差推断 |

> `auto`（默认）：根据 start->end 的水平方向推断轨道轴向。若两端 X 差值大则东西向，Z 差值大则南北向。

---

## 5. 完整命令树（P0-P3）

### 5.1 overlay 域 -- 叠加层控制（P0）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp overlay toggle` | -- | P0 | 切换叠加开关（渲染层必须实际检查此值） |
| `/kp overlay enable` | -- | P0 | 显式开启 |
| `/kp overlay disable` | -- | P0 | 显式关闭 |
| `/kp overlay reload` | -- | P0 | 重载 TOML 配置文件 + 重建 Theme |
| `/kp overlay status` | -- | P0 | 查看状态：开关/适配器/图数/节点数/边数 |

### 5.2 theme 域 -- 主题管理（P0）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp theme list` | -- | P0 | 扫描 `config/kineticplanner/themes/` 列出可用主题 |
| `/kp theme set` | `<name>` | P0 | 切换到指定主题 |
| `/kp theme reload` | -- | P0 | 从磁盘重新加载当前主题 JSON |
| `/kp theme reset` | -- | P0 | 重置为默认主题 |
| `/kp theme save` | `[name]` | P1 | 保存当前视觉参数为主题文件 |

### 5.3 adapter 域 -- 地图适配器（P0.5）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp adapter list` | -- | P0.5 | 列出已注册适配器及状态 |
| `/kp adapter switch` | `<name>` | P0.5 | 切换主适配器（xaero/journeymap） |
| `/kp adapter status` | -- | P0.5 | 当前适配器详细状态（含熔断器） |
| `/kp adapter reset` | -- | P0.5 | 重置熔断器，重新激活已熔断适配器 |

### 5.4 edit 域 -- 编辑引擎原子操作（P1）

#### 5.4.0 编辑模式

edit 域命令支持两种编辑模式，由 `--direct` 标志或配置项 `edit.mode` 控制：

| 模式 | 说明 | 适用场景 |
|---|---|---|
| **staging（默认）** | 编辑操作仅修改暂存树（规划草稿），不放置/移除实际方块 | 规划阶段，不改变现有世界 |
| **direct** | 编辑操作直接放置/移除 Create 轨道方块实体（有体积），同时记录变更到暂存树 | 直接 1:1 编辑，实时建造 |

- `extend`/`link` 在 staging 模式下：向暂存树图添加节点/边，不触碰世界方块
- `extend`/`link` 在 direct 模式下：调用 Create API 放置轨道方块 + 创建 TrackGraph 节点/边，并将变更记录推入暂存树 undo 栈
- `node remove` 在 direct 模式下：移除实际轨道方块，记录到 undo 栈
- 两种模式下，暂存树均可通过 `staging validate` 与实时世界树校验一致性

#### 5.4.1 extend -- 延伸轨道

```
/kp edit extend [start_pos] [end_pos] [axis] [-p new_start_pos new_axis]
```

| 参数 | 类型 | 省略回退 | 说明 |
|---|---|---|---|
| `start_pos` | PositionSpec | pos1 | 延伸起始节点位置（引用已有节点） |
| `end_pos` | PositionSpec | pos2 | 延伸终点位置（创建新节点） |
| `axis` | AxisSpec | auto | 终点轨道方向 |
| `-p new_start_pos` | PositionSpec | -- | 创建新起始节点（不使用已有节点） |
| `new_axis` | AxisSpec | auto | 新起始节点轨道方向 |

**语义：**
- 无 `-p`：从 `start_pos` 处的已有节点延伸轨道到 `end_pos`，创建新节点 + 边
- 有 `-p`：在 `new_start_pos` 创建新起始节点，再延伸到 `end_pos`，创建两个新节点 + 边
- 节点 = Create 轨道方块实体（有体积），放置操作由编辑引擎执行

#### 5.4.2 link -- 连线

```
/kp edit link [start_pos] [end_pos]
```

| 参数 | 类型 | 省略回退 | 说明 |
|---|---|---|---|
| `start_pos` | PositionSpec | pos1 | 已有节点位置 |
| `end_pos` | PositionSpec | pos2 | 已有节点位置 |

**语义：** 在两个已有节点之间创建边（BezierConnection）。不创建新节点。先放节点（extend 或手动），再 link。

#### 5.4.3 node -- 节点操作

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp edit node add` | `[pos]` | P1 | 在指定位置添加节点（放置轨道方块） |
| `/kp edit node remove` | `[pos]` 或 `@selector` | P1 | 移除节点（支持选择器批量） |
| `/kp edit node move` | `[from] [to]` | P1 | 移动节点位置 |
| `/kp edit node list` | `[@selector]` | P1 | 列出暂存树节点 |
| `/kp edit node info` | `[pos]` 或 `node:<ref>` | P1 | 查看节点详细信息 |

#### 5.4.4 edge -- 边操作

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp edit edge remove` | `[start_pos] [end_pos]` 或 `@selector` | P1 | 移除边 |
| `/kp edit edge list` | `[@selector]` | P1 | 列出暂存树边 |
| `/kp edit edge info` | `[start_pos] [end_pos]` | P1 | 查看边详细信息 |
| `/kp edit edge convert` | `<type> [start_pos] [end_pos] [params...]` | P2 | 转换边几何类型 |
| `/kp edit edge add-bezier` | `[p1 c1 c2 p2]` | P2 | 添加贝塞尔边（4 个 PositionSpec） |
| `/kp edit edge add-arc` | `[params...]` | P2 | 添加双圆弧边 |
| `/kp edit edge add-spline` | `[points...]` | P2 | 添加 B-spline 边 |

> `convert` 的 `<type>` 取值：`straight` | `bezier` | `arc` | `spline`，对应 `EdgeGeometry.Type` 枚举。

#### 5.4.5 edgepoint -- 边点操作

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp edit edgepoint add` | `<type> [pos]` | P1 | 添加边点（type: signal/station/observer） |
| `/kp edit edgepoint remove` | `<type> [pos]` 或 `@selector` | P1 | 移除边点 |
| `/kp edit edgepoint list` | `[@selector]` | P1 | 列出边点 |

#### 5.4.6 undo/redo

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp edit undo` | -- | P1 | 撤销上一步编辑（编辑引擎内部 undo 栈） |
| `/kp edit redo` | -- | P1 | 重做 |

### 5.5 staging 域 -- 暂存树（P1）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp staging status` | -- | P1 | 暂存区状态（节点数/边数/边点数/脏标记） |
| `/kp staging clear` | -- | P1 | 清空暂存区（直接丢弃，不提交） |
| `/kp staging validate` | -- | P1 | 校验暂存区与实时世界树的一致性 |
| `/kp staging show` | `[--layer <name>]` | P1 | 在地图上高亮显示暂存区内容 |

### 5.6 snap 域 -- 捕捉设置（P1）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp snap mode` | `[grid\|node\|edge\|none]` | P1 | 设置或查看捕捉模式 |
| `/kp snap toggle` | -- | P1 | 切换捕捉开关 |
| `/kp snap grid-size` | `[size]` | P1 | 设置网格捕捉间距（方块单位） |

### 5.7 branch 域 -- 分支管理（P3）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp branch list` | -- | P3 | 列出所有分支（标记当前分支） |
| `/kp branch create` | `<name> [from]` | P3 | 从指定分支创建（默认从当前分支） |
| `/kp branch switch` | `<name>` | P3 | 切换到指定分支 |
| `/kp branch delete` | `<name>` | P3 | 删除分支（需确认无未合并提交） |
| `/kp branch rename` | `<old> <new>` | P3 | 重命名分支 |
| `/kp branch merge` | `<name>` | P3 | 将指定分支合并到当前分支 |

### 5.8 vc 域 -- 版本控制（P3）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp vc commit` | `[message...]` | P3 | 提交暂存到当前分支（SVN式：校验实时世界树后记录） |
| `/kp vc log` | `[branch] [-n <limit>]` | P3 | 查看提交历史（默认当前分支，20条） |
| `/kp vc diff` | `[target]` | P3 | 差异对比（target: 分支名/commit hash/省略=vs实时世界树） |
| `/kp vc tag create` | `<name> [commit]` | P3 | 创建标签 |
| `/kp vc tag list` | -- | P3 | 列出标签 |
| `/kp vc tag delete` | `<name>` | P3 | 删除标签 |
| `/kp vc rollback` | `<commit>` | P3 | 回滚到指定提交（创建反向提交，不擦除历史） |
| `/kp vc cherry-pick` | `<commit>` | P3 | 将指定提交拣选到当前分支 |

**commit 语义：**
1. 冻结暂存区当前状态
2. 与实时 Create TrackGraph 做分层校验（SVN式）
3. 校验通过：记录为当前分支的新提交，生成 commit hash
4. 校验失败：拒绝提交，报告冲突项

**diff target 语义：**
- 省略：暂存区 vs 实时世界树
- `<branch-name>`：当前分支 vs 指定分支
- `<commit-hash>`：当前工作状态 vs 指定提交

### 5.9 io 域 -- 导入导出（P4 预留）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp io export` | `<format> [file]` | P4 | 导出当前分支/暂存到文件 |
| `/kp io import` | `<format> <file>` | P4 | 从文件导入到暂存区 |
| `/kp io formats` | -- | P4 | 列出支持的格式（nbt/blueprint/litematica/ifc） |

### 5.10 debug 域 -- 调试诊断（P0）

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp debug stats` | -- | P0 | 渲染统计（图数/节点数/边数/帧耗时） |
| `/kp debug dump` | -- | P0 | 转储当前维度 TrackGraph 数据到日志 |
| `/kp debug layer-count` | -- | P0 | 各图层对象计数 |
| `/kp debug overlay-anchors` | -- | P0 | 叠加层锚点诊断（中心十字线/边界） |
| `/kp debug pos-resolve` | `<PositionSpec>` | P1 | 解析 PositionSpec 并输出结果（调试用） |

### 5.11 server 域 -- 服务端中心化功能（P1+，OP 专用）

> server 域命令仅在 `server` sourceSet 中注册，需 OP 权限（等级 2+）。内置服务端不注册此域（客户端编辑引擎直接通信）。专用服务器上，server 域提供 [Server] 用户编辑引擎逃生口（无头运行，无 GUI）。

| 命令 | 参数 | 阶段 | 说明 |
|---|---|---|---|
| `/kp server status` | -- | P1 | 服务端引擎状态（[Server] 用户编辑引擎实例状态） |
| `/kp server validate` | `[dimension]` | P1 | 校验服务端世界树完整性 |
| `/kp server sync` | -- | P1 | 强制同步世界树到客户端 |
| `/kp server commit` | `<branch> <commit>` | P3 | 服务端层面确认规划提交 |
| `/kp server edit` | `<edit 子命令>` | P1 | [Server] 用户编辑逃生口（复用 edit 域逻辑，无头） |
| `/kp server config` | `<key> [value]` | P1 | 服务端引擎配置（同步策略/QoL 规则） |

> server 域命令仅在物理服务端注册，需 OP 权限（等级 2+）。内置服务端不注册此域（客户端直接通信）。

---

## 6. 别名表

高频操作的快捷方式，注册为 `/kp` 下的直接 literal 节点：

| 别名 | 等价命令 | 阶段 | 说明 |
|---|---|---|---|
| `/kp` | overlay status + staging status | P0 | 综合概览 |
| `/kp c` | vc commit | P3 | 提交 |
| `/kp b` | branch switch（无参数=branch list） | P3 | 切换分支 |
| `/kp l` | vc log | P3 | 查看历史 |
| `/kp d` | vc diff | P3 | 查看差异 |
| `/kp s` | staging status | P1 | 暂存状态 |
| `/kp an` | edit node add | P1 | 添加节点 |
| `/kp ae` | edit extend | P1 | 延伸轨道 |
| `/kp al` | edit link | P1 | 连线 |
| `/kp rn` | edit node remove | P1 | 移除节点 |
| `/kp re` | edit edge remove | P1 | 移除边 |
| `/kp u` | edit undo | P1 | 撤销 |

---

## 7. 参数 Brigadier 类型映射

### 7.1 标量参数

| 参数语义 | Brigadier 类型 | MC 1.21.1 API |
|---|---|---|
| 主题名/分支名 | `StringArgument.word()` | `StringArgumentType.word()` |
| 提交消息 | `StringArgument.greedyString()` | 消费剩余所有输入 |
| Commit hash | `StringArgument.word()` | 短 hash（8 位十六进制） |
| 数量限制 | `IntegerArgument.integer(1)` | `--limit` / `-n` 参数 |
| 网格大小 | `DoubleArgument.doubleArg(0.01)` | 正数 |
| 边点类型 | `StringArgument.word()` + 建议列表 | signal/station/observer |
| 几何类型 | `StringArgument.word()` + 建议列表 | straight/bezier/arc/spline |

### 7.2 复合参数

| 参数语义 | 实现方式 | 说明 |
|---|---|---|
| PositionSpec | 自定义 `ArgumentType<PositionSpec>` | 解析 1-3 个 token，支持关键字/坐标/表达式 |
| NodeSelector | 自定义 `ArgumentType<NodeSelector>` | 解析 `@key=val,...` 格式，单 token |
| AxisSpec | 自定义 `ArgumentType<AxisSpec>` | 解析方位词或 `vec:x,y,z`，单 token |

### 7.3 可选参数模式

Brigadier 中实现"省略坐标 -> 回退 pos1/pos2"的模式：

```java
// 无参数分支（使用 WE/FAWE pos1/pos2）
.executes(ctx -> extendWithDefaults())

// 有参数分支（显式 PositionSpec）
.then(Argument.positionSpec("start")
    .then(Argument.positionSpec("end")
        .then(Argument.axisSpec("axis")
            .executes(ctx -> extendWithCoords(...))
            // -p 子分支
            .then(Commands.literal("-p")
                .then(Argument.positionSpec("newStart")
                    .then(Argument.axisSpec("newAxis")
                        .executes(ctx -> extendWithNewStart(...))))))))
```

---

## 8. 权限模型

### 8.1 客户端命令（默认）

| 操作类别 | 权限要求 | 说明 |
|---|---|---|
| overlay/theme/adapter/debug | 无 | 客户端本地操作，无权限限制 |
| edit/staging/snap | 无（单人）/ 无（联机） | 编辑引擎在客户端运行 |
| branch/vc | 无 | 规划树版本控制在客户端 |

> 联机时，客户端编辑操作通过网络包同步到服务端。服务端可配置是否允许非 OP 玩家执行编辑（默认允许，服务器管理员可关闭）。

### 8.2 服务端命令（OP 专用）

| 操作类别 | 权限要求 | 说明 |
|---|---|---|
| `server status/validate/sync` | OP 等级 2+ | 诊断与同步 |
| `server commit` | OP 等级 2+ | 服务端层面确认规划提交 |
| `server config` | OP 等级 2+ | 服务端引擎配置 |

### 8.3 编辑同步策略

| 场景 | 策略 |
|---|---|
| 单人（内置服务端） | client sourceSet 的编辑引擎实例直接操作同进程逻辑服务端（server sourceSet），无网络开销 |
| 联机（物理客户端 -> 物理服务端） | 客户端编辑引擎（main sourceSet 实例）打包操作为网络包；服务端 `ServerEngineBackend`（server sourceSet）校验后应用；生存模式下受 QoL 规则约束 |
| 联机（OP 服务端逃生口） | OP 在服务端控制台执行 `server edit` 域命令，直接操作服务端编辑引擎实例（main sourceSet，[Server] 用户无头运行） |

---

## 9. 与现有代码的关系

### 9.1 P0 命令修复

当前 `KPCommands.java` 实现的 5 条命令需要修复，对齐本规格：

| 现有命令 | 问题 | 修复方案 |
|---|---|---|
| `/kp overlay toggle` | 渲染层不检查 `OVERLAY_ENABLED` | `WorldTreeReadOverlay.onMapRender` 入口加 `if (!KPConfig.OVERLAY_ENABLED.get()) return;` |
| `/kp overlay reload` | 只重建内存 Theme，不 reload 磁盘 | NeoForge config reload + ThemeSerializer 从 JSON 文件重载 |
| `/kp theme reload` | 与 overlay reload 完全相同 | 独立实现：仅从 JSON 文件重载当前主题 |
| `/kp theme list` | 硬编码 "default" | 扫描 `config/kineticplanner/themes/` 目录 |
| `/kp debug stats` | 无实际统计数据 | `WorldTreeReadOverlay` 暴露统计接口 |

### 9.2 sourceSet 重构与命令注册迁移

当前项目为两组 sourceSet（main=common, client=client）。需重构为三组：

| sourceSet | 路径 | 约束 | 当前内容迁移 |
|---|---|---|---|
| **main** | `src/main/java/` | 无头：不引用 client/blaze3d | 现有 common 类 + 从 client 迁入编辑引擎逻辑类 |
| **server** | `src/server/java/`（新增） | 可引用 main + 服务端 API | 新建：同步/QoL/校验 + server 域命令 + ServerPositionResolver |
| **client** | `src/client/java/` | 可引用 main + client API | 保留：渲染/UI/适配器 + client 域命令 + ClientPositionResolver |

**命令注册迁移：**

| 命令域 | 当前注册方式 | 迁移目标 |
|---|---|---|
| overlay/theme/debug | `RegisterCommandsEvent`（服务端事件，引用 client 类） | client sourceSet: `ClientCommandSourceStack` |
| adapter | 不存在 | client sourceSet: `ClientCommandSourceStack` |
| edit/staging/snap | 不存在 | client sourceSet: `ClientCommandSourceStack`；server sourceSet: `CommandSourceStack`（OP 逃生口） |
| branch/vc/io | 不存在 | 同 edit/staging |
| server | 不存在 | server sourceSet: `CommandSourceStack`（OP） |

> NeoForge 1.21.1 客户端命令 API 需运行时验证。若 `ClientCommandSourceStack` 不可用，回退到 `RegisterCommandsEvent` + `Dist.CLIENT` 守卫。

### 9.3 新增 API 模块

命令树驱动的编辑引擎 API 模块划分（按 sourceSet 归属）：

| API 模块 | 对应命令域 | sourceSet | 职责 |
|---|---|---|---|
| `OverlayControl` | overlay | main（逻辑） + client（渲染开关） | 叠加层开关状态/重载逻辑 |
| `ThemeManager` | theme | main | 主题加载/切换/保存/序列化 |
| `AdapterManager` | adapter | client | 适配器切换/熔断器管理（依赖客户端适配器） |
| `EditEngine` | edit | main | 节点/边/边点原子操作 + undo/redo（无头核心） |
| `StagingTree` | staging | main | 暂存区管理/校验（无头核心） |
| `SnapConfig` | snap | main | 捕捉模式/网格设置 |
| `BranchManager` | branch | main | 分支创建/切换/合并（无头核心） |
| `VersionControl` | vc | main | 提交/日志/差异/标签/回滚（无头核心） |
| `PositionSpecParser` | (参数系统) | main | 字符串 -> PositionSpec AST（纯解析） |
| `PositionResolver` | (参数系统) | main（接口） + client/server（实现） | PositionSpec -> Vec3（需运行时上下文） |
| `IWorldEditAccess` | (编辑引擎) | main（接口） + client/server（实现） | 写入 Create TrackGraph（放置/移除轨道方块） |
| `ServerEngineBackend` | server | server | 服务端中心化功能：同步/QoL/校验/[Server] 用户编辑逃生口 |

---

## 10. 分阶段实现优先级

| 优先级 | 内容 | 依赖 |
|---|---|---|
| **P0-refactor** | sourceSet 重构：main（无头编辑引擎）+ server（新增）+ client（GUI）；IWorldEditAccess 接口定义 | 现有 main + client 代码 |
| **P0-fix** | 修复现有 5 条命令 + 迁移客户端命令注册 + 新增 overlay enable/disable/status | sourceSet 重构 |
| **P0-fix** | theme set/reset + theme list 扫描目录 | ThemeSerializer 已有 |
| **P0-fix** | debug stats/dump/layer-count/overlay-anchors 实现 | WorldTreeReadOverlay 暴露统计 |
| **P0.5** | adapter 域 4 条命令 | MapOverlayDispatcher |
| **P1** | PositionSpecParser（main）+ PositionResolver 接口（main）+ ClientPositionResolver（client）+ AxisSpec 参数系统 | 自定义 ArgumentType |
| **P1** | edit 域（extend/link/node/edge/edgepoint/undo）| EditEngine(main) + IWorldEditAccess + StagingTree |
| **P1** | staging 域 4 条命令 | StagingTree(main) |
| **P1** | snap 域 3 条命令 | SnapConfig(main) |
| **P1** | server 域基础命令（status/validate/sync/config）+ ServerPositionResolver | server sourceSet 就绪 |
| **P1** | 别名（an/ae/al/rn/re/u/s） | edit + staging 域就绪 |
| **P2** | edit edge convert/add-bezier/add-arc/add-spline | EdgeGeometry ARC/SPLINE 类型 |
| **P3** | branch 域 6 条命令 | BranchManager(main) |
| **P3** | vc 域 8 条命令 | VersionControl(main) + 实时世界树校验 |
| **P3** | server edit 逃生口 + server commit | server sourceSet + EditEngine(main) |
| **P3** | 别名（c/b/l/d） | branch + vc 域就绪 |
| **P4** | io 域 3 条命令（预留） | IfcExchangeAdapter 接口 |

---

## 11. 与 Phase 0 spec 的差异说明

| Phase 0 spec 原文 | 本规格更新 |
|---|---|
| §4.8 定义 5 条命令 | 扩展为 P0-P3 完整命令树，原 5 条为 P0 子集 |
| §4.8 `RegisterCommandsEvent` 注册 | 三组 sourceSet：main（无头编辑引擎）+ server（逻辑服务端）+ client（GUI）；命令注册分属 client/server |
| 两组 sourceSet（main=common, client=client） | 三组 sourceSet（main=共用库+编辑引擎无头, server=逻辑服务端, client=GUI 工具） |
| §4.8 命令无参数 | 引入 PositionSpec/NodeSelector/AxisSpec 参数系统 |
| 无编辑命令 | 新增 edit 域（extend/link/node/edge/edgepoint）+ 编辑模式（staging/direct） |
| 无版本控制命令 | 新增 branch/vc 域（Git 分支 + SVN 校验） |
| 无别名 | 新增高频操作别名表 |
| 无服务端逃生口 | server 域：[Server] 用户无头编辑引擎实例（OP 专用） |

Phase 0 spec §4.8 的命令定义在本规格生效后视为被替代。
