package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Document(collection = "ai_usages")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'userId': 1, 'day': 1}"),
        @CompoundIndex(
                def = "{'organizationId':1,'userId':1,'datasetId':1,'day':1,'model':1,'workspaceId':1,'machineId':1}",
                unique = true,
                name = "uq_ai_usage_natural_key"
        ),
        @CompoundIndex(def = "{'organizationId':1,'userId':1,'allocationId':1}", name = "idx_ai_usage_allocation")
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageDocument {

    @Id
    private String id;

    private String organizationId;
    private String userId;
    private String gitHubLogin;

    private String datasetId;
    private LocalDate day;
    private String model;
    private String workspaceId;
    private String workspaceName;
    private String machineId;
    private String machineName;

    private long inputTokens;
    private long outputTokens;
    private int interactions;
    private String editor;
    private String allocationId;
    private BigDecimal costUsd;

    private Instant uploadedAt;

    @lombok.Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
