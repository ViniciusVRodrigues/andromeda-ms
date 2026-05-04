package andromeda.sei.ms.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn404ForResourceNotFound() {
        var response = handler.handleNotFound(new ResourceNotFoundException("Not found"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldReturn422ForBusinessException() {
        var response = handler.handleBusiness(new BusinessException("Business rule violated"));
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_ERROR");
    }

    @Test
    void shouldReturn403ForForbiddenException() {
        var response = handler.handleForbidden(new ForbiddenException("Access denied"));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
