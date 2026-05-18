package com.jixiao2.server.culture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 文化价值观维度：与绩效模板配置一致；绩效记录上存快照 JSON。 */
public final class CultureDimensionsSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<List<Map<String, Object>>>() {};

  private static final double EPS = 0.02;

  public static final String DEFAULT_JSON =
      "[{\"name\":\"利他\",\"maxScore\":7,\"description\":\"1. 客户为先，不断完善产品与服务，为用户创造价值。\\n2. 分享与互助，不断提升能力与效率。\\n3. 让和你合作的人成功，相互成就。\",\"criteria\":\"优秀(7分)，合格(4-6分)，不合格(0分)\"},"
          + "{\"name\":\"本分\",\"maxScore\":7,\"description\":\"1. 以诚相待，高效沟通。\\n2. 实事求是，说到做到。\\n3. 敢于质疑，敢于挑战，抓住事物本质。\\n4. 坚持自我批判，不断超越自我。\",\"criteria\":\"优秀(7分)，合格(4-6分)，不合格(0分)\"},"
          + "{\"name\":\"结果导向\",\"maxScore\":6,\"description\":\"1. 以结果来驱动行为，对结果负责。\\n2. 不找借口，突破客观条件限制，整合资源，不惜一切达成结果。\\n3. 关注团队结果，团队成功才有个人价值。\",\"criteria\":\"优秀(6分)，合格(3-5分)，不合格(0分)\"}]";

  private CultureDimensionsSupport() {}

  public static List<Map<String, Object>> defaultList() {
    return parseListOrDefault(null);
  }

  public static List<Map<String, Object>> parseListOrDefault(String json) {
    if (json == null || json.trim().isEmpty()) {
      return copyDefault();
    }
    try {
      List<Map<String, Object>> list = MAPPER.readValue(json, LIST_TYPE);
      if (list == null || list.isEmpty()) {
        return copyDefault();
      }
      return list;
    } catch (Exception e) {
      return copyDefault();
    }
  }

  private static List<Map<String, Object>> copyDefault() {
    try {
      return MAPPER.readValue(DEFAULT_JSON, LIST_TYPE);
    } catch (Exception e) {
      return new ArrayList<Map<String, Object>>();
    }
  }

  public static String toJson(List<Map<String, Object>> list) {
    try {
      return MAPPER.writeValueAsString(list == null ? Collections.emptyList() : list);
    } catch (Exception e) {
      return "[]";
    }
  }

  /**
   * 在维度快照上写入 templateWeight（模板内满分占比）与 effectiveWeight（折算到总表中的百分点，和为 schemePct）。
   */
  public static List<Map<String, Object>> withSchemeEffectiveWeights(List<Map<String, Object>> dims, double schemePct) {
    if (dims == null || dims.isEmpty() || schemePct <= EPS) {
      return dims == null ? new ArrayList<Map<String, Object>>() : dims;
    }
    double sumT = 0;
    for (Map<String, Object> d : dims) {
      Object ms = d.get("maxScore");
      double m = ms instanceof Number ? ((Number) ms).doubleValue() : Double.parseDouble(String.valueOf(ms));
      if (Double.isFinite(m) && m > 0) {
        sumT += m;
      }
    }
    if (sumT <= EPS) {
      return dims;
    }
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> src : dims) {
      Map<String, Object> row = new LinkedHashMap<String, Object>(src);
      Object ms = row.get("maxScore");
      double m = ms instanceof Number ? ((Number) ms).doubleValue() : Double.parseDouble(String.valueOf(ms));
      row.put("templateWeight", m);
      row.put("effectiveWeight", schemePct * m / sumT);
      out.add(row);
    }
    return out;
  }

  public static void validateTemplateDimensions(Object raw) {
    List<Map<String, Object>> dims = normalizeDimensionsInput(raw);
    if (dims.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观维度至少配置一项");
    }
    java.util.Set<String> names = new java.util.HashSet<String>();
    for (Map<String, Object> row : dims) {
      String name = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
      if (name.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观维度名称不能为空");
      }
      if (!names.add(name)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观维度名称不能重复");
      }
      Object ms = row.get("maxScore");
      double maxScore = ms instanceof Number ? ((Number) ms).doubleValue() : Double.parseDouble(String.valueOf(ms));
      if (!Double.isFinite(maxScore) || maxScore <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观各维「满分」须为正数");
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> normalizeDimensionsInput(Object raw) {
    if (raw == null) {
      return new ArrayList<Map<String, Object>>();
    }
    if (raw instanceof List) {
      List<?> list = (List<?>) raw;
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      for (Object o : list) {
        if (!(o instanceof Map)) {
          continue;
        }
        Map<String, Object> m = (Map<String, Object>) o;
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", m.get("name") == null ? "" : String.valueOf(m.get("name")).trim());
        row.put("maxScore", m.get("maxScore"));
        row.put("description", m.get("description") == null ? "" : String.valueOf(m.get("description")));
        row.put("criteria", m.get("criteria") == null ? "" : String.valueOf(m.get("criteria")));
        out.add(row);
      }
      return out;
    }
    return new ArrayList<Map<String, Object>>();
  }

  public static List<String> dimensionNames(List<Map<String, Object>> dims) {
    List<String> names = new ArrayList<String>();
    for (Map<String, Object> d : dims) {
      names.add(String.valueOf(d.get("name")));
    }
    return names;
  }

  public static double maxScoreForName(List<Map<String, Object>> dims, String name) {
    for (Map<String, Object> d : dims) {
      if (String.valueOf(d.get("name")).equals(name)) {
        Object ms = d.get("maxScore");
        return ms instanceof Number ? ((Number) ms).doubleValue() : Double.parseDouble(String.valueOf(ms));
      }
    }
    return 0;
  }

  public static boolean isCultureReviewComplete(List<Map<String, Object>> review, List<Map<String, Object>> dims) {
    if (dims == null || dims.isEmpty()) {
      return true;
    }
    if (review == null || review.isEmpty()) {
      return false;
    }
    Map<String, Map<String, Object>> byName = new LinkedHashMap<String, Map<String, Object>>();
    for (Map<String, Object> item : review) {
      byName.put(String.valueOf(item.get("name")), item);
    }
    for (Map<String, Object> dim : dims) {
      String n = String.valueOf(dim.get("name"));
      Map<String, Object> item = byName.get(n);
      if (item == null) {
        return false;
      }
      Object s = item.get("score");
      if (!(s instanceof Number) || Double.isNaN(((Number) s).doubleValue())) {
        return false;
      }
      double score = ((Number) s).doubleValue();
      double cap = maxScoreForName(dims, n);
      if (score < 0 || score > cap + 1e-6) {
        return false;
      }
    }
    return true;
  }

  public static void assertCultureScoresValid(List<Map<String, Object>> review, List<Map<String, Object>> dims) {
    if (review == null || review.isEmpty()) {
      return;
    }
    for (Map<String, Object> item : review) {
      String n = String.valueOf(item.get("name"));
      double cap = maxScoreForName(dims, n);
      if (cap <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观评分包含未知维度: " + n);
      }
      Object s = item.get("score");
      if (!(s instanceof Number) || Double.isNaN(((Number) s).doubleValue())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观评分须为数字");
      }
      double score = ((Number) s).doubleValue();
      if (score < 0 || score > cap + 1e-6) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文化价值观「" + n + "」评分须在 0～" + (int) cap + " 之间");
      }
    }
  }
}
