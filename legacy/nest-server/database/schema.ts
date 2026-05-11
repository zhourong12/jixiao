import { randomUUID } from 'node:crypto';
import {
  mysqlTable,
  varchar,
  json,
  int,
  text,
  datetime,
  decimal,
  date,
  index,
  uniqueIndex,
  primaryKey,
} from 'drizzle-orm/mysql-core';
import { sql } from 'drizzle-orm';

/** 系统级配置（如飞书应用凭证），优先于代码中的默认值 */
export const systemConfig = mysqlTable('system_config', {
  configKey: varchar('config_key', { length: 64 }).primaryKey().notNull(),
  configValue: text('config_value').notNull(),
  updatedAt: datetime('_updated_at', { mode: 'date' })
    .default(sql`CURRENT_TIMESTAMP`)
    .notNull(),
});

export const performanceTemplate = mysqlTable(
  'performance_template',
  {
    id: varchar('id', { length: 36 }).primaryKey().$defaultFn(() => randomUUID()),
    name: varchar('name', { length: 255 }).notNull(),
    position: varchar('position', { length: 255 }).notNull(),
    indicators: json('indicators').notNull(),
    status: varchar('status', { length: 50 }).default('enabled'),
    version: int('version').default(1),
    createdAt: datetime('_created_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    createdBy: varchar('_created_by', { length: 255 }),
    updatedAt: datetime('_updated_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    updatedBy: varchar('_updated_by', { length: 255 }),
  },
  (table) => [index('idx_performance_template_status').on(table.status)],
);

export const notification = mysqlTable(
  'notification',
  {
    id: varchar('id', { length: 36 }).primaryKey().$defaultFn(() => randomUUID()),
    title: varchar('title', { length: 255 }).notNull(),
    content: text('content').notNull(),
    sendType: varchar('send_type', { length: 50 }).notNull(),
    targetIds: json('target_ids').notNull(),
    senderId: varchar('sender_id', { length: 255 }).notNull(),
    readCount: int('read_count').default(0),
    totalCount: int('total_count').default(0),
    createdAt: datetime('_created_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    createdBy: varchar('_created_by', { length: 255 }),
    updatedAt: datetime('_updated_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    updatedBy: varchar('_updated_by', { length: 255 }),
  },
  (table) => [index('idx_notification_sender').on(table.senderId)],
);

export const performanceRecord = mysqlTable(
  'performance_record',
  {
    id: varchar('id', { length: 36 }).primaryKey().$defaultFn(() => randomUUID()),
    employeeId: varchar('employee_id', { length: 255 }).notNull(),
    templateId: varchar('template_id', { length: 36 }),
    period: varchar('period', { length: 50 }).notNull(),
    status: varchar('status', { length: 50 }).default('template_selection'),
    managerId: varchar('manager_id', { length: 255 }).notNull(),
    dottedManagerId: varchar('dotted_manager_id', { length: 255 }),
    selfReview: json('self_review'),
    managerReview: json('manager_review'),
    dottedManagerReview: json('dotted_manager_review'),
    totalScore: decimal('total_score', { precision: 5, scale: 2 }),
    rejectionReason: text('rejection_reason'),
    createdAt: datetime('_created_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    createdBy: varchar('_created_by', { length: 255 }),
    updatedAt: datetime('_updated_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    updatedBy: varchar('_updated_by', { length: 255 }),
    goalSetting: json('goal_setting'),
    goalApprovedBy: varchar('goal_approved_by', { length: 255 }),
    personalSummary: text('personal_summary'),
    finalReviewerId: varchar('final_reviewer_id', { length: 255 }),
    finalReviewedAt: datetime('final_reviewed_at', { mode: 'date' }),
  },
  (table) => [
    index('idx_performance_record_employee').on(table.employeeId),
    index('idx_performance_record_manager').on(table.managerId),
    index('idx_performance_record_period').on(table.period),
    index('idx_performance_record_status').on(table.status),
  ],
);

/** 菜单定义（与侧栏 menuKey 一致） */
export const rbacMenu = mysqlTable('menu', {
  menuKey: varchar('menu_key', { length: 64 }).primaryKey().notNull(),
  name: varchar('name', { length: 128 }).notNull(),
  sortOrder: int('sort_order').notNull().default(0),
});

/** 角色定义 */
export const rbacRole = mysqlTable('role', {
  roleKey: varchar('role_key', { length: 50 }).primaryKey().notNull(),
  name: varchar('name', { length: 100 }).notNull(),
  isSystem: int('is_system').notNull().default(1),
  sortOrder: int('sort_order').notNull().default(0),
});

/** 用户—角色（多对多） */
export const rbacUserRole = mysqlTable(
  'user_role',
  {
    userId: varchar('user_id', { length: 255 }).notNull(),
    roleKey: varchar('role_key', { length: 50 }).notNull(),
  },
  (t) => [primaryKey({ columns: [t.userId, t.roleKey] })],
);

/** 角色—菜单授权 */
export const rbacRoleMenu = mysqlTable(
  'role_menu',
  {
    roleKey: varchar('role_key', { length: 50 }).notNull(),
    menuKey: varchar('menu_key', { length: 64 }).notNull(),
    allowed: int('allowed').notNull().default(1),
  },
  (t) => [primaryKey({ columns: [t.roleKey, t.menuKey] })],
);

/** 评选周期：自然月或绩效季度 */
export const evaluationPeriod = mysqlTable(
  'evaluation_period',
  {
    id: varchar('id', { length: 36 }).primaryKey().$defaultFn(() => randomUUID()),
    periodType: varchar('period_type', { length: 16 }).notNull(),
    periodKey: varchar('period_key', { length: 16 }).notNull(),
    name: varchar('name', { length: 255 }).notNull().default(''),
    sortOrder: int('sort_order').notNull().default(0),
    status: varchar('status', { length: 32 }).notNull().default('open'),
    /** 月度周期归属的季度周期 id，季度行留空 */
    parentPeriodId: varchar('parent_period_id', { length: 36 }),
    createdAt: datetime('_created_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    updatedAt: datetime('_updated_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
  },
  (t) => [
    uniqueIndex('uq_evaluation_period_type_key').on(t.periodType, t.periodKey),
    index('idx_evaluation_period_parent').on(t.parentPeriodId),
  ],
);

/** 奖项类型字典 */
export const awardType = mysqlTable('award_type', {
  code: varchar('code', { length: 64 }).primaryKey().notNull(),
  name: varchar('name', { length: 128 }).notNull(),
  scope: varchar('scope', { length: 16 }).notNull(),
  maxWinners: int('max_winners'),
  sortOrder: int('sort_order').notNull().default(0),
  isSystem: int('is_system').notNull().default(1),
});

/** 某周期下的获奖记录 */
export const periodAward = mysqlTable(
  'period_award',
  {
    id: varchar('id', { length: 36 }).primaryKey().$defaultFn(() => randomUUID()),
    periodId: varchar('period_id', { length: 36 }).notNull(),
    awardCode: varchar('award_code', { length: 64 }).notNull(),
    employeeId: varchar('employee_id', { length: 255 }).notNull(),
    performanceRecordId: varchar('performance_record_id', { length: 36 }),
    remark: text('remark'),
    createdBy: varchar('created_by', { length: 255 }),
    createdAt: datetime('_created_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
  },
  (t) => [
    uniqueIndex('uq_period_award_period_award_emp').on(t.periodId, t.awardCode, t.employeeId),
    index('idx_period_award_period').on(t.periodId),
  ],
);

export const employeeHierarchy = mysqlTable(
  'employee_hierarchy',
  {
    id: varchar('id', { length: 36 }).primaryKey().$defaultFn(() => randomUUID()),
    employeeId: varchar('employee_id', { length: 255 }).notNull(),
    managerId: varchar('manager_id', { length: 255 }),
    dottedManagerId: varchar('dotted_manager_id', { length: 255 }),
    departmentId: varchar('department_id', { length: 255 }),
    departmentName: varchar('department_name', { length: 255 }),
    createdAt: datetime('_created_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    createdBy: varchar('_created_by', { length: 255 }),
    updatedAt: datetime('_updated_at', { mode: 'date' })
      .default(sql`CURRENT_TIMESTAMP`)
      .notNull(),
    updatedBy: varchar('_updated_by', { length: 255 }),
    name: varchar('name', { length: 100 }),
    phone: varchar('phone', { length: 20 }),
    employeeNo: varchar('employee_no', { length: 50 }),
    employeeType: varchar('employee_type', { length: 50 }),
    position: varchar('position', { length: 100 }),
    workLocation: varchar('work_location', { length: 100 }),
    joinDate: date('join_date', { mode: 'string' }),
  },
  (table) => [
    index('idx_employee_hierarchy_department_id').on(table.departmentId),
    index('idx_employee_hierarchy_dotted_manager_id').on(table.dottedManagerId),
    uniqueIndex('idx_employee_hierarchy_employee_id').on(table.employeeId),
    index('idx_employee_hierarchy_manager_id').on(table.managerId),
  ],
);

export const systemConfigTable = systemConfig;
export const employeeHierarchyTable = employeeHierarchy;
export const notificationTable = notification;
export const performanceRecordTable = performanceRecord;
export const performanceTemplateTable = performanceTemplate;
export const evaluationPeriodTable = evaluationPeriod;
export const awardTypeTable = awardType;
export const periodAwardTable = periodAward;
