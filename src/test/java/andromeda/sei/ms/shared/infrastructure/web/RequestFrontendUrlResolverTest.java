package andromeda.sei.ms.shared.infrastructure.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFrontendUrlResolverTest {

    private static final String FALLBACK = "http://localhost:5173";

    private final RequestFrontendUrlResolver resolver = new RequestFrontendUrlResolver(FALLBACK);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldResolveFallbackWhenNoRequestContext() {
        assertThat(resolver.resolve()).isEqualTo(FALLBACK);
    }

    @Test
    void shouldResolveOriginHeaderWhenPresent() {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://andromeda.ia.br");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.resolve()).isEqualTo("https://andromeda.ia.br");
    }

    @Test
    void shouldResolveFallbackWhenOriginHeaderMissing() {
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.resolve()).isEqualTo(FALLBACK);
    }

    @Test
    void shouldResolveFallbackWhenOriginHeaderIsInvalid() {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "not-a-valid-origin");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.resolve()).isEqualTo(FALLBACK);
    }
}
