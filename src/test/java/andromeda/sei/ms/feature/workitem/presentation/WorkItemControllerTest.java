package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.workitem.application.dto.UpdateWorkItemManualFieldsInput;
import andromeda.sei.ms.feature.workitem.application.dto.WorkItemChildrenOutput;
import andromeda.sei.ms.feature.workitem.application.usecase.ListWorkItemChildrenUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.UpdateWorkItemManualFieldsUseCase;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkItemControllerTest {

    @Mock
    private UpdateWorkItemManualFieldsUseCase updateWorkItemManualFieldsUseCase;

    @Mock
    private ListWorkItemChildrenUseCase listWorkItemChildrenUseCase;

    @InjectMocks
    private WorkItemController controller;

    private MockMvc mockMvc;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateManualFields_shouldReturn200WithUpdatedFields() throws Exception {
        WorkItem updated = workItem();
        updated.setComplexity(8.0);
        updated.setComplexitySource(FieldValueSource.MANUAL_OVERRIDE);
        when(updateWorkItemManualFieldsUseCase.execute(eq("wi-1"), eq("user-1"), any(UpdateWorkItemManualFieldsInput.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/work-items/wi-1/manual-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"complexity": 8, "clearComplexityOverride": false, "clearEffortOverride": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complexity").value(8.0))
                .andExpect(jsonPath("$.complexitySource").value("MANUAL_OVERRIDE"));
    }

    @Test
    void updateManualFields_shouldReturn404WhenWorkItemNotFound() throws Exception {
        when(updateWorkItemManualFieldsUseCase.execute(eq("missing"), eq("user-1"), any(UpdateWorkItemManualFieldsInput.class)))
                .thenThrow(new WorkItemNotFoundException("missing"));

        mockMvc.perform(patch("/api/v1/work-items/missing/manual-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"complexity": 5, "clearComplexityOverride": false, "clearEffortOverride": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateManualFields_shouldReturn200WhenClearingEffortOverride() throws Exception {
        WorkItem updated = workItem();
        updated.setEffort(13.0);
        updated.setEffortSource(FieldValueSource.AZURE_SYNC);
        when(updateWorkItemManualFieldsUseCase.execute(eq("wi-1"), eq("user-1"), any(UpdateWorkItemManualFieldsInput.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/work-items/wi-1/manual-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clearComplexityOverride": false, "clearEffortOverride": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effortSource").value("AZURE_SYNC"));
    }

    @Test
    void listChildren_shouldReturnChildren() throws Exception {
        WorkItem child = workItem();
        when(listWorkItemChildrenUseCase.execute("wi-1"))
                .thenReturn(new WorkItemChildrenOutput(List.of(child), false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/work-items/wi-1/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.content[0].externalId").value("ext-1"));
    }

    @Test
    void listChildren_shouldReturnEmptyListForLeaf() throws Exception {
        when(listWorkItemChildrenUseCase.execute("wi-1"))
                .thenReturn(new WorkItemChildrenOutput(List.of(), false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/work-items/wi-1/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listChildren_shouldReturn404WhenWorkItemNotFound() throws Exception {
        when(listWorkItemChildrenUseCase.execute("missing"))
                .thenThrow(new WorkItemNotFoundException("missing"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/work-items/missing/children"))
                .andExpect(status().isNotFound());
    }

    private WorkItem workItem() {
        return new WorkItem("wi-1", "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", NOW, "Title", "Bug", WorkItem.ResolvedType.BUG,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }
}
