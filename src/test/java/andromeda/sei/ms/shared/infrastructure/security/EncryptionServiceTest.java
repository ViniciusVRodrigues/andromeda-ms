package andromeda.sei.ms.shared.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private static final String VALID_KEY_BASE64 =
            Base64.getEncoder().encodeToString(new byte[32]); // 256-bit zero key for tests

    @Test
    void shouldRoundTripEncryptAndDecrypt() {
        var service = new EncryptionService(VALID_KEY_BASE64);
        var plaintext = "super-secret-token";

        var ciphertext = service.encrypt(plaintext);
        var decrypted = service.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encryptShouldProduceDifferentCiphertextEachTime() {
        var service = new EncryptionService(VALID_KEY_BASE64);
        var plaintext = "same-input";

        var first = service.encrypt(plaintext);
        var second = service.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void decryptShouldThrowWhenKeyIsWrong() {
        var serviceA = new EncryptionService(VALID_KEY_BASE64);
        var wrongKey = Base64.getEncoder().encodeToString(new byte[32]);
        // flip a byte to ensure different key (both are zeros here, use a known-different key)
        byte[] keyBytes = new byte[32];
        keyBytes[0] = 1;
        var serviceB = new EncryptionService(Base64.getEncoder().encodeToString(keyBytes));

        var ciphertext = serviceA.encrypt("some-value");

        assertThatThrownBy(() -> serviceB.decrypt(ciphertext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }
}
