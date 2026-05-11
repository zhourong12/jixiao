import { Controller, Get, Req, UseGuards } from '@nestjs/common';
import type { Request } from 'express';
import { SessionUserGuard } from '../../guards/session-user.guard';
import { EvaluationService } from './evaluation.service';

@Controller('api/admin/award-types')
@UseGuards(SessionUserGuard)
export class AwardTypeController {
  constructor(private readonly evaluationService: EvaluationService) {}

  @Get()
  list(@Req() req: Request) {
    return this.evaluationService.listAwardTypes(req.userContext!.userId!);
  }
}
