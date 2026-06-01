package andromeda.sei.ms.shared.presentation.dto;

import andromeda.sei.ms.shared.domain.exception.DomainException;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }

    public static ErrorResponse from(DomainException ex) {
        return ErrorResponse.of(ex.getCode(), ex.getMessage());
    }
}
