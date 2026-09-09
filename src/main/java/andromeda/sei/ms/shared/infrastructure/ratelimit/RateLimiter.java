package andromeda.sei.ms.shared.infrastructure.ratelimit;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitador de tentativas por chave, com janela deslizante em memoria.
 *
 * Primeiro controle de trafego de ENTRADA do backend — o unico controle de
 * taxa que existia era de saida, no cliente do GitHub.
 *
 * Limitacao conhecida: o contador vive na instancia. Com varias replicas o
 * limite passa a valer por replica, nao globalmente. E aceitavel na escala
 * atual; migrar para um contador compartilhado (Mongo ou Redis) e a evolucao
 * natural quando houver mais de uma instancia.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * Registra uma tentativa e recusa quando a chave excede o limite na janela.
     *
     * @throws TooManyAttemptsException quando o limite e ultrapassado
     */
    public void check(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                throw new TooManyAttemptsException();
            }
            timestamps.addLast(now);
        }

        /* Sem isso o mapa cresceria indefinidamente com chaves ociosas. */
        if (hits.size() > 10_000) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty()
                            || e.getValue().peekLast().isBefore(cutoff);
                }
            });
        }
    }

    public static class TooManyAttemptsException extends BusinessRuleException {
        public TooManyAttemptsException() {
            super("TOO_MANY_ATTEMPTS", "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }
    }
}
