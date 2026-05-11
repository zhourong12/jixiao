package com.jixiao2.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SessionTokenCodecTest {

  @Test
  void signAndVerify_roundTrip() {
    MockEnvironment env = new MockEnvironment();
    SessionTokenCodec codec = new SessionTokenCodec(env, "unit-test-secret");
    List<String> roles = Arrays.asList("super_admin", "employee");
    String token = codec.sign("zhou_rong", "周荣", roles, null);
    assertThat(token).isNotBlank();
    SessionPayload p = codec.verify(token);
    assertThat(p).isNotNull();
    assertThat(p.getSub()).isEqualTo("zhou_rong");
    assertThat(p.getName()).isEqualTo("周荣");
    assertThat(p.getRoles()).containsExactly("super_admin", "employee");
  }
}
