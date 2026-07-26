# KP 地图内嵌配置面板 — 扁平 GUI 设计规格（建议）

> 日期：2026-07-26
> 状态：建议（待评审）
> 范围：`ProviderConfigScreen`（Xaero / JourneyMap 全屏地图内嵌配置面板）的视觉与交互重设计。
> 本文只定义**设计决策与规格**，不含实现细节。

## 1. 背景与目标

现有面板为纯文本拼装：`[ X ]` 文本复选框、刺眼的蓝色块 Tab、数值只读（须改用 `/kp provider set` 命令）、固定高度留白、无边框无层次。

目标：
- 达到现代扁平 GUI（SolidWorks / FreeCAD 设置面板）水准：真实控件、状态可感知、操作即时生效。
- 视觉上与 Create 模组 UI 同源，同时确立 KP 自身品牌识别（KP 紫 accent）。
- 面板保持嵌入式（渲染于地图 Screen 之上），不引入独立 Screen。

非目标：
- 不改动面板对外交互契约（打开/关闭方式、Mixin 挂载点）。
- 不覆盖 Cloth Config 独立配置屏（二者职责不同）。

## 2. UI 库决策

**采用 Catnip 渲染原语**（Create 6 官方拆分的 Blaze3D 高层 UI 库）。

理由：
- Create 的运行时硬依赖 → 玩家侧零新增依赖。
- 渲染原语不绑定 Screen 生命周期，可在任意 `GuiGraphics` 上绘制，契合嵌入场景。
- 与 Create 配色、tooltip 风格天然同源。

约束：Catnip 版本须与项目锁定的 Create 版本所要求的一致（版本错配会引发兼容事故）。

备选否决：完整 UI 框架（Modern UI / Elementa 等）假设自有 Screen，嵌入场景过重；纯手写则重复造轮。

## 3. 设计系统（Design Tokens）

### 3.1 配色

| Token | 值 | 用途 |
|---|---|---|
| `panelBg` | `0xF228282C` | 面板底（95% 不透明深灰） |
| `panelBorder` | `0xFF000000`，顶部 1px `0x40FFFFFF` 高光 | flat-raised 立体边 |
| `accent` | `0xFF7C57D4`（KP 紫） | 选中 Tab 下划线、勾选态、控件 hover |
| `textPrimary` | `0xFFF3EFE0`（Create 米白） | 标题、label |
| `textSecondary` | `0xFF8B8B93` | 提示、单位 |
| `danger` | `0xFFFFAD60`（Create 橙） | `[FUSED]` 熔断标记 |

### 3.2 图标系统（SolidWorks / FreeCAD 惯例）

- 16×16 扁平单色线稿图标，1px 线宽、统一视觉重心，集中于一张 atlas。
- **状态用渲染时着色（tint）表达，而非多张贴图**：
  normal `0xFFC8C8C8` / hover `0xFFFFFFFF` / active KP紫 / disabled 40% alpha。
- 图标清单：`gear`、`check`、`minus`、`plus`、`close`、`link`、`layers`。

### 3.3 网格与排版

- 4px 基数间距阶梯（4/8/12/16），行高 16px，MC 原生 9px 字体。
- 投影：右下偏移 2px、20% 黑（扁平投影惯例）。

## 4. 布局规格

```
┌────────────────────────────────┐
│ ⚙ Kinetic Planner           [×]│  ← 标题栏 18px；下方 accent 40% 分隔线
│ xaeroworldmap   journeymap     │  ← 文本 Tab；选中项底部 2px KP紫下划线（弃色块）
│ ────────────────────────────── │
│  Enabled                  [☑]  │  ← label 左 / 控件右的对齐列，行高 16
│  Priority             [−] 0 [+]│  ← 步进器，点击即生效
│  Line Width        [−] 1.00 [+]│
│  Alpha             [−] 1.00 [+]│
│  Dashed                   [ ]  │
│  Create Track Map         [☑]  │
│ ────────────────────────────── │
│  提示文本（次级色）              │
└────────────────────────────────┘
```

- 复选框：14×14、1px 边框；勾选 = KP紫填充 + 米白 `check` 图标。
- 面板高度按内容自适应；宽度维持 200px。

## 5. 交互规格

- 复选框、步进器点击**即时生效**（移除「Use /kp provider set」提示；命令仍保留作为脚本途径）。
- 行 hover：8% 白底高亮；参数行悬停显示说明 tooltip（Create/Catnip 风格）。
- 步进器数值 clamp 在合理域（Alpha 0–1、Line Width 0.1–4，步进 0.05；Priority 整数步进 1）。
- `[×]` 与齿轮按钮均可关闭面板；不拦截 Esc。
- 熔断（`[FUSED]`）provider 的 Tab 置为 danger 色，控件禁用（disabled tint）。

## 6. 验收标准

- 悬停齿轮按钮只出现「Kinetic Planner」一条提示（Create 提示已被 Mixin 封杀）。
- 面板内所有开关/数值改动即时反映到地图叠加层，无需命令。
- Xaero 与 JourneyMap 两侧视觉与行为一致。
- 与 Modern UI 共存时字体渲染无异常。

## 7. 关联

- Mixin 封杀设计（Create 按钮/Toast/叠加层接管）：见 `.codebuddy/memory/MEMORY.md`「接管 Create Track Map」。
- 前置设计：`2026-07-20-kinetic-planner-phase0-design.md`。
