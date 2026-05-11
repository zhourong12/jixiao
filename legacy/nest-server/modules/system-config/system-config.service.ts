import { Injectable, Inject } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { systemConfig } from '@server/database/schema';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';

export interface SystemConfigItemDto {
  key: string;
  value: string;
  label: string;
  description: string;
  type: 'number' | 'string';
}

const CONFIG_SCHEMA: Omit<SystemConfigItemDto, 'value'>[] = [
  {
    key: 'manager_review_weight',
    label: '直属上级评审权重',
    description: '直属上级评分在总分中的占比，取值 0~1（如 0.7 表示 70%）',
    type: 'number',
  },
  {
    key: 'dotted_manager_review_weight',
    label: '虚线上级评审权重',
    description: '虚线上级评分在总分中的占比，取值 0~1（如 0.3 表示 30%）',
    type: 'number',
  },
];

@Injectable()
export class SystemConfigService {
  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly menuPermissionService: MenuPermissionService,
  ) {}

  async getAll(userId: string): Promise<{ items: SystemConfigItemDto[] }> {
    await this.menuPermissionService.assertMenuAllowed(userId, 'admin_system_config');
    const rows = await this.db
      .select({ configKey: systemConfig.configKey, configValue: systemConfig.configValue })
      .from(systemConfig);

    const valueMap = new Map(rows.map((r) => [r.configKey, r.configValue]));

    const items: SystemConfigItemDto[] = CONFIG_SCHEMA.map((schema) => ({
      ...schema,
      value: valueMap.get(schema.key) ?? '',
    }));

    return { items };
  }

  async update(
    userId: string,
    body: { configs: Array<{ key: string; value: string }> },
  ): Promise<{ success: boolean }> {
    await this.menuPermissionService.assertMenuAllowed(userId, 'admin_system_config');

    const validKeys = new Set(CONFIG_SCHEMA.map((s) => s.key));
    for (const item of body.configs) {
      if (!validKeys.has(item.key)) continue;

      const schema = CONFIG_SCHEMA.find((s) => s.key === item.key);
      if (schema?.type === 'number') {
        const num = parseFloat(item.value);
        if (!Number.isFinite(num)) continue;
      }

      const existing = await this.db
        .select({ configKey: systemConfig.configKey })
        .from(systemConfig)
        .where(eq(systemConfig.configKey, item.key))
        .limit(1);

      if (existing.length > 0) {
        await this.db
          .update(systemConfig)
          .set({ configValue: item.value })
          .where(eq(systemConfig.configKey, item.key));
      } else {
        await this.db.insert(systemConfig).values({
          configKey: item.key,
          configValue: item.value,
        });
      }
    }

    return { success: true };
  }
}
