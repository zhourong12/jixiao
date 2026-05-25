package com.jixiao2.server.feishu;

import com.jixiao2.server.home.HomeService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 按站内待办数同步飞书工作台应用角标。 */
@Service
public class FeishuBadgeSyncService {

  private static final Logger log = LoggerFactory.getLogger(FeishuBadgeSyncService.class);

  private final JdbcTemplate jdbc;
  private final HomeService homeService;
  private final FeishuAppBadgeService appBadgeService;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "feishu-badge-sync-" + n.incrementAndGet());
              t.setDaemon(true);
              return t;
            }
          });

  public FeishuBadgeSyncService(
      JdbcTemplate jdbc, HomeService homeService, FeishuAppBadgeService appBadgeService) {
    this.jdbc = jdbc;
    this.homeService = homeService;
    this.appBadgeService = appBadgeService;
  }

  public void scheduleSyncForEmployeeIds(String... employeeIds) {
    scheduleSyncWithTrigger("async", employeeIds);
  }

  public void scheduleSyncWithTrigger(String trigger, String... employeeIds) {
    if (!appBadgeService.isAppBadgeEnabled()) {
      return;
    }
    if (employeeIds == null || employeeIds.length == 0) {
      return;
    }
    Set<String> ids = new LinkedHashSet<String>();
    for (String id : employeeIds) {
      if (id != null && !id.trim().isEmpty()) {
        ids.add(id.trim());
      }
    }
    if (ids.isEmpty()) {
      return;
    }
    final String t = trigger == null || trigger.trim().isEmpty() ? "async" : trigger.trim();
    log.info("飞书应用角标已排队异步同步 trigger={} employeeIds={}", t, ids);
    executor.execute(
        () -> {
          for (String id : ids) {
            try {
              syncForEmployeeId(id, t);
            } catch (Exception e) {
              log.warn("飞书应用角标异步同步失败 trigger={} employeeId={}", t, id, e);
            }
          }
        });
  }

  public boolean syncForEmployeeId(String employeeId) {
    return syncForEmployeeId(employeeId, "direct");
  }

  public boolean syncForEmployeeId(String employeeId, String trigger) {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      return false;
    }
    if (!appBadgeService.isAppBadgeEnabled()) {
      log.info("飞书应用角标跳过 trigger={} employeeId={} reason=开关已关闭", trigger, employeeId.trim());
      return false;
    }
    String eid = employeeId.trim();
    String t = trigger == null || trigger.trim().isEmpty() ? "direct" : trigger.trim();
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT eh.feishu_subject_id, eh.feishu_open_id, eh.name, s.code AS subject_code "
                + "FROM employee_hierarchy eh "
                + "LEFT JOIN feishu_subject s ON s.id = eh.feishu_subject_id "
                + "WHERE eh.employee_id = ? LIMIT 1",
            new Object[] {eid},
            (rs, rn) -> {
              java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
              m.put("feishuSubjectId", rs.getString("feishu_subject_id"));
              m.put("feishuOpenId", rs.getString("feishu_open_id"));
              m.put("name", rs.getString("name"));
              m.put("subjectCode", rs.getString("subject_code"));
              return m;
            });
    if (rows.isEmpty()) {
      log.info("飞书应用角标跳过 trigger={} employeeId={} reason=员工不存在", t, eid);
      return false;
    }
    Map<String, Object> row = rows.get(0);
    String subjectId =
        row.get("feishuSubjectId") == null ? null : String.valueOf(row.get("feishuSubjectId"));
    String subjectCode =
        row.get("subjectCode") == null ? null : String.valueOf(row.get("subjectCode"));
    String openId = row.get("feishuOpenId") == null ? null : String.valueOf(row.get("feishuOpenId"));
    String name = row.get("name") == null ? null : String.valueOf(row.get("name"));
    if (subjectId == null || subjectId.trim().isEmpty()) {
      log.info(
          "飞书应用角标跳过 trigger={} employeeId={} name={} reason=未绑定飞书主体",
          t,
          eid,
          name);
      return false;
    }
    String receive = openId != null && !openId.trim().isEmpty() ? openId.trim() : eid;
    if (!receive.startsWith("ou_") && !receive.startsWith("on_")) {
      log.info(
          "飞书应用角标跳过 trigger={} employeeId={} name={} subjectCode={} reason=无有效 feishu_open_id",
          t,
          eid,
          name,
          subjectCode);
      return false;
    }
    int count = homeService.countTodos(eid, null, null);
    log.info(
        "飞书应用角标同步开始 trigger={} employeeId={} name={} subjectCode={} subjectId={} openId={} todoCount={}",
        t,
        eid,
        name,
        subjectCode,
        subjectId.trim(),
        receive,
        count);
    boolean ok = appBadgeService.setWebAppBadge(subjectId, receive, count);
    log.info(
        "飞书应用角标同步结束 trigger={} employeeId={} subjectCode={} openId={} todoCount={} success={}",
        t,
        eid,
        subjectCode,
        receive,
        count,
        ok);
    return ok;
  }

  public void scheduleSyncForPerformanceRecord(String recordId) {
    if (!appBadgeService.isAppBadgeEnabled()) {
      return;
    }
    if (recordId == null || recordId.trim().isEmpty()) {
      return;
    }
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT employee_id, manager_id, dotted_manager_id FROM performance_record WHERE id = ? LIMIT 1",
            new Object[] {recordId.trim()},
            (rs, rn) -> {
              java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("managerId", rs.getString("manager_id"));
              m.put("dottedManagerId", rs.getString("dotted_manager_id"));
              return m;
            });
    if (rows.isEmpty()) {
      return;
    }
    Map<String, Object> r = rows.get(0);
    Set<String> ids = new LinkedHashSet<String>();
    addId(ids, r.get("employeeId"));
    addId(ids, r.get("managerId"));
    addId(ids, r.get("dottedManagerId"));
    log.info("飞书应用角标因绩效变更触发 recordId={} employeeIds={}", recordId.trim(), ids);
    scheduleSyncWithTrigger("performance_record:" + recordId.trim(), ids.toArray(new String[0]));
  }

  private static void addId(Set<String> ids, Object v) {
    if (v == null) {
      return;
    }
    String s = String.valueOf(v).trim();
    if (!s.isEmpty()) {
      ids.add(s);
    }
  }
}
