package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.config.TaskQueueProperties;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.infra.redis.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务队列补偿：启动补偿一次（补投过期未执行的 QUEUED 任务）+ 可选低频兜底（默认关闭）。
 * 补投 = 重新入队，消费侧重读 DB 校验，多投无害；多实例下 SET NX EX 锁防重。
 *
 * @author cxxl
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class TaskStartupCompensator {

    private static final String LOCK_KEY_SUFFIX = ":compensate-lock";

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final TaskQueueSupport taskQueue;
    private final RedisLock redisLock;
    private final TaskQueueProperties properties;

    /** 应用就绪后执行一次启动补偿（补投 + 孤儿恢复） */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        compensate("启动补偿");
        recoverOrphans();
        if (properties.isLowFreqSweepEnabled()) {
            log.info("低频兜底已开启, interval={}", properties.getLowFreqSweepInterval());
        }
    }

    /** 低频兜底周期扫描（默认关闭；间隔在启动时固定） */
    @Scheduled(fixedDelayString = "#{@taskQueueProperties.lowFreqSweepInterval.toMillis()}")
    public void scheduledSweep() {
        if (!properties.isLowFreqSweepEnabled()) {
            return;
        }
        compensate("低频兜底");
        recoverOrphans();
    }

    /** RUNNING 孤儿恢复：开始时间超过其环节超时阈值的执行中任务 → 置 FAILED(EXECUTOR_TIMEOUT) */
    private void recoverOrphans() {
        String token = redisLock.tryLock(lockKey(), properties.getSweepLockTtl());
        if (token == null) {
            log.debug("补偿锁未抢到，跳过孤儿恢复");
            return;
        }
        try {
            Duration minTimeout = properties.getTimeout().values().stream()
                    .min(Duration::compareTo)
                    .orElse(Duration.ofMinutes(5));
            LocalDateTime threshold = LocalDateTime.now().minus(minTimeout);
            List<KbPipelineTask> stale = pipelineTaskDbService.listStaleRunning(threshold);
            int recovered = 0;
            for (KbPipelineTask task : stale) {
                Duration stageTimeout = properties.timeoutOf(task.getStage());
                if (ObjectUtil.isNotNull(task.getStartedAt())
                        && task.getStartedAt().isBefore(LocalDateTime.now().minus(stageTimeout))) {
                    pipelineTaskDbService.finish(task.getId(), PipelineTaskStatus.FAILED.name(),
                            PipelineTaskErrorCode.EXECUTOR_TIMEOUT.name(), "孤儿任务恢复：RUNNING 超过环节超时阈值");
                    recovered++;
                }
            }
            if (recovered > 0) {
                log.info("孤儿任务恢复完成, count={}", recovered);
            }
        } finally {
            redisLock.unlock(lockKey(), token);
        }
    }

    /** 补投过期 QUEUED 任务（消费侧重读 DB 校验，重复入队无害） */
    private void compensate(String scene) {
        String token = redisLock.tryLock(lockKey(), properties.getSweepLockTtl());
        if (token == null) {
            log.debug("补偿锁未抢到，跳过本轮, scene={}", scene);
            return;
        }
        try {
            LocalDateTime threshold = LocalDateTime.now().minus(properties.getCompensateThreshold());
            List<KbPipelineTask> stale = pipelineTaskDbService.listStaleQueued(threshold);
            for (KbPipelineTask task : stale) {
                taskQueue.enqueue(task.getId());
            }
            if (!stale.isEmpty()) {
                log.info("补偿重投递完成, scene={}, count={}", scene, stale.size());
            }
        } finally {
            redisLock.unlock(lockKey(), token);
        }
    }

    private String lockKey() {
        return properties.getQueueKey() + LOCK_KEY_SUFFIX;
    }
}
