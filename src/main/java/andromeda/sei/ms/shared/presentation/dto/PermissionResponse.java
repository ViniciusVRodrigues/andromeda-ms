package andromeda.sei.ms.shared.presentation.dto;

/** @param scope {@code ORG_ONLY} or {@code PROJECT_SCOPABLE} */
public record PermissionResponse(String code, String label, String scope) {}
