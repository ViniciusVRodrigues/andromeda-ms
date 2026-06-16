package andromeda.sei.ms.feature.project.presentation;

import andromeda.sei.ms.feature.project.application.usecase.CreateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.DeactivateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.ListProjectsUseCase;
import andromeda.sei.ms.feature.project.application.usecase.UpdateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.UpdateProjectMembersUseCase;
import andromeda.sei.ms.feature.project.presentation.dto.ProjectRequest;
import andromeda.sei.ms.feature.project.presentation.dto.ProjectResponse;
import andromeda.sei.ms.feature.project.presentation.dto.UpdateProjectMembersRequest;
import andromeda.sei.ms.feature.workitem.application.usecase.GetProjectSprintsUseCase;
import andromeda.sei.ms.feature.workitem.presentation.dto.SprintSummaryResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ListProjectsUseCase listProjectsUseCase;
    private final CreateProjectUseCase createProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final DeactivateProjectUseCase deactivateProjectUseCase;
    private final UpdateProjectMembersUseCase updateProjectMembersUseCase;
    private final GetProjectSprintsUseCase getProjectSprintsUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectResponse>> list() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth.getName();
        boolean hasUserManage = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("USER_MANAGE"::equals);
        var projects = listProjectsUseCase.execute(userId, hasUserManage).stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        var project = createProjectUseCase.execute(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectResponse> update(@PathVariable String id,
                                                   @Valid @RequestBody ProjectRequest request) {
        var project = updateProjectUseCase.execute(id, request.name(), request.description());
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        deactivateProjectUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ProjectResponse> updateMembers(@PathVariable String id,
                                                          @Valid @RequestBody UpdateProjectMembersRequest request) {
        var project = updateProjectMembersUseCase.execute(id, request.memberIds());
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @GetMapping("/{projectId}/sprints")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SprintSummaryResponse>> listSprints(@PathVariable String projectId) {
        String orgId = TenantContextHolder.get();
        var sprints = getProjectSprintsUseCase.execute(orgId, projectId);
        return ResponseEntity.ok(sprints.stream().map(SprintSummaryResponse::from).toList());
    }
}
