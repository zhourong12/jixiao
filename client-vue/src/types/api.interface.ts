/**
 * 前后端共享的类型定义
 * 绩效管理系统 API 接口类型
 */

// ==================== 通用类型 ====================

export type UserRole = 'super_admin' | 'admin' | 'employee';

export interface UserInfo {
  userId: string;
  name: string;
  avatar?: string;
}

export interface PaginationParams {
  page: number;
  pageSize: number;
}

export interface PaginationResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

// ==================== 绩效模板模块 ====================

export interface PerformanceIndicator {
  name: string;
  weight: number;
  description: string;
}

export interface PerformanceTemplate {
  id: string;
  name: string;
  position: string;
  indicators: PerformanceIndicator[];
  status: 'enabled' | 'disabled';
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  position: string;
  indicators: PerformanceIndicator[];
}

export interface UpdateTemplateRequest {
  name?: string;
  position?: string;
  indicators?: PerformanceIndicator[];
}

export interface TemplateListItem {
  id: string;
  name: string;
  position: string;
  indicatorCount: number;
  status: 'enabled' | 'disabled';
  version: number;
  createdAt: string;
}

// ==================== 绩效记录模块 ====================

export type PerformanceStatus =
  | 'template_selection'     // 待选择模板
  | 'goal_setting'           // 目标设定中（员工填写目标）
  | 'goal_pending_review'    // 待审核目标（上级/虚线上级审核）
  | 'goal_rejected'          // 目标被驳回
  | 'self_review'            // 自评中
  | 'manager_review'         // 直属上级评分中（无虚线上级时仅此阶段）
  | 'dual_manager_review'    // 直属+虚线上级并行评分（均有虚线上级时，双方均提交后进入校准）
  | 'dotted_manager_review'  // 虚线上级评分中（旧流程：直属已评完后的单独阶段）
  | 'final_review'           // 待终审（管理员审核/绩效校准）
  | 'completed';             // 已完成（已下发）

export interface ReviewItem {
  indicatorName: string;
  score: number;
  comment: string;
}

export interface GoalSettingItem {
  indicatorName: string;
  target?: string;
  weight: number;
}

export interface PerformanceRecord {
  id: string;
  employeeId: string;
  employeeName: string;
  templateId: string;
  templateName: string;
  period: string;
  status: PerformanceStatus;
  managerId: string;
  managerName: string;
  dottedManagerId?: string;
  dottedManagerName?: string;
  goalSetting?: GoalSettingItem[];       // 员工设定的目标
  goalApprovedBy?: string;               // 目标审核人ID
  personalSummary?: string;              // 个人总结（自评阶段填写）
  selfReview?: ReviewItem[];
  managerReview?: ReviewItem[];
  dottedManagerReview?: ReviewItem[];
  totalScore?: number;
  /** 直属评分齐全时按模板权重的加权分（详情各角色可见） */
  managerWeightedTotal?: number;
  /** 虚线评分齐全时同上 */
  dottedManagerWeightedTotal?: number;
  rejectionReason?: string;
  finalReviewerId?: string;              // 终审人ID
  finalReviewedAt?: string;              // 终审时间
  indicators?: Array<{ name: string; weight: number; description: string }>;
  /** 有多级评审且当前用户可见评分数据时返回：系统配置中的角色权重 */
  reviewRoleWeights?: { managerWeight: number; dottedWeight: number };
  /** 各指标上直属分、虚线分及按角色权重线性合成后的分项分（仅两侧都有分时 mergedScore 有值） */
  reviewMergedIndicators?: Array<{
    indicatorName: string;
    managerScore?: number;
    dottedScore?: number;
    mergedScore?: number;
  }>;
  /** 双方评审均完成时，与 totalScore 一致的合成总分预览 */
  reviewMergedTotal?: number;
  createdAt: string;
  updatedAt: string;
}

export interface PerformanceListItem {
  id: string;
  employeeId: string;
  employeeName: string;
  period: string;
  status: PerformanceStatus;
  managerId: string;
  managerName: string;
  /** 虚线上级（列表扩展字段，如校准队列） */
  dottedManagerId?: string;
  dottedManagerName?: string;
  totalScore?: number;
  createdAt: string;
  updatedAt: string;
}

export interface PerformanceFilterParams {
  status?: PerformanceStatus;
  period?: string;
  departmentId?: string;
  employeeName?: string;
}

export interface PerformanceListParams extends PerformanceFilterParams {
  page: number;
  pageSize: number;
}

export interface PerformanceListResponse {
  items: PerformanceListItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SaveDraftRequest {
  reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
  content: ReviewItem[] | GoalSettingItem[];
  personalSummary?: string;
}

export interface SubmitReviewRequest {
  reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
  content: ReviewItem[] | GoalSettingItem[];
  personalSummary?: string;
}

export interface ApproveGoalRequest {
  approved: boolean;           // true: 批准, false: 驳回
  rejectionReason?: string;    // 驳回原因
}

export interface FinalReviewRequest {
  approved: boolean;           // true: 批准下发, false: 驳回
  rejectionReason?: string;    // 驳回原因
  returnToStage?: PerformanceStatus; // 驳回到哪个阶段
}

export interface CreatePerformanceRequest {
  employeeIds?: string[];      // 员工ID列表（支持批量，与 employeeNames 二选一）
  employeeNames?: string[];    // 员工姓名列表（支持批量，与 employeeIds 二选一）
  period: string;              // 考核周期，月度为 YYYY-MM（与 evaluation_period 月度 key 一致）
  departmentId?: string;       // 部门ID（可选，已废弃，请使用 departmentName）
  departmentName?: string;     // 部门名称（可选，选择部门则包含该部门下所有员工）
}

export interface CreatePerformanceResult {
  employeeId: string;
  employeeName?: string;
  success: boolean;
  id?: string;
  error?: string;
}

export interface CreatePerformanceResponse {
  results: CreatePerformanceResult[];
  total: number;
  successCount: number;
  failCount: number;
}

export interface SelectTemplateRequest {
  templateId: string;
}

export interface CalibrationReviewRequest {
  approved: boolean;
  finalScore?: number;
  rejectionReason?: string;
  returnToStage?: PerformanceStatus;
}

export interface RejectPerformanceRequest {
  reason: string;
}

export interface SubmitReviewResponse {
  success: boolean;
  newStatus: PerformanceStatus;
}

export interface PerformanceExportItem {
  employeeName: string;
  department: string;
  period: string;
  status: string;
  totalScore: number;
  selfReviewComment: string;
  managerReviewComment: string;
  dottedManagerReviewComment: string;
  updatedAt: string;
}

// ==================== 首页/工作台模块 ====================

export type TodoType =
  | 'template_selection'
  | 'goal_setting'
  | 'goal_rejected'
  | 'goal_pending_review'
  | 'self_review'
  | 'manager_review'
  | 'dual_manager_review'
  | 'dotted_manager_review'
  | 'final_review';

export interface TodoItem {
  id: string;
  period: string;
  type: TodoType;
  title: string;
  deadline?: string;
}

export interface PerformanceOverview {
  total: number;
  pending: number;
  completed: number;
  rejected: number;
  /** 概览统计的自然月（与查询参数一致，默认当月） */
  year?: number;
  month?: number;
}

/** 工作台快捷操作数量（与当前用户职责相关） */
export interface HomeActionCounts {
  /** 待我评分：直属上级评分 + 虚线上级评分（且当前用户为对应评分人） */
  needScore: number;
  /** 待我审核目标：目标待审核且我为直属或虚线上级 */
  needApproveGoal: number;
  /** 待终审（仅管理员角色有值） */
  needFinalReview: number;
}

/** 前端菜单权限 key，与侧栏路由一一对应 */
export type MenuPermissionKey =
  | 'todo'
  | 'home'
  | 'performance_list'
  | 'performance_export'
  | 'performance_list_all'
  | 'performance_batch_create'
  | 'performance_review_admin'
  | 'admin_performance_calibration'
  | 'my_performance'
  | 'admin_templates'
  | 'admin_notifications'
  | 'admin_employees'
  | 'admin_roles'
  | 'admin_permissions'
  | 'admin_statistics_months'
  | 'admin_system_config';

export const MENU_PERMISSION_KEYS: MenuPermissionKey[] = [
  'todo',
  'home',
  'performance_list',
  'performance_export',
  'performance_list_all',
  'performance_batch_create',
  'performance_review_admin',
  'admin_performance_calibration',
  'my_performance',
  'admin_templates',
  'admin_notifications',
  'admin_employees',
  'admin_roles',
  'admin_permissions',
  'admin_statistics_months',
  'admin_system_config',
];

export interface MenuPermissionsMeResponse {
  /** 主角色（用于展示；权限以 menus 为准） */
  role: string;
  /** 用户绑定的全部角色 key（来自 user_role） */
  roles: string[];
  menus: Record<MenuPermissionKey, boolean>;
}

export interface MenuPermissionMatrixResponse {
  roles: string[];
  /** 与 roles 顺序一致 */
  roleInfos: Array<{ roleKey: string; name: string; isSystem: boolean }>;
  menus: MenuPermissionKey[];
  matrix: Record<string, Partial<Record<MenuPermissionKey, boolean>>>;
}

export interface RbacRoleItem {
  roleKey: string;
  name: string;
  isSystem: boolean;
  sortOrder: number;
}

export interface CreateRbacRoleRequest {
  roleKey: string;
  name: string;
  sortOrder?: number;
}

export interface UpdateRbacRoleRequest {
  name?: string;
  sortOrder?: number;
}

export interface UpdateMenuPermissionsBody {
  /** 被配置的角色，如 employee / admin / super_admin */
  role: string;
  menus: Partial<Record<MenuPermissionKey, boolean>>;
}

// ==================== 周期与评选 ====================

export type EvaluationPeriodType = 'month' | 'quarter';

export interface EvaluationPeriodItem {
  id: string;
  periodType: EvaluationPeriodType;
  /** 自然月 YYYY-MM 或绩效季度 YYYY-Q1～Q4 */
  periodKey: string;
  name: string;
  sortOrder: number;
  status: string;
  /** 仅月度：归属的季度周期 id */
  parentPeriodId?: string | null;
  /** 仅月度：归属季度的 period_key（列表接口联表返回） */
  parentPeriodKey?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEvaluationPeriodRequest {
  periodType: EvaluationPeriodType;
  periodKey: string;
  name?: string;
  sortOrder?: number;
  status?: string;
  /** 仅月度：填写季度周期 id */
  parentPeriodId?: string | null;
}

export interface UpdateEvaluationPeriodRequest {
  periodType?: EvaluationPeriodType;
  periodKey?: string;
  name?: string;
  sortOrder?: number;
  status?: string;
  parentPeriodId?: string | null;
}

export type AwardTypeScope = 'month' | 'quarter' | 'both';

export interface AwardTypeItem {
  code: string;
  name: string;
  scope: AwardTypeScope;
  maxWinners: number | null;
  sortOrder: number;
  isSystem: boolean;
}

export interface PeriodAwardItem {
  id: string;
  periodId: string;
  awardCode: string;
  awardName: string;
  employeeId: string;
  employeeName: string | null;
  performanceRecordId: string | null;
  remark: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface CreatePeriodAwardRequest {
  periodId: string;
  awardCode: string;
  employeeId: string;
  performanceRecordId?: string;
  remark?: string;
}

export interface DepartmentOption {
  id: string;
  name: string | null;
}

export interface PerformanceLeaderboardItem {
  rank: number;
  employeeId: string;
  employeeName: string;
  departmentId: string | null;
  departmentName: string | null;
  totalScore: number;
  performancePeriod: string;
  recordId: string;
}

export interface PerformanceLeaderboardResponse {
  items: PerformanceLeaderboardItem[];
  scope: 'month' | 'quarter';
  /** 月度为 YYYY-MM，季度为 YYYY-Qn */
  key: string;
}

// ==================== 通知模块 ====================

export type SendType = 'all' | 'department' | 'specified';

export interface Notification {
  id: string;
  title: string;
  content: string;
  sendType: SendType;
  targetIds: string[];
  senderId: string;
  senderName: string;
  sentAt: string;
  readCount: number;
  totalCount: number;
}

export interface NotificationListItem {
  id: string;
  title: string;
  sendType: SendType;
  sendTime: string;
  senderName: string;
  readCount: number;
  totalCount: number;
}

export interface SendNotificationRequest {
  title: string;
  content: string;
  sendType: SendType;
  targetIds: string[];
}

export interface SendNotificationResponse {
  success: boolean;
  notificationId: string;
}

// ==================== 员工管理模块 ====================

export interface EmployeeListItem {
  id: string;
  userId: string;
  name: string;
  avatar?: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  departmentName?: string;
  managerId?: string;
  managerName?: string;
  dottedManagerId?: string;
  dottedManagerName?: string;
  /** 与菜单权限一致的主角色：super_admin > admin > 无记录视为 employee */
  roleKey?: string;
  roleName?: string;
}

export interface EmployeeHierarchy {
  employeeId: string;
  managerId?: string;
  dottedManagerId?: string;
  departmentId?: string;
  departmentName?: string;
}

export interface UpdateEmployeeHierarchyRequest {
  managerId?: string;
  dottedManagerId?: string;
  departmentId?: string;
  departmentName?: string;
}

export interface CreateEmployeeRequest {
  userId: string;
  name: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  departmentName?: string;
  managerId?: string;
  dottedManagerId?: string;
  /** 系统角色，仅管理员可传；不传则不在 user_role 建记录（与未分配员工一致） */
  roleKey?: string;
}

export interface UpdateEmployeeRequest {
  name?: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  departmentName?: string;
  managerId?: string;
  dottedManagerId?: string;
  /** 系统角色，仅管理员可改；传空字符串可清空 user_role（恢复为默认员工） */
  roleKey?: string;
}

export interface EmployeeListParams {
  page: number;
  pageSize: number;
  keyword?: string;
}

export interface EmployeeListResponse {
  items: EmployeeListItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface EmployeeRoleOption {
  roleKey: string;
  name: string;
}

export interface SyncEmployeesRequest {
  clearExisting: boolean;
}

export interface SyncEmployeesResponse {
  success: boolean;
  syncedCount: number;
  totalCount?: number;     // 从飞书获取的总数
  validCount?: number;     // 过滤后有user_id的有效用户数
  skippedCount?: number;   // 已存在跳过的数量
  message?: string;
}

// ==================== 管理员配置模块 ====================

export interface AdminConfig {
  userId: string;
  role: UserRole;
  createdAt: string;
}
