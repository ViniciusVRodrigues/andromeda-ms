package andromeda.sei.ms.feature.organization.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Frequencia do sync agendado da organizacao — uma rede de seguranca opcional; o caminho
 * principal de atualizacao e o refresh sob demanda.
 */
public enum ScheduledSyncFrequency {

    /** Sem sync agendado: as APIs externas so sao chamadas por refresh explicito. */
    OFF(null),
    DAILY(Duration.ofHours(24)),
    EVERY_6_HOURS(Duration.ofHours(6));

    private final Duration interval;

    ScheduledSyncFrequency(Duration interval) {
        this.interval = interval;
    }

    /** Intervalo entre execucoes; vazio para {@link #OFF}. */
    public Optional<Duration> interval() {
        return Optional.ofNullable(interval);
    }
}
