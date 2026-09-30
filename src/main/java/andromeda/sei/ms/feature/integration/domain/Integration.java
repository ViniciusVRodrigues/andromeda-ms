package andromeda.sei.ms.feature.integration.domain;

public enum Integration {
    AZURE_DEVOPS("AZURE_READ"),
    GITHUB("REPOS_READ"),
    AI_CONNECTOR("AI_READ_ALL");

    private final String readPermission;

    Integration(String readPermission) {
        this.readPermission = readPermission;
    }

    /** Permission code needed to read this integration's data and sync logs. */
    public String readPermission() {
        return readPermission;
    }
}
