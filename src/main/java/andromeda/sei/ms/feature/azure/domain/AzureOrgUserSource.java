package andromeda.sei.ms.feature.azure.domain;

/** Where an {@link AzureOrgUser} identity was seen. Precedence: DIRECTORY > TEAM > WORKITEM = PR. */
public enum AzureOrgUserSource {
    DIRECTORY(3), TEAM(2), WORKITEM(1), PR(1);

    private final int precedence;

    AzureOrgUserSource(int precedence) { this.precedence = precedence; }

    public int precedence() { return precedence; }
}
