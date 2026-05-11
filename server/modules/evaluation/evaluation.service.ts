import {
  Injectable,
  Inject,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import {
  eq,
  and,
  desc,
  isNotNull,
  inArray,
  asc,
  ne,
  gte,
  lt,
  count,
  type SQL,
} from 'drizzle-orm';
import { alias } from 'drizzle-orm/mysql-core';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import {
  performanceRecord,
  evaluationPeriod,
  awardType,
  periodAward,
  employeeHierarchy,
} from '@server/database/schema';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';
import type {
  EvaluationPeriodItem,
  CreateEvaluationPeriodRequest,
  UpdateEvaluationPeriodRequest,
  AwardTypeItem,
  PerformanceLeaderboardResponse,
  PerformanceLeaderboardItem,
  PeriodAwardItem,
  CreatePeriodAwardRequest,
} from '@shared/api.interface';

function toIso(d: Date | null | undefined): string {
  if (!d) return '';
  return d.toISOString();
}

function monthUpdatedRange(year: number, month: number): { start: Date; endExclusive: Date } {
  const start = new Date(year, month - 1, 1, 0, 0, 0, 0);
  const endExclusive = new Date(year, month, 1, 0, 0, 0, 0);
  return { start, endExclusive };
}

function parseYearMonth(ym: string): { year: number; month: number } {
  const m = /^(\d{4})-(\d{2})$/.exec(ym?.trim() ?? '');
  if (!m) {
    throw new BadRequestException('月度 key 须为 YYYY-MM 格式');
  }
  const year = Number(m[1]);
  const month = Number(m[2]);
  if (month < 1 || month > 12) {
    throw new BadRequestException('月度 key 无效');
  }
  return { year, month };
}

function parsePerformanceQuarterPeriod(p: string): string {
  const t = p?.trim() ?? '';
  if (!t) {
    throw new BadRequestException('请指定绩效季度 key');
  }
  if (!/^\d{4}-Q[1-4]$/.test(t)) {
    throw new BadRequestException('季度 key 须为 YYYY-Q1～Q4 格式');
  }
  return t;
}

function normalizePeriodKey(periodType: 'month' | 'quarter', raw: string): string {
  if (periodType === 'month') {
    const { year, month } = parseYearMonth(raw);
    return `${year}-${String(month).padStart(2, '0')}`;
  }
  return parsePerformanceQuarterPeriod(raw);
}

function awardScopeMatches(awardScope: string, periodType: string): boolean {
  if (awardScope === 'both') return true;
  return awardScope === periodType;
}

function mapPeriodRow(
  r: typeof evaluationPeriod.$inferSelect,
  parentPeriodKey?: string | null,
): EvaluationPeriodItem {
  return {
    id: r.id,
    periodType: r.periodType as 'month' | 'quarter',
    periodKey: r.periodKey,
    name: r.name,
    sortOrder: r.sortOrder,
    status: r.status,
    parentPeriodId: r.parentPeriodId ?? null,
    parentPeriodKey: parentPeriodKey ?? null,
    createdAt: toIso(r.createdAt),
    updatedAt: toIso(r.updatedAt),
  };
}

@Injectable()
export class EvaluationService {
  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly menuPermissionService: MenuPermissionService,
  ) {}

  private async assertAllowed(userId: string): Promise<void> {
    await this.menuPermissionService.assertMenuAllowed(userId, 'admin_statistics_months');
  }

  private async parentPeriodKeyForRow(
    r: typeof evaluationPeriod.$inferSelect,
  ): Promise<string | null> {
    if (r.periodType !== 'month' || !r.parentPeriodId) return null;
    const p = await this.db
      .select({ periodKey: evaluationPeriod.periodKey })
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, r.parentPeriodId))
      .limit(1);
    return p[0]?.periodKey ?? null;
  }

  /** 月度归属的季度 id；空字符串视为不归属 */
  private async resolveMonthParentId(
    raw: string | null | undefined,
  ): Promise<string | null> {
    const t = raw?.trim() ?? '';
    if (!t) return null;
    const parent = await this.db
      .select({ id: evaluationPeriod.id, periodType: evaluationPeriod.periodType })
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, t))
      .limit(1);
    if (!parent.length || parent[0]!.periodType !== 'quarter') {
      throw new BadRequestException('所属季度无效');
    }
    return t;
  }

  async listPeriods(
    userId: string,
    periodType?: 'month' | 'quarter',
  ): Promise<{ items: EvaluationPeriodItem[] }> {
    await this.assertAllowed(userId);
    const epParent = alias(evaluationPeriod, 'eval_period_parent');
    const order = [
      asc(evaluationPeriod.sortOrder),
      desc(evaluationPeriod.periodKey),
    ];
    const sel = {
      id: evaluationPeriod.id,
      periodType: evaluationPeriod.periodType,
      periodKey: evaluationPeriod.periodKey,
      name: evaluationPeriod.name,
      sortOrder: evaluationPeriod.sortOrder,
      status: evaluationPeriod.status,
      parentPeriodId: evaluationPeriod.parentPeriodId,
      createdAt: evaluationPeriod.createdAt,
      updatedAt: evaluationPeriod.updatedAt,
      parentPeriodKey: epParent.periodKey,
    };
    const rows =
      periodType != null
        ? await this.db
            .select(sel)
            .from(evaluationPeriod)
            .leftJoin(epParent, eq(evaluationPeriod.parentPeriodId, epParent.id))
            .where(eq(evaluationPeriod.periodType, periodType))
            .orderBy(...order)
        : await this.db
            .select(sel)
            .from(evaluationPeriod)
            .leftJoin(epParent, eq(evaluationPeriod.parentPeriodId, epParent.id))
            .orderBy(...order);
    return {
      items: rows.map((row) =>
        mapPeriodRow(
          {
            id: row.id,
            periodType: row.periodType,
            periodKey: row.periodKey,
            name: row.name,
            sortOrder: row.sortOrder,
            status: row.status,
            parentPeriodId: row.parentPeriodId,
            createdAt: row.createdAt,
            updatedAt: row.updatedAt,
          },
          row.parentPeriodKey,
        ),
      ),
    };
  }

  async createPeriod(
    userId: string,
    body: CreateEvaluationPeriodRequest,
  ): Promise<EvaluationPeriodItem> {
    await this.assertAllowed(userId);
    const pt = body.periodType;
    if (pt !== 'month' && pt !== 'quarter') {
      throw new BadRequestException('periodType 须为 month 或 quarter');
    }
    const periodKey = normalizePeriodKey(pt, body.periodKey);
    const dup = await this.db
      .select({ id: evaluationPeriod.id })
      .from(evaluationPeriod)
      .where(and(eq(evaluationPeriod.periodType, pt), eq(evaluationPeriod.periodKey, periodKey)))
      .limit(1);
    if (dup.length) {
      throw new BadRequestException(`周期 ${pt} / ${periodKey} 已存在`);
    }
    if (pt === 'quarter' && body.parentPeriodId?.trim()) {
      throw new BadRequestException('季度周期不能设置所属季度');
    }
    const parentId = pt === 'month' ? await this.resolveMonthParentId(body.parentPeriodId) : null;
    const id = randomUUID();
    await this.db.insert(evaluationPeriod).values({
      id,
      periodType: pt,
      periodKey,
      name: body.name?.trim() ?? '',
      sortOrder: body.sortOrder ?? 0,
      status: body.status?.trim() || 'open',
      parentPeriodId: parentId,
    });
    const row = await this.db
      .select()
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, id))
      .limit(1);
    const r0 = row[0]!;
    return mapPeriodRow(r0, await this.parentPeriodKeyForRow(r0));
  }

  async updatePeriod(
    userId: string,
    id: string,
    body: UpdateEvaluationPeriodRequest,
  ): Promise<EvaluationPeriodItem> {
    await this.assertAllowed(userId);
    const existing = await this.db
      .select()
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, id))
      .limit(1);
    if (!existing.length) {
      throw new NotFoundException('评选周期不存在');
    }
    const cur = existing[0]!;
    let periodType = cur.periodType as 'month' | 'quarter';
    if (body.periodType != null) {
      if (body.periodType !== 'month' && body.periodType !== 'quarter') {
        throw new BadRequestException('periodType 须为 month 或 quarter');
      }
      periodType = body.periodType;
    }
    let periodKey = cur.periodKey;
    if (body.periodKey != null && body.periodKey.trim() !== '') {
      periodKey = normalizePeriodKey(periodType, body.periodKey);
    }
    if (periodKey !== cur.periodKey || periodType !== cur.periodType) {
      const dup = await this.db
        .select({ id: evaluationPeriod.id })
        .from(evaluationPeriod)
        .where(
          and(
            eq(evaluationPeriod.periodType, periodType),
            eq(evaluationPeriod.periodKey, periodKey),
            ne(evaluationPeriod.id, id),
          ),
        )
        .limit(1);
      if (dup.length) {
        throw new BadRequestException(`周期 ${periodType} / ${periodKey} 已被占用`);
      }
    }
    if (periodType === 'quarter' && body.parentPeriodId?.trim()) {
      throw new BadRequestException('季度周期不能设置所属季度');
    }
    const patch: Partial<typeof evaluationPeriod.$inferInsert> = {};
    if (body.periodType != null) patch.periodType = body.periodType;
    if (body.periodKey != null && body.periodKey.trim() !== '') patch.periodKey = periodKey;
    if (body.name !== undefined) patch.name = body.name.trim();
    if (body.sortOrder !== undefined) patch.sortOrder = body.sortOrder;
    if (body.status !== undefined) patch.status = body.status.trim() || 'open';
    if (periodType === 'quarter') {
      patch.parentPeriodId = null;
    } else if (body.parentPeriodId !== undefined) {
      patch.parentPeriodId = await this.resolveMonthParentId(body.parentPeriodId);
    }
    if (Object.keys(patch).length) {
      await this.db.update(evaluationPeriod).set(patch).where(eq(evaluationPeriod.id, id));
    }
    const row = await this.db
      .select()
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, id))
      .limit(1);
    const r0 = row[0]!;
    return mapPeriodRow(r0, await this.parentPeriodKeyForRow(r0));
  }

  async removePeriod(userId: string, id: string): Promise<void> {
    await this.assertAllowed(userId);
    const existing = await this.db
      .select()
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, id))
      .limit(1);
    if (!existing.length) {
      throw new NotFoundException('评选周期不存在');
    }
    await this.db.delete(evaluationPeriod).where(eq(evaluationPeriod.id, id));
  }

  async listAwardTypes(userId: string): Promise<{ items: AwardTypeItem[] }> {
    await this.assertAllowed(userId);
    const rows = await this.db.select().from(awardType).orderBy(asc(awardType.sortOrder));
    return {
      items: rows.map((r) => ({
        code: r.code,
        name: r.name,
        scope: r.scope as AwardTypeItem['scope'],
        maxWinners: r.maxWinners,
        sortOrder: r.sortOrder,
        isSystem: r.isSystem === 1,
      })),
    };
  }

  /** 绩效记录中出现过的 period（去重），供季度下拉参考 */
  async listPerformancePeriods(userId: string): Promise<{ items: string[] }> {
    await this.assertAllowed(userId);
    const rows = await this.db
      .select({ period: performanceRecord.period })
      .from(performanceRecord)
      .groupBy(performanceRecord.period)
      .orderBy(desc(performanceRecord.period));
    return { items: rows.map((r) => r.period) };
  }

  async getLeaderboard(
    userId: string,
    params: {
      scope: 'month' | 'quarter';
      key: string;
      departmentIds?: string[];
    },
  ): Promise<PerformanceLeaderboardResponse> {
    await this.assertAllowed(userId);
    const scope = params.scope;
    if (scope !== 'month' && scope !== 'quarter') {
      throw new BadRequestException('scope 须为 month 或 quarter');
    }
    const keyNorm = normalizePeriodKey(scope, params.key);

    const clauses: SQL[] = [
      eq(performanceRecord.status, 'completed'),
      isNotNull(performanceRecord.totalScore),
    ];

    if (scope === 'month') {
      const { year, month } = parseYearMonth(keyNorm);
      const { start, endExclusive } = monthUpdatedRange(year, month);
      clauses.push(gte(performanceRecord.updatedAt, start));
      clauses.push(lt(performanceRecord.updatedAt, endExclusive));
    } else {
      clauses.push(eq(performanceRecord.period, keyNorm));
    }

    const deptIds = (params.departmentIds ?? []).filter(Boolean);
    if (deptIds.length > 0) {
      clauses.push(inArray(employeeHierarchy.departmentId, deptIds));
    }

    const rows = await this.db
      .select({
        recordId: performanceRecord.id,
        employeeId: performanceRecord.employeeId,
        totalScore: performanceRecord.totalScore,
        period: performanceRecord.period,
        employeeName: employeeHierarchy.name,
        departmentId: employeeHierarchy.departmentId,
        departmentName: employeeHierarchy.departmentName,
      })
      .from(performanceRecord)
      .innerJoin(
        employeeHierarchy,
        eq(performanceRecord.employeeId, employeeHierarchy.employeeId),
      )
      .where(and(...clauses))
      .orderBy(desc(performanceRecord.totalScore));

    const bestByEmployee = new Map<
      string,
      {
        recordId: string;
        employeeId: string;
        totalScore: number;
        period: string;
        employeeName: string | null;
        departmentId: string | null;
        departmentName: string | null;
      }
    >();

    for (const r of rows) {
      const score = Number(r.totalScore);
      if (!Number.isFinite(score)) continue;
      const prev = bestByEmployee.get(r.employeeId);
      if (!prev || score > prev.totalScore) {
        bestByEmployee.set(r.employeeId, {
          recordId: r.recordId,
          employeeId: r.employeeId,
          totalScore: score,
          period: r.period,
          employeeName: r.employeeName ?? null,
          departmentId: r.departmentId ?? null,
          departmentName: r.departmentName ?? null,
        });
      }
    }

    const sorted = [...bestByEmployee.values()].sort((a, b) => b.totalScore - a.totalScore);
    const items: PerformanceLeaderboardItem[] = sorted.map((r, i) => ({
      rank: i + 1,
      employeeId: r.employeeId,
      employeeName: r.employeeName || r.employeeId,
      departmentId: r.departmentId,
      departmentName: r.departmentName,
      totalScore: r.totalScore,
      performancePeriod: r.period,
      recordId: r.recordId,
    }));

    return { items, scope, key: keyNorm };
  }

  async listPeriodAwards(userId: string, periodId: string): Promise<{ items: PeriodAwardItem[] }> {
    await this.assertAllowed(userId);
    const p = await this.db
      .select()
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, periodId))
      .limit(1);
    if (!p.length) {
      throw new NotFoundException('评选周期不存在');
    }
    const rows = await this.db
      .select({
        id: periodAward.id,
        periodId: periodAward.periodId,
        awardCode: periodAward.awardCode,
        awardName: awardType.name,
        employeeId: periodAward.employeeId,
        employeeName: employeeHierarchy.name,
        performanceRecordId: periodAward.performanceRecordId,
        remark: periodAward.remark,
        createdBy: periodAward.createdBy,
        createdAt: periodAward.createdAt,
      })
      .from(periodAward)
      .innerJoin(awardType, eq(periodAward.awardCode, awardType.code))
      .leftJoin(
        employeeHierarchy,
        eq(periodAward.employeeId, employeeHierarchy.employeeId),
      )
      .where(eq(periodAward.periodId, periodId))
      .orderBy(asc(awardType.sortOrder), asc(periodAward.createdAt));
    return {
      items: rows.map((r) => ({
        id: r.id,
        periodId: r.periodId,
        awardCode: r.awardCode,
        awardName: r.awardName,
        employeeId: r.employeeId,
        employeeName: r.employeeName ?? null,
        performanceRecordId: r.performanceRecordId ?? null,
        remark: r.remark ?? null,
        createdBy: r.createdBy ?? null,
        createdAt: toIso(r.createdAt),
      })),
    };
  }

  async createPeriodAward(
    userId: string,
    body: CreatePeriodAwardRequest,
  ): Promise<PeriodAwardItem> {
    await this.assertAllowed(userId);
    const [periodRow] = await this.db
      .select()
      .from(evaluationPeriod)
      .where(eq(evaluationPeriod.id, body.periodId))
      .limit(1);
    if (!periodRow) {
      throw new NotFoundException('评选周期不存在');
    }
    const periodType = periodRow.periodType;
    const [at] = await this.db
      .select()
      .from(awardType)
      .where(eq(awardType.code, body.awardCode))
      .limit(1);
    if (!at) {
      throw new BadRequestException('奖项类型不存在');
    }
    if (!awardScopeMatches(at.scope, periodType)) {
      throw new BadRequestException('该奖项与当前周期类型不匹配');
    }
    if (at.maxWinners != null) {
      const [{ n }] = await this.db
        .select({ n: count() })
        .from(periodAward)
        .where(
          and(
            eq(periodAward.periodId, body.periodId),
            eq(periodAward.awardCode, body.awardCode),
          ),
        );
      if (Number(n) >= at.maxWinners) {
        throw new BadRequestException('该奖项获奖人数已达上限');
      }
    }
    if (body.performanceRecordId) {
      const [rec] = await this.db
        .select()
        .from(performanceRecord)
        .where(eq(performanceRecord.id, body.performanceRecordId))
        .limit(1);
      if (!rec) {
        throw new NotFoundException('绩效记录不存在');
      }
      if (rec.employeeId !== body.employeeId) {
        throw new BadRequestException('绩效记录与员工不匹配');
      }
    }
    const id = randomUUID();
    try {
      await this.db.insert(periodAward).values({
        id,
        periodId: body.periodId,
        awardCode: body.awardCode,
        employeeId: body.employeeId,
        performanceRecordId: body.performanceRecordId ?? null,
        remark: body.remark?.trim() ?? null,
        createdBy: userId,
      });
    } catch {
      throw new BadRequestException('该员工在此奖项下可能已存在记录');
    }
    const list = await this.listPeriodAwards(userId, body.periodId);
    const created = list.items.find((x) => x.id === id);
    return created!;
  }

  async removePeriodAward(userId: string, awardRowId: string): Promise<void> {
    await this.assertAllowed(userId);
    const existing = await this.db
      .select()
      .from(periodAward)
      .where(eq(periodAward.id, awardRowId))
      .limit(1);
    if (!existing.length) {
      throw new NotFoundException('获奖记录不存在');
    }
    await this.db.delete(periodAward).where(eq(periodAward.id, awardRowId));
  }
}
