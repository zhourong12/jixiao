import {
  Controller,
  Get,
  Post,
  Query,
  Body,
  Req,
} from '@nestjs/common';
import { NeedLogin } from '@lark-apaas/fullstack-nestjs-core';
import { NotificationService } from './notification.service';
import type { Request } from 'express';
import type {
  SendNotificationRequest,
  SendNotificationResponse,
  NotificationListItem,
  PaginationResponse,
} from '@shared/api.interface';

@Controller('api/admin/notifications')
export class NotificationController {
  // Notification controller
  constructor(private readonly notificationService: NotificationService) {}

  @Get()
  async list(
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    return this.notificationService.list({
      page: page ? parseInt(page, 10) : 1,
      pageSize: pageSize ? parseInt(pageSize, 10) : 20,
    });
  }

  @NeedLogin()
  @Post()
  async send(
    @Req() req: Request,
    @Body() body: SendNotificationRequest,
  ): Promise<SendNotificationResponse> {
    return this.notificationService.send(
      req.userContext?.userId,
      req.userContext?.userName || '',
      body,
    ) as Promise<SendNotificationResponse>;
  }
}
