package andromeda.sei.ms.feature.agent.domain.attribution;

import static org.assertj.core.api.Assertions.assertThat;

import andromeda.sei.ms.feature.agent.domain.attribution.PortableRegex.Portable;
import org.junit.jupiter.api.Test;

class PortableRegexTest {

    @Test
    void should_convertAbHashToEscapedLiteralWithIgnoreCase() {
        Portable p = PortableRegex.convert(BranchPatternMatcher.AB_HASH_REGEX).orElseThrow();
        assertThat(p.source()).isEqualTo("AB#(\\d+)(?!\\d)");
        assertThat(p.flags()).isEqualTo("i");
    }

    @Test
    void should_convertPrefixEscapingSpecialCharacters() {
        Portable p = PortableRegex.convert(BranchPatternMatcher.prefixRegex("WI.")).orElseThrow();
        assertThat(p.source()).isEqualTo("WI\\.(\\d+)(?!\\d)");
        assertThat(p.flags()).isEqualTo("i");
    }

    @Test
    void should_convertJiraKeyWithoutFlags() {
        Portable p = PortableRegex.convert(BranchPatternMatcher.JIRA_KEY_REGEX).orElseThrow();
        assertThat(p.source()).isEqualTo("([A-Z][A-Z0-9]{1,9}-\\d+)");
        assertThat(p.flags()).isEmpty();
    }

    @Test
    void should_convertNumberKeepingLookaheadAndIgnoreCase() {
        Portable p = PortableRegex.convert(BranchPatternMatcher.NUMBER_REGEX).orElseThrow();
        assertThat(p.source()).isEqualTo("(?:^|/)(?:[a-z]+[-_])?(\\d{2,7})(?=[-_/.]|$)");
        assertThat(p.flags()).isEqualTo("i");
    }

    @Test
    void should_acceptNamedGroups() {
        assertThat(PortableRegex.convert("(?<id>[A-Z]+-\\d+)")).isPresent();
    }

    @Test
    void should_refuseWhatHasNoSafeEquivalent() {
        assertThat(PortableRegex.convert("(?s)(\\d+)")).isEmpty();
        assertThat(PortableRegex.convert("(?x)(\\d+)")).isEmpty();
        assertThat(PortableRegex.convert("(\\d++)")).isEmpty();
        assertThat(PortableRegex.convert("\\A(\\d+)")).isEmpty();
        assertThat(PortableRegex.convert("(\\d+)\\Z")).isEmpty();
        assertThat(PortableRegex.convert("(?>a+)(\\d+)")).isEmpty();
        assertThat(PortableRegex.convert("[a-z&&[^b]](\\d+)")).isEmpty();
        assertThat(PortableRegex.convert("(\\p{Alpha}+-\\d+)")).isEmpty();
        assertThat(PortableRegex.convert("")).isEmpty();
        assertThat(PortableRegex.convert(null)).isEmpty();
    }

    @Test
    void should_keepLazyQuantifiers() {
        assertThat(PortableRegex.convert("(a+?)(\\d+)")).isPresent();
    }
}
