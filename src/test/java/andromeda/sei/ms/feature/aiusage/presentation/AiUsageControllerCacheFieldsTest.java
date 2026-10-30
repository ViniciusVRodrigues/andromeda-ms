package andromeda.sei.ms.feature.aiusage.presentation;

import andromeda.sei.ms.feature.aiusage.application.dto.AiUsageSummaryByMonthOutput;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryByMonthUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetDailyAiUsageUseCase;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc (as in the sibling tests): the cache fields reach the JSON of summary and daily. */
class AiUsageControllerCacheFieldsTest {

    private final GetAiUsageSummaryByMonthUseCase byMonth = mock(GetAiUsageSummaryByMonthUseCase.class);
    private final GetDailyAiUsageUseCase daily = mock(GetDailyAiUsageUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u1", null, AuthorityUtils.createAuthorityList("AI_READ_OWN")));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AiUsageController(null, byMonth, null, null, null, daily, null, null)).build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void summary_exposesTotalCacheTokens() throws Exception {
        when(byMonth.execute("org-1", "u1", YearMonth.of(2026, 10))).thenReturn(new AiUsageSummaryByMonthOutput(
                YearMonth.of(2026, 10), 10, 5, 3000, 400, 2, BigDecimal.ONE, List.of()));

        mockMvc.perform(get("/api/v1/ai/usage/summary").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInputTokens").value(10))
                .andExpect(jsonPath("$.totalCacheReadTokens").value(3000))
                .andExpect(jsonPath("$.totalCacheWriteTokens").value(400));
    }

    @Test
    void daily_exposesCacheTokens() throws Exception {
        when(daily.execute(any(), any(), any())).thenReturn(List.of(new GetDailyAiUsageUseCase.Day(
                LocalDate.of(2026, 10, 5), 10, 5, 3000, 400, 2, BigDecimal.ONE, false)));

        mockMvc.perform(get("/api/v1/ai/usage/daily").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cacheReadTokens").value(3000))
                .andExpect(jsonPath("$[0].cacheWriteTokens").value(400));
    }
}
