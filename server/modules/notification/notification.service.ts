import { Injectable, Logger } from '@nestjs/common';
import { Inject } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { CapabilityService } from '@lark-apaas/fullstack-nestjs-core';
import { notification } from '@server/database/schema';
import { count } from 'drizzle-orm';
import type { SendType } from '@shared/api.interface';
import type {
  SendPerformanceNotificationOneInput,
  SendPerformanceNotificationOneOutput,
} from '@shared/plugin-types';

const PLUGIN_INSTANCE_ID = 'send_performance_notification_1';
const ACTION_KEY = 'send_feishu_message';

interface ListParams {
  page: number;
  pageSize: number;
}

@Injectable()
export class NotificationService {
  private readonly logger = new Logger(NotificationService.name);

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly capabilityService: CapabilityService,
  ) {}

  async list(params: ListParams) {
    const offset = (params.page - 1) * params.pageSize;

    const itemsResult = await this.db
      .select({
        id: notification.id,
        title: notification.title,
        sendType: notification.sendType,
        sendTime: notification.createdAt,
        senderName: notification.createdBy,
        readCount: notification.readCount,
        totalCount: notification.totalCount,
      })
      .from(notification)
      .orderBy(notification.createdAt)
      .limit(params.pageSize)
      .offset(offset);

    const totalResult = await this.db
      .select({ count: count() })
      .from(notification);

    const total = Number(totalResult[0]?.count ?? 0);

    const items = itemsResult.map((item) => ({
      id: item.id,
      title: item.title,
      sendType: item.sendType,
      sendTime: item.sendTime.toISOString(),
      senderName: item.senderName,
      readCount: item.readCount,
      totalCount: item.totalCount,
    }));

    return {
      items,
      total,
      page: params.page,
      pageSize: params.pageSize,
    };
  }

  async send(
    userId: string | undefined,
    userName: string,
    body: { title: string; content: string; sendType: string; targetIds: string[] },
  ) {
    if (!userId) {
      throw new Error('用户未登录，无法发送通知');
    }

    if (!body.targetIds || body.targetIds.length === 0) {
      throw new Error('接收人列表不能为空');
    }

    // 1. 创建通知记录
    const id = randomUUID();
    await this.db.insert(notification).values({
      id,
      title: body.title,
      content: body.content,
      sendType: body.sendType as SendType,
      targetIds: body.targetIds,
      senderId: userId,
      totalCount: body.targetIds.length,
      readCount: 0,
    });

    this.logger.log(
      `通知记录已创建: id=${id}, targetCount=${body.targetIds.length}`,
    );

    // 2. 调用飞书插件发送通知
    try {
      const input: SendPerformanceNotificationOneInput = {
        notification_title: body.title,
        notification_content: body.content,
        receiver_user_ids: body.targetIds,
      };

      const result = (await this.capabilityService
        .load(PLUGIN_INSTANCE_ID)
        .call(ACTION_KEY, input)) as SendPerformanceNotificationOneOutput;

      if (result?.success) {
        this.logger.log(
          `飞书通知发送成功: notificationId=${id}`,
        );
      } else {
        this.logger.error(
          `飞书通知发送返回失败: notificationId=${id}`,
        );
      }

      return {
        success: result?.success ?? false,
        notificationId: id,
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      this.logger.error(
        `飞书通知发送异常: notificationId=${id}, error=${errorMessage}`,
      );

      // 通知记录已落库，返回部分成功状态
      return {
        success: false,
        notificationId: id,
        error: errorMessage,
      };
    }
  }
}
