package andromeda.sei.ms.shared.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    private static MockHttpServletRequest request(String forwarded) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.9");
        if (forwarded != null) r.addHeader("X-Forwarded-For", forwarded);
        return r;
    }

    @Test
    void should_useTheRemoteAddress_when_theHeaderIsAbsent() {
        assertThat(resolver.resolve(request(null))).isEqualTo("10.0.0.9");
    }

    @Test
    void should_useTheFirstValueOfTheList() {
        assertThat(resolver.resolve(request("203.0.113.7, 10.0.0.1, 10.0.0.2"))).isEqualTo("203.0.113.7");
    }

    @Test
    void should_trimSpaces() {
        assertThat(resolver.resolve(request("   203.0.113.7   ,10.0.0.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    void should_fallBackToTheRemoteAddress_when_theHeaderIsBlankOrEmptyFirst() {
        assertThat(resolver.resolve(request("   "))).isEqualTo("10.0.0.9");
        assertThat(resolver.resolve(request(" , 1.2.3.4"))).isEqualTo("10.0.0.9");
    }
}
