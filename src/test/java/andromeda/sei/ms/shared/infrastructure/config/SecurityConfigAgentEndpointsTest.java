package andromeda.sei.ms.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigAgentEndpointsTest {

    private static boolean isPublic(String path) {
        PathPatternParser parser = new PathPatternParser();
        return Arrays.stream(SecurityConfig.PUBLIC_ENDPOINTS)
                .map(parser::parse).anyMatch(p -> p.matches(PathContainer.parsePath(path)));
    }

    @Test
    void should_exposeOnlyTheTwoDeviceFlowRoutesWithoutAToken() {
        assertThat(isPublic("/agent/v1/device/code")).isTrue();
        assertThat(isPublic("/agent/v1/device/token")).isTrue();
        assertThat(isPublic("/agent/v1/heartbeat")).isFalse();
        assertThat(isPublic("/api/v1/agent/devices")).isFalse();
        assertThat(isPublic("/api/v1/agent/pairing/BCDF-GHJK")).isFalse();
        assertThat(isPublic("/api/v1/agent/meta")).isFalse();
    }
}
