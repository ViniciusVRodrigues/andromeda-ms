package andromeda.sei.ms.feature.role.infrastructure.config;

import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleDocument;
import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleMongoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Default roles: ADMIN holds PROJECT_MANAGE (project mutations), and every seeded REPOS_* code is
 * stored with its legacy GITHUB_* mirror so a rolled-back deploy keeps repository access.
 */
@ExtendWith(MockitoExtension.class)
class RoleSeederTest {

    @Mock private RoleMongoRepository roleMongoRepository;

    @InjectMocks private RoleSeeder seeder;

    @Test
    void should_seedDefaultRoles_withProjectManageForAdmin_andLegacyMirrors() {
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull(anyString())).thenReturn(Optional.empty());

        seeder.run(null);

        Map<String, RoleDocument> saved = captureSaved();
        assertThat(saved.get("ADMIN").getPermissionCodes())
                .contains("PROJECT_MANAGE", "USER_MANAGE", "REPOS_READ", "GITHUB_READ", "REPOS_CONFIGURE", "GITHUB_CONFIGURE");
        assertThat(saved.get("TECH_LEAD").getPermissionCodes()).contains("REPOS_READ", "GITHUB_READ");
        assertThat(saved.get("MANAGER").getPermissionCodes()).contains("REPOS_READ", "GITHUB_READ")
                .doesNotContain("USER_MANAGE", "PROJECT_MANAGE");
        assertThat(saved.get("DEVELOPER").getPermissionCodes()).noneMatch(c -> c.startsWith("GITHUB_"));
    }

    @Test
    void should_backfillMissingMirrorsAndProjectManage_onExistingDefaultRoles() {
        RoleDocument admin = RoleDocument.builder().name("ADMIN").label("Administrador").isDefault(true)
                .permissionCodes(new ArrayList<>(List.of("USER_MANAGE", "REPOS_READ"))).active(true).build();
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull(anyString())).thenReturn(Optional.empty());
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull("ADMIN")).thenReturn(Optional.of(admin));

        seeder.run(null);

        assertThat(captureSaved().get("ADMIN").getPermissionCodes())
                .contains("PROJECT_MANAGE", "REPOS_READ", "GITHUB_READ", "GITHUB_CONFIGURE");
    }

    @Test
    void should_notRewrite_whenDefaultRoleIsAlreadyComplete() {
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull(anyString())).thenReturn(Optional.empty());
        seeder.run(null);
        Map<String, RoleDocument> first = captureSaved();
        org.mockito.Mockito.reset(roleMongoRepository);
        first.values().forEach(doc -> when(roleMongoRepository.findByNameAndOrganizationIdIsNull(doc.getName()))
                .thenReturn(Optional.of(doc)));

        seeder.run(null);

        verify(roleMongoRepository, never()).save(any());
    }

    private Map<String, RoleDocument> captureSaved() {
        ArgumentCaptor<RoleDocument> captor = ArgumentCaptor.forClass(RoleDocument.class);
        verify(roleMongoRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .collect(Collectors.toMap(RoleDocument::getName, Function.identity(), (a, b) -> b));
    }
}
