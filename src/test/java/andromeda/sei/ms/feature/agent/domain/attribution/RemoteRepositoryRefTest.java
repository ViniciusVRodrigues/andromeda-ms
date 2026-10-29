package andromeda.sei.ms.feature.agent.domain.attribution;

import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteRepositoryRefTest {

    @ParameterizedTest
    @ValueSource(strings = {"github.com/Owner/Repo", "https://github.com/Owner/Repo.git",
            "https://user:pw@github.com/owner/repo/", "git@github.com:Owner/Repo.git", "ssh://git@github.com/owner/repo.git"})
    void should_parseGithubForms(String raw) {
        var ref = RemoteRepositoryRef.parse(raw).orElseThrow();
        assertThat(ref.kind()).isEqualTo(Kind.GITHUB);
        assertThat(ref.name()).isEqualTo("owner/repo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev.azure.com/Org/Proj/Repo", "https://dev.azure.com/org/proj/_git/repo",
            "https://org@dev.azure.com/org/proj/_git/Repo", "https://org.visualstudio.com/proj/_git/repo",
            "https://org.visualstudio.com/DefaultCollection/proj/_git/repo", "git@ssh.dev.azure.com:v3/org/proj/repo"})
    void should_parseAzureForms(String raw) {
        var ref = RemoteRepositoryRef.parse(raw).orElseThrow();
        assertThat(ref.kind()).isEqualTo(Kind.AZURE_REPOS);
        assertThat(ref.name()).isEqualTo("repo");
        assertThat(ref.azureOrganization()).isEqualTo("org");
        assertThat(ref.azureProject()).isEqualTo("proj");
    }

    @Test
    void should_decodeAzureNames() {
        assertThat(RemoteRepositoryRef.parse("https://dev.azure.com/org/proj/_git/My%20Repo").orElseThrow().name())
                .isEqualTo("my repo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "gitlab.com/o/r", "github.com/onlyowner", "github.com/o/r/extra", "lixo"})
    void should_returnEmpty_when_unknown(String raw) {
        assertThat(RemoteRepositoryRef.parse(raw)).isEmpty();
    }

    @Test
    void should_returnEmpty_when_null() {
        assertThat(RemoteRepositoryRef.parse(null)).isEmpty();
    }
}
