# Kinetic Planner — AI 编码助手项目规则

> **自包含项目上下文文件**。AI 编码助手在本文件 + `.agents/rules/`（alwaysApply）即可获得完整开发上下文。
> 最后更新：2026-07-31。

---

## §1 项目基础介绍

**Kinetic Planner** 是 Minecraft 1.21.1 NeoForge 模组，在全屏地图（Xaero's World Map / JourneyMap）上以 **CAD 操作思路**叠加显示并编辑机械动力（Create）铁路拓扑。

| 属性 | 值 |
|---|---|
| mod_id | `kinetic_planner` |
| mod_group_id | `net.jsmua.kinetic_planner` |
| 许可证 | MIT |
| 开发分支 | `1.21` |
| 当前版本 | `0.1.0-alpha` |
| 渲染方案 | Blaze3D 三角形带（POSITION_COLOR shader） |

**核心功能：** 把 Create 运行时的 `TrackGraph` 图拓扑以矢量方式叠加到地图上，支持规划、编辑、版本控制与多格式导入导出（IFC 4.3 铁路子集）。

**设计理念：**
- **CAD-first**：地图即画布，叠加层即 CAD 图层
- **Provider 解耦**：地图模组通过工厂注册表（`MapProviderRegistry`）可插拔
- **sourceSet 隔离**：common（纯 JVM）/ client（GUI/渲染）/ server（编辑引擎）三层分离
- **可测试性**：common 层纯数学/逻辑可 JUnit 5 单测，client 层 Mockito mock

**目标用户：** Minecraft 机械动力（Create）模组玩家中的铁路规划者。

---

## §2 开发环境推荐配置

### 2.1 必选工具链

| 工具 | 版本 | 说明 |
|---|---|---|
| **Java (JDK)** | 21（Zulu 21.0.11） | Gradle toolchain 自动解析（Foojay） |
| **Gradle Wrapper** | 9.6.1 | 项目自带，无需全局安装 |
| **Git** | ≥ 2.40 | 分支 `1.21`，直接开发 |
| **OS** | Windows 10/11（推荐） | Linux/macOS 亦可 |

### 2.2 IDE 配置

**首选：IntelliJ IDEA**（Community 即可）

- 启用源码/文档自动下载（`build.gradle` 已配置 `downloadJavadoc = true`）
- 主 sourceSet 须包含 `src/client/java` 和 `src/server/java`（Gradle 导入自动识别）
- 使用 Foojay 解析 Java 工具链

**备选：VS Code / Cursor**

- 安装 `Extension Pack for Java`（vscjava.vscode-java-pack）
- 打开项目后自动识别 Gradle 多 sourceSet

### 2.3 构建命令

```bash
gradlew compileJava compileClientJava compileServerJava   # 编译三 sourceSet
gradlew test                                               # 测试（134 @Test，15 @Disabled）
gradlew build                                              # 完整构建（编译+测试+jar）
gradlew runClient                                          # 启动游戏客户端
```

- 优先使用 `GRADLE_USER_HOME` 作为 Gradle 用户目录
- Shell 为 `cmd.exe` / PowerShell 时多命令用 `&` 或 `&&` 分隔
- 编译编码：UTF-8（`build.gradle` 中 `options.encoding = 'UTF-8'`）
- 测试框架：JUnit 5 + Mockito 5

### 2.4 关键依赖版本

| 依赖 | 版本 | 风险 |
|---|---|---|
| Minecraft | 1.21.1（锁定） | 低 |
| NeoForge | 21.1.235 | 低 |
| Create | 6.0.10-280 | **中**（内部类依赖） |
| Xaero's World Map | curse 7401095（未锁） | **高**（Mixin 注入点） |
| JourneyMap | 1.21.1-6.0.1 | 低 |
| LDLib2 | 2.2.26 | 低 |
| Flywheel | 1.0.6 | 低 |

---

## §3 必选规则文件索引

项目通过 `.agents/rules/` 目录管理 Agent 规则。以下为**始终加载**的规则文件：

| 文件 | 内容 |
|---|---|
| `AGENTS.md`（本文件） | 项目介绍、环境配置、规则索引、关联机制、记忆指南、探索流程、维护规范 |
| `.agents/rules/hard-rules.md` | 硬性 API 与架构规则（sourceSet/MC/Create/NeoForge/Mixin/测试） |
| `.agents/rules/package-structure.md` | 完整包结构树、代码统计、Mixin 清单 |
| `.agents/rules/documents.md` | 设计规格/实现计划/开发参考 完整文档索引 |

---

## §4 规则文件关联机制

### 4.1 问题判定

若 AI 编码工具的初始上下文**未**包含上述始终加载的规则文件，则判定为 **`.agents/` 目录配置缺失**。

### 4.2 处置流程

**必须第一时间阻断当前任务**，向用户说明：

1. **当前情况**：工具未加载项目 Agent 规则，可能导致违反 sourceSet 分离、API 约定等关键约束
2. **重要性**：规则文件包含 MC/Create/NeoForge API 陷阱（如 `addVertex` vs `vertex`）、Mixin 约定等，缺失将导致编译失败
3. **不分发品牌工具目录的理由**：`.claude/`、`.trae/` 等品牌目录已在 `.gitignore` 中排除，项目不绑定特定 AI 工具
4. **操作方案**（推荐符号链接，免维护）：

```powershell
# Windows（管理员 PowerShell）— 以 Claude Code 为例
New-Item -ItemType SymbolicLink -Path ".claude" -Target ".agents"

# 其他工具类推：.trae → .agents, .codebuddy → .agents 等
```

```bash
# Linux / macOS
ln -s .agents .claude
```

**验证：** 重新打开项目，确认工具上下文中出现始终加载规则的内容。

---

## §5 本地记忆使用指南

### 5.1 目录定位

`.agents/memory/` 是**工作区本地记忆**存储目录：

- **已排除版本控制**（`.agents/.gitignore` 中包含 `memory`）
- **不应同步**到远程仓库或跨设备
- 定性为**短期记忆**，仅在当前工作区有效

### 5.2 记忆生命周期

```
创建 → 使用 → 沉淀/淘汰
  │              │
  │              ├─ 事实性结论 → 沉淀到 .agents/rules/ 对应规则文件
  │              ├─ 阶段性状态 → 沉淀到 STATUS.md
  │              └─ 过时/错误 → 删除
  │
  └─ 记录探索发现、临时决策、待验证假设
```

**核心原则：记忆数据不可替代正式文档的沉淀作用。**

### 5.3 无记忆管理功能的平台操作

对于不支持内置记忆管理的 AI 编码工具，使用 Markdown 文件手动管理：

```
.agents/memory/
├── MEMORY.md          # 主记忆文件（按主题分段）
└── scratch/           # 临时草稿（可随时清理）
```

**CRUD 操作：**
- **创建**：在 `MEMORY.md` 中新增 `## 主题` 段落
- **读取**：任务开始时查阅 `MEMORY.md` 相关段落
- **更新**：直接编辑对应段落，标注更新日期
- **删除**：结论已沉淀到规则文件后，删除对应段落

---

## §6 项目探索规范流程

### 6.1 分层探索策略（由浅入深）

```
规则文件 → JavaDoc/LSP → 设计文档 → 源代码
```

| 层级 | 何时使用 | 工具 |
|---|---|---|
| **规则文件** | 任务开始、了解约束 | 直接阅读 `.agents/rules/`（始终加载） |
| **JavaDoc / LSP** | 仅需接口签名、参数类型、方法契约 | IDE 悬浮 / `Ctrl+Q` / LSP `textDocument/hover` |
| **设计文档** | 理解架构决策、阶段规划 | 按 `documents.md` 索引查阅 |
| **源代码** | 需要实现细节、调试、修改 | 读取具体 `.java` 文件 |
| **外部文档** | 外部依赖 API 不在本地 JavaDoc 中 | Context7 / 网络搜索 |

### 6.2 JavaDoc 优先原则（Java 特色）

**Java 拥有标准化的 JavaDoc 体系，应充分利用以减少冗余文档：**

1. **接口信息**：必须使用 LSP 工具或 IDE JavaDoc 查询，**不要**通过阅读实现代码获取签名
2. **方法契约**：优先查看 JavaDoc 中的 `@param` / `@return` / `@throws` 说明
3. **包结构**：`package-info.java`（若存在）提供包级说明
4. **外部依赖**：Create/NeoForge 等依赖通过 IDE 反编译 + JavaDoc 悬浮查询

**项目源码 JavaDoc 规范：**
- 类级：职责描述 + 架构上下文 + 消费者列表
- 方法级：功能 + `@param` + `@return`
- 行内：解释"为什么"（不是"做什么"），API 适配标注 `// MC 1.21.1:` / `// Create 6.0.10:`

### 6.3 上下文补全顺序

当当前上下文不含完成任务所需的信息时，**必须**按以下顺序补全：

1. **查阅始终加载规则**：硬性规则、包结构、文档索引
2. **查阅按需规则**：构建测试、注册表框架等（`.agents/rules/` 中 `alwaysApply: false` 的文件）
3. **查阅设计规格与实现计划**：按 `documents.md` 索引定位
4. **JavaDoc / LSP 查询**：验证 API 签名、字段可见性、方法契约
5. **搜索代码库**：语义搜索或正则搜索定位实现
6. **搜索外部文档**：Context7 / 网络搜索（外部依赖 API）

**不得在未查阅上述资源的情况下猜测 API 签名或项目约定。**

---

## §7 项目维护与文档管理规范

### 7.1 文档同步维护

所有项目更改者（人类或 AI）**必须**同步维护对应文档：

| 变更类型 | 须更新的文档 |
|---|---|
| 新增/删除/重命名类 | `.agents/rules/package-structure.md`（包结构树 + 代码统计） |
| API 约定发现/修正 | `.agents/rules/hard-rules.md` |
| 阶段完成/状态变化 | `STATUS.md` |
| 新增设计规格/计划 | `.agents/rules/documents.md`（文档索引表） |
| 注册表框架变更 | `.agents/rules/registry-framework.md` |
| 构建/测试策略变更 | `.agents/rules/build-and-test.md` |

### 7.2 记忆沉淀规则

- **短期记忆**（`.agents/memory/`）：探索发现、临时决策、待验证假设 → 不同步
- **长期事实** → 沉淀到 `.agents/rules/` 对应规则文件
- **阶段状态** → 沉淀到 `STATUS.md`
- **API 陷阱** → 沉淀到 `.agents/rules/hard-rules.md`
