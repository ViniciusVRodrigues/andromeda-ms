package andromeda.sei.ms.feature.azure.application.dto;

import java.time.LocalDate;

/** A user and a UTC day on which new work item activity was stored by a sync. */
public record ActivityDay(String userId, LocalDate day) {}
