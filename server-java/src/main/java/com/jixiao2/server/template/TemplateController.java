package com.jixiao2.server.template;

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
@RequestMapping("/api/admin/templates")
public class TemplateController {

  private final TemplateService templateService;

  public TemplateController(TemplateService templateService) {
    this.templateService = templateService;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String type) {
    return templateService.list(page, pageSize, type);
  }

  @GetMapping("/{id}")
  public Map<String, Object> getById(@PathVariable("id") String id) {
    return templateService.getById(id);
  }

  @PostMapping
  public Map<String, Object> create(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    return templateService.create(userId, body);
  }

  @PatchMapping("/{id}")
  public Map<String, Boolean> update(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return templateService.update(id, body);
  }

  @PostMapping("/{id}/toggle-status")
  public Map<String, Object> toggleStatus(@PathVariable("id") String id) {
    return templateService.toggleStatus(id);
  }

  @PostMapping("/{id}/copy")
  public Map<String, Object> copy(@PathVariable("id") String id) {
    return templateService.copy(id);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@PathVariable("id") String id) {
    return templateService.delete(id);
  }
}
