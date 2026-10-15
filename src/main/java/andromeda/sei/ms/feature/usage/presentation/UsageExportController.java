package andromeda.sei.ms.feature.usage.presentation;

import andromeda.sei.ms.feature.usage.application.usecase.ExportUsageEventsUseCase;
import andromeda.sei.ms.feature.usage.domain.exception.UsageExportRateLimitedException;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Usage CSV export for the organization admin (USER_MANAGE and ROLE_MANAGE; org level, no {@code @perm}).
 * The organization is the tenant's; the stream runs on another thread, so it is captured before.
 */
@RestController
@RequestMapping("/api/v1/usage-events")
@RequiredArgsConstructor
public class UsageExportController {

    static final int MAX_EXPORTS = 10;
    static final Duration WINDOW = Duration.ofHours(1);

    private final ExportUsageEventsUseCase exportUseCase;
    private final RateLimiter rateLimiter;

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('USER_MANAGE') and hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        ExportUsageEventsUseCase.Export export = exportUseCase.prepare(TenantContextHolder.get(), from, to);
        String key = "usage-export:" + user.getUserId();
        if (!rateLimiter.tryAcquire(key, MAX_EXPORTS, WINDOW)) {
            throw new UsageExportRateLimitedException(rateLimiter.retryAfterSeconds(key, WINDOW));
        }
        StreamingResponseBody body = out -> exportUseCase.write(export, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"usage-events-"
                        + export.orgSlug() + "-" + from + "-" + to + ".csv\"")
                .body(body);
    }

    /** Missing or malformed date: a client error (the global handler would answer 500). */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadDate(Exception ex) {
        String name = ex instanceof MissingServletRequestParameterException m
                ? m.getParameterName()
                : ((MethodArgumentTypeMismatchException) ex).getName();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", "Data ausente ou inválida: " + name));
    }
}
