package com.jixiao2.server.performance;

import com.jixiao2.server.feishu.FeishuEmployeeMessagingService;
import com.jixiao2.server.feishu.FeishuTaskService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PerformanceFeishuTaskService {

  private static final Logger log = LoggerFactory.getLogger(PerformanceFeishuTaskService.class);
  public static final String KEY_FEISHU_TASK_ENABLED = "performance_feishu_task_enabled";
  /** grep 打点：绩效飞书待办自动完成链路 */
  private static final String FEISHU_TASK_TRACE = "[feishu-task-trace]";
  private static final ZoneId CN = ZoneId.of("Asia/Shanghai");

  private final JdbcTemplate jdbc;
  private final FeishuTaskService feishuTaskService;
  private final FeishuEmployeeMessagingService feishuEmployeeMessaging;
  private final PerformanceFeishuLogNames logNames;

  public PerformanceFeishuTaskService(
      JdbcTemplate jdbc,
      FeishuTaskService feishuTaskService,
      FeishuEmployeeMessagingService feishuEmployeeMessaging,
      PerformanceFeishuLogNames logNames) {
    this.jdbc = jdbc;
    this.feishuTaskService = feishuTaskService;
    this.feishuEmployeeMessaging = feishuEmployeeMessaging;
    this.logNames = logNames;
  }

  public static String statusToNodeKey(String status) {
    if (status == null || status.trim().isEmpty()) {
      return null;
    }
    String s = status.trim();
    if ("goal_setting".equals(s) || "goal_rejected".equals(s)) {
      return "goal";
    }
    if ("goal_pending_review".equals(s)) {
      return "goal_review";
    }
    if ("plan_execution".equals(s)) {
      return null;
    }
    if ("self_review".equals(s)) {
      return "self";
    }
    if ("manager_review".equals(s) || "dual_manager_review".equals(s) || "dotted_manager_review".equals(s)) {
      return "manager";
    }
    if ("final_review".equals(s)) {
      return "final";
    }
    if ("issued".equals(s)) {
      return "confirm";
    }
    return null;
  }

  public int getDueDays(String nodeKey) {
    if (nodeKey == null || nodeKey.trim().isEmpty()) {
      return 7;
    }
    try {
      Integer n =
          jdbc.queryForObject(
              "SELECT due_days FROM performance_feishu_task_node_config WHERE node_key = ? LIMIT 1",
              Integer.class,
              nodeKey.trim());
      if (n == null || n < 0) {
        return 7;
      }
      return n;
    } catch (Exception e) {
      return 7;
    }
  }

  public static long dueEpochMillisForDays(int dueDays) {
    int d = Math.max(0, dueDays);
    LocalDate end = LocalDate.now(CN).plusDays(d);
    ZonedDateTime zdt = end.atTime(23, 59, 59).atZone(CN);
    return zdt.toInstant().toEpochMilli();
  }

  public void supersedePendingSameAssignee(String recordId, String nodeKey, String assigneeEmployeeId) {
    if (recordId == null
        || recordId.trim().isEmpty()
        || nodeKey == null
        || nodeKey.trim().isEmpty()
        || assigneeEmployeeId == null
        || assigneeEmployeeId.trim().isEmpty()) {
      return;
    }
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT id, feishu_task_guid FROM performance_feishu_task WHERE performance_record_id = ? AND node_key = ? AND assignee_employee_id = ? AND status = 'pending'",
            new Object[] {recordId.trim(), nodeKey.trim(), assigneeEmployeeId.trim()},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("feishu_task_guid", rs.getString("feishu_task_guid"));
              return m;
            });
    for (Map<String, Object> row : rows) {
      completeOneRowInFeishuAndDb(row);
    }
  }

  private void completeOneRowInFeishuAndDb(Map<String, Object> row) {
    String id = String.valueOf(row.get("id"));
    String guid = row.get("feishu_task_guid") == null ? null : String.valueOf(row.get("feishu_task_guid"));
    String assignee;
    try {
      assignee =
          jdbc.queryForObject(
              "SELECT assignee_employee_id FROM performance_feishu_task WHERE id = ? LIMIT 1",
              String.class,
              id);
    } catch (Exception e) {
      assignee = null;
    }
    Optional<FeishuEmployeeMessagingService.FeishuMessagingContext> opt =
        assignee == null || assignee.trim().isEmpty()
            ? Optional.empty()
            : feishuEmployeeMessaging.resolveForEmployeeId(assignee.trim());
    if (opt.isPresent() && opt.get().isOk()) {
      FeishuEmployeeMessagingService.FeishuMessagingContext ctx = opt.get();
      try {
        if (guid != null && !guid.trim().isEmpty()) {
          feishuTaskService.completeTaskWithRetry(
              ctx.getImAppId(), ctx.getImAppSecret(), guid.trim());
        }
      } catch (Exception e) {
        log.warn("飞书待办完成（旧单）异常 taskRowId={}", id, e);
      }
    }
    jdbc.update(
        "UPDATE performance_feishu_task SET status = 'cancelled', completed_at = NOW() WHERE id = ? AND status = 'pending'",
        id);
  }

  /**
   * 在 IM 卡片发送成功后创建飞书待办；失败则本地记 cancelled。
   */
  public boolean isFeishuTaskEnabled() {
    try {
      String v =
          jdbc.queryForObject(
              "SELECT config_value FROM system_config WHERE config_key = ? LIMIT 1",
              String.class,
              KEY_FEISHU_TASK_ENABLED);
      if (v == null || v.trim().isEmpty()) {
        return true;
      }
      String t = v.trim();
      return "1".equals(t) || "true".equalsIgnoreCase(t);
    } catch (Exception e) {
      return true;
    }
  }

  public void setFeishuTaskEnabled(boolean enabled) {
    String val = enabled ? "1" : "0";
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, KEY_FEISHU_TASK_ENABLED);
    if (exists != null && exists > 0) {
      jdbc.update(
          "UPDATE system_config SET config_value = ? WHERE config_key = ?", val, KEY_FEISHU_TASK_ENABLED);
    } else {
      jdbc.update(
          "INSERT INTO system_config (config_key, config_value) VALUES (?, ?)",
          KEY_FEISHU_TASK_ENABLED,
          val);
    }
  }

  public void createAfterImSuccess(
      FeishuEmployeeMessagingService.FeishuMessagingContext ctx,
      String receiveOpenId,
      String assigneeEmployeeId,
      String recordId,
      String nodeKey,
      String summary,
      String textBody,
      String entranceUrl) {
    if (!isFeishuTaskEnabled()) {
      return;
    }
    if (recordId == null
        || recordId.trim().isEmpty()
        || nodeKey == null
        || nodeKey.trim().isEmpty()
        || assigneeEmployeeId == null
        || assigneeEmployeeId.trim().isEmpty()) {
      return;
    }
    if (ctx == null || !ctx.isOk()) {
      return;
    }
    supersedePendingSameAssignee(recordId, nodeKey, assigneeEmployeeId);
    int dueDays = getDueDays(nodeKey);
    long dueMs = dueEpochMillisForDays(dueDays);
    java.sql.Timestamp dueAt = new java.sql.Timestamp(dueMs);
    String desc = textBody == null ? "" : textBody;
    if (entranceUrl != null && !entranceUrl.trim().isEmpty()) {
      desc = desc + "\n\n前往绩效：" + entranceUrl.trim();
    }
    String rowId = UUID.randomUUID().toString();
    String sum = summary == null ? "" : summary;
    if (sum.length() > 500) {
      sum = sum.substring(0, 500);
    }
    jdbc.update(
        "INSERT INTO performance_feishu_task (id, performance_record_id, node_key, assignee_employee_id, feishu_task_guid, summary, description, due_at, status) VALUES (?,?,?,?,NULL,?,?,?,'pending')",
        rowId,
        recordId.trim(),
        nodeKey.trim(),
        assigneeEmployeeId.trim(),
        sum,
        desc,
        dueAt);
    String guid =
        feishuTaskService.createTaskWithRetry(
            ctx.getImAppId(), ctx.getImAppSecret(), summary, desc, receiveOpenId, dueMs);
    if (guid == null || guid.trim().isEmpty()) {
      jdbc.update(
          "UPDATE performance_feishu_task SET status = 'cancelled', completed_at = NOW() WHERE id = ?",
          rowId);
      log.warn(
          "飞书待办创建失败已标记 cancelled recordId={} nodeKey={} {}",
          recordId,
          nodeKey,
          logNames.assignee(assigneeEmployeeId));
    } else {
      jdbc.update("UPDATE performance_feishu_task SET feishu_task_guid = ? WHERE id = ?", guid.trim(), rowId);
    }
  }

  public void completePending(String recordId, String nodeKey, String assigneeEmployeeIdOrNull) {
    if (recordId == null || recordId.trim().isEmpty() || nodeKey == null || nodeKey.trim().isEmpty()) {
      return;
    }
    String sql =
        "SELECT id, feishu_task_guid, assignee_employee_id FROM performance_feishu_task WHERE performance_record_id = ? AND node_key = ? AND status = 'pending'";
    List<Map<String, Object>> rows;
    if (assigneeEmployeeIdOrNull != null && !assigneeEmployeeIdOrNull.trim().isEmpty()) {
      rows =
          jdbc.query(
              sql + " AND assignee_employee_id = ?",
              new Object[] {recordId.trim(), nodeKey.trim(), assigneeEmployeeIdOrNull.trim()},
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getString("id"));
                m.put("feishu_task_guid", rs.getString("feishu_task_guid"));
                m.put("assignee_employee_id", rs.getString("assignee_employee_id"));
                return m;
              });
    } else {
      rows =
          jdbc.query(
              sql,
              new Object[] {recordId.trim(), nodeKey.trim()},
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getString("id"));
                m.put("feishu_task_guid", rs.getString("feishu_task_guid"));
                m.put("assignee_employee_id", rs.getString("assignee_employee_id"));
                return m;
              });
    }
    boolean traceGoalSubmit =
        "goal".equals(nodeKey.trim())
            && assigneeEmployeeIdOrNull != null
            && !assigneeEmployeeIdOrNull.trim().isEmpty();
    if (traceGoalSubmit) {
      String ids =
          rows.stream().map(r -> String.valueOf(r.get("id"))).collect(Collectors.joining(","));
      log.info(
          "{} goal_submit:auto_complete_pending recordId={} nodeKey=goal {} pendingRowCount={} taskRowIds=[{}]",
          FEISHU_TASK_TRACE,
          recordId.trim(),
          logNames.assignee(assigneeEmployeeIdOrNull),
          rows.size(),
          ids);
    }

    for (Map<String, Object> row : rows) {
      completePendingRow(row, traceGoalSubmit);
    }
  }

  private void completePendingRow(Map<String, Object> row, boolean traceGoalSubmit) {
    String id = String.valueOf(row.get("id"));
    String guid = row.get("feishu_task_guid") == null ? null : String.valueOf(row.get("feishu_task_guid"));
    String assignee = row.get("assignee_employee_id") == null ? null : String.valueOf(row.get("assignee_employee_id"));
    Optional<FeishuEmployeeMessagingService.FeishuMessagingContext> opt =
        assignee == null || assignee.trim().isEmpty()
            ? Optional.empty()
            : feishuEmployeeMessaging.resolveForEmployeeId(assignee.trim());
    if (opt.isPresent() && opt.get().isOk()) {
      FeishuEmployeeMessagingService.FeishuMessagingContext ctx = opt.get();
      try {
        if (guid != null && !guid.trim().isEmpty()) {
          boolean fsOk =
              feishuTaskService.completeTaskWithRetry(
                  ctx.getImAppId(), ctx.getImAppSecret(), guid.trim());
          if (traceGoalSubmit) {
            log.info(
                "{} goal_submit:feishu_complete_task taskRowId={} nodeKey=goal {} feishuGuid={} feishuApiOk={}",
                FEISHU_TASK_TRACE,
                id,
                logNames.assignee(assignee),
                guid.trim(),
                fsOk);
          }
        }
      } catch (Exception e) {
        log.warn("飞书待办完成异常 taskRowId={}", id, e);
      }
    }
    int updated =
        jdbc.update(
            "UPDATE performance_feishu_task SET status = 'completed', completed_at = NOW() WHERE id = ? AND status = 'pending'",
            id);
    if (traceGoalSubmit) {
      log.info(
          "{} goal_submit:local_mark_completed taskRowId={} nodeKey=goal {} hasFeishuGuid={} dbRowsUpdated={}",
          FEISHU_TASK_TRACE,
          id,
          logNames.assignee(assignee),
          guid != null && !guid.trim().isEmpty(),
          updated);
    }
  }

  public void completeAfterSubmit(String actorUserId, String recordId, String oldStatus, String reviewType) {
    if (recordId == null || recordId.trim().isEmpty() || actorUserId == null || actorUserId.trim().isEmpty()) {
      return;
    }
    if ("goal".equals(reviewType)) {
      log.info(
          "{} goal_submit:begin nodeKey=goal {} recordId={} oldStatus={}",
          FEISHU_TASK_TRACE,
          logNames.assignee(actorUserId),
          recordId.trim(),
          oldStatus);
      completePending(recordId, "goal", actorUserId);
      return;
    }
    if ("self".equals(reviewType)) {
      completePending(recordId, "self", actorUserId);
      return;
    }
    if ("manager".equals(reviewType) || "dotted_manager".equals(reviewType)) {
      completePending(recordId, "manager", actorUserId);
      return;
    }
    String nk = statusToNodeKey(oldStatus);
    if (nk != null) {
      completePending(recordId, nk, actorUserId);
    }
  }

  public void completeAllGoalReview(String recordId) {
    completePending(recordId, "goal_review", null);
  }

  public void completeAllFinal(String recordId) {
    completePending(recordId, "final", null);
  }

  public void completeConfirmForEmployee(String recordId, String employeeId) {
    completePending(recordId, "confirm", employeeId);
  }

  public void completeManagerForActor(String recordId, String actorUserId) {
    completePending(recordId, "manager", actorUserId);
  }

  public List<Map<String, Object>> listNodeConfig(@SuppressWarnings("unused") String userId) {
    return jdbc.query(
        "SELECT node_key, name, due_days, sort_order FROM performance_feishu_task_node_config ORDER BY sort_order ASC",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("nodeKey", rs.getString("node_key"));
          m.put("name", rs.getString("name"));
          m.put("dueDays", rs.getInt("due_days"));
          m.put("sortOrder", rs.getInt("sort_order"));
          return m;
        });
  }

  public void updateNodeConfigs(@SuppressWarnings("unused") String userId, List<Map<String, Object>> items) {
    if (items == null) {
      return;
    }
    for (Map<String, Object> it : items) {
      if (it == null) {
        continue;
      }
      String key = it.get("nodeKey") == null ? null : String.valueOf(it.get("nodeKey"));
      Object d = it.get("dueDays");
      if (key == null || key.trim().isEmpty() || d == null) {
        continue;
      }
      int due;
      try {
        due = ((Number) d).intValue();
      } catch (Exception e) {
        continue;
      }
      if (due < 0 || due > 3650) {
        continue;
      }
      jdbc.update(
          "UPDATE performance_feishu_task_node_config SET due_days = ? WHERE node_key = ?", due, key.trim());
    }
  }
}
