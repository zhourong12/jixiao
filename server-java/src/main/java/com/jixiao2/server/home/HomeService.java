package com.jixiao2.server.home;

import com.jixiao2.server.menu.MenuPermissionService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

  private static final List<String> ACTIVE_STATUSES =
      java.util.Arrays.asList(
          "template_selection",
          "goal_setting",
          "goal_pending_review",
          "goal_rejected",
          "self_review",
          "manager_review",
          "dual_manager_review",
          "dotted_manager_review",
          "final_review");

  private final JdbcTemplate jdbc;
  private final MenuPermissionService menuPermissionService;

  public HomeService(JdbcTemplate jdbc, MenuPermissionService menuPermissionService) {
    this.jdbc = jdbc;
    this.menuPermissionService = menuPermissionService;
  }

  private static int[] resolveYearMonth(Integer year, Integer month) {
    LocalDate now = LocalDate.now();
    int y = year != null ? year : now.getYear();
    int m = month != null ? Math.min(12, Math.max(1, month)) : now.getMonthValue();
    return new int[] {y, m};
  }

  private static Timestamp monthStart(int year, int month) {
    return Timestamp.valueOf(YearMonth.of(year, month).atDay(1).atStartOfDay());
  }

  private static Timestamp monthEndExclusive(int year, int month) {
    return Timestamp.valueOf(YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay());
  }

  private boolean isAdminRole(String role) {
    return "super_admin".equals(role) || "admin".equals(role);
  }

  public Map<String, Object> getTodos(String userId, Integer year, Integer month) {
    int[] ym = resolveYearMonth(year, month);
    Timestamp start = monthStart(ym[0], ym[1]);
    Timestamp endEx = monthEndExclusive(ym[0], ym[1]);
    String role = menuPermissionService.getUserRole(userId);
    boolean admin = isAdminRole(role);

    List<String> whereParts = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();

    whereParts.add(
        "((status=? AND employee_id=?) OR (status=? AND employee_id=?) OR (status=? AND employee_id=?) OR "
            + "(status=? AND (manager_id=? OR dotted_manager_id=?)) OR "
            + "(status=? AND employee_id=?) OR (status=? AND manager_id=?) OR "
            + "(status=? AND (manager_id=? OR dotted_manager_id=?)) OR "
            + "(status=? AND dotted_manager_id=?))");
    args.add("template_selection");
    args.add(userId);
    args.add("goal_setting");
    args.add(userId);
    args.add("goal_rejected");
    args.add(userId);
    args.add("goal_pending_review");
    args.add(userId);
    args.add(userId);
    args.add("self_review");
    args.add(userId);
    args.add("manager_review");
    args.add(userId);
    args.add("dual_manager_review");
    args.add(userId);
    args.add(userId);
    args.add("dotted_manager_review");
    args.add(userId);
    if (admin) {
      whereParts.add("(status=? AND 1=1)");
      args.add("final_review");
    }

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT id, period, status FROM performance_record WHERE (");
    sql.append(String.join(" OR ", whereParts));
    sql.append(") AND _updated_at >= ? AND _updated_at < ? ORDER BY _updated_at DESC");
    args.add(start);
    args.add(endEx);

    List<Map<String, Object>> rows =
        jdbc.query(
            sql.toString(),
            args.toArray(),
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("period", rs.getString("period"));
              m.put("status", rs.getString("status"));
              return m;
            });

    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> r : rows) {
      String period = (String) r.get("period");
      String st = (String) r.get("status");
      String type = mapTodoType(st);
      String title = buildTitle(period, st);
      Map<String, Object> it = new LinkedHashMap<String, Object>();
      it.put("id", r.get("id"));
      it.put("period", period);
      it.put("type", type);
      it.put("title", title);
      items.add(it);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  private static String mapTodoType(String status) {
    if ("template_selection".equals(status)) {
      return "template_selection";
    }
    if ("goal_setting".equals(status)) {
      return "goal_setting";
    }
    if ("goal_rejected".equals(status)) {
      return "goal_rejected";
    }
    if ("goal_pending_review".equals(status)) {
      return "goal_pending_review";
    }
    if ("self_review".equals(status)) {
      return "self_review";
    }
    if ("manager_review".equals(status)) {
      return "manager_review";
    }
    if ("dual_manager_review".equals(status)) {
      return "dual_manager_review";
    }
    if ("dotted_manager_review".equals(status)) {
      return "dotted_manager_review";
    }
    if ("final_review".equals(status)) {
      return "final_review";
    }
    return "self_review";
  }

  private static String buildTitle(String period, String status) {
    if ("template_selection".equals(status)) {
      return period + " 待选择绩效模板";
    }
    if ("goal_setting".equals(status)) {
      return period + " 目标设定";
    }
    if ("goal_rejected".equals(status)) {
      return period + " 目标被驳回，请修改";
    }
    if ("goal_pending_review".equals(status)) {
      return period + " 待审核目标";
    }
    if ("self_review".equals(status)) {
      return period + " 绩效自评";
    }
    if ("manager_review".equals(status)) {
      return period + " 直属上级评分";
    }
    if ("dual_manager_review".equals(status)) {
      return period + " 上级与虚线上级评分";
    }
    if ("dotted_manager_review".equals(status)) {
      return period + " 虚线上级评分";
    }
    if ("final_review".equals(status)) {
      return period + " 待终审/校准";
    }
    return period + " 待办";
  }

  public Map<String, Object> getOverview(String userId, Integer year, Integer month) {
    int[] ym = resolveYearMonth(year, month);
    Timestamp start = monthStart(ym[0], ym[1]);
    Timestamp endEx = monthEndExclusive(ym[0], ym[1]);
    String role = menuPermissionService.getUserRole(userId);
    boolean admin = isAdminRole(role);

    StringBuilder base = new StringBuilder("_updated_at >= ? AND _updated_at < ?");
    List<Object> args = new ArrayList<Object>();
    args.add(start);
    args.add(endEx);
    if (!admin) {
      base.append(" AND (employee_id=? OR manager_id=? OR dotted_manager_id=?)");
      args.add(userId);
      args.add(userId);
      args.add(userId);
    }

    int total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE " + base, Integer.class, args.toArray());

    String inActive = buildInPlaceholders(ACTIVE_STATUSES.size());
    List<Object> pendingArgs = new ArrayList<Object>(args);
    pendingArgs.addAll(ACTIVE_STATUSES);
    int pending =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE "
                + base
                + " AND status IN ("
                + inActive
                + ")",
            Integer.class,
            pendingArgs.toArray());

    List<Object> compArgs = new ArrayList<Object>(args);
    int completed =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE " + base + " AND status=?",
            Integer.class,
            concat(compArgs, "completed"));

    List<Object> rejArgs = new ArrayList<Object>(args);
    int rejected =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE " + base + " AND status=?",
            Integer.class,
            concat(rejArgs, "goal_rejected"));

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("total", total);
    out.put("pending", pending);
    out.put("completed", completed);
    out.put("rejected", rejected);
    out.put("year", ym[0]);
    out.put("month", ym[1]);
    return out;
  }

  private static Object[] concat(List<Object> a, String extra) {
    List<Object> n = new ArrayList<Object>(a);
    n.add(extra);
    return n.toArray();
  }

  private static String buildInPlaceholders(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('?');
    }
    return sb.toString();
  }

  public Map<String, Object> getActionCounts(String userId, Integer year, Integer month) {
    int[] ym = resolveYearMonth(year, month);
    Timestamp start = monthStart(ym[0], ym[1]);
    Timestamp endEx = monthEndExclusive(ym[0], ym[1]);
    String role = menuPermissionService.getUserRole(userId);
    boolean admin = isAdminRole(role);

    int mgrScore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE status=? AND manager_id=? "
                + "AND _updated_at >= ? AND _updated_at < ?",
            Integer.class,
            "manager_review",
            userId,
            start,
            endEx);
    int dualScore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE status=? AND (manager_id=? OR dotted_manager_id=?) "
                + "AND _updated_at >= ? AND _updated_at < ?",
            Integer.class,
            "dual_manager_review",
            userId,
            userId,
            start,
            endEx);
    int dottedScore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE status=? AND dotted_manager_id=? "
                + "AND _updated_at >= ? AND _updated_at < ?",
            Integer.class,
            "dotted_manager_review",
            userId,
            start,
            endEx);
    int approveGoal =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE status=? AND (manager_id=? OR dotted_manager_id=?) "
                + "AND _updated_at >= ? AND _updated_at < ?",
            Integer.class,
            "goal_pending_review",
            userId,
            userId,
            start,
            endEx);
    int needScore = mgrScore + dualScore + dottedScore;
    int needFinalReview = 0;
    if (admin) {
      needFinalReview =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM performance_record WHERE status=? AND _updated_at >= ? AND _updated_at < ?",
              Integer.class,
              "final_review",
              start,
              endEx);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("needScore", needScore);
    out.put("needApproveGoal", approveGoal);
    out.put("needFinalReview", needFinalReview);
    return out;
  }
}
