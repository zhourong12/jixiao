package com.jixiao2.server.performance;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonthlyPerformanceScheduler {

  private static final Logger log = LoggerFactory.getLogger(MonthlyPerformanceScheduler.class);

  private final PerformanceService performanceService;

  public MonthlyPerformanceScheduler(PerformanceService performanceService) {
    this.performanceService = performanceService;
  }

  /** 每天 23:30 检查是否为当月最后一天；若是，则为本自然月（YYYY-MM）批量创建「待选模板」绩效占位。 */
  @Scheduled(cron = "0 30 23 * * ?")
  public void handleLastDayOfMonth() {
    if ("1".equals(System.getenv("DISABLE_MONTHLY_PERFORMANCE_CRON"))) {
      return;
    }
    LocalDate today = LocalDate.now();
    LocalDate tomorrow = today.plusDays(1);
    if (tomorrow.getMonthValue() == today.getMonthValue()) {
      return;
    }
    String period = today.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    try {
      Map<String, Object> r = performanceService.ensureMonthlyPerformanceRecordsForPeriod(period);
      log.info(
          "月底绩效占位创建完成 period={} created={} skipped={}",
          period,
          r.get("created"),
          r.get("skipped"));
    } catch (Exception e) {
      log.error("月底绩效占位创建失败 period=" + period, e);
    }
  }

}
