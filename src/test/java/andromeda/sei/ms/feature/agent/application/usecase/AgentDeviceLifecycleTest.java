package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatInput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentOAuthException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDeviceLifecycleTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    private FakeAgentStore store;
    private MutableClock clock;
    private AgentVersionPolicy policy;
    private RefreshAgentTokenUseCase refresh;
    private AuthenticateAgentTokenUseCase authenticate;
    private RecordHeartbeatUseCase heartbeat;
    private ListOwnDevicesUseCase list;
    private RevokeOwnDeviceUseCase revoke;
    private RequestDeviceSyncUseCase requestSync;
    private AgentDevice device;
    private String access;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        store = new FakeAgentStore();
        clock = new MutableClock(Instant.parse("2026-10-29T12:00:00Z"));
        policy = new AgentVersionPolicy("0.1.0", "0.2.0");
        AgentTokenIssuer issuer = new AgentTokenIssuer(Duration.ofHours(1), Duration.ofDays(90),
                Duration.ofSeconds(120), clock);
        OrganizationRepository orgs = mock(OrganizationRepository.class);
        EffectivePermissionService perms = mock(EffectivePermissionService.class);
        when(perms.snapshot(ORG, USER)).thenReturn(Optional.of(mock(UserPermissionSnapshot.class)));
        refresh = new RefreshAgentTokenUseCase(store, orgs, issuer, clock);
        authenticate = new AuthenticateAgentTokenUseCase(store, perms, policy, clock);
        heartbeat = new RecordHeartbeatUseCase(store, policy, clock);
        list = new ListOwnDevicesUseCase(store, policy, Duration.ofDays(3), clock);
        revoke = new RevokeOwnDeviceUseCase(store, clock);
        requestSync = new RequestDeviceSyncUseCase(store, clock);

        device = new AgentDevice();
        device.setOrganizationId(ORG);
        device.setUserId(USER);
        device.setMachineName("NOTE-ANA");
        device.setOs("windows");
        device.setOsLabel("Windows 11");
        device.setArch("x64");
        device.setTimezone("America/Sao_Paulo");
        device.setAgentVersion("0.2.0");
        device.setPairedAt(clock.instant());
        device.setActive(true);
        store.save(device);
        var issued = issuer.issue(device);
        access = issued.accessToken();
        refreshToken = issued.refreshToken();
    }

    private static void assertInvalidGrant(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AgentOAuthException.class,
                e -> assertThat(e.getError()).isEqualTo("invalid_grant"));
    }

    @Test
    void should_rotateRefreshAndAcceptThePreviousOnceInsideTheGrace() {
        var second = refresh.execute(refreshToken);
        assertThat(second.refreshToken()).isNotEqualTo(refreshToken);
        clock.advance(Duration.ofSeconds(60));
        var third = refresh.execute(refreshToken);
        assertThat(third.refreshToken()).isNotEqualTo(second.refreshToken());
        clock.advance(Duration.ofSeconds(10));
        assertInvalidGrant(() -> refresh.execute(refreshToken));
        assertThat(device.isRevoked()).isTrue();
        assertThat(device.getRevokedBy()).isEqualTo("system:refresh-reuse");
        assertThatThrownBy(() -> refresh.execute(third.refreshToken())).isInstanceOf(AgentOAuthException.class);
    }

    @Test
    void should_revokeOnReuseOfThePreviousAfterTheGraceWindow() {
        refresh.execute(refreshToken);
        clock.advance(Duration.ofSeconds(121));
        assertInvalidGrant(() -> refresh.execute(refreshToken));
        assertThat(device.isRevoked()).isTrue();
    }

    @Test
    void should_rejectUnknownAndExpiredRefresh() {
        assertInvalidGrant(() -> refresh.execute("adr_nope"));
        clock.advance(Duration.ofDays(91));
        assertInvalidGrant(() -> refresh.execute(refreshToken));
    }

    @Test
    void should_authenticateAndWriteLastSeenAtMostOncePerMinute() {
        var r = authenticate.execute(access, "0.2.0");
        assertThat(r.principal()).isEqualTo(new AgentPrincipal(ORG, USER, device.getId()));
        assertThat(r.versionSupported()).isTrue();
        assertThat(store.touchSeenCalls).isEqualTo(1);
        assertThat(device.getLastSeenAt()).isEqualTo(clock.instant());
        clock.advance(Duration.ofSeconds(30));
        authenticate.execute(access, "0.2.0");
        assertThat(store.touchSeenCalls).isEqualTo(1);
        clock.advance(Duration.ofSeconds(31));
        authenticate.execute(access, "0.2.0");
        assertThat(store.touchSeenCalls).isEqualTo(2);
    }

    @Test
    void should_answerUnsupportedButStillRecordLastSeenForMissingOrOldHeader() {
        var missing = authenticate.execute(access, null);
        assertThat(missing.versionSupported()).isFalse();
        assertThat(missing.minVersion()).isEqualTo("0.1.0");
        assertThat(device.getLastSeenAt()).isEqualTo(clock.instant());
        assertThat(authenticate.execute(access, "0.0.5").versionSupported()).isFalse();
    }

    @Test
    void should_answer401ForRevokedExpiredAndUnknownTokens() {
        assertThatThrownBy(() -> authenticate.execute("adt_unknown", "0.2.0"))
                .isInstanceOf(AgentTokenInvalidException.class);
        assertThatThrownBy(() -> authenticate.execute(null, "0.2.0")).isInstanceOf(AgentTokenInvalidException.class);
        clock.advance(Duration.ofHours(2));
        assertThatThrownBy(() -> authenticate.execute(access, "0.2.0")).isInstanceOf(AgentTokenInvalidException.class);
        clock.advance(Duration.ofHours(-2));
        revoke.execute(ORG, USER, device.getId());
        assertThatThrownBy(() -> authenticate.execute(access, "0.2.0"))
                .isInstanceOfSatisfying(AgentTokenInvalidException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AGENT_TOKEN_INVALID"));
    }

    @Test
    void should_recordHeartbeatWithTimezoneAndNeverTouchLastUpload() {
        AgentPrincipal principal = new AgentPrincipal(ORG, USER, device.getId());
        var out = heartbeat.execute(principal, new HeartbeatInput("0.2.1", "Europe/Lisbon", 7, null));
        assertThat(device.getTimezone()).isEqualTo("Europe/Lisbon");
        assertThat(device.getPendingRows()).isEqualTo(7);
        assertThat(device.getAgentVersion()).isEqualTo("0.2.1");
        assertThat(device.getLastSeenAt()).isEqualTo(clock.instant());
        assertThat(device.getLastUploadAt()).isNull();
        assertThat(store.touchUploadCalls).isZero();
        assertThat(out.minVersion()).isEqualTo("0.1.0");
        heartbeat.execute(principal, new HeartbeatInput(null, "Not/AZone", null, null));
        assertThat(device.getTimezone()).isEqualTo("Europe/Lisbon");
    }

    @Test
    void should_listStaleWithDaysWithoutContactAndVersionStatus() {
        device.setLastSeenAt(clock.instant().minus(Duration.ofDays(4)).minusSeconds(60));
        device.setAgentVersion("0.1.5");
        List<DeviceOutput> out = list.execute(ORG, USER);
        assertThat(out).hasSize(1);
        DeviceOutput d = out.get(0);
        assertThat(d.status()).isEqualTo("STALE");
        assertThat(d.daysWithoutContact()).isEqualTo(4);
        assertThat(d.versionStatus()).isEqualTo("OUTDATED");
        assertThat(d.outdated()).isTrue();
        assertThat(d.os().family()).isEqualTo("WINDOWS");
        assertThat(d.os().label()).isEqualTo("Windows 11");
        assertThat(d.os().arch()).isEqualTo("x64");
        device.setAgentVersion("0.0.9");
        assertThat(list.execute(ORG, USER).get(0).versionStatus()).isEqualTo("UNSUPPORTED");
        device.setAgentVersion("0.2.0");
        device.setLastSeenAt(clock.instant());
        DeviceOutput ok = list.execute(ORG, USER).get(0);
        assertThat(ok.status()).isEqualTo("ACTIVE");
        assertThat(ok.versionStatus()).isEqualTo("OK");
        assertThat(ok.outdated()).isFalse();
    }

    @Test
    void should_showRevokedOnlyFor90DaysAndNotRevokeAgain() {
        revoke.execute(ORG, USER, device.getId());
        assertThat(list.execute(ORG, USER).get(0).status()).isEqualTo("REVOKED");
        assertThat(device.getAccessTokenHash()).isNull();
        assertThatThrownBy(() -> revoke.execute(ORG, USER, device.getId()))
                .isInstanceOf(AgentDeviceNotFoundException.class);
        clock.advance(Duration.ofDays(91));
        assertThat(list.execute(ORG, USER)).isEmpty();
    }

    @Test
    void should_answer404WhenRevokingADeviceOfSomeoneElseOrOfAnotherOrg() {
        assertThatThrownBy(() -> revoke.execute(ORG, "user-2", device.getId()))
                .isInstanceOf(AgentDeviceNotFoundException.class);
        assertThatThrownBy(() -> revoke.execute("org-2", USER, device.getId()))
                .isInstanceOf(AgentDeviceNotFoundException.class);
        assertThat(device.isRevoked()).isFalse();
    }

    @Test
    void should_reportMetaAndAiefIgnoredOnceTheCutoverDayArrives() {
        var meta = new GetAgentMetaUseCase(store, policy, "andromeda-agent", 22, "https://p",
                "andromeda task --clear", clock);
        device.setCutoverDay(LocalDate.parse("2026-10-30"));
        var m = meta.execute(ORG, USER);
        assertThat(m.minVersion()).isEqualTo("0.1.0");
        assertThat(m.recommendedVersion()).isEqualTo("0.2.0");
        assertThat(m.packageName()).isEqualTo("andromeda-agent");
        assertThat(m.nodeMin()).isEqualTo(22);
        assertThat(m.aiefIgnored()).isFalse();
        device.setCutoverDay(LocalDate.parse("2026-10-29"));
        assertThat(meta.execute(ORG, USER).aiefIgnored()).isTrue();
    }

    // ---- sync now ----

    private AgentPrincipal principal() {
        return new AgentPrincipal(ORG, USER, device.getId());
    }

    private HeartbeatInput beat(String syncServed) {
        return new HeartbeatInput(null, null, null, syncServed);
    }

    @Test
    void should_recordASyncRequestAndBeIdempotentWhilePending() {
        var first = requestSync.execute(ORG, USER, false, device.getId());
        assertThat(first.deviceId()).isEqualTo(device.getId());
        assertThat(first.syncRequestedAt()).isEqualTo(clock.instant());
        assertThat(first.syncServedAt()).isNull();
        assertThat(device.getSyncRequestedBy()).isEqualTo(USER);
        clock.advance(Duration.ofSeconds(20));
        var second = requestSync.execute(ORG, USER, false, device.getId());
        assertThat(second.syncRequestedAt()).isEqualTo(first.syncRequestedAt());
        assertThat(device.getSyncRequestedAt()).isEqualTo(first.syncRequestedAt());
    }

    @Test
    void should_answer404ForSyncOnUnknownRevokedOtherOrgOrOtherPerson() {
        assertThatThrownBy(() -> requestSync.execute(ORG, USER, false, "nope"))
                .isInstanceOf(AgentDeviceNotFoundException.class);
        assertThatThrownBy(() -> requestSync.execute("org-2", USER, true, device.getId()))
                .isInstanceOf(AgentDeviceNotFoundException.class);
        assertThatThrownBy(() -> requestSync.execute(ORG, "user-2", false, device.getId()))
                .isInstanceOf(AgentDeviceNotFoundException.class);
        assertThat(device.getSyncRequestedAt()).isNull();
        revoke.execute(ORG, USER, device.getId());
        assertThatThrownBy(() -> requestSync.execute(ORG, USER, false, device.getId()))
                .isInstanceOf(AgentDeviceNotFoundException.class);
    }

    @Test
    void should_letWhoManagesAllRequestASyncOnAnotherPersonDevice() {
        var out = requestSync.execute(ORG, "admin-1", true, device.getId());
        assertThat(out.syncRequestedAt()).isEqualTo(clock.instant());
        assertThat(device.getSyncRequestedBy()).isEqualTo("admin-1");
    }

    @Test
    void should_returnThePendingRequestInTheHeartbeatAndNullWithoutOne() {
        assertThat(heartbeat.execute(principal(), beat(null)).syncRequestedAt()).isNull();
        Instant requested = requestSync.execute(ORG, USER, false, device.getId()).syncRequestedAt();
        assertThat(heartbeat.execute(principal(), beat(null)).syncRequestedAt()).isEqualTo(requested);
    }

    @Test
    void should_clearThePendingRequestWhenTheHeartbeatEchoesIt() {
        Instant requested = requestSync.execute(ORG, USER, false, device.getId()).syncRequestedAt();
        clock.advance(Duration.ofSeconds(5));
        var out = heartbeat.execute(principal(), beat(requested.toString()));
        assertThat(out.syncRequestedAt()).isNull();
        assertThat(device.getSyncRequestedAt()).isNull();
        assertThat(device.getSyncRequestedBy()).isNull();
        assertThat(device.getSyncServedAt()).isEqualTo(clock.instant());
    }

    @Test
    void should_keepANewerRequestWhenTheHeartbeatEchoesAnOlderOne() {
        Instant old = requestSync.execute(ORG, USER, false, device.getId()).syncRequestedAt();
        clock.advance(Duration.ofSeconds(5));
        device.setSyncRequestedAt(clock.instant());
        var out = heartbeat.execute(principal(), beat(old.toString()));
        assertThat(out.syncRequestedAt()).isEqualTo(clock.instant());
        assertThat(device.getSyncServedAt()).isNull();
    }

    @Test
    void should_ignoreAnInvalidOrUnmatchedSyncServedWithoutFailing() {
        Instant requested = requestSync.execute(ORG, USER, false, device.getId()).syncRequestedAt();
        assertThat(heartbeat.execute(principal(), beat("ontem")).syncRequestedAt()).isEqualTo(requested);
        assertThat(heartbeat.execute(principal(), beat(requested + " ".repeat(40))).syncRequestedAt())
                .isEqualTo(requested);
        assertThat(heartbeat.execute(principal(), beat(requested.plusSeconds(1).toString())).syncRequestedAt())
                .isEqualTo(requested);
        assertThat(device.getSyncServedAt()).isNull();
        device.setSyncRequestedAt(null);
        assertThat(heartbeat.execute(principal(), beat(requested.toString())).syncRequestedAt()).isNull();
        assertThat(store.clearServedCalls).isZero();
    }

    @Test
    void should_matchTheEchoAtMillisecondPrecision() {
        device.setSyncRequestedAt(Instant.parse("2026-10-29T18:00:01.123Z"));
        var out = heartbeat.execute(principal(), beat("2026-10-29T18:00:01.123456Z"));
        assertThat(out.syncRequestedAt()).isNull();
    }

    @Test
    void should_flagSyncNowSupportedFromAgent030AndNotWhenRevoked() {
        device.setAgentVersion("0.2.0");
        assertThat(list.execute(ORG, USER).get(0).syncNowSupported()).isFalse();
        device.setAgentVersion("0.3.0");
        assertThat(list.execute(ORG, USER).get(0).syncNowSupported()).isTrue();
        device.setAgentVersion("1.0.0");
        assertThat(list.execute(ORG, USER).get(0).syncNowSupported()).isTrue();
        device.setAgentVersion(null);
        assertThat(list.execute(ORG, USER).get(0).syncNowSupported()).isFalse();
        device.setAgentVersion("abc");
        assertThat(list.execute(ORG, USER).get(0).syncNowSupported()).isFalse();
        device.setAgentVersion("1.0.0");
        device.setSyncRequestedAt(Instant.parse("2026-10-29T11:00:00Z"));
        device.setSyncServedAt(Instant.parse("2026-10-29T10:00:00Z"));
        DeviceOutput d = list.execute(ORG, USER).get(0);
        assertThat(d.syncRequestedAt()).isEqualTo(Instant.parse("2026-10-29T11:00:00Z"));
        assertThat(d.syncServedAt()).isEqualTo(Instant.parse("2026-10-29T10:00:00Z"));
        revoke.execute(ORG, USER, device.getId());
        assertThat(list.execute(ORG, USER).get(0).syncNowSupported()).isFalse();
    }
}
