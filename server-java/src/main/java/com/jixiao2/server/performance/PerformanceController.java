package com.jixiao2.server.performance;

import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
public class PerformanceController {

  private final PerformanceService performanceService;

  public PerformanceController(PerformanceService performanceService) {
    this.performanceService = performanceService;
  }

  @GetMapping
  public Map<String, Object> list(
      @CurrentUser String userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String focus,
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String subjectCode,
      @RequestParam(required = false) String departmentId,
      @RequestParam(required = false) String employeeName,
      @RequestParam(required = false, defaultValue = "1") int page,
      @RequestParam(required = false, defaultValue = "20") int pageSize) {
    Map<String, Object> result =
        performanceService.list(
            userId, status, focus, period, subjectCode, departmentId, employeeName, page, pageSize);
    result.put("userRole", performanceService.getUserRole(userId));
    return result;
  }

  @GetMapping("/create/month-periods")
  public Map<String, Object> listMonthPeriodsForCreate(@CurrentUser String userId) {
    return performanceService.listMonthPeriodsForCreate(userId);
  }

  @GetMapping("/filter/month-periods")
  public Map<String, Object> listMonthPeriodsForFilter(@CurrentUser String userId) {
    return performanceService.listMonthPeriodsForFilter(userId);
  }

  @GetMapping("/export")
  public Map<String, Object> export(
      @CurrentUser String userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String focus,
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String subjectCode,
      @RequestParam(required = false) String departmentId,
      @RequestParam(required = false) String employeeName) {
    return performanceService.exportData(
        userId, status, focus, period, subjectCode, departmentId, employeeName);
  }

  @GetMapping("/calibration/supervisor-queue")
  public Map<String, Object> listSupervisorCalibrationQueue(
      @CurrentUser String userId,
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String subjectCode,
      @RequestParam(required = false) String departmentId,
      @RequestParam(required = false) String employeeName,
      @RequestParam(required = false, defaultValue = "1") int page,
      @RequestParam(required = false, defaultValue = "20") int pageSize) {
    return performanceService.listSupervisorCalibrationQueue(
        userId, period, subjectCode, departmentId, employeeName, page, pageSize);
  }

  @GetMapping("/{id}")
  public Map<String, Object> getDetail(@CurrentUser String userId, @PathVariable String id) {
    return performanceService.getDetail(userId, id);
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@CurrentUser String userId, @PathVariable String id) {
    return performanceService.softDelete(userId, id);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> saveDraft(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    return performanceService.saveDraft(userId, id, body);
  }

  @PostMapping("/{id}/submit")
  public Map<String, Object> submit(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body)
      throws Exception {
    return performanceService.submit(userId, id, body);
  }

  @PostMapping("/{id}/reject")
  public Map<String, Object> reject(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
    return performanceService.reject(userId, id, reason);
  }

  @PostMapping("/{id}/approve-goal")
  public Map<String, Object> approveGoal(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    return performanceService.approveGoal(userId, id, body);
  }

  @PostMapping("/{id}/final-review")
  public Map<String, Object> finalReview(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    return performanceService.finalReview(userId, id, body);
  }

  @PostMapping
  public Map<String, Object> create(
      @CurrentUser String userId, @RequestBody Map<String, Object> body) {
    return performanceService.createBatch(userId, body);
  }

  @PostMapping("/{id}/select-template")
  public Map<String, Object> selectTemplate(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    String templateId = body.get("templateId") == null ? "" : String.valueOf(body.get("templateId"));
    return performanceService.selectTemplate(userId, id, templateId);
  }

  @PutMapping("/{id}/goal-indicators")
  public Map<String, Object> saveGoalIndicators(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    return performanceService.saveGoalIndicators(userId, id, body);
  }

  @PostMapping("/{id}/calibrate")
  public Map<String, Object> calibrate(
      @CurrentUser String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
    return performanceService.calibrate(userId, id, body);
  }

  @PostMapping("/{id}/confirm-result")
  public Map<String, Object> confirmResult(
      @CurrentUser String userId, @PathVariable String id) {
    return performanceService.confirmResult(userId, id);
  }
}
