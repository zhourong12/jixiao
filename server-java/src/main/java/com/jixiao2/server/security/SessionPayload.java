package com.jixiao2.server.security;

import java.util.Collections;
import java.util.List;

/** 会话载荷（Java 8：显式 POJO，与 Node SessionPayload 字段一致）。 */
public final class SessionPayload {

  private final String sub;
  private final String name;
  private final List<String> roles;
  private final String feishuOpenId;
  private final long exp;

  public SessionPayload(
      String sub, String name, List<String> roles, String feishuOpenId, long exp) {
    this.sub = sub;
    this.name = name;
    this.roles = roles == null ? Collections.<String>emptyList() : roles;
    this.feishuOpenId = feishuOpenId;
    this.exp = exp;
  }

  public String getSub() {
    return sub;
  }

  public String getName() {
    return name;
  }

  public List<String> getRoles() {
    return roles;
  }

  public String getFeishuOpenId() {
    return feishuOpenId;
  }

  public long getExp() {
    return exp;
  }
}
