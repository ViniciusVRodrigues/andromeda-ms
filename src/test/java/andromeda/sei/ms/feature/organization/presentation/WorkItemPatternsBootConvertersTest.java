package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.usecase.ConfirmWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.GetWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.PreviewWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateWorkItemPatternsUseCase;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The request bodies read through the message converters Spring Boot auto-configures (as in production). */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = WorkItemPatternsBootConvertersTest.Config.class)
class WorkItemPatternsBootConvertersTest {

    @Configuration
    @EnableWebMvc
    @Import({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class})
    static class Config {
        @Bean OrganizationWorkItemPatternsController controller() {
            var update = mock(UpdateWorkItemPatternsUseCase.class);
            when(update.execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                    .thenThrow(new IllegalStateException("reached the use case"));
            return new OrganizationWorkItemPatternsController(mock(GetWorkItemPatternsUseCase.class), update,
                    mock(ConfirmWorkItemPatternsUseCase.class), mock(PreviewWorkItemPatternsUseCase.class),
                    mock(DetectWorkItemPatternsUseCase.class), new RateLimiter());
        }
        @Bean GlobalExceptionHandler advice() { return new GlobalExceptionHandler(); }
    }

    @Autowired WebApplicationContext wac;

    @Test
    void should_notAnswer400_when_putHasObjectPatterns() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // 500 = body was read and the (mocked) use case was reached; 400 = MALFORMED_REQUEST
        var res = mvc.perform(put("/api/v1/organizations/me/work-item-patterns").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"patterns\":[{\"regex\":\"(?i)\\\\QAB#\\\\E(\\\\d+)(?!\\\\d)\",\"alsoInText\":true}]}"))
                .andReturn();
        int s = res.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(s).isNotEqualTo(400);
    }
}
