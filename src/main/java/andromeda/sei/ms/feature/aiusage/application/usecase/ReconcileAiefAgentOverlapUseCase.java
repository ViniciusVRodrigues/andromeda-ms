package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.AiefEditorTools;
import andromeda.sei.ms.feature.aiusage.application.AiefOverlapRule;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.AgentCoverage;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.Candidate;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the extension rows that the agent also reported turned off (active=false, supersededBy=AGENT),
 * so no total counts the same usage twice (extensao-fora-dos-totais). Runs organization by organization;
 * a failure in one is logged and does not stop the others.
 */
@Slf4j
@Service
public class ReconcileAiefAgentOverlapUseCase {

    public record Result(long deactivated, long reactivated) {}

    private final OrganizationRepository organizations;
    private final AiefOverlapRepository repository;
    private final AiefEditorTools tools;

    public ReconcileAiefAgentOverlapUseCase(OrganizationRepository organizations, AiefOverlapRepository repository,
                                            AiefEditorTools tools) {
        this.organizations = organizations;
        this.repository = repository;
        this.tools = tools;
    }

    /** @param fromDay first day to look at; null = whole history */
    public Result execute(LocalDate fromDay) {
        long off = 0;
        long on = 0;
        for (Organization org : organizations.findAllActive()) {
            try {
                Result r = executeFor(org.getId(), fromDay);
                off += r.deactivated();
                on += r.reactivated();
            } catch (Exception e) {
                log.error("Reconciliacao extensao x agente falhou na organizacao {}", org.getId(), e);
            }
        }
        if (off > 0 || on > 0) {
            log.info("Extensao x agente: {} linha(s) desligada(s), {} religada(s)", off, on);
        }
        return new Result(off, on);
    }

    private Result executeFor(String organizationId, LocalDate fromDay) {
        List<Candidate> candidates = repository.findCandidates(organizationId, tools.editors(), fromDay);
        if (candidates.isEmpty()) return new Result(0, 0);
        Set<String> agentTools = new HashSet<>();
        candidates.forEach(c -> {
            String t = tools.toolOf(c.editor());
            if (t != null) agentTools.add(t);
        });
        Set<AgentCoverage> coverage = repository.findAgentCoverage(organizationId, candidates, agentTools);
        AiefOverlapRule.Decision decision = AiefOverlapRule.decide(candidates, coverage, tools);
        long off = decision.toDeactivate().isEmpty() ? 0
                : repository.deactivate(organizationId, decision.toDeactivate());
        long on = decision.toReactivate().isEmpty() ? 0
                : repository.reactivate(organizationId, decision.toReactivate());
        return new Result(off, on);
    }
}
