package com.jixiao2.server.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 绩效流程节点默认/解析截止时间（基于考核周期 yyyy-MM）。 */
@Service
public class PerformanceNodeDeadlineService {

  public static final String KEY_GOAL = "goal";
  public static final String KEY_PLAN = "plan_execution";
  public static final String KEY_SCORING = "scoring";
  public static final String KEY_FINAL = "final";
  public static final String KEY_CONFIRM = "confirm";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

  public Map<String, String> defaultDeadlinesForPeriod(String period) {
    YearMonth ym = parsePeriod(period);
    YearMonth next = ym.plusMonths(1);
    Map<String, String> m = new LinkedHashMap<String, String>();
    m.put(KEY_GOAL, ym.atDay(10).format(ISO));
    m.put(KEY_PLAN, ym.atDay(10).format(ISO));
    m.put(KEY_SCORING, next.atDay(6).format(ISO));
    m.put(KEY_FINAL, next.atDay(8).format(ISO));
    m.put(KEY_CONFIRM, next.atDay(9).format(ISO));
    return m;
  }

  public Map<String, String> mergeDeadlines(String period, Map<String, Object> overrides) {
    Map<String, String> base = defaultDeadlinesForPeriod(period);
    if (overrides == null || overrides.isEmpty()) {
      return base;
    }
    for (Map.Entry<String, Object> e : overrides.entrySet()) {
      if (e.getKey() == null || e.getValue() == null) {
        continue;
      }
      String v = String.valueOf(e.getValue()).trim();
      if (!v.isEmpty()) {
        base.put(e.getKey().trim(), v);
      }
    }
    // 创建绩效时仅配置 goal 等四项；计划执行中与目标阶段共用 goal 截止日
    String goal = base.get(KEY_GOAL);
    if (goal != null && !goal.isEmpty()) {
      base.put(KEY_PLAN, goal);
    }
    return base;
  }

  public String writeJson(Map<String, String> deadlines) {
    try {
      return MAPPER.writeValueAsString(deadlines);
    } catch (Exception e) {
      return "{}";
    }
  }

  public Map<String, String> parseJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      return new LinkedHashMap<String, String>();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<String, String>();
    }
  }

  public LocalDate deadlineDate(Map<String, String> deadlines, String key) {
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

  public boolean isPastDeadline(LocalDate deadline) {
    if (deadline == null) {
      return false;
    }
    return LocalDate.now().isAfter(deadline);
  }

  public static YearMonth parsePeriod(String period) {
    if (period == null || period.trim().isEmpty()) {
      return YearMonth.now();
    }
    try {
      return YearMonth.parse(period.trim());
    } catch (Exception e) {
      return YearMonth.now();
    }
  }
}
