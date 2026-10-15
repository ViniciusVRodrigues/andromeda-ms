package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.LoginMethod;
import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Records a successful login: {@code User.lastLoginAt} always, and a LOGIN event when the collection
 * is on. Best-effort: a failure is a WARN (never the e-mail) and never reaches the login.
 */
@Component
@RequiredArgsConstructor
public class RecordLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordLoginUseCase.class);

    private final UserRepository userRepository;
    private final UsageEventRepository usageEventRepository;
    private final TelemetrySettings settings;

    public void execute(String userId, String organizationId, LoginMethod method) {
        Instant now = Instant.now();
        try {
            userRepository.recordLogin(userId, organizationId, now);
        } catch (RuntimeException e) {
            log.warn("Could not record lastLoginAt (user {}, org {}): {}", userId, organizationId, e.getClass().getSimpleName());
        }
        if (!settings.isEnabled()) return;
        try {
            usageEventRepository.insertAll(List.of(UsageEvent.login(organizationId, userId, method, now)));
        } catch (RuntimeException e) {
            log.warn("Could not record LOGIN event (user {}, org {}): {}", userId, organizationId, e.getClass().getSimpleName());
        }
    }
}
