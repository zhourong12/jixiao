package com.jixiao2.server.evaluation;

import com.jixiao2.server.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/evaluation-periods")
public class EvaluationPeriodController {

  private final EvaluationService evaluationService;

  public EvaluationPeriodController(EvaluationService evaluationService) {
    this.evaluationService = evaluationService;
  }

  @GetMapping
  public Map<String, Object> list(
      @CurrentUser String userId, @RequestParam(name = "period_type", required = false) String periodType) {
    return evaluationService.listPeriods(userId, periodType);
  }

  @PostMapping
  public Map<String, Object> create(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    return evaluationService.createPeriod(userId, body);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @CurrentUser String userId, @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return evaluationService.updatePeriod(userId, id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> remove(@CurrentUser String userId, @PathVariable("id") String id) {
    evaluationService.removePeriod(userId, id);
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }
}
