package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.application.dto.CreateUserInput;
import andromeda.sei.ms.feature.user.application.dto.CreateUserOutput;
import andromeda.sei.ms.feature.user.application.dto.UpdateUserInput;
import andromeda.sei.ms.feature.user.application.dto.UserOutput;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserEmailConflictException;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private CreateUserUseCase createUserUseCase;

    @InjectMocks
    private UpdateUserUseCase updateUserUseCase;

    @InjectMocks
    private DeactivateUserUseCase deactivateUserUseCase;

    private static final String ORG_ID = "org-1";
    private static final Instant NOW = Instant.now();

    private User activeUser(String id) {
        return new User(id, ORG_ID, "user@test.com", "hash", "John Doe", "role-dev", null, null, null, null, null, true, true, 0L, NOW, NOW);
    }

    // ── CreateUserUseCase ────────────────────────────────────────────────────

    @Test
    void createUser_shouldReturnTempPassword() {
        var saved = activeUser("user-1");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(saved);

        CreateUserOutput output = createUserUseCase.execute(
                new CreateUserInput("user@test.com", "role-dev"), ORG_ID);

        assertThat(output.id()).isEqualTo("user-1");
        assertThat(output.email()).isEqualTo("user@test.com");
        assertThat(output.inviteToken()).isNotBlank();
        assertThat(output.inviteToken()).hasSize(32);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_shouldThrowEmailConflict() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> createUserUseCase.execute(
                new CreateUserInput("user@test.com", "role-dev"), ORG_ID))
                .isInstanceOf(UserEmailConflictException.class)
                .hasMessageContaining("user@test.com");
    }

    // ── UpdateUserUseCase ────────────────────────────────────────────────────

    @Test
    void updateUser_shouldUpdateNameAndRoleId() {
        var existing = activeUser("user-1");
        var updated = new User("user-1", ORG_ID, "user@test.com", "hash", "Jane Doe", "role-lead", null, null, null, null, null, true, true, 1L, NOW, NOW);

        when(userRepository.findByIdAndOrganizationId("user-1", ORG_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenReturn(updated);

        UserOutput output = updateUserUseCase.execute(new UpdateUserInput("user-1", "Jane Doe", "role-lead", null), ORG_ID);

        assertThat(output.name()).isEqualTo("Jane Doe");
        assertThat(output.roleId()).isEqualTo("role-lead");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUser_shouldThrowNotFoundForInvalidId() {
        when(userRepository.findByIdAndOrganizationId("invalid", ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUserUseCase.execute(new UpdateUserInput("invalid", "Name", null, null), ORG_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("invalid");
    }

    // ── DeactivateUserUseCase ────────────────────────────────────────────────

    @Test
    void deactivateUser_shouldSoftDelete() {
        var existing = activeUser("user-1");
        var deactivated = new User("user-1", ORG_ID, "user@test.com", "hash", "John Doe", "role-dev", null, null, null, null, null, true, false, 1L, NOW, NOW);

        when(userRepository.findByIdAndOrganizationId("user-1", ORG_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenReturn(deactivated);

        deactivateUserUseCase.execute("user-1", ORG_ID);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void deactivateUser_shouldThrowNotFoundForInvalidId() {
        when(userRepository.findByIdAndOrganizationId("invalid", ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deactivateUserUseCase.execute("invalid", ORG_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("invalid");
    }
}
