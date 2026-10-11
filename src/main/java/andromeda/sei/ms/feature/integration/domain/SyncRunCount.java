package andromeda.sei.ms.feature.integration.domain;

import java.time.LocalDate;

/** How many sync logs of one project (or none, organization level), UTC day and status. */
public record SyncRunCount(String projectId, LocalDate day, SyncStatus status, long count) {}
