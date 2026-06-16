package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListProjectsUseCase {

    private final ProjectRepository projectRepository;

    public List<Project> execute(String userId, boolean hasUserManage) {
        String organizationId = TenantContextHolder.get();
        if (hasUserManage) {
            return projectRepository.findAllActiveByOrganizationId(organizationId);
        }
        return projectRepository.findAllActiveByOrganizationIdAndMemberId(organizationId, userId);
    }
}
