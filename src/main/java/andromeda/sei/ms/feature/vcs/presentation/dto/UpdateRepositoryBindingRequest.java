package andromeda.sei.ms.feature.vcs.presentation.dto;

import java.util.List;

/** PATCH of a binding: every field is optional and an absent one is left as it is. */
public record UpdateRepositoryBindingRequest(Boolean syncEnabled, List<String> branches, String productionBranch,
                                             Boolean filterPullRequestsByBranch) {}
