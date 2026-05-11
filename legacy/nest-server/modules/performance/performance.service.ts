import { Injectable, NotFoundException, ForbiddenException, Logger } from '@nestjs/common';
import { Inject } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { eq, and, or, desc, asc, countDistinct, like, inArray, isNotNull, sql, type SQL } from 'drizzle-orm';
import { alias } from 'drizzle-orm/mysql-core';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import {
  performanceRecord,
  performanceTemplate,
  employeeHierarchy,
  systemConfig,
  evaluationPeriod,
} from '@server/database/schema';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';
import type { ReviewItem, PerformanceStatus, GoalSettingItem, PerformanceIndicator } from '@shared/api.interface';

interface ListParams {
  status?: string;
  /** 工作台快捷筛选：need_score | need_approve_goal（与 status 互斥，优先 focus） */
  focus?: string;
  period?: string;
  departmentId?: string;
  employeeName?: string;
  page: number;
  pageSize: number;
}

@Injectable()
export class PerformanceService {
  private readonly logger = new Logger(PerformanceService.name);

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly menuPermissionService: MenuPermissionService,
  ) {}

  /** 权限以 user_role + role 为准，不信任请求体或客户端传入的角色列表 */
  private async resolveRolesForUser(userId: string | undefined): Promise<string[]> {
    if (!userId) return [];
    try {
      const keys = await this.menuPermissionService.getRoleKeysForUser(userId);
      return keys.length ? keys : ['employee'];
    } catch (error) {
      this.logger.error('获取用户角色失败', JSON.stringify(error));
      return [];
    }
  }

  async getUserRole(userId: string | undefined): Promise<string> {
    if (!userId) return 'employee';
    try {
      return this.menuPermissionService.getUserRole(userId);
    } catch (error) {
      this.logger.error('获取用户角色失败', JSON.stringify(error));
      return 'employee';
    }
  }

  private async performanceMenuFlags(userId: string): Promise<{
    listAll: boolean;
    batchCreate: boolean;
    reviewAdmin: boolean;
    exportData: boolean;
  }> {
    const { menus } = await this.menuPermissionService.getEffectiveMenusForUser(userId);
    return {
      listAll: menus.performance_list_all === true,
      batchCreate: menus.performance_batch_create === true,
      reviewAdmin: menus.performance_review_admin === true,
      exportData: menus.performance_export === true,
    };
  }

  /**
   * 从 system_config 读取评审角色权重。
   * key: manager_review_weight / dotted_manager_review_weight，值为 0~1 的小数。
   * 未配置时返回 undefined，调用方按均值处理。
   */
  private async getReviewWeights(): Promise<{ managerWeight?: number; dottedWeight?: number }> {
    try {
      const rows = await this.db
        .select({ configKey: systemConfig.configKey, configValue: systemConfig.configValue })
        .from(systemConfig)
        .where(
          or(
            eq(systemConfig.configKey, 'manager_review_weight'),
            eq(systemConfig.configKey, 'dotted_manager_review_weight'),
          ),
        );
      let managerWeight: number | undefined;
      let dottedWeight: number | undefined;
      for (const r of rows) {
        const v = parseFloat(r.configValue);
        if (Number.isFinite(v)) {
          if (r.configKey === 'manager_review_weight') managerWeight = v;
          if (r.configKey === 'dotted_manager_review_weight') dottedWeight = v;
        }
      }
      return { managerWeight, dottedWeight };
    } catch {
      return {};
    }
  }

  /**
   * 按模板指标权重计算单次评审的加权得分。
   * 如果找不到模板或指标权重全为 0，退化为简单求均值。
   */
  private calcWeightedScore(review: ReviewItem[], indicators: PerformanceIndicator[]): number {
    if (!review.length) return 0;

    const weightMap = new Map<string, number>();
    for (const ind of indicators) {
      weightMap.set(ind.name, ind.weight);
    }

    let totalWeightedScore = 0;
    let totalWeight = 0;
    for (const item of review) {
      const w = weightMap.get(item.indicatorName) ?? 0;
      totalWeightedScore += (item.score || 0) * w;
      totalWeight += w;
    }

    if (totalWeight > 0) return totalWeightedScore / totalWeight;
    return review.reduce((s, i) => s + (i.score || 0), 0) / review.length;
  }

  /**
   * 获取绩效记录关联模板的指标列表。
   */
  private async getTemplateIndicators(templateId: string | null): Promise<PerformanceIndicator[]> {
    if (!templateId) return [];
    const rows = await this.db
      .select({ indicators: performanceTemplate.indicators })
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, templateId))
      .limit(1);
    return (rows[0]?.indicators as PerformanceIndicator[]) || [];
  }

  /**
   * 综合计算 totalScore：
   * - 只有上级评分时: totalScore = 上级加权分
   * - 同时有虚线上级时: totalScore = m_weight × 上级分 + d_weight × 虚线分
   *   权重从 system_config 取，取不到按均值（各 0.5）
   */
  private async computeTotalScore(
    templateId: string | null,
    managerReview: ReviewItem[] | null,
    dottedManagerReview: ReviewItem[] | null,
    hasDottedManager: boolean,
  ): Promise<number> {
    const indicators = await this.getTemplateIndicators(templateId);

    const mScore = managerReview ? this.calcWeightedScore(managerReview, indicators) : 0;

    if (!hasDottedManager || !dottedManagerReview) {
      return Math.round(mScore * 100) / 100;
    }

    const dScore = this.calcWeightedScore(dottedManagerReview, indicators);
    const { managerWeight, dottedWeight } = await this.getReviewWeights();

    let mW: number;
    let dW: number;
    if (managerWeight !== undefined && dottedWeight !== undefined) {
      mW = managerWeight;
      dW = dottedWeight;
    } else {
      mW = 0.5;
      dW = 0.5;
    }

    const total = mScore * mW + dScore * dW;
    return Math.round(total * 100) / 100;
  }

  /** 评审 JSON 是否已对模板内全部指标给出有效分数（含 0 分） */
  private isReviewComplete(
    review: ReviewItem[] | null | undefined,
    indicatorNames: string[],
  ): boolean {
    if (!review?.length || !indicatorNames.length) return false;
    const byName = new Map(review.map((i) => [i.indicatorName, i]));
    for (const name of indicatorNames) {
      const item = byName.get(name);
      if (item === undefined || typeof item.score !== 'number' || Number.isNaN(item.score)) {
        return false;
      }
    }
    return true;
  }

  private normalizeRoleWeights(managerWeight?: number, dottedWeight?: number): { mW: number; dW: number } {
    if (managerWeight !== undefined && dottedWeight !== undefined) {
      return { mW: managerWeight, dW: dottedWeight };
    }
    return { mW: 0.5, dW: 0.5 };
  }

  async list(userId: string | undefined, params: ListParams) {
    if (!userId) {
      return {
        items: [],
        total: 0,
        page: params.page,
        pageSize: params.pageSize,
        canBatchCreate: false,
        canExport: false,
      };
    }

    const userRoles = await this.resolveRolesForUser(userId);
    const { listAll, batchCreate, exportData } = await this.performanceMenuFlags(userId);

    const emp = alias(employeeHierarchy, 'perf_list_emp');
    const mgr = alias(employeeHierarchy, 'perf_list_mgr');

    const conditions: SQL[] = [];

    if (params.focus === 'need_score') {
      conditions.push(
        or(
          and(
            eq(performanceRecord.status, 'manager_review'),
            eq(performanceRecord.managerId, userId),
          ),
          and(
            eq(performanceRecord.status, 'dual_manager_review'),
            or(
              eq(performanceRecord.managerId, userId),
              eq(performanceRecord.dottedManagerId, userId),
            ),
          ),
          and(
            eq(performanceRecord.status, 'dotted_manager_review'),
            eq(performanceRecord.dottedManagerId, userId),
          ),
        )!,
      );
    } else if (params.focus === 'need_approve_goal') {
      conditions.push(
        and(
          eq(performanceRecord.status, 'goal_pending_review'),
          or(
            eq(performanceRecord.managerId, userId),
            eq(performanceRecord.dottedManagerId, userId),
          ),
        )!,
      );
    } else if (params.status) {
      conditions.push(eq(performanceRecord.status, params.status));
    }
    if (params.period) {
      conditions.push(eq(performanceRecord.period, params.period));
    }

    if (!listAll) {
      conditions.push(
        or(
          eq(performanceRecord.employeeId, userId),
          eq(performanceRecord.managerId, userId),
          eq(performanceRecord.dottedManagerId, userId),
        ),
      );
    }

    const deptId = params.departmentId?.trim();
    if (deptId && listAll) {
      conditions.push(eq(emp.departmentId, deptId));
    }

    const nameQ = params.employeeName?.trim();
    if (nameQ) {
      const safe = `%${nameQ.replace(/%/g, '').replace(/_/g, '')}%`;
      if (safe.length > 2) {
        conditions.push(like(emp.name, safe));
      }
    }

    const whereClause = conditions.length > 0 ? and(...conditions) : undefined;
    const offset = (params.page - 1) * params.pageSize;

    const [itemsResult, countResult] = await Promise.all([
      this.db
        .select({
          id: performanceRecord.id,
          employeeId: performanceRecord.employeeId,
          period: performanceRecord.period,
          status: performanceRecord.status,
          managerId: performanceRecord.managerId,
          totalScore: performanceRecord.totalScore,
          createdAt: performanceRecord.createdAt,
          updatedAt: performanceRecord.updatedAt,
          employeeName: emp.name,
          managerName: mgr.name,
        })
        .from(performanceRecord)
        .leftJoin(emp, eq(emp.employeeId, performanceRecord.employeeId))
        .leftJoin(mgr, eq(mgr.employeeId, performanceRecord.managerId))
        .where(whereClause)
        .orderBy(desc(performanceRecord.createdAt))
        .limit(params.pageSize)
        .offset(offset),
      this.db
        .select({ count: countDistinct(performanceRecord.id) })
        .from(performanceRecord)
        .leftJoin(emp, eq(emp.employeeId, performanceRecord.employeeId))
        .leftJoin(mgr, eq(mgr.employeeId, performanceRecord.managerId))
        .where(whereClause),
    ]);

    const total = Number((countResult[0] as { count: unknown }).count ?? 0);

    const items = itemsResult.map((item) => ({
      id: item.id,
      employeeId: item.employeeId,
      employeeName: (item.employeeName && String(item.employeeName).trim()) || '',
      period: item.period,
      status: item.status as PerformanceStatus,
      managerId: item.managerId,
      managerName: (item.managerName && String(item.managerName).trim()) || '',
      totalScore: item.totalScore ? parseFloat(String(item.totalScore)) : undefined,
      createdAt: item.createdAt.toISOString(),
      updatedAt: item.updatedAt.toISOString(),
    }));

    this.logger.log(
      `绩效列表查询: userId=${userId}, roles=${JSON.stringify(userRoles)}, total=${total}`,
    );

    return {
      items,
      total,
      page: params.page,
      pageSize: params.pageSize,
      canBatchCreate: batchCreate,
      canExport: exportData,
    };
  }

  /** 超级管理员：处于「上级评分」相关节点的全量绩效（含直属/虚线并行），用于校准侧监控 */
  async listSupervisorCalibrationQueue(
    userId: string | undefined,
    params: {
      period?: string;
      departmentId?: string;
      employeeName?: string;
      page: number;
      pageSize: number;
    },
  ) {
    if (!userId) {
      return { items: [], total: 0, page: params.page, pageSize: params.pageSize };
    }
    const role = await this.menuPermissionService.getUserRole(userId);
    this.menuPermissionService.assertSuperAdmin(role, '仅超级管理员可查看绩效校准（上级评分）队列');

    const emp = alias(employeeHierarchy, 'perf_svcal_emp');
    const mgr = alias(employeeHierarchy, 'perf_svcal_mgr');
    const dot = alias(employeeHierarchy, 'perf_svcal_dot');

    const conditions: SQL[] = [
      or(
        eq(performanceRecord.status, 'manager_review'),
        eq(performanceRecord.status, 'dual_manager_review'),
        eq(performanceRecord.status, 'dotted_manager_review'),
      )!,
    ];

    if (params.period?.trim()) {
      conditions.push(eq(performanceRecord.period, params.period.trim()));
    }

    const deptId = params.departmentId?.trim();
    if (deptId) {
      conditions.push(eq(emp.departmentId, deptId));
    }

    const nameQ = params.employeeName?.trim();
    if (nameQ) {
      const safe = `%${nameQ.replace(/%/g, '').replace(/_/g, '')}%`;
      if (safe.length > 2) {
        conditions.push(like(emp.name, safe));
      }
    }

    const whereClause = conditions.length > 0 ? and(...conditions) : undefined;
    const offset = (params.page - 1) * params.pageSize;

    const [itemsResult, countResult] = await Promise.all([
      this.db
        .select({
          id: performanceRecord.id,
          employeeId: performanceRecord.employeeId,
          period: performanceRecord.period,
          status: performanceRecord.status,
          managerId: performanceRecord.managerId,
          dottedManagerId: performanceRecord.dottedManagerId,
          totalScore: performanceRecord.totalScore,
          createdAt: performanceRecord.createdAt,
          updatedAt: performanceRecord.updatedAt,
          employeeName: emp.name,
          managerName: mgr.name,
          dottedManagerName: dot.name,
        })
        .from(performanceRecord)
        .leftJoin(emp, eq(emp.employeeId, performanceRecord.employeeId))
        .leftJoin(mgr, eq(mgr.employeeId, performanceRecord.managerId))
        .leftJoin(dot, eq(dot.employeeId, performanceRecord.dottedManagerId))
        .where(whereClause)
        .orderBy(desc(performanceRecord.updatedAt))
        .limit(params.pageSize)
        .offset(offset),
      this.db
        .select({ count: countDistinct(performanceRecord.id) })
        .from(performanceRecord)
        .leftJoin(emp, eq(emp.employeeId, performanceRecord.employeeId))
        .leftJoin(mgr, eq(mgr.employeeId, performanceRecord.managerId))
        .leftJoin(dot, eq(dot.employeeId, performanceRecord.dottedManagerId))
        .where(whereClause),
    ]);

    const total = Number((countResult[0] as { count: unknown }).count ?? 0);

    const items = itemsResult.map((item) => ({
      id: item.id,
      employeeId: item.employeeId,
      employeeName: (item.employeeName && String(item.employeeName).trim()) || '',
      period: item.period,
      status: item.status as PerformanceStatus,
      managerId: item.managerId,
      managerName: (item.managerName && String(item.managerName).trim()) || '',
      dottedManagerId: item.dottedManagerId || undefined,
      dottedManagerName: item.dottedManagerId
        ? (item.dottedManagerName && String(item.dottedManagerName).trim()) || ''
        : undefined,
      totalScore: item.totalScore ? parseFloat(String(item.totalScore)) : undefined,
      createdAt: item.createdAt.toISOString(),
      updatedAt: item.updatedAt.toISOString(),
    }));

    return { items, total, page: params.page, pageSize: params.pageSize };
  }

  async exportData(userId: string | undefined, params: Omit<ListParams, 'page' | 'pageSize'>) {
    if (!userId) {
      return { items: [] };
    }

    await this.menuPermissionService.assertMenuAllowed(userId, 'performance_export');

    const { listAll } = await this.performanceMenuFlags(userId);

    const emp = alias(employeeHierarchy, 'perf_export_emp');

    const conditions: SQL[] = [];

    if (params.focus === 'need_score') {
      conditions.push(
        or(
          and(
            eq(performanceRecord.status, 'manager_review'),
            eq(performanceRecord.managerId, userId),
          ),
          and(
            eq(performanceRecord.status, 'dual_manager_review'),
            or(
              eq(performanceRecord.managerId, userId),
              eq(performanceRecord.dottedManagerId, userId),
            ),
          ),
          and(
            eq(performanceRecord.status, 'dotted_manager_review'),
            eq(performanceRecord.dottedManagerId, userId),
          ),
        )!,
      );
    } else if (params.focus === 'need_approve_goal') {
      conditions.push(
        and(
          eq(performanceRecord.status, 'goal_pending_review'),
          or(
            eq(performanceRecord.managerId, userId),
            eq(performanceRecord.dottedManagerId, userId),
          ),
        )!,
      );
    } else if (params.status) {
      conditions.push(eq(performanceRecord.status, params.status));
    }
    if (params.period) {
      conditions.push(eq(performanceRecord.period, params.period));
    }

    if (!listAll) {
      conditions.push(
        or(
          eq(performanceRecord.employeeId, userId),
          eq(performanceRecord.managerId, userId),
          eq(performanceRecord.dottedManagerId, userId),
        ),
      );
    }

    const deptId = params.departmentId?.trim();
    if (deptId && listAll) {
      conditions.push(eq(emp.departmentId, deptId));
    }

    const nameQ = params.employeeName?.trim();
    if (nameQ) {
      const safe = `%${nameQ.replace(/%/g, '').replace(/_/g, '')}%`;
      if (safe.length > 2) {
        conditions.push(like(emp.name, safe));
      }
    }

    const whereClause = conditions.length > 0 ? and(...conditions) : undefined;

    const records = await this.db
      .select({
        employeeId: performanceRecord.employeeId,
        period: performanceRecord.period,
        status: performanceRecord.status,
        totalScore: performanceRecord.totalScore,
        selfReview: performanceRecord.selfReview,
        managerReview: performanceRecord.managerReview,
        dottedManagerReview: performanceRecord.dottedManagerReview,
        updatedAt: performanceRecord.updatedAt,
        employeeName: emp.name,
        departmentName: emp.departmentName,
      })
      .from(performanceRecord)
      .leftJoin(emp, eq(emp.employeeId, performanceRecord.employeeId))
      .where(whereClause)
      .orderBy(desc(performanceRecord.createdAt));

    const items = records.map((record) => {
      const selfReviewComment = (record.selfReview as Array<{ comment: string }> | undefined)
        ?.map((r: { comment: string }) => r.comment)
        .join('; ') ?? '';
      const managerReviewComment = (record.managerReview as Array<{ comment: string }> | undefined)
        ?.map((r: { comment: string }) => r.comment)
        .join('; ') ?? '';
      const dottedManagerReviewComment = (record.dottedManagerReview as Array<{ comment: string }> | undefined)
        ?.map((r: { comment: string }) => r.comment)
        .join('; ') ?? '';

      return {
        employeeName: (record.employeeName && String(record.employeeName).trim()) || '',
        department: (record.departmentName && String(record.departmentName).trim()) || '',
        period: record.period,
        status: record.status,
        totalScore: record.totalScore ? parseFloat(String(record.totalScore)) : 0,
        selfReviewComment,
        managerReviewComment,
        dottedManagerReviewComment,
        updatedAt: record.updatedAt.toISOString(),
      };
    });

    this.logger.log(`绩效数据导出: userId=${userId}, count=${items.length}`);

    return { items };
  }

  async getDetail(userId: string, id: string) {
    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, id))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];
    const { listAll } = await this.performanceMenuFlags(userId);
    const isEmployee = r.employeeId === userId;
    const isManager = r.managerId === userId;
    const isDottedManager = r.dottedManagerId === userId;

    if (!listAll && !isEmployee && !isManager && !isDottedManager) {
      throw new ForbiddenException('无权查看该绩效记录');
    }

    // 查询模板（如果已选择）
    let template: { name: string; indicators: unknown } | null = null;
    if (r.templateId) {
      const templates = await this.db
        .select()
        .from(performanceTemplate)
        .where(eq(performanceTemplate.id, r.templateId))
        .limit(1);
      template = templates[0] || null;
    }

    const isCompleted = r.status === 'completed';
    const hideReviewData = isEmployee && !listAll && !isCompleted;

    const nameLookupIds = [r.employeeId, r.managerId];
    if (r.dottedManagerId) {
      nameLookupIds.push(r.dottedManagerId);
    }
    const uniqueNameIds = [...new Set(nameLookupIds)];
    const nameRows =
      uniqueNameIds.length > 0
        ? await this.db
            .select({
              employeeId: employeeHierarchy.employeeId,
              name: employeeHierarchy.name,
            })
            .from(employeeHierarchy)
            .where(inArray(employeeHierarchy.employeeId, uniqueNameIds))
        : [];
    const nameById = new Map(
      nameRows.map((row) => [
        row.employeeId as string,
        (row.name && String(row.name).trim()) || '',
      ]),
    );

    const indicators = (template?.indicators as PerformanceIndicator[]) || [];
    const indicatorNames = indicators.map((i) => i.name);

    const detailViewerRole = await this.menuPermissionService.getUserRole(userId);
    const isSuperAdminViewer = detailViewerRole === 'super_admin';

    const showReviewSynthesis =
      !hideReviewData &&
      indicatorNames.length > 0 &&
      (isManager || isDottedManager || listAll || isSuperAdminViewer) &&
      (!!r.dottedManagerId || isSuperAdminViewer);

    let reviewRoleWeights: { managerWeight: number; dottedWeight: number } | undefined;
    let reviewMergedIndicators:
      | Array<{
          indicatorName: string;
          managerScore?: number;
          dottedScore?: number;
          mergedScore?: number;
        }>
      | undefined;
    let reviewMergedTotal: number | undefined;

    const mgrRevForTotals = (r.managerReview as ReviewItem[] | null) || null;
    const dotRevForTotals = (r.dottedManagerReview as ReviewItem[] | null) || null;
    let managerWeightedTotal: number | undefined;
    let dottedManagerWeightedTotal: number | undefined;
    if (indicatorNames.length > 0) {
      if (this.isReviewComplete(mgrRevForTotals, indicatorNames)) {
        managerWeightedTotal =
          Math.round(this.calcWeightedScore(mgrRevForTotals!, indicators) * 100) / 100;
      }
      if (r.dottedManagerId && this.isReviewComplete(dotRevForTotals, indicatorNames)) {
        dottedManagerWeightedTotal =
          Math.round(this.calcWeightedScore(dotRevForTotals!, indicators) * 100) / 100;
      }
    }

    if (showReviewSynthesis) {
      const { managerWeight, dottedWeight } = await this.getReviewWeights();
      const { mW, dW } = this.normalizeRoleWeights(managerWeight, dottedWeight);
      reviewRoleWeights = { managerWeight: mW, dottedWeight: dW };

      const mgrRev = (r.managerReview as ReviewItem[] | null) || null;
      const dotRev = (r.dottedManagerReview as ReviewItem[] | null) || null;
      const mMap = new Map((mgrRev || []).map((x) => [x.indicatorName, x.score]));
      const dMap = new Map((dotRev || []).map((x) => [x.indicatorName, x.score]));

      reviewMergedIndicators = indicatorNames.map((name) => {
        const ms = mMap.get(name);
        const ds = dMap.get(name);
        const mergedScore =
          typeof ms === 'number' &&
          typeof ds === 'number' &&
          !Number.isNaN(ms) &&
          !Number.isNaN(ds)
            ? Math.round((mW * ms + dW * ds) * 100) / 100
            : undefined;
        return {
          indicatorName: name,
          managerScore: typeof ms === 'number' ? ms : undefined,
          dottedScore: typeof ds === 'number' ? ds : undefined,
          mergedScore,
        };
      });

      if (this.isReviewComplete(mgrRev, indicatorNames) && this.isReviewComplete(dotRev, indicatorNames)) {
        reviewMergedTotal = await this.computeTotalScore(r.templateId, mgrRev, dotRev, true);
      }
    }

    return {
      id: r.id,
      employeeId: r.employeeId,
      employeeName: nameById.get(r.employeeId) || '',
      templateId: r.templateId || undefined,
      templateName: template?.name || '',
      period: r.period,
      status: r.status as PerformanceStatus,
      managerId: r.managerId,
      managerName: nameById.get(r.managerId) || '',
      dottedManagerId: r.dottedManagerId || undefined,
      dottedManagerName: r.dottedManagerId ? nameById.get(r.dottedManagerId) || '' : undefined,
      goalSetting: (r.goalSetting as GoalSettingItem[]) || undefined,
      goalApprovedBy: r.goalApprovedBy || undefined,
      personalSummary: r.personalSummary || undefined,
      selfReview: (r.selfReview as ReviewItem[]) || undefined,
      managerReview: hideReviewData ? undefined : (r.managerReview as ReviewItem[]) || undefined,
      dottedManagerReview: hideReviewData ? undefined : (r.dottedManagerReview as ReviewItem[]) || undefined,
      totalScore: r.totalScore ? parseFloat(String(r.totalScore)) : undefined,
      managerWeightedTotal,
      dottedManagerWeightedTotal,
      rejectionReason: r.rejectionReason || undefined,
      finalReviewerId: r.finalReviewerId || undefined,
      finalReviewedAt: r.finalReviewedAt ? r.finalReviewedAt.toISOString() : undefined,
      indicators: (template?.indicators as Array<{ name: string; weight: number; description: string }>) || [],
      reviewRoleWeights,
      reviewMergedIndicators,
      reviewMergedTotal,
      createdAt: r.createdAt.toISOString(),
      updatedAt: r.updatedAt.toISOString(),
    };
  }

  async saveDraft(
    userId: string,
    id: string,
    body: { reviewType: string; content: ReviewItem[] | GoalSettingItem[]; personalSummary?: string },
  ) {
    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, id))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];
    const updateData: Record<string, unknown> = {};

    if (body.reviewType === 'goal') {
      if (r.employeeId !== userId) {
        throw new ForbiddenException('无权保存目标设定');
      }
      updateData.goalSetting = body.content;
    } else if (body.reviewType === 'self') {
      if (r.employeeId !== userId) {
        throw new ForbiddenException('无权保存该草稿');
      }
      updateData.selfReview = body.content;
      if (body.personalSummary !== undefined) {
        updateData.personalSummary = body.personalSummary;
      }
    } else if (body.reviewType === 'manager') {
      if (r.managerId !== userId) {
        throw new ForbiddenException('无权保存该草稿');
      }
      if (r.status !== 'manager_review' && r.status !== 'dual_manager_review' && r.status !== 'final_review') {
        throw new ForbiddenException('当前状态不允许保存上级评分草稿');
      }
      updateData.managerReview = body.content;
    } else if (body.reviewType === 'dotted_manager') {
      if (r.dottedManagerId !== userId) {
        throw new ForbiddenException('无权保存该草稿');
      }
      if (
        r.status !== 'dotted_manager_review' &&
        r.status !== 'dual_manager_review' &&
        r.status !== 'final_review'
      ) {
        throw new ForbiddenException('当前状态不允许保存虚线上级评分草稿');
      }
      updateData.dottedManagerReview = body.content;
    } else {
      throw new ForbiddenException('无效的评审类型');
    }

    await this.db
      .update(performanceRecord)
      .set(updateData)
      .where(eq(performanceRecord.id, id));

    return { success: true };
  }

  async submit(
    userId: string,
    id: string,
    body: { reviewType: string; content: ReviewItem[] | GoalSettingItem[]; personalSummary?: string },
  ) {
    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, id))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];
    const updateData: Record<string, unknown> = {};
    let newStatus: PerformanceStatus = r.status as PerformanceStatus;

    if (body.reviewType === 'goal') {
      if (r.employeeId !== userId) {
        throw new ForbiddenException('无权提交目标设定');
      }
      if (r.status !== 'goal_setting' && r.status !== 'goal_rejected') {
        throw new ForbiddenException('当前状态不允许提交目标设定');
      }
      updateData.goalSetting = body.content;
      newStatus = 'goal_pending_review';
    } else if (body.reviewType === 'self') {
      if (r.employeeId !== userId) {
        throw new ForbiddenException('无权提交自评');
      }
      if (r.status !== 'self_review' && r.status !== 'goal_rejected') {
        throw new ForbiddenException('当前状态不允许提交自评');
      }
      updateData.selfReview = body.content;
      if (body.personalSummary !== undefined) {
        updateData.personalSummary = body.personalSummary;
      }
      const indicators = await this.getTemplateIndicators(r.templateId);
      const indicatorNames = indicators.map((i) => i.name);
      if (this.isReviewComplete(body.content as ReviewItem[], indicatorNames)) {
        const selfTotal = this.calcWeightedScore(body.content as ReviewItem[], indicators);
        updateData.totalScore = String(Math.round(selfTotal * 100) / 100);
      }
      newStatus = r.dottedManagerId ? 'dual_manager_review' : 'manager_review';
    } else if (body.reviewType === 'manager') {
      if (r.managerId !== userId) {
        throw new ForbiddenException('无权提交上级评分');
      }
      if (r.status !== 'manager_review' && r.status !== 'dual_manager_review' && r.status !== 'final_review') {
        throw new ForbiddenException('当前状态不允许提交上级评分');
      }
      updateData.managerReview = body.content;
      const hasDotted = !!r.dottedManagerId;
      const indicators = await this.getTemplateIndicators(r.templateId);
      const indicatorNames = indicators.map((i) => i.name);

      if (r.status === 'final_review') {
        newStatus = 'final_review';
        if (hasDotted) {
          const mgrComplete = this.isReviewComplete(body.content as ReviewItem[], indicatorNames);
          const dotComplete = this.isReviewComplete(r.dottedManagerReview as ReviewItem[] | null, indicatorNames);
          if (mgrComplete && dotComplete) {
            const total = await this.computeTotalScore(
              r.templateId,
              body.content as ReviewItem[],
              r.dottedManagerReview as ReviewItem[] | null,
              true,
            );
            updateData.totalScore = String(total);
          } else if (mgrComplete) {
            updateData.totalScore = String(
              Math.round(this.calcWeightedScore(body.content as ReviewItem[], indicators) * 100) / 100,
            );
          }
        } else {
          const total = await this.computeTotalScore(
            r.templateId,
            body.content as ReviewItem[],
            null,
            false,
          );
          updateData.totalScore = String(total);
        }
      } else if (r.status === 'dual_manager_review') {
        const mgrComplete = this.isReviewComplete(body.content as ReviewItem[], indicatorNames);
        const dotComplete = this.isReviewComplete(r.dottedManagerReview as ReviewItem[] | null, indicatorNames);
        if (mgrComplete && dotComplete && hasDotted) {
          const total = await this.computeTotalScore(
            r.templateId,
            body.content as ReviewItem[],
            r.dottedManagerReview as ReviewItem[] | null,
            true,
          );
          updateData.totalScore = String(total);
          newStatus = 'final_review';
        } else {
          newStatus = 'dual_manager_review';
          if (mgrComplete) {
            updateData.totalScore = String(
              Math.round(this.calcWeightedScore(body.content as ReviewItem[], indicators) * 100) / 100,
            );
          }
        }
      } else {
        if (!hasDotted) {
          const total = await this.computeTotalScore(
            r.templateId,
            body.content as ReviewItem[],
            null,
            false,
          );
          updateData.totalScore = String(total);
          newStatus = 'final_review';
        } else {
          newStatus = 'dual_manager_review';
          const mgrComplete = this.isReviewComplete(body.content as ReviewItem[], indicatorNames);
          if (mgrComplete) {
            updateData.totalScore = String(
              Math.round(this.calcWeightedScore(body.content as ReviewItem[], indicators) * 100) / 100,
            );
          }
        }
      }
    } else if (body.reviewType === 'dotted_manager') {
      if (r.dottedManagerId !== userId) {
        throw new ForbiddenException('无权提交虚线上级评分');
      }
      if (
        r.status !== 'dotted_manager_review' &&
        r.status !== 'dual_manager_review' &&
        r.status !== 'final_review'
      ) {
        throw new ForbiddenException('当前状态不允许提交虚线上级评分');
      }
      updateData.dottedManagerReview = body.content;

      if (r.status === 'final_review') {
        newStatus = 'final_review';
        const indicators = await this.getTemplateIndicators(r.templateId);
        const indicatorNames = indicators.map((i) => i.name);
        const dotComplete = this.isReviewComplete(body.content as ReviewItem[], indicatorNames);
        const mgrComplete = this.isReviewComplete(r.managerReview as ReviewItem[] | null, indicatorNames);
        if (mgrComplete && dotComplete && r.dottedManagerId) {
          const total = await this.computeTotalScore(
            r.templateId,
            r.managerReview as ReviewItem[] | null,
            body.content as ReviewItem[],
            true,
          );
          updateData.totalScore = String(total);
        } else if (dotComplete) {
          updateData.totalScore = String(
            Math.round(this.calcWeightedScore(body.content as ReviewItem[], indicators) * 100) / 100,
          );
        }
      } else if (r.status === 'dual_manager_review') {
        const indicators = await this.getTemplateIndicators(r.templateId);
        const indicatorNames = indicators.map((i) => i.name);
        const dotComplete = this.isReviewComplete(body.content as ReviewItem[], indicatorNames);
        const mgrComplete = this.isReviewComplete(r.managerReview as ReviewItem[] | null, indicatorNames);
        if (mgrComplete && dotComplete) {
          const total = await this.computeTotalScore(
            r.templateId,
            r.managerReview as ReviewItem[] | null,
            body.content as ReviewItem[],
            true,
          );
          updateData.totalScore = String(total);
          newStatus = 'final_review';
        } else {
          newStatus = 'dual_manager_review';
          if (dotComplete) {
            updateData.totalScore = String(
              Math.round(this.calcWeightedScore(body.content as ReviewItem[], indicators) * 100) / 100,
            );
          }
        }
      } else {
        const total = await this.computeTotalScore(
          r.templateId,
          r.managerReview as ReviewItem[] | null,
          body.content as ReviewItem[],
          true,
        );
        updateData.totalScore = String(total);
        newStatus = 'final_review';
      }
    } else {
      throw new ForbiddenException('无效的评审类型');
    }

    updateData.status = newStatus;

    await this.db
      .update(performanceRecord)
      .set(updateData)
      .where(eq(performanceRecord.id, id));

    this.logger.log(`绩效 ${id} 已提交，新状态: ${newStatus}`);

    return { success: true, newStatus };
  }

  async reject(userId: string, id: string, reason: string) {
    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, id))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];
    const isManager = r.managerId === userId;
    const isDottedManager = r.dottedManagerId === userId;

    if (!isManager && !isDottedManager) {
      throw new ForbiddenException('无权驳回该绩效');
    }

    if (isManager && r.status !== 'manager_review' && r.status !== 'dual_manager_review') {
      throw new ForbiddenException('当前状态不允许驳回');
    }

    if (
      isDottedManager &&
      r.status !== 'dotted_manager_review' &&
      r.status !== 'dual_manager_review'
    ) {
      throw new ForbiddenException('当前状态不允许驳回');
    }

    await this.db
      .update(performanceRecord)
      .set({
        status: 'goal_rejected',
        rejectionReason: reason,
      })
      .where(eq(performanceRecord.id, id));

    this.logger.log(`绩效 ${id} 已驳回，原因: ${reason}`);

    return { success: true };
  }

  /** 创建绩效弹窗：考核月度仅来自「周期与评选」中已维护的月度周期（evaluation_period.period_type = month） */
  async listMonthPeriodsForCreate(userId: string | undefined): Promise<{
    items: Array<{ periodKey: string; name: string }>;
  }> {
    if (!userId) {
      throw new ForbiddenException('请先登录');
    }
    await this.menuPermissionService.assertMenuAllowed(userId, 'performance_batch_create');

    const rows = await this.db
      .select({
        periodKey: evaluationPeriod.periodKey,
        name: evaluationPeriod.name,
      })
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.periodType, 'month'))
      .orderBy(asc(evaluationPeriod.sortOrder), desc(evaluationPeriod.periodKey));

    return {
      items: rows.map((r) => ({
        periodKey: r.periodKey,
        name: (r.name && r.name.trim()) || '',
      })),
    };
  }

  async createBatch(
    userId: string | undefined,
    body: { employeeIds?: string[]; employeeNames?: string[]; period: string; departmentName?: string },
  ) {
    if (!userId) {
      throw new ForbiddenException('请先登录');
    }

    await this.menuPermissionService.assertMenuAllowed(userId, 'performance_batch_create');

    // 收集需要创建的员工列表（按姓名）
    const employeeNamesToCreate: string[] = [...(body.employeeNames || [])];

    // 如果指定了部门，查询该部门下所有员工姓名
    if (body.departmentName) {
      const deptEmployees = await this.db
        .select({ name: employeeHierarchy.name })
        .from(employeeHierarchy)
        .where(eq(employeeHierarchy.departmentName, body.departmentName));

      const deptEmployeeNames = deptEmployees.map((e) => e.name).filter(Boolean) as string[];
      employeeNamesToCreate.push(...deptEmployeeNames);
    }

    // 去重
    const uniqueEmployeeNames = [...new Set(employeeNamesToCreate)];

    // 最多100条
    if (uniqueEmployeeNames.length > 100) {
      throw new ForbiddenException('单次最多创建100条绩效记录');
    }

    const results: Array<{
      employeeId: string;
      employeeName?: string;
      success: boolean;
      id?: string;
      error?: string;
    }> = [];

    for (const employeeName of uniqueEmployeeNames) {
      try {
        // 按姓名查询员工信息
        const hierarchy = await this.db
          .select()
          .from(employeeHierarchy)
          .where(eq(employeeHierarchy.name, employeeName))
          .limit(1);

        if (!hierarchy.length) {
          results.push({
            employeeId: '',
            employeeName,
            success: false,
            error: '员工不在层级表中，请先同步员工信息',
          });
          continue;
        }

        const emp = hierarchy[0];
        const employeeIdValue = emp.employeeId;
        const managerId = emp.managerId;
        const dottedManagerId = emp.dottedManagerId;

        if (!managerId) {
          results.push({
            employeeId: employeeIdValue as string,
            employeeName: emp.name || undefined,
            success: false,
            error: '该员工未设置直属上级',
          });
          continue;
        }

        // 检查同周期是否已有绩效
        const existing = await this.db
          .select()
          .from(performanceRecord)
          .where(
            and(
              eq(performanceRecord.employeeId, employeeIdValue as string),
              eq(performanceRecord.period, body.period),
            ),
          )
          .limit(1);

        if (existing.length) {
          results.push({
            employeeId: employeeIdValue as string,
            employeeName: emp.name || undefined,
            success: false,
            error: `该员工在 ${body.period} 周期已有绩效记录`,
          });
          continue;
        }

        // 创建绩效记录（无需模板，状态为待选模板）
        const newId = randomUUID();
        const result = await this.db
          .insert(performanceRecord)
          .values({
            id: newId,
            employeeId: employeeIdValue as string,
            period: body.period,
            status: 'template_selection',
            managerId,
            dottedManagerId,
          });

        results.push({
          employeeId: employeeIdValue as string,
          employeeName: emp.name || undefined,
          success: true,
          id: newId,
        });

        this.logger.log(`创建绩效记录: employeeName=${employeeName}, period=${body.period}, id=${newId}`);
      } catch (error) {
        results.push({
          employeeId: '',
          employeeName,
          success: false,
          error: error instanceof Error ? error.message : '创建失败',
        });
      }
    }

    const successCount = results.filter((r) => r.success).length;
    const failCount = results.length - successCount;

    return {
      results,
      total: results.length,
      successCount,
      failCount,
    };
  }

  async approveGoal(
    userId: string,
    id: string,
    body: { approved: boolean; rejectionReason?: string },
  ): Promise<{ success: boolean; newStatus: PerformanceStatus }> {
    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, id))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];
    const isManager = r.managerId === userId;
    const isDottedManager = r.dottedManagerId === userId;

    if (!isManager && !isDottedManager) {
      throw new ForbiddenException('无权审核目标设定');
    }

    if (r.status !== 'goal_pending_review') {
      throw new ForbiddenException('当前状态不允许审核目标');
    }

    const newStatus: PerformanceStatus = body.approved ? 'self_review' : 'goal_rejected';
    const updateData: Record<string, unknown> = {
      status: newStatus,
      goalApprovedBy: userId,
    };

    if (!body.approved) {
      updateData.rejectionReason = body.rejectionReason || '目标设定未通过审核';
    }

    await this.db
      .update(performanceRecord)
      .set(updateData)
      .where(eq(performanceRecord.id, id));

    this.logger.log(`绩效 ${id} 目标审核完成，新状态: ${newStatus}`);

    return { success: true, newStatus };
  }

  async finalReview(
    userId: string,
    id: string,
    body: { approved: boolean; rejectionReason?: string; returnToStage?: PerformanceStatus },
  ): Promise<{ success: boolean; newStatus: PerformanceStatus }> {
    const { reviewAdmin } = await this.performanceMenuFlags(userId);

    if (!reviewAdmin) {
      throw new ForbiddenException('无权执行终审操作');
    }

    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, id))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];

    if (r.status !== 'final_review') {
      throw new ForbiddenException('当前状态不允许终审');
    }

    let newStatus: PerformanceStatus;
    const updateData: Record<string, unknown> = {
      finalReviewerId: userId,
      finalReviewedAt: new Date(),
    };

    if (body.approved) {
      newStatus = 'completed';
    } else {
      newStatus = body.returnToStage || 'self_review';
      updateData.rejectionReason = body.rejectionReason || '终审未通过';
    }

    updateData.status = newStatus;

    await this.db
      .update(performanceRecord)
      .set(updateData)
      .where(eq(performanceRecord.id, id));

    this.logger.log(`绩效 ${id} 终审完成，新状态: ${newStatus}`);

    return { success: true, newStatus };
  }

  async selectTemplate(
    userId: string,
    performanceId: string,
    templateId: string,
  ): Promise<{ success: boolean; newStatus: PerformanceStatus }> {
    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, performanceId))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];

    // 验证当前用户是否是该绩效的员工本人
    if (r.employeeId !== userId) {
      throw new ForbiddenException('只有本人可以选择模板');
    }

    // 验证状态
    if (r.status !== 'template_selection') {
      throw new ForbiddenException('当前状态不允许选择模板');
    }

    // 验证模板是否存在且启用
    const templates = await this.db
      .select()
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, templateId))
      .limit(1);

    if (!templates.length) {
      throw new NotFoundException('模板不存在');
    }

    const template = templates[0];
    if (template.status !== 'enabled') {
      throw new ForbiddenException('该模板已停用');
    }

    // 更新绩效记录
    await this.db
      .update(performanceRecord)
      .set({
        templateId,
        status: 'goal_setting',
      })
      .where(eq(performanceRecord.id, performanceId));

    this.logger.log(`绩效 ${performanceId} 选择模板: ${templateId}`);

    return { success: true, newStatus: 'goal_setting' };
  }

  async calibrate(
    userId: string | undefined,
    performanceId: string,
    body: { approved: boolean; finalScore?: number; rejectionReason?: string; returnToStage?: PerformanceStatus },
  ): Promise<{ success: boolean; newStatus: PerformanceStatus }> {
    if (!userId) {
      throw new ForbiddenException('请先登录');
    }

    const { reviewAdmin } = await this.performanceMenuFlags(userId);

    if (!reviewAdmin) {
      throw new ForbiddenException('无权执行绩效校准');
    }

    const record = await this.db
      .select()
      .from(performanceRecord)
      .where(eq(performanceRecord.id, performanceId))
      .limit(1);

    if (!record.length) {
      throw new NotFoundException('绩效记录不存在');
    }

    const r = record[0];

    if (r.status !== 'final_review') {
      throw new ForbiddenException('当前状态不允许绩效校准');
    }

    let newStatus: PerformanceStatus;
    const updateData: Record<string, unknown> = {
      finalReviewerId: userId,
      finalReviewedAt: new Date(),
    };

    if (body.approved) {
      newStatus = 'completed';
      // 如果指定了校准分数，更新总分
      if (body.finalScore !== undefined) {
        updateData.totalScore = body.finalScore;
      }
    } else {
      newStatus = body.returnToStage || 'self_review';
      updateData.rejectionReason = body.rejectionReason || '校准未通过';
    }

    updateData.status = newStatus;

    await this.db
      .update(performanceRecord)
      .set(updateData)
      .where(eq(performanceRecord.id, performanceId));

    this.logger.log(`绩效 ${performanceId} 校准完成，新状态: ${newStatus}`);

    return { success: true, newStatus };
  }

  /**
   * 定时任务：按自然月周期 `YYYY-MM` 为所有已设直属上级的员工创建「待选模板」记录（同周期幂等）。
   */
  async ensureMonthlyPerformanceRecordsForPeriod(period: string): Promise<{ created: number; skipped: number }> {
    const rows = await this.db
      .select()
      .from(employeeHierarchy)
      .where(isNotNull(employeeHierarchy.managerId));

    let created = 0;
    let skipped = 0;

    for (const emp of rows) {
      const employeeIdValue = emp.employeeId as string;
      const managerId = emp.managerId as string | null;
      if (!managerId) {
        skipped += 1;
        continue;
      }

      const existing = await this.db
        .select({ id: performanceRecord.id })
        .from(performanceRecord)
        .where(
          and(
            eq(performanceRecord.employeeId, employeeIdValue),
            eq(performanceRecord.period, period),
          ),
        )
        .limit(1);

      if (existing.length) {
        skipped += 1;
        continue;
      }

      const newId = randomUUID();
      await this.db.insert(performanceRecord).values({
        id: newId,
        employeeId: employeeIdValue,
        period,
        status: 'template_selection',
        managerId,
        dottedManagerId: emp.dottedManagerId,
      });
      created += 1;
    }

    this.logger.log(`月度绩效自动创建 period=${period} created=${created} skipped=${skipped}`);
    return { created, skipped };
  }
}
