package com.jixiao2.server.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.culture.CultureDimensionsSupport;
import com.jixiao2.server.feishu.FeishuBadgeSyncService;
import java.time.LocalDate;
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

@Service
public class PerformanceDeadlineAutoService {

  private static final Logger log = LoggerFactory.getLogger(PerformanceDeadlineAutoService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<Map<String, Object>>> REVIEW_LIST_TYPE =
      new TypeReference<List<Map<String, Object>>>() {};

  private final JdbcTemplate jdbc;
  private final PerformanceNodeDeadlineService deadlineService;
  private final FeishuBadgeSyncService feishuBadgeSyncService;
  private final PerformanceService performanceService;

  public PerformanceDeadlineAutoService(
      JdbcTemplate jdbc,
      PerformanceNodeDeadlineService deadlineService,
      FeishuBadgeSyncService feishuBadgeSyncService,
      PerformanceService performanceService) {
    this.jdbc = jdbc;
    this.deadlineService = deadlineService;
    this.feishuBadgeSyncService = feishuBadgeSyncService;
    this.performanceService = performanceService;
  }

  public Map<String, Object> runAll(
      LocalDate asOfDate, String recordId, Set<String> forcePassedNodes, boolean skipDeadlineCheck) {
    LocalDate today = asOfDate != null ? asOfDate : LocalDate.now();
    Set<String> force = forcePassedNodes == null ? Collections.<String>emptySet() : forcePassedNodes;
    int advanced = 0;
    int autoConfirmed = 0;
    List<Map<String, Object>> changes = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> rows = loadRecords(recordId);
    for (Map<String, Object> row : rows) {
      String id = String.valueOf(row.get("id"));
      String status = String.valueOf(row.get("status"));
      Map<String, String> deadlines =
          deadlineService.parseJson((String) row.get("nodeDeadlines"));
      if (deadlines.isEmpty()) {
        deadlines = deadlineService.defaultDeadlinesForPeriod(String.valueOf(row.get("period")));
      }
      try {
        if (advanceGoalToPlan(row, status, deadlines, today, force, skipDeadlineCheck, changes)) {
          advanced++;
          continue;
        }
        if (advanceScoringPhase(row, status, deadlines, today, force, skipDeadlineCheck, changes)) {
          advanced++;
          continue;
        }
        if (advanceFinalToIssued(id, status, deadlines, today, force, skipDeadlineCheck, changes)) {
          advanced++;
          continue;
        }
        if (autoConfirmIssued(id, status, deadlines, today, force, skipDeadlineCheck, changes)) {
          autoConfirmed++;
        }
      } catch (Exception e) {
        log.warn("绩效截止自动处理失败 recordId={} status={}", id, status, e);
      }
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("asOfDate", today.toString());
    out.put("skipDeadlineCheck", skipDeadlineCheck);
    out.put("advanced", advanced);
    out.put("autoConfirmed", autoConfirmed);
    out.put("changes", changes);
    out.put("recordCount", rows.size());
    return out;
  }

  private List<Map<String, Object>> loadRecords(String recordId) {
    String sql =
        "SELECT id, employee_id, period, status, node_deadlines, manager_id, dotted_manager_id,"
            + " performance_indicators, culture_dimensions, learning_dimensions,"
            + " self_review, manager_review, dotted_manager_review,"
            + " culture_self_review, culture_manager_review, culture_dotted_manager_review,"
            + " learning_self_review, learning_manager_review, learning_dotted_manager_review"
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
          m.put("managerId", rs.getString("manager_id"));
          m.put("dottedManagerId", rs.getString("dotted_manager_id"));
          m.put("performanceIndicators", rs.getString("performance_indicators"));
          m.put("cultureDimensions", rs.getString("culture_dimensions"));
          m.put("learningDimensions", rs.getString("learning_dimensions"));
          m.put("selfReview", rs.getString("self_review"));
          m.put("managerReview", rs.getString("manager_review"));
          m.put("dottedManagerReview", rs.getString("dotted_manager_review"));
          m.put("cultureSelfReview", rs.getString("culture_self_review"));
          m.put("cultureManagerReview", rs.getString("culture_manager_review"));
          m.put("cultureDottedManagerReview", rs.getString("culture_dotted_manager_review"));
          m.put("learningSelfReview", rs.getString("learning_self_review"));
          m.put("learningManagerReview", rs.getString("learning_manager_review"));
          m.put("learningDottedManagerReview", rs.getString("learning_dotted_manager_review"));
          return m;
        });
  }

  private LocalDate effectiveDeadline(
      Map<String, String> deadlines, String key, LocalDate today, Set<String> forcePassedNodes) {
    if (forcePassedNodes.contains(key)) {
      return today.minusDays(1);
    }
    return deadlineService.deadlineDate(deadlines, key);
  }

  private static void addChange(
      List<Map<String, Object>> changes,
      String recordId,
      String fromStatus,
      String toStatus,
      String action,
      String remark) {
    Map<String, Object> c = new LinkedHashMap<String, Object>();
    c.put("recordId", recordId);
    c.put("fromStatus", fromStatus);
    c.put("toStatus", toStatus);
    c.put("action", action);
    c.put("remark", remark);
    changes.add(c);
  }

  private boolean advanceGoalToPlan(
      Map<String, Object> row,
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Set<String> forcePassedNodes,
      boolean skipDeadlineCheck,
      List<Map<String, Object>> changes) {
    if (!skipDeadlineCheck) {
      LocalDate goalEnd =
          effectiveDeadline(deadlines, PerformanceNodeDeadlineService.KEY_GOAL, today, forcePassedNodes);
      if (goalEnd == null || !today.isAfter(goalEnd)) {
        return false;
      }
    }
    if (!"goal_setting".equals(status)
        && !"goal_rejected".equals(status)
        && !"goal_pending_review".equals(status)
        && !"template_selection".equals(status)) {
      return false;
    }
    String id = String.valueOf(row.get("id"));
    jdbc.update(
        "UPDATE performance_record SET status=?, deadline_flow_anchor=? WHERE id=?",
        "plan_execution",
        status,
        id);
    logFlow(id, status, "plan_execution", "deadline_auto_goal_to_plan", null, "目标阶段逾期未处理，进入计划执行中");
    addChange(changes, id, status, "plan_execution", "deadline_auto_goal_to_plan", "目标阶段逾期未处理，进入计划执行中");
    feishuBadgeSyncService.scheduleSyncForPerformanceRecord(id);
    return true;
  }

  private boolean advanceScoringPhase(
      Map<String, Object> row,
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Set<String> forcePassedNodes,
      boolean skipDeadlineCheck,
      List<Map<String, Object>> changes) {
    if (!skipDeadlineCheck) {
      LocalDate scoringEnd =
          effectiveDeadline(deadlines, PerformanceNodeDeadlineService.KEY_SCORING, today, forcePassedNodes);
      if (scoringEnd == null || !today.isAfter(scoringEnd)) {
        return false;
      }
    }
    String id = String.valueOf(row.get("id"));
    if (skipDeadlineCheck && isScoringReviewStatus(status)) {
      return advanceScoringToFinalWithZeros(row, status, changes);
    }
    if ("plan_execution".equals(status)) {
      jdbc.update("UPDATE performance_record SET status=? WHERE id=?", "self_review", id);
      logFlow(id, status, "self_review", "deadline_auto_plan_to_self", null, "计划执行期结束，进入自评");
      addChange(changes, id, status, "self_review", "deadline_auto_plan_to_self", "计划执行期结束，进入自评");
      feishuBadgeSyncService.scheduleSyncForPerformanceRecord(id);
      return true;
    }
    if ("self_review".equals(status)) {
      String next = nextManagerStatus(row);
      List<String> indicatorNames = indicatorNamesFromRow(row);
      if (isPerfReviewComplete((String) row.get("selfReview"), indicatorNames)) {
        jdbc.update("UPDATE performance_record SET status=? WHERE id=?", next, id);
        logFlow(id, "self_review", next, "deadline_auto_self_to_manager", null, "自评已完成，进入上级评分");
        addChange(changes, id, "self_review", next, "deadline_auto_self_to_manager", "自评已完成，进入上级评分");
      } else {
        applyZeroSelfAndAdvanceManagers(row);
        logFlow(id, "self_review", next, "deadline_auto_self_zero", null, "逾期未自评，按0分并进入上级评分");
        addChange(changes, id, "self_review", next, "deadline_auto_self_zero", "逾期未自评，按0分并进入上级评分");
      }
      feishuBadgeSyncService.scheduleSyncForPerformanceRecord(id);
      return true;
    }
    if ("manager_review".equals(status)
        || "dual_manager_review".equals(status)
        || "dotted_manager_review".equals(status)) {
      return advanceScoringToFinalWithZeros(row, status, changes);
    }
    return false;
  }

  private static boolean isScoringReviewStatus(String status) {
    return "self_review".equals(status)
        || "manager_review".equals(status)
        || "dual_manager_review".equals(status)
        || "dotted_manager_review".equals(status);
  }

  /** 自评/上级评分逾期未处理：仅对未完成评分的角色按 0 分，已提交的保留，并进入校准。 */
  private boolean advanceScoringToFinalWithZeros(
      Map<String, Object> row, String status, List<Map<String, Object>> changes) {
    String id = String.valueOf(row.get("id"));
    List<String> indicatorNames = indicatorNamesFromRow(row);
    List<Map<String, Object>> cultureDims =
        CultureDimensionsSupport.parseListOrDefault((String) row.get("cultureDimensions"));
    List<Map<String, Object>> learningInds = parseIndicators((String) row.get("learningDimensions"));
    boolean hasDotted = row.get("dottedManagerId") != null;

    String selfJson =
        resolvePerfReviewJson((String) row.get("selfReview"), row, indicatorNames);
    String cultureSelfJson =
        resolveCultureReviewJson((String) row.get("cultureSelfReview"), row, cultureDims);
    String learningSelfJson =
        resolveLearningReviewJson((String) row.get("learningSelfReview"), row, learningInds);
    String mgrJson =
        resolvePerfReviewJson((String) row.get("managerReview"), row, indicatorNames);
    String cultureMgrJson =
        resolveCultureReviewJson((String) row.get("cultureManagerReview"), row, cultureDims);
    String learningMgrJson =
        resolveLearningReviewJson((String) row.get("learningManagerReview"), row, learningInds);
    String dotJson =
        hasDotted
            ? resolvePerfReviewJson((String) row.get("dottedManagerReview"), row, indicatorNames)
            : null;
    String cultureDotJson =
        hasDotted
            ? resolveCultureReviewJson((String) row.get("cultureDottedManagerReview"), row, cultureDims)
            : null;
    String learningDotJson =
        hasDotted
            ? resolveLearningReviewJson((String) row.get("learningDottedManagerReview"), row, learningInds)
            : null;

    jdbc.update(
        "UPDATE performance_record SET status=?, deadline_flow_anchor=?,"
            + " self_review=CAST(? AS JSON), culture_self_review=CAST(? AS JSON), learning_self_review=CAST(? AS JSON),"
            + " manager_review=CAST(? AS JSON), culture_manager_review=CAST(? AS JSON), learning_manager_review=CAST(? AS JSON),"
            + " dotted_manager_review=CAST(? AS JSON), culture_dotted_manager_review=CAST(? AS JSON), learning_dotted_manager_review=CAST(? AS JSON)"
            + " WHERE id=?",
        "final_review",
        status,
        selfJson,
        cultureSelfJson,
        learningSelfJson,
        mgrJson,
        cultureMgrJson,
        learningMgrJson,
        dotJson,
        cultureDotJson,
        learningDotJson,
        id);
    performanceService.refreshTotalScoreIfManagersReady(id);
    logFlow(id, status, "final_review", "deadline_auto_scoring_zero", null, "评分阶段逾期未处理，未完成项按0分并进入校准");
    addChange(changes, id, status, "final_review", "deadline_auto_scoring_zero", "评分阶段逾期未处理，未完成项按0分并进入校准");
    feishuBadgeSyncService.scheduleSyncForPerformanceRecord(id);
    return true;
  }

  private void applyZeroSelfAndAdvanceManagers(Map<String, Object> row) {
    String id = String.valueOf(row.get("id"));
    String selfJson = writeJson(buildZeroPerfReview(row));
    String cultureJson = writeJson(buildZeroCultureReview(row));
    String learningJson = writeJson(buildZeroLearningReview(row));
    String next = nextManagerStatus(row);
    jdbc.update(
        "UPDATE performance_record SET status=?, self_review=CAST(? AS JSON), culture_self_review=CAST(? AS JSON), learning_self_review=CAST(? AS JSON) WHERE id=?",
        next,
        selfJson,
        cultureJson,
        learningJson,
        id);
  }

  private String nextManagerStatus(Map<String, Object> row) {
    return row.get("dottedManagerId") != null ? "dual_manager_review" : "manager_review";
  }

  private boolean advanceFinalToIssued(
      String id,
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Set<String> forcePassedNodes,
      boolean skipDeadlineCheck,
      List<Map<String, Object>> changes) {
    if (!"final_review".equals(status)) {
      return false;
    }
    if (!skipDeadlineCheck) {
      LocalDate finalEnd =
          effectiveDeadline(deadlines, PerformanceNodeDeadlineService.KEY_FINAL, today, forcePassedNodes);
      if (finalEnd == null || !today.isAfter(finalEnd)) {
        return false;
      }
    }
    jdbc.update(
        "UPDATE performance_record SET status=?, rejection_reason=NULL WHERE id=?",
        "issued",
        id);
    logFlow(id, "final_review", "issued", "deadline_auto_calibrate", null, "校准逾期未处理，系统自动下发结果");
    addChange(changes, id, "final_review", "issued", "deadline_auto_calibrate", "校准逾期未处理，系统自动下发结果");
    feishuBadgeSyncService.scheduleSyncForPerformanceRecord(id);
    return true;
  }

  private boolean autoConfirmIssued(
      String id,
      String status,
      Map<String, String> deadlines,
      LocalDate today,
      Set<String> forcePassedNodes,
      boolean skipDeadlineCheck,
      List<Map<String, Object>> changes) {
    if (!"issued".equals(status)) {
      return false;
    }
    if (!skipDeadlineCheck) {
      LocalDate confirmEnd =
          effectiveDeadline(deadlines, PerformanceNodeDeadlineService.KEY_CONFIRM, today, forcePassedNodes);
      if (confirmEnd == null || !today.isAfter(confirmEnd)) {
        return false;
      }
    }
    jdbc.update("UPDATE performance_record SET status=? WHERE id=?", "completed", id);
    logFlow(id, "issued", "completed", "deadline_auto_confirm", null, "员工逾期未确认，系统自动确认");
    addChange(changes, id, "issued", "completed", "deadline_auto_confirm", "员工逾期未确认，系统自动确认");
    feishuBadgeSyncService.scheduleSyncForPerformanceRecord(id);
    return true;
  }

  private List<Map<String, Object>> buildZeroPerfReview(Map<String, Object> row) {
    List<Map<String, Object>> indicators = parseIndicators((String) row.get("performanceIndicators"));
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> ind : indicators) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("indicatorName", String.valueOf(ind.get("name")));
      item.put("score", 0);
      item.put("comment", "逾期未评，系统自动按0分");
      out.add(item);
    }
    return out;
  }

  private List<Map<String, Object>> buildZeroCultureReview(Map<String, Object> row) {
    List<Map<String, Object>> dims = parseIndicators((String) row.get("cultureDimensions"));
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> d : dims) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      Object name = d.get("name");
      item.put("name", name == null ? "" : String.valueOf(name));
      item.put("score", 0);
      item.put("comment", "逾期未评");
      out.add(item);
    }
    return out;
  }

  private List<Map<String, Object>> buildZeroLearningReview(Map<String, Object> row) {
    List<Map<String, Object>> inds = parseIndicators((String) row.get("learningDimensions"));
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> ind : inds) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("indicatorName", String.valueOf(ind.get("name")));
      item.put("score", 0);
      item.put("comment", "逾期未评");
      out.add(item);
    }
    return out;
  }

  private List<String> indicatorNamesFromRow(Map<String, Object> row) {
    List<String> names = new ArrayList<String>();
    for (Map<String, Object> ind : parseIndicators((String) row.get("performanceIndicators"))) {
      names.add(String.valueOf(ind.get("name")));
    }
    return names;
  }

  private static boolean isPerfReviewComplete(String json, List<String> indicatorNames) {
    if (indicatorNames.isEmpty()) {
      return false;
    }
    List<Map<String, Object>> review = parseReviewList(json);
    if (review.isEmpty()) {
      return false;
    }
    Map<String, Map<String, Object>> byName = new LinkedHashMap<String, Map<String, Object>>();
    for (Map<String, Object> i : review) {
      byName.put(String.valueOf(i.get("indicatorName")), i);
    }
    for (String name : indicatorNames) {
      Map<String, Object> item = byName.get(name);
      if (item == null) {
        return false;
      }
      Object s = item.get("score");
      if (!(s instanceof Number) || Double.isNaN(((Number) s).doubleValue())) {
        return false;
      }
    }
    return true;
  }

  private String resolvePerfReviewJson(
      String existingJson, Map<String, Object> row, List<String> indicatorNames) {
    if (isPerfReviewComplete(existingJson, indicatorNames)) {
      return existingJson;
    }
    return writeJson(buildZeroPerfReview(row));
  }

  private String resolveCultureReviewJson(
      String existingJson, Map<String, Object> row, List<Map<String, Object>> cultureDims) {
    if (cultureDims.isEmpty()) {
      return existingJson != null && !existingJson.trim().isEmpty() ? existingJson : writeJson(buildZeroCultureReview(row));
    }
    List<Map<String, Object>> review = parseReviewList(existingJson);
    if (CultureDimensionsSupport.isCultureReviewComplete(review, cultureDims)) {
      return existingJson != null && !existingJson.trim().isEmpty() ? existingJson : writeJson(buildZeroCultureReview(row));
    }
    return writeJson(buildZeroCultureReview(row));
  }

  private String resolveLearningReviewJson(
      String existingJson, Map<String, Object> row, List<Map<String, Object>> learningInds) {
    if (learningInds.isEmpty()) {
      return existingJson != null && !existingJson.trim().isEmpty() ? existingJson : writeJson(buildZeroLearningReview(row));
    }
    List<String> names = new ArrayList<String>();
    for (Map<String, Object> ind : learningInds) {
      names.add(String.valueOf(ind.get("name")));
    }
    if (isPerfReviewComplete(existingJson, names)) {
      return existingJson;
    }
    return writeJson(buildZeroLearningReview(row));
  }

  private static List<Map<String, Object>> parseReviewList(String json) {
    if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
      return new ArrayList<Map<String, Object>>();
    }
    try {
      List<Map<String, Object>> list = MAPPER.readValue(json, REVIEW_LIST_TYPE);
      return list == null ? new ArrayList<Map<String, Object>>() : list;
    } catch (Exception e) {
      return new ArrayList<Map<String, Object>>();
    }
  }

  private List<Map<String, Object>> parseIndicators(String json) {
    if (json == null || json.trim().isEmpty()) {
      return new ArrayList<Map<String, Object>>();
    }
    try {
      return MAPPER.readValue(json, REVIEW_LIST_TYPE);
    } catch (Exception e) {
      return new ArrayList<Map<String, Object>>();
    }
  }

  private void logFlow(
      String recordId, String from, String to, String action, String actor, String remark) {
    try {
      jdbc.update(
          "INSERT INTO performance_record_flow_log (id, performance_record_id, from_status, to_status, action, actor_user_id, remark, _created_at) VALUES (?,?,?,?,?,?,?,NOW())",
          java.util.UUID.randomUUID().toString(),
          recordId,
          from,
          to,
          action,
          actor,
          remark);
    } catch (Exception ignored) {
      // ignore
    }
  }

  private String writeJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception e) {
      return "[]";
    }
  }
}
