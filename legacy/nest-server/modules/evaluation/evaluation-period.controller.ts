import {
  Controller,
  Get,
  Post,
  Put,
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
import type {
  CreateEvaluationPeriodRequest,
  UpdateEvaluationPeriodRequest,
} from '@shared/api.interface';

@Controller('api/admin/evaluation-periods')
@UseGuards(SessionUserGuard)
export class EvaluationPeriodController {
  constructor(private readonly evaluationService: EvaluationService) {}

  @Get()
  list(
    @Req() req: Request,
    @Query('period_type') periodType?: 'month' | 'quarter',
  ) {
    return this.evaluationService.listPeriods(req.userContext!.userId!, periodType);
  }

  @Post()
  create(@Req() req: Request, @Body() body: CreateEvaluationPeriodRequest) {
    return this.evaluationService.createPeriod(req.userContext!.userId!, body);
  }

  @Put(':id')
  update(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: UpdateEvaluationPeriodRequest,
  ) {
    return this.evaluationService.updatePeriod(req.userContext!.userId!, id, body);
  }

  @Delete(':id')
  async remove(@Req() req: Request, @Param('id') id: string) {
    await this.evaluationService.removePeriod(req.userContext!.userId!, id);
    return { success: true };
  }
}
