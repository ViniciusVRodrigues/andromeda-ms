package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolve os nomes dos responsáveis de um conjunto de work items numa única consulta.
 *
 * <p>O nome não é desnormalizado no work item de propósito (ver {@code WorkItemSummaryResponse}):
 * os usuários de uma organização são poucos e cabem num lookup só, e resolver na leitura evita
 * que uma troca de nome deixe o backlog inteiro desatualizado até o próximo sync.
 *
 * <p>Compartilhado entre os controllers de backlog e de work item para que a listagem em árvore
 * e a expansão de filhos exibam a mesma coisa — antes só as raízes resolviam o nome, e os filhos
 * apareciam com o e-mail do Azure.
 */
@Component
@RequiredArgsConstructor
public class AssigneeNameResolver {

    private final UserRepository userRepository;

    public Map<String, String> resolveFor(Collection<WorkItem> items) {
        boolean anyAssignee = items.stream().anyMatch(item -> item.getAssigneeUserId() != null);
        if (!anyAssignee) return Map.of();

        return userRepository.findAllByOrganizationId(TenantContextHolder.get()).stream()
                .filter(u -> u.getName() != null)
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
    }
}
