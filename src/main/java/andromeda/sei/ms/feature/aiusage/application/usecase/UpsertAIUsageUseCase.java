package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AiefCoexistence;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UpsertAIUsageUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final AutoAssignAllocationUseCase autoAssignAllocationUseCase;
    private final CalculateAiCostUseCase calculateAiCostUseCase;
    private final OrganizationRepository organizationRepository;
    private final AiefCoexistence aiefCoexistence;

    public UpsertAIUsageUseCase(AIUsageRepository aiUsageRepository, AutoAssignAllocationUseCase autoAssignAllocationUseCase,
                                CalculateAiCostUseCase calculateAiCostUseCase, OrganizationRepository organizationRepository,
                                AiefCoexistence aiefCoexistence) {
        this.aiUsageRepository = aiUsageRepository;
        this.autoAssignAllocationUseCase = autoAssignAllocationUseCase;
        this.calculateAiCostUseCase = calculateAiCostUseCase;
        this.organizationRepository = organizationRepository;
        this.aiefCoexistence = aiefCoexistence;
    }

    /**
     * Stores the entries, except those of days that belong to the local agent of the user (design 6:
     * the AIEF is ignored from the cutover day on, so the usage is not counted twice).
     *
     * @return how many entries were ignored (not stored)
     */
    public int execute(List<AIUsageEntry> entries, String organizationId, String userId) {
        Instant now = Instant.now();

        Map<String, AiefCoexistence.Verdict> verdicts = new HashMap<>();
        List<AIUsageEntry> kept = new ArrayList<>();
        Set<AIUsageEntry> flagged = new HashSet<>();
        int ignoredCount = 0;
        for (AIUsageEntry e : entries) {
            AiefCoexistence.Verdict v = verdicts.computeIfAbsent(e.day() + "|" + e.editor() + "|" + e.model(),
                    k -> aiefCoexistence.verdict(organizationId, userId, e.day(), e.editor(), e.model()));
            if (v == null) v = AiefCoexistence.Verdict.STORE;
            if (v == AiefCoexistence.Verdict.IGNORE) {
                ignoredCount++;
                continue;
            }
            kept.add(e);
            if (v == AiefCoexistence.Verdict.STORE_FLAGGED) flagged.add(e);
        }
        if (ignoredCount > 0) {
            log.info("ai-usage ignored (agent already sent the tool): org={} user={} entries={}", organizationId,
                    userId, ignoredCount);
        }
        entries = kept;

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
        for (int i = 0; i < usages.size(); i++) {
            if (flagged.contains(entries.get(i))) usages.get(i).setPossibleDuplicate(true);
        }

        for (AIUsage usage : usages) {
            aiUsageRepository.upsert(usage);
            BigDecimal cost = calculateAiCostUseCase.execute(usage, prices);
            aiUsageRepository.updateCost(
                    usage.getOrganizationId(), usage.getUserId(), usage.getDatasetId(),
                    usage.getDay(), usage.getModel(), usage.getWorkspaceId(), usage.getMachineId(),
                    cost);
            log.info("ai-usage upserted: org={} user={} day={} model={} inputTokens={} outputTokens={} cost={}",
                    usage.getOrganizationId(), usage.getUserId(), usage.getDay(),
                    usage.getModel(), usage.getInputTokens(), usage.getOutputTokens(),
                    cost != null ? cost : "no price match");
        }

        log.info("ai-usage batch complete: org={} user={} entries={}",
                organizationId, userId, usages.size());

        // Trigger auto-assignment for each distinct day in the batch
        usages.stream()
                .map(AIUsage::getDay)
                .distinct()
                .forEach(day -> autoAssignAllocationUseCase.execute(organizationId, userId, day));
        return ignoredCount;
    }
}
