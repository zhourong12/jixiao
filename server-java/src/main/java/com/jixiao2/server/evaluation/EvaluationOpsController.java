package com.jixiao2.server.evaluation;

import com.jixiao2.server.web.CurrentUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/evaluation")
public class EvaluationOpsController {

  private final EvaluationService evaluationService;

  public EvaluationOpsController(EvaluationService evaluationService) {
    this.evaluationService = evaluationService;
  }

  @GetMapping("/performance-periods")
  public Map<String, Object> performancePeriods(@CurrentUser String userId) {
    return evaluationService.listPerformancePeriods(userId);
  }

  @GetMapping("/leaderboard")
  public Map<String, Object> leaderboard(
      @CurrentUser String userId,
      @RequestParam String scope,
      @RequestParam String key,
      @RequestParam(name = "departmentIds", required = false) String departmentIdsRaw) {
    List<String> departmentIds = null;
    if (departmentIdsRaw != null && !departmentIdsRaw.trim().isEmpty()) {
      departmentIds = new ArrayList<String>();
      for (String part : departmentIdsRaw.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          departmentIds.add(trimmed);
        }
      }
    }
    return evaluationService.getLeaderboard(userId, scope, key, departmentIds);
  }

  @GetMapping("/awards")
  public Map<String, Object> listAwards(@CurrentUser String userId, @RequestParam String periodId) {
    return evaluationService.listPeriodAwards(userId, periodId);
  }

  @PostMapping("/awards")
  public Map<String, Object> createAward(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    return evaluationService.createPeriodAward(userId, body);
  }

  @DeleteMapping("/awards/{id}")
  public Map<String, Boolean> removeAward(@CurrentUser String userId, @PathVariable("id") String id) {
    evaluationService.removePeriodAward(userId, id);
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }
}
