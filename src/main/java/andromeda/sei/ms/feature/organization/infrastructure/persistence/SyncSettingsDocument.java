package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Subdocumento embutido em {@code organizations.syncSettings}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncSettingsDocument {

    private ScheduledSyncFrequency scheduledSync;
    private Integer backfillDays;
}
