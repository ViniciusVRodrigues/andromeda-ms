package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.PairingOutput;
import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingNotFoundException;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Shows the person what is asking to pair, and to whom it would be linked, so they recognise their machine. */
@Component
public class GetPairingUseCase {

    private final AgentDeviceAuthorizationRepository authorizations;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public GetPairingUseCase(AgentDeviceAuthorizationRepository authorizations,
                             OrganizationRepository organizations, UserRepository users) {
        this(authorizations, organizations, users, Clock.systemUTC());
    }

    GetPairingUseCase(AgentDeviceAuthorizationRepository authorizations, OrganizationRepository organizations,
                      UserRepository users, Clock clock) {
        this.authorizations = authorizations;
        this.organizations = organizations;
        this.users = users;
        this.clock = clock;
    }

    /** {@code browserIp} is the address of this request (null when unknown). */
    public PairingOutput execute(String organizationId, String userId, String typedUserCode, String browserIp) {
        AgentDeviceAuthorization a = PendingPairings.find(authorizations, typedUserCode);
        String normalized = AgentCodes.normalizeUserCode(typedUserCode).orElseThrow(AgentPairingNotFoundException::new);
        Boolean sameNetwork = a.getIp() == null || browserIp == null ? null : a.getIp().equals(browserIp);
        String organizationName = organizations.findById(organizationId).map(o -> o.getName()).orElse(null);
        String userName = users.findByIdAndOrganizationId(userId, organizationId).map(u -> u.getName()).orElse(null);
        return new PairingOutput(AgentCodes.formatUserCode(normalized), PendingPairings.statusOf(a, clock.instant()),
                a.getMachineName(), a.getOs(), a.getOsLabel(), a.getArch(), a.getAgentVersion(), a.getIp(),
                sameNetwork, a.getCreatedAt(), a.getExpiresAt(), organizationName, userName);
    }
}
