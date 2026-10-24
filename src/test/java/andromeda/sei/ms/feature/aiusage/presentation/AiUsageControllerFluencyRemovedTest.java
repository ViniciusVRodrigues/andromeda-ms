package andromeda.sei.ms.feature.aiusage.presentation;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryByMonthUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetDailyAiUsageUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** remover-fluency-score 2.1: the read route no longer exists. */
class AiUsageControllerFluencyRemovedTest {

    @Test
    void fluencyScoreRouteAnswers404() throws Exception {
        AiUsageController controller = new AiUsageController(
                Mockito.mock(GetAiUsageSummaryUseCase.class), Mockito.mock(GetAiUsageSummaryByMonthUseCase.class),
                null, null, Mockito.mock(RecalculateAiCostsUseCase.class), Mockito.mock(GetDailyAiUsageUseCase.class),
                Mockito.mock(CurrentUserPermissions.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/ai/fluency-score")).andExpect(status().isNotFound());
    }
}
