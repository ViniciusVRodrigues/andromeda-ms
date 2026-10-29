package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ApprovedDeviceOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.PairingOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.TokenPairOutput;
import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceInfoInvalidException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentOAuthException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingConflictException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentVersionUnsupportedException;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPairingFlowTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    private FakeAgentStore store;
    private MutableClock clock;
    private RequestDeviceCodeUseCase request;
    private ExchangeDeviceTokenUseCase exchange;
    private ApprovePairingUseCase approve;
    private DenyPairingUseCase deny;
    private GetPairingUseCase get;

    @BeforeEach
    void setUp() {
        store = new FakeAgentStore();
        clock = new MutableClock(Instant.parse("2026-10-29T12:00:00Z"));
        AgentVersionPolicy policy = new AgentVersionPolicy("0.1.0", "0.2.0");
        AgentTokenIssuer issuer = new AgentTokenIssuer(Duration.ofHours(1), Duration.ofDays(90),
                Duration.ofSeconds(120), clock);
        OrganizationRepository orgs = mock(OrganizationRepository.class);
        Organization org = mock(Organization.class);
        when(org.getSlug()).thenReturn("acme");
        when(org.getName()).thenReturn("Acme");
        when(orgs.findById(ORG)).thenReturn(Optional.of(org));
        UserRepository users = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getName()).thenReturn("Ana");
        when(users.findByIdAndOrganizationId(USER, ORG)).thenReturn(Optional.of(user));
        request = new RequestDeviceCodeUseCase(store, policy, Duration.ofMinutes(10), 5, clock);
        exchange = new ExchangeDeviceTokenUseCase(store, store, orgs, issuer, clock);
        approve = new ApprovePairingUseCase(store, store, clock);
        deny = new DenyPairingUseCase(store, clock);
        get = new GetPairingUseCase(store, orgs, users, clock);
    }

    private DeviceCodeInput input(String version) {
        return new DeviceCodeInput(version, "NOTE-ANA", "windows", "11", "Windows 11", "x64",
                "America/Sao_Paulo", "1.2.3.4");
    }

    private DeviceCodeOutput ask() {
        return request.execute(input("0.2.0"));
    }

    private static void assertOAuth(Runnable call, String error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AgentOAuthException.class,
                e -> assertThat(e.getError()).isEqualTo(error));
    }

    @Test
    void should_storeOnlyHashesNeverTheRawCodes() {
        DeviceCodeOutput out = ask();
        var a = store.authorizations.values().iterator().next();
        assertThat(a.getDeviceCodeHash()).isEqualTo(AgentCodes.sha256(out.deviceCode()));
        assertThat(a.getUserCodeHash()).isEqualTo(AgentCodes.sha256(out.userCode().replace("-", "")));
        assertThat(a.getDeviceCodeHash()).isNotEqualTo(out.deviceCode());
        assertThat(out.userCode()).matches("[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}");
        assertThat(out.deviceCode()).startsWith("adc_");
        assertThat(out.expiresInSeconds()).isEqualTo(600);
        assertThat(a.getOsLabel()).isEqualTo("Windows 11");
        assertThat(a.getArch()).isEqualTo("x64");
    }

    @Test
    void should_rejectInvalidDeviceInfoAndOldVersions() {
        assertThatThrownBy(() -> request.execute(new DeviceCodeInput("0.2.0", "m", "beos", null, null, null, "UTC", "1.1.1.1")))
                .isInstanceOf(AgentDeviceInfoInvalidException.class);
        assertThatThrownBy(() -> request.execute(new DeviceCodeInput("0.2.0", "m", "linux", null, null, null, "Mars/Base", "1.1.1.1")))
                .isInstanceOf(AgentDeviceInfoInvalidException.class);
        assertThatThrownBy(() -> request.execute(new DeviceCodeInput("0.2.0", "m", "linux", null, "x".repeat(65), null, "UTC", "1.1.1.1")))
                .isInstanceOf(AgentDeviceInfoInvalidException.class);
        assertThatThrownBy(() -> request.execute(input("0.0.9"))).isInstanceOf(AgentVersionUnsupportedException.class);
    }

    @Test
    void should_answerPendingThenSlowDownAddingFiveSeconds() {
        DeviceCodeOutput out = ask();
        assertOAuth(() -> exchange.execute(out.deviceCode()), "authorization_pending");
        assertOAuth(() -> exchange.execute(out.deviceCode()), "slow_down");
        assertThat(store.authorizations.values().iterator().next().getIntervalSeconds()).isEqualTo(10);
        clock.advance(Duration.ofSeconds(11));
        assertOAuth(() -> exchange.execute(out.deviceCode()), "authorization_pending");
    }

    @Test
    void should_exchangeOnceAfterApproval() {
        DeviceCodeOutput out = ask();
        ApprovedDeviceOutput approved = approve.execute(ORG, USER, out.userCode().toLowerCase());
        TokenPairOutput tokens = exchange.execute(out.deviceCode());
        assertThat(tokens.accessToken()).startsWith("adt_");
        assertThat(tokens.refreshToken()).startsWith("adr_");
        assertThat(tokens.deviceId()).isEqualTo(approved.deviceId());
        assertThat(tokens.organizationSlug()).isEqualTo("acme");
        assertThat(tokens.scope()).isEqualTo("usage:write tasks:read");
        AgentDevice d = store.devices.get(approved.deviceId());
        assertThat(d.getAccessTokenHash()).isEqualTo(AgentCodes.sha256(tokens.accessToken()));
        assertThat(d.getRefreshTokenHash()).isEqualTo(AgentCodes.sha256(tokens.refreshToken()));
        assertThat(d.getCutoverDay()).isEqualTo(LocalDate.parse("2026-10-29"));
        assertThat(d.getOsLabel()).isEqualTo("Windows 11");
        assertOAuth(() -> exchange.execute(out.deviceCode()), "expired_token");
    }

    @Test
    void should_answerAccessDeniedAfterDenial() {
        DeviceCodeOutput out = ask();
        deny.execute(ORG, USER, out.userCode());
        assertOAuth(() -> exchange.execute(out.deviceCode()), "access_denied");
    }

    @Test
    void should_answerExpiredTokenAfterTheTtlAndInvalidGrantForUnknown() {
        DeviceCodeOutput out = ask();
        clock.advance(Duration.ofMinutes(11));
        assertOAuth(() -> exchange.execute(out.deviceCode()), "expired_token");
        assertOAuth(() -> exchange.execute("adc_unknown"), "invalid_grant");
    }

    @Test
    void should_revokeTheUnclaimedDeviceWhenAnApprovedCodeExpires() {
        DeviceCodeOutput out = ask();
        ApprovedDeviceOutput approved = approve.execute(ORG, USER, out.userCode());
        clock.advance(Duration.ofMinutes(11));
        assertOAuth(() -> exchange.execute(out.deviceCode()), "expired_token");
        assertThat(store.devices.get(approved.deviceId()).isRevoked()).isTrue();
    }

    @Test
    void should_showPairingInAnyStateAnd404OnlyForUnknownCode() {
        DeviceCodeOutput out = ask();
        PairingOutput p = get.execute(ORG, USER, out.userCode(), "1.2.3.4");
        assertThat(p.status()).isEqualTo("PENDING");
        assertThat(p.sameNetworkAsBrowser()).isTrue();
        assertThat(p.requesterIp()).isEqualTo("1.2.3.4");
        assertThat(p.organizationName()).isEqualTo("Acme");
        assertThat(p.userName()).isEqualTo("Ana");
        assertThat(p.osLabel()).isEqualTo("Windows 11");
        assertThat(get.execute(ORG, USER, out.userCode(), "9.9.9.9").sameNetworkAsBrowser()).isFalse();
        assertThat(get.execute(ORG, USER, out.userCode(), null).sameNetworkAsBrowser()).isNull();

        approve.execute(ORG, USER, out.userCode());
        assertThat(get.execute(ORG, USER, out.userCode(), null).status()).isEqualTo("APPROVED");
        exchange.execute(out.deviceCode());
        assertThat(get.execute(ORG, USER, out.userCode(), null).status()).isEqualTo("USED");

        DeviceCodeOutput second = ask();
        clock.advance(Duration.ofMinutes(11));
        assertThat(get.execute(ORG, USER, second.userCode(), null).status()).isEqualTo("EXPIRED");
        DeviceCodeOutput third = ask();
        deny.execute(ORG, USER, third.userCode());
        assertThat(get.execute(ORG, USER, third.userCode(), null).status()).isEqualTo("DENIED");

        assertThatThrownBy(() -> get.execute(ORG, USER, "BCDF-GHJK", null))
                .isInstanceOf(AgentPairingNotFoundException.class);
    }

    @Test
    void should_approveIdempotentlyForTheSameUserAndConflictForOthers() {
        DeviceCodeOutput out = ask();
        ApprovedDeviceOutput first = approve.execute(ORG, USER, out.userCode());
        ApprovedDeviceOutput again = approve.execute(ORG, USER, out.userCode());
        assertThat(again.deviceId()).isEqualTo(first.deviceId());
        assertThat(store.devices).hasSize(1);
        assertThatThrownBy(() -> approve.execute(ORG, "user-2", out.userCode()))
                .isInstanceOfSatisfying(AgentPairingConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AGENT_PAIRING_USED"));
    }

    @Test
    void should_answer409WithTheStateForApproveAndDeny() {
        DeviceCodeOutput expired = ask();
        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> approve.execute(ORG, USER, expired.userCode()))
                .isInstanceOfSatisfying(AgentPairingConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("AGENT_PAIRING_EXPIRED");
                    assertThat(e.getPairingStatus()).isEqualTo("EXPIRED");
                });
        assertThatThrownBy(() -> deny.execute(ORG, USER, expired.userCode()))
                .isInstanceOf(AgentPairingConflictException.class);

        DeviceCodeOutput denied = ask();
        deny.execute(ORG, USER, denied.userCode());
        deny.execute(ORG, USER, denied.userCode());
        assertThatThrownBy(() -> approve.execute(ORG, USER, denied.userCode()))
                .isInstanceOfSatisfying(AgentPairingConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AGENT_PAIRING_DENIED"));

        DeviceCodeOutput approved = ask();
        approve.execute(ORG, USER, approved.userCode());
        assertThatThrownBy(() -> deny.execute(ORG, USER, approved.userCode()))
                .isInstanceOf(AgentPairingConflictException.class);
    }
}
