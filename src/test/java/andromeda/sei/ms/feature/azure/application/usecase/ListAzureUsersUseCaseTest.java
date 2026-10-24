package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureUserOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAzureUsersUseCaseTest {

    private static final Instant NOW = Instant.now();

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureOrgUserRepository azureOrgUserRepository;
    @Mock private UserRepository userRepository;

    private ListAzureUsersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAzureUsersUseCase(azureConnectionRepository, azureOrgUserRepository, userRepository);
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    private void connected() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(mock(AzureConnection.class)));
    }

    private static AzureOrgUser identity(String azureId, String name, String email, AzureOrgUserSource... sources) {
        return new AzureOrgUser(null, "org-1", azureId, name, email, email, List.of(sources), true, NOW, NOW, null);
    }

    private static User user(String id, String azureId) {
        User u = new User(id, "org-1", id + "@x.com", "h", "N", "r", null, null, null, null, null, true, true, 1L, NOW, NOW);
        u.setAzureId(azureId);
        return u;
    }

    @Test
    void should_throwConnectionNotFound_whenOrgHasNoConnection_andNotReadDirectory() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute()).isInstanceOf(AzureConnectionNotFoundException.class);
        verify(azureOrgUserRepository, never()).findAllByOrganizationIdAndActiveTrue("org-1");
    }

    @Test
    void should_returnEmptyList_whenDirectoryNotSyncedYet() {
        connected();
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of());
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute()).isEmpty();
    }

    @Test
    void should_sortByDisplayNameIgnoringCase_andPlaceNullNamesFirstWithoutFailing() {
        connected();
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(
                identity("a3", "bruno", "b@x.com", AzureOrgUserSource.DIRECTORY),
                identity("a1", "Alvaro", "al@x.com", AzureOrgUserSource.TEAM),
                identity("a4", "Ze", "z@x.com", AzureOrgUserSource.PR),
                identity("a2", "ana", "a@x.com", AzureOrgUserSource.WORKITEM),
                identity("a0", null, "n@x.com", AzureOrgUserSource.WORKITEM)));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute()).extracting(AzureUserOutput::azureId)
                .containsExactly("a0", "a1", "a2", "a3", "a4");
    }

    @Test
    void should_deduplicateByAzureId() {
        connected();
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(
                identity("a1", "Ana", "a@x.com", AzureOrgUserSource.DIRECTORY),
                identity("a1", "Ana", "a@x.com", AzureOrgUserSource.TEAM)));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute()).hasSize(1);
    }

    @Test
    void should_carrySourcesAndLinkedUserId_andOnlyReadTheCallersOrg() {
        connected();
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(
                identity("a1", "Ana", "a@x.com", AzureOrgUserSource.DIRECTORY, AzureOrgUserSource.WORKITEM),
                identity("a2", "Pedro", "p@x.com", AzureOrgUserSource.WORKITEM)));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(user("u1", "a1"), user("u2", null)));

        List<AzureUserOutput> result = useCase.execute();

        assertThat(result.get(0).azureEmail()).isEqualTo("a@x.com");
        assertThat(result.get(0).sources()).containsExactlyInAnyOrder("DIRECTORY", "WORKITEM");
        assertThat(result.get(0).linkedUserId()).isEqualTo("u1");
        assertThat(result.get(1).linkedUserId()).isNull();
        verify(azureOrgUserRepository).findAllByOrganizationIdAndActiveTrue("org-1");
        verify(userRepository).findAllByOrganizationId("org-1");
        verify(azureConnectionRepository).findByOrganizationIdAndActiveTrue("org-1");
    }
}
