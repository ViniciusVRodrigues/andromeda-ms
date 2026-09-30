package andromeda.sei.ms.shared.infrastructure.async;

import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.springframework.core.task.TaskDecorator;

/**
 * Propaga o {@link TenantContextHolder} da thread que submete para a thread do executor.
 *
 * <p>O tenant e um ThreadLocal simples e nao atravessa a fronteira do pool sozinho. O valor e
 * capturado no momento da submissao e reposicionado na thread de trabalho; o {@code finally}
 * e obrigatorio porque threads do pool sao reutilizadas e um tenant vazado daria acesso cruzado
 * entre organizacoes. Quando a submissao nao tem tenant (ex.: thread do scheduler), a tarefa
 * roda sem tenant e deve recebe-lo explicitamente (o orquestrador recebe o {@code orgId}).
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String organizationId = TenantContextHolder.get();
        return () -> {
            String previous = TenantContextHolder.get();
            TenantContextHolder.set(organizationId);
            try {
                runnable.run();
            } finally {
                if (previous == null) {
                    TenantContextHolder.clear();
                } else {
                    TenantContextHolder.set(previous);
                }
            }
        };
    }
}
