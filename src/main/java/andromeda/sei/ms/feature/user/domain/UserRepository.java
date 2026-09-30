package andromeda.sei.ms.feature.user.domain;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByIdAndOrganizationId(String id, String organizationId);

    List<User> findAllByOrganizationId(String organizationId);

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
}
