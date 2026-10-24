package andromeda.sei.ms.feature.azure.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure matcher (perfil-integracoes D11): decides whether a user's email identifies exactly one
 * active org identity. Compares normalized (trim, lowercase) email against the candidate's
 * {@code azureEmail} and {@code principalName}.
 */
public final class AzureIdentityMatcher {

    public enum Status { LINKED, NOT_FOUND, AMBIGUOUS, TAKEN, MATCH }

    /** {@code candidate} is set only for {@link Status#MATCH}. */
    public record Result(Status status, AzureOrgUser candidate) {
        static Result of(Status status) { return new Result(status, null); }
    }

    private AzureIdentityMatcher() {}

    /**
     * @param userEmail      login email of the user
     * @param alreadyLinked  the user already has an {@code azureId}, from any source
     * @param candidates     active identities of the user's organization
     * @param takenAzureIds  azure ids already linked to other users of the organization
     */
    public static Result match(String userEmail, boolean alreadyLinked, Collection<AzureOrgUser> candidates,
                               Set<String> takenAzureIds) {
        if (alreadyLinked) return Result.of(Status.LINKED);
        String email = normalize(userEmail);
        if (email == null || candidates == null) return Result.of(Status.NOT_FOUND);

        Map<String, AzureOrgUser> matches = new LinkedHashMap<>();
        for (AzureOrgUser candidate : candidates) {
            if (candidate == null || !candidate.isActive() || candidate.getAzureId() == null) continue;
            if (email.equals(normalize(candidate.getAzureEmail()))
                    || email.equals(normalize(candidate.getPrincipalName()))) {
                matches.putIfAbsent(candidate.getAzureId(), candidate);
            }
        }
        if (matches.isEmpty()) return Result.of(Status.NOT_FOUND);
        if (matches.size() > 1) return Result.of(Status.AMBIGUOUS);

        AzureOrgUser only = matches.values().iterator().next();
        if (takenAzureIds != null && takenAzureIds.contains(only.getAzureId())) return Result.of(Status.TAKEN);
        return new Result(Status.MATCH, only);
    }

    static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
