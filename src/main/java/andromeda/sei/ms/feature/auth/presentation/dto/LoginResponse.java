package andromeda.sei.ms.feature.auth.presentation.dto;

public record LoginResponse(String accessToken, long expiresIn) {
}
