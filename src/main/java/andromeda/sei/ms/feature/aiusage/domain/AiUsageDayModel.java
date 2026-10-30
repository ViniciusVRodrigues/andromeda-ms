package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * AI usage of a set of users on one day with one model (a {@code null} model is its own row), summed
 * in the database. {@code costUsd} counts a missing cost as zero; {@code lastUploadedAt} is the latest
 * upload of the rows, {@code null} when none has one.
 */
public record AiUsageDayModel(LocalDate day, String model, long inputTokens, long outputTokens, long interactions,
                              BigDecimal costUsd, Instant lastUploadedAt) {}
