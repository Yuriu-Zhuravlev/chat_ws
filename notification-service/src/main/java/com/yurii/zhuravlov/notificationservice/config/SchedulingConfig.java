package com.yurii.zhuravlov.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    /** Closes connections that never authenticate, and those whose token has expired. */
    @Bean
    public TaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-sched-");
        // Without this, cancelled tasks sit in the queue until their original fire time —
        // noticeable memory at thousands of connections.
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}