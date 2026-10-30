package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.TokenPairOutput;
import andromeda.sei.ms.feature.agent.application.usecase.ExchangeDeviceTokenUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RefreshAgentTokenUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RequestDeviceCodeUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.exception.AgentOAuthException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRateLimitedException;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.DeviceCodeRequest;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.DeviceCodeResponse;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.DeviceTokenRequest;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.OAuthError;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.TokenResponse;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/** Public endpoints of the device flow (RFC 8628): ask for a code and exchange/refresh tokens. */
@Tag(name = "Agent device flow")
@RestController
@RequestMapping("/agent/v1/device")
public class AgentDeviceAuthController {

    static final Duration CODE_WINDOW = Duration.ofMinutes(10);
    static final Duration TOKEN_WINDOW = Duration.ofMinutes(1);

    private final RequestDeviceCodeUseCase requestDeviceCode;
    private final ExchangeDeviceTokenUseCase exchangeDeviceToken;
    private final RefreshAgentTokenUseCase refreshAgentToken;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final String frontendUrl;
    private final int codePerIp;
    private final int tokenPerIp;
    private final int tokenPerCredential;

    /** Defaults, for tests. */
    AgentDeviceAuthController(RequestDeviceCodeUseCase requestDeviceCode,
                              ExchangeDeviceTokenUseCase exchangeDeviceToken,
                              RefreshAgentTokenUseCase refreshAgentToken,
                              RateLimiter rateLimiter, ClientIpResolver clientIpResolver, String frontendUrl) {
        this(requestDeviceCode, exchangeDeviceToken, refreshAgentToken, rateLimiter, clientIpResolver, frontendUrl,
                100, 1200, 30);
    }

    @Autowired
    public AgentDeviceAuthController(RequestDeviceCodeUseCase requestDeviceCode,
                                     ExchangeDeviceTokenUseCase exchangeDeviceToken,
                                     RefreshAgentTokenUseCase refreshAgentToken,
                                     RateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                                     @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                     @Value("${andromeda.agent.rate-limit.code-per-ip:100}") int codePerIp,
                                     @Value("${andromeda.agent.rate-limit.token-per-ip:1200}") int tokenPerIp,
                                     @Value("${andromeda.agent.rate-limit.token-per-credential:30}") int tokenPerCredential) {
        this.requestDeviceCode = requestDeviceCode;
        this.exchangeDeviceToken = exchangeDeviceToken;
        this.refreshAgentToken = refreshAgentToken;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.frontendUrl = frontendUrl;
        this.codePerIp = codePerIp;
        this.tokenPerIp = tokenPerIp;
        this.tokenPerCredential = tokenPerCredential;
    }

    @PostMapping("/code")
    public DeviceCodeResponse code(@Valid @RequestBody DeviceCodeRequest body, HttpServletRequest request) {
        String ip = clientIpResolver.resolve(request);
        String key = "agent-code:" + ip;
        if (!rateLimiter.tryAcquire(key, codePerIp, CODE_WINDOW)) {
            throw new AgentRateLimitedException(rateLimiter.retryAfterSeconds(key, CODE_WINDOW));
        }
        DeviceCodeOutput out = requestDeviceCode.execute(new DeviceCodeInput(body.agentVersion(), body.machineName(),
                body.os(), body.osVersion(), body.osLabel(), body.arch(), body.timezone(), ip));
        String base = frontendUrl.replaceAll("/+$", "") + "/parear";
        String complete = UriComponentsBuilder.fromUriString(base).queryParam("codigo", out.userCode())
                .build().toUriString();
        return new DeviceCodeResponse(out.deviceCode(), out.userCode(), base, complete,
                out.expiresInSeconds(), out.intervalSeconds());
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> tokenJson(HttpServletRequest request,
                                                   @RequestBody(required = false) DeviceTokenRequest json) {
        DeviceTokenRequest body = json == null ? new DeviceTokenRequest(null, null, null) : json;
        return handle(body, request);
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<TokenResponse> tokenForm(HttpServletRequest request,
                                                   @RequestParam(name = "grant_type", required = false) String grantType,
                                                   @RequestParam(name = "device_code", required = false) String deviceCode,
                                                   @RequestParam(name = "refresh_token", required = false) String refreshToken) {
        return handle(new DeviceTokenRequest(grantType, deviceCode, refreshToken), request);
    }

    private ResponseEntity<TokenResponse> handle(DeviceTokenRequest body, HttpServletRequest request) {
        String key = "agent-token:" + clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire(key, tokenPerIp, TOKEN_WINDOW)) {
            throw new AgentRateLimitedException(rateLimiter.retryAfterSeconds(key, TOKEN_WINDOW));
        }
        String credential = credentialOf(body);
        if (!blank(credential)) {
            String credKey = "agent-token-cred:" + AgentCodes.sha256(credential);
            if (!rateLimiter.tryAcquire(credKey, tokenPerCredential, TOKEN_WINDOW)) {
                throw new AgentRateLimitedException(rateLimiter.retryAfterSeconds(credKey, TOKEN_WINDOW));
            }
        }
        String grant = body.grantType();
        if (blank(grant)) {
            throw new AgentOAuthException("invalid_request", "grant_type é obrigatório");
        }
        TokenPairOutput out;
        switch (grant) {
            case "urn:ietf:params:oauth:grant-type:device_code", "device_code" -> {
                if (blank(body.deviceCode())) {
                    throw new AgentOAuthException("invalid_request", "device_code é obrigatório");
                }
                out = exchangeDeviceToken.execute(body.deviceCode());
            }
            case "refresh_token" -> {
                if (blank(body.refreshToken())) {
                    throw new AgentOAuthException("invalid_request", "refresh_token é obrigatório");
                }
                out = refreshAgentToken.execute(body.refreshToken());
            }
            default -> throw new AgentOAuthException("unsupported_grant_type", "grant_type não suportado");
        }
        TokenResponse res = new TokenResponse(out.accessToken(), "Bearer", out.expiresInSeconds(),
                out.refreshToken(), out.scope(), out.deviceId(), out.organizationSlug());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(res);
    }

    private static String credentialOf(DeviceTokenRequest body) {
        String grant = body.grantType();
        if (grant == null) {
            return null;
        }
        return switch (grant) {
            case "urn:ietf:params:oauth:grant-type:device_code", "device_code" -> body.deviceCode();
            case "refresh_token" -> body.refreshToken();
            default -> null;
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** RFC 6749 error body: 400 {@code {error, error_description}}. */
    @ExceptionHandler(AgentOAuthException.class)
    public ResponseEntity<OAuthError> oauthError(AgentOAuthException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new OAuthError(e.getError(), e.getMessage()));
    }
}
