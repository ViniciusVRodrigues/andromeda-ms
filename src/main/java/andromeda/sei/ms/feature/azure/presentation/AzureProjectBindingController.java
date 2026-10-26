package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.BindAzureProjectInput;
import andromeda.sei.ms.feature.azure.application.dto.UpdateAzureBindingInput;
import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.application.usecase.BindAzureProjectUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ForceResyncProjectUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.RepairAzureDataUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureProjectsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypeFieldsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypeStatesUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypesUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListSiblingAzureProjectBindingsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.RemoveAzureProjectBindingUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureProjectBindingSyncUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateWorkflowConfigUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureProjectBindingResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureProjectResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureSiblingBindingResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureWorkItemTypeFieldResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureWorkItemTypeResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureWorkItemTypeStateResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.BindAzureProjectRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.UpdateAzureBindingRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.UpdateWorkflowConfigRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.WorkflowConfigResponse;
import andromeda.sei.ms.feature.azure.application.usecase.ForceResyncProjectUseCase.ResyncSummaryResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.dto.SyncStartedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Azure DevOps Projects", description = "Azure DevOps project binding management")
@RestController
@RequestMapping("/api/v1/integrations/azure")
@RequiredArgsConstructor
public class AzureProjectBindingController {

    private final ListAzureProjectsUseCase listAzureProjectsUseCase;
    private final BindAzureProjectUseCase bindAzureProjectUseCase;
    private final RemoveAzureProjectBindingUseCase removeAzureProjectBindingUseCase;
    private final UpdateAzureProjectBindingSyncUseCase updateAzureProjectBindingSyncUseCase;
    private final UpdateWorkflowConfigUseCase updateWorkflowConfigUseCase;
    private final ForceResyncProjectUseCase forceResyncProjectUseCase;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final ListAzureWorkItemTypesUseCase listAzureWorkItemTypesUseCase;
    private final ListAzureWorkItemTypeFieldsUseCase listAzureWorkItemTypeFieldsUseCase;
    private final ListAzureWorkItemTypeStatesUseCase listAzureWorkItemTypeStatesUseCase;
    private final ListSiblingAzureProjectBindingsUseCase listSiblingAzureProjectBindingsUseCase;
    private final RepairAzureDataUseCase repairAzureDataUseCase;
    private final andromeda.sei.ms.feature.integration.application.usecase.WipeIntegrationDataUseCase wipeIntegrationDataUseCase;

    @Operation(summary = "List available Azure DevOps projects",
            description = "Optional forProjectId excludes ADO projects actively bound to other "
                    + "Andromeda projects, keeping the one bound to forProjectId itself (design.md D4)")
    @ApiResponse(responseCode = "200", description = "Projects listed")
    @ApiResponse(responseCode = "409", description = "No Azure connection established")
    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureProjectResponse>> listProjects(
            @RequestParam(required = false) String forProjectId) {
        List<AzureProjectResponse> projects = listAzureProjectsUseCase.execute(forProjectId)
                .stream()
                .map(p -> new AzureProjectResponse(p.azureProjectId(), p.azureProjectName()))
                .toList();
        return ResponseEntity.ok(projects);
    }

    @Operation(summary = "Bind an Andromeda project to an Azure DevOps project")
    @ApiResponse(responseCode = "200", description = "Binding created or updated")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @PostMapping("/projects/{projectId}/binding")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<AzureProjectBindingResponse> bindProject(
            @PathVariable String projectId,
            @Valid @RequestBody BindAzureProjectRequest request) {
        AzureProjectBinding binding = bindAzureProjectUseCase.execute(projectId,
                new BindAzureProjectInput(request.azureProjectId(), request.azureProjectName()));
        return ResponseEntity.ok(toResponse(binding));
    }

    @Operation(summary = "Remove Azure DevOps project binding")
    @ApiResponse(responseCode = "204", description = "Binding removed")
    @ApiResponse(responseCode = "404", description = "Binding not found")
    @DeleteMapping("/projects/{projectId}/binding")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> removeBinding(@PathVariable String projectId) {
        removeAzureProjectBindingUseCase.execute(projectId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Force full re-sync of work item transition history for a project")
    @ApiResponse(responseCode = "202", description = "Re-sync completed")
    @ApiResponse(responseCode = "404", description = "Binding or connection not found")
    @ApiResponse(responseCode = "403", description = "Missing AZURE_CONFIGURE permission")
    @PostMapping("/projects/{projectId}/resync")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<ResyncSummaryResponse> resyncProject(@PathVariable String projectId) {
        String orgId = TenantContextHolder.get();
        ResyncSummaryResponse summary = forceResyncProjectUseCase.execute(orgId, projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(summary);
    }

    @Operation(summary = "Repara os dados do Azure DevOps refazendo um sync completo",
            description = "Zera o watermark de leitura e dispara um sync FULL. Preserva os registros "
                    + "existentes (upsert por externalId) e recupera o que faltava — inclusive itens "
                    + "que um sync incremental jamais voltaria a buscar. Assíncrono: acompanhe pelo "
                    + "histórico de sincronizações. Passe projectId para limitar a um binding.")
    @ApiResponse(responseCode = "202", description = "Repair disparado")
    @ApiResponse(responseCode = "403", description = "Sem permissão AZURE_CONFIGURE")
    @ApiResponse(responseCode = "404", description = "Nenhum binding sincronizável encontrado")
    @PostMapping("/repair")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<SyncStartedResponse> repair(@RequestParam(required = false) String projectId) {
        String syncId = repairAzureDataUseCase.execute(TenantContextHolder.get(), projectId);
        return ResponseEntity.accepted().body(new SyncStartedResponse(syncId));
    }

    /**
     * Hard delete e irreversível. A organização vem sempre do contexto autenticado — nunca do
     * cliente —, e o wipe não desconecta nem desvincula: só apaga os dados.
     */
    @Operation(summary = "Apaga permanentemente os dados sincronizados do Azure DevOps",
            description = "Remoção definitiva e sem volta. Não desconecta a integração nem remove o "
                    + "vínculo do projeto — só apaga os dados, e um novo sync os reconstrói. Passe "
                    + "projectId para limitar a um binding; sem ele, limpa a integração inteira na "
                    + "organização (incluindo os caches de projetos/usuários Azure). Assíncrono: "
                    + "acompanhe pelo histórico de sincronizações.")
    @ApiResponse(responseCode = "202", description = "Wipe disparado")
    @ApiResponse(responseCode = "403", description = "Sem permissão AZURE_CONFIGURE")
    @DeleteMapping("/data")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> wipeData(@RequestParam(required = false) String projectId) {
        var scope = projectId == null
                ? andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope.wholeIntegration()
                : andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope.forProject(projectId);
        wipeIntegrationDataUseCase.execute(
                TenantContextHolder.get(),
                andromeda.sei.ms.feature.integration.domain.Integration.AZURE_DEVOPS,
                scope);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Toggle sync for a bound project")
    @ApiResponse(responseCode = "200", description = "Sync updated")
    @ApiResponse(responseCode = "404", description = "Binding not found")
    @PatchMapping("/projects/{projectId}/binding")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> updateSync(
            @PathVariable String projectId,
            @RequestBody UpdateAzureBindingRequest request) {
        updateAzureProjectBindingSyncUseCase.execute(projectId, new UpdateAzureBindingInput(request.syncEnabled()));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "List all Azure DevOps project bindings for the organization")
    @ApiResponse(responseCode = "200", description = "Bindings listed")
    @GetMapping("/bindings")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureProjectBindingResponse>> listBindings() {
        String orgId = TenantContextHolder.get();
        List<AzureProjectBindingResponse> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndActiveTrue(orgId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(bindings);
    }

    @Operation(summary = "Get a specific Azure DevOps project binding")
    @ApiResponse(responseCode = "200", description = "Binding returned")
    @ApiResponse(responseCode = "404", description = "Binding not found")
    @GetMapping("/bindings/{bindingId}")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<AzureProjectBindingResponse> getBinding(@PathVariable String bindingId) {
        String orgId = TenantContextHolder.get();
        return azureProjectBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .map(b -> ResponseEntity.ok(toResponse(b)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update workflow config for a binding")
    @ApiResponse(responseCode = "200", description = "Workflow config updated")
    @ApiResponse(responseCode = "404", description = "Binding not found")
    @ApiResponse(responseCode = "403", description = "Missing AZURE_CONFIGURE permission")
    @ApiResponse(responseCode = "400", description = "Duplicate field mapping target")
    @PutMapping("/bindings/{bindingId}/workflow-config")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<AzureProjectBindingResponse> updateWorkflowConfig(
            @PathVariable String bindingId,
            @Valid @RequestBody UpdateWorkflowConfigRequest request) {
        var input = new UpdateWorkflowConfigInput(
                request.statusMappings() == null ? List.of() :
                        request.statusMappings().stream()
                                .map(s -> new UpdateWorkflowConfigInput.StatusMappingInput(
                                        s.workItemType(), s.rawStatus(), s.resolvedStatus()))
                                .toList(),
                request.typeMappings() == null ? List.of() :
                        request.typeMappings().stream()
                                .map(t -> new UpdateWorkflowConfigInput.TypeMappingInput(t.rawType(), t.resolvedType()))
                                .toList(),
                request.fieldMappings() == null ? List.of() :
                        request.fieldMappings().stream()
                                .map(f -> new UpdateWorkflowConfigInput.FieldMappingInput(
                                        f.internalField(), f.workItemType(),
                                        f.azureFieldReferenceName(), f.azureFieldType()))
                                .toList(),
                request.syncFilter() == null ? null :
                        new UpdateWorkflowConfigInput.WiqlFilterInput(
                                request.syncFilter().workItemTypes(),
                                request.syncFilter().areaPath(),
                                request.syncFilter().iterationPath()),
                request.hierarchyLevels() == null ? List.of() :
                        request.hierarchyLevels().stream()
                                .map(h -> new UpdateWorkflowConfigInput.HierarchyLevelInput(h.rawType(), h.rank()))
                                .toList()
        );
        AzureProjectBinding updated = updateWorkflowConfigUseCase.execute(bindingId, input);
        return ResponseEntity.ok(toResponse(updated));
    }

    @Operation(summary = "List work item types discovered on the bound Azure DevOps project")
    @ApiResponse(responseCode = "200", description = "Work item types listed")
    @ApiResponse(responseCode = "404", description = "Binding not found")
    @GetMapping("/bindings/{bindingId}/process/work-item-types")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureWorkItemTypeResponse>> listWorkItemTypes(@PathVariable String bindingId) {
        List<AzureWorkItemTypeResponse> types = listAzureWorkItemTypesUseCase.execute(bindingId)
                .stream()
                .map(t -> new AzureWorkItemTypeResponse(t.name(), t.referenceName()))
                .toList();
        return ResponseEntity.ok(types);
    }

    @Operation(summary = "List fields available for a work item type on the bound project")
    @ApiResponse(responseCode = "200", description = "Fields listed")
    @ApiResponse(responseCode = "404", description = "Binding or work item type not found")
    @ApiResponse(responseCode = "502", description = "Azure DevOps introspection call failed")
    @GetMapping("/bindings/{bindingId}/process/work-item-types/{typeName}/fields")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureWorkItemTypeFieldResponse>> listWorkItemTypeFields(
            @PathVariable String bindingId, @PathVariable String typeName) {
        List<AzureWorkItemTypeFieldResponse> fields = listAzureWorkItemTypeFieldsUseCase.execute(bindingId, typeName)
                .stream()
                .map(f -> new AzureWorkItemTypeFieldResponse(f.referenceName(), f.name(), f.type(), f.required()))
                .toList();
        return ResponseEntity.ok(fields);
    }

    @Operation(summary = "List real Azure DevOps states for a work item type on the bound project")
    @ApiResponse(responseCode = "200", description = "States listed")
    @ApiResponse(responseCode = "404", description = "Binding or work item type not found")
    @ApiResponse(responseCode = "422", description = "Azure DevOps introspection call failed")
    @GetMapping("/bindings/{bindingId}/process/work-item-types/{typeName}/states")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureWorkItemTypeStateResponse>> listWorkItemTypeStates(
            @PathVariable String bindingId, @PathVariable String typeName) {
        List<AzureWorkItemTypeStateResponse> states = listAzureWorkItemTypeStatesUseCase.execute(bindingId, typeName)
                .stream()
                .map(s -> new AzureWorkItemTypeStateResponse(s.name(), s.color(), s.category()))
                .toList();
        return ResponseEntity.ok(states);
    }

    @Operation(summary = "List sibling Azure DevOps bindings on the same organization, excluding the given binding")
    @ApiResponse(responseCode = "200", description = "Siblings listed")
    @GetMapping("/bindings/{bindingId}/siblings")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureSiblingBindingResponse>> listSiblings(@PathVariable String bindingId) {
        List<AzureSiblingBindingResponse> siblings = listSiblingAzureProjectBindingsUseCase.execute(bindingId)
                .stream()
                .map(b -> new AzureSiblingBindingResponse(b.getId(), b.getAzureProjectName()))
                .toList();
        return ResponseEntity.ok(siblings);
    }

    private AzureProjectBindingResponse toResponse(AzureProjectBinding binding) {
        WorkflowConfigResponse wfConfig = null;
        if (binding.getWorkflowConfig() != null) {
            var wf = binding.getWorkflowConfig();
            List<WorkflowConfigResponse.StatusMappingResponse> statusMappings = wf.getStatusMappings() == null
                    ? List.of()
                    : wf.getStatusMappings().stream()
                            .map(s -> new WorkflowConfigResponse.StatusMappingResponse(
                                    s.getWorkItemType(), s.getRawStatus(), s.getResolvedStatus()))
                            .toList();
            List<WorkflowConfigResponse.TypeMappingResponse> typeMappings = wf.getTypeMappings() == null
                    ? List.of()
                    : wf.getTypeMappings().stream()
                            .map(t -> new WorkflowConfigResponse.TypeMappingResponse(t.getRawType(), t.getResolvedType()))
                            .toList();
            List<WorkflowConfigResponse.FieldMappingResponse> fieldMappings = wf.getFieldMappings() == null
                    ? List.of()
                    : wf.getFieldMappings().stream()
                            .map(f -> new WorkflowConfigResponse.FieldMappingResponse(
                                    f.getInternalField(), f.getWorkItemType(), f.getAzureFieldReferenceName(), f.getAzureFieldType()))
                            .toList();
            // Surface the implicit default mappings (complexity-effort D5) of DEFAULT-config bindings, one per
            // mapped type, so callers see the effective mapping without knowing the default-fallback rule.
            if (wf.getConfigSource() == WorkflowConfig.ConfigSource.DEFAULT && wf.getTypeMappings() != null) {
                List<WorkflowConfigResponse.FieldMappingResponse> surfaced = new java.util.ArrayList<>(fieldMappings);
                for (FieldMapping.InternalFieldTarget target : List.of(
                        FieldMapping.InternalFieldTarget.COMPLEXITY, FieldMapping.InternalFieldTarget.EFFORT)) {
                    if (surfaced.stream().anyMatch(f -> f.internalField() == target)) continue;
                    for (var typeMapping : wf.getTypeMappings()) {
                        wf.resolveFieldMapping(target, typeMapping.getRawType()).ifPresent(m ->
                                surfaced.add(new WorkflowConfigResponse.FieldMappingResponse(
                                        m.getInternalField(), typeMapping.getRawType(),
                                        m.getAzureFieldReferenceName(), m.getAzureFieldType())));
                    }
                }
                fieldMappings = surfaced;
            }
            WorkflowConfigResponse.WiqlFilterResponse syncFilter = wf.getSyncFilter() == null ? null :
                    new WorkflowConfigResponse.WiqlFilterResponse(
                            wf.getSyncFilter().getWorkItemTypes(),
                            wf.getSyncFilter().getAreaPath(),
                            wf.getSyncFilter().getIterationPath());
            List<WorkflowConfigResponse.HierarchyLevelResponse> hierarchyLevels = wf.getHierarchyLevels() == null
                    ? List.of()
                    : wf.getHierarchyLevels().stream()
                            .map(h -> new WorkflowConfigResponse.HierarchyLevelResponse(h.getRawType(), h.getRank()))
                            .toList();
            wfConfig = new WorkflowConfigResponse(statusMappings, typeMappings, fieldMappings, syncFilter,
                    wf.getConfigSource() != null ? wf.getConfigSource().name() : null, hierarchyLevels);
        }
        return new AzureProjectBindingResponse(
                binding.getId(),
                binding.getProjectId(),
                binding.getAzureProjectId(),
                binding.getAzureProjectName(),
                binding.isSyncEnabled(),
                binding.getLastSyncAt(),
                wfConfig
        );
    }
}
