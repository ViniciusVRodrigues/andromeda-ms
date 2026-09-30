package andromeda.sei.ms.feature.vcs.presentation.dto;

import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;

import java.util.List;

/** Repositories of the project (any provider) plus the freshness of the project's data (on-demand-refresh). */
public record ProjectRepositoriesResponse(List<ProjectRepositoryOutput> repos, DataFreshness dataFreshness) {}
