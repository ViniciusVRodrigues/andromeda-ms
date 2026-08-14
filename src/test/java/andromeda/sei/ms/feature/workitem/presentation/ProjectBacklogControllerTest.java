package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.usecase.BacklogFilter;
import andromeda.sei.ms.feature.workitem.application.usecase.ListProjectBacklogOrphansUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.ListProjectBacklogRootsUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectBacklogControllerTest {

    @Mock
    private ListProjectBacklogRootsUseCase listProjectBacklogRootsUseCase;

    @Mock
    private ListProjectBacklogOrphansUseCase listProjectBacklogOrphansUseCase;

    @Mock
    private AssigneeNameResolver assigneeNameResolver;

    @InjectMocks
    private ProjectBacklogController controller;

    private MockMvc mockMvc;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listRoots_shouldReturn200WithPage() throws Exception {
        WorkItem item = workItem("wi-1");
        when(listProjectBacklogRootsUseCase.execute(eq("proj-1"), anyInt(), anyInt(), any()))
                .thenReturn(new WorkItemPageResult(List.of(item), 0, 20, 1));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/roots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].externalId").value("wi-1"));
    }

    @Test
    void listRoots_shouldPassPaginationParams() throws Exception {
        when(listProjectBacklogRootsUseCase.execute(eq("proj-1"), eq(2), eq(50), any()))
                .thenReturn(new WorkItemPageResult(List.of(), 2, 50, 0));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/roots?page=2&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void listRoots_shouldReturn404WhenProjectNotFound() throws Exception {
        when(listProjectBacklogRootsUseCase.execute(eq("missing"), anyInt(), anyInt(), any()))
                .thenThrow(new ProjectNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/projects/missing/work-items/roots"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listOrphans_shouldReturn200WithPage() throws Exception {
        WorkItem item = workItem("wi-2");
        when(listProjectBacklogOrphansUseCase.execute(eq("proj-1"), anyInt(), anyInt(), any()))
                .thenReturn(new WorkItemPageResult(List.of(item), 0, 20, 1));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/orphans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalId").value("wi-2"));
    }

    @Test
    void listOrphans_shouldReturn404WhenProjectNotFound() throws Exception {
        when(listProjectBacklogOrphansUseCase.execute(eq("missing"), anyInt(), anyInt(), any()))
                .thenThrow(new ProjectNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/projects/missing/work-items/orphans"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRoots_shouldForwardFilterParamsToUseCase() throws Exception {
        ArgumentCaptor<BacklogFilter> captor = ArgumentCaptor.forClass(BacklogFilter.class);
        when(listProjectBacklogRootsUseCase.execute(eq("proj-1"), anyInt(), anyInt(), any()))
                .thenReturn(new WorkItemPageResult(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/roots")
                        .param("sprintId", "sprint-1")
                        .param("assigneeUserId", "user-1")
                        .param("search", "login"))
                .andExpect(status().isOk());

        verify(listProjectBacklogRootsUseCase).execute(eq("proj-1"), anyInt(), anyInt(), captor.capture());
        BacklogFilter filter = captor.getValue();
        assertThat(filter.sprintId()).isEqualTo("sprint-1");
        assertThat(filter.assigneeUserId()).isEqualTo("user-1");
        assertThat(filter.search()).isEqualTo("login");
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    void listRoots_shouldForwardTypeAndStatusFilters() throws Exception {
        ArgumentCaptor<BacklogFilter> captor = ArgumentCaptor.forClass(BacklogFilter.class);
        when(listProjectBacklogRootsUseCase.execute(eq("proj-1"), anyInt(), anyInt(), any()))
                .thenReturn(new WorkItemPageResult(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/roots")
                        .param("type", "bug")
                        .param("status", "DONE"))
                .andExpect(status().isOk());

        verify(listProjectBacklogRootsUseCase).execute(eq("proj-1"), anyInt(), anyInt(), captor.capture());
        /* minúsculas aceitas: o valor vem de um query param, não de um enum tipado no cliente. */
        assertThat(captor.getValue().resolvedType()).isEqualTo(WorkItem.ResolvedType.BUG);
        assertThat(captor.getValue().resolvedStatus()).isEqualTo(WorkItem.ResolvedStatus.DONE);
    }

    /**
     * Valor desconhecido vira "sem filtro" em vez de 400 — derrubar a listagem porque a UI
     * mandou um tipo que esta versão não conhece seria pior do que ignorá-lo.
     */
    @Test
    void listRoots_shouldIgnoreUnknownTypeInsteadOfFailing() throws Exception {
        ArgumentCaptor<BacklogFilter> captor = ArgumentCaptor.forClass(BacklogFilter.class);
        when(listProjectBacklogRootsUseCase.execute(eq("proj-1"), anyInt(), anyInt(), any()))
                .thenReturn(new WorkItemPageResult(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/roots").param("type", "NAO_EXISTE"))
                .andExpect(status().isOk());

        verify(listProjectBacklogRootsUseCase).execute(eq("proj-1"), anyInt(), anyInt(), captor.capture());
        assertThat(captor.getValue().resolvedType()).isNull();
        assertThat(captor.getValue().isEmpty()).isTrue();
    }

    /**
     * Sem parâmetros de filtro o controller precisa enviar um filtro vazio — é o que mantém a
     * visão em árvore; qualquer valor não-nulo achataria a lista.
     */
    @Test
    void listRoots_shouldSendEmptyFilterWhenNoParamsGiven() throws Exception {
        ArgumentCaptor<BacklogFilter> captor = ArgumentCaptor.forClass(BacklogFilter.class);
        when(listProjectBacklogRootsUseCase.execute(eq("proj-1"), anyInt(), anyInt(), any()))
                .thenReturn(new WorkItemPageResult(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/projects/proj-1/work-items/roots"))
                .andExpect(status().isOk());

        verify(listProjectBacklogRootsUseCase).execute(eq("proj-1"), anyInt(), anyInt(), captor.capture());
        assertThat(captor.getValue().isEmpty()).isTrue();
    }

    private WorkItem workItem(String externalId) {
        return new WorkItem(externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title", "Task", WorkItem.ResolvedType.TASK,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }
}
