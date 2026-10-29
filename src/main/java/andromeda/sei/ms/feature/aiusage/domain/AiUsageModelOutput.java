package andromeda.sei.ms.feature.aiusage.domain;

/**
 * Output tokens and record count of one model within a period, plus the input and cache sums of the
 * local agent rows only ({@code source == "AGENT"}).
 */
public record AiUsageModelOutput(String model, long outputTokens, long records,
                                 long agentInputTokens, long agentCacheReadTokens, long agentCacheWriteTokens) {

    public AiUsageModelOutput(String model, long outputTokens, long records) {
        this(model, outputTokens, records, 0, 0, 0);
    }
}
