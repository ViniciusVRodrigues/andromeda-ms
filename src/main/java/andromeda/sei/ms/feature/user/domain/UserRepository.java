package andromeda.sei.ms.feature.user.domain;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByIdAndOrganizationId(String id, String organizationId);

    List<User> findAllByOrganizationId(String organizationId);

    /** Active AND inactive users of the organization (read-only joins such as the usage export). */
    List<User> findAllByOrganizationIdIncludingInactive(String organizationId);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByOrganizationIdAndAzureId(String organizationId, String azureId);

    Optional<User> findByOrganizationIdAndGithubId(String organizationId, String githubId);

    /** Active users of the organization whose login email matches, ignoring case. */
    List<User> findAllByOrganizationIdAndEmailIgnoringCase(String organizationId, String email);

    /** Active users of the organization whose linked Azure DevOps email matches, ignoring case. */
    List<User> findAllByOrganizationIdAndAzureEmailIgnoringCase(String organizationId, String azureEmail);

    Optional<User> findByInviteToken(String inviteToken);

    boolean existsByOrganizationIdAndRoleIdAndActiveTrue(String organizationId, String roleId);

    User save(User user);

    /**
     * Stamps lastLoginAt with an atomic $set filtered by id, organization and active.
     * Deliberately not save(): it must not touch version or updatedAt, so a concurrent edit never
     * turns a login into a conflict.
     */
    void recordLogin(String userId, String organizationId, java.time.Instant at);
}
