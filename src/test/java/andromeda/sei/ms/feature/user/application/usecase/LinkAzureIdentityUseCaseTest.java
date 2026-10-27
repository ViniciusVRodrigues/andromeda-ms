package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.domain.AzureIdentitySource;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.VcsIdentityLinked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkAzureIdentityUseCaseTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private User user() {
        return new User("u1", "org", "ana@acme.com", "h", "Ana", "r", null, null, null, null,
                null, true, true, 1L, T0, T0);
    }

    @Test
    void should_recordAdminSourceAndLinkedAt_whenLinking() {
        User user = user();
        when(userRepository.findByIdAndOrganizationId("u1", "org")).thenReturn(Optional.of(user));
        when(userRepository.findByOrganizationIdAndAzureId("org", "az-1")).thenReturn(Optional.empty());
        Instant before = Instant.now();

        new LinkAzureIdentityUseCase(userRepository, workItemRepository, eventPublisher).execute("org", "u1", "az-1", "a@az.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getAzureId()).isEqualTo("az-1");
        assertThat(saved.getValue().getAzureIdentitySource()).isEqualTo(AzureIdentitySource.ADMIN);
        assertThat(saved.getValue().getAzureIdentityLinkedAt()).isAfterOrEqualTo(before);
        verify(eventPublisher).publishEvent(new VcsIdentityLinked("org", "u1"));
    }

    @Test
    void should_clearSourceAndLinkedAt_whenUnlinking() {
        User user = user();
        user.setAzureId("az-1");
        user.setAzureIdentitySource(AzureIdentitySource.AUTO_EMAIL);
        user.setAzureIdentityLinkedAt(T0);
        when(userRepository.findByIdAndOrganizationId("u1", "org")).thenReturn(Optional.of(user));

        new LinkAzureIdentityUseCase(userRepository, workItemRepository, eventPublisher).execute("org", "u1", null, null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getAzureId()).isNull();
        assertThat(saved.getValue().getAzureIdentitySource()).isNull();
        assertThat(saved.getValue().getAzureIdentityLinkedAt()).isNull();
        verifyNoInteractions(eventPublisher);
    }
}
