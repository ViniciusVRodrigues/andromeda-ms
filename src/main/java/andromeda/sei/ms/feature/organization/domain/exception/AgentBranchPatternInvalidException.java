package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

import java.util.List;
import java.util.stream.Collectors;

/** 422 {@code AGENT_BRANCH_PATTERN_INVALID}: carries every rejected entry (index, code, message). */
public class AgentBranchPatternInvalidException extends BusinessRuleException {

    /** One rejected entry; {@code code} is a {@link BranchPatternMatcher.ErrorCode} name. */
    public record Item(int index, String code, String message) {}

    private final List<Item> errors;

    public AgentBranchPatternInvalidException(List<BranchPatternMatcher.Error> errors) {
        super("AGENT_BRANCH_PATTERN_INVALID", "Invalid branch pattern: " + errors.stream()
                .map(e -> "index " + e.index() + " (" + e.reason() + ")")
                .collect(Collectors.joining("; ")));
        this.errors = errors.stream().map(e -> new Item(e.index(), e.code().name(), e.reason())).toList();
    }

    public List<Item> getErrors() { return errors; }
}
