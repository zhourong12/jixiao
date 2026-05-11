import {
  Controller,
  Get,
  Post,
  Delete,
  Body,
  Param,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { SessionUserGuard } from '../../guards/session-user.guard';
import { EvaluationService } from './evaluation.service';
import type { CreatePeriodAwardRequest } from '@shared/api.interface';

@Controller('api/admin/evaluation')
@UseGuards(SessionUserGuard)
export class EvaluationOpsController {
  constructor(private readonly evaluationService: EvaluationService) {}

  @Get('performance-periods')
  performancePeriods(@Req() req: Request) {
    return this.evaluationService.listPerformancePeriods(req.userContext!.userId!);
  }

  @Get('leaderboard')
  leaderboard(
    @Req() req: Request,
    @Query('scope') scope: 'month' | 'quarter',
    @Query('key') key: string,
    @Query('departmentIds') departmentIdsRaw?: string,
  ) {
    const departmentIds = departmentIdsRaw
      ? departmentIdsRaw
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      : undefined;
    return this.evaluationService.getLeaderboard(req.userContext!.userId!, {
      scope,
      key,
      departmentIds,
    });
  }

  @Get('awards')
  listAwards(@Req() req: Request, @Query('periodId') periodId: string) {
    return this.evaluationService.listPeriodAwards(req.userContext!.userId!, periodId);
  }

  @Post('awards')
  createAward(@Req() req: Request, @Body() body: CreatePeriodAwardRequest) {
    return this.evaluationService.createPeriodAward(req.userContext!.userId!, body);
  }

  @Delete('awards/:id')
  async removeAward(@Req() req: Request, @Param('id') id: string) {
    await this.evaluationService.removePeriodAward(req.userContext!.userId!, id);
    return { success: true };
  }
}
