package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.application.usecase.PreviewAgentBranchPatternsUseCase;

import java.util.List;

/** {@code match} is {@code [start, end]} of the whole match in the branch, or null. */
public record AgentBranchPreviewItem(String branch, String workItemNumber, Integer patternIndex, List<Integer> match) {

    public static AgentBranchPreviewItem from(PreviewAgentBranchPatternsUseCase.Result r) {
        return new AgentBranchPreviewItem(r.branch(), r.workItemNumber(), r.patternIndex(),
                r.matchStart() == null ? null : List.of(r.matchStart(), r.matchEnd()));
    }
}
