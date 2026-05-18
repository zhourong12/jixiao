package com.jixiao2.server.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.culture.CultureDimensionsSupport;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TemplateService {

  private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final double WEIGHT_SUM_EPS = 0.01;

  private final JdbcTemplate jdbc;

  public TemplateService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
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

  public Map<String, Object> list(int page, int pageSize, String type) {
    String whereClause = (type != null && !type.isEmpty()) ? " WHERE pt.type = ?" : "";
    List<Object> countArgs = new ArrayList<>();
    if (!whereClause.isEmpty()) countArgs.add(type);
    Integer total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM performance_template pt" + whereClause, countArgs.toArray(), Integer.class);
    int offset = (page - 1) * pageSize;
    List<Object> queryArgs = new ArrayList<>();
    if (!whereClause.isEmpty()) queryArgs.add(type);
    queryArgs.add(pageSize);
    queryArgs.add(offset);
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pt.id, pt.name, pt.type, pt.position, pt.indicators, pt.culture_dimensions, pt.status, pt.version, pt._created_at, "
                + "pt.assessment_rule_id, ar.name AS assessment_rule_name "
                + "FROM performance_template pt "
                + "LEFT JOIN assessment_rule ar ON ar.id = pt.assessment_rule_id "
                + whereClause
                + " ORDER BY pt._created_at DESC LIMIT ? OFFSET ?",
            queryArgs.toArray(),
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("type", rs.getString("type"));
              m.put("position", rs.getString("position"));
              String tp = rs.getString("type");
              if ("culture".equals(tp)) {
                m.put(
                    "indicatorCount",
                    CultureDimensionsSupport.parseListOrDefault(rs.getString("culture_dimensions")).size());
              } else {
                m.put("indicatorCount", indicatorCount(rs.getString("indicators")));
              }
              m.put("cultureDimensionCount", cultureDimensionsStoredCount(tp, rs.getString("culture_dimensions")));
              m.put("status", rs.getString("status"));
              m.put("version", rs.getInt("version"));
              m.put("assessmentRuleId", rs.getString("assessment_rule_id"));
              m.put("assessmentRuleName", rs.getString("assessment_rule_name"));
              Timestamp created = rs.getTimestamp("_created_at");
              m.put("createdAt", created == null ? null : created.toInstant().toString());
              return m;
            });
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  public Map<String, Object> getById(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT pt.id, pt.name, pt.type, pt.position, pt.indicators, pt.culture_dimensions, pt.status, pt.version, pt._created_at, pt._updated_at, "
                + "pt.assessment_rule_id, ar.name AS assessment_rule_name "
                + "FROM performance_template pt "
                + "LEFT JOIN assessment_rule ar ON ar.id = pt.assessment_rule_id "
                + "WHERE pt.id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("type", rs.getString("type"));
              m.put("position", rs.getString("position"));
              m.put("indicators", parseIndicators(rs.getString("indicators")));
              m.put(
                  "cultureDimensions",
                  parseCultureDimensionsForTemplateDetail(rs.getString("type"), rs.getString("culture_dimensions")));
              m.put("status", rs.getString("status"));
              m.put("version", rs.getInt("version"));
              m.put("assessmentRuleId", rs.getString("assessment_rule_id"));
              m.put("assessmentRuleName", rs.getString("assessment_rule_name"));
              Timestamp created = rs.getTimestamp("_created_at");
              Timestamp updated = rs.getTimestamp("_updated_at");
              m.put("createdAt", created == null ? null : created.toInstant().toString());
              m.put("updatedAt", updated == null ? null : updated.toInstant().toString());
              return m;
            });
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    return rows.get(0);
  }

  public Map<String, Object> create(String userId, Map<String, Object> body) {
    String type = body.get("type") == null ? "performance" : String.valueOf(body.get("type")).trim();
    if (!"performance".equals(type) && !"culture".equals(type) && !"learning".equals(type)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板类型无效，须为 performance/culture/learning");
    }
    String id = UUID.randomUUID().toString();
    String indicatorsJson;
    String cultureJson = null;
    if ("performance".equals(type)) {
      validateIndicatorWeightsPositive(body.get("indicators"));
      indicatorsJson = toJson(body.get("indicators") == null ? new ArrayList<>() : body.get("indicators"));
    } else if ("culture".equals(type)) {
      List<Map<String, Object>> cultureDims;
      if (body.containsKey("cultureDimensions") && body.get("cultureDimensions") != null) {
        cultureDims = CultureDimensionsSupport.normalizeDimensionsInput(body.get("cultureDimensions"));
        CultureDimensionsSupport.validateTemplateDimensions(cultureDims);
      } else {
        cultureDims = CultureDimensionsSupport.defaultList();
      }
      cultureJson = CultureDimensionsSupport.toJson(cultureDims);
      indicatorsJson = toJson(body.get("indicators") == null ? new ArrayList<>() : body.get("indicators"));
      log.info(
          "[template.create] type=culture templateId={} name={} cultureSnippet={}",
          id,
          body.get("name"),
          truncateForLog(cultureJson, 400));
    } else {
      validateIndicatorWeightsPositive(body.get("indicators"));
      indicatorsJson = toJson(body.get("indicators") == null ? new ArrayList<>() : body.get("indicators"));
      cultureJson = CultureDimensionsSupport.toJson(Collections.emptyList());
    }
    jdbc.update(
        "INSERT INTO performance_template (id, name, type, position, indicators, culture_dimensions, assessment_rule_id, _created_by, _updated_by) VALUES (?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,?,?)",
        id,
        body.get("name"),
        type,
        body.get("position") == null ? "" : body.get("position"),
        indicatorsJson,
        cultureJson,
        null,
        userId,
        userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    return out;
  }

  public Map<String, Boolean> update(String id, Map<String, Object> body) {
    List<Map<String, Object>> existing =
        jdbc.query(
            "SELECT version, type FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("version", rs.getInt("version"));
              m.put("type", rs.getString("type"));
              return m;
            });
    if (existing.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    String type = String.valueOf(existing.get(0).get("type"));
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    if (body.containsKey("name")) {
      sets.add("name = ?");
      args.add(body.get("name"));
    }
    if (body.containsKey("position")) {
      sets.add("position = ?");
      args.add(body.get("position"));
    }
    if (body.containsKey("indicators")) {
      if ("performance".equals(type) || "learning".equals(type)) {
        validateIndicatorWeightsPositive(body.get("indicators"));
      }
      sets.add("indicators = CAST(? AS JSON)");
      args.add(toJson(body.get("indicators")));
    }
    if (body.containsKey("cultureDimensions")) {
      List<Map<String, Object>> cultureDims =
          CultureDimensionsSupport.normalizeDimensionsInput(body.get("cultureDimensions"));
      CultureDimensionsSupport.validateTemplateDimensions(cultureDims);
      sets.add("culture_dimensions = CAST(? AS JSON)");
      args.add(CultureDimensionsSupport.toJson(cultureDims));
      log.info(
          "[template.update] id={} type={} cultureDimensionsSnippet={}",
          id,
          type,
          truncateForLog(CultureDimensionsSupport.toJson(cultureDims), 400));
    }
    if (body.containsKey("indicators") || body.containsKey("cultureDimensions")) {
      sets.add("version = ?");
      args.add(((Number) existing.get(0).get("version")).intValue() + 1);
    }
    if (!sets.isEmpty()) {
      args.add(id);
      jdbc.update(
          "UPDATE performance_template SET " + String.join(", ", sets) + " WHERE id = ?",
          args.toArray());
    }
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  public Map<String, Object> toggleStatus(String id) {
    List<Map<String, String>> rows =
        jdbc.query(
            "SELECT status, type FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, String> m = new LinkedHashMap<>();
              m.put("status", rs.getString("status"));
              m.put("type", rs.getString("type"));
              return m;
            });
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    String current = rows.get(0).get("status");
    String type = rows.get(0).get("type");
    String next = "enabled".equals(current) ? "disabled" : "enabled";
    if ("enabled".equals(next) && ("culture".equals(type) || "learning".equals(type))) {
      jdbc.update("UPDATE performance_template SET status = 'disabled' WHERE type = ? AND id != ?", type, id);
    }
    jdbc.update("UPDATE performance_template SET status = ? WHERE id = ?", next, id);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    out.put("newStatus", next);
    return out;
  }

  public Map<String, Object> copy(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT name, type, position, indicators, culture_dimensions FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("name", rs.getString("name"));
              m.put("type", rs.getString("type"));
              m.put("position", rs.getString("position"));
              m.put("indicators", rs.getString("indicators"));
              m.put("culture_dimensions", rs.getString("culture_dimensions"));
              return m;
            });
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    String newId = UUID.randomUUID().toString();
    Map<String, Object> src = rows.get(0);
    String cultureSnap =
        src.get("culture_dimensions") == null || String.valueOf(src.get("culture_dimensions")).trim().isEmpty()
            ? null
            : String.valueOf(src.get("culture_dimensions"));
    jdbc.update(
        "INSERT INTO performance_template (id, name, type, position, indicators, culture_dimensions, assessment_rule_id, status, version) VALUES (?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,?,1)",
        newId,
        String.valueOf(src.get("name")) + " (副本)",
        src.get("type"),
        src.get("position"),
        src.get("indicators"),
        cultureSnap,
        null,
        "disabled");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("newTemplateId", newId);
    return out;
  }

  public Map<String, Boolean> delete(String id) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_template WHERE id = ?", Integer.class, id);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    jdbc.update("DELETE FROM performance_template WHERE id = ?", id);
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  /** 目标设定阶段写入 {@code performance_record.performance_indicators} 时的权重校验。 */
  public static void validateGoalPerformanceIndicators(Object indicatorsObj) {
    validateGoalPerformanceIndicators(indicatorsObj, 100.0);
  }

  /**
   * 各绩效指标 {@code weight} 之和须等于 {@code schemePerformancePct}（与 {@link
   * PerformanceService#schemePerformancePctFromRecord} 一致：有方案时为方案「绩效」占比，否则为 100）。
   */
  public static void validateGoalPerformanceIndicators(Object indicatorsObj, double schemePerformancePct) {
    double sum = sumIndicatorWeights(indicatorsObj);
    if (schemePerformancePct > WEIGHT_SUM_EPS) {
      if (Math.abs(sum - schemePerformancePct) > WEIGHT_SUM_EPS) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            String.format(
                "绩效指标权重合计须等于评分方案中的绩效占比（须为 %.0f%%，当前合计 %.2f%%）",
                schemePerformancePct, sum));
      }
      return;
    }
    validateIndicatorWeightsPositive(indicatorsObj);
  }

  /**
   * @deprecated 请使用 {@link #validateGoalPerformanceIndicators(Object, double)}，传入与落库前相同的方案绩效占比。
   */
  @Deprecated
  public static void validateGoalPerformanceIndicators(Object indicatorsObj, Map<String, Object> scoringWeights) {
    double expected = 100.0;
    if (scoringWeights != null && scoringWeights.get("performance") != null) {
      double p = weightNumber(scoringWeights.get("performance"));
      if (p > WEIGHT_SUM_EPS) {
        expected = p;
      }
    }
    validateGoalPerformanceIndicators(indicatorsObj, expected);
  }

  /**
   * 提交目标时：校验 goal_setting 中绩效权重之和、记录快照上文化满分之和、学习权重之和分别与评分方案 P/C/L 一致。
   */
  public static void validateGoalSubmitScoringAlignment(
      Map<String, Object> scoringWeights,
      List<Map<String, Object>> goalSettingContent,
      List<Map<String, Object>> cultureDims,
      List<Map<String, Object>> learningDims) {
    if (scoringWeights == null || scoringWeights.isEmpty()) {
      return;
    }
    double pPct = weightNumber(scoringWeights.get("performance"));
    double cPct = weightNumber(scoringWeights.get("culture"));
    double lPct = weightNumber(scoringWeights.get("learning"));
    if (goalSettingContent != null && !goalSettingContent.isEmpty() && pPct > WEIGHT_SUM_EPS) {
      double sumP = 0;
      for (Map<String, Object> row : goalSettingContent) {
        sumP += weightNumber(row.get("weight"));
      }
      if (Math.abs(sumP - pPct) > WEIGHT_SUM_EPS) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            String.format(
                "绩效目标权重合计须等于评分方案中的绩效占比（须为 %.0f%%，当前合计 %.2f%%）",
                pPct, sumP));
      }
    }
    if (cultureDims != null && !cultureDims.isEmpty() && cPct > WEIGHT_SUM_EPS) {
      double sumC = 0;
      for (Map<String, Object> d : cultureDims) {
        sumC += weightNumber(d.get("maxScore"));
      }
      if (sumC <= WEIGHT_SUM_EPS) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            String.format("文化价值观各维满分合计须大于 0（当前合计 %.2f）", sumC));
      }
    }
    if (learningDims != null && !learningDims.isEmpty() && lPct > WEIGHT_SUM_EPS) {
      double sumMax = 0;
      double sumW = 0;
      for (Map<String, Object> d : learningDims) {
        sumMax += weightNumber(d.get("maxScore"));
        sumW += weightNumber(d.get("weight"));
      }
      if (sumMax <= WEIGHT_SUM_EPS && sumW <= WEIGHT_SUM_EPS) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            String.format(
                "学习与成长快照：各维满分或权重合计须大于 0（当前满分合计 %.2f，权重合计 %.2f）",
                sumMax, sumW));
      }
    }
  }

  private static double weightNumber(Object o) {
    if (o == null) {
      return 0;
    }
    if (o instanceof Number) {
      return ((Number) o).doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(o).trim());
    } catch (Exception e) {
      return 0;
    }
  }

  private static void validateIndicatorWeightsPositive(Object indicatorsObj) {
    double sum = sumIndicatorWeights(indicatorsObj);
    if (sum <= WEIGHT_SUM_EPS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指标权重合计须大于 0");
    }
  }

  /**
   * @deprecated 不再需要动态折算——模板权重即最终权重。保留方法供旧代码兼容参考。
   */
  @Deprecated
  public static List<Map<String, Object>> performanceIndicatorsWithSchemeApplied(
      List<Map<String, Object>> templateRows, double schemePerformancePct) {
    if (templateRows == null || templateRows.isEmpty()) {
      return new ArrayList<Map<String, Object>>();
    }
    double sumT = 0;
    for (Map<String, Object> ind : templateRows) {
      sumT += weightNumber(ind.get("weight"));
    }
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> src : templateRows) {
      Map<String, Object> row = new LinkedHashMap<String, Object>(src);
      double tw = weightNumber(row.get("weight"));
      row.put("templateWeight", tw);
      double eff =
          schemePerformancePct > WEIGHT_SUM_EPS && sumT > WEIGHT_SUM_EPS
              ? schemePerformancePct * tw / sumT
              : tw;
      row.put("weight", eff);
      out.add(row);
    }
    return out;
  }

  private static double sumIndicatorWeights(Object indicatorsObj) {
    if (indicatorsObj == null) {
      return 0;
    }
    try {
      JsonNode arr = MAPPER.valueToTree(indicatorsObj);
      if (!arr.isArray()) {
        return 0;
      }
      double s = 0;
      for (JsonNode n : arr) {
        JsonNode w = n.get("weight");
        if (w == null || w.isNull()) {
          continue;
        }
        if (w.isNumber()) {
          s += w.asDouble();
        } else if (w.isTextual()) {
          try {
            s += Double.parseDouble(w.asText().trim());
          } catch (NumberFormatException ignored) {
            // skip non-numeric
          }
        }
      }
      return s;
    } catch (Exception e) {
      return 0;
    }
  }

  private static int cultureDimensionsStoredCount(String type, String json) {
    if ("learning".equals(type)) {
      try {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
          return 0;
        }
        JsonNode n = MAPPER.readTree(json);
        return n.isArray() ? n.size() : 0;
      } catch (Exception e) {
        return 0;
      }
    }
    return CultureDimensionsSupport.parseListOrDefault(json).size();
  }

  private static List<Map<String, Object>> parseCultureDimensionsForTemplateDetail(String type, String json) {
    if ("learning".equals(type)) {
      try {
        if (json == null || json.trim().isEmpty()) {
          return new ArrayList<Map<String, Object>>();
        }
        List<Map<String, Object>> list = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        return list != null ? list : new ArrayList<Map<String, Object>>();
      } catch (Exception e) {
        return new ArrayList<Map<String, Object>>();
      }
    }
    return CultureDimensionsSupport.parseListOrDefault(json);
  }

  private static int indicatorCount(String json) {
    try {
      JsonNode n = MAPPER.readTree(json);
      return n.isArray() ? n.size() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private static Object parseIndicators(String json) {
    try {
      return MAPPER.readValue(json, Object.class);
    } catch (Exception e) {
      return new ArrayList<Object>();
    }
  }

  private static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      return "[]";
    }
  }
}
