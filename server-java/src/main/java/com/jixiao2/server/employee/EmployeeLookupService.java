package com.jixiao2.server.employee;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeLookupService {

  private static final String DEFAULT_LOCAL_PASSWORD = "123456";

  private final JdbcTemplate jdbc;

  public EmployeeLookupService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void assertPassword(String password) {
    if (!DEFAULT_LOCAL_PASSWORD.equals(password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }
  }

  public EmployeeRow resolveByUsername(String username) {
    String u = username == null ? "" : username.trim();
    if (u.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请输入用户名");
    }
    List<EmployeeRow> byId =
        jdbc.query(
            "SELECT employee_id, name FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
            (rs, rowNum) ->
                new EmployeeRow(rs.getString("employee_id"), rs.getString("name")),
            u);
    if (!byId.isEmpty()) {
      return byId.get(0);
    }
    List<EmployeeRow> byName =
        jdbc.query(
            "SELECT employee_id, name FROM employee_hierarchy WHERE name = ?",
            (rs, rowNum) ->
                new EmployeeRow(rs.getString("employee_id"), rs.getString("name")),
            u);
    if (byName.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
    }
    if (byName.size() > 1) {
      List<EmployeeRow> privileged = new java.util.ArrayList<EmployeeRow>();
      for (EmployeeRow row : byName) {
        if (hasLocalAccount(row.getEmployeeId())) {
          privileged.add(row);
        }
      }
      if (privileged.size() == 1) {
        return privileged.get(0);
      }
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "存在重名员工，请使用员工编号登录");
    }
    return byName.get(0);
  }

  private boolean hasLocalAccount(String employeeId) {
    Integer adminCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_config WHERE user_id = ?",
            Integer.class,
            employeeId);
    if (adminCount != null && adminCount > 0) {
      return true;
    }
    Integer roleCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE user_id = ?",
            Integer.class,
            employeeId);
    return roleCount != null && roleCount > 0;
  }

  public String displayName(EmployeeRow row, String fallbackUsername) {
    if (row.getName() != null && !row.getName().trim().isEmpty()) {
      return row.getName().trim();
    }
    return fallbackUsername;
  }

  public static final class EmployeeRow {
    private final String employeeId;
    private final String name;

    public EmployeeRow(String employeeId, String name) {
      this.employeeId = employeeId;
      this.name = name;
    }

    public String getEmployeeId() {
      return employeeId;
    }

    public String getName() {
      return name;
    }
  }
}
