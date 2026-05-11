import { BadRequestException, Inject, Injectable } from '@nestjs/common';
import { eq, inArray } from 'drizzle-orm';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from './app-database';
import { systemConfig } from './schema';

const KEYS = ['feishu_app_id', 'feishu_app_secret'] as const;

@Injectable()
export class FeishuCredentialsService {
  constructor(@Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase) {}

  /**
   * 解析飞书应用凭证：优先环境变量，其次 system_config 表。
   */
  async resolve(): Promise<{ appId: string; appSecret: string }> {
    const envId = process.env.FEISHU_APP_ID?.trim();
    const envSecret = process.env.FEISHU_APP_SECRET?.trim();
    if (envId && envSecret) {
      return { appId: envId, appSecret: envSecret };
    }

    const rows = await this.db
      .select({
        configKey: systemConfig.configKey,
        configValue: systemConfig.configValue,
      })
      .from(systemConfig)
      .where(inArray(systemConfig.configKey, [...KEYS]));

    const map = new Map(rows.map((r) => [r.configKey, r.configValue.trim()]));
    const appId = map.get('feishu_app_id') ?? '';
    const appSecret = map.get('feishu_app_secret') ?? '';
    if (!appId || !appSecret) {
      throw new BadRequestException(
        '未配置飞书应用：请设置环境变量 FEISHU_APP_ID / FEISHU_APP_SECRET，或在 system_config 表写入 feishu_app_id、feishu_app_secret',
      );
    }
    return { appId, appSecret };
  }

  /** 仅 App Id（OAuth 授权页可只读 id） */
  async resolveAppIdOnly(): Promise<string> {
    const envId = process.env.FEISHU_APP_ID?.trim();
    if (envId) return envId;
    const rows = await this.db
      .select({ configValue: systemConfig.configValue })
      .from(systemConfig)
      .where(eq(systemConfig.configKey, 'feishu_app_id'))
      .limit(1);
    const id = rows[0]?.configValue?.trim() ?? '';
    if (!id) {
      throw new BadRequestException(
        '未配置飞书 App Id：请设置 FEISHU_APP_ID 或在 system_config 写入 feishu_app_id',
      );
    }
    return id;
  }
}
