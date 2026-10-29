package andromeda.sei.ms.feature.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCodesTest {

    @Test
    void should_generateEightCharsFromTheRestrictedAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = AgentCodes.newUserCode();
            assertThat(code).hasSize(8);
            assertThat(code.chars().mapToObj(c -> String.valueOf((char) c)))
                    .allMatch(AgentCodes.USER_CODE_ALPHABET::contains);
        }
        assertThat(AgentCodes.USER_CODE_ALPHABET).doesNotContain("A", "E", "I", "O", "U", "0", "1", "Y");
    }

    @Test
    void should_formatAsTwoGroupsOfFour() {
        assertThat(AgentCodes.formatUserCode("BCDFGHJK")).isEqualTo("BCDF-GHJK");
    }

    @Test
    void should_lookupIgnoringCaseHyphenAndSpaces() {
        assertThat(AgentCodes.normalizeUserCode("bcdf-ghjk")).contains("BCDFGHJK");
        assertThat(AgentCodes.normalizeUserCode(" BCDF GHJK ")).contains("BCDFGHJK");
        assertThat(AgentCodes.normalizeUserCode("BCDFGHJK")).contains("BCDFGHJK");
    }

    @Test
    void should_rejectMalformedCodes() {
        assertThat(AgentCodes.normalizeUserCode(null)).isEmpty();
        assertThat(AgentCodes.normalizeUserCode("BCDF-GHJ")).isEmpty();
        assertThat(AgentCodes.normalizeUserCode("AAAA-AAAA")).isEmpty();
    }

    @Test
    void should_prefixTokensAndHashToHex() {
        assertThat(AgentCodes.newDeviceCode()).startsWith("adc_").hasSize(47);
        assertThat(AgentCodes.newAccessToken()).startsWith("adt_");
        assertThat(AgentCodes.newRefreshToken()).startsWith("adr_");
        assertThat(AgentCodes.sha256("abc")).hasSize(64).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
