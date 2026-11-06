package com.weiyi.agent.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ChatExecutorConfig {

    /**
     * 流结束回调专用线程池：Reactor 回调线程不跑阻塞落库，
     * 用量/assistant 消息/标题投递都在此异步执行（对用户可延迟，但必须有记录）。
     */
    @Bean
    public ThreadPoolTaskExecutor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-exec-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        // 落库不可丢：队列打满退化为提交线程执行，不拒绝
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * 首轮标题流内探测：主回复流式期间低频查库，标题先就绪即经 SSE 下发一次 title 事件。
     * 单线程守护线程即可（探测为毫秒级 DB 点查），流结束/失败时由主链路取消任务。
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService titleProbeScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "title-probe");
            thread.setDaemon(true);
            return thread;
        });
    }
}
