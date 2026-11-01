package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.AllocationPolicyOutput;
import andromeda.sei.ms.feature.organization.application.usecase.GetAllocationPolicyUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAllocationPolicyUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.AllocationPolicyRequest;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc like the other controllers; the PreAuthorize of each endpoint is checked by reflection. */
@ExtendWith(MockitoExtension.class)
class OrganizationAllocationPolicyControllerTest {

    private static final String URL = "/api/v1/organizations/me/allocation-policy";

    @Mock private GetAllocationPolicyUseCase getUseCase;
    @Mock private UpdateAllocationPolicyUseCase updateUseCase;

    @InjectMocks private OrganizationAllocationPolicyController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_returnTheCap_when_getting() throws Exception {
        when(getUseCase.execute("org-1")).thenReturn(new AllocationPolicyOutput(15));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outOfDeliveryCapPercent").value(15));
    }

    @Test
    void should_updateTheCap_when_inRange() throws Exception {
        when(updateUseCase.execute("org-1", 30)).thenReturn(new AllocationPolicyOutput(30));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"outOfDeliveryCapPercent\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outOfDeliveryCapPercent").value(30));
    }

    @Test
    void should_acceptTheBounds_0_and_100() throws Exception {
        when(updateUseCase.execute("org-1", 0)).thenReturn(new AllocationPolicyOutput(0));
        when(updateUseCase.execute("org-1", 100)).thenReturn(new AllocationPolicyOutput(100));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"outOfDeliveryCapPercent\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"outOfDeliveryCapPercent\":100}"))
                .andExpect(status().isOk());
    }

    @Test
    void should_answer400ValidationError_when_outOfRangeOrMissing() throws Exception {
        for (String body : new String[]{"{\"outOfDeliveryCapPercent\":-1}", "{\"outOfDeliveryCapPercent\":101}", "{}"}) {
            mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verify(updateUseCase, never()).execute(anyString(), anyInt());
    }

    @Test
    void should_guardPutWithUserManage_andGetWithAuthentication() throws Exception {
        assertThat(OrganizationAllocationPolicyController.class
                .getMethod("update", AllocationPolicyRequest.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('USER_MANAGE')");
        assertThat(OrganizationAllocationPolicyController.class.getMethod("get")
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
}
