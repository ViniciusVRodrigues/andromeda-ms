package andromeda.sei.ms.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Client address for rate limiting only: the first {@code X-Forwarded-For} value (trimmed), else the
 * remote address of the socket. Safe today because nginx rewrites the header from {@code CF-Connecting-IP};
 * SEC1 hardens that later. The result is a throwaway key: never persist it and never log it.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isEmpty()) return first;
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
