package andromeda.sei.ms.feature.user.application.dto;

/**
 * @param callerUserId the authenticated user making the change — a caller cannot change their own
 *                     role ({@code USER_SELF_ROLE_CHANGE_DENIED})
 */
public record UpdateUserInput(String id, String name, String roleId, String seniority, String callerUserId) {
}
