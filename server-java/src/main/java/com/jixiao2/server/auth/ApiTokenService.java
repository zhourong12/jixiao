package com.jixiao2.server.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiTokenService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private final JdbcTemplate jdbc;

  public ApiTokenService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void ensureTable() {
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS api_token ("
            + "id bigint(20) NOT NULL AUTO_INCREMENT,"
            + "token varchar(64) NOT NULL COMMENT '随机 hex token',"
            + "name varchar(100) NOT NULL COMMENT '标签名称',"
            + "user_id varchar(100) NOT NULL COMMENT '创建者 user_id，Token 继承其角色',"
            + "created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "expires_at datetime DEFAULT NULL COMMENT 'NULL=永不过期',"
            + "last_used_at datetime DEFAULT NULL,"
            + "PRIMARY KEY (id),"
            + "UNIQUE KEY uk_api_token_token (token),"
            + "KEY idx_api_token_user_id (user_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
  }

  @Transactional
  public Map<String, Object> generateToken(String userId, String name, LocalDateTime expiresAt) {
    String trimmedName = name == null ? "" : name.trim();
    if (trimmedName.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 Token 名称");
    }
    if (trimmedName.length() > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token 名称不能超过 100 个字符");
    }

    String token = randomToken();
    jdbc.update(
        "INSERT INTO api_token (token, name, user_id, expires_at) VALUES (?,?,?,?)",
        token,
        trimmedName,
        userId,
        expiresAt == null ? null : Timestamp.valueOf(expiresAt));

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    out.put("token", token);
    return out;
  }

  public ApiTokenInfo validateToken(String token) {
    String raw = token == null ? "" : token.trim();
    if (!raw.matches("^[0-9a-f]{64}$")) {
      return null;
    }
    List<ApiTokenInfo> rows =
        jdbc.query(
            "SELECT t.id, t.user_id, COALESCE(e.name, t.user_id) AS user_name "
                + "FROM api_token t LEFT JOIN employee_hierarchy e ON e.employee_id = t.user_id "
                + "WHERE t.token = ? AND (t.expires_at IS NULL OR t.expires_at > NOW()) LIMIT 1",
            (rs, rn) ->
                new ApiTokenInfo(
                    rs.getLong("id"), rs.getString("user_id"), rs.getString("user_name")),
            raw);
    if (rows.isEmpty()) {
      return null;
    }
    ApiTokenInfo info = rows.get(0);
    jdbc.update("UPDATE api_token SET last_used_at = NOW() WHERE id = ?", info.getId());
    return info;
  }

  public List<Map<String, Object>> listTokens(String userId) {
    return jdbc.query(
        "SELECT id, name, created_at, expires_at, last_used_at FROM api_token "
            + "WHERE user_id = ? ORDER BY created_at DESC, id DESC",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("id", rs.getLong("id"));
          m.put("name", rs.getString("name"));
          m.put("createdAt", rs.getTimestamp("created_at"));
          m.put("expiresAt", rs.getTimestamp("expires_at"));
          m.put("lastUsedAt", rs.getTimestamp("last_used_at"));
          return m;
        },
        userId);
  }

  public void deleteToken(String userId, Long tokenId) {
    if (tokenId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Token ID");
    }
    int affected = jdbc.update("DELETE FROM api_token WHERE id = ? AND user_id = ?", tokenId, userId);
    if (affected == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token 不存在");
    }
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    char[] out = new char[64];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xff;
      out[i * 2] = HEX[v >>> 4];
      out[i * 2 + 1] = HEX[v & 0x0f];
    }
    return new String(out);
  }

  public static final class ApiTokenInfo {
    private final long id;
    private final String userId;
    private final String userName;

    public ApiTokenInfo(long id, String userId, String userName) {
      this.id = id;
      this.userId = userId;
      this.userName = userName;
    }

    public long getId() {
      return id;
    }

    public String getUserId() {
      return userId;
    }

    public String getUserName() {
      return userName;
    }
  }
}
