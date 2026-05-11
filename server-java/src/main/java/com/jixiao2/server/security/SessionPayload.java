package com.jixiao2.server.security;

import java.util.List;

public record SessionPayload(
    String sub,
    String name,
    List<String> roles,
    String feishuOpenId,
    long exp) {}
