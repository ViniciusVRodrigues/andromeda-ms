package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.AgentSettingsOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.AgentBranchPatternInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAgentSettingsUseCaseTest {

    @Mock private OrganizationRepository repository;

    private Organization org() {
        return new Organization("org-1", "Org", "org", null, null, List.of(), List.of(), null, true, 1L,
                Instant.now(), Instant.now());
    }

    @Test
    void should_storeAndExposePatterns() {
        Organization o = org();
        when(repository.findById("org-1")).thenReturn(Optional.of(o));
        when(repository.save(o)).thenReturn(o);

        AgentSettingsOutput out = new UpdateAgentSettingsUseCase(repository).execute("org-1", List.of("^wi-(\\d+)"));

        assertThat(out.branchPatterns()).containsExactly("^wi-(\\d+)");
        assertThat(out.usingDefaults()).isFalse();
    }

    @Test
    void should_restoreDefaults_when_emptyOrNull() {
        Organization o = org();
        o.setAgentSettings(new andromeda.sei.ms.feature.organization.domain.AgentSettings(List.of("x(\\d+)")));
        when(repository.findById("org-1")).thenReturn(Optional.of(o));
        when(repository.save(o)).thenReturn(o);

        AgentSettingsOutput out = new UpdateAgentSettingsUseCase(repository).execute("org-1", List.of());

        assertThat(out.usingDefaults()).isTrue();
        assertThat(out.branchPatterns()).isEqualTo(out.defaults());
    }

    @Test
    void should_rejectWithIndex_and_notSave() {
        assertThatThrownBy(() -> new UpdateAgentSettingsUseCase(repository)
                .execute("org-1", List.of("ok(\\d+)", "no-group")))
                .isInstanceOf(AgentBranchPatternInvalidException.class)
                .hasMessageContaining("index 1");
        verify(repository, never()).save(any());
    }
}
