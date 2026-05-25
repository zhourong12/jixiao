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
  maxScore?: number;
  criteria?: string;
}

/** 绩效模板各指标权重(%)之和仅在「设定目标」阶段须与评分方案绩效占比一致；模板管理不限制合计值。 */
export const TEMPLATE_PERF_WEIGHT_TOTAL = 100;

/** 文化价值观模板：各维「满分」之和（默认 20 = 7+7+6）。 */
export const TEMPLATE_CULTURE_MAX_TOTAL = 20;

/** 模板 / 绩效记录快照上的文化价值观维度定义（名称须与评分 JSON 中 name 一致）。 */
export interface CultureDimensionDef {
  name: string;
  maxScore: number;
  description?: string;
  criteria?: string;
}

export interface PerformanceTemplate {
  id: string;
  name: string;
  type?: 'performance' | 'culture' | 'learning';
  position: string;
  indicators: PerformanceIndicator[];
  /** 文化价值观维度配置；缺省时后端使用内置默认三套 */
  cultureDimensions?: CultureDimensionDef[];
  /** 历史数据可能仍有模板侧规则；新模板不再绑定 */
  assessmentRuleId?: string | null;
  assessmentRuleName?: string;
  status: 'enabled' | 'disabled';
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface AssessmentRuleListItem {
  id: string;
  name: string;
  managerWeight: number;
  dottedManagerWeight: number;
  status: 'enabled' | 'disabled';
  createdAt: string;
  updatedAt: string;
}

export interface CreateAssessmentRuleRequest {
  name: string;
  /** 与 dottedManagerWeight 相加须为 1；允许一侧为 0（如 1.0 与 0） */
  managerWeight: number;
  dottedManagerWeight: number;
  status?: 'enabled' | 'disabled';
}

export interface UpdateAssessmentRuleRequest {
  name?: string;
  managerWeight?: number;
  dottedManagerWeight?: number;
  status?: 'enabled' | 'disabled';
}

export interface CreateTemplateRequest {
  name: string;
  position: string;
  indicators: PerformanceIndicator[];
  cultureDimensions?: CultureDimensionDef[];
  type?: 'performance' | 'culture' | 'learning';
}

export interface UpdateTemplateRequest {
  name?: string;
  position?: string;
  indicators?: PerformanceIndicator[];
  cultureDimensions?: CultureDimensionDef[];
  type?: 'performance' | 'culture' | 'learning';
}

export interface TemplateListItem {
  id: string;
  name: string;
  type?: 'performance' | 'culture' | 'learning';
  position: string;
  indicatorCount: number;
  /** 文化价值观维度条数（列表接口） */
  cultureDimensionCount?: number;
  assessmentRuleId?: string;
  assessmentRuleName?: string;
  status: 'enabled' | 'disabled';
  version: number;
  createdAt: string;
}

// ==================== 绩效记录模块 ====================

export type PerformanceStatus =
  | 'template_selection'     // 待选择模板（旧状态，已弃用）
  | 'goal_setting'           // 目标设定中（员工填写目标）
  | 'goal_pending_review'    // 待审核目标（仅直属上级审核）
  | 'goal_rejected'          // 目标被驳回
  | 'plan_execution'         // 计划执行中（目标审核通过后，评分截止前）
  | 'self_review'            // 自评中
  | 'manager_review'         // 直属上级评分中（无虚线上级时仅此阶段）
  | 'dual_manager_review'    // 直属+虚线上级并行评分（均有虚线上级时，双方均提交后进入校准）
  | 'dotted_manager_review'  // 虚线上级评分中（旧流程：直属已评完后的单独阶段）
  | 'final_review'           // 待终审（管理员审核/绩效校准）
  | 'issued'                 // 已下发，待员工确认
  | 'completed';             // 已完成（员工已确认）

export interface ReviewItem {
  indicatorName: string;
  score: number;
  comment: string;
}

export interface GoalSettingItem {
  indicatorName: string;
  target?: string;
  weight: number;
  criteria?: string;
}

export interface SaveGoalIndicatorsRequest {
  indicators: PerformanceIndicator[];
}

export interface CultureReviewItem {
  name: string;
  score: number;
  comment?: string;
}

export const CULTURE_VALUE_ITEMS = [
  { name: '利他', maxScore: 7, description: '1. 客户为先，不断完善产品与服务，为用户创造价值。\n2. 分享与互助，不断提升能力与效率。\n3. 让和你合作的人成功，相互成就。', criteria: '优秀(7分)，合格(4-6分)，不合格(0分)' },
  { name: '本分', maxScore: 7, description: '1. 以诚相待，高效沟通。\n2. 实事求是，说到做到。\n3. 敢于质疑，敢于挑战，抓住事物本质。\n4. 坚持自我批判，不断超越自我。', criteria: '优秀(7分)，合格(4-6分)，不合格(0分)' },
  { name: '结果导向', maxScore: 6, description: '1. 以结果来驱动行为，对结果负责。\n2. 不找借口，突破客观条件限制，整合资源，不惜一切达成结果。\n3. 关注团队结果，团队成功才有个人价值。', criteria: '优秀(6分)，合格(3-5分)，不合格(0分)' },
] as const;

export interface PerformanceRecord {
  id: string;
  employeeId: string;
  employeeName: string;
  /** 被考核人所属飞书主体（详情） */
  feishuSubjectCode?: string;
  feishuSubjectName?: string;
  templateId?: string;
  templateName: string;
  /** 绩效记录选用的考核规则（详情接口返回） */
  assessmentRuleId?: string | null;
  /** 考核规则名称（详情接口返回） */
  assessmentRuleName?: string;
  period: string;
  status: PerformanceStatus;
  managerId: string;
  managerName: string;
  dottedManagerId?: string;
  dottedManagerName?: string;
  goalSetting?: GoalSettingItem[];       // 员工设定的目标
  goalApprovedBy?: string;               // 目标审核人ID
  personalSummary?: string;              // 个人总结（自评阶段填写）
  managerSummary?: string;               // 直属上级评价（评分阶段填写）
  dottedManagerSummary?: string;         // 虚线上级评价（评分阶段填写）
  selfReview?: ReviewItem[];
  managerReview?: ReviewItem[];
  dottedManagerReview?: ReviewItem[];
  cultureSelfReview?: CultureReviewItem[];
  cultureManagerReview?: CultureReviewItem[];
  cultureDottedManagerReview?: CultureReviewItem[];
  /** 创建绩效时自模板快照；评分与展示均以此为准（模板后续修改不影响本条记录） */
  cultureDimensions?: CultureDimensionDef[];
  totalScore?: number;
  /** 按加权总分划档：>95 S，>90 A，>70 B，否则 C（有总分时写入） */
  scoreGrade?: string | null;
  /** 直属评分齐全时按模板权重的加权分（详情各角色可见） */
  managerWeightedTotal?: number;
  /** 虚线评分齐全时同上 */
  dottedManagerWeightedTotal?: number;
  rejectionReason?: string;
  finalReviewerId?: string;              // 终审人ID
  finalReviewedAt?: string;              // 终审时间
  /** 各节点截止日期（goal / plan_execution / scoring / final / confirm） */
  nodeDeadlines?: Record<string, string>;
  /** 创建该条绩效的管理员，默认负责校准 */
  calibrationOwnerId?: string;
  calibrationOwnerName?: string;
  /** 截止自动推进前的未完成节点，校准/计划执行回退时恢复 */
  deadlineFlowAnchor?: PerformanceStatus;
  /** 当前用户是否可执行校准（创建人或复审管理员） */
  canCalibrate?: boolean;
  /** 计划执行中：当前用户是否为该条绩效创建人（可下发员工自评） */
  canIssueSelfReview?: boolean;
  indicators?: PerformanceIndicator[];
  /** 有多级评审且当前用户可见评分数据时返回：按绩效记录上考核规则（或历史模板/系统配置回退）的角色权重 */
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
  learningDimensions?: PerformanceIndicator[];
  scoringSchemeId?: string;
  scoringWeights?: { performance: number; culture: number; learning: number };
  cultureTemplateId?: string;
  learningTemplateId?: string;
  learningSelfReview?: ReviewItem[];
  learningManagerReview?: ReviewItem[];
  learningDottedManagerReview?: ReviewItem[];
  createdAt: string;
  updatedAt: string;
}

/** 新建模板时默认文化维度（与内置三套一致）。 */
export function defaultCultureDimensions(): CultureDimensionDef[] {
  return CULTURE_VALUE_ITEMS.map((c) => ({
    name: c.name,
    maxScore: c.maxScore,
    description: c.description,
    criteria: c.criteria,
  }));
}

/** 详情页 / 评分：有快照（含空数组）时用快照；未返回该字段时回退默认三套。 */
export function recordCultureDimensions(rec: PerformanceRecord | null | undefined): CultureDimensionDef[] {
  const d = rec?.cultureDimensions;
  if (d !== undefined && d !== null) return d;
  return defaultCultureDimensions();
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
  templateId?: string;
  totalScore?: number;
  /** 按加权总分划档：>95 S，>90 A，>70 B，否则 C（有总分时写入） */
  scoreGrade?: string | null;
  createdAt: string;
  updatedAt: string;
  /** 计划执行中且当前用户为该条创建人时可下发员工自评 */
  canIssueSelfReview?: boolean;
}

export interface BatchIssueSelfReviewResponse {
  success: boolean;
  successCount: number;
  failed: Array<{ id: string; reason: string }>;
}

export interface PerformanceFilterParams {
  /** 单状态或多状态；请求时以英文逗号拼接 */
  status?: PerformanceStatus | PerformanceStatus[];
  period?: string;
  /** 飞书主体 code，与 departmentId 可组合筛选 */
  subjectCode?: string;
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
  canBatchCreate?: boolean;
  canBatchIssueSelfReview?: boolean;
  canExport?: boolean;
  canDelete?: boolean;
}

export interface SaveDraftRequest {
  reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
  content: ReviewItem[] | GoalSettingItem[];
  cultureContent?: CultureReviewItem[];
  learningContent?: CultureReviewItem[];
  personalSummary?: string;
  managerSummary?: string;
  dottedManagerSummary?: string;
}

export interface SubmitReviewRequest {
  reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
  content: ReviewItem[] | GoalSettingItem[];
  cultureContent?: CultureReviewItem[];
  learningContent?: CultureReviewItem[];
  personalSummary?: string;
  managerSummary?: string;
  dottedManagerSummary?: string;
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

export type PerformanceNodeDeadlineKey = 'goal' | 'plan_execution' | 'scoring' | 'final' | 'confirm';

export interface CreatePerformanceRequest {
  employeeIds?: string[];
  employeeNames?: string[];
  period: string;
  scoringSchemeId: string;
  nodeDeadlines?: Partial<Record<PerformanceNodeDeadlineKey, string>>;
  /** 已废弃：考核规则在员工档案中绑定；请求体可省略 */
  assessmentRuleId?: string;
  templateId?: string;
  departmentId?: string;
  departmentName?: string;
  subjectCode?: string;
}

export interface ScoringScheme {
  id: string;
  name: string;
  performanceWeight: number;
  cultureWeight: number;
  learningWeight: number;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ScoringSchemeListResponse {
  /** 列表；后端若只返回 `items`，由 listScoringSchemes 归一成此字段 */
  list: ScoringScheme[];
  total: number;
}

export interface CreateScoringSchemeRequest {
  name: string;
  performanceWeight: number;
  cultureWeight: number;
  learningWeight: number;
}

export interface UpdateScoringSchemeRequest {
  name?: string;
  performanceWeight?: number;
  cultureWeight?: number;
  learningWeight?: number;
  status?: string;
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
  /** 回退时更新目标节点截止时间，yyyy-MM-dd */
  deadline?: string;
}

export interface RejectPerformanceRequest {
  reason: string;
}

export interface AdminRejectSelfReviewRequest {
  recordId: string;
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
  scoreGrade?: string | null;
  selfReviewComment: string;
  managerReviewComment: string;
  dottedManagerReviewComment: string;
  updatedAt: string;
}

// ==================== 待办模块 ====================

export type TodoType =
  | 'template_selection'
  | 'goal_setting'
  | 'goal_rejected'
  | 'goal_pending_review'
  | 'self_review'
  | 'manager_review'
  | 'dual_manager_review'
  | 'dotted_manager_review'
  | 'plan_execution'
  | 'final_review'
  | 'issued';

export interface TodoItem {
  id: string;
  period: string;
  type: TodoType;
  title: string;
  employeeId?: string;
  employeeName?: string;
  departmentName?: string;
  deadline?: string;
}

/** 前端菜单权限 key，与侧栏路由一一对应 */
export type MenuPermissionKey =
  | 'todo'
  | 'performance_list'
  | 'performance_export'
  | 'performance_list_all'
  | 'performance_batch_create'
  | 'performance_review_admin'
  | 'admin_performance_calibration'
  | 'admin_performance_feishu_task'
  | 'my_performance'
  | 'admin_templates'
  | 'admin_assessment_rules'
  | 'admin_scoring_schemes'
  | 'admin_employees'
  | 'admin_departments'
  | 'admin_roles'
  | 'admin_permissions'
  | 'admin_statistics_months'
  | 'admin_api_tokens';

export const MENU_PERMISSION_KEYS: MenuPermissionKey[] = [
  'todo',
  'performance_list',
  'performance_export',
  'performance_list_all',
  'performance_batch_create',
  'performance_review_admin',
  'admin_performance_calibration',
  'admin_performance_feishu_task',
  'my_performance',
  'admin_templates',
  'admin_assessment_rules',
  'admin_scoring_schemes',
  'admin_employees',
  'admin_departments',
  'admin_roles',
  'admin_permissions',
  'admin_statistics_months',
  'admin_api_tokens',
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

export interface ApiTokenItem {
  id: number;
  name: string;
  createdAt?: string | null;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
}

export interface ApiTokenListResponse {
  items: ApiTokenItem[];
}

export interface CreateApiTokenRequest {
  name: string;
  expiresAt?: string | null;
}

export interface CreateApiTokenResponse {
  success: boolean;
  token: string;
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

/** 飞书绩效待办节点截止天数配置 */
export interface PerformanceFeishuTaskNodeConfigItem {
  nodeKey: string;
  name: string;
  dueDays: number;
  sortOrder: number;
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
  periodKey?: string;
  periodName?: string;
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

export interface DepartmentTreeSubject {
  subjectCode: string;
  subjectName: string;
  departments: DepartmentOption[];
}

export interface OrgDepartmentListItem {
  id: string;
  name: string;
  larkDepartmentId?: string | null;
  sortOrder?: number;
  subjectCode: string;
  subjectName: string;
  employeeCount: number;
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

export interface QuarterLeaderboardMonthlyItem {
  periodKey: string;
  periodName: string;
  recordId?: string;
  totalScore?: number;
  status?: string;
}

export interface QuarterLeaderboardDetailResponse {
  quarterKey: string;
  employeeId: string;
  employeeName: string;
  quarterRecordId?: string;
  quarterTotalScore?: number;
  quarterStatus?: string;
  monthlyItems: QuarterLeaderboardMonthlyItem[];
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
  /** 飞书主体 code；发送前必选，收件人仅含该主体下员工 */
  subjectCode?: string;
}

export interface SendNotificationResponse {
  success: boolean;
  notificationId: string;
  /** 飞书私聊送达成功人数（与 notification.read_count 一致） */
  feishuSent?: number;
  /** 飞书私聊未送达人数 */
  feishuFailed?: number;
  message?: string;
}

// ==================== 员工管理模块 ====================

export interface EmployeeListItem {
  id: string;
  userId: string;
  name: string;
  feishuSubjectId?: string;
  feishuOpenId?: string;
  feishuSubjectCode?: string;
  feishuSubjectName?: string;
  avatar?: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  departmentId?: string | null;
  departmentName?: string;
  managerId?: string;
  managerName?: string;
  dottedManagerId?: string;
  dottedManagerName?: string;
  /** 与菜单权限一致的主角色：super_admin > admin > 无记录视为 employee */
  roleKey?: string;
  roleName?: string;
  /** 批量创建绩效时使用的考核规则（员工档案绑定） */
  assessmentRuleId?: string | null;
  assessmentRuleName?: string | null;
}

/** GET /api/employees/all 列表项（选人、上级下拉等；不传 subjectCode 时为全部主体） */
export type EmployeeDirectoryListItem = Pick<
  EmployeeListItem,
  | "userId"
  | "name"
  | "departmentId"
  | "departmentName"
  | "feishuSubjectCode"
  | "feishuSubjectName"
  | "assessmentRuleId"
  | "assessmentRuleName"
>;

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
  departmentId?: string;
  userId: string;
  name: string;
  /** 新建员工时绑定飞书主体（英文 code，与库 feishu_subject.code 一致） */
  feishuSubjectCode?: string;
  feishuSubjectId?: string;
  /** 飞书 open_id；与 userId 一致时可只填 userId */
  feishuOpenId?: string;
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
  /** 考核规则（启用中的规则 id）；不传则不在档案中绑定 */
  assessmentRuleId?: string;
}

export interface BatchUpdateEmployeeAssessmentRuleRequest {
  employeeIds: string[];
  /** 传空字符串可清空绑定 */
  assessmentRuleId?: string;
}

export interface BatchUpdateEmployeeAssessmentRuleResponse {
  success: boolean;
  updatedCount: number;
}

export interface UpdateEmployeeRequest {
  departmentId?: string;
  name?: string;
  feishuSubjectCode?: string;
  feishuSubjectId?: string;
  feishuOpenId?: string;
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
  /** 考核规则；传空字符串可清空员工档案中的绑定 */
  assessmentRuleId?: string;
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
  /** 飞书主体 code */
  subjectCode: string;
}

export interface FeishuSubjectOption {
  code: string;
  name: string;
}

export interface FeishuUserOptionItem {
  feishuOpenId: string;
  name: string;
  feishuSubjectCode: string;
  /** 飞书 employee_no，可能为空 */
  employeeNo?: string;
  /** 用户首次出现所在部门名称（按部门遍历顺序） */
  departmentName?: string;
}

/** GET /api/employees/feishu-user-options */
export interface FeishuUserOptionsResponse {
  items: FeishuUserOptionItem[];
  total?: number;
  truncated?: boolean;
}

/** GET /api/employees/feishu-user-profile */
export interface FeishuUserProfileResponse {
  employeeNo: string;
  departmentName: string;
  mobile: string;
}

export interface SyncEmployeesResponse {
  success: boolean;
  syncedCount: number;
  totalCount?: number;     // 从飞书获取的总数
  validCount?: number;     // 过滤后有user_id的有效用户数
  skippedCount?: number;   // 已存在跳过的数量
  message?: string;
}

export interface SyncFeishuSubjectResult {
  subjectCode: string;
  subjectName?: string;
  success: boolean;
  createdCount: number;
  createdNames?: string[];
  updatedCount: number;
  failedCount: number;
  /** 通讯录有但未分配飞书应用席位、未同步 */
  skippedNoSeatCount?: number;
  deletedCount?: number;
  reconciledManagerCount?: number;
  totalCount?: number;
  message?: string;
}

export interface SyncFeishuEmployeesResponse {
  success: boolean;
  createdCount: number;
  createdNames?: string[];
  updatedCount: number;
  failedCount: number;
  /** 通讯录有但未分配飞书应用席位、未同步 */
  skippedNoSeatCount?: number;
  deletedCount?: number;
  reconciledManagerCount?: number;
  subjects: SyncFeishuSubjectResult[];
  message?: string;
}

// ==================== 管理员配置模块 ====================

export interface AdminConfig {
  userId: string;
  role: UserRole;
  createdAt: string;
}
