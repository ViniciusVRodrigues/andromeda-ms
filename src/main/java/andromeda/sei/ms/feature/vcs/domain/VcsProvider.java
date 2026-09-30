package andromeda.sei.ms.feature.vcs.domain;

/**
 * Version-control provider of a repository (vcs-core, design D1). Provider is an attribute of each
 * repository, not a parallel architecture: every {@code vcs_*} document carries it as discriminator.
 */
public enum VcsProvider {
    GITHUB,
    AZURE_REPOS
}
