package andromeda.sei.ms.feature.azure.domain;

/** Outcome of listing the organization's projects with a credential (the connection validation call). */
public enum AzureProjectsAccess {
    /** The project list call succeeded. */
    GRANTED,
    /** Azure DevOps answered 401/403 (or the organization does not exist for the credential). */
    DENIED
}
