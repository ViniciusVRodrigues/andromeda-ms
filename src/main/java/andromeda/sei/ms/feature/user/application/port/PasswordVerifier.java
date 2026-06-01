package andromeda.sei.ms.feature.user.application.port;

public interface PasswordVerifier {

    boolean verify(String raw, String hash);
}
