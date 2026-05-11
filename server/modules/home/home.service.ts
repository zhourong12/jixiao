import { Injectable, Inject } from '@nestjs/common';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { eq, or, and, count, inArray, desc, gte, lt, type SQL } from 'drizzle-orm';
import { performanceRecord } from '../../database/schema';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';
import type { TodoItem, PerformanceOverview, HomeActionCounts } from '@shared/api.interface';

const ACTIVE_STATUSES = [
  'template_selection',
  'goal_setting',
  'goal_pending_review',
  'goal_rejected',
  'self_review',
  'manager_review',
  'dual_manager_review',
  'dotted_manager_review',
  'final_review',
] as const;

function resolveYearMonth(year?: number, month?: number): { year: number; month: number } {
  const now = new Date();
  const y = year != null && Number.isFinite(year) ? Math.floor(year) : now.getFullYear();
  const m = month != null && Number.isFinite(month) ? Math.min(12, Math.max(1, Math.floor(month))) : now.getMonth() + 1;
  return { year: y, month: m };
}

function monthUpdatedRange(year: number, month: number): { start: Date; endExclusive: Date } {
  const start = new Date(year, month - 1, 1, 0, 0, 0, 0);
  const endExclusive = new Date(year, month, 1, 0, 0, 0, 0);
  return { start, endExclusive };
}

@Injectable()
export class HomeService {
  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly menuPermissionService: MenuPermissionService,
  ) {}

  private async getUserRole(userId: string): Promise<string> {
    return this.menuPermissionService.getUserRole(userId);
  }

  private isAdminRole(role: string): boolean {
    return role === 'super_admin' || role === 'admin';
  }

  /** 当前用户可见的绩效范围条件（管理员看全量） */
  private async userScopeCondition(userId: string): Promise<SQL | undefined> {
    const role = await this.getUserRole(userId);
    if (this.isAdminRole(role)) {
      return undefined;
    }
    return or(
      eq(performanceRecord.employeeId, userId),
      eq(performanceRecord.managerId, userId),
      eq(performanceRecord.dottedManagerId, userId),
    );
  }

  private monthCondition(year: number, month: number): SQL {
    const { start, endExclusive } = monthUpdatedRange(year, month);
    return and(
      gte(performanceRecord.updatedAt, start),
      lt(performanceRecord.updatedAt, endExclusive),
    )!;
  }

  async getTodos(
    userId: string,
    opts?: { year?: number; month?: number },
  ): Promise<{ items: TodoItem[] }> {
    const { year, month } = resolveYearMonth(opts?.year, opts?.month);
    const monthCond = this.monthCondition(year, month);
    const role = await this.getUserRole(userId);
    const admin = this.isAdminRole(role);

    const clauses: SQL[] = [
      and(
        eq(performanceRecord.status, 'template_selection'),
        eq(performanceRecord.employeeId, userId),
      ),
      and(eq(performanceRecord.status, 'goal_setting'), eq(performanceRecord.employeeId, userId)),
      and(eq(performanceRecord.status, 'goal_rejected'), eq(performanceRecord.employeeId, userId)),
      and(
        eq(performanceRecord.status, 'goal_pending_review'),
        or(
          eq(performanceRecord.managerId, userId),
          eq(performanceRecord.dottedManagerId, userId),
        ),
      ),
      and(eq(performanceRecord.status, 'self_review'), eq(performanceRecord.employeeId, userId)),
      and(eq(performanceRecord.status, 'manager_review'), eq(performanceRecord.managerId, userId)),
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
    ];
    if (admin) {
      clauses.push(eq(performanceRecord.status, 'final_review'));
    }

    const rows = await this.db
      .select({
        id: performanceRecord.id,
        period: performanceRecord.period,
        status: performanceRecord.status,
      })
      .from(performanceRecord)
      .where(and(or(...clauses), monthCond))
      .orderBy(desc(performanceRecord.updatedAt));

    const items: TodoItem[] = rows.map((r) => {
      const period = r.period;
      switch (r.status) {
        case 'template_selection':
          return { id: r.id, period, type: 'template_selection', title: `${period} 待选择绩效模板` };
        case 'goal_setting':
          return { id: r.id, period, type: 'goal_setting', title: `${period} 目标设定` };
        case 'goal_rejected':
          return { id: r.id, period, type: 'goal_rejected', title: `${period} 目标被驳回，请修改` };
        case 'goal_pending_review':
          return { id: r.id, period, type: 'goal_pending_review', title: `${period} 待审核目标` };
        case 'self_review':
          return { id: r.id, period, type: 'self_review', title: `${period} 绩效自评` };
        case 'manager_review':
          return { id: r.id, period, type: 'manager_review', title: `${period} 直属上级评分` };
        case 'dual_manager_review':
          return { id: r.id, period, type: 'dual_manager_review', title: `${period} 上级与虚线上级评分` };
        case 'dotted_manager_review':
          return { id: r.id, period, type: 'dotted_manager_review', title: `${period} 虚线上级评分` };
        case 'final_review':
          return { id: r.id, period, type: 'final_review', title: `${period} 待终审/校准` };
        default:
          return { id: r.id, period, type: 'self_review', title: `${period} 待办` };
      }
    });

    return { items };
  }

  async getOverview(
    userId: string,
    opts?: { year?: number; month?: number },
  ): Promise<PerformanceOverview> {
    const { year, month } = resolveYearMonth(opts?.year, opts?.month);
    const monthCond = this.monthCondition(year, month);
    const scope = await this.userScopeCondition(userId);

    const baseWhere = scope ? and(scope, monthCond) : monthCond;

    const totalResult = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(baseWhere);

    const pendingWhere = and(
      baseWhere,
      inArray(performanceRecord.status, [...ACTIVE_STATUSES] as unknown as string[]),
    );

    const pendingResult = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(pendingWhere);

    const completedWhere = and(baseWhere, eq(performanceRecord.status, 'completed'));

    const completedResult = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(completedWhere);

    const rejectedWhere = and(baseWhere, eq(performanceRecord.status, 'goal_rejected'));

    const rejectedResult = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(rejectedWhere);

    const total = Number((totalResult[0] as { count: unknown }).count);
    const pending = Number((pendingResult[0] as { count: unknown }).count);
    const completed = Number((completedResult[0] as { count: unknown }).count);
    const rejected = Number((rejectedResult[0] as { count: unknown }).count);

    return { total, pending, completed, rejected, year, month };
  }

  async getActionCounts(
    userId: string,
    opts?: { year?: number; month?: number },
  ): Promise<HomeActionCounts> {
    const { year, month } = resolveYearMonth(opts?.year, opts?.month);
    const monthCond = this.monthCondition(year, month);
    const role = await this.getUserRole(userId);
    const admin = this.isAdminRole(role);

    const mgrScore = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(
        and(
          eq(performanceRecord.status, 'manager_review'),
          eq(performanceRecord.managerId, userId),
          monthCond,
        ),
      );

    const dualScore = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(
        and(
          eq(performanceRecord.status, 'dual_manager_review'),
          or(
            eq(performanceRecord.managerId, userId),
            eq(performanceRecord.dottedManagerId, userId),
          ),
          monthCond,
        ),
      );

    const dottedScore = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(
        and(
          eq(performanceRecord.status, 'dotted_manager_review'),
          eq(performanceRecord.dottedManagerId, userId),
          monthCond,
        ),
      );

    const approveGoal = await this.db
      .select({ count: count() })
      .from(performanceRecord)
      .where(
        and(
          eq(performanceRecord.status, 'goal_pending_review'),
          or(
            eq(performanceRecord.managerId, userId),
            eq(performanceRecord.dottedManagerId, userId),
          ),
          monthCond,
        ),
      );

    const needScore =
      Number((mgrScore[0] as { count: unknown }).count) +
      Number((dualScore[0] as { count: unknown }).count) +
      Number((dottedScore[0] as { count: unknown }).count);
    const needApproveGoal = Number((approveGoal[0] as { count: unknown }).count);

    let needFinalReview = 0;
    if (admin) {
      const fr = await this.db
        .select({ count: count() })
        .from(performanceRecord)
        .where(and(eq(performanceRecord.status, 'final_review'), monthCond));
      needFinalReview = Number((fr[0] as { count: unknown }).count);
    }

    return { needScore, needApproveGoal, needFinalReview };
  }
}
