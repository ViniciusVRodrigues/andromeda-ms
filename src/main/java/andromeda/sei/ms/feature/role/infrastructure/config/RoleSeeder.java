package andromeda.sei.ms.feature.role.infrastructure.config;

import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleDocument;
import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleMongoRepository;
import andromeda.sei.ms.shared.domain.security.PermissionAliasResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Order(2)
public class RoleSeeder implements ApplicationRunner {

    private final RoleMongoRepository roleMongoRepository;

    private static final List<String> ALL_PERMISSIONS = List.of(
            "USER_MANAGE", "ROLE_MANAGE",
            "AZURE_CONFIGURE", "AZURE_READ",
            "REPOS_CONFIGURE", "REPOS_READ",
            "AI_READ_OWN", "AI_READ_ALL",
            "DASHBOARD_INDIVIDUAL", "DASHBOARD_TEAM", "DASHBOARD_STRATEGIC",
            "PRICE_TABLE_MANAGE",
            "CONNECTOR_TOKEN_MANAGE_OWN", "CONNECTOR_TOKEN_MANAGE_ALL",
            "PROJECT_MANAGE", "WORK_ITEM_MANAGE",
            "PROJECT_ACCESS_ALL"
    );

    private record RoleSeed(String name, String label, List<String> permissionCodes) {}

    private static final List<RoleSeed> DEFAULT_ROLES = List.of(
            new RoleSeed("ADMIN", "Administrador", ALL_PERMISSIONS),
            new RoleSeed("TECH_LEAD", "Líder Técnico", List.of(
                    "AZURE_READ", "REPOS_READ", "AI_READ_ALL",
                    "DASHBOARD_TEAM", "DASHBOARD_INDIVIDUAL", "CONNECTOR_TOKEN_MANAGE_OWN",
                    "PROJECT_MANAGE", "WORK_ITEM_MANAGE"
            )),
            new RoleSeed("MANAGER", "Gerente", List.of(
                    "AZURE_READ", "REPOS_READ", "AI_READ_ALL",
                    "DASHBOARD_INDIVIDUAL", "DASHBOARD_STRATEGIC",
                    "AI_READ_OWN", "CONNECTOR_TOKEN_MANAGE_OWN",
                    // Managers read every project, as before project-scoped-permissions.
                    "PROJECT_ACCESS_ALL"
            )),
            new RoleSeed("DEVELOPER", "Desenvolvedor", List.of(
                    "AI_READ_OWN", "DASHBOARD_INDIVIDUAL", "CONNECTOR_TOKEN_MANAGE_OWN"
            ))
    );

    /**
     * Writes {@link RoleDocument}s directly (bypassing {@code RoleDocumentMapper}), so it stores the
     * legacy {@code GITHUB_*} mirror next to every {@code REPOS_*} code itself — a rolled-back deploy
     * checks the legacy codes (see {@link PermissionAliasResolver}). The backfill below adds the
     * mirrors to existing default roles on the next boot.
     */
    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        for (RoleSeed declared : DEFAULT_ROLES) {
            RoleSeed seed = new RoleSeed(declared.name(), declared.label(),
                    PermissionAliasResolver.withLegacyMirrors(declared.permissionCodes()));
            roleMongoRepository.findByNameAndOrganizationIdIsNull(seed.name())
                    .ifPresentOrElse(
                            existing -> {
                                boolean dirty = false;
                                if (existing.getLabel() == null) {
                                    existing.setLabel(seed.label());
                                    dirty = true;
                                }
                                // Backfill any missing always-on permissions
                                List<String> current = existing.getPermissionCodes() == null
                                        ? new java.util.ArrayList<>()
                                        : new java.util.ArrayList<>(existing.getPermissionCodes());
                                for (String code : seed.permissionCodes()) {
                                    if (!current.contains(code)) {
                                        current.add(code);
                                        dirty = true;
                                    }
                                }
                                if (dirty) {
                                    existing.setPermissionCodes(current);
                                    existing.setUpdatedAt(now);
                                    roleMongoRepository.save(existing);
                                }
                            },
                            () -> {
                                RoleDocument doc = RoleDocument.builder()
                                        .organizationId(null)
                                        .name(seed.name())
                                        .label(seed.label())
                                        .isDefault(true)
                                        .permissionCodes(seed.permissionCodes())
                                        .active(true)
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build();
                                roleMongoRepository.save(doc);
                            }
                    );
        }
    }
}
