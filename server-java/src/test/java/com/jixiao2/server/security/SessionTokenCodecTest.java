package com.jixiao2.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SessionTokenCodecTest {

  @Test
  void signAndVerify_roundTrip() {
    MockEnvironment env = new MockEnvironment();
    SessionTokenCodec codec = new SessionTokenCodec(env, "unit-test-secret");
    String token = codec.sign("zhou_rong", "周荣", List.of("super_admin", "employee"), null);
    assertThat(token).isNotBlank();
    SessionPayload p = codec.verify(token);
    assertThat(p).isNotNull();
    assertThat(p.sub()).isEqualTo("zhou_rong");
    assertThat(p.name()).isEqualTo("周荣");
    assertThat(p.roles()).containsExactly("super_admin", "employee");
  }
}
