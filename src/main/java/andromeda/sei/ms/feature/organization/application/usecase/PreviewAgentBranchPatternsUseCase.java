package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.exception.AgentBranchPatternInvalidException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dry-run of a pattern list against sample branches, with the same matcher (cut and deadline) the
 * attribution uses. Nothing is stored. Null or empty patterns preview the defaults.
 */
@Component
public class PreviewAgentBranchPatternsUseCase {

    /** {@code workItemNumber}, {@code patternIndex} and {@code matchStart/End} are null when nothing matched. */
    public record Result(String branch, String workItemNumber, Integer patternIndex, Integer matchStart, Integer matchEnd) {}

    public List<Result> execute(List<String> branchPatterns, List<String> branches) {
        List<BranchPatternMatcher.Error> errors = BranchPatternMatcher.validateAll(branchPatterns);
        if (!errors.isEmpty()) throw new AgentBranchPatternInvalidException(errors);
        BranchPatternMatcher matcher = BranchPatternMatcher.of(branchPatterns);
        return (branches == null ? List.<String>of() : branches).stream()
                .map(branch -> matcher.matchDetailed(branch)
                        .map(h -> new Result(branch, h.group(), h.patternIndex(), h.start(), h.end()))
                        .orElseGet(() -> new Result(branch, null, null, null, null)))
                .toList();
    }
}
