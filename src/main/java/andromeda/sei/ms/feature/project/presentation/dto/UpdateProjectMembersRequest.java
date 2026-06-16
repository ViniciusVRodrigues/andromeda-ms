package andromeda.sei.ms.feature.project.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateProjectMembersRequest(
        @NotNull List<String> memberIds
) {}
