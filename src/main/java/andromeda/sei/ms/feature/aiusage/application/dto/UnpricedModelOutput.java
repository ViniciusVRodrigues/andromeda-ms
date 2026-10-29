package andromeda.sei.ms.feature.aiusage.application.dto;

import java.util.List;

/**
 * A model with usage in the period that the cost cannot fully price. {@code missing} is a subset of
 * OUTPUT, INPUT, CACHE_READ, CACHE_WRITE (["OUTPUT"] when there is no entry, empty when not billable);
 * the token sums are from agent rows only.
 */
public record UnpricedModelOutput(String model, long outputTokens, long records, boolean billable,
                                  List<String> missing, long inputTokens, long cacheReadTokens,
                                  long cacheWriteTokens) {}
