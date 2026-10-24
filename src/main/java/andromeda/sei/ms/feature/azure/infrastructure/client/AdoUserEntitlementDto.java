package andromeda.sei.ms.feature.azure.infrastructure.client;

/**
 * A user of the Azure DevOps organization directory ({@code userentitlements}). {@code id} is the
 * item id, equal to {@code System.AssignedTo.id} on work items; {@code originId} is the AAD/MSA id
 * and does not match work items.
 */
public record AdoUserEntitlementDto(String id, String displayName, String mailAddress,
                                    String principalName, String originId) {}
