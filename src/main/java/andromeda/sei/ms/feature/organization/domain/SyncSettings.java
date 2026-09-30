package andromeda.sei.ms.feature.organization.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Configuracoes de sync da organizacao.
 *
 * @param scheduledSync frequencia do sync agendado
 * @param backfillDays  janela, em dias, do primeiro sync de um binding: so dados criados/alterados
 *                      nesse periodo sao buscados, em vez do historico inteiro
 */
public record SyncSettings(ScheduledSyncFrequency scheduledSync, int backfillDays) {

    public static final int DEFAULT_BACKFILL_DAYS = 90;
    public static final int MIN_BACKFILL_DAYS = 7;
    public static final int MAX_BACKFILL_DAYS = 730;

    public SyncSettings {
        Objects.requireNonNull(scheduledSync, "scheduledSync");
    }

    /** Organizacoes novas nascem sem sync agendado — o principio do produto e refresh explicito. */
    public static SyncSettings defaults() {
        return new SyncSettings(ScheduledSyncFrequency.OFF, DEFAULT_BACKFILL_DAYS);
    }

    public static boolean isValidBackfillDays(int days) {
        return days >= MIN_BACKFILL_DAYS && days <= MAX_BACKFILL_DAYS;
    }

    /** Limite inferior do primeiro sync de um binding, relativo a {@code now}. */
    public Instant backfillFrom(Instant now) {
        return now.minus(Duration.ofDays(backfillDays));
    }
}
