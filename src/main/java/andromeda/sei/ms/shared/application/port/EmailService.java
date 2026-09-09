package andromeda.sei.ms.shared.application.port;

public interface EmailService {
    void sendOtp(String email, String code);
    void sendInvite(String email, String name, String inviteLink);
    void sendPasswordReset(String email, String resetLink);
}
