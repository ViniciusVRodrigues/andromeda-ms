package andromeda.sei.ms.shared.presentation;

import andromeda.sei.ms.shared.presentation.dto.PermissionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    // Permissions that are implicitly granted to all users and should not be
    // shown in the role configuration UI (AZURE_READ, GITHUB_READ, AI_READ_OWN,
    // CONNECTOR_TOKEN_MANAGE_OWN are always-on; DASHBOARD_INDIVIDUAL is the
    // default for every role).
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("AI_READ_ALL", "Visualizar IA de todos"),
            Map.entry("AZURE_CONFIGURE", "Configurar Azure DevOps"),
            Map.entry("CONNECTOR_TOKEN_MANAGE_ALL", "Gerenciar tokens da org"),
            Map.entry("DASHBOARD_STRATEGIC", "Dashboard Estratégico"),
            Map.entry("DASHBOARD_TEAM", "Dashboard de Time"),
            Map.entry("GITHUB_CONFIGURE", "Configurar GitHub"),
            Map.entry("PRICE_TABLE_MANAGE", "Gerenciar Tabela de Preços"),
            Map.entry("PROJECT_MANAGE", "Gerenciar Projetos"),
            Map.entry("ROLE_MANAGE", "Gerenciar Cargos"),
            Map.entry("USER_MANAGE", "Gerenciar Usuários")
    );

    @GetMapping
    public ResponseEntity<Map<String, List<PermissionResponse>>> list() {
        List<PermissionResponse> permissions = LABELS.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new PermissionResponse(e.getKey(), e.getValue()))
                .toList();
        return ResponseEntity.ok(Map.of("permissions", permissions));
    }
}
