# 绩效管理业务流程

> 本文档描述绩效从创建到完成的完整生命周期，包括状态节点、操作角色、评分权重计算规则。

---

## 1. 角色定义

| 角色 | 标识 | 说明 |
|------|------|------|
| 超级管理员 | `super_admin` | `admin_config` 表配置，可操作所有绩效，执行终审/校准 |
| 管理员 | `admin` | 同上，权限等同 `super_admin` |
| 员工（被考核人） | `employee` | 绩效记录的 `employee_id`，负责选模板、设定目标、自评 |
| 直属上级 | `manager` | 绩效记录的 `manager_id`，来自 `employee_hierarchy`，负责审核目标、上级评分 |
| 虚线上级 | `dotted_manager` | 绩效记录的 `dotted_manager_id`（可选），负责虚线评分 |

---

## 2. 状态机与流转

```
template_selection          创建后初始状态
       │
       │  员工选择模板 (POST /:id/select-template)
       ▼
  goal_setting              员工填写目标
       │
       │  员工提交目标 (POST /:id/submit  reviewType=goal)
       ▼
goal_pending_review         等待上级审核目标
       │
       ├──approved──▶  self_review         上级批准 → 进入自评
       │                    │
       │                    │  员工提交自评 (POST /:id/submit  reviewType=self)
       │                    ▼
       │              manager_review        直属上级评分
       │                    │
       │                    ├─(有虚线上级)──▶ dotted_manager_review   虚线上级评分
       │                    │                       │
       │                    │                       │  虚线上级提交评分
       │                    │                       │  → 加权计算 totalScore
       │                    │                       ▼
       │                    ├─(无虚线上级)──▶  final_review    待终审/校准
       │                    │                       │
       │                    │                       ├──approved──▶ completed ✓
       │                    │                       └──rejected──▶ 回退到指定阶段
       │                    │
       └──rejected──▶ goal_rejected         目标被驳回 → 员工可重新提交目标
```

### 状态枚举

| 状态 | 标签 | 操作人 | 下一步操作 |
|------|------|--------|-----------|
| `template_selection` | 待选择模板 | 员工 | 选择模板 → `goal_setting` |
| `goal_setting` | 目标设定中 | 员工 | 提交目标 → `goal_pending_review` |
| `goal_pending_review` | 待审核目标 | 直属/虚线上级 | 批准 → `self_review`；驳回 → `goal_rejected` |
| `goal_rejected` | 目标被驳回 | 员工 | 修改后重新提交目标 → `goal_pending_review` |
| `self_review` | 自评中 | 员工 | 提交自评 → `manager_review` |
| `manager_review` | 直属上级评分中 | 直属上级 | 提交评分 → `dotted_manager_review`（有虚线）或 `final_review`（无虚线） |
| `dotted_manager_review` | 虚线上级评分中 | 虚线上级 | 提交评分 → `final_review` |
| `final_review` | 待终审/校准 | 管理员 | 批准 → `completed`；驳回 → 回退到指定阶段 |
| `completed` | 已完成 | — | 终态 |

---

## 3. 评分与权重计算

### 3.1 评分结构

每次评分（自评 / 上级评 / 虚线评）都是针对模板中的**指标列表**逐项打分：

```typescript
interface ReviewItem {
  indicatorName: string;  // 指标名称
  score: number;          // 该指标得分（如 0-100）
  comment: string;        // 评语
}
```

模板中的指标带有**指标权重**（各指标权重之和应为 100）：

```typescript
interface PerformanceIndicator {
  name: string;
  weight: number;         // 如 40（表示 40%）
  description: string;
}
```

### 3.2 单次评分加权得分

以直属上级评分为例，假设模板有 3 个指标：

| 指标 | 权重 | 上级打分 |
|------|------|---------|
| 业绩目标 | 40 | 85 |
| 团队协作 | 30 | 90 |
| 专业能力 | 30 | 80 |

**该评审角色的加权分** = Σ(指标分 × 指标权重) / Σ权重
= (85×40 + 90×30 + 80×30) / (40+30+30)
= (3400 + 2700 + 2400) / 100
= **85.0**

### 3.3 多角色加权汇总（totalScore）

当直属上级与虚线上级均完成评分后，按 **角色权重** 合成最终总分：

```
totalScore = manager_score × manager_weight + dotted_score × dotted_weight
```

- **权重来源**：优先从 `system_config` 表读取：
  - `manager_review_weight` — 直属上级权重（0~1 之间的小数，如 `0.7`）
  - `dotted_manager_review_weight` — 虚线上级权重（如 `0.3`）
- **未配置时**：两者各 **0.5**（均值）
- **无虚线上级时**：直属上级权重 = 1.0，直接取上级加权分

### 3.4 终审/校准

管理员在 `final_review` 阶段可：
- **批准**：确认总分 → `completed`
- **批准并校准**：覆写 `totalScore` 为校准分数 → `completed`
- **驳回**：指定回退阶段（默认回 `self_review`）

---

## 4. API 端点清单

| 方法 | 路径 | 说明 | 操作人 |
|------|------|------|--------|
| POST | `/api/performances` | 批量创建绩效 | 管理员 |
| GET | `/api/performances` | 绩效列表 | 所有角色 |
| GET | `/api/performances/:id` | 绩效详情 | 相关人 |
| POST | `/:id/select-template` | 选择模板 | 员工 |
| PATCH | `/:id` | 保存草稿 | 员工/上级 |
| POST | `/:id/submit` | 提交（目标/自评/评分） | 员工/上级 |
| POST | `/:id/approve-goal` | 审核目标 | 上级 |
| POST | `/:id/reject` | 驳回 | 上级 |
| POST | `/:id/final-review` | 终审 | 管理员 |
| POST | `/:id/calibrate` | 校准 | 管理员 |
| GET | `/api/session/me` | 当前登录用户（侧栏展示，账密/飞书共用） | 已登录 |
| POST | `/api/session/logout` | 清除会话 Cookie | 已登录 |

### 4.1 前端：「待选择模板」时如何选模板

在 **绩效列表** 中，状态为「待选择模板」时，点击 **「选择模板」** 或 **「详情」** 进入绩效详情页 `/performances/:id`。在详情页顶部流程中的 **「选择模板」** 区块里，选择一条已启用的模板并确认，状态会变为「目标设定中」。

---

## 5. 演示数据组织结构

```
管理部 (dept-default)
├── 演示经理 (demo_manager) — 经理，无登录密码限制（默认 123456）
│   └── 周荣 (zhou_rong) — 员工 + super_admin
│       ├── 直属上级: demo_manager
│       └── 虚线上级: (无)
└── 演示虚线上级 (demo_dotted_mgr) — 可选，测试虚线流程时使用
```

---

## 6. 典型测试场景：完整周期

| 步骤 | 登录身份 | 操作 | 预期状态变化 |
|------|---------|------|-------------|
| 1 | 周荣 (super_admin) | 创建绩效：选「周荣」，周期 2026-Q2 | → `template_selection` |
| 2 | 周荣 (employee) | 选择模板 | → `goal_setting` |
| 3 | 周荣 (employee) | 填写并提交目标 | → `goal_pending_review` |
| 4 | 演示经理 (manager) | 登录，审核批准目标 | → `self_review` |
| 5 | 周荣 (employee) | 提交自评 | → `manager_review` |
| 6 | 演示经理 (manager) | 提交上级评分 | → `final_review`（无虚线上级） |
| 7 | 周荣 (super_admin) | 终审批准 / 校准 | → `completed` |
