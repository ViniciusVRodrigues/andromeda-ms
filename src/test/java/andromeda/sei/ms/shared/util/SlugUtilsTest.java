package andromeda.sei.ms.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A checagem de disponibilidade do wizard recebe o NOME e aplica esta mesma
 * derivacao — por isso o comportamento de colisao precisa estar fixado.
 */
class SlugUtilsTest {

    @Test
    void differentNamesCanCollideOnTheSameSlug() {
        assertThat(SlugUtils.toSlug("Acme Ltda")).isEqualTo(SlugUtils.toSlug("ACME   ltda"));
    }

    @Test
    void stripsPunctuationAndCollapsesSeparators() {
        assertThat(SlugUtils.toSlug("Foo & Bar / Baz!")).isEqualTo("foo-and-bar-baz");
    }

    @Test
    void trimsLeadingAndTrailingSeparators() {
        assertThat(SlugUtils.toSlug("  -Acme-  ")).isEqualTo("acme");
    }
}
