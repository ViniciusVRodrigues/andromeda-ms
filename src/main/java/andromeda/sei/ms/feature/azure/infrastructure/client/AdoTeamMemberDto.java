package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code isContainer} is true for groups; the client drops those, so downstream it is always false. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoTeamMemberDto(String id, String displayName, String uniqueName, boolean isContainer) {
    public AdoTeamMemberDto(String id, String displayName, String uniqueName) {
        this(id, displayName, uniqueName, false);
    }
}
