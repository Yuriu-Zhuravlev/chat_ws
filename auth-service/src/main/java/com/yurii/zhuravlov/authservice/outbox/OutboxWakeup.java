package com.yurii.zhuravlov.authservice.outbox;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.outbox.scheduling-enabled",
                       havingValue = "true", matchIfMissing = true)
public class OutboxWakeup {

    private final OutboxPublisher publisher;
    private final ExecutorService executor;

    public OutboxWakeup(OutboxPublisher publisher) {
        this.publisher = publisher;

        // One thread, queue of one, extra wake-ups discarded: a publish that is already
        // queued will pick up whatever was committed since, so a second one adds nothing.
        // This also collapses a burst of events into a single run without any bookkeeping.
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardPolicy());
    }

    /**
     * Publishing must happen after the commit, never inside the transaction: the rows
     * are not visible to the poller's own transaction until then. Best-effort by design —
     * if this is dropped or fails, the scheduled pass still picks the events up.
     */
    public void publishAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish();
            }
        });
    }

    private void publish() {
        executor.execute(() -> {
            try {
                publisher.publishPending();
            } catch (Exception e) {
                log.warn("Outbox wake-up run failed; the scheduled pass will retry", e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}