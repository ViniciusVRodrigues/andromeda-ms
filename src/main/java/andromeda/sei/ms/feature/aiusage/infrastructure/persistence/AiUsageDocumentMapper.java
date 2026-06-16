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
                doc.getEditor(),
                doc.getAllocationId(),
                doc.getCostUsd(),
                doc.getUploadedAt(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public AiUsageDocument toDocument(AIUsage usage) {
        var builder = AiUsageDocument.builder()
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
                .editor(usage.getEditor())
                .allocationId(usage.getAllocationId())
                .costUsd(usage.getCostUsd())
                .uploadedAt(usage.getUploadedAt())
                .active(usage.isActive())
                .createdAt(usage.getCreatedAt())
                .updatedAt(usage.getUpdatedAt());

        if (usage.getId() != null) {
            builder.id(usage.getId());
            builder.version(usage.getVersion());
        }

        return builder.build();
    }
}
