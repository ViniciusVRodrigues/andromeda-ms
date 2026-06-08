package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.BindAzureProjectInput;
import andromeda.sei.ms.feature.azure.application.dto.UpdateAzureBindingInput;
import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.application.usecase.BindAzureProjectUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureProjectsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.RemoveAzureProjectBindingUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureProjectBindingSyncUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateWorkflowConfigUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureProjectBindingResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureProjectResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.BindAzureProjectRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.UpdateAzureBindingRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.UpdateWorkflowConfigRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.WorkflowConfigResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final AzureProjectBindingRepository azureProjectBindingRepository;

    @Operation(summary = "List available Azure DevOps projects")
    @ApiResponse(responseCode = "200", description = "Projects listed")
    @ApiResponse(responseCode = "409", description = "No Azure connection established")
    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<List<AzureProjectResponse>> listProjects() {
        List<AzureProjectResponse> projects = listAzureProjectsUseCase.execute()
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
    public ResponseEntity<Void> bindProject(
            @PathVariable String projectId,
            @Valid @RequestBody BindAzureProjectRequest request) {
        bindAzureProjectUseCase.execute(projectId,
                new BindAzureProjectInput(request.azureProjectId(), request.azureProjectName()));
        return ResponseEntity.ok().build();
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
    @PutMapping("/bindings/{bindingId}/workflow-config")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<AzureProjectBindingResponse> updateWorkflowConfig(
            @PathVariable String bindingId,
            @Valid @RequestBody UpdateWorkflowConfigRequest request) {
        var input = new UpdateWorkflowConfigInput(
                request.statusMappings() == null ? List.of() :
                        request.statusMappings().stream()
                                .map(s -> new UpdateWorkflowConfigInput.StatusMappingInput(s.rawStatus(), s.resolvedStatus()))
                                .toList(),
                request.typeMappings() == null ? List.of() :
                        request.typeMappings().stream()
                                .map(t -> new UpdateWorkflowConfigInput.TypeMappingInput(t.rawType(), t.resolvedType()))
                                .toList()
        );
        AzureProjectBinding updated = updateWorkflowConfigUseCase.execute(bindingId, input);
        return ResponseEntity.ok(toResponse(updated));
    }

    private AzureProjectBindingResponse toResponse(AzureProjectBinding binding) {
        WorkflowConfigResponse wfConfig = null;
        if (binding.getWorkflowConfig() != null) {
            var wf = binding.getWorkflowConfig();
            List<WorkflowConfigResponse.StatusMappingResponse> statusMappings = wf.getStatusMappings() == null
                    ? List.of()
                    : wf.getStatusMappings().stream()
                            .map(s -> new WorkflowConfigResponse.StatusMappingResponse(s.getRawStatus(), s.getResolvedStatus()))
                            .toList();
            List<WorkflowConfigResponse.TypeMappingResponse> typeMappings = wf.getTypeMappings() == null
                    ? List.of()
                    : wf.getTypeMappings().stream()
                            .map(t -> new WorkflowConfigResponse.TypeMappingResponse(t.getRawType(), t.getResolvedType()))
                            .toList();
            wfConfig = new WorkflowConfigResponse(statusMappings, typeMappings,
                    wf.getConfigSource() != null ? wf.getConfigSource().name() : null);
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
