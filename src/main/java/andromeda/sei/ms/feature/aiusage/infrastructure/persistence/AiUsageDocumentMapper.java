package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import org.springframework.stereotype.Component;

@Component
public class AiUsageDocumentMapper {

    public AIUsage toDomain(AiUsageDocument doc) {
        AIUsage usage = new AIUsage(
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
        usage.setSource(doc.getSource());
        usage.setAgentKey(doc.getAgentKey());
        usage.setBranchApproximate(doc.isBranchApproximate());
        usage.setDeviceId(doc.getDeviceId());
        usage.setTool(doc.getTool());
        usage.setSessionHash(doc.getSessionHash());
        usage.setBucketStart(doc.getBucketStart());
        usage.setCacheReadTokens(doc.getCacheReadTokens());
        usage.setCacheWriteTokens(doc.getCacheWriteTokens());
        usage.setThinkingTokens(doc.getThinkingTokens());
        usage.setOrigin(doc.getOrigin());
        usage.setRepository(doc.getRepository());
        usage.setBranch(doc.getBranch());
        usage.setTaskRef(doc.getTaskRef());
        usage.setAttributionMethod(doc.getAttributionMethod());
        usage.setAttributionSource(doc.getAttributionSource());
        usage.setWorkItemId(doc.getWorkItemId());
        usage.setProjectId(doc.getProjectId());
        usage.setAgentVersion(doc.getAgentVersion());
        usage.setBatchId(doc.getBatchId());
        usage.setSupersededBy(doc.getSupersededBy());
        usage.setPossibleDuplicate(doc.isPossibleDuplicate());
        return usage;
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
                .updatedAt(usage.getUpdatedAt())
                .source(usage.getSource())
                .agentKey(usage.getAgentKey())
                .branchApproximate(usage.isBranchApproximate())
                .deviceId(usage.getDeviceId())
                .tool(usage.getTool())
                .sessionHash(usage.getSessionHash())
                .bucketStart(usage.getBucketStart())
                .cacheReadTokens(usage.getCacheReadTokens())
                .cacheWriteTokens(usage.getCacheWriteTokens())
                .thinkingTokens(usage.getThinkingTokens())
                .origin(usage.getOrigin())
                .repository(usage.getRepository())
                .branch(usage.getBranch())
                .taskRef(usage.getTaskRef())
                .attributionMethod(usage.getAttributionMethod())
                .attributionSource(usage.getAttributionSource())
                .workItemId(usage.getWorkItemId())
                .projectId(usage.getProjectId())
                .agentVersion(usage.getAgentVersion())
                .batchId(usage.getBatchId())
                .supersededBy(usage.getSupersededBy())
                .possibleDuplicate(usage.isPossibleDuplicate());

        if (usage.getId() != null) {
            builder.id(usage.getId());
            builder.version(usage.getVersion());
        }

        return builder.build();
    }
}
