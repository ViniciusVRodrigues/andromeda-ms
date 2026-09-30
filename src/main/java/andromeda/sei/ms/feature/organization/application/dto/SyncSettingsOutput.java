package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;

import java.time.Instant;

public record SyncSettingsOutput(ScheduledSyncFrequency scheduledSync, int backfillDays, Instant lastScheduledSyncAt) {

    public static SyncSettingsOutput from(Organization organization) {
        return new SyncSettingsOutput(
                organization.getSyncSettings().scheduledSync(),
                organization.getSyncSettings().backfillDays(),
                organization.getLastScheduledSyncAt());
    }
}
