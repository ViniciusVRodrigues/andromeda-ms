package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

import java.util.List;
import java.util.stream.Collectors;

/** 422 {@code WORK_ITEM_PATTERN_INVALID}: carries every rejected entry (index, code, message). */
public class WorkItemPatternInvalidException extends BusinessRuleException {

    /** One rejected entry; {@code code} is a {@link BranchPatternMatcher.ErrorCode} name. */
    public record Item(int index, String code, String message) {}

    private final List<Item> errors;

    public WorkItemPatternInvalidException(List<BranchPatternMatcher.Error> errors) {
        super("WORK_ITEM_PATTERN_INVALID", "Invalid work item pattern: " + errors.stream()
                .map(e -> "index " + e.index() + " (" + e.reason() + ")")
                .collect(Collectors.joining("; ")));
        this.errors = errors.stream().map(e -> new Item(e.index(), e.code().name(), e.reason())).toList();
    }

    public List<Item> getErrors() { return errors; }
}
