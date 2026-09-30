package andromeda.sei.ms.shared.infrastructure.config;

import andromeda.sei.ms.shared.infrastructure.async.TenantContextTaskDecorator;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executores da aplicacao.
 *
 * <p>{@code syncExecutor} isola as chamadas a APIs externas disparadas por refresh/sync
 * (design D1): pool fixo de 2 threads e fila de 20; com a fila cheia a submissao e rejeitada
 * ({@link ThreadPoolExecutor.AbortPolicy}) e quem submete traduz isso em {@code 429}.
 *
 * <p>Declarar qualquer {@code Executor} desliga o {@code applicationTaskExecutor} autoconfigurado
 * pelo Boot. Ele e redeclarado aqui como {@code @Primary} (e com o alias {@code taskExecutor},
 * o nome que o {@code @Async} procura) para que o wipe de integracao e o suporte assincrono do
 * MVC nao passem a disputar o pool limitado do sync.
 */
@Configuration(proxyBeanMethods = false)
public class AsyncExecutorConfig {

    public static final String SYNC_EXECUTOR = "syncExecutor";

    static final int SYNC_POOL_SIZE = 2;
    static final int SYNC_QUEUE_CAPACITY = 20;
    private static final int SHUTDOWN_AWAIT_SECONDS = 30;

    @Bean(name = SYNC_EXECUTOR)
    public ThreadPoolTaskExecutor syncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(SYNC_POOL_SIZE);
        executor.setMaxPoolSize(SYNC_POOL_SIZE);
        executor.setQueueCapacity(SYNC_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("sync-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
        return executor;
    }

    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }
}
