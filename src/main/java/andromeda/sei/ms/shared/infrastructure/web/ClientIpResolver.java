package andromeda.sei.ms.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Client address for rate limiting only. Works like the Tomcat {@code RemoteIpValve}: the
 * {@code X-Forwarded-For} header is honoured only when the socket peer ({@code remoteAddr}) is a trusted
 * proxy ({@code andromeda.web.trusted-proxies}, CIDR list). The chain is walked right to left, skipping
 * trusted proxies; the first untrusted entry is the client. A direct client cannot forge the header.
 * An invalid entry stops the walk and the last valid entry seen (or the remote address) is used.
 * Only IP literals are parsed (no DNS lookups). The result is a throwaway key: never persist it and never log it.
 */
@Component
public class ClientIpResolver {

    static final String DEFAULT_TRUSTED_PROXIES =
            "127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, ::1/128, fc00::/7, fe80::/10";

    private final List<Cidr> trusted;

    public ClientIpResolver() {
        this(DEFAULT_TRUSTED_PROXIES);
    }

    @Autowired
    public ClientIpResolver(@Value("${andromeda.web.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxies) {
        List<Cidr> list = new ArrayList<>();
        if (trustedProxies != null) {
            for (String part : trustedProxies.split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) list.add(Cidr.parse(p));
            }
        }
        this.trusted = List.copyOf(list);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null) return "unknown";
        byte[] remoteBytes = parseLiteral(remote.trim());
        if (remoteBytes == null || !isTrusted(remoteBytes)) return remote;

        List<String> entries = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        while (headers != null && headers.hasMoreElements()) {
            for (String e : headers.nextElement().split(",", -1)) entries.add(e.trim());
        }
        String lastValid = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            byte[] bytes = parseLiteral(entries.get(i));
            if (bytes == null) return lastValid != null ? lastValid : remote;
            lastValid = canonical(bytes);
            if (!isTrusted(bytes)) return lastValid;
        }
        return lastValid != null ? lastValid : remote;
    }

    private boolean isTrusted(byte[] address) {
        for (Cidr c : trusted) if (c.contains(address)) return true;
        return false;
    }

    private static String canonical(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parses an IPv4 or IPv6 literal to its bytes, or returns null. Never touches DNS. */
    static byte[] parseLiteral(String s) {
        if (s == null || s.isEmpty()) return null;
        if (s.indexOf(':') < 0) return parseIpv4(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = c == ':' || c == '.' || (c < 128 && Character.digit(c, 16) >= 0);
            if (!ok) return null;
        }
        try {
            // contains ':' and only literal characters: parsed as an IPv6 literal, no name resolution
            return InetAddress.getByName(s).getAddress();
        } catch (UnknownHostException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) return null;
            int v = 0;
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') return null;
                v = v * 10 + (c - '0');
            }
            if (v > 255) return null;
            out[i] = (byte) v;
        }
        return out;
    }

    private record Cidr(byte[] network, int prefix) {

        static Cidr parse(String text) {
            String addr = text;
            Integer prefix = null;
            int slash = text.indexOf('/');
            if (slash >= 0) {
                addr = text.substring(0, slash).trim();
                String pre = text.substring(slash + 1).trim();
                if (pre.isEmpty() || pre.length() > 3 || !pre.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + text);
                }
                prefix = Integer.parseInt(pre);
            }
            byte[] bytes = parseLiteral(addr);
            if (bytes == null) throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + text);
            int max = bytes.length * 8;
            if (prefix == null) prefix = max;
            if (prefix > max) throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + text);
            return new Cidr(bytes, prefix);
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int full = prefix / 8;
            for (int i = 0; i < full; i++) if (address[i] != network[i]) return false;
            int rem = prefix % 8;
            if (rem == 0) return true;
            int mask = (0xFF << (8 - rem)) & 0xFF;
            return (address[full] & mask) == (network[full] & mask);
        }
    }
}
