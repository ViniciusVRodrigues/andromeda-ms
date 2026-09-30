package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;

public record UpdateSyncSettingsInput(ScheduledSyncFrequency scheduledSync, int backfillDays) {}
