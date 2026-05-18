package com.jixiao2.server.assessmentrule;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assessment-rules")
public class AssessmentRuleController {

  private final AssessmentRuleService assessmentRuleService;
  private final MenuPermissionService menuPermissionService;

  public AssessmentRuleController(
      AssessmentRuleService assessmentRuleService, MenuPermissionService menuPermissionService) {
    this.assessmentRuleService = assessmentRuleService;
    this.menuPermissionService = menuPermissionService;
  }

  private void assertCanRead(String userId) {
    if (menuPermissionService.isMenuAllowed(userId, "admin_assessment_rules")
        || menuPermissionService.isMenuAllowed(userId, "admin_templates")
        || menuPermissionService.isMenuAllowed(userId, "performance_batch_create")) {
      return;
    }
    menuPermissionService.assertMenuAllowed(userId, "admin_assessment_rules");
  }

  @GetMapping
  public Map<String, Object> list(
      @CurrentUser String userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "200") int pageSize) {
    assertCanRead(userId);
    return assessmentRuleService.list(page, pageSize);
  }

  @GetMapping("/{id}")
  public Map<String, Object> getById(@CurrentUser String userId, @PathVariable("id") String id) {
    assertCanRead(userId);
    return assessmentRuleService.getById(id);
  }

  @PostMapping
  public Map<String, Object> create(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_assessment_rules");
    return assessmentRuleService.create(userId, body);
  }

  @PatchMapping("/{id}")
  public Map<String, Boolean> update(
      @CurrentUser String userId, @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_assessment_rules");
    return assessmentRuleService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@CurrentUser String userId, @PathVariable("id") String id) {
    menuPermissionService.assertMenuAllowed(userId, "admin_assessment_rules");
    return assessmentRuleService.delete(id);
  }
}
