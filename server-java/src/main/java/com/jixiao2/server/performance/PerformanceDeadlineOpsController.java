package com.jixiao2.server.performance;

import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效节点截止运维接口：手动触发截止日提醒或自动流转（需 super_admin 或 performance_review_admin）。
 *
 * <p>测试自动流转：将 {@code asOfDate} 设为「截止日的次日」，或对 {@code forceNodes} 传入对应 key 强制视为已过期。
 */
@RestController
@RequestMapping("/api/performances/ops")
public class PerformanceDeadlineOpsController {

  private final PerformanceDeadlineOpsService opsService;
  private final PerformanceService performanceService;

  public PerformanceDeadlineOpsController(
      PerformanceDeadlineOpsService opsService, PerformanceService performanceService) {
    this.opsService = opsService;
    this.performanceService = performanceService;
  }

  /** 手动发送截止日飞书提醒（默认按当天是否为截止日判断）。 */
  @PostMapping("/deadline-reminders")
  public Map<String, Object> runReminders(
      @CurrentUser String userId, @RequestBody(required = false) Map<String, Object> body) {
    return opsService.runReminders(userId, body == null ? new java.util.LinkedHashMap<String, Object>() : body);
  }

  /**
   * 手动触发节点自动流转（测试用）。Body: {@code { "recordId": "...", "forceAdvance": true }}。
   * {@code forceAdvance} 默认 true：不校验截止日，按当前状态推进一步（含 final_review→issued、issued→completed）。
   * 未传 {@code forceAdvance} 或 false 时与定时任务一致，仅当 asOfDate 已过对应节点截止日才流转。
   */
  @PostMapping("/deadline-auto")
  public Map<String, Object> runAuto(
      @CurrentUser String userId, @RequestBody Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "请提供 recordId");
    }
    return opsService.runAuto(userId, body);
  }

  /** 超管驳回员工自评（自评总结不合格），退回员工修改。Body: {@code { "recordId", "reason" }}。 */
  @PostMapping("/reject-self-review")
  public Map<String, Object> rejectSelfReview(
      @CurrentUser String userId, @RequestBody Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "请提供 recordId 与 reason");
    }
    String recordId = body.get("recordId") == null ? "" : String.valueOf(body.get("recordId")).trim();
    String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
    if (recordId.isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "recordId 必填");
    }
    return performanceService.rejectSelfReviewBySuperAdmin(userId, recordId, reason);
  }
}
