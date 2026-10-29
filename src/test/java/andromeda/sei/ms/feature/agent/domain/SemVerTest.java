package andromeda.sei.ms.feature.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemVerTest {

    @Test
    void should_compareNumerically() {
        assertThat(SemVer.isAtLeast("0.10.0", "0.9.0")).isTrue();
        assertThat(SemVer.isAtLeast("1.0.0", "1.0.0")).isTrue();
        assertThat(SemVer.isAtLeast("0.1.9", "0.2.0")).isFalse();
    }

    @Test
    void should_treatPreReleaseBelowRelease() {
        assertThat(SemVer.isAtLeast("1.0.0-beta.1", "1.0.0")).isFalse();
        assertThat(SemVer.isAtLeast("1.0.0", "1.0.0-beta.1")).isTrue();
    }

    @Test
    void should_failOnMissingOrInvalidVersion() {
        assertThat(SemVer.isAtLeast(null, "0.1.0")).isFalse();
        assertThat(SemVer.isAtLeast("abc", "0.1.0")).isFalse();
    }
}
