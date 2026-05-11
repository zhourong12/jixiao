import { Controller, Get, Patch, Body, Req, UseGuards } from '@nestjs/common';
import type { Request } from 'express';
import { SessionUserGuard } from '../../guards/session-user.guard';
import { SystemConfigService, type SystemConfigItemDto } from './system-config.service';

@Controller('api/admin/system-config')
@UseGuards(SessionUserGuard)
export class SystemConfigController {
  constructor(private readonly systemConfigService: SystemConfigService) {}

  @Get()
  async getAll(@Req() req: Request): Promise<{ items: SystemConfigItemDto[] }> {
    return this.systemConfigService.getAll(req.userContext!.userId!);
  }

  @Patch()
  async update(
    @Req() req: Request,
    @Body() body: { configs: Array<{ key: string; value: string }> },
  ): Promise<{ success: boolean }> {
    return this.systemConfigService.update(req.userContext!.userId!, body);
  }
}
