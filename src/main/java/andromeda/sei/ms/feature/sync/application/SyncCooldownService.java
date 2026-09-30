package andromeda.sei.ms.feature.sync.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cooldown de 30 segundos por binding (design D2), compartilhado por todos os caminhos de sync
 * (refresh de projeto, triggers manuais, sync por binding, agendado).
 *
 * <p>A chave e o {@code bindingId}, nao a organizacao: dois projetos com bindings diferentes
 * podem ser atualizados em sequencia, mas o mesmo binding nao e sincronizado duas vezes em 30s.
 *
 * <p>O cooldown e registrado na <b>admissao</b> (quando o pedido e aceito), nao no fim do sync:
 * assim um segundo refresh do mesmo projeto enquanto o primeiro ainda roda recebe {@code 429}
 * em vez de disparar um sync concorrente sobre o mesmo binding.
 *
 * <p>Estado em memoria: aceitavel com instancia unica (risco anotado no design); com replicas,
 * migrar para o Mongo.
 */
@Component
public class SyncCooldownService {

    static final Duration COOLDOWN = Duration.ofSeconds(30);
    private static final long MILLIS_PER_SECOND = 1000L;

    private final ConcurrentHashMap<String, Instant> lastAdmissionByBinding = new ConcurrentHashMap<>();
    private final Clock clock;

    public SyncCooldownService() {
        this(Clock.systemUTC());
    }

    SyncCooldownService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Tenta admitir um sync do binding, de forma atomica (sem janela entre checar e registrar).
     *
     * @return {@code 0} quando admitido (o cooldown passa a contar agora); caso contrario, quantos
     *         segundos faltam para o binding sair do cooldown (sempre {@code >= 1})
     */
    public long tryAcquire(String bindingId) {
        Instant now = clock.instant();
        AtomicLong remaining = new AtomicLong();
        lastAdmissionByBinding.compute(bindingId, (id, last) -> {
            long left = remainingSeconds(last, now);
            remaining.set(left);
            return left > 0 ? last : now;
        });
        return remaining.get();
    }

    /** Segundos restantes de cooldown do binding, sem registrar nada ({@code 0} = livre). */
    public long remainingSeconds(String bindingId) {
        return remainingSeconds(lastAdmissionByBinding.get(bindingId), clock.instant());
    }

    /**
     * Desfaz uma admissao que nao chegou a rodar (ex.: fila do executor cheia), para o usuario
     * nao ficar preso 30s por um pedido que nunca foi executado.
     */
    public void release(String bindingId) {
        lastAdmissionByBinding.remove(bindingId);
    }

    private static long remainingSeconds(Instant last, Instant now) {
        if (last == null) return 0;
        long leftMillis = COOLDOWN.toMillis() - Duration.between(last, now).toMillis();
        if (leftMillis <= 0) return 0;
        return (leftMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }
}
