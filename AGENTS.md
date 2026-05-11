# UI 设计指南

> **设计类型**: App 设计（应用架构设计）
> **确认检查**: 本指南适用于可交互的应用/网站/工具。

> ℹ️ Section 1-2 为设计意图与决策上下文。Code agent 实现时以 Section 3 及之后的具体参数为准。

## 1. Design Archetype (设计原型)

### 1.1 内容理解

- **目标用户**: 企业内部员工、部门管理者、HR/行政管理员，在办公环境中高频使用
- **核心目的**: 帮助企业完成绩效管理流程，支持多级权限管控和流程自动化流转
- **期望情绪**: 专业、可信、高效、清晰
- **需避免的感受**: 混乱、信息过载、操作繁琐、廉价感

### 1.2 设计语言

- **Aesthetic Direction**: 专业企业级后台系统，需要清晰的信息层级、舒适的阅读体验和高效的操作路径
- **Visual Signature**: 沉稳专业蓝调、清晰信息卡片、适度留白、胶囊状态标签
- **Emotional Tone**: 专业可信 - 服务企业绩效管理，需要建立用户对系统的信任感
- **Design Style**: 经典稳重「Muji 极简」，企业级系统需要去除多余装饰，专注内容和操作效率
- **Application Type**: Admin/SaaS - 多页面后台管理系统，功能模块较多

## 2. Design Principles (设计理念)

1. **信息优先**：去除不必要的装饰元素，让数据和操作成为视觉中心
2. **层级清晰**：通过卡片、留白和字体权重建立清晰的信息层级，帮助用户快速定位
3. **专业稳重**：配色和排版符合企业办公场景，传递信任感和专业度
4. **高效操作**：关键操作突出展示，状态标识清晰可辨，减少用户思考成本

## 3. Color System (色彩系统)

**配色设计理由**：企业绩效管理需要建立信任感，选择沉稳的靛蓝作为主色，低饱和度避免视觉疲劳，适配高频办公使用。

### 3.1 主题颜色

| 角色               | CSS 变量               | Tailwind Class            | HSL 值    
| ------------------ | ---------------------- | ------------------------- | ---------- 
| bg                 | `--background`         | `bg-background`           | hsl(215 25% 97%)
| card               | `--card`               | `bg-card`                 | hsl(0 0% 100%)
| text               | `--foreground`         | `text-foreground`         | hsl(218 45% 13%)
| textMuted          | `--muted-foreground`   | `text-muted-foreground`   | hsl(218 15% 45%)
| primary            | `--primary`            | `bg-primary`              | hsl(215 80% 42%)
| primary-foreground | `--primary-foreground` | `text-primary-foreground` | hsl(0 0% 100%)
| accent             | `--accent`             | `bg-accent`               | hsl(215 25% 95%)
| accent-foreground  | `--accent-foreground`  | `text-accent-foreground`  | hsl(215 80% 42%)
| border             | `--border`             | `border-border`           | hsl(214 20% 90%)

### 3.2 Sidebar 颜色（仅当使用 Sidebar 导航时定义）

| 角色                       | CSS 变量                       | Tailwind Class                    | HSL 值     | 设计说明                         |
| -------------------------- | ------------------------------ | --------------------------------- | ---------- | -------------------------------- |
| sidebar                    | `--sidebar`                    | `bg-sidebar`                      | hsl(218 45% 13%) | Sidebar 背景色，深色导航区与内容区形成区分 |
| sidebar-foreground         | `--sidebar-foreground`         | `text-sidebar-foreground`         | hsl(215 20% 85%) | 导航文字，浅色在深色背景保证可读性 |
| sidebar-primary            | `--sidebar-primary`            | `bg-sidebar-primary`              | hsl(215 80% 42%) | 激活态背景，使用主色保持品牌一致性 |
| sidebar-primary-foreground | `--sidebar-primary-foreground` | `text-sidebar-primary-foreground` | hsl(0 0% 100%) | 激活态文字，白色对比度充足 |
| sidebar-accent             | `--sidebar-accent`             | `bg-sidebar-accent`               | hsl(215 30% 22%) | Hover 态背景，提供柔和交互反馈 |
| sidebar-accent-foreground  | `--sidebar-accent-foreground`  | `text-sidebar-accent-foreground`  | hsl(215 20% 85%) | Hover 态文字，保持可辨识度 |
| sidebar-border             | `--sidebar-border`             | `border-sidebar-border`           | hsl(215 30% 25%) | Sidebar 右侧边框，与内容区分 |
| sidebar-ring               | `--sidebar-ring`               | `ring-sidebar-ring`               | hsl(215 80% 42%) | 聚焦环颜色，与主色保持一致 |

### 3.4 语义颜色（可选）

| 语义 | CSS 变量 | HSL 值 | 用途 |
| ---- | -------- | ------ | ---- |
| 成功 | `--success` | hsl(145 63% 40%) | 已完成、提交成功、启用状态 |
| 成功-背景 | `--success-bg` | hsl(145 30% 95%) | 成功状态背景 |
| 警告 | `--warning` | hsl(35 92% 40%) | 待处理、提醒、草稿 |
| 警告-背景 | `--warning-bg` | hsl(35 30% 95%) | 警告状态背景 |
| 错误 | `--danger` | hsl(0 84% 55%) | 错误、驳回、停用 |
| 错误-背景 | `--danger-bg` | hsl(0 30% 95%) | 错误状态背景 |
| 信息 | `--info` | hsl(215 80% 42%) | 进行中、待评分 |
| 信息-背景 | `--info-bg` | hsl(215 30% 95%) | 信息状态背景 |

## 4. Typography (字体排版)

- **Heading**: 思源黑体, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif
- **Body**: 思源黑体, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif
- **数字/代码**: 思源黑体, Roboto Mono, SFMono-Regular, Menlo, Monaco, Consolas, monospace
- **字体导入**: 使用系统字体栈，无需引入外部字体

**排版层级**:

| 元素 | 字号 | 字重 | 行高 |
| ---- | ---- | ---- | ---- |
| Page Title | 28px (text-2xl) | font-bold | 1.3 |
| Section Title | 20px (text-xl) | font-semibold | 1.4 |
| Card Title | 16px (text-base) | font-semibold | 1.4 |
| Body Text | 14px (text-sm) | font-normal | 1.6 |
| Secondary Text | 13px (text-xs) | font-normal | 1.5 |
| Table Text | 14px (text-sm) | font-normal | 1.5 |

## 5. Layout Strategy (布局策略)

### 5.1 结构方向

**导航策略**：功能模块多，包含前台操作和后台管理，需要持久导航 → 采用侧边栏固定布局

- 左侧固定侧边栏展示主导航菜单
- 右侧内容区独立滚动
- 顶部内容区左侧显示页面标题，右侧放置用户信息/角色切换

**页面架构特征**：
- 数据密集型企业后台，适中信息密度，平衡内容和呼吸感
- 采用卡片式布局承载不同功能区块
- 表格用于展示绩效列表和模板列表

### 5.2 响应式原则

**断点策略**：
- 大屏 (>1280px)：侧边栏完整展开，内容区充分利用宽度
- 平板 (<1280px)：侧边栏可折叠为图标模式
- 移动端 (<768px)：侧边栏收起为抽屉，点击汉堡按钮呼出

**内容密度**：
- 桌面端保持适当留白，信息不拥挤
- 移动端单列展示，增大点击热区，确保可操作性
- 表格在移动端允许横向滚动

## 6. Visual Language (视觉语言)

**形态特征**：
- 极简锐利 → 小圆角 (`rounded-md` / 0.375rem)，符合企业系统专业感
- 卡片使用 `shadow-sm`  subtle 阴影，营造轻度层次感
- 状态标签使用胶囊形状 (`rounded-full`)，清晰区分不同绩效状态
- 表格边框使用细线条，保持干净整洁

**间距策略**：
- 容器间距：`space-y-6` 保持章节呼吸感
- 卡片内边距：`p-6`，信息不拥挤
- 网格间距：`gap-4`，区块之间保持适度距离

**装饰策略**：
- 极简设计，不使用额外装饰元素
- 通过卡片、阴影、留白自然建立层次
- 仅在首页工作台可使用轻微数据可视化点缀

**动效原则**：
- 快速响应，悬停反馈时长 150ms，过渡自然不拖沓
- 侧边栏展开/收起使用平滑过渡 (200ms)
- 所有可交互元素必须有 hover/focus 状态反馈

**状态规范**：
- 主按钮：`bg-primary text-primary-foreground`，hover 时主色明度降低 8%
- 次要按钮：`bg-accent text-accent-foreground`，hover 保持一致基调
- 表单输入：focus 时 ring-primary，边框颜色与主色一致
- 表格行：hover 使用 `bg-accent` 高亮，帮助用户定位

**可及性保障**：
- 所有正文文字对比度 ≥ 4.5:1，满足 WCAG AA 标准
- 交互元素最小点击尺寸 ≥ 44px × 44px
- 状态颜色除色相外，也通过文字明暗度区分，保障色盲用户可识别
- 流程进度条各阶段明确标识，当前阶段高亮显示

---

## 数据库：菜单与权限（RBAC）

菜单数据在表 `menu`（`menu_key`, `name`, `sort_order`），授权在 `role_menu`（`role_key`, `menu_key`, `allowed`）。代码侧权威列表为 `shared/api.interface.ts` 中的 `MenuPermissionKey` / `MENU_PERMISSION_KEYS`。

### 新增或修改菜单时（必须同步）

1. 在 `MenuPermissionKey` 与 `MENU_PERMISSION_KEYS` 中增加或调整 key。
2. 侧栏 `client/src/components/Layout.tsx` 的 `navItems`（若有独立路由）。
3. `client/src/pages/admin/PermissionManagePage/PermissionManagePage.tsx` 的 `MENU_LABELS`（权限矩阵展示名）。
4. **SQL**：在 `server/database/sql/initial.sql` 的 `INSERT INTO menu` 段落写入同一批定义（新建库用）。
5. **SQL**：在 `server/database/sql/sync-menu-table.sql` 中追加/修改对应行（已有库可重复执行，会更新 `name`/`sort_order`，并用 `INSERT IGNORE` 为各角色补全缺失的 `role_menu`）。

### 已有库 `menu` / `role_menu` 为空或不完整时

在数据库中执行：

`server/database/sql/sync-menu-table.sql`

该脚本会： upsert 全部菜单行；为 `super_admin` / `admin` / `employee` 插入尚未存在的 `role_menu` 记录（不覆盖你已手动改过的开关）。

### HTTP API 变更时（必须同步清单与 Newman）

修改、新增或删除对外 HTTP 接口（`server/modules/**/*.controller.ts` 及相关的 `shared/api.interface.ts`）时，须同步：

- `scripts/newman/api-inventory.md`
- `scripts/newman/build-collection.mjs`，并执行 `npm run newman:build` 生成 `scripts/newman/jixiao2-api.postman_collection.json`

细则与例外见 **`.cursor/rules/api-newman-sync.mdc`**（编辑上述文件时 Cursor 会附带该规则）。