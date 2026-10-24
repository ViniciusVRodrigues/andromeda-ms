package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.AzureIdentityMatchService;
import andromeda.sei.ms.feature.azure.application.dto.MyAzureIdentityOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.user.domain.AzureIdentitySource;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMyAzureIdentityUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-24T03:00:12Z");

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureOrgUserRepository azureOrgUserRepository;
    @Mock private UserRepository userRepository;

    private GetMyAzureIdentityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetMyAzureIdentityUseCase(azureConnectionRepository, userRepository,
                azureOrgUserRepository, new AzureIdentityMatchService(azureOrgUserRepository, userRepository));
    }

    private void connected() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(mock(AzureConnection.class)));
    }

    private static User user(String id, String email, String azureId) {
        User u = new User(id, "org-1", email, "h", "N", "r", null, null, null, null, null, true, true, 1L, NOW, NOW);
        u.setAzureId(azureId);
        return u;
    }

    private static AzureOrgUser identity(String azureId, String name, String email) {
        return new AzureOrgUser(null, "org-1", azureId, name, email, email,
                List.of(AzureOrgUserSource.DIRECTORY), true, NOW, NOW, null);
    }

    private void loggedUser(User u) {
        when(userRepository.findByIdAndOrganizationId(u.getId(), "org-1")).thenReturn(Optional.of(u));
    }

    @Test
    void should_returnNoConnection_whenOrgHasNoConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.empty());

        MyAzureIdentityOutput out = useCase.execute("org-1", "u1");

        assertThat(out).isEqualTo(MyAzureIdentityOutput.of("NO_CONNECTION"));
        verify(azureOrgUserRepository, never()).findAllByOrganizationIdAndActiveTrue(any());
    }

    @Test
    void should_returnLinked_withSourceAndDate_fromTheOrgDirectory() {
        connected();
        User u = user("u1", "ana@x.com", "az-1");
        u.setAzureEmail("ana@x.com");
        u.setAzureIdentitySource(AzureIdentitySource.AUTO_EMAIL);
        u.setAzureIdentityLinkedAt(NOW);
        loggedUser(u);
        when(azureOrgUserRepository.findByOrganizationIdAndAzureIdAndActiveTrue("org-1", "az-1"))
                .thenReturn(Optional.of(identity("az-1", "Ana Souza", "ana@x.com")));

        MyAzureIdentityOutput out = useCase.execute("org-1", "u1");

        assertThat(out.status()).isEqualTo("LINKED");
        assertThat(out.azureEmail()).isEqualTo("ana@x.com");
        assertThat(out.displayName()).isEqualTo("Ana Souza");
        assertThat(out.source()).isEqualTo("AUTO_EMAIL");
        assertThat(out.linkedAt()).isEqualTo(NOW);
    }

    @Test
    void should_returnLinked_evenWhenTheDirectoryNoLongerHasTheIdentity() {
        connected();
        User u = user("u1", "ana@x.com", "az-1");
        u.setAzureEmail("ana@azure.com");
        u.setAzureIdentitySource(AzureIdentitySource.ADMIN);
        loggedUser(u);
        when(azureOrgUserRepository.findByOrganizationIdAndAzureIdAndActiveTrue("org-1", "az-1"))
                .thenReturn(Optional.empty());

        MyAzureIdentityOutput out = useCase.execute("org-1", "u1");

        assertThat(out.status()).isEqualTo("LINKED");
        assertThat(out.azureEmail()).isEqualTo("ana@azure.com");
        assertThat(out.displayName()).isNull();
        assertThat(out.source()).isEqualTo("ADMIN");
    }

    @Test
    void should_returnNotFound_whenNoCandidateHasTheEmail() {
        connected();
        loggedUser(user("u1", "ana@x.com", null));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(identity("az-9", "Outro", "outro@x.com")));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute("org-1", "u1")).isEqualTo(MyAzureIdentityOutput.of("NOT_FOUND"));
    }

    @Test
    void should_returnAmbiguous_whenTwoCandidatesShareTheEmail() {
        connected();
        loggedUser(user("u1", "ana@x.com", null));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(
                List.of(identity("az-1", "A1", "ana@x.com"), identity("az-2", "A2", "ana@x.com")));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute("org-1", "u1")).isEqualTo(MyAzureIdentityOutput.of("AMBIGUOUS"));
    }

    @Test
    void should_returnTaken_withoutAnyDataOfTheOwner() {
        connected();
        loggedUser(user("u1", "ana@x.com", null));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(identity("az-1", "Ana Azure", "ana@x.com")));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(user("u2", "dono@x.com", "az-1")));

        assertThat(useCase.execute("org-1", "u1")).isEqualTo(MyAzureIdentityOutput.of("TAKEN"));
    }

    @Test
    void should_notWriteAnything_andReportNotFoundWhenAFreeCandidateExistsButIsNotLinkedYet() {
        connected();
        loggedUser(user("u1", "ana@x.com", null));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(identity("az-1", "Ana", "ana@x.com")));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        MyAzureIdentityOutput out = useCase.execute("org-1", "u1");

        assertThat(out).isEqualTo(MyAzureIdentityOutput.of("NOT_FOUND"));
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).linkAutoEmailIfUnlinked(any(), any(), any(), any(), any());
        verify(azureOrgUserRepository, never()).save(any());
    }

    @Test
    void should_evaluateOnlyWithinTheCallersOrganization() {
        connected();
        loggedUser(user("u1", "ana@x.com", null));
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of());
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of());

        useCase.execute("org-1", "u1");

        verify(azureConnectionRepository).findByOrganizationIdAndActiveTrue("org-1");
        verify(userRepository).findByIdAndOrganizationId("u1", "org-1");
        verify(azureOrgUserRepository).findAllByOrganizationIdAndActiveTrue("org-1");
        verify(userRepository).findAllByOrganizationId("org-1");
    }
}
