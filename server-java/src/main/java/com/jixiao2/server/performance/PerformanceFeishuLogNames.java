package com.jixiao2.server.performance;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 飞书绩效通知/待办日志：将 employee_id 解析为可读姓名。 */
@Component
public class PerformanceFeishuLogNames {

  private final JdbcTemplate jdbc;

  public PerformanceFeishuLogNames(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String displayName(String employeeId) {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      return "";
    }
    String id = employeeId.trim();
    try {
      List<String> rows =
          jdbc.query(
              "SELECT name FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
              (rs, rn) -> rs.getString(1),
              id);
      if (!rows.isEmpty()) {
        String n = rows.get(0);
        if (n != null && !n.trim().isEmpty() && !n.trim().equals(id)) {
          return n.trim();
        }
      }
    } catch (Exception ignored) {
      // 日志辅助，查询失败时回退 id
    }
    return id.startsWith("ou_") || id.startsWith("on_") ? "未同步姓名" : id;
  }

  /** IM 接收人：姓名 + employeeId + open_id */
  public String recipient(String employeeId, String receiveOpenId) {
    String id = employeeId == null ? "" : employeeId.trim();
    String rid = receiveOpenId == null ? "" : receiveOpenId.trim();
    return "employeeName="
        + displayName(id)
        + " employeeId="
        + id
        + " receiveId="
        + rid;
  }

  public String assignee(String employeeId) {
    String id = employeeId == null ? "" : employeeId.trim();
    return "assigneeName=" + displayName(id) + " assigneeId=" + id;
  }

  static String nodeKeyLabel(String nodeKey) {
    if (nodeKey == null || nodeKey.trim().isEmpty()) {
      return "-";
    }
    return nodeKey.trim();
  }
}
