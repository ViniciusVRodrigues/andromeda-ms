package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WiqlFilter;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureFieldMappingDuplicateTargetException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureHierarchyLevelDuplicateTypeException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureWorkflowConfigMappingsEmptyException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saves a binding's {@link WorkflowConfig}. This is also the single "wizard completion" signal
 * used by both the field-mapping wizard's "Concluir" (save) action and its "Pular e usar
 * padrões" (skip) action — skip calls this same endpoint with the current draft state rather
 * than untouched defaults (see azure-staged-sync-gating design.md Decision 2). A bare wizard
 * close never calls this endpoint, so it never completes onboarding.
 *
 * <p>If the binding's {@code syncEnabled} is still {@code false} at the time of this call (i.e.
 * this is the binding's first-ever completion, not an edit to an already-onboarded binding),
 * this flips it to {@code true} and triggers an immediate sync scoped to just this binding —
 * see {@link TriggerAzureSyncUseCase#execute(String)}.
 *
 * <p>Separately, if the binding's {@code workflowConfig.configSource} was already {@code CUSTOM}
 * before this save (i.e. this is an edit to an already-configured binding, not first-time setup),
 * this also triggers an immediate scoped resync so {@code resolvedType}/{@code resolvedStatus}
 * reflect the new mapping without waiting for the next scheduled sync (see
 * {@code azure-wizard-smart-hierarchy} spec). These two resync triggers are mutually exclusive:
 * first-time completion (config was {@code DEFAULT}) can never also have had a {@code CUSTOM}
 * config already, so exactly one path fires per save, never both.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateWorkflowConfigUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final TriggerAzureSyncUseCase triggerAzureSyncUseCase;

    public AzureProjectBinding execute(String bindingId, UpdateWorkflowConfigInput input) {
        String orgId = TenantContextHolder.get();

        AzureProjectBinding binding = azureProjectBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new AzureBindingNotFoundException(bindingId));

        boolean isFirstCompletion = !binding.isSyncEnabled();
        boolean wasAlreadyCustom = binding.getWorkflowConfig() != null
                && binding.getWorkflowConfig().getConfigSource() == WorkflowConfig.ConfigSource.CUSTOM;

        List<StatusMapping> statusMappings = input.statusMappings() == null ? List.of() :
                input.statusMappings().stream()
                        .map(s -> new StatusMapping(s.workItemType(), s.rawStatus(), s.resolvedStatus()))
                        .toList();

        List<TypeMapping> typeMappings = input.typeMappings() == null ? List.of() :
                input.typeMappings().stream()
                        .map(t -> new TypeMapping(t.rawType(), t.resolvedType()))
                        .toList();

        validateNotWipingMappings(binding, statusMappings, typeMappings);

        List<FieldMapping> fieldMappings = input.fieldMappings() == null ? List.of() :
                input.fieldMappings().stream()
                        .map(f -> new FieldMapping(f.internalField(), f.workItemType(), f.azureFieldReferenceName(), f.azureFieldType()))
                        .toList();
        validateNoDuplicateTargets(fieldMappings);

        WiqlFilter syncFilter = input.syncFilter() == null ? null :
                new WiqlFilter(
                        input.syncFilter().workItemTypes() == null ? List.of() : input.syncFilter().workItemTypes(),
                        input.syncFilter().areaPath(),
                        input.syncFilter().iterationPath());

        List<HierarchyLevel> hierarchyLevels = input.hierarchyLevels() == null ? List.of() :
                input.hierarchyLevels().stream()
                        .map(h -> new HierarchyLevel(h.rawType(), h.rank()))
                        .toList();
        validateNoDuplicateRawTypes(hierarchyLevels);

        binding.setWorkflowConfig(new WorkflowConfig(
                statusMappings, typeMappings, fieldMappings, syncFilter, WorkflowConfig.ConfigSource.CUSTOM,
                hierarchyLevels));

        if (isFirstCompletion) {
            binding.setSyncEnabled(true);
        }

        AzureProjectBinding saved = azureProjectBindingRepository.save(binding);

        if (isFirstCompletion) {
            try {
                triggerAzureSyncUseCase.execute(saved.getId());
            } catch (Exception e) {
                log.warn("Immediate scoped sync failed after wizard completion for binding={}: {}",
                        saved.getId(), e.getMessage(), e);
            }
        } else if (wasAlreadyCustom) {
            try {
                triggerAzureSyncUseCase.execute(saved.getId());
            } catch (Exception e) {
                log.warn("Immediate scoped resync failed after config edit for binding={}: {}",
                        saved.getId(), e.getMessage(), e);
            }
        }

        return saved;
    }

    /**
     * Defense in depth against a caller (e.g. a wizard save that forgot to populate mappings)
     * silently wiping an already-configured binding back to empty. Does not block first-time
     * setup: if the binding's current list is already empty, an empty incoming list is accepted.
     */
    private void validateNotWipingMappings(AzureProjectBinding binding, List<StatusMapping> statusMappings,
                                            List<TypeMapping> typeMappings) {
        WorkflowConfig current = binding.getWorkflowConfig();
        if (current == null) return;

        boolean currentStatusNonEmpty = current.getStatusMappings() != null && !current.getStatusMappings().isEmpty();
        if (statusMappings.isEmpty() && currentStatusNonEmpty) {
            throw new AzureWorkflowConfigMappingsEmptyException(
                    "Refusing to replace existing non-empty statusMappings with an empty list for binding " + binding.getId());
        }

        boolean currentTypeNonEmpty = current.getTypeMappings() != null && !current.getTypeMappings().isEmpty();
        if (typeMappings.isEmpty() && currentTypeNonEmpty) {
            throw new AzureWorkflowConfigMappingsEmptyException(
                    "Refusing to replace existing non-empty typeMappings with an empty list for binding " + binding.getId());
        }
    }

    /**
     * Duplicate-target detection is keyed on (internalField, normalized workItemType): two entries
     * with the same internalField but different workItemType are not duplicates (they apply to
     * different work item types), but two entries with the same internalField and the same
     * (or both-null/wildcard) workItemType are.
     */
    private void validateNoDuplicateTargets(List<FieldMapping> fieldMappings) {
        Set<String> seen = new HashSet<>();
        for (FieldMapping mapping : fieldMappings) {
            String key = mapping.getInternalField().name() + "|" + normalizeWorkItemType(mapping.getWorkItemType());
            if (!seen.add(key)) {
                throw new AzureFieldMappingDuplicateTargetException(mapping.getInternalField().name(), mapping.getWorkItemType());
            }
        }
    }

    private String normalizeWorkItemType(String workItemType) {
        return workItemType == null ? null : workItemType.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validateNoDuplicateRawTypes(List<HierarchyLevel> hierarchyLevels) {
        Set<String> seen = new HashSet<>();
        for (HierarchyLevel level : hierarchyLevels) {
            if (!seen.add(level.getRawType())) {
                throw new AzureHierarchyLevelDuplicateTypeException(level.getRawType());
            }
        }
    }
}
