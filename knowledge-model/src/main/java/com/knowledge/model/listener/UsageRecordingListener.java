package com.knowledge.model.listener;

import com.knowledge.common.utils.NullUtil;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用量监听器：挂在对话模型实例上，记录一次调用的用量、延迟与错误，并按级别打印一行。
 * 只观察调用过程，不改变调用结果；属性表里没有调用上下文即静默跳过。明细落库归计量片。
 *
 * <p>尝试次数不在这里计：LC4j 的重试发生在一次 `chat` 调用内部，本监听器的回调一次调用只触发一次，
 * 计数落在 HTTP 客户端装饰器 {@link CountingHttpClient} 上。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class UsageRecordingListener implements ChatModelListener {

    /** 请求到达：记录首次请求的时间戳；尝试次数由 HTTP 客户端装饰器写入 */
    @Override
    public void onRequest(ChatModelRequestContext context) {
        ModelCallTrace trace = trace(context.attributes());
        if (NullUtil.isNull(trace)) {
            return;
        }
        trace.markRequestStart();
    }

    /** 响应到达：写入用量与延迟 */
    @Override
    public void onResponse(ChatModelResponseContext context) {
        ModelCallTrace trace = trace(context.attributes());
        if (NullUtil.isNull(trace)) {
            return;
        }
        trace.setUsage(ModelUsageMapper.toModelUsage(context.chatResponse().tokenUsage()));
        trace.markLatency();
        log.info("模型调用成功，尝试次数=[{}]，重试次数=[{}]，延迟毫秒=[{}]，最近尝试毫秒=[{}]，用量=[{}]",
                trace.attempts(), trace.retryCount(), trace.latencyMs(), trace.lastAttemptLatencyMs(), trace.usage());
    }

    /** 出错：写入延迟与原始异常，错误码的映射由调用器完成 */
    @Override
    public void onError(ChatModelErrorContext context) {
        ModelCallTrace trace = trace(context.attributes());
        if (NullUtil.isNull(trace)) {
            return;
        }
        trace.setError(context.error());
        trace.markLatency();
        log.warn("模型调用失败，尝试次数=[{}]，重试次数=[{}]，延迟毫秒=[{}]，最近尝试毫秒=[{}]，错误=[{}]",
                trace.attempts(), trace.retryCount(), trace.latencyMs(), trace.lastAttemptLatencyMs(), trace.error());
    }

    /** 属性表取调用上下文；未带上下文即返回 null */
    private ModelCallTrace trace(Map<Object, Object> attributes) {
        Object value = NullUtil.isNull(attributes) ? null : attributes.get(ModelCallTrace.ATTRIBUTE_KEY);
        return value instanceof ModelCallTrace trace ? trace : null;
    }
}
