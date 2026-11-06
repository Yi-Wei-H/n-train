package com.weiyi.common.mq.core;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.trace.TraceIdSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQListener;

import java.nio.charset.StandardCharsets;

/**
 * MQ 消费入口统一基类（规则 6）：恢复 traceId 的动作严格先于业务 {@code handle} 执行。
 *
 * <p>取值链：消息用户属性 {@code X-Trace-Id}（生产端 MqSender 写入）→ 缺失或非法（历史脏数据）
 * 则生成新值（兼容无标记的存量消息，Error Case 3/4）→ 写 MDC → 业务处理 → finally 清理。
 * 消费线程是池化的，put/remove 必须配对，否则后续消息会串上一个 traceId。
 *
 * <p>子类只需实现 {@link #handle(MessageExt)}：
 * <pre>{@code
 * @RocketMQMessageListener(topic = ..., consumerGroup = ...)
 * public class XxxConsumer extends TraceIdMqListener {
 *     @Override
 *     protected void handle(MessageExt message) { ... }
 * }
 * }</pre>
 *
 * <p>注意：必须实现 {@code RocketMQListener<MessageExt>}（本类已实现）。rocketmq-spring 通过
 * {@code getGenericInterfaces()} 解析消息类型，消费 MessageExt 才能拿到用户属性。
 *
 * <p>故障隔离（规则 9）：trace 的生成/恢复/清理任何异常都只 warn，业务处理结果不受影响。
 */
@Slf4j
public abstract class TraceIdMqListener implements RocketMQListener<MessageExt> {

    @Override
    public final void onMessage(MessageExt message) {
        try {
            TraceIdSupport.bind(TraceIdSupport.resolve(readTraceId(message)));
        } catch (Exception e) {
            log.warn("[trace] MQ 消费恢复 traceId 失败，本条按无标记处理: {}", e.getMessage());
        }
        try {
            handle(message);
        } finally {
            TraceIdSupport.clear();
        }
    }

    /** 业务消费逻辑：traceId 已就绪，直接写业务日志即可 */
    protected abstract void handle(MessageExt message);

    /** 消息用户属性里的 traceId（即生产端 MDC 的值），缺失返回 null */
    protected static String readTraceId(MessageExt message) {
        try {
            return message == null ? null : message.getUserProperty(Constants.HEADER_TRACE_ID);
        } catch (Exception e) {
            log.warn("[trace] 读取消息 traceId 属性失败: {}", e.getMessage());
            return null;
        }
    }

    /** 消息体按 UTF-8 解码（各消费端统一用它，避免各自重复写字符集） */
    protected static String bodyOf(MessageExt message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
