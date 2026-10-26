package andromeda.sei.ms.shared.presentation;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.presentation.dto.PermissionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Permission catalog with the scope of every code (project-scoped-permissions, permission-scopes).
 * Scopes come from {@link PermissionCatalog}; the labels are the server-side defaults (the UI keeps
 * its own {@code PERMISSION_LABELS}).
 *
 * <p>Still public (see {@code SecurityConfig.PUBLIC_ENDPOINTS}): the signup wizard reads it before
 * authentication. The catalog carries no tenant data.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("AI_READ_ALL", "Visualizar IA de todos"),
            Map.entry("AI_READ_OWN", "Visualizar uso de IA próprio"),
            Map.entry("AZURE_CONFIGURE", "Configurar Azure DevOps"),
            Map.entry("AZURE_READ", "Ver dados Azure"),
            Map.entry("CONNECTOR_TOKEN_MANAGE_ALL", "Gerenciar tokens da org"),
            Map.entry("CONNECTOR_TOKEN_MANAGE_OWN", "Gerenciar token IA próprio"),
            Map.entry("DASHBOARD_INDIVIDUAL", "Dashboard Pessoal"),
            Map.entry("DASHBOARD_STRATEGIC", "Dashboard Estratégico"),
            Map.entry("DASHBOARD_TEAM", "Dashboard de Time"),
            Map.entry("REPOS_CONFIGURE", "Configurar Repositórios"),
            Map.entry("REPOS_READ", "Ver dados de Repositórios"),
            Map.entry("PRICE_TABLE_MANAGE", "Gerenciar Tabela de Preços"),
            Map.entry("PROJECT_ACCESS_ALL", "Acesso a todos os projetos"),
            Map.entry("ORGANIZATION_DELETE", "Excluir a organização"),
            Map.entry("PROJECT_MANAGE", "Gerenciar Projetos"),
            Map.entry("ROLE_MANAGE", "Gerenciar Cargos"),
            Map.entry("USER_MANAGE", "Gerenciar Usuários"),
            Map.entry("WORK_ITEM_MANAGE", "Editar work items")
    );

    @GetMapping
    public ResponseEntity<Map<String, List<PermissionResponse>>> list() {
        List<PermissionResponse> permissions = PermissionCatalog.scopes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> new PermissionResponse(e.getKey(), LABELS.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue().name()))
                .toList();
        return ResponseEntity.ok(Map.of("permissions", permissions));
    }
}
