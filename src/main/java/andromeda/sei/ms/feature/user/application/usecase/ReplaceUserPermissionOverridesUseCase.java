package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.application.dto.PermissionOverrideInput;
import andromeda.sei.ms.feature.user.application.dto.ReplacePermissionOverridesInput;
import andromeda.sei.ms.feature.user.application.dto.UserPermissionsOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.PermissionCodeInvalidException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionEscalationDeniedException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionSelfEditDeniedException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionNotProjectScopableException;
import andromeda.sei.ms.feature.user.domain.exception.UserConflictException;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.shared.application.security.UserPermissionsChanged;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.domain.security.PermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replaces the full override list of a user (project-scoped-permissions 5.2, D7).
 *
 * <p>Callers cannot replace their own overrides ({@code PERMISSION_SELF_EDIT_DENIED}): the escalation
 * guard would otherwise let a self-imposed deny lock the caller out for good.
 *
 * <p>Validation happens before anything is saved, in this order: code in the catalog
 * ({@code PERMISSION_CODE_INVALID}), project scope only for PROJECT_SCOPABLE codes
 * ({@code PERMISSION_NOT_PROJECT_SCOPABLE}), project active in the organization
 * ({@code PROJECT_NOT_FOUND}), stale version ({@code USER_CONFLICT}), then the escalation guard:
 * for every override added or removed (by code, effect and scope) the caller must effectively hold
 * the code at that scope ({@code PERMISSION_ESCALATION_DENIED}). Unchanged entries keep their
 * {@code updatedBy}/{@code updatedAt}.
 */
@Component
@RequiredArgsConstructor
public class ReplaceUserPermissionOverridesUseCase {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final GetUserPermissionsUseCase getUserPermissionsUseCase;
    private final ApplicationEventPublisher eventPublisher;

    public UserPermissionsOutput execute(ReplacePermissionOverridesInput input) {
        String orgId = input.caller().organizationId();
        User user = userRepository.findByIdAndOrganizationId(input.userId(), orgId)
                .orElseThrow(() -> new UserNotFoundException(input.userId()));
        if (user.getId().equals(input.caller().userId())) {
            throw new PermissionSelfEditDeniedException(user.getId());
        }

        Set<PermissionOverride.Key> requested = validate(orgId, input.overrides());
        if (input.version() != null && !Objects.equals(input.version(), user.getVersion())) {
            throw new UserConflictException(user.getId());
        }

        Map<PermissionOverride.Key, PermissionOverride> current = user.getPermissionOverrides().stream()
                .collect(Collectors.toMap(PermissionOverride::key, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        guardEscalation(input.caller().resolver(), requested, current.keySet());

        if (!requested.equals(current.keySet())) {
            user.setPermissionOverrides(merge(requested, current, input.caller().userId()));
            userRepository.save(user);
            eventPublisher.publishEvent(new UserPermissionsChanged(orgId, user.getId()));
        }
        return getUserPermissionsUseCase.execute(orgId, user.getId());
    }

    private Set<PermissionOverride.Key> validate(String orgId, List<PermissionOverrideInput> overrides) {
        Set<PermissionOverride.Key> keys = new LinkedHashSet<>();
        if (overrides == null) return keys;
        for (PermissionOverrideInput o : overrides) {
            if (o == null || !PermissionCatalog.isKnown(o.code())) {
                throw new PermissionCodeInvalidException(o == null ? null : o.code());
            }
            PermissionEffect effect = parseEffect(o);
            String projectId = o.projectId() == null || o.projectId().isBlank() ? null : o.projectId();
            if (projectId != null) {
                if (!PermissionCatalog.isProjectScopable(o.code())) {
                    throw new PermissionNotProjectScopableException(o.code());
                }
                projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                        .orElseThrow(() -> new ProjectNotFoundException(projectId));
            }
            keys.add(new PermissionOverride.Key(o.code(), effect, projectId));
        }
        return keys;
    }

    private static PermissionEffect parseEffect(PermissionOverrideInput o) {
        if (o.effect() != null) {
            for (PermissionEffect effect : PermissionEffect.values()) {
                if (effect.name().equals(o.effect())) return effect;
            }
        }
        throw new PermissionCodeInvalidException(o.code() + " (effect " + o.effect() + ")");
    }

    private static void guardEscalation(PermissionResolver caller, Set<PermissionOverride.Key> requested,
                                        Set<PermissionOverride.Key> current) {
        Set<PermissionOverride.Key> changed = new LinkedHashSet<>(requested);
        changed.removeAll(current);
        current.stream().filter(k -> !requested.contains(k)).forEach(changed::add);
        for (PermissionOverride.Key key : changed) {
            boolean holds = key.projectId() == null
                    ? caller.hasOrg(key.code())
                    : caller.hasOnProject(key.code(), key.projectId());
            if (!holds) throw new PermissionEscalationDeniedException(key.code(), key.projectId());
        }
    }

    private static List<PermissionOverride> merge(Set<PermissionOverride.Key> requested,
                                                  Map<PermissionOverride.Key, PermissionOverride> current,
                                                  String callerId) {
        Instant now = Instant.now();
        return requested.stream()
                .map(k -> current.getOrDefault(k,
                        new PermissionOverride(k.code(), k.effect(), k.projectId(), callerId, now)))
                .toList();
    }
}
