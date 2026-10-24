package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector.ObservedIdentity;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureOrgUserCollectorAdapterTest {

    private static final String ORG = "org-1";
    private static final String PEDRO = "11111111-2222-3333-4444-555555555555";
    private static final String ANA = "aaaaaaaa-2222-3333-4444-555555555555";

    @Mock private AzureOrgUserRepository repository;
    @InjectMocks private AzureOrgUserCollectorAdapter adapter;

    @SuppressWarnings("unchecked")
    private List<AzureOrgUser> saved() {
        ArgumentCaptor<List<AzureOrgUser>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldCreateRowWithWorkItemSource_forPersonOutsideTheTeams() {
        when(repository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of());

        adapter.collect(ORG, List.of(new ObservedIdentity(PEDRO.toUpperCase(), "Pedro", "pedro@x.com",
                AzureOrgUserSource.WORKITEM)));

        List<AzureOrgUser> users = saved();
        assertThat(users).hasSize(1);
        AzureOrgUser pedro = users.get(0);
        assertThat(pedro.getOrganizationId()).isEqualTo(ORG);
        assertThat(pedro.getAzureId()).isEqualTo(PEDRO);
        assertThat(pedro.getSources()).containsExactly(AzureOrgUserSource.WORKITEM);
        assertThat(pedro.getAzureEmail()).isEqualTo("pedro@x.com");
        assertThat(pedro.isActive()).isTrue();
    }

    @Test
    void shouldNotOverwriteDirectoryNameButAccumulateSource() {
        Instant now = Instant.now();
        AzureOrgUser ana = new AzureOrgUser("row-1", ORG, ANA, "Ana Dir", "ana@x.com", "ana@x.com",
                List.of(AzureOrgUserSource.DIRECTORY), true, now, now, 2L);
        when(repository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of(ana));

        adapter.collect(ORG, List.of(new ObservedIdentity(ANA, "ana wi", "ana.wi@x.com", AzureOrgUserSource.WORKITEM)));

        AzureOrgUser result = saved().get(0);
        assertThat(result.getId()).isEqualTo("row-1");
        assertThat(result.getDisplayName()).isEqualTo("Ana Dir");
        assertThat(result.getAzureEmail()).isEqualTo("ana@x.com");
        assertThat(result.getSources())
                .containsExactlyInAnyOrder(AzureOrgUserSource.DIRECTORY, AzureOrgUserSource.WORKITEM);
    }

    @Test
    void shouldIgnoreGroupsAndIdentitiesWithoutId() {
        when(repository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of());

        adapter.collect(ORG, List.of(
                new ObservedIdentity(ANA, "Team", "[Proj]\\Team", AzureOrgUserSource.PR),
                new ObservedIdentity(null, "Nobody", "n@x.com", AzureOrgUserSource.PR),
                new ObservedIdentity(" ", "Blank", "b@x.com", AzureOrgUserSource.PR)));

        verify(repository, never()).saveAll(any());
    }

    @Test
    void shouldOnlyTouchTheGivenOrganization() {
        when(repository.findAllByOrganizationIdAndActiveTrue("org-2")).thenReturn(List.of());

        adapter.collect("org-2", List.of(new ObservedIdentity(ANA, "Ana", "ana@x.com", AzureOrgUserSource.PR)));

        assertThat(saved()).allMatch(u -> u.getOrganizationId().equals("org-2"));
        verify(repository).findAllByOrganizationIdAndActiveTrue("org-2");
        verifyNoMoreInteractions(repository);
    }
}
