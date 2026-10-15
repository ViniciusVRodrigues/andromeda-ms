package andromeda.sei.ms.feature.usage.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsageLabelSanitizerTest {

    @Test
    void should_replaceDigitsAndEmails() {
        assertThat(UsageLabelSanitizer.sanitize("Sprint 42 de ana@acme.com")).isEqualTo("Sprint ## de [email]");
    }

    @Test
    void should_replaceEmailBeforeDigits_soDigitsInsideTheEmailDoNotLeak() {
        assertThat(UsageLabelSanitizer.sanitize("ana2.silva@acme.co.uk")).isEqualTo("[email]");
    }

    @Test
    void should_dropControlCharacters_andCollapseWhitespace() {
        assertThat(UsageLabelSanitizer.sanitize("  Atualizar\u0000   \t dados\n\n agora  ")).isEqualTo("Atualizar dados agora");
    }

    @Test
    void should_cutAtSixtyCharacters() {
        String label = UsageLabelSanitizer.sanitize("a".repeat(100));
        assertThat(label).hasSize(60);
    }

    @Test
    void should_returnNull_when_nullOrBlankAfterCleaning() {
        assertThat(UsageLabelSanitizer.sanitize(null)).isNull();
        assertThat(UsageLabelSanitizer.sanitize("  \u0001 \n ")).isNull();
    }
}
