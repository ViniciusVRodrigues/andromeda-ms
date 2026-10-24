package andromeda.sei.ms.feature.azure.presentation.dto;

import java.time.Instant;

/** Caller's own Azure link state. By design has no {@code azureId} and nothing about other people. */
public record MyAzureIdentityResponse(String status, String azureEmail, String displayName,
                                      String source, Instant linkedAt) {}
