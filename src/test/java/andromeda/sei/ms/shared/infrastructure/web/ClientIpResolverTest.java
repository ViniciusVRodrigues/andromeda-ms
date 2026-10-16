package andromeda.sei.ms.shared.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    private static MockHttpServletRequest request(String remote, String... forwarded) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        for (String f : forwarded) r.addHeader("X-Forwarded-For", f);
        return r;
    }

    @Test
    void should_ignoreTheHeader_when_theClientConnectsDirectly() {
        assertThat(resolver.resolve(request("203.0.113.5", "9.9.9.9"))).isEqualTo("203.0.113.5");
    }

    @Test
    void should_returnTheRealClient_when_theProxyAppendedItAfterAForgedValue() {
        assertThat(resolver.resolve(request("172.18.0.1", "6.6.6.6, 203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void should_skipTrustedProxiesInTheChain() {
        assertThat(resolver.resolve(request("10.0.0.9", "6.6.6.6, 203.0.113.7, 172.18.0.5"))).isEqualTo("203.0.113.7");
    }

    @Test
    void should_returnTheSingleValue() {
        assertThat(resolver.resolve(request("127.0.0.1", " 203.0.113.7 "))).isEqualTo("203.0.113.7");
    }

    @Test
    void should_walkMultipleHeaders() {
        assertThat(resolver.resolve(request("127.0.0.1", "6.6.6.6", "203.0.113.7, 10.1.1.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    void should_stopAtGarbage_andReturnTheLastValidEntry() {
        assertThat(resolver.resolve(request("127.0.0.1", "1.1.1.1, garbage, 203.0.113.7, 10.0.0.2")))
                .isEqualTo("203.0.113.7");
        assertThat(resolver.resolve(request("127.0.0.1", "1.1.1.1, garbage, 10.0.0.2"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("127.0.0.1", "1.1.1.1, 300.1.1.1"))).isEqualTo("127.0.0.1");
    }

    @Test
    void should_rejectHostnames_withoutDnsLookup() {
        assertThat(resolver.resolve(request("127.0.0.1", "evil.example.com"))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1", "203.0.113.7, evil.example.com"))).isEqualTo("127.0.0.1");
    }

    @Test
    void should_returnTheLeftmost_when_everyEntryIsTrusted() {
        assertThat(resolver.resolve(request("127.0.0.1", "10.0.0.1, 192.168.1.1"))).isEqualTo("10.0.0.1");
    }

    @Test
    void should_canonicalizeIpv6() {
        assertThat(resolver.resolve(request("::1", "2001:DB8:0:0:0:0:0:1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(resolver.resolve(request("::1", "2001:db8::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(resolver.resolve(request("fd00::5", "2001:db8::1, fc00::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void should_rejectBracketsAndPorts() {
        assertThat(resolver.resolve(request("127.0.0.1", "[2001:db8::1]"))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1", "1.2.3.4:80"))).isEqualTo("127.0.0.1");
    }

    @Test
    void should_useTheRemoteAddress_when_noHeader() {
        assertThat(resolver.resolve(request("10.0.0.9"))).isEqualTo("10.0.0.9");
        assertThat(resolver.resolve(request("127.0.0.1", "   "))).isEqualTo("127.0.0.1");
    }

    @Test
    void should_returnUnknown_when_remoteAddressIsNull() {
        MockHttpServletRequest r = request("1.1.1.1");
        r.setRemoteAddr(null);
        assertThat(resolver.resolve(r)).isEqualTo("unknown");
    }

    @Test
    void should_honourACustomTrustedList() {
        ClientIpResolver custom = new ClientIpResolver("198.51.100.0/24, 203.0.113.9");
        assertThat(custom.resolve(request("198.51.100.4", "6.6.6.6, 203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(custom.resolve(request("10.0.0.1", "6.6.6.6"))).isEqualTo("10.0.0.1");
        assertThat(custom.resolve(request("203.0.113.9", "6.6.6.6"))).isEqualTo("6.6.6.6");
    }

    @Test
    void should_trustNoOne_when_theListIsEmpty() {
        ClientIpResolver none = new ClientIpResolver("");
        assertThat(none.resolve(request("127.0.0.1", "6.6.6.6"))).isEqualTo("127.0.0.1");
    }

    @Test
    void should_failStartup_when_aCidrIsInvalid() {
        assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/33")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("not-an-ip")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("example.com/8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("::1/129")).isInstanceOf(IllegalArgumentException.class);
    }
}
