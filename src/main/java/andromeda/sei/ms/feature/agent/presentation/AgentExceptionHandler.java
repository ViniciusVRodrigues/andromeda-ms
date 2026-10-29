package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceInfoInvalidException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingConflictException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentVersionUnsupportedException;
import andromeda.sei.ms.shared.presentation.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Agent errors whose body carries more than {@code code/message/timestamp}. */
@RestControllerAdvice(basePackages = "andromeda.sei.ms.feature.agent.presentation")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentExceptionHandler {

    @ExceptionHandler(AgentVersionUnsupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupported(AgentVersionUnsupportedException e) {
        Map<String, Object> body = base(e.getCode(), e.getMessage());
        body.put("minVersion", e.getMinVersion());
        return ResponseEntity.status(HttpStatus.UPGRADE_REQUIRED).body(body);
    }

    @ExceptionHandler(AgentDeviceInfoInvalidException.class)
    public ResponseEntity<ErrorResponse> invalidDevice(AgentDeviceInfoInvalidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(e));
    }

    @ExceptionHandler(AgentPairingConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(AgentPairingConflictException e) {
        Map<String, Object> body = base(e.getCode(), e.getMessage());
        body.put("status", e.getPairingStatus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private static Map<String, Object> base(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now());
        return body;
    }
}
