package com.jixiao2.server.performance;

import com.jixiao2.server.menu.MenuPermissionService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 绩效节点截止：手动触发提醒/自动流转（测试与运维）。 */
@Service
public class PerformanceDeadlineOpsService {

  private static final Set<String> VALID_NODE_KEYS =
      Collections.unmodifiableSet(
          new HashSet<String>() {
            {
              add(PerformanceNodeDeadlineService.KEY_GOAL);
              add(PerformanceNodeDeadlineService.KEY_SCORING);
              add(PerformanceNodeDeadlineService.KEY_FINAL);
              add(PerformanceNodeDeadlineService.KEY_CONFIRM);
            }
          });

  private final MenuPermissionService menuPermissionService;
  private final PerformanceDeadlineAutoService deadlineAutoService;
  private final PerformanceDeadlineNotifyService deadlineNotifyService;

  public PerformanceDeadlineOpsService(
      MenuPermissionService menuPermissionService,
      PerformanceDeadlineAutoService deadlineAutoService,
      PerformanceDeadlineNotifyService deadlineNotifyService) {
    this.menuPermissionService = menuPermissionService;
    this.deadlineAutoService = deadlineAutoService;
    this.deadlineNotifyService = deadlineNotifyService;
  }

  public void assertOpsAllowed(String userId) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }
    String role = menuPermissionService.getUserRole(userId);
    if ("super_admin".equals(role)) {
      return;
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_review_admin");
  }

  public Map<String, Object> runReminders(String userId, Map<String, Object> body) {
    assertOpsAllowed(userId);
    LocalDate asOfDate = parseAsOfDate(body.get("asOfDate"));
    String recordId = optTrim(body.get("recordId"));
    Set<String> forceNodes = parseNodeKeys(body.get("forceNodes"));
    boolean ignoreSent = parseBool(body.get("ignoreSent"), false);
    int reminders =
        deadlineNotifyService.runRemindersForDate(asOfDate, recordId, forceNodes, ignoreSent);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("asOfDate", asOfDate.toString());
    out.put("recordId", recordId);
    out.put("forceNodes", forceNodes);
    out.put("ignoreSent", ignoreSent);
    out.put("remindersSent", reminders);
    return out;
  }

  public Map<String, Object> runAuto(String userId, Map<String, Object> body) {
    assertOpsAllowed(userId);
    String recordId = optTrim(body.get("recordId"));
    if (recordId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordId 必填");
    }
    boolean forceAdvance = parseBool(body.get("forceAdvance"), true);
    Map<String, Object> stats =
        deadlineAutoService.runAll(LocalDate.now(), recordId, Collections.emptySet(), forceAdvance);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.putAll(stats);
    out.put("recordId", recordId);
    out.put("forceAdvance", forceAdvance);
    return out;
  }

  private static LocalDate parseAsOfDate(Object raw) {
    if (raw == null) {
      return LocalDate.now();
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return LocalDate.now();
    }
    try {
      return LocalDate.parse(s);
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "asOfDate 格式须为 YYYY-MM-DD");
    }
  }

  private static String optTrim(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    return s.isEmpty() ? null : s;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> parseNodeKeys(Object raw) {
    if (raw == null) {
      return Collections.emptySet();
    }
    Set<String> out = new HashSet<String>();
    if (raw instanceof List) {
      for (Object item : (List<Object>) raw) {
        addNodeKey(out, item);
      }
    } else {
      for (String part : String.valueOf(raw).split(",")) {
        addNodeKey(out, part);
      }
    }
    return out;
  }

  private static void addNodeKey(Set<String> out, Object item) {
    if (item == null) {
      return;
    }
    String key = String.valueOf(item).trim();
    if (key.isEmpty()) {
      return;
    }
    if (!VALID_NODE_KEYS.contains(key)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "forceNodes 仅支持 goal、scoring、final、confirm");
    }
    out.add(key);
  }

  private static boolean parseBool(Object raw, boolean defaultValue) {
    if (raw == null) {
      return defaultValue;
    }
    if (raw instanceof Boolean) {
      return (Boolean) raw;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return defaultValue;
    }
    return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
  }
}
