package andromeda.sei.ms.feature.github.application.dto;

import java.time.Instant;

/**
 * One bound repository plus its current health. A null metric means "no data", which the UI
 * must render differently from zero — a repository nobody reviewed has no average review time,
 * which is not the same as reviewing instantly.
 */
public record ProjectRepoSummaryOutput(
        String repoId,
        String fullName,
        long openPrCount,
        long stalePrCount,
        Double avgCycleTimeHours,
        Double avgTimeToFirstReviewHours,
        Double latestCfr,
        Boolean latestCfrEstimated,
        Instant latestCfrPeriodEnd) {}
