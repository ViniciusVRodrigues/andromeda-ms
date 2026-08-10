package andromeda.sei.ms.feature.role.infrastructure.config;

import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Order(1)
public class PermissionSeeder implements ApplicationRunner {

    private final PermissionRepository permissionRepository;

    private static final List<String[]> PERMISSIONS = List.of(
            new String[]{"USER_MANAGE",                    "Gerenciar usuários da organização",              "Usuários"},
            new String[]{"ROLE_MANAGE",                    "Gerenciar roles e permissões",                   "Roles"},
            new String[]{"AZURE_CONFIGURE",                "Configurar integração com Azure DevOps",          "Azure"},
            new String[]{"AZURE_READ",                     "Visualizar dados do Azure DevOps",               "Azure"},
            new String[]{"GITHUB_CONFIGURE",               "Configurar integração com GitHub",               "GitHub"},
            new String[]{"GITHUB_READ",                    "Visualizar dados do GitHub",                     "GitHub"},
            new String[]{"AI_READ_OWN",                    "Visualizar próprio uso de IA",                   "IA"},
            new String[]{"AI_READ_ALL",                    "Visualizar uso de IA de todos os membros",       "IA"},
            new String[]{"DASHBOARD_INDIVIDUAL",           "Acessar dashboard individual de métricas",       "Dashboard"},
            new String[]{"DASHBOARD_TEAM",                 "Acessar dashboard de time",                      "Dashboard"},
            new String[]{"DASHBOARD_STRATEGIC",            "Acessar dashboard estratégico",                  "Dashboard"},
            new String[]{"PRICE_TABLE_MANAGE",             "Gerenciar tabela de preços de IA",               "Preços"},
            new String[]{"CONNECTOR_TOKEN_MANAGE_OWN",     "Gerenciar próprio token de conector",            "Conector"},
            new String[]{"CONNECTOR_TOKEN_MANAGE_ALL",     "Gerenciar tokens de conector de toda a org",     "Conector"},
            new String[]{"PROJECT_MANAGE",                 "Gerenciar projetos da organização",               "Projetos"},
            new String[]{"WORK_ITEM_MANAGE",                "Editar manualmente campos de work items",         "Work Items"}
    );

    @Override
    public void run(ApplicationArguments args) {
        for (String[] entry : PERMISSIONS) {
            String code = entry[0];
            if (!permissionRepository.existsByCode(code)) {
                permissionRepository.save(new Permission(null, code, entry[1], entry[2]));
            }
        }
    }
}
