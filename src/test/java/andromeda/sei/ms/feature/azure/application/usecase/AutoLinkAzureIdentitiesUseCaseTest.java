package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.AzureIdentityMatchService;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoLinkAzureIdentitiesUseCaseTest {

    private static final Instant NOW = Instant.now();

    @Mock private UserRepository userRepository;
    @Mock private AzureOrgUserRepository azureOrgUserRepository;
    @Mock private WorkItemRepository workItemRepository;

    private AutoLinkAzureIdentitiesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AutoLinkAzureIdentitiesUseCase(userRepository, azureOrgUserRepository,
                new AzureIdentityMatchService(azureOrgUserRepository, userRepository), workItemRepository);
    }

    private static User user(String id, String org, String email, String azureId) {
        User u = new User(id, org, email, "h", "N", "r", null, null, null, null, null, true, true, 1L, NOW, NOW);
        u.setAzureId(azureId);
        return u;
    }

    private static AzureOrgUser identity(String org, String azureId, String email) {
        return new AzureOrgUser(null, org, azureId, "N", email, email, List.of(AzureOrgUserSource.DIRECTORY),
                true, NOW, NOW, null);
    }

    @Test
    void should_linkAndBackfill_whenSingleFreeIdentityMatches() {
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(user("u1", "org-1", "Pedro@x.com", null)));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(identity("org-1", "az-1", "pedro@x.com")));
        when(userRepository.linkAutoEmailIfUnlinked(eq("org-1"), eq("u1"), eq("az-1"), eq("pedro@x.com"), any()))
                .thenReturn(true);

        assertThat(useCase.executeForOrganization("org-1")).isEqualTo(1);

        verify(workItemRepository).updateAssigneeUserIdByExternalIdOrEmail("org-1", "az-1", "Pedro@x.com", "u1");
    }

    @Test
    void should_doNothing_whenConditionalWriteLosesRaceWithAdmin() {
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(user("u1", "org-1", "p@x.com", null)));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(identity("org-1", "az-1", "p@x.com")));
        when(userRepository.linkAutoEmailIfUnlinked(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(false);

        assertThat(useCase.executeForOrganization("org-1")).isZero();

        verify(workItemRepository, never()).updateAssigneeUserIdByExternalIdOrEmail(any(), any(), any(), any());
    }

    @Test
    void should_neverTouchUsersThatAlreadyHaveAzureId() {
        when(userRepository.findAllByOrganizationId("org-1"))
                .thenReturn(List.of(user("u1", "org-1", "p@x.com", "az-admin")));

        assertThat(useCase.executeForOrganization("org-1")).isZero();

        verify(userRepository, never()).linkAutoEmailIfUnlinked(any(), any(), any(), any(), any());
    }

    @Test
    void should_notLink_whenAmbiguousOrTaken() {
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(
                user("u1", "org-1", "dup@x.com", null),
                user("u2", "org-1", "taken@x.com", null),
                user("u3", "org-1", "owner@x.com", "az-taken")));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(
                identity("org-1", "az-a", "dup@x.com"), identity("org-1", "az-b", "dup@x.com"),
                identity("org-1", "az-taken", "taken@x.com")));

        assertThat(useCase.executeForOrganization("org-1")).isZero();

        verify(userRepository, never()).linkAutoEmailIfUnlinked(any(), any(), any(), any(), any());
    }

    @Test
    void should_scopeEverythingToTheGivenOrganization() {
        when(userRepository.findAllByOrganizationId("org-2")).thenReturn(List.of(user("u9", "org-2", "p@x.com", null)));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-2")).thenReturn(List.of());

        assertThat(useCase.executeForOrganization("org-2")).isZero();

        verify(azureOrgUserRepository).findAllByOrganizationIdAndActiveTrue("org-2");
        verify(azureOrgUserRepository, never()).findAllByOrganizationIdAndActiveTrue("org-1");
        verify(userRepository, never()).findAllByOrganizationId("org-1");
    }

    @Test
    void should_continueWithOtherUsers_whenOneLinkThrows() {
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(
                user("u1", "org-1", "a@x.com", null), user("u2", "org-1", "b@x.com", null)));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(
                identity("org-1", "az-a", "a@x.com"), identity("org-1", "az-b", "b@x.com")));
        when(userRepository.linkAutoEmailIfUnlinked(eq("org-1"), eq("u1"), any(), any(), any()))
                .thenThrow(new RuntimeException("duplicate key"));
        when(userRepository.linkAutoEmailIfUnlinked(eq("org-1"), eq("u2"), any(), any(), any())).thenReturn(true);

        assertThat(useCase.executeForOrganization("org-1")).isEqualTo(1);
    }

    @Test
    void should_linkSingleUser_onAccountCompletion() {
        when(userRepository.findByIdAndOrganizationId("u1", "org-1"))
                .thenReturn(Optional.of(user("u1", "org-1", "p@x.com", null)));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(identity("org-1", "az-1", "p@x.com")));
        when(userRepository.linkAutoEmailIfUnlinked(eq("org-1"), eq("u1"), eq("az-1"), any(), any())).thenReturn(true);

        assertThat(useCase.executeForUser("org-1", "u1")).isTrue();
    }
}
