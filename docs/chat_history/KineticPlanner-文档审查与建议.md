# Kinetic Planner 文档审查与建议

> 审查对象：`https://github.com/Golandro/kinetic-planner` 分支 `1.21`
> 审查时间：2026-07-21
> 结论：项目的**设计/计划文档质量很高但藏在工具专用目录里**，且**与当前代码严重脱节**；**面向用户与开发者的可读文档基本缺失**，其中 README 仍是上游 NeoForge MDK 模板。建议按优先级补齐。

---

## 一、当前文档资产盘点

| 文件 | 状态 | 性质 | 问题 |
|---|---|---|---|
| `../../README.md` | 未定制 | 上游 MDK 模板原文 | 通篇讲"这是一个模板仓库，可克隆新建模组"，**对 Kinetic Planner 只字未提** |
| `../superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md` | 详尽（~678 行） | superpowers 工作流"规格"文档 | 质量高，但路径含工具名、没有目录、未标注"代码尚未实现" |
| `../superpowers/plans/2026-07-21-kinetic-planner-phase0.md` | 详尽（~834 行） | superpowers 工作流"实现计划" | 同上；分了 11 个 Task，但代码里**一个都还没做** |
| `LICENSE` | MIT（Golandro 2026） | 许可证 | 与 `../../gradle.properties` 的 `mod_license=All Rights Reserved` **矛盾** |
| `../../TEMPLATE_LICENSE.txt` | 上游 MDK MIT 模板 | 旧址文件 | 应随重命名一起清理（见建议） |
| `src/main/java/com/example/examplemod/*` | **仍是示例骨架** | 源码 | Task 1 未执行，`../../gradle.properties` 仍是 `mod_id=examplemod` |
| `../../.github/workflows/build.yml` | 可用 | CI | 仅 `./gradlew build`，无测试/产物上传，基本合格 |

**核心矛盾**：设计文档把 Phase 0 讲得非常完整，但工作树里的代码还是 NeoForge MDK 的 `examplemod` 起始模板（包名 `com.example.examplemod`、仍注册 `EXAMPLE_BLOCK` 等）。也就是说——**文档领先于代码约一个 Phase**。

---

## 二、最严重的问题（建议优先处理）

### 1. README 是上游模板，没有任何项目信息（P0）
当前 README 开头就是：

> *"This template repository can be directly cloned to get you started with a new mod."*

对外部读者（用户、贡献者、未来自己）而言，他们无法从 README 得知：
- 这是什么模组、解决什么问题；
- 需要装哪些前置（Create **必需**，Xaero 可选，JourneyMap 计划中）；
- 怎么构建、怎么用、有哪些命令（`/kp ...`）。

**建议**：重写成真正的项目 README（模板见下文第五节结构），至少包含：一句话定位、特性、前置依赖、安装、构建、链接到设计文档。

### 2. 文档与代码状态脱节（P0）
设计/计划文档描述的是"目标 Phase 0"，但代码尚未落地。任何 clone 仓库的人会被文档误导，以为功能已实现。

**建议**：
- 在 `docs/superpowers/specs/...` 顶部加一行**状态横幅**，例如：`> 状态：本规格为 Phase 0 设计，截至 2026-07-21 代码尚未实现（仍为 MDK 骨架）。`
- 在 README 加一个 **Development Status / 开发进度** 小节，明确各 Phase 的实现状态。

### 3. 许可证声明不一致（P1）
- `LICENSE` 文件：MIT，Copyright (c) 2026 Golandro
- `../../gradle.properties`：`mod_license=All Rights Reserved`
- `../../TEMPLATE_LICENSE.txt`：上游 NeoForged MDK 的 MIT 模板（旧址）

三者口径不统一，发布时会造成困惑。

**建议**：
- 确定采用 MIT（与 `LICENSE` 一致），把 `../../gradle.properties` 的 `mod_license` 改为 `MIT`；
- 删除 `../../TEMPLATE_LICENSE.txt`（MDK 遗留，已无意义）；
- 如确需保留 "All Rights Reserved"，则同步改 `LICENSE` 文件——但 MIT 对开源模组更友好，推荐前者。

---

## 三、缺失的文档（按优先级补齐）

| 优先级 | 缺失项 | 受众 | 内容要点 |
|---|---|---|---|
| P0 | 项目 README（重写） | 用户/贡献者 | 定位、特性、依赖、安装、构建、链接 |
| P1 | 开发状态/路线图 | 所有读者 | Phase 0–5 概览 + 当前实现进度 |
| P1 | 兼容性矩阵 | 用户 | Create / NeoForge / Xaero / JourneyMap 版本对应（设计文档里散落，未集中） |
| P1 | 构建与开发环境指南 | 贡献者 | JDK 21、Gradle wrapper、`./gradlew build`/`./test`、运行客户端调试 |
| P2 | 用户使用手册 | 用户 | 打开 Xaero 地图后叠加层的开关、主题、命令(`/kp overlay toggle` 等) |
| P2 | 架构概览（开发者版） | 贡献者 | 在 design 文档基础上提炼分包/数据流图，独立于 superpowers 工具目录 |
| P3 | `CONTRIBUTING.md` | 贡献者 | 代码风格、分支策略、如何执行 superpowers 计划 |
| P3 | `CHANGELOG.md` | 用户 | 随版本发布记录 |

---

## 四、对现有设计文档本身的改进建议

设计/计划文档本身写得相当扎实（接口签名、验收标准、风险约束都有），但仍可优化：

1. **加目录（ToC）**：规格文档 678 行、计划 834 行，没有锚点导航，长文档难跳转。
2. **把文档从 `../superpowers` 移到人类可读路径**：
   `docs/superpowers/specs/...` 与 `docs/superpowers/plans/...` 是某个 agent 工作流的专用命名，外部读者看不懂。
   **建议**重命名为 `docs/design/phase0-design.md` 与 `docs/plans/phase0.md`，并在 README 引用。
3. **显式补充"风险与已知脆弱点"章节**：设计里最大的技术风险是 **Xaero `GuiMap.render` 的 Mixin 注入点**——依赖 Xaero 内部类与渲染时机，跨 Xaero 版本极易断裂（熔断器已设计，但应在文档里把"版本脆弱性"作为一等风险列出，并给出 `ScreenEvent.Render.Post` 备选方案的触发条件）。
4. **标注"假设待验证"**：例如"Xaero 相机参数换算公式 `blocksPerPixel = guiScale * interfaceScale / mapScale`"依赖对 Xaero 内部字段的读取，文档应注明"需在目标 Xaero 版本实测验证"。
5. **计划文档应跟踪完成度**：11 个 Task 目前全是 `[ ]`，建议在 README 或计划顶部用进度条/勾选汇总，让读者一眼看到"已落地 vs 计划中"。

---

## 五、建议的目标文档结构

```
kinetic-planner/
├── README.md                      # 重写：定位/特性/依赖/安装/构建/链接
├── LICENSE                        # MIT（保留，与 gradle 对齐）
├── CHANGELOG.md                   # 版本记录（P3）
├── CONTRIBUTING.md                # 贡献指南（P3）
├── docs/
│   ├── STATUS.md                  # 开发状态 + Phase 路线图 + 兼容性矩阵（P1，新建）
│   ├── design/
│   │   └── phase0-design.md       # 由原 specs/ 迁移并重命名
│   ├── plans/
│   │   └── phase0.md              # 由原 plans/ 迁移并重命名
│   └── user-guide.md              # 使用手册：叠加层/主题/命令（P2，新建）
└── （删除 TEMPLATE_LICENSE.txt、清理 superpowers 目录）
```

---

## 六、我可以直接帮你做的事（可选）

如果你愿意，我可以按上述建议**直接动手**，例如：
1. 重写 `../../README.md`（中英双语或仅中文，含依赖/安装/构建/链接）；
2. 新建 `docs/STATUS.md`（开发状态 + Phase 路线图 + 兼容性矩阵）；
3. 把 `docs/superpowers/{specs,plans}` 迁移为 `docs/{design,plans}` 并补目录与状态横幅；
4. 统一许可证（改 `../../gradle.properties` + 删除 `../../TEMPLATE_LICENSE.txt`）；
5. 新建 `docs/user-guide.md`（命令、主题、叠加开关说明）。

告诉我需要落实哪几项，或先按优先级从 P0 开始即可。
