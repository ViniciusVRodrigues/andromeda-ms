package andromeda.sei.ms.shared.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "Minha Empresa,          minha-empresa",
            "Minha Empresa S/A,      minha-empresa-s-a",
            "Acme & Co.,             acme-and-co",
            "Multiple   Spaces,      multiple-spaces",
            "Already-Hyphenated,     already-hyphenated",
    })
    void shouldConvertToSlug(String input, String expected) {
        assertThat(SlugUtils.toSlug(input.strip())).isEqualTo(expected.strip());
    }

    @Test
    void shouldRemoveLeadingAndTrailingHyphens() {
        assertThat(SlugUtils.toSlug("-test-")).isEqualTo("test");
    }

    @Test
    void shouldCollapseConsecutiveHyphens() {
        assertThat(SlugUtils.toSlug("foo--bar")).isEqualTo("foo-bar");
    }

    @Test
    void shouldHandleLowercaseConversion() {
        assertThat(SlugUtils.toSlug("UPPER CASE")).isEqualTo("upper-case");
    }
}
