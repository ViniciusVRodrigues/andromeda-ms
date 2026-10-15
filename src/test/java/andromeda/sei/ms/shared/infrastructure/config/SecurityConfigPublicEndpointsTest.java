package andromeda.sei.ms.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigPublicEndpointsTest {

    private static boolean isPublic(String path) {
        PathPatternParser parser = new PathPatternParser();
        return Arrays.stream(SecurityConfig.PUBLIC_ENDPOINTS)
                .map(parser::parse)
                .anyMatch((PathPattern p) -> p.matches(PathContainer.parsePath(path)));
    }

    @Test
    void should_letVisitorsPostTheAnonymousFunnelWithoutAJwt() {
        assertThat(isPublic("/api/v1/usage-events/public")).isTrue();
    }

    @Test
    void should_keepTheAuthenticatedUsageEndpointBehindTheJwt() {
        assertThat(isPublic("/api/v1/usage-events")).isFalse();
        assertThat(isPublic("/api/v1/usage-events/export")).isFalse();
    }
}
