package andromeda.sei.ms.feature.role.infrastructure.config;

import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleDocument;
import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleMongoRepository;
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
            "GITHUB_CONFIGURE", "GITHUB_READ",
            "AI_READ_OWN", "AI_READ_ALL",
            "DASHBOARD_INDIVIDUAL", "DASHBOARD_TEAM", "DASHBOARD_STRATEGIC",
            "PRICE_TABLE_MANAGE",
            "CONNECTOR_TOKEN_MANAGE_OWN", "CONNECTOR_TOKEN_MANAGE_ALL",
            "PROJECT_MANAGE"
    );

    private record RoleSeed(String name, List<String> permissionCodes) {}

    private static final List<RoleSeed> DEFAULT_ROLES = List.of(
            new RoleSeed("ADMIN", ALL_PERMISSIONS),
            new RoleSeed("TECH_LEAD", List.of(
                    "AZURE_READ", "GITHUB_READ", "AI_READ_ALL",
                    "DASHBOARD_TEAM", "DASHBOARD_INDIVIDUAL", "CONNECTOR_TOKEN_MANAGE_OWN",
                    "PROJECT_MANAGE"
            )),
            new RoleSeed("MANAGER", List.of(
                    "AZURE_READ", "GITHUB_READ", "AI_READ_ALL", "DASHBOARD_STRATEGIC"
            )),
            new RoleSeed("DEVELOPER", List.of(
                    "AI_READ_OWN", "DASHBOARD_INDIVIDUAL", "CONNECTOR_TOKEN_MANAGE_OWN"
            ))
    );

    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        for (RoleSeed seed : DEFAULT_ROLES) {
            if (roleMongoRepository.findByNameAndOrganizationIdIsNull(seed.name()).isEmpty()) {
                RoleDocument doc = RoleDocument.builder()
                                .organizationId(null)
                                .name(seed.name())
                                .isDefault(true)
                                .permissionCodes(seed.permissionCodes())
                                .active(true)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                roleMongoRepository.save(doc);
            }
        }
    }
}
