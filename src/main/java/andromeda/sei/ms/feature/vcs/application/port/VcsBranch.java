package andromeda.sei.ms.feature.vcs.application.port;

/** A branch of a repository: short name (no {@code refs/heads/}) and whether it is the repository's default. */
public record VcsBranch(String name, boolean isDefault) {}
