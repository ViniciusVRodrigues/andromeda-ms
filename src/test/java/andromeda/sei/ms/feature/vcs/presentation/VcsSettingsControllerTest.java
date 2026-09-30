package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsInput;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.GetWorkItemLinkSettingsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UpdateWorkItemLinkSettingsUseCase;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** vcs-core task 5.4: work item link settings endpoints (standalone MockMvc). */
@ExtendWith(MockitoExtension.class)
class VcsSettingsControllerTest {

    @Mock private GetWorkItemLinkSettingsUseCase getWorkItemLinkSettingsUseCase;
    @Mock private UpdateWorkItemLinkSettingsUseCase updateWorkItemLinkSettingsUseCase;

    @InjectMocks
    private VcsSettingsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void should_readWorkItemLinkSettings() throws Exception {
        when(getWorkItemLinkSettingsUseCase.execute()).thenReturn(new WorkItemLinkSettingsOutput(true, List.of("AB#", "WI-")));

        mockMvc.perform(get("/api/v1/repos/settings/work-item-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.prefixes[1]").value("WI-"));
    }

    @Test
    void should_updateWorkItemLinkSettings() throws Exception {
        when(updateWorkItemLinkSettingsUseCase.execute(new WorkItemLinkSettingsInput(false, List.of("AB#"))))
                .thenReturn(new WorkItemLinkSettingsOutput(false, List.of()));

        mockMvc.perform(put("/api/v1/repos/settings/work-item-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"prefixes\":[\"AB#\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
