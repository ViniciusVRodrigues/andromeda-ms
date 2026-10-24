package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task 4.4: azureId (normalized GUID) → azureEmail → email, scoped to the organization. */
@ExtendWith(MockitoExtension.class)
class AzureReposAuthorResolverTest {

    private static final String ORG = "org-1";
    private static final String GUID = "d6245f20-2af8-44f4-9451-8107cb2767db";
    private static final String EMAIL = "fabrikamfiber16@hotmail.com";

    @Mock private UserRepository userRepository;
    @Mock private AzureOrgUserRepository azureOrgUserRepository;
    @Mock private andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector orgUserCollector;
    @InjectMocks private AzureReposAuthorResolver resolver;

    @Test
    void shouldServeTheAzureReposProvider() {
        assertThat(resolver.provider()).isEqualTo(VcsProvider.AZURE_REPOS);
    }

    @Test
    void shouldResolveByLinkedAzureIdAfterNormalizing() {
        User linked = user("u-1");
        when(userRepository.findByOrganizationIdAndAzureId(ORG, GUID)).thenReturn(Optional.of(linked));

        assertThat(resolver.resolveUserId(ORG, "D6245F202AF844F494518107CB2767DB", EMAIL)).isEqualTo("u-1");
        verify(userRepository, never()).findAllByOrganizationIdAndEmailIgnoringCase(anyString(), anyString());
    }

    @Test
    void shouldFallBackToAzureEmailThenLoginEmail() {
        User byEmail = user("u-2");
        when(userRepository.findByOrganizationIdAndAzureId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findAllByOrganizationIdAndAzureEmailIgnoringCase(ORG, EMAIL)).thenReturn(List.of());
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase(ORG, EMAIL)).thenReturn(List.of(byEmail));

        assertThat(resolver.resolveUserId(ORG, GUID, EMAIL)).isEqualTo("u-2");
    }

    @Test
    void shouldPreferTheAzureEmailMatch() {
        User byAzureEmail = user("u-3");
        when(userRepository.findByOrganizationIdAndAzureId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findAllByOrganizationIdAndAzureEmailIgnoringCase(ORG, EMAIL))
                .thenReturn(List.of(byAzureEmail));

        assertThat(resolver.resolveUserId(ORG, GUID, EMAIL)).isEqualTo("u-3");
        verify(userRepository, never()).findAllByOrganizationIdAndEmailIgnoringCase(anyString(), anyString());
    }

    @Test
    void shouldUseTheCachedAzureIdentityEmailWhenThePayloadHasNone() {
        User byAzureEmail = user("u-4");
        AzureOrgUser cached = mock(AzureOrgUser.class);
        when(cached.getAzureEmail()).thenReturn(EMAIL);
        when(userRepository.findByOrganizationIdAndAzureId(any(), any())).thenReturn(Optional.empty());
        when(azureOrgUserRepository.findByOrganizationIdAndAzureIdAndActiveTrue(ORG, GUID))
                .thenReturn(Optional.of(cached));
        when(userRepository.findAllByOrganizationIdAndAzureEmailIgnoringCase(ORG, EMAIL))
                .thenReturn(List.of(byAzureEmail));

        assertThat(resolver.resolveUserId(ORG, GUID)).isEqualTo("u-4");
    }

    @Test
    void shouldResolveCommitsByEmailOnly() {
        User byEmail = user("u-5");
        when(userRepository.findAllByOrganizationIdAndAzureEmailIgnoringCase(ORG, EMAIL)).thenReturn(List.of());
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase(ORG, EMAIL)).thenReturn(List.of(byEmail));

        assertThat(resolver.resolveUserId(ORG, null, EMAIL)).isEqualTo("u-5");
        verify(userRepository, never()).findByOrganizationIdAndAzureId(any(), any());
    }

    @Test
    void shouldReturnNullWhenNothingMatchesOrTheNameIsNotAnEmail() {
        lenient().when(userRepository.findByOrganizationIdAndAzureId(any(), any())).thenReturn(Optional.empty());
        lenient().when(azureOrgUserRepository.findByOrganizationIdAndAzureIdAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolveUserId(ORG, GUID, "Build\\123")).isNull();
        assertThat(resolver.resolveUserId(ORG, null, null)).isNull();
        verify(userRepository, never()).findAllByOrganizationIdAndEmailIgnoringCase(anyString(), anyString());
    }

    private static User user(String id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFlushObservedPullRequestIdentitiesInASingleBatchPerOrganization() {
        resolver.observe(ORG, "D6245F202AF844F494518107CB2767DB", "Fab", EMAIL);
        resolver.observe(ORG, GUID, "Fab again", EMAIL);
        resolver.observe(ORG, null, "No id", EMAIL);
        resolver.observe("org-2", GUID, "Other org", EMAIL);

        resolver.flushObserved(ORG);
        resolver.flushObserved(ORG); // already drained: no second call

        org.mockito.ArgumentCaptor<java.util.Collection<
                andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector.ObservedIdentity>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(orgUserCollector).collect(org.mockito.ArgumentMatchers.eq(ORG), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        var seen = captor.getValue().iterator().next();
        assertThat(seen.azureId()).isEqualTo(GUID);
        assertThat(seen.source()).isEqualTo(andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource.PR);
        verify(orgUserCollector, never()).collect(org.mockito.ArgumentMatchers.eq("org-2"), any());
    }
}
