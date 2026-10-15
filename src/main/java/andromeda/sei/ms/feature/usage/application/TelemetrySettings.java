package andromeda.sei.ms.feature.usage.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Kill switch of the usage collection ({@code TELEMETRY_ENABLED}, default true). Off: the endpoint
 * answers 202 and drops the batch, and no LOGIN event is written. {@code User.lastLoginAt} is
 * operational data and is written either way.
 */
@Component
public class TelemetrySettings {

    private final boolean enabled;

    public TelemetrySettings(@Value("${andromeda.telemetry.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
