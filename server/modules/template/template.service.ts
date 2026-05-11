import { Injectable, NotFoundException } from '@nestjs/common';
import { Inject } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { eq, desc, count as drizzleCount } from 'drizzle-orm';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { performanceTemplate } from '@server/database/schema';
import type { PerformanceIndicator } from '@shared/api.interface';

interface ListParams {
  page: number;
  pageSize: number;
}

@Injectable()
export class TemplateService {
  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
  ) {}

  async list(params: ListParams) {
    const totalResult = await this.db
      .select({ count: drizzleCount() })
      .from(performanceTemplate);
    const total = Number(totalResult[0].count);

    const items = await this.db
      .select({
        id: performanceTemplate.id,
        name: performanceTemplate.name,
        position: performanceTemplate.position,
        indicators: performanceTemplate.indicators,
        status: performanceTemplate.status,
        version: performanceTemplate.version,
        createdAt: performanceTemplate.createdAt,
      })
      .from(performanceTemplate)
      .orderBy(desc(performanceTemplate.createdAt))
      .limit(params.pageSize)
      .offset((params.page - 1) * params.pageSize);

    return {
      items: items.map((item) => ({
        id: item.id,
        name: item.name,
        position: item.position,
        indicatorCount: (item.indicators as PerformanceIndicator[]).length,
        status: item.status as 'enabled' | 'disabled',
        version: item.version,
        createdAt: item.createdAt.toISOString(),
      })),
      total,
      page: params.page,
      pageSize: params.pageSize,
    };
  }

  async getById(id: string) {
    const result = await this.db
      .select()
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, id));

    if (result.length === 0) {
      throw new NotFoundException('模板不存在');
    }

    const item = result[0];
    return {
      id: item.id,
      name: item.name,
      position: item.position,
      indicators: item.indicators as PerformanceIndicator[],
      status: item.status as 'enabled' | 'disabled',
      version: item.version,
      createdAt: item.createdAt.toISOString(),
      updatedAt: item.updatedAt.toISOString(),
    };
  }

  async create(
    userId: string,
    body: { name: string; position: string; indicators: PerformanceIndicator[] },
  ) {
    const id = randomUUID();
    await this.db.insert(performanceTemplate).values({
      id,
      name: body.name,
      position: body.position,
      indicators: body.indicators,
      createdBy: userId,
      updatedBy: userId,
    });

    return { id };
  }

  async update(
    id: string,
    body: { name?: string; position?: string; indicators?: PerformanceIndicator[] },
  ) {
    const existing = await this.db
      .select()
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, id));

    if (existing.length === 0) {
      throw new NotFoundException('模板不存在');
    }

    const updateData: Record<string, unknown> = {};
    if (body.name !== undefined) updateData.name = body.name;
    if (body.position !== undefined) updateData.position = body.position;
    if (body.indicators !== undefined) {
      updateData.indicators = body.indicators;
      updateData.version = (existing[0].version || 0) + 1;
    }

    await this.db
      .update(performanceTemplate)
      .set(updateData)
      .where(eq(performanceTemplate.id, id));

    return { success: true };
  }

  async toggleStatus(id: string) {
    const existing = await this.db
      .select()
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, id));

    if (existing.length === 0) {
      throw new NotFoundException('模板不存在');
    }

    const currentStatus = existing[0].status;
    const newStatus = currentStatus === 'enabled' ? 'disabled' : 'enabled';

    await this.db
      .update(performanceTemplate)
      .set({ status: newStatus })
      .where(eq(performanceTemplate.id, id));

    return { success: true, newStatus };
  }

  async copy(id: string) {
    const existing = await this.db
      .select()
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, id));

    if (existing.length === 0) {
      throw new NotFoundException('模板不存在');
    }

    const source = existing[0];
    const newName = `${source.name} (副本)`;

    const newId = randomUUID();
    await this.db.insert(performanceTemplate).values({
      id: newId,
      name: newName,
      position: source.position,
      indicators: source.indicators,
      status: 'disabled',
      version: 1,
    });

    return { newTemplateId: newId };
  }

  async delete(id: string) {
    const existing = await this.db
      .select()
      .from(performanceTemplate)
      .where(eq(performanceTemplate.id, id));

    if (existing.length === 0) {
      throw new NotFoundException('模板不存在');
    }

    await this.db
      .delete(performanceTemplate)
      .where(eq(performanceTemplate.id, id));

    return { success: true };
  }
}
