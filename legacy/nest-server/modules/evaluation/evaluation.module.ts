import { Module } from '@nestjs/common';
import { MenuPermissionModule } from '../menu-permission/menu-permission.module';
import { EvaluationService } from './evaluation.service';
import { EvaluationPeriodController } from './evaluation-period.controller';
import { AwardTypeController } from './award-type.controller';
import { EvaluationOpsController } from './evaluation-ops.controller';

@Module({
  imports: [MenuPermissionModule],
  controllers: [
    EvaluationPeriodController,
    AwardTypeController,
    EvaluationOpsController,
  ],
  providers: [EvaluationService],
})
export class EvaluationModule {}
