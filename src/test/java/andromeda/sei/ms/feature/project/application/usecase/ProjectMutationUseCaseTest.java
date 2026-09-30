package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Project edit/deactivate of a project that belongs to another organization answers 404
 * ({@code PROJECT_NOT_FOUND}) even for a {@code PROJECT_MANAGE} holder — existence is not leaked.
 */
@ExtendWith(MockitoExtension.class)
class ProjectMutationUseCaseTest {

    private static final String ORG_ID = "org-1";
    private static final String FOREIGN_PROJECT_ID = "p-foreign";
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private UpdateProjectUseCase updateProjectUseCase;
    @InjectMocks private DeactivateProjectUseCase deactivateProjectUseCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG_ID);
        when(projectRepository.findById(FOREIGN_PROJECT_ID)).thenReturn(Optional.of(new Project(
                FOREIGN_PROJECT_ID, "org-2", "Other", "other", "desc", true, new ArrayList<>(), 0L, NOW, NOW)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_throwProjectNotFound_when_updatingProjectOfAnotherOrganization() {
        assertThatThrownBy(() -> updateProjectUseCase.execute(FOREIGN_PROJECT_ID, "New", "desc"))
                .isInstanceOf(ProjectNotFoundException.class)
                .extracting("code").isEqualTo("PROJECT_NOT_FOUND");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void should_throwProjectNotFound_when_deactivatingProjectOfAnotherOrganization() {
        assertThatThrownBy(() -> deactivateProjectUseCase.execute(FOREIGN_PROJECT_ID))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(projectRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
