package andromeda.sei.ms.feature.organization.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationScheduledSyncTest {

    private static final Instant TICK = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void shouldNeverBeDueWhenOff() {
        Organization organization = organization(ScheduledSyncFrequency.OFF, null);

        assertThat(organization.isScheduledSyncDue(TICK)).isFalse();
    }

    @Test
    void shouldBeDueWhenNeverRan() {
        assertThat(organization(ScheduledSyncFrequency.DAILY, null).isScheduledSyncDue(TICK)).isTrue();
    }

    @Test
    void shouldBeDueExactlyOneIntervalAfterLastRun() {
        Organization organization = organization(ScheduledSyncFrequency.DAILY, TICK.minus(Duration.ofHours(24)));

        assertThat(organization.isScheduledSyncDue(TICK)).isTrue();
    }

    @Test
    void shouldNotBeDueBeforeTheIntervalElapses() {
        Organization organization = organization(ScheduledSyncFrequency.DAILY, TICK.minus(Duration.ofHours(23)));

        assertThat(organization.isScheduledSyncDue(TICK)).isFalse();
    }

    @Test
    void shouldUseSixHourIntervalForEverySixHours() {
        assertThat(organization(ScheduledSyncFrequency.EVERY_6_HOURS, TICK.minus(Duration.ofHours(6)))
                .isScheduledSyncDue(TICK)).isTrue();
        assertThat(organization(ScheduledSyncFrequency.EVERY_6_HOURS, TICK.minus(Duration.ofHours(5)))
                .isScheduledSyncDue(TICK)).isFalse();
    }

    @Test
    void shouldDefaultNewOrganizationsToOffWithNinetyDayBackfill() {
        Organization organization = new Organization(null, "Org", "org", null, null, null, null, null,
                true, null, TICK, TICK);

        assertThat(organization.getSyncSettings()).isEqualTo(SyncSettings.defaults());
    }

    private static Organization organization(ScheduledSyncFrequency frequency, Instant lastScheduledSyncAt) {
        Organization organization = new Organization("org-1", "Org", "org", null, null, null, null, null,
                true, 0L, TICK, TICK);
        organization.setSyncSettings(new SyncSettings(frequency, SyncSettings.DEFAULT_BACKFILL_DAYS));
        organization.setLastScheduledSyncAt(lastScheduledSyncAt);
        return organization;
    }
}
