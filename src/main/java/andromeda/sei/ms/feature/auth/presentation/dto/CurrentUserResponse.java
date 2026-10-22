package andromeda.sei.ms.feature.auth.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CurrentUserResponse(
        String userId,
        String name,
        String email,
        String organizationId,
        String orgName,
        String orgSlug,
        /** Same as {@code orgPermissions}, kept for one release (project-scoped-permissions 8.1). */
        List<String> permissions,
        List<String> orgPermissions,
        Map<String, List<String>> projectPermissions,
        /** Cargo da pessoa: id e o rótulo de exibição (label, ou o nome quando não há label). */
        String roleId,
        String roleLabel,
        /** Nível de senioridade (nome, como na lista da organização); null sem nível. */
        String seniority,
        /** Quando a pessoa entrou na organização: o createdAt do usuário (criação da org ou do convite). */
        Instant memberSince
) {}
