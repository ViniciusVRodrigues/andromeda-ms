package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAiefCutover implements AiefCutover {

    private final AiUsageAllocationRepository allocations;

    @Override
    public LocalDate cutoverDay(String organizationId, String userId, String timezone, Instant approvedAt) {
        LocalDate today = approvedAt.atZone(ZoneId.of(timezone)).toLocalDate();
        // a day with a validated AIEF allocation is left alone: the agent starts counting tomorrow
        return allocations.existsValidatedByOrganizationIdAndUserIdAndDay(organizationId, userId, today)
                ? today.plusDays(1) : today;
    }
}
