package andromeda.sei.ms.feature.vcs.application.dto;

import java.time.Instant;

/**
 * One weekly bucket of a repository's delivery metrics, computed on read from its merged pull
 * requests (delivery-metrics). {@code changeFailRatePercent} is a percentage, {@code null} in a
 * week without merges; {@code deploymentsPerWeek} is then {@code 0}.
 */
public record DeliveryMetricPointOutput(Instant periodStart, Instant periodEnd, Double changeFailRatePercent,
                                        Double deploymentsPerWeek, int mergedPrs, int remediationPrs) {}
