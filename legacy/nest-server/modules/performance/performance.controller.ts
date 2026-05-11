import {
  Controller,
  Get,
  Post,
  Patch,
  Param,
  Query,
  Body,
  Req,
  UseGuards,
} from '@nestjs/common';
import { PerformanceService } from './performance.service';
import type { Request } from 'express';
import type {
  ReviewItem,
  GoalSettingItem,
  ApproveGoalRequest,
  FinalReviewRequest,
  CreatePerformanceRequest,
  SelectTemplateRequest,
  CalibrationReviewRequest,
} from '@shared/api.interface';
import { SessionUserGuard } from '../../guards/session-user.guard';

@Controller('api/performances')
@UseGuards(SessionUserGuard)
export class PerformanceController {
  constructor(private readonly performanceService: PerformanceService) {}

  @Get()
  async list(
    @Req() req: Request,
    @Query('status') status?: string,
    @Query('focus') focus?: string,
    @Query('period') period?: string,
    @Query('departmentId') departmentId?: string,
    @Query('employeeName') employeeName?: string,
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    const userId = req.userContext!.userId!;
    const userRole = await this.performanceService.getUserRole(userId);
    const result = await this.performanceService.list(userId, {
      status,
      focus,
      period,
      departmentId,
      employeeName,
      page: page ? parseInt(page, 10) : 1,
      pageSize: pageSize ? parseInt(pageSize, 10) : 20,
    });
    return { ...result, userRole };
  }

  @Get('create/month-periods')
  async listMonthPeriodsForCreate(@Req() req: Request) {
    return this.performanceService.listMonthPeriodsForCreate(req.userContext!.userId!);
  }

  @Get('export')
  async export(
    @Req() req: Request,
    @Query('status') status?: string,
    @Query('focus') focus?: string,
    @Query('period') period?: string,
    @Query('departmentId') departmentId?: string,
    @Query('employeeName') employeeName?: string,
  ) {
    const userId = req.userContext!.userId!;
    return this.performanceService.exportData(userId, {
      status,
      focus,
      period,
      departmentId,
      employeeName,
    });
  }

  @Get('calibration/supervisor-queue')
  async listSupervisorCalibrationQueue(
    @Req() req: Request,
    @Query('period') period?: string,
    @Query('departmentId') departmentId?: string,
    @Query('employeeName') employeeName?: string,
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    const userId = req.userContext!.userId!;
    return this.performanceService.listSupervisorCalibrationQueue(userId, {
      period,
      departmentId,
      employeeName,
      page: page ? parseInt(page, 10) : 1,
      pageSize: pageSize ? parseInt(pageSize, 10) : 20,
    });
  }

  @Get(':id')
  async getDetail(@Req() req: Request, @Param('id') id: string) {
    const userId = req.userContext!.userId!;
    return this.performanceService.getDetail(userId, id);
  }

  @Patch(':id')
  async saveDraft(
    @Req() req: Request,
    @Param('id') id: string,
    @Body()
    body: {
      reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
      content: ReviewItem[] | GoalSettingItem[];
      personalSummary?: string;
    },
  ) {
    return this.performanceService.saveDraft(req.userContext!.userId!, id, body);
  }

  @Post(':id/submit')
  async submit(
    @Req() req: Request,
    @Param('id') id: string,
    @Body()
    body: {
      reviewType: 'goal' | 'self' | 'manager' | 'dotted_manager';
      content: ReviewItem[] | GoalSettingItem[];
      personalSummary?: string;
    },
  ) {
    return this.performanceService.submit(req.userContext!.userId!, id, body);
  }

  @Post(':id/reject')
  async reject(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: { reason: string },
  ) {
    return this.performanceService.reject(req.userContext!.userId!, id, body.reason);
  }

  @Post(':id/approve-goal')
  async approveGoal(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: ApproveGoalRequest,
  ) {
    return this.performanceService.approveGoal(req.userContext!.userId!, id, body);
  }

  @Post(':id/final-review')
  async finalReview(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: FinalReviewRequest,
  ) {
    return this.performanceService.finalReview(req.userContext!.userId!, id, body);
  }

  @Post()
  async create(
    @Req() req: Request,
    @Body() body: CreatePerformanceRequest,
  ) {
    return this.performanceService.createBatch(req.userContext!.userId!, body);
  }

  @Post(':id/select-template')
  async selectTemplate(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: SelectTemplateRequest,
  ) {
    return this.performanceService.selectTemplate(
      req.userContext!.userId!,
      id,
      body.templateId,
    );
  }

  @Post(':id/calibrate')
  async calibrate(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: CalibrationReviewRequest,
  ) {
    return this.performanceService.calibrate(
      req.userContext!.userId!,
      id,
      body,
    );
  }
}
