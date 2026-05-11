import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Param,
  Query,
  Body,
  Req,
} from '@nestjs/common';
import { NeedLogin } from '@lark-apaas/fullstack-nestjs-core';
import { TemplateService } from './template.service';
import type { Request } from 'express';
import type { PerformanceIndicator } from '@shared/api.interface';

@Controller('api/admin/templates')
export class TemplateController {
  constructor(private readonly templateService: TemplateService) {}

  @Get()
  async list(
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    return this.templateService.list({
      page: page ? parseInt(page, 10) : 1,
      pageSize: pageSize ? parseInt(pageSize, 10) : 20,
    });
  }

  @Get(':id')
  async getById(@Param('id') id: string) {
    return this.templateService.getById(id);
  }

  @NeedLogin()
  @Post()
  async create(
    @Req() req: Request,
    @Body() body: { name: string; position: string; indicators: PerformanceIndicator[] },
  ) {
    return this.templateService.create(req.userContext.userId, body);
  }

  @NeedLogin()
  @Patch(':id')
  async update(
    @Param('id') id: string,
    @Body() body: { name?: string; position?: string; indicators?: PerformanceIndicator[] },
  ) {
    return this.templateService.update(id, body);
  }

  @NeedLogin()
  @Post(':id/toggle-status')
  async toggleStatus(@Param('id') id: string) {
    return this.templateService.toggleStatus(id);
  }

  @NeedLogin()
  @Post(':id/copy')
  async copy(@Param('id') id: string) {
    return this.templateService.copy(id);
  }

  @NeedLogin()
  @Delete(':id')
  async delete(@Param('id') id: string) {
    return this.templateService.delete(id);
  }
}
