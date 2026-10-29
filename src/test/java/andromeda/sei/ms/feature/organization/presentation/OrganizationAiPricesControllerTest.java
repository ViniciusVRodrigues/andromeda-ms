package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateAiPricesInput;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAiPricesUseCase;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** custo-ia-saida 1.5: price validation of PUT /organizations/ai-prices. */
class OrganizationAiPricesControllerTest {

    private final UpdateAiPricesUseCase useCase = mock(UpdateAiPricesUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        var controller = new OrganizationController(null, null, null, useCase, null,
                new OrganizationPresentationMapper(), null, null, null, null);
        var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private ResultActions putPrices(String body) throws Exception {
        return mockMvc.perform(put("/api/v1/organizations/ai-prices").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void should_return400_whenAPriceIsNegative() throws Exception {
        putPrices("{\"entries\":[{\"model\":\"m\",\"pricePerKInput\":1,\"pricePerKOutput\":-0.01}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        putPrices("{\"entries\":[{\"model\":\"m\",\"pricePerKInput\":-1,\"pricePerKOutput\":1}]}")
                .andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any(), any());
    }

    @Test
    void should_return400_whenOutputPriceIsMissing() throws Exception {
        putPrices("{\"entries\":[{\"model\":\"m\",\"pricePerKInput\":1}]}").andExpect(status().isBadRequest());
    }

    @Test
    void should_saveAndReturnNullInput_andAcceptZeroOutput() throws Exception {
        var output = new CreateOrganizationOutput("org-1", "n", "s", null, null, List.of(),
                List.of(new AiPriceEntry("free", null, BigDecimal.ZERO)), null, null);
        when(useCase.execute(eq("org-1"), any(UpdateAiPricesInput.class))).thenReturn(output);

        putPrices("{\"entries\":[{\"model\":\"free\",\"pricePerKOutput\":0}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPrices[0].pricePerKInput").value((Object) null))
                .andExpect(jsonPath("$.aiPrices[0].pricePerKOutput").value(0));

        ArgumentCaptor<UpdateAiPricesInput> in = ArgumentCaptor.forClass(UpdateAiPricesInput.class);
        verify(useCase).execute(eq("org-1"), in.capture());
        assertThat(in.getValue().entries().get(0).pricePerKInput()).isNull();
    }

    @Test
    void should_return400_whenACachePriceIsNegative() throws Exception {
        putPrices("{\"entries\":[{\"model\":\"m\",\"pricePerKOutput\":1,\"pricePerKCacheRead\":-0.01}]}")
                .andExpect(status().isBadRequest());
        putPrices("{\"entries\":[{\"model\":\"m\",\"pricePerKOutput\":1,\"pricePerKCacheWrite\":-0.01}]}")
                .andExpect(status().isBadRequest());
        verify(useCase, never()).execute(any(), any());
    }

    @Test
    void should_saveAndReturnCachePrices_andNullWhenAbsent() throws Exception {
        var output = new CreateOrganizationOutput("org-1", "n", "s", null, null, List.of(),
                List.of(new AiPriceEntry("m", null, BigDecimal.ONE, new BigDecimal("0.0005"), new BigDecimal("0.00625")),
                        new AiPriceEntry("n", null, BigDecimal.ONE)), null, null);
        when(useCase.execute(eq("org-1"), any(UpdateAiPricesInput.class))).thenReturn(output);

        putPrices("{\"entries\":[{\"model\":\"m\",\"pricePerKOutput\":1,\"pricePerKCacheRead\":0.0005,\"pricePerKCacheWrite\":0.00625},"
                + "{\"model\":\"n\",\"pricePerKOutput\":1}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPrices[0].pricePerKCacheRead").value(0.0005))
                .andExpect(jsonPath("$.aiPrices[0].pricePerKCacheWrite").value(0.00625))
                .andExpect(jsonPath("$.aiPrices[1].pricePerKCacheRead").value((Object) null))
                .andExpect(jsonPath("$.aiPrices[1].pricePerKCacheWrite").value((Object) null));

        ArgumentCaptor<UpdateAiPricesInput> in = ArgumentCaptor.forClass(UpdateAiPricesInput.class);
        verify(useCase).execute(eq("org-1"), in.capture());
        assertThat(in.getValue().entries().get(0).pricePerKCacheRead()).isEqualByComparingTo("0.0005");
        assertThat(in.getValue().entries().get(1).pricePerKCacheWrite()).isNull();
    }
}
