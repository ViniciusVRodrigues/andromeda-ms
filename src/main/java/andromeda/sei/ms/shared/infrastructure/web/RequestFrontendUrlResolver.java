package andromeda.sei.ms.shared.infrastructure.web;

import andromeda.sei.ms.shared.application.port.FrontendUrlResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class RequestFrontendUrlResolver implements FrontendUrlResolver {

    private final String fallbackFrontendUrl;

    public RequestFrontendUrlResolver(@Value("${app.frontend-url:http://localhost:5173}") String fallbackFrontendUrl) {
        this.fallbackFrontendUrl = fallbackFrontendUrl;
    }

    @Override
    public String resolve() {
        String origin = currentRequestOrigin();
        return origin != null ? origin : fallbackFrontendUrl;
    }

    private String currentRequestOrigin() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            return origin;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
