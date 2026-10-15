package andromeda.sei.ms.feature.auth.application.dto;

public record CompleteAccountInput(String token, String name, String password, String funnelId) {
}
