package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who the people of a dashboard are, within the caller's organization: display name and whether
 * their provider identities and AI connector are linked — what turns a count into "no data"
 * ({@code null}) instead of a misleading zero.
 */
@Component
@RequiredArgsConstructor
public class DashboardPeopleDirectory {

    private final UserRepository userRepository;
    private final ConnectorTokenRepository connectorTokenRepository;

    /** Every user of the organization by id (one query); duplicated ids keep the first. */
    public Map<String, PersonInfo> people(String organizationId) {
        return userRepository.findAllByOrganizationId(organizationId).stream()
                .filter(u -> u.getId() != null)
                .map(DashboardPeopleDirectory::toInfo)
                .collect(Collectors.toMap(PersonInfo::id, Function.identity(), (first, second) -> first));
    }

    /** The user of this organization; a placeholder named after the id when not found. */
    public PersonInfo person(String organizationId, String userId) {
        return userRepository.findByIdAndOrganizationId(userId, organizationId)
                .map(DashboardPeopleDirectory::toInfo)
                .orElseGet(() -> PersonInfo.unknown(userId));
    }

    /** Holds an active AI connector token in this organization. */
    public boolean aiConnected(String organizationId, String userId) {
        return !connectorTokenRepository.findActiveByOrganizationIdAndUserId(organizationId, userId).isEmpty();
    }

    /**
     * How many of {@code memberIds} have each identity linked, within this organization: Azure
     * DevOps and GitHub from the user, AI from an active connector token (one query per member).
     * A member id with no user of this organization counts in {@code members} only.
     */
    public PeopleCoverage coverage(String organizationId, Collection<String> memberIds) {
        Map<String, User> users = userRepository.findAllByOrganizationId(organizationId).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        List<User> members = memberIds.stream().map(users::get).filter(Objects::nonNull).toList();
        return new PeopleCoverage(memberIds.size(),
                (int) members.stream().filter(u -> present(u.getAzureId())).count(),
                (int) members.stream().filter(u -> present(u.getGithubId())).count(),
                (int) members.stream().filter(u -> aiConnected(organizationId, u.getId())).count());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static PersonInfo toInfo(User user) {
        boolean github = user.getGithubId() != null && !user.getGithubId().isBlank();
        boolean azure = user.getAzureId() != null && !user.getAzureId().isBlank();
        return new PersonInfo(user.getId(), displayName(user), github, github || azure);
    }

    private static String displayName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return user.getId();
    }

    /** Members of a project and how many have each identity linked. */
    public record PeopleCoverage(int members, int azureLinked, int githubLinked, int aiConnected) {}

    /** {@code vcsLinked}: a GitHub or Azure DevOps identity, the keys commits/PRs are attributed by. */
    public record PersonInfo(String id, String name, boolean githubLinked, boolean vcsLinked) {

        static PersonInfo unknown(String userId) {
            return new PersonInfo(userId, userId, false, false);
        }
    }
}
