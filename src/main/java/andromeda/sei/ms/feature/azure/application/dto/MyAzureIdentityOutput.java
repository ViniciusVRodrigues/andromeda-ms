package andromeda.sei.ms.feature.azure.application.dto;

import java.time.Instant;

/**
 * The caller's own Azure link state. Never carries {@code azureId} nor any data of another person;
 * every field but {@code status} is filled only for {@code LINKED}.
 */
public record MyAzureIdentityOutput(String status, String azureEmail, String displayName,
                                    String source, Instant linkedAt) {

    public static MyAzureIdentityOutput of(String status) {
        return new MyAzureIdentityOutput(status, null, null, null, null);
    }
}
