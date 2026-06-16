package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectInvalidMemberException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProjectMembersUseCaseTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private UpdateProjectMembersUseCase useCase;

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String USER_ID = "user-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Project project() {
        return new Project(PROJECT_ID, ORG_ID, "My Project", "my-project", "desc", true, new ArrayList<>(), 0L, NOW, NOW);
    }

    @Test
    void execute_happyPath_replacesMemberIds() {
        var proj = project();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(proj));
        when(userRepository.findByIdAndOrganizationId(USER_ID, ORG_ID))
                .thenReturn(Optional.of(new User(USER_ID, ORG_ID, "alice@x.com", null, "Alice", null, null, null, null, null, null, true, true, 0L, NOW, NOW)));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(PROJECT_ID, List.of(USER_ID));

        assertThat(result.getMemberIds()).containsExactly(USER_ID);
    }

    @Test
    void execute_projectNotFound_throwsNotFoundException() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(PROJECT_ID, List.of(USER_ID)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void execute_userNotInOrg_throwsInvalidMemberException() {
        var proj = project();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(proj));
        when(userRepository.findByIdAndOrganizationId(USER_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(PROJECT_ID, List.of(USER_ID)))
                .isInstanceOf(ProjectInvalidMemberException.class);
    }
}
