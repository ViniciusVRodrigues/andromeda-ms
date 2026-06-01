package andromeda.sei.ms.feature.user.application.port;

public interface PasswordHasher {

    String hash(String raw);
}
