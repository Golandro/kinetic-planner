---
description: 项目文档索引
alwaysApply: true
enabled: true
updatedAt:
provider:
---
# 文档索引

> 本文件由 AGENTS.md 拆分而来，提供完整的项目文档索引。最后更新：2026-07-31。

## 设计规格

| 文档 | 路径 | 内容 |
|---|---|---|
| Phase 0 设计规格 | `../../docs/superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md` | 架构、接口契约、验收标准、失败模式、兼容性矩阵（§4.5 NanoVG 已被 0b spec 替代为 Blaze3D） |
| Phase 0b 设计规格 | `../../docs/superpowers/specs/2026-07-22-kinetic-planner-phase0b-design.md` | Blaze3D 薄封装渲染引擎、主题系统、配置/命令体系 |
| 命令树设计规格 | `../../docs/superpowers/specs/2026-07-23-kinetic-planner-command-tree-design.md` | P0-P3 命令树设计（78 个命令节点 + 12 别名） |
| 配置面板扁平 GUI 设计 | `../../docs/superpowers/specs/2026-07-26-config-panel-flat-gui-design.md` | Phase B 实施规格：LDLib2 UI 树/控件/LSS 主题/事件转发/TDD 要求 |
| LDLib2 API 审计 | `../../docs/superpowers/specs/2026-07-27-ldlib2-api-verification.md` | LDLib2 关键 API 签名验证（TabView/Toggle/Stylesheet/纹理类等） |
| Xaero 地图编辑器集成设计 | `../../docs/superpowers/specs/2026-07-27-xaero-map-editor-integration-design.md` | Xaero 全屏地图内编辑模式设计（Screen 壳+工具面板+Ribbon 栏） |
| 注册表框架与 Provider 解耦审计 | `../../docs/superpowers/specs/2026-07-29-registry-framework-provider-decoupling-audit.md` | KPRegistry/KPId/ProviderConfigBinding/MapProviderFactory 框架设计与 O1-O3 优化审计 |

## 实现计划

| 文档 | 路径 | 内容 |
|---|---|---|
| Phase 0a 实现计划 | `../../docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md` | 10 个 Task：骨架+sourceSet 拆分+MC 原生线叠加+可视化锚点 |
| Phase 0b 实现计划 | `../../docs/superpowers/plans/2026-07-22-kinetic-planner-phase0b.md` | 11 个 Task：Blaze3D 矢量封装+主题+配置命令+验收 |
| P0 重构计划 | `../../docs/superpowers/plans/2026-07-23-kinetic-planner-p0-refactor-fix.md` | 5 个 Task：sourceSet 重构+命令迁移+OverlayControl/ThemeManager+14 命令 |
| P1.0 实现计划 | `../../docs/superpowers/plans/2026-07-23-kinetic-planner-p1.0-provider-config.md` | per-provider 配置+hideCreateTrackMap+嵌入式 UI（Phase A/B） |
| P0.5+PhaseB+P1.1 计划 | `../../docs/superpowers/plans/2026-07-25-kinetic-planner-p0.5-phaseb-p1.1.md` | JM 适配器+熔断器+嵌入式 UI（Xaero+JM 双路）+dashed 渲染（9 Task） |
| Xaero 地图编辑器集成计划 | `../../docs/superpowers/plans/2026-07-28-xaero-map-editor-integration.md` | Xaero 全屏地图内编辑模式实现（Editor Screen/工具面板/Ribbon） |
| 注册表框架与 Provider 解耦计划 | `../../docs/superpowers/plans/2026-07-29-registry-framework-provider-decoupling.md` | KPRegistry/KPId + ProviderConfigBinding + MapProviderFactory 工厂注册表（13 Task，含 O1-O3 优化） |

## 开发参考

| 文档 | 路径 | 内容 |
|---|---|---|
| 路线图与状态 | `../../STATUS.md` | 阶段规划、进度跟踪、兼容性、核实状态 |
| 编码规范速查 | `../../docs/conventions.md` | MC 1.21.1 API 约定、Create 6.0.10 API 修正、渲染模式、命名约定、常见错误 |
| JavaDoc 查询指南 | `../../docs/javadoc-guide.md` | 外部依赖 JavaDoc 查询方法、项目 JavaDoc 规范 |
| LLM 项目规则 | `../../AGENTS.md` | AI 编码助手自包含上下文（§1-§7：项目介绍/环境/规则索引/关联机制/记忆/探索流程/维护规范） |
| Agent 详细上下文 | `..` | 包结构、构建测试、文档索引（本目录） |

## 审查历史

| 文档 | 路径 | 内容 |
|---|---|---|
| 文档审查报告 | `../../docs/chat_history/KineticPlanner-文档计划完整审查.md` | 完整审查意见与修订记录 |
| 文档审查与建议 | `../../docs/chat_history/KineticPlanner-文档审查与建议.md` | README/许可证/文档结构建议 |
