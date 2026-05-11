package com.jixiao2.server.evaluation;

import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/award-types")
public class AwardTypeController {

  private final EvaluationService evaluationService;

  public AwardTypeController(EvaluationService evaluationService) {
    this.evaluationService = evaluationService;
  }

  @GetMapping
  public Map<String, Object> list(@CurrentUser String userId) {
    return evaluationService.listAwardTypes(userId);
  }
}
