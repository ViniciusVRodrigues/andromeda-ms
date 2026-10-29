package andromeda.sei.ms.feature.aiusage.presentation;

import andromeda.sei.ms.feature.aiusage.application.dto.UnpricedModelOutput;
import andromeda.sei.ms.feature.aiusage.application.usecase.ListUnpricedModelsUseCase;
import andromeda.sei.ms.feature.aiusage.domain.exception.AiUsagePeriodInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc ({@code @WebMvcTest} does not run under Boot 4 here) for 200/400, and a
 * method-security context for the 403 that standalone MockMvc never evaluates.
 */
class AiUsageControllerUnpricedModelsTest {

    private final ListUnpricedModelsUseCase useCase = mock(ListUnpricedModelsUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        AiUsageController controller = new AiUsageController(null, null, null, null, null, null, null, useCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return200_withOpenPeriodByDefault() throws Exception {
        when(useCase.execute("org-1", null, null)).thenReturn(List.of(new UnpricedModelOutput("gpt-6", 10L, 2, true, List.of("OUTPUT"), 0L, 0L, 0L)));

        mockMvc.perform(get("/api/v1/ai/unpriced-models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("gpt-6"))
                .andExpect(jsonPath("$[0].outputTokens").value(10))
                .andExpect(jsonPath("$[0].records").value(2))
                .andExpect(jsonPath("$[0].billable").value(true));
    }

    @Test
    void should_passFromAndTo() throws Exception {
        when(useCase.execute("org-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ai/unpriced-models?from=2026-09-01&to=2026-09-30")).andExpect(status().isOk());

        verify(useCase).execute("org-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void should_return400_whenFromIsAfterTo() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 2);
        LocalDate to = LocalDate.of(2026, 10, 1);
        when(useCase.execute("org-1", from, to)).thenThrow(new AiUsagePeriodInvalidException(from, to));

        mockMvc.perform(get("/api/v1/ai/unpriced-models?from=2026-10-02&to=2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_USAGE_PERIOD_INVALID"));
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean AiUsageController controller() {
            return new AiUsageController(null, null, null, null, null, null, null, mock(ListUnpricedModelsUseCase.class));
        }
    }

    @Test
    void should_return403_withoutPriceTableManage_and_allowWithIt() {
        try (var ctx = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            AiUsageController secured = ctx.getBean(AiUsageController.class);

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "u", "n", AuthorityUtils.createAuthorityList("AI_READ_ALL")));
            assertThatThrownBy(() -> secured.unpricedModels(null, null)).isInstanceOf(AccessDeniedException.class);

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "u", "n", AuthorityUtils.createAuthorityList("PRICE_TABLE_MANAGE")));
            assertThat(secured.unpricedModels(null, null).getStatusCode().is2xxSuccessful()).isTrue();
        }
    }
}
