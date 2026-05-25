package com.jixiao2.server.performance;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 绩效节点截止日自动流转（超时默认推进、员工逾期未确认自动签收）。 */
@Component
public class PerformanceDeadlineScheduler {

  private static final Logger log = LoggerFactory.getLogger(PerformanceDeadlineScheduler.class);

  private final PerformanceDeadlineAutoService deadlineAutoService;
  private final PerformanceDeadlineNotifyService deadlineNotifyService;

  public PerformanceDeadlineScheduler(
      PerformanceDeadlineAutoService deadlineAutoService,
      PerformanceDeadlineNotifyService deadlineNotifyService) {
    this.deadlineAutoService = deadlineAutoService;
    this.deadlineNotifyService = deadlineNotifyService;
  }

  /** 每天 01:10 检查节点自动流转（截止日次日及以后生效：final→issued、confirm→completed 等）。 */
  @Scheduled(cron = "0 10 1 * * ?")
  public void runDailyAuto() {
    if ("1".equals(System.getenv("DISABLE_PERFORMANCE_DEADLINE_CRON"))) {
      return;
    }
    try {
      Map<String, Object> stats =
          deadlineAutoService.runAll(java.time.LocalDate.now(), null, null, false);
      log.info(
          "绩效节点截止自动流转完成 advanced={} autoConfirmed={}",
          stats.get("advanced"),
          stats.get("autoConfirmed"));
    } catch (Exception e) {
      log.error("绩效节点截止自动流转失败", e);
    }
  }

  /** 每天 10:00 对截止日等于当天的记录发送飞书提醒。 */
  @Scheduled(cron = "0 0 10 * * ?")
  public void runDailyReminders() {
    if ("1".equals(System.getenv("DISABLE_PERFORMANCE_DEADLINE_CRON"))) {
      return;
    }
    try {
      int reminders = deadlineNotifyService.runRemindersForDate(java.time.LocalDate.now(), null, null, false);
      log.info("绩效节点截止日提醒完成 reminders={}", reminders);
    } catch (Exception e) {
      log.error("绩效节点截止日提醒失败", e);
    }
  }
}
