package andromeda.sei.ms.shared.security;

import andromeda.sei.ms.shared.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("andromeda-test-secret-key-minimum-32-chars-long", 86400000L);
        jwtService = new JwtService(properties);
    }

    @Test
    void shouldGenerateAndParseValidToken() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        List<String> permissions = List.of("USER_MANAGE", "ROLE_MANAGE");

        String token = jwtService.generateToken(userId, orgId, permissions);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();

        var claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("orgId", String.class)).isEqualTo(orgId.toString());
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        assertThat(jwtService.isTokenValid("invalid.token.here")).isFalse();
    }
}
