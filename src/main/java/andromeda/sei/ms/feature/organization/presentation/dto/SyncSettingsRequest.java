package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import jakarta.validation.constraints.NotNull;

/**
 * @param scheduledSync {@code OFF}, {@code DAILY} ou {@code EVERY_6_HOURS}
 * @param backfillDays  7 a 730 (validado no caso de uso, que responde 422)
 */
public record SyncSettingsRequest(@NotNull ScheduledSyncFrequency scheduledSync, @NotNull Integer backfillDays) {}
