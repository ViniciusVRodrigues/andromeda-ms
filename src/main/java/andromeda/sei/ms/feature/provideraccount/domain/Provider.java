package andromeda.sei.ms.feature.provideraccount.domain;

public enum Provider {
    GITHUB,
    AZURE_DEVOPS,
    JIRA
    // AZURE_REPOS removed (vcs-core-refactor): an Azure Repos identity is the Azure DevOps one (User.azureId).
}
