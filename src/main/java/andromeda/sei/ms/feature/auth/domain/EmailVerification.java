package andromeda.sei.ms.feature.auth.domain;

import java.time.Instant;

public class EmailVerification {

    private String id;
    private String email;
    private String code;
    private boolean used;
    private Instant createdAt;

    public EmailVerification(String id, String email, String code, boolean used, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.code = code;
        this.used = used;
        this.createdAt = createdAt;
    }

    public void markUsed() {
        this.used = true;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getCode() { return code; }
    public boolean isUsed() { return used; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUsed(boolean used) { this.used = used; }
}