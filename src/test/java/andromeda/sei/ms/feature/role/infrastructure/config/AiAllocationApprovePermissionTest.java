package andromeda.sei.ms.feature.role.infrastructure.config;

import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.PermissionRepository;
import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleDocument;
import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleMongoRepository;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.PermissionScope;
import andromeda.sei.ms.shared.presentation.PermissionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** uso-ia-fora-de-entrega P1: AI_ALLOCATION_APPROVE in the catalog, the seeders, the presets and the label. */
@ExtendWith(MockitoExtension.class)
class AiAllocationApprovePermissionTest {

    private static final String CODE = "AI_ALLOCATION_APPROVE";

    @Mock private RoleMongoRepository roleMongoRepository;
    @Mock private PermissionRepository permissionRepository;

    @Test
    void should_beProjectScopableInTheCatalog() {
        assertThat(PermissionCatalog.scopeOf(CODE)).contains(PermissionScope.PROJECT_SCOPABLE);
        assertThat(PermissionCatalog.isProjectScopable(CODE)).isTrue();
        assertThat(PermissionCatalog.isAssignableToRole(CODE)).isTrue();
    }

    @Test
    void should_seedThePermissionWithLabelAndAiGroup() {
        when(permissionRepository.existsByCode(anyString())).thenReturn(true);
        when(permissionRepository.existsByCode(CODE)).thenReturn(false);

        new PermissionSeeder(permissionRepository).run(null);

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(CODE);
        assertThat(captor.getValue().getDescription()).isEqualTo("Aprovar uso de IA fora da regra");
        assertThat(captor.getValue().getModule()).isEqualTo("IA");
    }

    @Test
    void should_giveTheCodeToTechLeadManagerAndAdminOnly_whenSeedingFresh() {
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull(anyString())).thenReturn(Optional.empty());

        new RoleSeeder(roleMongoRepository).run(null);

        Map<String, RoleDocument> saved = captureSaved();
        assertThat(saved.get("ADMIN").getPermissionCodes()).contains(CODE);
        assertThat(saved.get("TECH_LEAD").getPermissionCodes()).contains(CODE);
        assertThat(saved.get("MANAGER").getPermissionCodes()).contains(CODE);
        assertThat(saved.get("DEVELOPER").getPermissionCodes()).doesNotContain(CODE);
    }

    @Test
    void should_backfillTheCodeIdempotently_onExistingDefaultRoles() {
        RoleDocument techLead = RoleDocument.builder().name("TECH_LEAD").label("Líder Técnico").isDefault(true)
                .permissionCodes(new ArrayList<>(List.of("AZURE_READ"))).active(true).build();
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull(anyString())).thenReturn(Optional.empty());
        when(roleMongoRepository.findByNameAndOrganizationIdIsNull("TECH_LEAD")).thenReturn(Optional.of(techLead));
        RoleSeeder seeder = new RoleSeeder(roleMongoRepository);

        seeder.run(null);
        seeder.run(null);

        assertThat(techLead.getPermissionCodes()).containsOnlyOnce(CODE);
    }

    @Test
    void should_labelTheCodeInThePermissionsEndpoint() {
        var body = new PermissionController().list().getBody();

        assertThat(body.get("permissions")).anySatisfy(p -> {
            assertThat(p.code()).isEqualTo(CODE);
            assertThat(p.label()).isEqualTo("Aprovar uso de IA fora da regra");
            assertThat(p.scope()).isEqualTo("PROJECT_SCOPABLE");
        });
    }

    private Map<String, RoleDocument> captureSaved() {
        ArgumentCaptor<RoleDocument> captor = ArgumentCaptor.forClass(RoleDocument.class);
        verify(roleMongoRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .collect(Collectors.toMap(RoleDocument::getName, Function.identity(), (a, b) -> b));
    }
}
