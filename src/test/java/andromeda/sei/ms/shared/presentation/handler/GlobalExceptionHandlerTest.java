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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                default            -> { /* ok */ }
            }
        }
    }
}
