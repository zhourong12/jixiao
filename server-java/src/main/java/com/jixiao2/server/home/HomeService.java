package com.jixiao2.server.home;

import com.jixiao2.server.employee.EmployeeService;
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

  private final JdbcTemplate jdbc;
  private final MenuPermissionService menuPermissionService;
  private final EmployeeService employeeService;

  public HomeService(
      JdbcTemplate jdbc, MenuPermissionService menuPermissionService, EmployeeService employeeService) {
    this.jdbc = jdbc;
    this.menuPermissionService = menuPermissionService;
    this.employeeService = employeeService;
  }

  /** 与绩效终审/校准能力一致：可看到「待终审」类待办的用户（非全员绩效列表）。 */
  private boolean canSeeFinalReviewTodos(String userId, String role) {
    if (userId == null || userId.isEmpty()) {
      return false;
    }
    if ("super_admin".equals(role)) {
      return true;
    }
    if (menuPermissionService.isMenuAllowed(userId, "performance_review_admin")) {
      return true;
    }
    return employeeService.readCalibrationAssigneeEmployeeIds().contains(userId);
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

  public Map<String, Object> getTodos(String userId, Integer year, Integer month) {
    int[] ym = resolveYearMonth(year, month);
    Timestamp start = monthStart(ym[0], ym[1]);
    Timestamp endEx = monthEndExclusive(ym[0], ym[1]);
    String role = menuPermissionService.getUserRole(userId);

    List<Object> args = new ArrayList<Object>();
    List<String> whereParts = new ArrayList<String>();
    whereParts.add(
        "((pr.status IN ('template_selection','goal_setting') AND pr.employee_id=?) OR (pr.status=? AND pr.employee_id=?) OR "
            + "(pr.status=? AND (pr.manager_id=? OR pr.dotted_manager_id=?)) OR "
            + "(pr.status=? AND pr.employee_id=?) OR (pr.status=? AND pr.manager_id=?) OR "
            + "(pr.status=? AND (pr.manager_id=? OR pr.dotted_manager_id=?)) OR "
            + "(pr.status=? AND pr.dotted_manager_id=?) OR "
            + "(pr.status=? AND pr.employee_id=?))");
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
    args.add("issued");
    args.add(userId);
    if (canSeeFinalReviewTodos(userId, role)) {
      whereParts.add("(pr.status='final_review')");
    }

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(
        "SELECT pr.id, pr.period, pr.status, pr.employee_id, eh.name AS employee_name, eh.department_name FROM performance_record pr "
            + "LEFT JOIN employee_hierarchy eh ON eh.employee_id = pr.employee_id WHERE (");
    sqlBuilder.append(String.join(" OR ", whereParts));
    sqlBuilder.append(
        ") AND pr.deleted_at IS NULL AND pr._updated_at >= ? AND pr._updated_at < ? ORDER BY pr._updated_at DESC");
    String sql = sqlBuilder.toString();
    args.add(start);
    args.add(endEx);

    List<Map<String, Object>> rows =
        jdbc.query(
            sql,
            args.toArray(),
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("period", rs.getString("period"));
              m.put("status", rs.getString("status"));
              m.put("employeeId", rs.getString("employee_id"));
              m.put("employeeName", rs.getString("employee_name"));
              m.put("departmentName", rs.getString("department_name"));
              return m;
            });

    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> r : rows) {
      String period = (String) r.get("period");
      String st = (String) r.get("status");
      String employeeName = (String) r.get("employeeName");
      String departmentName = (String) r.get("departmentName");
      String type = mapTodoType(st);
      String title = buildTitle(employeeName, st);
      Map<String, Object> it = new LinkedHashMap<String, Object>();
      it.put("id", r.get("id"));
      it.put("period", period);
      it.put("type", type);
      it.put("title", title);
      it.put("employeeId", r.get("employeeId"));
      it.put(
          "employeeName",
          employeeName != null && !employeeName.trim().isEmpty() ? employeeName.trim() : "");
      it.put(
          "departmentName",
          departmentName != null && !departmentName.trim().isEmpty() ? departmentName.trim() : "");
      items.add(it);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  private static String mapTodoType(String status) {
    if ("template_selection".equals(status) || "goal_setting".equals(status)) {
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
    if ("issued".equals(status)) {
      return "issued";
    }
    return "self_review";
  }

  private static String buildTitle(String employeeName, String status) {
    String who =
        employeeName != null && !employeeName.trim().isEmpty() ? employeeName.trim() + " · " : "";
    if ("template_selection".equals(status) || "goal_setting".equals(status)) {
      return who + "目标设定";
    }
    if ("goal_rejected".equals(status)) {
      return who + "目标被驳回，请修改";
    }
    if ("goal_pending_review".equals(status)) {
      return who + "待审核目标";
    }
    if ("self_review".equals(status)) {
      return who + "绩效自评";
    }
    if ("manager_review".equals(status)) {
      return who + "直属上级评分";
    }
    if ("dual_manager_review".equals(status)) {
      return who + "上级与虚线上级评分";
    }
    if ("dotted_manager_review".equals(status)) {
      return who + "虚线上级评分";
    }
    if ("final_review".equals(status)) {
      return who + "待终审/校准";
    }
    if ("issued".equals(status)) {
      return who + "绩效结果待确认";
    }
    return who + "待办";
  }
}
