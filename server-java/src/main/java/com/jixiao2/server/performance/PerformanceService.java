package com.jixiao2.server.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.culture.CultureDimensionsSupport;
import com.jixiao2.server.employee.EmployeeService;
import com.jixiao2.server.feishu.FeishuRegistryService;
import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.orgdepartment.OrgDepartmentService;
import com.jixiao2.server.template.TemplateService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PerformanceService {

  private static final Logger log = LoggerFactory.getLogger(PerformanceService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<Map<String, Object>>> REVIEW_LIST_TYPE =
      new TypeReference<List<Map<String, Object>>>() {};
  private static final double ROLE_WEIGHT_EPS = 1e-9;

  private final JdbcTemplate jdbc;
  private final MenuPermissionService menuPermissionService;
  private final PerformanceFeishuNotifier performanceFeishuNotifier;
  private final PerformanceFeishuTaskService performanceFeishuTaskService;
  private final FeishuRegistryService feishuRegistry;
  private final EmployeeService employeeService;
  private final OrgDepartmentService orgDepartmentService;

  public PerformanceService(
      JdbcTemplate jdbc,
      MenuPermissionService menuPermissionService,
      PerformanceFeishuNotifier performanceFeishuNotifier,
      PerformanceFeishuTaskService performanceFeishuTaskService,
      FeishuRegistryService feishuRegistry,
      EmployeeService employeeService,
      OrgDepartmentService orgDepartmentService) {
    this.jdbc = jdbc;
    this.menuPermissionService = menuPermissionService;
    this.performanceFeishuNotifier = performanceFeishuNotifier;
    this.performanceFeishuTaskService = performanceFeishuTaskService;
    this.feishuRegistry = feishuRegistry;
    this.employeeService = employeeService;
    this.orgDepartmentService = orgDepartmentService;
  }

  private void tryLogPerformanceFlow(
      String performanceRecordId,
      String fromStatus,
      String toStatus,
      String action,
      String actorUserId,
      String remark) {
    if (performanceRecordId == null
        || performanceRecordId.isEmpty()
        || fromStatus == null
        || toStatus == null
        || fromStatus.equals(toStatus)) {
      return;
    }
    String safeRemark = remark;
    if (safeRemark != null && safeRemark.length() > 2000) {
      safeRemark = safeRemark.substring(0, 2000);
    }
    try {
      jdbc.update(
          "INSERT INTO performance_record_flow_log (id, performance_record_id, from_status, to_status, action, actor_user_id, remark, _created_at) VALUES (?,?,?,?,?,?,?,NOW())",
          UUID.randomUUID().toString(),
          performanceRecordId,
          fromStatus,
          toStatus,
          action,
          actorUserId == null ? "" : actorUserId,
          safeRemark);
    } catch (Exception ex) {
      log.warn(
          "performance flow log insert failed record={} {}->{}",
          performanceRecordId,
          fromStatus,
          toStatus,
          ex);
    }
  }

  public String getUserRole(String userId) {
    if (userId == null || userId.isEmpty()) {
      return "employee";
    }
    try {
      return menuPermissionService.getUserRole(userId);
    } catch (Exception e) {
      log.error("获取用户角色失败", e);
      return "employee";
    }
  }

  private List<String> resolveRolesForUser(String userId) {
    if (userId == null || userId.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      List<String> keys = menuPermissionService.getRoleKeysForUser(userId);
      return keys.isEmpty() ? Collections.singletonList("employee") : keys;
    } catch (Exception e) {
      log.error("获取用户角色失败", e);
      return Collections.emptyList();
    }
  }

  private static class MenuFlags {
    final boolean listAll;
    final boolean batchCreate;
    final boolean reviewAdmin;
    final boolean exportData;

    MenuFlags(boolean listAll, boolean batchCreate, boolean reviewAdmin, boolean exportData) {
      this.listAll = listAll;
      this.batchCreate = batchCreate;
      this.reviewAdmin = reviewAdmin;
      this.exportData = exportData;
    }
  }

  private MenuFlags performanceMenuFlags(String userId) {
    MenuPermissionService.MenuPermissionsMe perm =
        menuPermissionService.getEffectiveMenusForUser(userId);
    Map<String, Boolean> menus = perm.getMenus();
    return new MenuFlags(
        Boolean.TRUE.equals(menus.get("performance_list_all")),
        Boolean.TRUE.equals(menus.get("performance_batch_create")),
        Boolean.TRUE.equals(menus.get("performance_review_admin")),
        Boolean.TRUE.equals(menus.get("performance_export")));
  }

  /** 优先绩效记录上的考核规则；否则回退模板绑定（旧数据）；再回退 system_config。 */
  private Map<String, Double> getReviewWeightsForScoring(String assessmentRuleId, String templateId) {
    String rid = assessmentRuleId == null ? "" : assessmentRuleId.trim();
    if (!rid.isEmpty()) {
      try {
        List<Map<String, Object>> rows =
            jdbc.query(
                "SELECT manager_weight AS mw, dotted_manager_weight AS dw FROM assessment_rule WHERE id = ? AND status = 'enabled' LIMIT 1",
                (rs, rn) -> {
                  Map<String, Object> m = new LinkedHashMap<String, Object>();
                  m.put("mw", rs.getBigDecimal("mw"));
                  m.put("dw", rs.getBigDecimal("dw"));
                  return m;
                },
                rid);
        if (!rows.isEmpty()) {
          java.math.BigDecimal mwB = (java.math.BigDecimal) rows.get(0).get("mw");
          java.math.BigDecimal dwB = (java.math.BigDecimal) rows.get(0).get("dw");
          if (mwB != null && dwB != null) {
            double mw = mwB.doubleValue();
            double dw = dwB.doubleValue();
            if (mw >= 0 && dw >= 0 && Double.isFinite(mw) && Double.isFinite(dw) && mw + dw > ROLE_WEIGHT_EPS) {
              Map<String, Double> out = new HashMap<String, Double>();
              out.put("managerWeight", mw);
              out.put("dottedWeight", dw);
              return out;
            }
          }
        }
      } catch (Exception ignored) {
        // fall through
      }
    }
    if (templateId != null && !templateId.isEmpty()) {
      try {
        List<Map<String, Object>> rows =
            jdbc.query(
                "SELECT ar.manager_weight AS mw, ar.dotted_manager_weight AS dw "
                    + "FROM performance_template pt "
                    + "INNER JOIN assessment_rule ar ON ar.id = pt.assessment_rule_id AND ar.status = 'enabled' "
                    + "WHERE pt.id = ? LIMIT 1",
                (rs, rn) -> {
                  Map<String, Object> m = new LinkedHashMap<String, Object>();
                  m.put("mw", rs.getBigDecimal("mw"));
                  m.put("dw", rs.getBigDecimal("dw"));
                  return m;
                },
                templateId);
        if (!rows.isEmpty()) {
          java.math.BigDecimal mwB = (java.math.BigDecimal) rows.get(0).get("mw");
          java.math.BigDecimal dwB = (java.math.BigDecimal) rows.get(0).get("dw");
          if (mwB != null && dwB != null) {
            double mw = mwB.doubleValue();
            double dw = dwB.doubleValue();
            if (mw >= 0 && dw >= 0 && Double.isFinite(mw) && Double.isFinite(dw) && mw + dw > ROLE_WEIGHT_EPS) {
              Map<String, Double> out = new HashMap<String, Double>();
              out.put("managerWeight", mw);
              out.put("dottedWeight", dw);
              return out;
            }
          }
        }
      } catch (Exception ignored) {
        // fall through
      }
    }
    return getReviewWeights();
  }

  private Map<String, Double> getReviewWeights() {
    try {
      List<Map<String, Object>> rows =
          jdbc.query(
              "SELECT config_key, config_value FROM system_config WHERE config_key IN (?, ?)",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("configKey", rs.getString("config_key"));
                m.put("configValue", rs.getString("config_value"));
                return m;
              },
              "manager_review_weight",
              "dotted_manager_review_weight");
      Double managerWeight = null;
      Double dottedWeight = null;
      for (Map<String, Object> r : rows) {
        try {
          double v = Double.parseDouble(String.valueOf(r.get("configValue")));
          if (Double.isFinite(v)) {
            if ("manager_review_weight".equals(r.get("configKey"))) {
              managerWeight = v;
            } else if ("dotted_manager_review_weight".equals(r.get("configKey"))) {
              dottedWeight = v;
            }
          }
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
      Map<String, Double> out = new HashMap<String, Double>();
      if (managerWeight != null) {
        out.put("managerWeight", managerWeight);
      }
      if (dottedWeight != null) {
        out.put("dottedWeight", dottedWeight);
      }
      return out;
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  private double calcWeightedScore(List<Map<String, Object>> review, List<Map<String, Object>> indicators) {
    if (review == null || review.isEmpty()) {
      return 0;
    }
    Map<String, Double> weightMap = new HashMap<String, Double>();
    if (indicators != null) {
      for (Map<String, Object> ind : indicators) {
        Object w = ind.get("weight");
        double weight = w instanceof Number ? ((Number) w).doubleValue() : 0;
        weightMap.put(String.valueOf(ind.get("name")), weight);
      }
    }
    double totalWeightedScore = 0;
    double totalWeight = 0;
    for (Map<String, Object> item : review) {
      Object nameObj = item.get("indicatorName");
      if (nameObj == null) {
        nameObj = item.get("name");
      }
      String name = nameObj != null ? String.valueOf(nameObj) : "";
      if (name.isEmpty()) {
        continue;
      }
      double w = weightMap.containsKey(name) ? weightMap.get(name) : 0;
      double score = scoreOf(item);
      totalWeightedScore += score * w;
      totalWeight += w;
    }
    if (totalWeight > 0) {
      return totalWeightedScore / totalWeight;
    }
    double sum = 0;
    for (Map<String, Object> item : review) {
      sum += scoreOf(item);
    }
    return sum / review.size();
  }

  private static double scoreOf(Map<String, Object> item) {
    Object s = item.get("score");
    if (s instanceof Number) {
      return ((Number) s).doubleValue();
    }
    return 0;
  }

  private List<Map<String, Object>> getTemplateIndicators(String templateId) {
    if (templateId == null || templateId.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> jsonRows =
        jdbc.query(
            "SELECT indicators FROM performance_template WHERE id = ? LIMIT 1",
            (rs, rn) -> rs.getString("indicators"),
            templateId);
    if (jsonRows.isEmpty() || jsonRows.get(0) == null) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(jsonRows.get(0), REVIEW_LIST_TYPE);
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /** 优先使用记录上的绩效指标快照，否则从所选绩效模板读取。 */
  private List<Map<String, Object>> resolvePerformanceIndicators(Map<String, Object> r) {
    Object snap = r.get("recordPerformanceIndicators");
    String snapStr = snap == null ? null : String.valueOf(snap);
    if (snapStr != null && !snapStr.isEmpty() && !"null".equals(snapStr)) {
      try {
        List<Map<String, Object>> parsed = MAPPER.readValue(snapStr, REVIEW_LIST_TYPE);
        if (parsed != null && !parsed.isEmpty()) {
          return parsed;
        }
      } catch (Exception ignored) {
        // fall through
      }
    }
    String tid = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
    if (tid != null && tid.isEmpty()) {
      tid = null;
    }
    return getTemplateIndicators(tid);
  }

  private static double sumCultureScores(List<Map<String, Object>> cultureReview) {
    if (cultureReview == null || cultureReview.isEmpty()) return 0;
    double total = 0;
    for (Map<String, Object> item : cultureReview) {
      total += scoreOf(item);
    }
    return total;
  }

  /** 模板指标权重之和 / 100，作为绩效部分在百分制总分中的系数（约定合计为 80 → 0.8）。 */
  private static double perfWeightFactorFromIndicators(List<Map<String, Object>> indicators) {
    if (indicators == null || indicators.isEmpty()) {
      return 0.8;
    }
    double sum = 0;
    for (Map<String, Object> ind : indicators) {
      Object w = ind.get("weight");
      sum += w instanceof Number ? ((Number) w).doubleValue() : 0;
    }
    return sum > 0 ? sum / 100.0 : 0.8;
  }

  private double computeTotalScore(
      String assessmentRuleId,
      String templateId,
      List<Map<String, Object>> indicators,
      List<Map<String, Object>> managerReview,
      List<Map<String, Object>> dottedManagerReview,
      boolean hasDottedManager,
      List<Map<String, Object>> cultureManagerReview,
      List<Map<String, Object>> cultureDottedManagerReview)
      throws Exception {
    return computeTotalScore(assessmentRuleId, templateId, indicators, managerReview, dottedManagerReview,
        hasDottedManager, cultureManagerReview, cultureDottedManagerReview, null, null, null, null, null);
  }

  private double computeTotalScore(
      String assessmentRuleId,
      String templateId,
      List<Map<String, Object>> indicators,
      List<Map<String, Object>> managerReview,
      List<Map<String, Object>> dottedManagerReview,
      boolean hasDottedManager,
      List<Map<String, Object>> cultureManagerReview,
      List<Map<String, Object>> cultureDottedManagerReview,
      Map<String, Object> scoringWeightsMap,
      List<Map<String, Object>> cultureDims,
      List<Map<String, Object>> learningManagerReview,
      List<Map<String, Object>> learningDottedManagerReview,
      List<Map<String, Object>> learningIndicators)
      throws Exception {

    if (scoringWeightsMap != null) {
      double pPct = numberVal(scoringWeightsMap.get("performance"));
      double cPct = numberVal(scoringWeightsMap.get("culture"));
      double lPct = numberVal(scoringWeightsMap.get("learning"));

      Map<String, Double> roleWeights = getReviewWeightsForScoring(assessmentRuleId, templateId);
      double mW = roleWeights.containsKey("managerWeight") ? roleWeights.get("managerWeight") : 0.5;
      double dW = roleWeights.containsKey("dottedWeight") ? roleWeights.get("dottedWeight") : 0.5;
      boolean useDotted = hasDottedManager && dottedManagerReview != null;

      double perfRate = 0;
      if (pPct > 0) {
        double mPerf = managerReview != null ? calcWeightedScore(managerReview, indicators) : 0;
        if (useDotted) {
          double dPerf = calcWeightedScore(dottedManagerReview, indicators);
          perfRate = mPerf * mW + dPerf * dW;
        } else {
          perfRate = mPerf;
        }
      }

      double cRate = 0;
      if (cPct > 0) {
        double cMaxSum = cultureMaxSum(cultureDims);
        double mCR = cMaxSum > 0 ? sumCultureScores(cultureManagerReview) / cMaxSum * 100.0 : 0;
        if (useDotted) {
          double dCR = cMaxSum > 0 ? sumCultureScores(cultureDottedManagerReview) / cMaxSum * 100.0 : 0;
          cRate = mCR * mW + dCR * dW;
        } else {
          cRate = mCR;
        }
      }

      double lRate = 0;
      if (lPct > 0 && learningIndicators != null && !learningIndicators.isEmpty()) {
        double mLR = learningManagerReview != null ? calcWeightedScore(learningManagerReview, learningIndicators) : 0;
        if (useDotted) {
          double dLR = learningDottedManagerReview != null ? calcWeightedScore(learningDottedManagerReview, learningIndicators) : 0;
          lRate = mLR * mW + dLR * dW;
        } else {
          lRate = mLR;
        }
      }

      return round2(perfRate * pPct / 100.0 + cRate * cPct / 100.0 + lRate * lPct / 100.0);
    }

    double perfFactor = perfWeightFactorFromIndicators(indicators);
    double mScore = managerReview != null ? calcWeightedScore(managerReview, indicators) : 0;
    double mCulture = sumCultureScores(cultureManagerReview);
    if (!hasDottedManager || dottedManagerReview == null) {
      return round2(mScore * perfFactor + mCulture);
    }
    double dScore = calcWeightedScore(dottedManagerReview, indicators);
    double dCulture = sumCultureScores(cultureDottedManagerReview);
    Map<String, Double> weights = getReviewWeightsForScoring(assessmentRuleId, templateId);
    double mW2 = weights.containsKey("managerWeight") ? weights.get("managerWeight") : 0.5;
    double dW2 = weights.containsKey("dottedWeight") ? weights.get("dottedWeight") : 0.5;
    if (dW2 < ROLE_WEIGHT_EPS) {
      return round2(mScore * perfFactor + mCulture);
    }
    if (mW2 < ROLE_WEIGHT_EPS) {
      return round2(dScore * perfFactor + dCulture);
    }
    double perfScore = mScore * mW2 + dScore * dW2;
    double cultureScore = mCulture * mW2 + dCulture * dW2;
    return round2(perfScore * perfFactor + cultureScore);
  }

  private static double numberVal(Object o) {
    if (o instanceof Number) return ((Number) o).doubleValue();
    if (o == null) return 0;
    try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
  }

  private static double cultureMaxSum(List<Map<String, Object>> cultureDims) {
    if (cultureDims == null || cultureDims.isEmpty()) return 0;
    double sum = 0;
    for (Map<String, Object> d : cultureDims) {
      sum += numberVal(d.get("maxScore"));
    }
    return sum;
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private double computeSingleReviewerTotal(
      List<Map<String, Object>> review,
      List<Map<String, Object>> indicators,
      List<Map<String, Object>> cultureReview) {
    return round2(
        calcWeightedScore(review, indicators) * perfWeightFactorFromIndicators(indicators)
            + sumCultureScores(cultureReview));
  }

  private double computeSingleReviewerTotalWithWeights(
      List<Map<String, Object>> review,
      List<Map<String, Object>> indicators,
      List<Map<String, Object>> cultureReview,
      Map<String, Object> scoringWeightsMap,
      List<Map<String, Object>> cultureDims,
      List<Map<String, Object>> learningReview,
      List<Map<String, Object>> learningIndicators) {
    if (scoringWeightsMap != null) {
      double pPct = numberVal(scoringWeightsMap.get("performance"));
      double cPct = numberVal(scoringWeightsMap.get("culture"));
      double lPct = numberVal(scoringWeightsMap.get("learning"));
      double perfRate = pPct > 0 ? calcWeightedScore(review, indicators) : 0;
      double cRate = 0;
      if (cPct > 0) {
        double cMax = cultureMaxSum(cultureDims);
        cRate = cMax > 0 ? sumCultureScores(cultureReview) / cMax * 100.0 : 0;
      }
      double lRate = 0;
      if (lPct > 0 && learningIndicators != null && !learningIndicators.isEmpty()) {
        lRate = learningReview != null ? calcWeightedScore(learningReview, learningIndicators) : 0;
      }
      return round2(perfRate * pPct / 100.0 + cRate * cPct / 100.0 + lRate * lPct / 100.0);
    }
    return computeSingleReviewerTotal(review, indicators, cultureReview);
  }

  private List<Map<String, Object>> recordCultureDimsFromRow(Map<String, Object> r) {
    return CultureDimensionsSupport.parseListOrDefault((String) r.get("recordCultureDimensions"));
  }

  private Map<String, Object> parseScoringWeightsMap(Map<String, Object> r) {
    String json = r.get("scoringWeights") == null ? null : String.valueOf(r.get("scoringWeights"));
    if (json == null || json.isEmpty() || "null".equals(json)) return null;
    try {
      return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return null;
    }
  }

  /** 评分方案中绩效占比；无方案或未配置时按 100（整表绩效）。 */
  private double schemePerformancePctFromRecord(Map<String, Object> r) {
    Map<String, Object> sw = parseScoringWeightsMap(r);
    if (sw != null && sw.get("performance") != null) {
      double p = numberVal(sw.get("performance"));
      if (p > 1e-6) {
        return p;
      }
    }
    return 100.0;
  }

  private static String truncateForLog(String s, int maxLen) {
    if (s == null) {
      return "null";
    }
    String t = s.trim();
    if (t.isEmpty()) {
      return "(empty)";
    }
    if (t.length() <= maxLen) {
      return t;
    }
    return t.substring(0, maxLen) + "...<truncated,len=" + t.length() + ">";
  }

  private static double cultureMaxScoreSum(List<Map<String, Object>> dims) {
    if (dims == null) {
      return 0;
    }
    double sum = 0;
    for (Map<String, Object> d : dims) {
      Object ms = d.get("maxScore");
      if (ms instanceof Number) {
        sum += ((Number) ms).doubleValue();
      } else if (ms != null) {
        try {
          sum += Double.parseDouble(String.valueOf(ms));
        } catch (NumberFormatException ignored) {
          // ignore
        }
      }
    }
    return sum;
  }

  private List<Map<String, Object>> parseLearningIndicators(Map<String, Object> r) {
    String json = r.get("recordLearningDimensions") == null ? null : String.valueOf(r.get("recordLearningDimensions"));
    if (json == null || json.isEmpty() || "null".equals(json)) return Collections.emptyList();
    try {
      List<Map<String, Object>> parsed = MAPPER.readValue(json, REVIEW_LIST_TYPE);
      return parsed != null ? parsed : Collections.emptyList();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /**
   * 将维度满分缩放到合计 100（兼容旧模板合计非 100）。仅用于学习类快照的兜底解析路径；文化价值观写入绩效记录时不再调用，满分直接取自文化模板。
   */
  private static void scaleDimensionMaxScoresTo100(List<Map<String, Object>> dims) {
    if (dims == null || dims.isEmpty()) {
      return;
    }
    double s = 0;
    for (Map<String, Object> d : dims) {
      s += numberVal(d.get("maxScore"));
    }
    if (s < 1e-6 || Math.abs(s - 100.0) < 0.03) {
      return;
    }
    double k = 100.0 / s;
    for (Map<String, Object> d : dims) {
      d.put("maxScore", round2(numberVal(d.get("maxScore")) * k));
    }
  }

  private List<Map<String, Object>> resolveLearningTemplateRows(String cultureDimJson, String indJson) {
    try {
      if (cultureDimJson != null && !cultureDimJson.trim().isEmpty() && !"[]".equals(cultureDimJson.trim())) {
        List<Map<String, Object>> fromC = MAPPER.readValue(cultureDimJson, REVIEW_LIST_TYPE);
        if (fromC != null && !fromC.isEmpty()) {
          return new ArrayList<Map<String, Object>>(fromC);
        }
      }
      if (indJson == null || indJson.trim().isEmpty() || "[]".equals(indJson.trim())) {
        return new ArrayList<Map<String, Object>>();
      }
      List<Map<String, Object>> fromI = MAPPER.readValue(indJson, REVIEW_LIST_TYPE);
      if (fromI == null) {
        return new ArrayList<Map<String, Object>>();
      }
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      for (Map<String, Object> row : fromI) {
        Map<String, Object> m = new LinkedHashMap<String, Object>(row);
        Object w = row.get("weight");
        double wt = w instanceof Number ? ((Number) w).doubleValue() : numberVal(w);
        if (m.get("maxScore") == null || numberVal(m.get("maxScore")) <= 0) {
          m.put("maxScore", wt > 0 ? wt : 0);
        }
        out.add(m);
      }
      return out;
    } catch (Exception e) {
      return new ArrayList<Map<String, Object>>();
    }
  }

  private boolean isReviewComplete(List<Map<String, Object>> review, List<String> indicatorNames) {
    if (review == null || review.isEmpty() || indicatorNames.isEmpty()) {
      return false;
    }
    Map<String, Map<String, Object>> byName = new HashMap<String, Map<String, Object>>();
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

  private static double[] normalizeRoleWeights(Double managerWeight, Double dottedWeight) {
    if (managerWeight != null && dottedWeight != null) {
      return new double[] {managerWeight, dottedWeight};
    }
    return new double[] {0.5, 0.5};
  }

  private static String trimOrEmpty(String s) {
    return s == null ? "" : s.trim();
  }

  private static String iso(Timestamp ts) {
    return ts == null ? null : ts.toInstant().toString();
  }

  private static Double toDouble(BigDecimal bd) {
    return bd == null ? null : bd.doubleValue();
  }

  private static String scoreGradeFromTotal(double totalScore) {
    if (!Double.isFinite(totalScore)) {
      return "C";
    }
    if (totalScore > 95.0) {
      return "S";
    }
    if (totalScore > 90.0) {
      return "A";
    }
    if (totalScore > 70.0) {
      return "B";
    }
    return "C";
  }

  private static void appendTotalScoreAndGrade(List<String> sets, List<Object> args, double total) {
    sets.add("total_score=?");
    args.add(total);
    sets.add("score_grade=?");
    args.add(scoreGradeFromTotal(total));
  }

  private List<Map<String, Object>> parseReviewJson(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.readValue(json, REVIEW_LIST_TYPE);
    } catch (Exception e) {
      return null;
    }
  }

  private String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 序列化失败");
    }
  }

  private static class WhereClause {
    final String sql;
    final List<Object> args;

    WhereClause(String sql, List<Object> args) {
      this.sql = sql;
      this.args = args;
    }
  }

  private static List<String> parseStatusFilter(String status) {
    if (status == null || status.trim().isEmpty()) {
      return Collections.emptyList();
    }
    List<String> out = new ArrayList<String>();
    for (String part : status.split(",")) {
      String t = part.trim();
      if (!t.isEmpty() && !out.contains(t)) {
        out.add(t);
      }
    }
    return out;
  }

  private void appendEmployeeSubjectFilter(
      List<String> parts, List<Object> args, String subjectCode, String empAlias) {
    String code = subjectCode == null ? "" : subjectCode.trim();
    if (code.isEmpty()) {
      return;
    }
    String sid =
        feishuRegistry
            .findSubjectIdByCode(code)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    parts.add(
        "("
            + empAlias
            + ".feishu_subject_id = ? OR ("
            + empAlias
            + ".feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))");
    args.add(sid);
    args.add(sid);
  }

  private WhereClause buildListWhere(
      String userId,
      String status,
      String focus,
      String period,
      String subjectCode,
      String departmentId,
      String employeeName,
      boolean listAll,
      String empAlias) {
    List<String> parts = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    parts.add("pr.deleted_at IS NULL");

    if ("need_score".equals(focus)) {
      parts.add(
          "((pr.status=? AND pr.manager_id=?) OR (pr.status=? AND (pr.manager_id=? OR pr.dotted_manager_id=?)) OR (pr.status=? AND pr.dotted_manager_id=?))");
      args.add("manager_review");
      args.add(userId);
      args.add("dual_manager_review");
      args.add(userId);
      args.add(userId);
      args.add("dotted_manager_review");
      args.add(userId);
    } else if ("need_approve_goal".equals(focus)) {
      parts.add("(pr.status=? AND (pr.manager_id=? OR pr.dotted_manager_id=?))");
      args.add("goal_pending_review");
      args.add(userId);
      args.add(userId);
    } else {
      List<String> statusList = parseStatusFilter(status);
      if (!statusList.isEmpty()) {
        if (statusList.size() == 1) {
          parts.add("pr.status=?");
          args.add(statusList.get(0));
        } else {
          StringBuilder in = new StringBuilder("pr.status IN (");
          for (int i = 0; i < statusList.size(); i++) {
            if (i > 0) {
              in.append(",");
            }
            in.append("?");
          }
          in.append(")");
          parts.add(in.toString());
          args.addAll(statusList);
        }
      }
    }
    if (period != null && !period.isEmpty()) {
      parts.add("pr.period=?");
      args.add(period);
    }
    if (!listAll) {
      parts.add("(pr.employee_id=? OR pr.manager_id=? OR pr.dotted_manager_id=?)");
      args.add(userId);
      args.add(userId);
      args.add(userId);
    }
    String subCode = subjectCode == null ? "" : subjectCode.trim();
    String deptId = departmentId == null ? "" : departmentId.trim();
    if (listAll) {
      if (!deptId.isEmpty()) {
        if (orgDepartmentService.tableReady()) {
          orgDepartmentService.appendEmployeeDepartmentFilter(parts, args, subCode, deptId, empAlias);
        } else {
          parts.add("(" + empAlias + ".department_id=? OR " + empAlias + ".department_name=?)");
          args.add(deptId);
          args.add(deptId);
          if (!subCode.isEmpty()) {
            appendEmployeeSubjectFilter(parts, args, subCode, empAlias);
          }
        }
      } else if (!subCode.isEmpty()) {
        appendEmployeeSubjectFilter(parts, args, subCode, empAlias);
      }
    }
    String nameQ = employeeName == null ? "" : employeeName.trim();
    if (!nameQ.isEmpty()) {
      String safe = "%" + nameQ.replace("%", "").replace("_", "") + "%";
      if (safe.length() > 2) {
        parts.add(empAlias + ".name LIKE ?");
        args.add(safe);
      }
    }
    String sql = parts.isEmpty() ? "1=1" : String.join(" AND ", parts);
    return new WhereClause(sql, args);
  }

  public Map<String, Object> list(
      String userId,
      String status,
      String focus,
      String period,
      String subjectCode,
      String departmentId,
      String employeeName,
      int page,
      int pageSize) {
    Map<String, Object> empty = new LinkedHashMap<String, Object>();
    empty.put("items", Collections.emptyList());
    empty.put("total", 0);
    empty.put("page", page);
    empty.put("pageSize", pageSize);
    empty.put("canBatchCreate", false);
    empty.put("canExport", false);
    if (userId == null || userId.isEmpty()) {
      return empty;
    }
    List<String> userRoles = resolveRolesForUser(userId);
    MenuFlags flags = performanceMenuFlags(userId);
    WhereClause wc =
        buildListWhere(
            userId, status, focus, period, subjectCode, departmentId, employeeName, flags.listAll, "emp");
    int offset = (page - 1) * pageSize;

    String from =
        " FROM performance_record pr"
            + " LEFT JOIN employee_hierarchy emp ON emp.employee_id = pr.employee_id"
            + " LEFT JOIN employee_hierarchy mgr ON mgr.employee_id = pr.manager_id"
            + " WHERE "
            + wc.sql;

    List<Object> listArgs = new ArrayList<Object>(wc.args);
    listArgs.add(pageSize);
    listArgs.add(offset);

    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pr.id, pr.employee_id, pr.period, pr.status, pr.manager_id, pr.total_score, pr.score_grade,"
                + " pr._created_at, pr._updated_at, emp.name AS employee_name, mgr.name AS manager_name"
                + from
                + " ORDER BY pr._created_at DESC LIMIT ? OFFSET ?",
            (rs, rn) -> mapListItem(rs),
            listArgs.toArray());

    Integer total =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT pr.id)" + from, Integer.class, wc.args.toArray());

    log.info(
        "绩效列表查询: userId={}, roles={}, total={}", userId, userRoles, total);

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    out.put("canBatchCreate", flags.batchCreate);
    out.put("canExport", flags.exportData);
    out.put("canDelete", flags.batchCreate);
    return out;
  }

  private Map<String, Object> mapListItem(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("id", rs.getString("id"));
    item.put("employeeId", rs.getString("employee_id"));
    item.put("employeeName", trimOrEmpty(rs.getString("employee_name")));
    item.put("period", rs.getString("period"));
    item.put("status", rs.getString("status"));
    item.put("managerId", rs.getString("manager_id"));
    item.put("managerName", trimOrEmpty(rs.getString("manager_name")));
    item.put("totalScore", toDouble(rs.getBigDecimal("total_score")));
    item.put("scoreGrade", rs.getString("score_grade"));
    item.put("createdAt", iso(rs.getTimestamp("_created_at")));
    item.put("updatedAt", iso(rs.getTimestamp("_updated_at")));
    return item;
  }

  public Map<String, Object> listSupervisorCalibrationQueue(
      String userId,
      String period,
      String subjectCode,
      String departmentId,
      String employeeName,
      int page,
      int pageSize) {
    Map<String, Object> empty = new LinkedHashMap<String, Object>();
    empty.put("items", Collections.emptyList());
    empty.put("total", 0);
    empty.put("page", page);
    empty.put("pageSize", pageSize);
    if (userId == null || userId.isEmpty()) {
      return empty;
    }
    menuPermissionService.assertSuperAdmin(
        menuPermissionService.getUserRole(userId), "仅超级管理员可查看绩效校准队列");

    List<String> parts = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    parts.add("pr.deleted_at IS NULL");
    parts.add("pr.status=?");
    args.add("final_review");
    if (period != null && !period.trim().isEmpty()) {
      parts.add("pr.period=?");
      args.add(period.trim());
    }
    String subCode = subjectCode == null ? "" : subjectCode.trim();
    String deptId = departmentId == null ? "" : departmentId.trim();
    if (!deptId.isEmpty()) {
      if (orgDepartmentService.tableReady()) {
        orgDepartmentService.appendEmployeeDepartmentFilter(parts, args, subCode, deptId, "emp");
      } else {
        parts.add("(emp.department_id=? OR emp.department_name=?)");
        args.add(deptId);
        args.add(deptId);
        if (!subCode.isEmpty()) {
          appendEmployeeSubjectFilter(parts, args, subCode, "emp");
        }
      }
    } else if (!subCode.isEmpty()) {
      appendEmployeeSubjectFilter(parts, args, subCode, "emp");
    }
    String nameQ = employeeName == null ? "" : employeeName.trim();
    if (!nameQ.isEmpty()) {
      String safe = "%" + nameQ.replace("%", "").replace("_", "") + "%";
      if (safe.length() > 2) {
        parts.add("emp.name LIKE ?");
        args.add(safe);
      }
    }
    String where = String.join(" AND ", parts);
    int offset = (page - 1) * pageSize;
    String from =
        " FROM performance_record pr"
            + " LEFT JOIN employee_hierarchy emp ON emp.employee_id = pr.employee_id"
            + " LEFT JOIN employee_hierarchy mgr ON mgr.employee_id = pr.manager_id"
            + " LEFT JOIN employee_hierarchy dot ON dot.employee_id = pr.dotted_manager_id"
            + " WHERE "
            + where;

    List<Object> listArgs = new ArrayList<Object>(args);
    listArgs.add(pageSize);
    listArgs.add(offset);

    final int[] totalHolder = new int[] {-1};
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pr.id, pr.employee_id, pr.period, pr.status, pr.manager_id, pr.dotted_manager_id,"
                + " pr.total_score, pr.score_grade, pr._created_at, pr._updated_at, emp.name AS employee_name,"
                + " mgr.name AS manager_name, dot.name AS dotted_manager_name,"
                + " COUNT(*) OVER() AS _list_total"
                + from
                + " ORDER BY pr._updated_at DESC LIMIT ? OFFSET ?",
            (rs, rn) -> {
              if (totalHolder[0] < 0) {
                totalHolder[0] = rs.getInt("_list_total");
              }
              Map<String, Object> item = mapListItem(rs);
              String dottedId = rs.getString("dotted_manager_id");
              item.put("dottedManagerId", dottedId);
              if (dottedId != null && !dottedId.isEmpty()) {
                item.put("dottedManagerName", trimOrEmpty(rs.getString("dotted_manager_name")));
              }
              return item;
            },
            listArgs.toArray());

    int total = totalHolder[0] < 0 ? 0 : totalHolder[0];

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  public Map<String, Object> exportData(
      String userId,
      String status,
      String focus,
      String period,
      String subjectCode,
      String departmentId,
      String employeeName) {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", Collections.emptyList());
    if (userId == null || userId.isEmpty()) {
      return out;
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_export");
    MenuFlags flags = performanceMenuFlags(userId);
    WhereClause wc =
        buildListWhere(
            userId, status, focus, period, subjectCode, departmentId, employeeName, flags.listAll, "emp");

    String sql =
        "SELECT pr.employee_id, pr.period, pr.status, pr.total_score, pr.score_grade, pr.self_review, pr.manager_review,"
            + " pr.dotted_manager_review, pr._updated_at, emp.name AS employee_name, emp.department_name"
            + " FROM performance_record pr"
            + " LEFT JOIN employee_hierarchy emp ON emp.employee_id = pr.employee_id"
            + " WHERE "
            + wc.sql
            + " ORDER BY pr._created_at DESC";

    List<Map<String, Object>> items =
        jdbc.query(
            sql,
            (rs, rn) -> {
              Map<String, Object> row = new LinkedHashMap<String, Object>();
              row.put("employeeName", trimOrEmpty(rs.getString("employee_name")));
              row.put("department", trimOrEmpty(rs.getString("department_name")));
              row.put("period", rs.getString("period"));
              row.put("status", rs.getString("status"));
              BigDecimal ts = rs.getBigDecimal("total_score");
              row.put("totalScore", ts == null ? 0 : ts.doubleValue());
              row.put("scoreGrade", rs.getString("score_grade"));
              row.put("selfReviewComment", joinComments(rs.getString("self_review")));
              row.put("managerReviewComment", joinComments(rs.getString("manager_review")));
              row.put("dottedManagerReviewComment", joinComments(rs.getString("dotted_manager_review")));
              row.put("updatedAt", iso(rs.getTimestamp("_updated_at")));
              return row;
            },
            wc.args.toArray());

    log.info("绩效数据导出: userId={}, count={}", userId, items.size());
    out.put("items", items);
    return out;
  }

  private String joinComments(String json) {
    List<Map<String, Object>> list = parseReviewJson(json);
    if (list == null || list.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> r : list) {
      if (sb.length() > 0) {
        sb.append("; ");
      }
      Object c = r.get("comment");
      sb.append(c == null ? "" : String.valueOf(c));
    }
    return sb.toString();
  }

  private Map<String, Object> loadRecord(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT * FROM performance_record WHERE id = ? AND deleted_at IS NULL LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("employeeId", rs.getString("employee_id"));
              m.put("templateId", rs.getString("template_id"));
              m.put("recordPerformanceIndicators", rs.getString("performance_indicators"));
              m.put("recordCultureDimensions", rs.getString("culture_dimensions"));
              m.put("recordLearningDimensions", rs.getString("learning_dimensions"));
              m.put("scoringSchemeId", rs.getString("scoring_scheme_id"));
              m.put("scoringWeights", rs.getString("scoring_weights"));
              m.put("cultureTemplateId", rs.getString("culture_template_id"));
              m.put("learningTemplateId", rs.getString("learning_template_id"));
              m.put("assessmentRuleId", rs.getString("assessment_rule_id"));
              m.put("period", rs.getString("period"));
              m.put("status", rs.getString("status"));
              m.put("managerId", rs.getString("manager_id"));
              m.put("dottedManagerId", rs.getString("dotted_manager_id"));
              m.put("goalSetting", rs.getString("goal_setting"));
              m.put("goalApprovedBy", rs.getString("goal_approved_by"));
              m.put("personalSummary", rs.getString("personal_summary"));
              m.put("managerSummary", rs.getString("manager_summary"));
              m.put("dottedManagerSummary", rs.getString("dotted_manager_summary"));
              m.put("selfReview", rs.getString("self_review"));
              m.put("managerReview", rs.getString("manager_review"));
              m.put("dottedManagerReview", rs.getString("dotted_manager_review"));
              m.put("cultureSelfReview", rs.getString("culture_self_review"));
              m.put("cultureManagerReview", rs.getString("culture_manager_review"));
              m.put("cultureDottedManagerReview", rs.getString("culture_dotted_manager_review"));
              m.put("learningSelfReview", rs.getString("learning_self_review"));
              m.put("learningManagerReview", rs.getString("learning_manager_review"));
              m.put("learningDottedManagerReview", rs.getString("learning_dotted_manager_review"));
              m.put("totalScore", rs.getBigDecimal("total_score"));
              m.put("scoreGrade", rs.getString("score_grade"));
              m.put("rejectionReason", rs.getString("rejection_reason"));
              m.put("finalReviewerId", rs.getString("final_reviewer_id"));
              m.put("finalReviewedAt", rs.getTimestamp("final_reviewed_at"));
              m.put("createdAt", rs.getTimestamp("_created_at"));
              m.put("updatedAt", rs.getTimestamp("_updated_at"));
              return m;
            },
            id);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "绩效记录不存在");
    }
    return rows.get(0);
  }

  public Map<String, Object> getDetail(String userId, String id) {
    Map<String, Object> r = loadRecord(id);
    MenuFlags flags = performanceMenuFlags(userId);
    String employeeId = String.valueOf(r.get("employeeId"));
    String managerId = String.valueOf(r.get("managerId"));
    String dottedManagerId = r.get("dottedManagerId") == null ? null : String.valueOf(r.get("dottedManagerId"));
    boolean isEmployee = employeeId.equals(userId);
    boolean isManager = managerId.equals(userId);
    boolean isDottedManager = dottedManagerId != null && dottedManagerId.equals(userId);
    if (!flags.listAll && !isEmployee && !isManager && !isDottedManager) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该绩效记录");
    }

    String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
    String recordAssessmentRuleId =
        r.get("assessmentRuleId") == null ? null : String.valueOf(r.get("assessmentRuleId")).trim();
    if (recordAssessmentRuleId != null && recordAssessmentRuleId.isEmpty()) {
      recordAssessmentRuleId = null;
    }
    String templateName = "";
    String assessmentRuleName = "";
    List<Map<String, Object>> indicators = resolvePerformanceIndicators(r);
    String rawCultureJson = (String) r.get("recordCultureDimensions");
    String cultureTplId = r.get("cultureTemplateId") == null ? null : String.valueOf(r.get("cultureTemplateId"));
    List<Map<String, Object>> cultureDimensions =
        CultureDimensionsSupport.parseListOrDefault(rawCultureJson);
    log.info(
        "[getPerformanceDetail] recordId={} viewerUserId={} perfTemplateId={} culture_template_id={} cultureRawDbSnippet={} cultureParsedMaxScoreSum={} cultureParsedDims={}",
        r.get("id"),
        userId,
        templateId,
        cultureTplId,
        truncateForLog(rawCultureJson, 600),
        cultureMaxScoreSum(cultureDimensions),
        cultureDimensions);
    if (templateId != null && !templateId.isEmpty()) {
      List<String> tplNames =
          jdbc.query(
              "SELECT name FROM performance_template WHERE id = ? LIMIT 1",
              (rs, rn) -> rs.getString("name"),
              templateId);
      if (!tplNames.isEmpty()) {
        templateName = trimOrEmpty(tplNames.get(0));
      }
    }
    if (recordAssessmentRuleId != null) {
      List<String> ruleNames =
          jdbc.query(
              "SELECT name FROM assessment_rule WHERE id = ? LIMIT 1",
              (rs, rn) -> rs.getString("name"),
              recordAssessmentRuleId);
      if (!ruleNames.isEmpty()) {
        assessmentRuleName = trimOrEmpty(ruleNames.get(0));
      }
    }

    boolean isCompleted = "completed".equals(r.get("status"));
    boolean hideReviewData = isEmployee && !flags.listAll && !isCompleted;

    Set<String> nameIds = new HashSet<String>();
    nameIds.add(employeeId);
    nameIds.add(managerId);
    if (dottedManagerId != null) {
      nameIds.add(dottedManagerId);
    }
    Map<String, String> nameById = loadEmployeeNames(nameIds);

    List<String> indicatorNames = new ArrayList<String>();
    for (Map<String, Object> ind : indicators) {
      indicatorNames.add(String.valueOf(ind.get("name")));
    }

    String detailViewerRole = menuPermissionService.getUserRole(userId);
    boolean isSuperAdminViewer = "super_admin".equals(detailViewerRole);
    boolean showReviewSynthesis =
        !hideReviewData
            && !indicatorNames.isEmpty()
            && (isManager || isDottedManager || flags.listAll || isSuperAdminViewer)
            && (dottedManagerId != null || isSuperAdminViewer);

    List<Map<String, Object>> mgrRevForTotals = parseReviewJson((String) r.get("managerReview"));
    List<Map<String, Object>> dotRevForTotals = parseReviewJson((String) r.get("dottedManagerReview"));
    List<Map<String, Object>> cultureMgrRev = parseReviewJson((String) r.get("cultureManagerReview"));
    List<Map<String, Object>> cultureDotRev = parseReviewJson((String) r.get("cultureDottedManagerReview"));
    Double managerWeightedTotal = null;
    Double dottedManagerWeightedTotal = null;
    if (!indicatorNames.isEmpty()) {
      if (isReviewComplete(mgrRevForTotals, indicatorNames)) {
        managerWeightedTotal = round2(calcWeightedScore(mgrRevForTotals, indicators));
      }
      if (dottedManagerId != null && isReviewComplete(dotRevForTotals, indicatorNames)) {
        dottedManagerWeightedTotal = round2(calcWeightedScore(dotRevForTotals, indicators));
      }
    }

    Map<String, Object> reviewRoleWeights = null;
    List<Map<String, Object>> reviewMergedIndicators = null;
    Double reviewMergedTotal = null;
    if (showReviewSynthesis) {
      Map<String, Double> weights = getReviewWeightsForScoring(recordAssessmentRuleId, templateId);
      double[] norm =
          normalizeRoleWeights(weights.get("managerWeight"), weights.get("dottedWeight"));
      reviewRoleWeights = new LinkedHashMap<String, Object>();
      reviewRoleWeights.put("managerWeight", norm[0]);
      reviewRoleWeights.put("dottedWeight", norm[1]);

      Map<String, Double> mMap = new HashMap<String, Double>();
      if (mgrRevForTotals != null) {
        for (Map<String, Object> x : mgrRevForTotals) {
          mMap.put(String.valueOf(x.get("indicatorName")), scoreOf(x));
        }
      }
      Map<String, Double> dMap = new HashMap<String, Double>();
      if (dotRevForTotals != null) {
        for (Map<String, Object> x : dotRevForTotals) {
          dMap.put(String.valueOf(x.get("indicatorName")), scoreOf(x));
        }
      }
      reviewMergedIndicators = new ArrayList<Map<String, Object>>();
      for (String name : indicatorNames) {
        Double ms = mMap.get(name);
        Double ds = dMap.get(name);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("indicatorName", name);
        if (ms != null) {
          row.put("managerScore", ms);
        }
        if (ds != null) {
          row.put("dottedScore", ds);
        }
        if (ms != null && ds != null) {
          if (norm[1] <= ROLE_WEIGHT_EPS) {
            row.put("mergedScore", round2(ms));
          } else if (norm[0] <= ROLE_WEIGHT_EPS) {
            row.put("mergedScore", round2(ds));
          } else {
            row.put("mergedScore", round2(norm[0] * ms + norm[1] * ds));
          }
        }
        reviewMergedIndicators.add(row);
      }
      boolean mgrDone = isReviewComplete(mgrRevForTotals, indicatorNames);
      boolean dotDone = isReviewComplete(dotRevForTotals, indicatorNames);
      boolean bothWeighted = norm[0] > ROLE_WEIGHT_EPS && norm[1] > ROLE_WEIGHT_EPS;
      boolean canMergeTotal;
      if (bothWeighted) {
        canMergeTotal = mgrDone && dotDone && dottedManagerId != null;
      } else if (norm[1] <= ROLE_WEIGHT_EPS) {
        canMergeTotal = mgrDone;
      } else if (norm[0] <= ROLE_WEIGHT_EPS) {
        canMergeTotal = dotDone && dottedManagerId != null;
      } else {
        canMergeTotal = mgrDone;
      }
      if (canMergeTotal) {
        try {
          Map<String, Object> swMap = parseScoringWeightsMap(r);
          List<Map<String, Object>> learningMgrRevForMerge = parseReviewJson((String) r.get("learningManagerReview"));
          List<Map<String, Object>> learningDotRevForMerge = parseReviewJson((String) r.get("learningDottedManagerReview"));
          List<Map<String, Object>> learningIndsForMerge = parseLearningIndicators(r);
          reviewMergedTotal =
              computeTotalScore(
                  recordAssessmentRuleId,
                  templateId,
                  indicators,
                  mgrRevForTotals,
                  dotRevForTotals,
                  dottedManagerId != null,
                  cultureMgrRev,
                  cultureDotRev,
                  swMap,
                  cultureDimensions,
                  learningMgrRevForMerge,
                  learningDotRevForMerge,
                  learningIndsForMerge);
        } catch (Exception e) {
          reviewMergedTotal = null;
        }
      }
    }

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", r.get("id"));
    out.put("employeeId", employeeId);
    out.put("employeeName", nameById.get(employeeId));
    if (templateId != null) {
      out.put("templateId", templateId);
    }
    if (recordAssessmentRuleId != null) {
      out.put("assessmentRuleId", recordAssessmentRuleId);
    }
    if (!assessmentRuleName.isEmpty()) {
      out.put("assessmentRuleName", assessmentRuleName);
    }
    out.put("templateName", templateName);
    out.put("period", r.get("period"));
    out.put("status", r.get("status"));
    out.put("managerId", managerId);
    out.put("managerName", nameById.get(managerId));
    if (dottedManagerId != null) {
      out.put("dottedManagerId", dottedManagerId);
      out.put("dottedManagerName", nameById.get(dottedManagerId));
    }
    out.put("goalSetting", parseReviewJson((String) r.get("goalSetting")));
    Object goalApprovedBy = r.get("goalApprovedBy");
    if (goalApprovedBy != null) {
      out.put("goalApprovedBy", goalApprovedBy);
    }
    Object personalSummary = r.get("personalSummary");
    if (personalSummary != null) {
      out.put("personalSummary", personalSummary);
    }
    Object managerSummary = r.get("managerSummary");
    if (managerSummary != null) {
      out.put("managerSummary", managerSummary);
    }
    Object dottedManagerSummary = r.get("dottedManagerSummary");
    if (dottedManagerSummary != null) {
      out.put("dottedManagerSummary", dottedManagerSummary);
    }
    out.put("selfReview", parseReviewJson((String) r.get("selfReview")));
    out.put("cultureSelfReview", parseReviewJson((String) r.get("cultureSelfReview")));
    if (!hideReviewData) {
      out.put("managerReview", mgrRevForTotals);
      out.put("dottedManagerReview", dotRevForTotals);
      out.put("cultureManagerReview", cultureMgrRev);
      out.put("cultureDottedManagerReview", cultureDotRev);
    }
    out.put("totalScore", toDouble((BigDecimal) r.get("totalScore")));
    out.put("scoreGrade", r.get("scoreGrade"));
    if (managerWeightedTotal != null) {
      out.put("managerWeightedTotal", managerWeightedTotal);
    }
    if (dottedManagerWeightedTotal != null) {
      out.put("dottedManagerWeightedTotal", dottedManagerWeightedTotal);
    }
    Object rejectionReason = r.get("rejectionReason");
    if (rejectionReason != null) {
      out.put("rejectionReason", rejectionReason);
    }
    Object finalReviewerId = r.get("finalReviewerId");
    if (finalReviewerId != null) {
      out.put("finalReviewerId", finalReviewerId);
    }
    Timestamp finalReviewedAt = (Timestamp) r.get("finalReviewedAt");
    if (finalReviewedAt != null) {
      out.put("finalReviewedAt", iso(finalReviewedAt));
    }
    out.put("indicators", indicators);
    out.put("cultureDimensions", cultureDimensions);
    List<Map<String, Object>> learningDimensions = Collections.emptyList();
    try {
      String ldJson = (String) r.get("recordLearningDimensions");
      if (ldJson != null && !ldJson.trim().isEmpty()) {
        learningDimensions = MAPPER.readValue(ldJson, REVIEW_LIST_TYPE);
      }
    } catch (Exception ignored) {
    }
    out.put("learningDimensions", learningDimensions);
    if (r.get("scoringSchemeId") != null) {
      out.put("scoringSchemeId", r.get("scoringSchemeId"));
    }
    if (r.get("scoringWeights") != null) {
      try {
        out.put("scoringWeights", MAPPER.readValue((String) r.get("scoringWeights"), Map.class));
      } catch (Exception ignored) {
        out.put("scoringWeights", r.get("scoringWeights"));
      }
    }
    if (r.get("cultureTemplateId") != null) {
      out.put("cultureTemplateId", r.get("cultureTemplateId"));
    }
    if (r.get("learningTemplateId") != null) {
      out.put("learningTemplateId", r.get("learningTemplateId"));
    }
    // 员工本人的学习与成长自评与绩效/文化自评一致，不因 hideReviewData 隐藏；上级学习评仍受 hideReviewData 控制
    out.put("learningSelfReview", parseReviewJson((String) r.get("learningSelfReview")));
    if (!hideReviewData) {
      out.put("learningManagerReview", parseReviewJson((String) r.get("learningManagerReview")));
      out.put("learningDottedManagerReview", parseReviewJson((String) r.get("learningDottedManagerReview")));
    }
    if (reviewRoleWeights != null) {
      out.put("reviewRoleWeights", reviewRoleWeights);
    }
    if (reviewMergedIndicators != null) {
      out.put("reviewMergedIndicators", reviewMergedIndicators);
    }
    if (reviewMergedTotal != null) {
      out.put("reviewMergedTotal", reviewMergedTotal);
    }
    out.put("createdAt", iso((Timestamp) r.get("createdAt")));
    out.put("updatedAt", iso((Timestamp) r.get("updatedAt")));
    out.put("calibrationAssignees", employeeService.listCalibrationAssigneesWithNames());
    return out;
  }

  private Map<String, String> loadEmployeeNames(Set<String> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> idList = new ArrayList<String>(ids);
    String placeholders = String.join(",", Collections.nCopies(idList.size(), "?"));
    Map<String, String> nameById = new HashMap<String, String>();
    jdbc.query(
        "SELECT employee_id, name FROM employee_hierarchy WHERE employee_id IN (" + placeholders + ")",
        rs -> {
          nameById.put(rs.getString("employee_id"), trimOrEmpty(rs.getString("name")));
        },
        idList.toArray());
    return nameById;
  }

  public Map<String, Object> softDelete(String userId, String id) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_batch_create");
    int updated =
        jdbc.update(
            "UPDATE performance_record SET deleted_at = NOW(), _updated_by = ? WHERE id = ? AND deleted_at IS NULL",
            userId,
            id);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "绩效记录不存在");
    }
    log.info("绩效 {} 已软删除，操作人: {}", id, userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> saveDraft(String userId, String id, Map<String, Object> body) {
    Map<String, Object> r = loadRecord(id);
    String reviewType = String.valueOf(body.get("reviewType"));
    Object content = body.get("content");
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();

    if ("goal".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存目标设定");
      }
      sets.add("goal_setting=?");
      args.add(writeJson(content));
    } else if ("self".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存该草稿");
      }
      sets.add("self_review=?");
      args.add(writeJson(content));
      if (body.containsKey("personalSummary")) {
        sets.add("personal_summary=?");
        args.add(body.get("personalSummary"));
      }
      if (body.containsKey("cultureContent")) {
        List<Map<String, Object>> cultDraft =
            body.get("cultureContent") instanceof List
                ? (List<Map<String, Object>>) body.get("cultureContent")
                : null;
        if (cultDraft == null) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观评分格式无效");
        }
        CultureDimensionsSupport.assertCultureScoresValid(cultDraft, recordCultureDimsFromRow(r));
        sets.add("culture_self_review=?");
        args.add(writeJson(cultDraft));
      }
      if (body.containsKey("learningContent")) {
        sets.add("learning_self_review=?");
        args.add(writeJson(body.get("learningContent")));
      }
    } else if ("manager".equals(reviewType)) {
      if (!String.valueOf(r.get("managerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存该草稿");
      }
      String st = String.valueOf(r.get("status"));
      if (!"manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许保存上级评分草稿");
      }
      sets.add("manager_review=?");
      args.add(writeJson(content));
      if (body.containsKey("managerSummary")) {
        sets.add("manager_summary=?");
        args.add(body.get("managerSummary"));
      }
      if (body.containsKey("cultureContent")) {
        List<Map<String, Object>> cultDraft =
            body.get("cultureContent") instanceof List
                ? (List<Map<String, Object>>) body.get("cultureContent")
                : null;
        if (cultDraft == null) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观评分格式无效");
        }
        CultureDimensionsSupport.assertCultureScoresValid(cultDraft, recordCultureDimsFromRow(r));
        sets.add("culture_manager_review=?");
        args.add(writeJson(cultDraft));
      }
      if (body.containsKey("learningContent")) {
        sets.add("learning_manager_review=?");
        args.add(writeJson(body.get("learningContent")));
      }
    } else if ("dotted_manager".equals(reviewType)) {
      if (r.get("dottedManagerId") == null || !String.valueOf(r.get("dottedManagerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存该草稿");
      }
      String st = String.valueOf(r.get("status"));
      if (!"dotted_manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许保存虚线上级评分草稿");
      }
      sets.add("dotted_manager_review=?");
      args.add(writeJson(content));
      if (body.containsKey("dottedManagerSummary")) {
        sets.add("dotted_manager_summary=?");
        args.add(body.get("dottedManagerSummary"));
      }
      if (body.containsKey("cultureContent")) {
        List<Map<String, Object>> cultDraft =
            body.get("cultureContent") instanceof List
                ? (List<Map<String, Object>>) body.get("cultureContent")
                : null;
        if (cultDraft == null) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观评分格式无效");
        }
        CultureDimensionsSupport.assertCultureScoresValid(cultDraft, recordCultureDimsFromRow(r));
        sets.add("culture_dotted_manager_review=?");
        args.add(writeJson(cultDraft));
      }
      if (body.containsKey("learningContent")) {
        sets.add("learning_dotted_manager_review=?");
        args.add(writeJson(body.get("learningContent")));
      }
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无效的评审类型");
    }

    args.add(id);
    jdbc.update("UPDATE performance_record SET " + String.join(", ", sets) + " WHERE id=?", args.toArray());
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> submit(String userId, String id, Map<String, Object> body) throws Exception {
    Map<String, Object> r = loadRecord(id);
    String oldStatus = String.valueOf(r.get("status"));
    String reviewType = String.valueOf(body.get("reviewType"));
    List<Map<String, Object>> content =
        body.get("content") instanceof List ? (List<Map<String, Object>>) body.get("content") : null;
    String newStatus = String.valueOf(r.get("status"));
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    String recordAssessmentRuleId =
        r.get("assessmentRuleId") == null ? null : String.valueOf(r.get("assessmentRuleId")).trim();
    if (recordAssessmentRuleId != null && recordAssessmentRuleId.isEmpty()) {
      recordAssessmentRuleId = null;
    }

    List<Map<String, Object>> recordPerfIndicators = resolvePerformanceIndicators(r);

    if ("goal".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交目标设定");
      }
      String st = String.valueOf(r.get("status"));
      if (!"goal_setting".equals(st) && !"goal_rejected".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交目标设定");
      }
      List<Map<String, Object>> goalEff;
      if (body.get("templateId") != null) {
        String tSel = String.valueOf(body.get("templateId")).trim();
        goalEff = tSel.isEmpty() ? resolvePerformanceIndicators(r) : getTemplateIndicators(tSel);
      } else {
        goalEff = resolvePerformanceIndicators(r);
      }
      if (goalEff == null || goalEff.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择绩效模板或保存绩效指标");
      }
      if (content == null || content.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写绩效目标");
      }
      for (Map<String, Object> ind : goalEff) {
        String nm = String.valueOf(ind.get("name"));
        boolean ok = false;
        for (Map<String, Object> g : content) {
          if (nm.equals(String.valueOf(g.get("indicatorName")))) {
            ok = true;
            break;
          }
        }
        if (!ok) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请为指标「" + nm + "」填写衡量标准");
        }
      }
      Map<String, Object> swSubmit = parseScoringWeightsMap(r);
      TemplateService.validateGoalSubmitScoringAlignment(
          swSubmit, content, recordCultureDimsFromRow(r), parseLearningIndicators(r));
      sets.add("goal_setting=?");
      args.add(writeJson(content));
      Object selectedTemplateId = body.get("templateId");
      if (selectedTemplateId != null) {
        String tid = String.valueOf(selectedTemplateId).trim();
        sets.add("template_id=?");
        args.add(tid.isEmpty() ? null : tid);
      }
      newStatus = "goal_pending_review";
    } else if ("self".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交自评");
      }
      String st = String.valueOf(r.get("status"));
      if (!"self_review".equals(st) && !"goal_rejected".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交自评");
      }
      sets.add("self_review=?");
      args.add(writeJson(content));
      if (body.containsKey("personalSummary")) {
        sets.add("personal_summary=?");
        args.add(body.get("personalSummary"));
      }
      List<Map<String, Object>> cultureSelf =
          body.get("cultureContent") instanceof List ? (List<Map<String, Object>>) body.get("cultureContent") : null;
      if (cultureSelf != null) {
        sets.add("culture_self_review=?");
        args.add(writeJson(cultureSelf));
      }
      List<Map<String, Object>> learningSelf =
          body.get("learningContent") instanceof List ? (List<Map<String, Object>>) body.get("learningContent") : null;
      if (learningSelf != null) {
        sets.add("learning_self_review=?");
        args.add(writeJson(learningSelf));
      }
      List<Map<String, Object>> existingCultureSelf = parseReviewJson((String) r.get("cultureSelfReview"));
      List<Map<String, Object>> dimsSelf = recordCultureDimsFromRow(r);
      List<Map<String, Object>> effectiveCultureSelf =
          cultureSelf != null && !cultureSelf.isEmpty() ? cultureSelf : existingCultureSelf;
      if (!dimsSelf.isEmpty()) {
        if (!CultureDimensionsSupport.isCultureReviewComplete(effectiveCultureSelf, dimsSelf)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请完成文化价值观自评");
        }
      }
      CultureDimensionsSupport.assertCultureScoresValid(effectiveCultureSelf, dimsSelf);
      List<Map<String, Object>> indicators = recordPerfIndicators;
      List<String> indicatorNames = new ArrayList<String>();
      for (Map<String, Object> ind : indicators) {
        indicatorNames.add(String.valueOf(ind.get("name")));
      }
      if (isReviewComplete(content, indicatorNames)) {
        Map<String, Object> swSelf = parseScoringWeightsMap(r);
        List<Map<String, Object>> learningIndsS = parseLearningIndicators(r);
        appendTotalScoreAndGrade(
            sets,
            args,
            computeSingleReviewerTotalWithWeights(
                content, indicators, effectiveCultureSelf, swSelf, dimsSelf, learningSelf, learningIndsS));
      }
      newStatus = r.get("dottedManagerId") != null ? "dual_manager_review" : "manager_review";
    } else if ("manager".equals(reviewType)) {
      if (!String.valueOf(r.get("managerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交上级评分");
      }
      String st = String.valueOf(r.get("status"));
      if (!"manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交上级评分");
      }
      sets.add("manager_review=?");
      args.add(writeJson(content));
      if (body.containsKey("managerSummary")) {
        sets.add("manager_summary=?");
        args.add(body.get("managerSummary"));
      }
      List<Map<String, Object>> cultureMgr =
          body.get("cultureContent") instanceof List ? (List<Map<String, Object>>) body.get("cultureContent") : null;
      if (cultureMgr != null) {
        sets.add("culture_manager_review=?");
        args.add(writeJson(cultureMgr));
      }
      List<Map<String, Object>> learningMgr =
          body.get("learningContent") instanceof List ? (List<Map<String, Object>>) body.get("learningContent") : null;
      if (learningMgr != null) {
        sets.add("learning_manager_review=?");
        args.add(writeJson(learningMgr));
      }
      boolean hasDotted = r.get("dottedManagerId") != null;
      String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
      List<Map<String, Object>> indicators = recordPerfIndicators;
      List<String> indicatorNames = new ArrayList<String>();
      for (Map<String, Object> ind : indicators) {
        indicatorNames.add(String.valueOf(ind.get("name")));
      }
      List<Map<String, Object>> existingDot = parseReviewJson((String) r.get("dottedManagerReview"));
      List<Map<String, Object>> existingCultureDot = parseReviewJson((String) r.get("cultureDottedManagerReview"));
      List<Map<String, Object>> existingCultureMgrForSubmit =
          parseReviewJson((String) r.get("cultureManagerReview"));
      List<Map<String, Object>> dimsMgr = recordCultureDimsFromRow(r);
      List<Map<String, Object>> effectiveCultureMgr =
          cultureMgr != null && !cultureMgr.isEmpty() ? cultureMgr : existingCultureMgrForSubmit;
      if (!dimsMgr.isEmpty()) {
        if (!CultureDimensionsSupport.isCultureReviewComplete(effectiveCultureMgr, dimsMgr)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请完成文化价值观上级评分");
        }
      }
      CultureDimensionsSupport.assertCultureScoresValid(effectiveCultureMgr, dimsMgr);
      List<Map<String, Object>> cultureMgrForTotals = effectiveCultureMgr;
      Map<String, Object> swMgr = parseScoringWeightsMap(r);
      List<Map<String, Object>> learningIndsMgr = parseLearningIndicators(r);
      List<Map<String, Object>> existingLearningDot = parseReviewJson((String) r.get("learningDottedManagerReview"));
      List<Map<String, Object>> existingLearningMgrForSubmit = parseReviewJson((String) r.get("learningManagerReview"));
      List<Map<String, Object>> effectiveLearningMgr =
          learningMgr != null && !learningMgr.isEmpty() ? learningMgr : existingLearningMgrForSubmit;

      if ("final_review".equals(st)) {
        newStatus = "final_review";
        if (hasDotted) {
          boolean mgrComplete = isReviewComplete(content, indicatorNames);
          boolean dotComplete = isReviewComplete(existingDot, indicatorNames);
          if (mgrComplete && dotComplete) {
            appendTotalScoreAndGrade(
                sets,
                args,
                computeTotalScore(
                    recordAssessmentRuleId,
                    templateId,
                    recordPerfIndicators,
                    content,
                    existingDot,
                    true,
                    cultureMgrForTotals,
                    existingCultureDot,
                    swMgr,
                    dimsMgr,
                    effectiveLearningMgr,
                    existingLearningDot,
                    learningIndsMgr));
          } else if (mgrComplete) {
            appendTotalScoreAndGrade(
                sets,
                args,
                computeSingleReviewerTotalWithWeights(
                    content, indicators, cultureMgrForTotals, swMgr, dimsMgr, effectiveLearningMgr, learningIndsMgr));
          }
        } else {
          appendTotalScoreAndGrade(
              sets,
              args,
              computeTotalScore(
                  recordAssessmentRuleId,
                  templateId,
                  recordPerfIndicators,
                  content,
                  null,
                  false,
                  cultureMgrForTotals,
                  null,
                  swMgr,
                  dimsMgr,
                  effectiveLearningMgr,
                  null,
                  learningIndsMgr));
        }
      } else if ("dual_manager_review".equals(st)) {
        boolean mgrComplete = isReviewComplete(content, indicatorNames);
        boolean dotComplete = isReviewComplete(existingDot, indicatorNames);
        if (mgrComplete && dotComplete && hasDotted) {
          appendTotalScoreAndGrade(
              sets,
              args,
              computeTotalScore(
                  recordAssessmentRuleId,
                  templateId,
                  recordPerfIndicators,
                  content,
                  existingDot,
                  true,
                  cultureMgrForTotals,
                  existingCultureDot,
                  swMgr,
                  dimsMgr,
                  effectiveLearningMgr,
                  existingLearningDot,
                  learningIndsMgr));
          newStatus = "final_review";
        } else {
          newStatus = "dual_manager_review";
          if (mgrComplete) {
            appendTotalScoreAndGrade(
                sets,
                args,
                computeSingleReviewerTotalWithWeights(
                    content, indicators, cultureMgrForTotals, swMgr, dimsMgr, effectiveLearningMgr, learningIndsMgr));
          }
        }
      } else {
        if (!hasDotted) {
          appendTotalScoreAndGrade(
              sets,
              args,
              computeTotalScore(
                  recordAssessmentRuleId,
                  templateId,
                  recordPerfIndicators,
                  content,
                  null,
                  false,
                  cultureMgrForTotals,
                  null,
                  swMgr,
                  dimsMgr,
                  effectiveLearningMgr,
                  null,
                  learningIndsMgr));
          newStatus = "final_review";
        } else {
          newStatus = "dual_manager_review";
          if (isReviewComplete(content, indicatorNames)) {
            appendTotalScoreAndGrade(
                sets,
                args,
                computeSingleReviewerTotalWithWeights(
                    content, indicators, cultureMgrForTotals, swMgr, dimsMgr, effectiveLearningMgr, learningIndsMgr));
          }
        }
      }
    } else if ("dotted_manager".equals(reviewType)) {
      if (r.get("dottedManagerId") == null || !String.valueOf(r.get("dottedManagerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交虚线上级评分");
      }
      String st = String.valueOf(r.get("status"));
      if (!"dotted_manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交虚线上级评分");
      }
      sets.add("dotted_manager_review=?");
      args.add(writeJson(content));
      if (body.containsKey("dottedManagerSummary")) {
        sets.add("dotted_manager_summary=?");
        args.add(body.get("dottedManagerSummary"));
      }
      List<Map<String, Object>> cultureDot =
          body.get("cultureContent") instanceof List ? (List<Map<String, Object>>) body.get("cultureContent") : null;
      if (cultureDot != null) {
        sets.add("culture_dotted_manager_review=?");
        args.add(writeJson(cultureDot));
      }
      List<Map<String, Object>> learningDot =
          body.get("learningContent") instanceof List ? (List<Map<String, Object>>) body.get("learningContent") : null;
      if (learningDot != null) {
        sets.add("learning_dotted_manager_review=?");
        args.add(writeJson(learningDot));
      }
      List<Map<String, Object>> existingCultureDotForSubmit =
          parseReviewJson((String) r.get("cultureDottedManagerReview"));
      List<Map<String, Object>> dimsDot = recordCultureDimsFromRow(r);
      List<Map<String, Object>> effectiveCultureDot =
          cultureDot != null && !cultureDot.isEmpty() ? cultureDot : existingCultureDotForSubmit;
      if (!dimsDot.isEmpty()) {
        if (!CultureDimensionsSupport.isCultureReviewComplete(effectiveCultureDot, dimsDot)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请完成文化价值观虚线上级评分");
        }
      }
      CultureDimensionsSupport.assertCultureScoresValid(effectiveCultureDot, dimsDot);
      List<Map<String, Object>> cultureDotForTotals = effectiveCultureDot;
      String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
      List<Map<String, Object>> indicators = recordPerfIndicators;
      List<String> indicatorNames = new ArrayList<String>();
      for (Map<String, Object> ind : indicators) {
        indicatorNames.add(String.valueOf(ind.get("name")));
      }
      List<Map<String, Object>> existingMgr = parseReviewJson((String) r.get("managerReview"));
      List<Map<String, Object>> existingCultureMgr = parseReviewJson((String) r.get("cultureManagerReview"));
      Map<String, Object> swDot = parseScoringWeightsMap(r);
      List<Map<String, Object>> learningIndsDot = parseLearningIndicators(r);
      List<Map<String, Object>> existingLearningMgrForDot = parseReviewJson((String) r.get("learningManagerReview"));
      List<Map<String, Object>> existingLearningDotForSubmit = parseReviewJson((String) r.get("learningDottedManagerReview"));
      List<Map<String, Object>> effectiveLearningDot =
          learningDot != null && !learningDot.isEmpty() ? learningDot : existingLearningDotForSubmit;

      if ("final_review".equals(st)) {
        newStatus = "final_review";
        boolean dotComplete = isReviewComplete(content, indicatorNames);
        boolean mgrComplete = isReviewComplete(existingMgr, indicatorNames);
        if (mgrComplete && dotComplete && r.get("dottedManagerId") != null) {
          appendTotalScoreAndGrade(
              sets,
              args,
              computeTotalScore(
                  recordAssessmentRuleId,
                  templateId,
                  recordPerfIndicators,
                  existingMgr,
                  content,
                  true,
                  existingCultureMgr,
                  cultureDotForTotals,
                  swDot,
                  dimsDot,
                  existingLearningMgrForDot,
                  effectiveLearningDot,
                  learningIndsDot));
        } else if (dotComplete) {
          appendTotalScoreAndGrade(
              sets,
              args,
              computeSingleReviewerTotalWithWeights(
                  content, indicators, cultureDotForTotals, swDot, dimsDot, effectiveLearningDot, learningIndsDot));
        }
      } else if ("dual_manager_review".equals(st)) {
        boolean dotComplete = isReviewComplete(content, indicatorNames);
        boolean mgrComplete = isReviewComplete(existingMgr, indicatorNames);
        if (mgrComplete && dotComplete) {
          appendTotalScoreAndGrade(
              sets,
              args,
              computeTotalScore(
                  recordAssessmentRuleId,
                  templateId,
                  recordPerfIndicators,
                  existingMgr,
                  content,
                  true,
                  existingCultureMgr,
                  cultureDotForTotals,
                  swDot,
                  dimsDot,
                  existingLearningMgrForDot,
                  effectiveLearningDot,
                  learningIndsDot));
          newStatus = "final_review";
        } else {
          newStatus = "dual_manager_review";
          if (dotComplete) {
            appendTotalScoreAndGrade(
                sets,
                args,
                computeSingleReviewerTotalWithWeights(
                    content, indicators, cultureDotForTotals, swDot, dimsDot, effectiveLearningDot, learningIndsDot));
          }
        }
      } else {
        appendTotalScoreAndGrade(
            sets,
            args,
            computeTotalScore(
                recordAssessmentRuleId,
                templateId,
                recordPerfIndicators,
                existingMgr,
                content,
                true,
                existingCultureMgr,
                cultureDotForTotals,
                swDot,
                dimsDot,
                existingLearningMgrForDot,
                effectiveLearningDot,
                learningIndsDot));
        newStatus = "final_review";
      }
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无效的评审类型");
    }

    if ("goal".equals(reviewType)) {
      sets.add("rejection_reason=NULL");
    } else if ("self".equals(reviewType)) {
      String stClear = String.valueOf(r.get("status"));
      if ("self_review".equals(stClear) || "goal_rejected".equals(stClear)) {
        sets.add("rejection_reason=NULL");
      }
    }

    sets.add("status=?");
    args.add(newStatus);
    args.add(id);
    jdbc.update("UPDATE performance_record SET " + String.join(", ", sets) + " WHERE id=?", args.toArray());
    if (!oldStatus.equals(newStatus)) {
      tryLogPerformanceFlow(id, oldStatus, newStatus, "submit", userId, reviewType);
    }
    log.info("绩效 {} 已提交，新状态: {}", id, newStatus);
    try {
      performanceFeishuTaskService.completeAfterSubmit(userId, id, oldStatus, reviewType);
    } catch (Exception ex) {
      log.warn("绩效飞书待办完成异常 record={}", id, ex);
    }
    try {
      if ("goal".equals(reviewType)) {
        performanceFeishuNotifier.notifyGoalPendingReview(r, id);
      } else if ("self".equals(reviewType)) {
        performanceFeishuNotifier.notifySelfSubmitted(r, id, newStatus);
      } else if ("manager".equals(reviewType)) {
        performanceFeishuNotifier.notifyAfterManagerSubmit(userId, r, id, oldStatus, newStatus);
      } else if ("dotted_manager".equals(reviewType)) {
        performanceFeishuNotifier.notifyAfterDottedSubmit(userId, r, id, oldStatus, newStatus);
      }
    } catch (Exception ex) {
      log.warn("绩效飞书通知派发异常 record={}", id, ex);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> reject(String userId, String id, String reason) {
    Map<String, Object> r = loadRecord(id);
    boolean isManager = String.valueOf(r.get("managerId")).equals(userId);
    boolean isDottedManager =
        r.get("dottedManagerId") != null && String.valueOf(r.get("dottedManagerId")).equals(userId);
    if (!isManager && !isDottedManager) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权驳回该绩效");
    }
    String st = String.valueOf(r.get("status"));
    if (isManager && !"manager_review".equals(st) && !"dual_manager_review".equals(st)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许驳回");
    }
    if (isDottedManager && !"dotted_manager_review".equals(st) && !"dual_manager_review".equals(st)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许驳回");
    }
    jdbc.update(
        "UPDATE performance_record SET status=?, rejection_reason=? WHERE id=?",
        "self_review",
        reason,
        id);
    tryLogPerformanceFlow(id, st, "self_review", "reject_subordinate", userId, reason);
    log.info("绩效 {} 已驳回自评并退回员工，原因: {}", id, reason);
    try {
      performanceFeishuTaskService.completePending(id, "manager", null);
    } catch (Exception ex) {
      log.warn("绩效飞书待办完成异常 record={}", id, ex);
    }
    try {
      performanceFeishuNotifier.notifyRejectToEmployee(r, id, reason);
    } catch (Exception ex) {
      log.warn("绩效飞书通知派发异常 record={}", id, ex);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }

  public Map<String, Object> listMonthPeriodsForCreate(String userId) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_batch_create");
    return listMonthPeriodsFromEvaluation();
  }

  /** 绩效列表/校准等筛选：与创建绩效一致，取「周期与评选」中已维护的月度周期。 */
  public Map<String, Object> listMonthPeriodsForFilter(String userId) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    boolean ok =
        menuPermissionService.isMenuAllowed(userId, "performance_list")
            || menuPermissionService.isMenuAllowed(userId, "admin_performance_calibration");
    if (!ok) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限");
    }
    return listMonthPeriodsFromEvaluation();
  }

  private Map<String, Object> listMonthPeriodsFromEvaluation() {
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT period_key, name FROM evaluation_period WHERE period_type=? ORDER BY sort_order ASC, period_key DESC",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("periodKey", rs.getString("period_key"));
              m.put("name", trimOrEmpty(rs.getString("name")));
              return m;
            },
            "month");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> createBatch(String userId, Map<String, Object> body) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_batch_create");

    Object sc0 = body.get("subjectCode");
    if (sc0 == null || String.valueOf(sc0).trim().isEmpty()) {
      sc0 = body.get("feishuSubjectCode");
    }
    String subjectCode = sc0 == null ? "" : String.valueOf(sc0).trim();
    if (subjectCode.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择飞书主体");
    }
    String subjectId =
        feishuRegistry
            .findSubjectIdByCode(subjectCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));

    List<Map<String, Object>> precResults = new ArrayList<Map<String, Object>>();
    LinkedHashSet<String> uniqueEmployeeIds = new LinkedHashSet<String>();

    if (body.get("employeeIds") instanceof List) {
      for (Object o : (List<?>) body.get("employeeIds")) {
        if (o != null) {
          String eid = String.valueOf(o).trim();
          if (!eid.isEmpty()) {
            uniqueEmployeeIds.add(eid);
          }
        }
      }
    }

    LinkedHashSet<String> uniqueNames = new LinkedHashSet<String>();
    if (body.get("employeeNames") instanceof List) {
      for (Object o : (List<?>) body.get("employeeNames")) {
        if (o != null) {
          String n = String.valueOf(o).trim();
          if (!n.isEmpty()) {
            uniqueNames.add(n);
          }
        }
      }
    }
    for (String employeeName : uniqueNames) {
      List<String> idMatches =
          jdbc.query(
              "SELECT DISTINCT employee_id FROM employee_hierarchy WHERE name = ? AND (feishu_subject_id = ? OR (feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))",
              (rs, rn) -> rs.getString("employee_id"),
              employeeName,
              subjectId,
              subjectId);
      if (idMatches.isEmpty()) {
        precResults.add(resultRow("", employeeName, false, null, "员工不在层级表中，请先同步员工信息"));
        continue;
      }
      if (idMatches.size() > 1) {
        precResults.add(
            resultRow(
                "",
                employeeName,
                false,
                null,
                "存在重名员工「"
                    + employeeName
                    + "」，请改用 employeeIds 传入飞书员工编号，或在通讯录中区分姓名"));
        continue;
      }
      uniqueEmployeeIds.add(idMatches.get(0));
    }

    Object deptNameObj = body.get("departmentName");
    if (deptNameObj != null && !String.valueOf(deptNameObj).isEmpty()) {
      String departmentName = String.valueOf(deptNameObj);
      List<String> deptEmployeeIds =
          jdbc.query(
              "SELECT employee_id FROM employee_hierarchy WHERE department_name = ? AND (feishu_subject_id = ? OR (feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))",
              (rs, rn) -> rs.getString("employee_id"),
              departmentName,
              subjectId,
              subjectId);
      for (String eid : deptEmployeeIds) {
        if (eid != null && !eid.isEmpty()) {
          uniqueEmployeeIds.add(eid);
        }
      }
    }

    if (uniqueEmployeeIds.size() > 100) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "单次最多创建100条绩效记录");
    }
    String period = String.valueOf(body.get("period"));

    Object schemeObj = body.get("scoringSchemeId");
    String scoringSchemeId = schemeObj == null ? "" : String.valueOf(schemeObj).trim();
    if (scoringSchemeId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择评分方案");
    }
    List<Map<String, Object>> schemeRows = jdbc.query(
        "SELECT performance_weight, culture_weight, learning_weight, status FROM scoring_scheme WHERE id = ? LIMIT 1",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("performanceWeight", rs.getBigDecimal("performance_weight").doubleValue());
          m.put("cultureWeight", rs.getBigDecimal("culture_weight").doubleValue());
          m.put("learningWeight", rs.getBigDecimal("learning_weight").doubleValue());
          m.put("status", rs.getString("status"));
          return m;
        },
        scoringSchemeId);
    if (schemeRows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分方案不存在");
    }
    if (!"enabled".equals(schemeRows.get(0).get("status"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该评分方案已停用");
    }
    Map<String, Object> schemeData = schemeRows.get(0);
    double perfW = ((Number) schemeData.get("performanceWeight")).doubleValue();
    double cultureW = ((Number) schemeData.get("cultureWeight")).doubleValue();
    double learningW = ((Number) schemeData.get("learningWeight")).doubleValue();
    String scoringWeightsJson;
    try {
      Map<String, Object> sw = new LinkedHashMap<>();
      sw.put("performance", perfW);
      sw.put("culture", cultureW);
      sw.put("learning", learningW);
      scoringWeightsJson = MAPPER.writeValueAsString(sw);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "评分方案权重序列化失败");
    }
    log.info(
        "[createPerformanceRecord] scoringSchemeId={} weights perf={} culture={} learning={}",
        scoringSchemeId,
        perfW,
        cultureW,
        learningW);

    String cultureTemplateId = null;
    String cultureSnapJson;
    if (cultureW > 0) {
      List<Map<String, Object>> cultRows = CultureDimensionsSupport.defaultList();
      List<Map<String, Object>> cultureTemplateRows =
          jdbc.query(
              "SELECT id, culture_dimensions FROM performance_template WHERE type = 'culture' AND status = 'enabled' ORDER BY _updated_at DESC LIMIT 1",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getString("id"));
                m.put("cultureDimensions", rs.getString("culture_dimensions"));
                return m;
              });
      String rawFromDb = null;
      if (!cultureTemplateRows.isEmpty()) {
        cultureTemplateId = (String) cultureTemplateRows.get(0).get("id");
        rawFromDb = (String) cultureTemplateRows.get(0).get("cultureDimensions");
        cultRows = CultureDimensionsSupport.parseListOrDefault(rawFromDb);
      }
      log.info(
          "[createPerformanceRecord] cultureSource templateRowCount={} cultureTemplateId={} rawDbSnippet={} usedDefaultListFallback={}",
          cultureTemplateRows.size(),
          cultureTemplateId,
          truncateForLog(rawFromDb, 600),
          cultureTemplateRows.isEmpty());
      cultureSnapJson = CultureDimensionsSupport.toJson(cultRows);
      log.info(
          "[createPerformanceRecord] cultureParsed maxScoreSum={} dimCount={} snapJsonSnippet={}",
          cultureMaxScoreSum(cultRows),
          cultRows.size(),
          truncateForLog(cultureSnapJson, 500));
    } else {
      cultureSnapJson = "[]";
    }

    String learningTemplateId = null;
    String learningDimsJson;
    if (learningW > 0) {
      learningDimsJson = "[]";
      List<Map<String, Object>> learningTemplateRows =
          jdbc.query(
              "SELECT id, culture_dimensions, indicators FROM performance_template WHERE type = 'learning' AND status = 'enabled' ORDER BY _updated_at DESC LIMIT 1",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getString("id"));
                m.put("cultureDimensions", rs.getString("culture_dimensions"));
                m.put("indicators", rs.getString("indicators"));
                return m;
              });
      if (!learningTemplateRows.isEmpty()) {
        learningTemplateId = (String) learningTemplateRows.get(0).get("id");
        String rawC = (String) learningTemplateRows.get(0).get("cultureDimensions");
        String rawI = (String) learningTemplateRows.get(0).get("indicators");
        List<Map<String, Object>> learnRows = new ArrayList<Map<String, Object>>();
        if (rawI != null && !rawI.trim().isEmpty() && !"[]".equals(rawI.trim())) {
          try {
            learnRows = MAPPER.readValue(rawI, REVIEW_LIST_TYPE);
          } catch (Exception e) {
            learnRows = new ArrayList<Map<String, Object>>();
          }
        }
        if (learnRows.isEmpty()) {
          learnRows = resolveLearningTemplateRows(rawC, rawI);
          if (!learnRows.isEmpty()) {
            scaleDimensionMaxScoresTo100(learnRows);
            learnRows = CultureDimensionsSupport.withSchemeEffectiveWeights(learnRows, learningW);
            for (Map<String, Object> d : learnRows) {
              d.put("weight", numberVal(d.get("effectiveWeight")));
            }
          }
        }
        if (!learnRows.isEmpty()) {
          learningDimsJson = writeJson(learnRows);
        }
      }
    } else {
      learningDimsJson = "[]";
    }
    log.info(
        "[createPerformanceRecord] learningSnapshot learningW={} learningTemplateId={} learningSnapLen={}",
        learningW,
        learningTemplateId,
        learningDimsJson == null ? 0 : learningDimsJson.length());

    String templateId = body.get("templateId") == null ? null : String.valueOf(body.get("templateId")).trim();
    if (templateId != null && templateId.isEmpty()) templateId = null;

    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>(precResults);

    for (String employeeIdValue : uniqueEmployeeIds) {
      try {
        List<Map<String, Object>> hierarchy =
            jdbc.query(
                "SELECT employee_id, name, manager_id, dotted_manager_id, assessment_rule_id FROM employee_hierarchy WHERE employee_id = ? AND (feishu_subject_id = ? OR (feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default'))) LIMIT 1",
                (rs, rn) -> {
                  Map<String, Object> m = new LinkedHashMap<String, Object>();
                  m.put("employeeId", rs.getString("employee_id"));
                  m.put("name", rs.getString("name"));
                  m.put("managerId", rs.getString("manager_id"));
                  m.put("dottedManagerId", rs.getString("dotted_manager_id"));
                  m.put("assessmentRuleId", rs.getString("assessment_rule_id"));
                  return m;
                },
                employeeIdValue,
                subjectId,
                subjectId);
        if (hierarchy.isEmpty()) {
          results.add(resultRow(employeeIdValue, "", false, null, "员工不在层级表中，请先同步员工信息"));
          continue;
        }
        Map<String, Object> emp = hierarchy.get(0);
        String managerId = emp.get("managerId") == null ? null : String.valueOf(emp.get("managerId"));
        String dottedManagerId =
            emp.get("dottedManagerId") == null ? null : String.valueOf(emp.get("dottedManagerId"));
        if (managerId == null || managerId.isEmpty()) {
          results.add(resultRow(employeeIdValue, (String) emp.get("name"), false, null, "该员工未设置直属上级"));
          continue;
        }
        Object ruleFromEmp = emp.get("assessmentRuleId");
        String assessmentRuleId =
            ruleFromEmp == null ? "" : String.valueOf(ruleFromEmp).trim();
        if (assessmentRuleId.isEmpty()) {
          results.add(
              resultRow(
                  employeeIdValue,
                  (String) emp.get("name"),
                  false,
                  null,
                  "请先在员工管理中为该员工绑定考核规则"));
          continue;
        }
        Integer ruleOk =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_rule WHERE id = ? AND status = 'enabled'",
                Integer.class,
                assessmentRuleId);
        if (ruleOk == null || ruleOk == 0) {
          results.add(
              resultRow(
                  employeeIdValue,
                  (String) emp.get("name"),
                  false,
                  null,
                  "员工绑定的考核规则不存在或已停用，请在员工管理中更换"));
          continue;
        }
        Integer existing =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM performance_record WHERE employee_id=? AND period=? AND deleted_at IS NULL",
                Integer.class,
                employeeIdValue,
                period);
        if (existing != null && existing > 0) {
          results.add(
              resultRow(
                  employeeIdValue,
                  (String) emp.get("name"),
                  false,
                  null,
                  "该员工在 " + period + " 周期已有绩效记录"));
          continue;
        }
        String newId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO performance_record (id, employee_id, period, status, manager_id, dotted_manager_id, template_id, culture_dimensions, learning_dimensions, scoring_scheme_id, scoring_weights, culture_template_id, learning_template_id, assessment_rule_id) VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,CAST(? AS JSON),?,?,?)",
            newId,
            employeeIdValue,
            period,
            "goal_setting",
            managerId,
            dottedManagerId,
            templateId,
            cultureSnapJson,
            learningDimsJson,
            scoringSchemeId,
            scoringWeightsJson,
            cultureTemplateId,
            learningTemplateId,
            assessmentRuleId);
        results.add(resultRow(employeeIdValue, (String) emp.get("name"), true, newId, null));
        log.info(
            "[createPerformanceRecord] inserted recordId={} employeeId={} assessmentRuleId={} culture_template_id={} cultureSnapLen={} learningSnapLen={}",
            newId,
            employeeIdValue,
            assessmentRuleId,
            cultureTemplateId,
            cultureSnapJson == null ? 0 : cultureSnapJson.length(),
            learningDimsJson == null ? 0 : learningDimsJson.length());
        log.info("创建绩效记录: employeeId={}, period={}, id={}", employeeIdValue, period, newId);
        try {
          log.info(
              "绩效批量创建后触发飞书通知调用 employeeId={} recordId={} period={}",
              employeeIdValue,
              newId,
              period);
          performanceFeishuNotifier.notifyRecordCreated(employeeIdValue, newId, period);
        } catch (Exception ex) {
          log.warn("绩效飞书通知派发异常 record={}", newId, ex);
        }
      } catch (Exception e) {
        results.add(
            resultRow(
                employeeIdValue,
                "",
                false,
                null,
                e.getMessage() == null ? "创建失败" : e.getMessage()));
      }
    }

    int successCount = 0;
    for (Map<String, Object> r : results) {
      if (Boolean.TRUE.equals(r.get("success"))) {
        successCount++;
      }
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("results", results);
    out.put("total", results.size());
    out.put("successCount", successCount);
    out.put("failCount", results.size() - successCount);
    return out;
  }

  private static Map<String, Object> resultRow(
      String employeeId, String employeeName, boolean success, String id, String error) {
    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("employeeId", employeeId);
    if (employeeName != null) {
      m.put("employeeName", employeeName);
    }
    m.put("success", success);
    if (id != null) {
      m.put("id", id);
    }
    if (error != null) {
      m.put("error", error);
    }
    return m;
  }

  public Map<String, Object> approveGoal(String userId, String id, Map<String, Object> body) {
    Map<String, Object> r = loadRecord(id);
    boolean isManager = String.valueOf(r.get("managerId")).equals(userId);
    boolean isDottedManager =
        r.get("dottedManagerId") != null && String.valueOf(r.get("dottedManagerId")).equals(userId);
    if (!isManager && !isDottedManager) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权审核目标设定");
    }
    if (!"goal_pending_review".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许审核目标");
    }
    String oldStatus = String.valueOf(r.get("status"));
    boolean approved = Boolean.TRUE.equals(body.get("approved"));
    String newStatus = approved ? "self_review" : "goal_rejected";
    if (approved) {
      jdbc.update(
          "UPDATE performance_record SET status=?, goal_approved_by=?, rejection_reason=NULL WHERE id=?",
          newStatus,
          userId,
          id);
    } else {
      String rejectionReason =
          body.get("rejectionReason") == null ? "目标设定未通过审核" : String.valueOf(body.get("rejectionReason"));
      jdbc.update(
          "UPDATE performance_record SET status=?, goal_approved_by=?, rejection_reason=? WHERE id=?",
          newStatus,
          userId,
          rejectionReason,
          id);
    }
    tryLogPerformanceFlow(
        id,
        oldStatus,
        newStatus,
        approved ? "approve_goal" : "reject_goal",
        userId,
        approved ? null : String.valueOf(body.get("rejectionReason")));
    log.info("绩效 {} 目标审核完成，新状态: {}", id, newStatus);
    try {
      performanceFeishuTaskService.completeAllGoalReview(id);
    } catch (Exception ex) {
      log.warn("绩效飞书待办完成异常 record={}", id, ex);
    }
    try {
      if (approved) {
        performanceFeishuNotifier.notifyApproveGoal(r, id, true, null);
      } else {
        String rr =
            body.get("rejectionReason") == null ? "目标设定未通过审核" : String.valueOf(body.get("rejectionReason"));
        performanceFeishuNotifier.notifyApproveGoal(r, id, false, rr);
      }
    } catch (Exception ex) {
      log.warn("绩效飞书通知派发异常 record={}", id, ex);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> finalReview(String userId, String id, Map<String, Object> body) {
    MenuFlags flags = performanceMenuFlags(userId);
    if (!flags.reviewAdmin) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权执行终审操作");
    }
    Map<String, Object> r = loadRecord(id);
    if (!"final_review".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许终审");
    }
    String oldStatus = String.valueOf(r.get("status"));
    boolean approved = Boolean.TRUE.equals(body.get("approved"));
    String newStatus;
    if (approved) {
      newStatus = "completed";
      jdbc.update(
          "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), rejection_reason=NULL WHERE id=?",
          newStatus,
          userId,
          id);
    } else {
      newStatus =
          body.get("returnToStage") == null ? "self_review" : String.valueOf(body.get("returnToStage"));
      String rejectionReason =
          body.get("rejectionReason") == null ? "终审未通过" : String.valueOf(body.get("rejectionReason"));
      jdbc.update(
          "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), rejection_reason=? WHERE id=?",
          newStatus,
          userId,
          rejectionReason,
          id);
    }
    tryLogPerformanceFlow(
        id,
        oldStatus,
        newStatus,
        approved ? "final_review_approve" : "final_review_reject",
        userId,
        approved ? null : String.valueOf(body.get("rejectionReason")));
    log.info("绩效 {} 终审完成，新状态: {}", id, newStatus);
    try {
      performanceFeishuTaskService.completeAllFinal(id);
    } catch (Exception ex) {
      log.warn("绩效飞书待办完成异常 record={}", id, ex);
    }
    try {
      String rejectionReason =
          approved
              ? null
              : (body.get("rejectionReason") == null ? "终审未通过" : String.valueOf(body.get("rejectionReason")));
      performanceFeishuNotifier.notifyFinalReviewToEmployee(r, id, approved, newStatus, rejectionReason);
    } catch (Exception ex) {
      log.warn("绩效飞书通知派发异常 record={}", id, ex);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> saveGoalIndicators(String userId, String performanceId, Map<String, Object> body) {
    Map<String, Object> r = loadRecord(performanceId);
    if (!String.valueOf(r.get("employeeId")).equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本人可编辑绩效指标");
    }
    String currentStatus = String.valueOf(r.get("status"));
    if (!"goal_setting".equals(currentStatus) && !"goal_rejected".equals(currentStatus)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许编辑绩效指标");
    }
    List<Map<String, Object>> indicators =
        body.get("indicators") instanceof List ? (List<Map<String, Object>>) body.get("indicators") : null;
    if (indicators == null || indicators.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少保留一项绩效指标");
    }
    Set<String> seen = new HashSet<String>();
    for (Map<String, Object> row : indicators) {
      String n = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
      if (n.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标名称不能为空");
      }
      if (!seen.add(n)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标名称不能重复: " + n);
      }
    }
    double schemeP = schemePerformancePctFromRecord(r);
    List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : indicators) {
      normalized.add(new LinkedHashMap<String, Object>(row));
    }
    TemplateService.validateGoalPerformanceIndicators(normalized, schemeP);
    List<Map<String, Object>> existingGoals = parseReviewJson((String) r.get("goalSetting"));
    List<Map<String, Object>> newGoals = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> ind : normalized) {
      String nm = String.valueOf(ind.get("name"));
      String savedCrit = "";
      if (existingGoals != null) {
        for (Map<String, Object> g : existingGoals) {
          if (nm.equals(String.valueOf(g.get("indicatorName")))) {
            Object c = g.get("criteria");
            savedCrit = c == null ? "" : String.valueOf(c);
            break;
          }
        }
      }
      if (savedCrit.isEmpty()) {
        Object c0 = ind.get("criteria");
        savedCrit = c0 == null ? "" : String.valueOf(c0);
      }
      Map<String, Object> gRow = new LinkedHashMap<String, Object>();
      gRow.put("indicatorName", nm);
      Object w = ind.get("weight");
      gRow.put("weight", w instanceof Number ? ((Number) w).doubleValue() : numberVal(w));
      gRow.put("criteria", savedCrit);
      newGoals.add(gRow);
    }
    jdbc.update(
        "UPDATE performance_record SET performance_indicators=CAST(? AS JSON), goal_setting=? WHERE id=?",
        writeJson(normalized),
        writeJson(newGoals),
        performanceId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  public Map<String, Object> selectTemplate(String userId, String performanceId, String templateId) {
    Map<String, Object> r = loadRecord(performanceId);
    if (!String.valueOf(r.get("employeeId")).equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本人可以选择模板");
    }
    String currentStatus = String.valueOf(r.get("status"));
    if (!"goal_setting".equals(currentStatus) && !"goal_rejected".equals(currentStatus)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许选择模板");
    }
    List<Map<String, Object>> tplRows =
        jdbc.query(
            "SELECT status, indicators FROM performance_template WHERE id = ? LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("status", rs.getString("status"));
              m.put("indicators", rs.getString("indicators"));
              return m;
            },
            templateId);
    if (tplRows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    if (!"enabled".equals(tplRows.get(0).get("status"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该模板已停用");
    }
    String indJson = (String) tplRows.get(0).get("indicators");
    if (indJson == null || indJson.trim().isEmpty() || "[]".equals(indJson.trim())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该模板未配置绩效指标");
    }
    List<Map<String, Object>> tplInds;
    try {
      tplInds = MAPPER.readValue(indJson, REVIEW_LIST_TYPE);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板指标数据无效");
    }
    List<Map<String, Object>> freshGoals = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> ind : tplInds) {
      Map<String, Object> gRow = new LinkedHashMap<String, Object>();
      gRow.put("indicatorName", String.valueOf(ind.get("name")));
      Object w = ind.get("weight");
      gRow.put("weight", w instanceof Number ? ((Number) w).doubleValue() : numberVal(w));
      Object c0 = ind.get("criteria");
      gRow.put("criteria", c0 == null ? "" : String.valueOf(c0));
      freshGoals.add(gRow);
    }
    jdbc.update(
        "UPDATE performance_record SET template_id=?, performance_indicators=CAST(? AS JSON), goal_setting=? WHERE id=?",
        templateId,
        writeJson(tplInds),
        writeJson(freshGoals),
        performanceId);
    log.info("绩效 {} 选择模板: {}", performanceId, templateId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", currentStatus);
    return out;
  }

  public Map<String, Object> calibrate(String userId, String id, Map<String, Object> body) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    MenuFlags flags = performanceMenuFlags(userId);
    if (!flags.reviewAdmin) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权执行绩效校准");
    }
    Map<String, Object> r = loadRecord(id);
    if (!"final_review".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许绩效校准");
    }
    String oldStatus = String.valueOf(r.get("status"));
    boolean approved = Boolean.TRUE.equals(body.get("approved"));
    String newStatus;
    if (approved) {
      newStatus = "issued";
      if (body.containsKey("finalScore") && body.get("finalScore") instanceof Number) {
        double finalScore = ((Number) body.get("finalScore")).doubleValue();
        jdbc.update(
            "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), total_score=?, score_grade=?, rejection_reason=NULL WHERE id=?",
            newStatus,
            userId,
            finalScore,
            scoreGradeFromTotal(finalScore),
            id);
      } else {
        jdbc.update(
            "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), rejection_reason=NULL WHERE id=?",
            newStatus,
            userId,
            id);
      }
    } else {
      newStatus =
          body.get("returnToStage") == null ? "self_review" : String.valueOf(body.get("returnToStage"));
      String rejectionReason =
          body.get("rejectionReason") == null ? "校准未通过" : String.valueOf(body.get("rejectionReason"));
      jdbc.update(
          "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), rejection_reason=? WHERE id=?",
          newStatus,
          userId,
          rejectionReason,
          id);
    }
    tryLogPerformanceFlow(
        id,
        oldStatus,
        newStatus,
        approved ? "calibrate_approve" : "calibrate_reject",
        userId,
        approved ? null : String.valueOf(body.get("rejectionReason")));
    log.info("绩效 {} 校准完成，新状态: {}", id, newStatus);
    try {
      performanceFeishuTaskService.completeAllFinal(id);
    } catch (Exception ex) {
      log.warn("绩效飞书待办完成异常 record={}", id, ex);
    }
    try {
      String rejectionReason =
          approved
              ? null
              : (body.get("rejectionReason") == null ? "校准未通过" : String.valueOf(body.get("rejectionReason")));
      performanceFeishuNotifier.notifyCalibrateToEmployee(r, id, approved, newStatus, rejectionReason);
    } catch (Exception ex) {
      log.warn("绩效飞书通知派发异常 record={}", id, ex);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> confirmResult(String userId, String id) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    Map<String, Object> r = loadRecord(id);
    if (!userId.equals(String.valueOf(r.get("employeeId")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本人可以确认绩效结果");
    }
    if (!"issued".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许确认绩效结果");
    }
    jdbc.update("UPDATE performance_record SET status=? WHERE id=?", "completed", id);
    tryLogPerformanceFlow(id, "issued", "completed", "confirm_result", userId, null);
    log.info("绩效 {} 员工确认结果，状态变更为 completed", id);
    try {
      performanceFeishuTaskService.completeConfirmForEmployee(id, userId);
    } catch (Exception ex) {
      log.warn("绩效飞书待办完成异常 record={}", id, ex);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", "completed");
    return out;
  }

  public Map<String, Object> ensureMonthlyPerformanceRecordsForPeriod(String period) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT employee_id, manager_id, dotted_manager_id FROM employee_hierarchy WHERE manager_id IS NOT NULL",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("managerId", rs.getString("manager_id"));
              m.put("dottedManagerId", rs.getString("dotted_manager_id"));
              return m;
            });

    int created = 0;
    int skipped = 0;
    for (Map<String, Object> emp : rows) {
      String employeeIdValue = String.valueOf(emp.get("employeeId"));
      String managerId = emp.get("managerId") == null ? null : String.valueOf(emp.get("managerId"));
      if (managerId == null || managerId.isEmpty()) {
        skipped++;
        continue;
      }
      Integer existing =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM performance_record WHERE employee_id=? AND period=? AND deleted_at IS NULL",
              Integer.class,
              employeeIdValue,
              period);
      if (existing != null && existing > 0) {
        skipped++;
        continue;
      }
      String newId = UUID.randomUUID().toString();
      String dottedManagerId =
          emp.get("dottedManagerId") == null ? null : String.valueOf(emp.get("dottedManagerId"));
      jdbc.update(
          "INSERT INTO performance_record (id, employee_id, period, status, manager_id, dotted_manager_id) VALUES (?,?,?,?,?,?)",
          newId,
          employeeIdValue,
          period,
          "goal_setting",
          managerId,
          dottedManagerId);
      created++;
      try {
        log.info(
            "月度绩效创建后触发飞书通知调用 employeeId={} recordId={} period={}",
            employeeIdValue,
            newId,
            period);
        performanceFeishuNotifier.notifyRecordCreated(employeeIdValue, newId, period);
      } catch (Exception ex) {
        log.warn("绩效飞书通知派发异常 record={}", newId, ex);
      }
    }
    log.info("月度绩效自动创建 period={} created={} skipped={}", period, created, skipped);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("created", created);
    out.put("skipped", skipped);
    return out;
  }
}
