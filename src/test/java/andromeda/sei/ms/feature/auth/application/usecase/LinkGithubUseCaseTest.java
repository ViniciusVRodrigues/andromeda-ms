package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkGithubUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void should_saveGithubId_andPublishVcsIdentityLinked() {
        User user = new User("u1", "org", "a@x.com", "h", "Ana", "r", null, null, null, null, null, true, true, 1L,
                Instant.now(), Instant.now());
        when(userRepository.findByIdAndOrganizationId("u1", "org")).thenReturn(Optional.of(user));

        new LinkGithubUseCase(userRepository, eventPublisher).execute("u1", "org", "58421");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getGithubId()).isEqualTo("58421");
        verify(eventPublisher).publishEvent(new VcsIdentityLinked("org", "u1"));
    }

    @Test
    void should_notPublish_whenUserNotFound() {
        when(userRepository.findByIdAndOrganizationId("u1", "org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new LinkGithubUseCase(userRepository, eventPublisher).execute("u1", "org", "1"))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }
}
