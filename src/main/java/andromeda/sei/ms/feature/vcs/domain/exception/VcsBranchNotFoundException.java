package andromeda.sei.ms.feature.vcs.domain.exception;

/**
 * Raised by a provider client when the branch asked for no longer exists (the sync skips it and
 * records it in {@code missingBranches}). Not a {@code DomainException}: it never reaches a response.
 */
public class VcsBranchNotFoundException extends RuntimeException {

    private final String branch;

    public VcsBranchNotFoundException(String branch, Throwable cause) {
        super("Branch not found: " + branch, cause);
        this.branch = branch;
    }

    public String getBranch() {
        return branch;
    }
}
