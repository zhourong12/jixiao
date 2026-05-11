import { Injectable, Logger } from '@nestjs/common';
import { Cron } from '@nestjs/schedule';
import { PerformanceService } from '../performance/performance.service';

@Injectable()
export class MonthlyPerformanceScheduler {
  private readonly log = new Logger(MonthlyPerformanceScheduler.name);

  constructor(private readonly performanceService: PerformanceService) {}

  /**
   * 每天 23:30 检查是否为当月最后一天；若是，则为本自然月（YYYY-MM）批量创建「待选模板」绩效占位。
   * 设置环境变量 DISABLE_MONTHLY_PERFORMANCE_CRON=1 可关闭。
   */
  @Cron('0 30 23 * * *')
  async handleLastDayOfMonth(): Promise<void> {
    if (process.env.DISABLE_MONTHLY_PERFORMANCE_CRON === '1') {
      return;
    }
    const d = new Date();
    const tomorrow = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1);
    if (tomorrow.getMonth() === d.getMonth()) {
      return;
    }
    const period = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    try {
      const r = await this.performanceService.ensureMonthlyPerformanceRecordsForPeriod(period);
      this.log.log(`月底绩效占位创建完成 period=${period} created=${r.created} skipped=${r.skipped}`);
    } catch (e) {
      this.log.error(`月底绩效占位创建失败 period=${period}`, e instanceof Error ? e.stack : String(e));
    }
  }
}
