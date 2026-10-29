package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.application.usecase.PreviewWorkItemPatternsUseCase;

import java.util.List;

/** {@code match} is {@code [start, end]} of the whole match in the branch, or null. */
public record WorkItemPatternPreviewItem(String branch, String workItemNumber, Integer patternIndex, List<Integer> match) {

    public static WorkItemPatternPreviewItem from(PreviewWorkItemPatternsUseCase.Result r) {
        return new WorkItemPatternPreviewItem(r.branch(), r.workItemNumber(), r.patternIndex(),
                r.matchStart() == null ? null : List.of(r.matchStart(), r.matchEnd()));
    }
}
