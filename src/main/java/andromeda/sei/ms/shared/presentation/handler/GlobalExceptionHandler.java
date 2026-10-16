package andromeda.sei.ms.shared.presentation.handler;

import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.PullRequestCommitsUnavailableException;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;
import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;
import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;
import andromeda.sei.ms.shared.domain.exception.UnauthorizedException;
import andromeda.sei.ms.shared.presentation.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ResourceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.from(ex));
    }

    /**
     * Cooldown ou fila de sync cheia: o cliente deve esperar {@code Retry-After} segundos. O
     * header e o contrato que o frontend usa para mostrar a contagem regressiva.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(ProviderInvalidException.class)
    public ResponseEntity<ErrorResponse> handleProviderInvalid(ProviderInvalidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.from(ex));
    }

/**
     * The VCS provider failed while serving one section (a pull request's commits) whose rest is
     * valid. 502 (vcs-repository-read-api): the upstream provider, not this service, failed.
     */
    @ExceptionHandler(PullRequestCommitsUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCommitsUnavailable(
            PullRequestCommitsUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.from(ex));
    }

    /* Loga só campo e código da restrição: o valor rejeitado pode ser dado pessoal (o
     * POST /usage-events/public é anônimo). Por isso spring.mvc.log-resolved-exception fica
     * false no application.yaml, senão o DevTools loga a exceção inteira, valor incluso. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + "=" + e.getCode())
                .collect(Collectors.joining(", ")));
        var message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    /**
     * A @PreAuthorize denial is a 403, not a server fault. Without this handler it fell through to
     * the catch-all below and surfaced as INTERNAL_ERROR/500, which made "this role is missing a
     * permission" look identical to "the backend crashed" (see WORK_ITEM_MANAGE missing from
     * RoleSeeder.ALL_PERMISSIONS -- every manual-fields save reported 500).
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", "Permissão insuficiente para esta operação"));
    }

    /**
     * A malformed/unbindable request body is a client error (400), not a server fault. Without
     * this it fell through to the catch-all and reported INTERNAL_ERROR/500 -- which is exactly
     * how a partial PATCH body omitting a primitive-typed flag looked like a backend crash.
     */
    /* Corrida entre duas escritas que o índice único barrou (a pré-checagem do caso de uso
     * não viu a outra): é conflito do cliente, não erro do servidor. */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(org.springframework.dao.DuplicateKeyException ex) {
        /* sem a mensagem do driver: ela traz o valor da chave (pode ser um e-mail) */
        log.warn("Duplicate key rejected by a unique index");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("RESOURCE_CONFLICT", "O recurso já existe ou foi alterado ao mesmo tempo"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("MALFORMED_REQUEST", "Corpo da requisição inválido"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Content-Type não suportado"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // The response body is deliberately generic (no internals leaked to callers), so the
        // stack trace must be logged here -- otherwise unexpected 500s are invisible in ops.
        log.error("Unhandled exception: {}", ex.toString(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
