package andromeda.sei.ms.feature.usage.domain;

/** The three flows that issue a JWT; stored as the {@code value} of a LOGIN event. */
public enum LoginMethod {
    PASSWORD("password"), INVITE("invite"), SIGNUP("signup");

    private final String value;

    LoginMethod(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
