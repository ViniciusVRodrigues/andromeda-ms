package andromeda.sei.ms.shared.application.security;

/**
 * A provider identity (GitHub account, Azure DevOps identity) now points to a user: the commits,
 * pull requests and review events synced earlier without an owner can be attributed to them
 * (atribuir-historico-vcs D1). Handled asynchronously; carries ids only, no personal data.
 */
public record VcsIdentityLinked(String organizationId, String userId) {}
