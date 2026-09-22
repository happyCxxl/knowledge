package com.knowledge.biz.task;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.config.TaskQueueProperties;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;

/**
 * 解析消费循环：BRPOP 阻塞叫醒（空队零开销睡眠）→ 扫库领批 QUEUED 的 PARSE/STRUCTURE 任务 →
 * 按 stage 分发到对应 Runner，线程池执行（看门狗按环节超时，超时回写 FAILED(EXECUTOR_TIMEOUT)）。
 * 多实例防重靠"条件更新领任务"（受影响行数=1 才执行，见各 Runner）。
 * 其他环节任务的领批与分发随各自环节 Runner 落地后接入。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseTaskConsumer {

    private final TaskQueueSupport taskQueue;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final TaskQueueProperties properties;
    private final ParseTaskRunner runner;
    private final StructureTaskRunner structureRunner;

    private volatile boolean running = false;
    private ThreadPoolExecutor watchdogPool;
    private ThreadPoolExecutor runnerPool;

    /** 应用就绪后启动（单次；daemon 线程随进程退出） */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        ThreadFactory factory = ThreadFactoryBuilder.create().setNamePrefix("parse-worker-").setDaemon(true).build();
        watchdogPool = new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getClaimBatchSize()), factory,
                new ThreadPoolExecutor.AbortPolicy());
        runnerPool = new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getClaimBatchSize()), factory,
                new ThreadPoolExecutor.AbortPolicy());
        Thread dispatcher = ThreadFactoryBuilder.create().setNamePrefix("parse-dispatcher-").setDaemon(true).build()
                .newThread(this::loop);
        dispatcher.start();
        log.info("解析消费循环启动, concurrency={}, claimBatchSize={}, timeout={}",
                properties.getConcurrency(), properties.getClaimBatchSize(),
                properties.timeoutOf(PipelineStage.PARSE.name()));
    }

    private void loop() {
        while (running) {
            try {
                // 阻塞叫醒；超时返回 null = 未被叫醒 → 继续等待，不扫库（无消息零开销）
                String wakeup = taskQueue.blockingPop();
                if (ObjectUtil.isNull(wakeup)) {
                    continue;
                }
                // 消息内容仅参考（DB 是唯一账本）：被叫醒才扫库领批
                List<KbPipelineTask> batch = pipelineTaskDbService.listQueuedByStages(
                        List.of(PipelineStage.PARSE.name(), PipelineStage.STRUCTURE.name()),
                        properties.getClaimBatchSize());
                for (KbPipelineTask task : batch) {
                    dispatch(task);
                }
            } catch (Exception e) {
                log.warn("解析消费循环异常, 稍后重试", e);
                sleepQuietly();
            }
        }
    }

    private void dispatch(KbPipelineTask task) {
        try {
            if (PipelineStage.PARSE.name().equals(task.getStage())) {
                watchdogPool.execute(() -> runWithTimeout(task.getId(),
                        properties.timeoutOf(PipelineStage.PARSE.name()), () -> runner.run(task.getId())));
            } else if (PipelineStage.STRUCTURE.name().equals(task.getStage())) {
                watchdogPool.execute(() -> runWithTimeout(task.getId(),
                        properties.timeoutOf(PipelineStage.STRUCTURE.name()), () -> structureRunner.run(task.getId())));
            } else {
                // 未知环节：跳过并告警（后续环节加 Runner 即接入，主循环零改动）
                log.warn("未知环节任务跳过, taskId={}, stage={}", task.getId(), task.getStage());
            }
        } catch (RejectedExecutionException e) {
            // 线程池满：任务保持 QUEUED，等下次叫醒（下次提交/补偿都会再叫醒）
        }
    }

    /** 看门狗：Future 限时等待；超时协作中断并回写 FAILED(EXECUTOR_TIMEOUT) */
    private void runWithTimeout(Long taskId, Duration timeout, Runnable action) {
        Future<?> future = null;
        try {
            future = runnerPool.submit(action);
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            // 终态回写带 RUNNING 条件：与 runner 的兜底回写互斥，后到者受影响行数为 0
            pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(),
                    PipelineTaskErrorCode.EXECUTOR_TIMEOUT.name(), "单任务超时（看门狗判定）");
            log.warn("任务超时, taskId={}", taskId);
        } catch (RejectedExecutionException e) {
            // 执行池满：任务保持 QUEUED
        } catch (Exception e) {
            log.warn("任务执行异常, taskId={}", taskId, e);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
