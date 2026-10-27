package andromeda.sei.ms.feature.aiusage.domain;

/** Output tokens and record count of one model within a period. */
public record AiUsageModelOutput(String model, long outputTokens, long records) {}
