package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.application.dto.SyncSettingsOutput;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;

import java.time.Instant;

public record SyncSettingsResponse(ScheduledSyncFrequency scheduledSync, int backfillDays, Instant lastScheduledSyncAt) {

    public static SyncSettingsResponse from(SyncSettingsOutput output) {
        return new SyncSettingsResponse(output.scheduledSync(), output.backfillDays(), output.lastScheduledSyncAt());
    }
}
