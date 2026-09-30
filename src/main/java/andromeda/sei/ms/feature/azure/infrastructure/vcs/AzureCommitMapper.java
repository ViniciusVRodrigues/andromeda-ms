package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.infrastructure.client.AzCommitDto;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;

/**
 * Azure DevOps {@code GitCommitRef} → neutral commit. The Git author carries only name, email and
 * date (design D4): there is no identity id, so the author resolves by email only and the name is
 * kept as {@code authorLogin}.
 */
public final class AzureCommitMapper {

    private AzureCommitMapper() {}

    public static VcsRemoteCommit map(AzCommitDto dto) {
        AzCommitDto.GitUser author = dto.author();
        return new VcsRemoteCommit(dto.commitId(), dto.comment(),
                author == null ? null : author.name(),
                null,
                author == null ? null : AzureDates.parse(author.date()),
                author == null ? null : author.email());
    }
}
