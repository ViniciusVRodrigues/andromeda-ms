package andromeda.sei.ms.feature.aiusage.application.dto;

/** A model with usage in the period and no row in the organization's price table. */
public record UnpricedModelOutput(String model, long outputTokens, long records, boolean billable) {}
