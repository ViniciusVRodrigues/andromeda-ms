package andromeda.sei.ms.feature.usage.domain;

import java.util.List;
import java.util.Optional;

/** Anonymous flows that happen before there is a JWT. The closed step list is part of the contract. */
public enum FunnelFlow {

    SIGNUP("signup", List.of("pessoal", "verificar", "senha", "org", "projeto", "senioridade", "cargos",
            "permissoes", "equipe", "confirmar")),
    INVITE("invite", List.of("bem-vindo", "conta", "ia", "pronto"));

    private final String value;
    private final List<String> steps;

    FunnelFlow(String value, List<String> steps) {
        this.value = value;
        this.steps = steps;
    }

    /** Wire and storage form ({@code signup} / {@code invite}). */
    public String value() {
        return value;
    }

    public boolean hasStep(String step) {
        return step != null && steps.contains(step);
    }

    public static Optional<FunnelFlow> fromValue(String value) {
        for (FunnelFlow f : values()) {
            if (f.value.equals(value)) return Optional.of(f);
        }
        return Optional.empty();
    }
}
