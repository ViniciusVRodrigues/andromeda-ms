package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import org.springframework.stereotype.Component;

@Component
public class AiUsageDocumentMapper {

    public AIUsage toDomain(AiUsageDocument doc) {
        return new AIUsage(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getGitHubLogin(),
                doc.getDatasetId(),
                doc.getDay(),
                doc.getModel(),
                doc.getWorkspaceId(),
                doc.getWorkspaceName(),
                doc.getMachineId(),
                doc.getMachineName(),
                doc.getInputTokens(),
                doc.getOutputTokens(),
                doc.getInteractions(),
                doc.getUploadedAt(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public AiUsageDocument toDocument(AIUsage usage) {
        return AiUsageDocument.builder()
                .id(usage.getId())
                .organizationId(usage.getOrganizationId())
                .userId(usage.getUserId())
                .gitHubLogin(usage.getGitHubLogin())
                .datasetId(usage.getDatasetId())
                .day(usage.getDay())
                .model(usage.getModel())
                .workspaceId(usage.getWorkspaceId())
                .workspaceName(usage.getWorkspaceName())
                .machineId(usage.getMachineId())
                .machineName(usage.getMachineName())
                .inputTokens(usage.getInputTokens())
                .outputTokens(usage.getOutputTokens())
                .interactions(usage.getInteractions())
                .uploadedAt(usage.getUploadedAt())
                .active(usage.isActive())
                .version(usage.getVersion())
                .createdAt(usage.getCreatedAt())
                .updatedAt(usage.getUpdatedAt())
                .build();
    }
}
