package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.attribution.PortableRegex;
import andromeda.sei.ms.feature.organization.application.usecase.GetWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The organization's branch patterns for the agent, in evaluation order, with a JS-portable form (design 5.3). */
@Component
@RequiredArgsConstructor
public class GetAgentWorkItemPatternsUseCase {

    public record Portable(String source, String flags) {}

    public record PatternOutput(String regex, String preset, Portable portable) {}

    public record Output(boolean enabled, Instant updatedAt, List<PatternOutput> patterns) {}

    private final GetWorkItemPatternsUseCase getPatterns;

    public Output execute(AgentPrincipal principal) {
        WorkItemPatterns wp = getPatterns.execute(principal.organizationId());
        List<PatternOutput> patterns = wp.patterns().stream()
                .map(e -> new PatternOutput(e.regex(), e.preset(), PortableRegex.convert(e.regex())
                        .map(p -> new Portable(p.source(), p.flags())).orElse(null)))
                .toList();
        return new Output(wp.enabled(), wp.updatedAt(), patterns);
    }
}
