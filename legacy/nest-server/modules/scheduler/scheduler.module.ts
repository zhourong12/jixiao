import { Module } from '@nestjs/common';
import { PerformanceModule } from '../performance/performance.module';
import { MonthlyPerformanceScheduler } from './monthly-performance.scheduler';

@Module({
  imports: [PerformanceModule],
  providers: [MonthlyPerformanceScheduler],
})
export class SchedulerModule {}
