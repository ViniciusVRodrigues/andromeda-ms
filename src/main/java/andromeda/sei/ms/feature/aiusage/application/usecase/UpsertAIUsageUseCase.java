package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpsertAIUsageUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final AutoAssignAllocationUseCase autoAssignAllocationUseCase;

    public void execute(List<AIUsageEntry> entries, String organizationId, String userId) {
        Instant now = Instant.now();

        List<AIUsage> usages = entries.stream()
                .map(e -> new AIUsage(
                        null,
                        organizationId,
                        userId,
                        e.gitHubLogin(),
                        e.datasetId(),
                        e.day(),
                        e.model(),
                        e.workspaceId(),
                        e.workspaceName(),
                        e.machineId(),
                        e.machineName(),
                        e.inputTokens(),
                        e.outputTokens(),
                        e.interactions(),
                        now,
                        true,
                        null,
                        now,
                        now
                ))
                .collect(Collectors.toList());

        usages.forEach(aiUsageRepository::upsert);

        // Trigger auto-assignment for each distinct day in the batch
        usages.stream()
                .map(AIUsage::getDay)
                .distinct()
                .forEach(day -> autoAssignAllocationUseCase.execute(organizationId, userId, day));
    }
}
