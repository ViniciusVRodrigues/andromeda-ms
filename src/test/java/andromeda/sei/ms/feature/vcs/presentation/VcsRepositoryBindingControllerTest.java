package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.vcs.application.dto.BindRepositoryInput;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.dto.BranchListOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBranchNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBranchesInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoSyncRunningException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsListBranchesFailedException;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.BindRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListBindableRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryBindingsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.BindingBranchSettings;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryBranchesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UpdateRepositoryBindingUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UnbindRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoAlreadyBoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoIdInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** vcs-core task 5.4 (standalone MockMvc; {@code @WebMvcTest} unavailable under Boot 4 here). */
@ExtendWith(MockitoExtension.class)
class VcsRepositoryBindingControllerTest {

    @Mock private ListBindableRepositoriesUseCase listBindableRepositoriesUseCase;
    @Mock private BindRepositoryUseCase bindRepositoryUseCase;
    @Mock private ListRepositoryBindingsUseCase listRepositoryBindingsUseCase;
    @Mock private UpdateRepositoryBindingUseCase updateRepositoryBindingUseCase;
    @Mock private ListRepositoryBranchesUseCase listRepositoryBranchesUseCase;
    @Mock private UnbindRepositoryUseCase unbindRepositoryUseCase;

    @InjectMocks
    private VcsRepositoryBindingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void should_listAvailable_withContractFields() throws Exception {
        when(listBindableRepositoriesUseCase.execute(VcsProvider.GITHUB)).thenReturn(List.of(new BindableRepositoryOutput(
                VcsProvider.GITHUB, "123", "octo/hello", null, null, "main", false, "proj-9",
                BindableRepositoryOutput.Source.CACHED)));

        mockMvc.perform(get("/api/v1/repos/available").param("provider", "GITHUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("GITHUB"))
                .andExpect(jsonPath("$[0].externalRepoId").value("123"))
                .andExpect(jsonPath("$[0].displayName").value("octo/hello"))
                .andExpect(jsonPath("$[0].defaultBranch").value("main"))
                .andExpect(jsonPath("$[0].bindable").value(false))
                .andExpect(jsonPath("$[0].boundToProjectId").value("proj-9"))
                .andExpect(jsonPath("$[0].source").value("CACHED"));
    }

    @Test
    void should_answer409_when_noConnectionForProvider() throws Exception {
        when(listBindableRepositoriesUseCase.execute(VcsProvider.AZURE_REPOS))
                .thenThrow(new VcsConnectionNotFoundException("AZURE_REPOS"));

        mockMvc.perform(get("/api/v1/repos/available").param("provider", "AZURE_REPOS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VCS_CONNECTION_NOT_FOUND"));
    }

    @Test
    void should_bindByFullName_andReturnTheBinding() throws Exception {
        when(bindRepositoryUseCase.execute(eq("proj-1"), any())).thenReturn(output(true));

        mockMvc.perform(post("/api/v1/repos/projects/proj-1/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"GITHUB","fullName":"octo/hello","syncEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("b-1"))
                .andExpect(jsonPath("$.projectId").value("proj-1"))
                .andExpect(jsonPath("$.repositoryId").value("repo-1"))
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.displayName").value("octo/hello"))
                .andExpect(jsonPath("$.syncEnabled").value(true));

        verify(bindRepositoryUseCase).execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, null, "octo/hello", true));
    }

    @Test
    void should_acceptLegacyRepoIdBody_replayedBy308() throws Exception {
        when(bindRepositoryUseCase.execute(eq("proj-1"), any())).thenReturn(output(true));

        mockMvc.perform(post("/api/v1/repos/projects/proj-1/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repoId":"octo/hello","syncEnabled":true}
                                """))
                .andExpect(status().isOk());

        verify(bindRepositoryUseCase).execute("proj-1", new BindRepositoryInput(null, null, "octo/hello", true));
    }

    @Test
    void should_answer409_when_repositoryBoundElsewhere() throws Exception {
        when(bindRepositoryUseCase.execute(eq("proj-1"), any())).thenThrow(new RepoAlreadyBoundException("octo/hello"));

        mockMvc.perform(post("/api/v1/repos/projects/proj-1/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GITHUB\",\"externalRepoId\":\"123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPO_ALREADY_BOUND"));
    }

    @Test
    void should_answer422_when_nameMalformed() throws Exception {
        when(bindRepositoryUseCase.execute(eq("proj-1"), any())).thenThrow(new RepoIdInvalidException("nope"));

        mockMvc.perform(post("/api/v1/repos/projects/proj-1/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"nope\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REPO_ID_INVALID"));
    }

    @Test
    void should_answer404_when_providerDoesNotKnowTheRepository() throws Exception {
        when(bindRepositoryUseCase.execute(eq("proj-1"), any())).thenThrow(new RepoNotFoundException("octo/ghost"));

        mockMvc.perform(post("/api/v1/repos/projects/proj-1/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"octo/ghost\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND"));
    }

    @Test
    void should_listOrganizationAndProjectBindings() throws Exception {
        when(listRepositoryBindingsUseCase.execute(null)).thenReturn(List.of(output(true)));
        when(listRepositoryBindingsUseCase.execute("proj-1")).thenReturn(List.of(output(false)));

        mockMvc.perform(get("/api/v1/repos/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].syncEnabled").value(true));
        mockMvc.perform(get("/api/v1/repos/projects/proj-1/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].syncEnabled").value(false));
    }

    @Test
    void should_toggleSync_withTheOldBody() throws Exception {
        when(updateRepositoryBindingUseCase.execute("b-1", new BindingBranchSettings.Change(false, null, null, null)))
                .thenReturn(output(false));

        mockMvc.perform(patch("/api/v1/repos/bindings/b-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"syncEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(false));
    }

    @Test
    void should_patchBranchesAndReturnTheContractFields() throws Exception {
        when(updateRepositoryBindingUseCase.execute("b-1", new BindingBranchSettings.Change(
                null, List.of("develop", "main"), "main", true))).thenReturn(output(true));

        mockMvc.perform(patch("/api/v1/repos/bindings/b-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branches\":[\"develop\",\"main\"],\"productionBranch\":\"main\","
                                + "\"filterPullRequestsByBranch\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultBranch").value("develop"))
                .andExpect(jsonPath("$.branches[1]").value("main"))
                .andExpect(jsonPath("$.productionBranch").value("main"))
                .andExpect(jsonPath("$.branchesConfigured").value(true))
                .andExpect(jsonPath("$.filterPullRequestsByBranch").value(true))
                .andExpect(jsonPath("$.missingBranches").isArray())
                .andExpect(jsonPath("$.syncState").value("PENDING"))
                .andExpect(jsonPath("$.maxBranches").value(10))
                .andExpect(jsonPath("$.backfillDays").value(90))
                .andExpect(jsonPath("$.resynced").value(true));
    }

    @Test
    void should_mapTheBranchErrors() throws Exception {
        when(updateRepositoryBindingUseCase.execute(eq("b-1"), any()))
                .thenThrow(new RepoSyncRunningException("b-1"))
                .thenThrow(new RepoBranchesInvalidException("at most 10 branches"))
                .thenThrow(new RepoBranchNotFoundException(List.of("gone")));

        mockMvc.perform(patch("/api/v1/repos/bindings/b-1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPO_SYNC_RUNNING"));
        mockMvc.perform(patch("/api/v1/repos/bindings/b-1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REPO_BRANCHES_INVALID"));
        mockMvc.perform(patch("/api/v1/repos/bindings/b-1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REPO_BRANCH_NOT_FOUND"));
    }

    @Test
    void should_bindWithBranches() throws Exception {
        when(bindRepositoryUseCase.execute(eq("proj-1"), any())).thenReturn(output(true));

        mockMvc.perform(post("/api/v1/repos/projects/proj-1/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GITHUB\",\"externalRepoId\":\"123\","
                                + "\"branches\":[\"develop\",\"main\"],\"productionBranch\":\"main\","
                                + "\"filterPullRequestsByBranch\":true}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<BindRepositoryInput> captor = org.mockito.ArgumentCaptor.forClass(BindRepositoryInput.class);
        verify(bindRepositoryUseCase).execute(eq("proj-1"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().branches()).containsExactly("develop", "main");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().productionBranch()).isEqualTo("main");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().filterPullRequestsByBranch()).isTrue();
    }

    @Test
    void should_listAvailableBranches_withTheContractShape() throws Exception {
        when(listRepositoryBranchesUseCase.ofAvailable(VcsProvider.AZURE_REPOS, "guid-1", null)).thenReturn(
                new BranchListOutput(List.of(new VcsBranch("develop", true), new VcsBranch("main", false)), 2, false));

        mockMvc.perform(get("/api/v1/repos/available/branches").param("provider", "AZURE_REPOS")
                        .param("externalRepoId", "guid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[0].name").value("develop"))
                .andExpect(jsonPath("$.branches[0].isDefault").value(true))
                .andExpect(jsonPath("$.branches[1].isDefault").value(false))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void should_listBindingBranches_and404ForForeignBinding() throws Exception {
        when(listRepositoryBranchesUseCase.ofBinding("b-1")).thenReturn(
                new BranchListOutput(List.of(new VcsBranch("main", true)), 1, true));
        when(listRepositoryBranchesUseCase.ofBinding("b-2")).thenThrow(new RepoBindingNotFoundException("b-2"));

        mockMvc.perform(get("/api/v1/repos/bindings/b-1/branches"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.truncated").value(true));
        mockMvc.perform(get("/api/v1/repos/bindings/b-2/branches"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPO_BINDING_NOT_FOUND"));
    }

    @Test
    void should_answer409_404_and502_forTheListingErrors() throws Exception {
        when(listRepositoryBranchesUseCase.ofAvailable(eq(VcsProvider.GITHUB), any(), any()))
                .thenThrow(new VcsConnectionNotFoundException("GITHUB"))
                .thenThrow(new RepoNotFoundException("x"))
                .thenThrow(new VcsListBranchesFailedException(new IllegalStateException("down")));

        mockMvc.perform(get("/api/v1/repos/available/branches").param("fullName", "a/b"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VCS_CONNECTION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/repos/available/branches").param("fullName", "a/b"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPO_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/repos/available/branches").param("fullName", "a/b"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("VCS_LIST_BRANCHES_FAILED"));
    }

    @Test
    void should_requireReposConfigureOnTheNewRoutes() throws Exception {
        for (String method : new String[]{"update", "listAvailableBranches", "listBindingBranches"}) {
            java.lang.reflect.Method handler = java.util.Arrays.stream(
                            andromeda.sei.ms.feature.vcs.presentation.VcsRepositoryBindingController.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
            org.springframework.security.access.prepost.PreAuthorize pre = handler
                    .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            org.assertj.core.api.Assertions.assertThat(pre.value()).isEqualTo("hasAuthority('REPOS_CONFIGURE')");
        }
    }

    @Test
    void should_unbind_with204_and404ForForeignBinding() throws Exception {
        mockMvc.perform(delete("/api/v1/repos/projects/proj-1/bindings/b-1")).andExpect(status().isNoContent());
        verify(unbindRepositoryUseCase).execute("proj-1", "b-1");

        doThrow(new RepoBindingNotFoundException("b-2")).when(unbindRepositoryUseCase).execute("proj-1", "b-2");
        mockMvc.perform(delete("/api/v1/repos/projects/proj-1/bindings/b-2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_BINDING_NOT_FOUND"));
    }

    private static RepoBindingOutput output(boolean syncEnabled) {
        return new RepoBindingOutput("b-1", "proj-1", "repo-1", VcsProvider.GITHUB, "octo/hello", syncEnabled,
                Instant.parse("2026-09-01T00:00:00Z"), "develop", List.of("develop", "main"), "main", true, true,
                List.of(), VcsSyncState.PENDING, 10, 90, true);
    }

    @Test
    void should_listAzureRepositories_withTheAdoProjectGuid() throws Exception {
        when(listBindableRepositoriesUseCase.execute(VcsProvider.AZURE_REPOS)).thenReturn(List.of(new BindableRepositoryOutput(
                VcsProvider.AZURE_REPOS, "278d5cd2-584d-4b63-824a-2ba458937249", "Fabrikam-Fiber-Git",
                "6ce954b1-ce1f-45d1-b94d-e6bf2464ba2c", "Fabrikam-Fiber-Git", "master", true, null,
                BindableRepositoryOutput.Source.LIVE)));

        mockMvc.perform(get("/api/v1/repos/available").param("provider", "AZURE_REPOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("AZURE_REPOS"))
                .andExpect(jsonPath("$[0].externalRepoId").value("278d5cd2-584d-4b63-824a-2ba458937249"))
                .andExpect(jsonPath("$[0].externalProjectId").value("6ce954b1-ce1f-45d1-b94d-e6bf2464ba2c"))
                .andExpect(jsonPath("$[0].externalProjectName").value("Fabrikam-Fiber-Git"))
                .andExpect(jsonPath("$[0].bindable").value(true));
    }

    @Test
    void should_answer403_when_theAzureCredentialLacksCodeRead() throws Exception {
        when(listBindableRepositoriesUseCase.execute(VcsProvider.AZURE_REPOS))
                .thenThrow(new andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException());

        mockMvc.perform(get("/api/v1/repos/available").param("provider", "AZURE_REPOS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AZURE_REPOS_SCOPE_MISSING"));
    }

    @Test
    void should_answer403_when_bindingAnAzureRepositoryWithoutCodeRead() throws Exception {
        when(bindRepositoryUseCase.execute(org.mockito.ArgumentMatchers.eq("proj-1"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/repos/projects/proj-1/bindings")
                        .contentType("application/json")
                        .content("{\"provider\":\"AZURE_REPOS\",\"externalRepoId\":\"r-1\",\"syncEnabled\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AZURE_REPOS_SCOPE_MISSING"));
    }
}
