package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One bound repository plus its health over the requested window. A null metric means "no data",
 * which the UI must render differently from zero.
 *
 * <p>{@code repoId} (= {@code repositoryId}, the internal {@code vcs_repositories} id) and
 * {@code fullName} (= {@code displayName}) keep the pre-refactor field names. {@code avg*} and
 * {@code latestCfr*} are deprecated; {@code latestCfr} is {@code delivery.changeFailRatePercent}
 * (a percentage) and {@code latestCfrPeriodEnd} the end of the window.
 */
public record ProjectRepositoryOutput(
        String repoId,
        String fullName,
        long openPrCount,
        long stalePrCount,
        @Schema(deprecated = true) Double avgCycleTimeHours,
        @Schema(deprecated = true) Double avgTimeToFirstReviewHours,
        @Schema(deprecated = true) Double latestCfr,
        @Schema(deprecated = true) Boolean latestCfrEstimated,
        @Schema(deprecated = true) Instant latestCfrPeriodEnd,
        DurationStat prCycleTime,
        ReviewMetrics review,
        DeliveryMetrics delivery,
        String repositoryId,
        VcsProvider provider,
        String displayName,
        String defaultBranch) {}
