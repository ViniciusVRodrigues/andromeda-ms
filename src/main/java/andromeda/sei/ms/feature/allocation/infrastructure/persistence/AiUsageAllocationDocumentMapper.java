package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiUsageAllocationDocumentMapper {

    public AiUsageAllocation toDomain(AiUsageAllocationDocument doc) {
        List<AllocationSplit> splits = doc.getSplits() == null ? List.of() :
                doc.getSplits().stream()
                        .map(s -> new AllocationSplit(s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage()))
                        .toList();

        AiUsageAllocation allocation = new AiUsageAllocation(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getDay(),
                doc.getAiUsageIds() != null ? doc.getAiUsageIds() : List.of(),
                splits,
                doc.getStatus(),
                doc.getAssignedBy(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getSuggestionSource()
        );
        if (doc.getAgent() != null) {
            allocation.setAgent(new AiUsageAllocation.AgentInfo(doc.getAgent().getResolvedBy(),
                    doc.getAgent().getRepositories() == null ? List.of() : doc.getAgent().getRepositories(),
                    doc.getAgent().getBranches() == null ? List.of() : doc.getAgent().getBranches()));
        }
        allocation.setMovedFromAgent(doc.isMovedFromAgent());
        return allocation;
    }

    public AiUsageAllocationDocument toDocument(AiUsageAllocation allocation) {
        List<AllocationSplitDocument> splitDocs = allocation.getSplits() == null ? List.of() :
                allocation.getSplits().stream()
                        .map(s -> AllocationSplitDocument.builder()
                                .workItemId(s.getWorkItemId())
                                .workItemTitle(s.getWorkItemTitle())
                                .percentage(s.getPercentage())
                                .build())
                        .toList();

        return AiUsageAllocationDocument.builder()
                .id(allocation.getId())
                .organizationId(allocation.getOrganizationId())
                .userId(allocation.getUserId())
                .day(allocation.getDay())
                .aiUsageIds(allocation.getAiUsageIds())
                .splits(splitDocs)
                .status(allocation.getStatus())
                .assignedBy(allocation.getAssignedBy())
                .suggestionSource(allocation.getSuggestionSource())
                .agent(allocation.getAgent() == null ? null : new AiUsageAllocationDocument.AgentInfoDocument(
                        allocation.getAgent().resolvedBy(), allocation.getAgent().repositories(),
                        allocation.getAgent().branches()))
                .movedFromAgent(allocation.isMovedFromAgent())
                .active(allocation.isActive())
                .version(allocation.getVersion())
                .createdAt(allocation.getCreatedAt())
                .updatedAt(allocation.getUpdatedAt())
                .build();
    }
}
