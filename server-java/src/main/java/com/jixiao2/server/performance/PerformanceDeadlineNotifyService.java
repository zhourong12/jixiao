package com.jixiao2.server.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 绩效四个节点截止日当天飞书提醒（与创建绩效卡片样式一致）。 */
@Service
public class PerformanceDeadlineNotifyService {

  private static final Logger log = LoggerFactory.getLogger(PerformanceDeadlineNotifyService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE =
      new TypeReference<Map<String, String>>() {};

  private final JdbcTemplate jdbc;
  private final PerformanceNodeDeadlineService deadlineService;
  private final PerformanceFeishuAsyncService feishuAsync;

  public PerformanceDeadlineNotifyService(
      JdbcTemplate jdbc,
      PerformanceNodeDeadlineService deadlineService,
      PerformanceFeishuAsyncService feishuAsync) {
    this.jdbc = jdbc;
    this.deadlineService = deadlineService;
    this.feishuAsync = feishuAsync;
  }

  /** 对截止日等于 {@code asOfDate} 的节点发送提醒（每个节点每个截止日仅推送一次，除非 ignoreSent）。 */
  public int runRemindersForDate(
      LocalDate asOfDate, String recordId, Set<String> forceRemindNodes, boolean ignoreSent) {
    LocalDate today = asOfDate != null ? asOfDate : LocalDate.now();
    Set<String> force = forceRemindNodes == null ? Collections.<String>emptySet() : forceRemindNodes;
    int sent = 0;
    List<Map<String, Object>> rows = loadActiveRecords(recordId);
    for (Map<String, Object> row : rows) {
      try {
        sent += processRecord(row, today, force, ignoreSent);
      } catch (Exception e) {
        log.warn("绩效截止提醒失败 recordId={}", row.get("id"), e);
      }
    }
    return sent;
  }

  private int processRecord(
      Map<String, Object> row, LocalDate today, Set<String> forceRemindNodes, boolean ignoreSent) {
    String recordId = String.valueOf(row.get("id"));
    String status = String.valueOf(row.get("status"));
    Map<String, String> deadlines = resolveDeadlines(row);
    Map<String, String> sentMap = parseSentMap((String) row.get("deadlineNotifySent"));
    boolean dirty = false;
    int count = 0;

    if (shouldRemindGoalPhase(status, deadlines, today, sentMap, forceRemindNodes, ignoreSent)) {
      count += sendGoalPhaseReminders(row, recordId, status, deadlines.get(PerformanceNodeDeadlineService.KEY_GOAL));
      sentMap.put(PerformanceNodeDeadlineService.KEY_GOAL, deadlines.get(PerformanceNodeDeadlineService.KEY_GOAL));
      dirty = true;
    }
    if (shouldRemindScoringPhase(status, deadlines, today, sentMap, forceRemindNodes, ignoreSent)) {
      count +=
          sendScoringPhaseReminders(row, recordId, status, deadlines.get(PerformanceNodeDeadlineService.KEY_SCORING));
      sentMap.put(PerformanceNodeDeadlineService.KEY_SCORING, deadlines.get(PerformanceNodeDeadlineService.KEY_SCORING));
      dirty = true;
    }
    if (shouldRemindFinal(status, deadlines, today, sentMap, forceRemindNodes, ignoreSent)) {
      count += sendFinalReminder(row, recordId, deadlines.get(PerformanceNodeDeadlineService.KEY_FINAL));
      sentMap.put(PerformanceNodeDeadlineService.KEY_FINAL, deadlines.get(PerformanceNodeDeadlineService.KEY_FINAL));
      dirty = true;
    }
    if (shouldRemindConfirm(status, deadlines, today, sentMap, forceRemindNodes, ignoreSent)) {
      count += sendConfirmReminder(row, recordId, deadlines.get(PerformanceNodeDeadlineService.KEY_CONFIRM));
      sentMap.put(PerformanceNodeDeadlineService.KEY_CONFIRM, deadlines.get(PerformanceNodeDeadlineService.KEY_CONFIRM));
      dirty = true;
    }

    if (dirty) {
      persistSentMap(recordId, sentMap);
    }
    return count;
  }

  private int sendGoalPhaseReminders(
      Map<String, Object> row, String recordId, String status, String deadlineStr) {
    int n = 0;
    String employeeId = String.valueOf(row.get("employeeId"));
    if ("goal_setting".equals(status)
        || "goal_rejected".equals(status)
        || "template_selection".equals(status)) {
      feishuAsync.dispatch(
          () ->
              feishuAsync
                  .notifier()
                  .notifyDeadlineReminder(
                      employeeId,
                      row,
                      recordId,
                      "goal",
                      "今日为目标设定及审核截止日。",
                      "截至时间：" + deadlineStr + "。请尽快完成目标设定并提交审核。",
                      false));
      n++;
    } else if ("goal_pending_review".equals(status)) {
      String mgr = row.get("managerId") == null ? null : String.valueOf(row.get("managerId"));
      if (mgr == null || mgr.isEmpty()) {
        return n;
      }
      feishuAsync.dispatch(
          () ->
              feishuAsync
                  .notifier()
                  .notifyDeadlineReminder(
                      mgr,
                      row,
                      recordId,
                      "goal_review",
                      "今日为目标设定及审核截止日。",
                      "截至时间：" + deadlineStr + "。请尽快完成目标审核。",
                      true));
      n++;
    }
    return n;
  }

  private int sendScoringPhaseReminders(
      Map<String, Object> row, String recordId, String status, String deadlineStr) {
    int n = 0;
    String employeeId = String.valueOf(row.get("employeeId"));
    if ("plan_execution".equals(status) || "self_review".equals(status)) {
      feishuAsync.dispatch(
          () ->
              feishuAsync
                  .notifier()
                  .notifyDeadlineReminder(
                      employeeId,
                      row,
                      recordId,
                      "self",
                      "今日为员工及上级评分截止日。",
                      "截至时间：" + deadlineStr + "。请尽快"
                          + ("plan_execution".equals(status) ? "进入自评并完成评分。" : "提交自评。"),
                      false));
      n++;
    }
    if ("manager_review".equals(status)
        || "dual_manager_review".equals(status)
        || "dotted_manager_review".equals(status)) {
      String mgr = row.get("managerId") == null ? null : String.valueOf(row.get("managerId"));
      String dot = row.get("dottedManagerId") == null ? null : String.valueOf(row.get("dottedManagerId"));
      List<String> to = new ArrayList<String>();
      if ("manager_review".equals(status) && mgr != null && !mgr.isEmpty()) {
        to.add(mgr);
      } else if ("dotted_manager_review".equals(status) && dot != null && !dot.isEmpty()) {
        to.add(dot);
      } else if ("dual_manager_review".equals(status)) {
        if (mgr != null && !mgr.isEmpty()) {
          to.add(mgr);
        }
        if (dot != null && !dot.isEmpty()) {
          to.add(dot);
        }
      }
      feishuAsync.dispatch(
          () ->
              feishuAsync
                  .notifier()
                  .notifyDeadlineReminderDistinct(
                      to,
                      row,
                      recordId,
                      "manager",
                      "今日为员工及上级评分截止日。",
                      "截至时间：" + deadlineStr + "。请尽快完成上级评分。",
                      true));
      n += to.size();
    }
    return n;
  }

  /** 待校准阶段不向校准负责人发送截止日飞书提醒。 */
  private int sendFinalReminder(Map<String, Object> row, String recordId, String deadlineStr) {
    String owner = resolveCalibrationOwnerId(row);
    log.debug(
        "绩效校准截止日飞书提醒已跳过 recordId={} owner={}",
        recordId,
        owner == null || owner.isEmpty() ? "" : owner);
    return 0;
  }

  private int sendConfirmReminder(Map<String, Object> row, String recordId, String deadlineStr) {
    String employeeId = String.valueOf(row.get("employeeId"));
    feishuAsync.dispatch(
        () ->
            feishuAsync
                .notifier()
                .notifyDeadlineReminder(
                    employeeId,
                    row,
                    recordId,
                    "confirm",
                    "今日为员工确认截止日。",
                    "截至时间：" + deadlineStr + "。请尽快确认绩效结果。",
                    false));
    return 1;
  }

  private static String resolveCalibrationOwnerId(Map<String, Object> row) {
    String owner =
        row.get("calibrationOwnerId") == null ? "" : String.valueOf(row.get("calibrationOwnerId")).trim();
    if (!owner.isEmpty()) {
      return owner;
    }
    return row.get("createdBy") == null ? "" : String.valueOf(row.get("createdBy")).trim();
  }

  private static boolean shouldRemindGoalPhase(
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Map<String, String> sentMap,
      Set<String> forceRemindNodes,
      boolean ignoreSent) {
    if (!isDeadlineTodayOrForced(deadlines, PerformanceNodeDeadlineService.KEY_GOAL, today, forceRemindNodes)) {
      return false;
    }
    if (!ignoreSent && alreadySent(sentMap, PerformanceNodeDeadlineService.KEY_GOAL, deadlines)) {
      return false;
    }
    return "goal_setting".equals(status)
        || "goal_rejected".equals(status)
        || "template_selection".equals(status)
        || "goal_pending_review".equals(status);
  }

  private static boolean shouldRemindScoringPhase(
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Map<String, String> sentMap,
      Set<String> forceRemindNodes,
      boolean ignoreSent) {
    if (!isDeadlineTodayOrForced(deadlines, PerformanceNodeDeadlineService.KEY_SCORING, today, forceRemindNodes)) {
      return false;
    }
    if (!ignoreSent && alreadySent(sentMap, PerformanceNodeDeadlineService.KEY_SCORING, deadlines)) {
      return false;
    }
    return "plan_execution".equals(status)
        || "self_review".equals(status)
        || "manager_review".equals(status)
        || "dual_manager_review".equals(status)
        || "dotted_manager_review".equals(status);
  }

  private static boolean shouldRemindFinal(
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Map<String, String> sentMap,
      Set<String> forceRemindNodes,
      boolean ignoreSent) {
    return "final_review".equals(status)
        && isDeadlineTodayOrForced(deadlines, PerformanceNodeDeadlineService.KEY_FINAL, today, forceRemindNodes)
        && (ignoreSent || !alreadySent(sentMap, PerformanceNodeDeadlineService.KEY_FINAL, deadlines));
  }

  private static boolean shouldRemindConfirm(
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Map<String, String> sentMap,
      Set<String> forceRemindNodes,
      boolean ignoreSent) {
    return "issued".equals(status)
        && isDeadlineTodayOrForced(deadlines, PerformanceNodeDeadlineService.KEY_CONFIRM, today, forceRemindNodes)
        && (ignoreSent || !alreadySent(sentMap, PerformanceNodeDeadlineService.KEY_CONFIRM, deadlines));
  }

  private static boolean isDeadlineTodayOrForced(
      Map<String, String> deadlines, String key, LocalDate today, Set<String> forceRemindNodes) {
    if (forceRemindNodes != null && forceRemindNodes.contains(key)) {
      return true;
    }
    return isDeadlineToday(deadlines, key, today);
  }

  private static boolean isDeadlineToday(Map<String, String> deadlines, String key, LocalDate today) {
    LocalDate d = deadlineServiceStaticDeadline(deadlines, key);
    return d != null && today.equals(d);
  }

  private static LocalDate deadlineServiceStaticDeadline(Map<String, String> deadlines, String key) {
    if (deadlines == null || key == null) {
      return null;
    }
    String s = deadlines.get(key);
    if (s == null || s.trim().isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(s.trim(), ISO);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean alreadySent(
      Map<String, String> sentMap, String key, Map<String, String> deadlines) {
    if (sentMap == null || deadlines == null) {
      return false;
    }
    String deadline = deadlines.get(key);
    if (deadline == null || deadline.isEmpty()) {
      return false;
    }
    return deadline.equals(sentMap.get(key));
  }

  private Map<String, String> resolveDeadlines(Map<String, Object> row) {
    Map<String, String> deadlines =
        deadlineService.parseJson((String) row.get("nodeDeadlines"));
    if (deadlines.isEmpty()) {
      deadlines = deadlineService.defaultDeadlinesForPeriod(String.valueOf(row.get("period")));
    }
    return deadlines;
  }

  private List<Map<String, Object>> loadActiveRecords(String recordId) {
    String sql =
        "SELECT id, employee_id, period, status, node_deadlines, deadline_notify_sent,"
            + " manager_id, dotted_manager_id, calibration_owner_id, _created_by"
            + " FROM performance_record WHERE deleted_at IS NULL AND status <> 'completed'";
    Object[] args = new Object[] {};
    if (recordId != null && !recordId.trim().isEmpty()) {
      sql += " AND id=?";
      args = new Object[] {recordId.trim()};
    }
    return jdbc.query(
        sql,
        args,
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("id", rs.getString("id"));
          m.put("employeeId", rs.getString("employee_id"));
          m.put("period", rs.getString("period"));
          m.put("status", rs.getString("status"));
          m.put("nodeDeadlines", rs.getString("node_deadlines"));
          m.put("deadlineNotifySent", rs.getString("deadline_notify_sent"));
          m.put("managerId", rs.getString("manager_id"));
          m.put("dottedManagerId", rs.getString("dotted_manager_id"));
          m.put("calibrationOwnerId", rs.getString("calibration_owner_id"));
          m.put("createdBy", rs.getString("_created_by"));
          return m;
        });
  }

  private Map<String, String> parseSentMap(String json) {
    if (json == null || json.trim().isEmpty()) {
      return new LinkedHashMap<String, String>();
    }
    try {
      Map<String, String> m = MAPPER.readValue(json, STRING_MAP_TYPE);
      return m == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(m);
    } catch (Exception e) {
      return new LinkedHashMap<String, String>();
    }
  }

  private void persistSentMap(String recordId, Map<String, String> sentMap) {
    try {
      String json = MAPPER.writeValueAsString(sentMap);
      jdbc.update(
          "UPDATE performance_record SET deadline_notify_sent=CAST(? AS JSON) WHERE id=?",
          json,
          recordId);
    } catch (Exception e) {
      log.warn("保存截止提醒去重状态失败 recordId={}", recordId, e);
    }
  }
}
