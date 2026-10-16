package andromeda.sei.ms.shared.presentation.handler;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;
import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn404ForResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("STUB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn409ForResourceConflictException() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUB_CONFLICT"));
    }

    @Test
    void shouldReturn422ForBusinessRuleException() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STUB_RULE"));
    }

    @Test
    void shouldReturn403ForForbiddenOperationException() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUB_FORBIDDEN"));
    }

    @Test
    void shouldReturn409WithoutTheKeyValue_whenAUniqueIndexRejectsARacingWrite() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ana@example.test"))));
    }

    @Test
    void shouldReturn500ForUnexpectedException() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void shouldNotExposeStackTraceInInternalError() throws Exception {
        mockMvc.perform(get("/stub").param("ex", "unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void shouldLogOnlyFieldAndCode_neverTheRejectedValue_onValidationError() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/stub").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"ana.souza@example.test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(m -> assertThat(m).contains("label=Size"))
                .noneSatisfy(m -> assertThat(m).contains("ana.souza"));
    }

    @Test
    void resolvedExceptionLoggingStaysOff_soDevToolsDoesNotLogRejectedValues() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        assertThat(String.valueOf(yaml.getObject().get("spring.mvc.log-resolved-exception"))).isEqualTo("false");
    }

    record StubBody(@Size(max = 5) String label) {}

    // ── Stub controller used only inside this test ──────────────────────────

    @RestController
    static class StubController {

        @GetMapping("/stub")
        void trigger(@RequestParam String ex) {
            switch (ex) {
                case "not-found"   -> throw new ResourceNotFoundException("STUB_NOT_FOUND", "stub not found");
                case "conflict"    -> throw new ResourceConflictException("STUB_CONFLICT", "stub conflict");
                case "business"    -> throw new BusinessRuleException("STUB_RULE", "stub rule");
                case "forbidden"   -> throw new ForbiddenOperationException("STUB_FORBIDDEN", "stub forbidden");
                case "unexpected"  -> throw new RuntimeException("unexpected internal failure");
                case "duplicate"   -> throw new org.springframework.dao.DuplicateKeyException(
                        "E11000 duplicate key error dup key: { email: \"ana@example.test\" }");
                default            -> { /* ok */ }
            }
        }

        @PostMapping("/stub")
        void validate(@Valid @RequestBody StubBody body) {
        }
    }
}
