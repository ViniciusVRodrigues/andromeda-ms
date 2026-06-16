package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpsertAIUsageUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final AutoAssignAllocationUseCase autoAssignAllocationUseCase;
    private final CalculateAiCostUseCase calculateAiCostUseCase;
    private final OrganizationRepository organizationRepository;

    public void execute(List<AIUsageEntry> entries, String organizationId, String userId) {
        Instant now = Instant.now();

        List<AiPriceEntry> prices = organizationRepository.findById(organizationId)
                .map(org -> org.getAiPrices() != null ? org.getAiPrices() : List.<AiPriceEntry>of())
                .orElse(List.of());

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
                        e.editor(),
                        null,
                        null,
                        now,
                        true,
                        null,
                        now,
                        now
                ))
                .collect(Collectors.toList());

        for (AIUsage usage : usages) {
            aiUsageRepository.upsert(usage);
            BigDecimal cost = calculateAiCostUseCase.execute(usage, prices);
            if (cost != null) {
                aiUsageRepository.updateCost(
                        usage.getOrganizationId(), usage.getUserId(), usage.getDatasetId(),
                        usage.getDay(), usage.getModel(), usage.getWorkspaceId(), usage.getMachineId(),
                        cost);
                log.info("ai-usage upserted: org={} user={} day={} model={} inputTokens={} outputTokens={} cost={}",
                        usage.getOrganizationId(), usage.getUserId(), usage.getDay(),
                        usage.getModel(), usage.getInputTokens(), usage.getOutputTokens(), cost);
            } else {
                log.info("ai-usage upserted: org={} user={} day={} model={} inputTokens={} outputTokens={} cost=no price match",
                        usage.getOrganizationId(), usage.getUserId(), usage.getDay(),
                        usage.getModel(), usage.getInputTokens(), usage.getOutputTokens());
            }
        }

        log.info("ai-usage batch complete: org={} user={} entries={}",
                organizationId, userId, usages.size());

        // Trigger auto-assignment for each distinct day in the batch
        usages.stream()
                .map(AIUsage::getDay)
                .distinct()
                .forEach(day -> autoAssignAllocationUseCase.execute(organizationId, userId, day));
    }
}
