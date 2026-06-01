package andromeda.sei.ms.feature.user.infrastructure.security;

import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.application.port.PasswordVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptPasswordHasher implements PasswordHasher, PasswordVerifier {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String raw) {
        return passwordEncoder.encode(raw);
    }

    @Override
    public boolean verify(String raw, String hash) {
        return passwordEncoder.matches(raw, hash);
    }
}
