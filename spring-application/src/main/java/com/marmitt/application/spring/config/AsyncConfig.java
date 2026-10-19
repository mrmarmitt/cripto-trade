package com.marmitt.application.spring.config;

import com.marmitt.application.spring.bootstrap.RunnerSignalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executores do caminho de mensagens (T42). Três filas separadas para que um não espere pelo outro:
 * <ul>
 *   <li><b>marketDataExecutor</b> — parsing e roteamento de market data (thread única: preserva a
 *       ordem de chegada). Só enfileira ticks nas mailboxes dos runners; nunca avalia estratégia.
 *       Também carrega respostas de ordem da WS API, por isso não descarta: a fila é grande e, se
 *       transbordar, a mensagem é descartada com log ERROR em vez de bloquear a thread do socket.</li>
 *   <li><b>userDataExecutor</b> — fills, cancels e rejects. Nunca descarta: com fila cheia a thread
 *       do socket de user data executa o trabalho (pressão legítima — fill não pode se perder).</li>
 *   <li><b>runnerSignalExecutor</b> — avaliação dos runners, drenando as mailboxes. A fila tem no
 *       máximo uma tarefa por runner, então é ilimitada sem risco.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("marketDataExecutor")
    public Executor marketDataExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("ws-market-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.error("marketDataExecutor: queue full ({} pending) - market message dropped", pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("userDataExecutor")
    public Executor userDataExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1_000);
        executor.setThreadNamePrefix("ws-user-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("runnerSignalExecutor")
    public Executor runnerSignalExecutor(RunnerSignalProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, properties.getThreads()));
        executor.setMaxPoolSize(Math.max(1, properties.getThreads()));
        executor.setThreadNamePrefix("runner-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("bootRecoveryQueryExecutor")
    public Executor bootRecoveryQueryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("boot-query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
