package com.weiyi.admin.mq;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.MqConst;
import com.weiyi.common.mq.core.TraceIdMqListener;
import com.weiyi.common.mq.event.LoginLogEvent;
import com.weiyi.common.mq.event.OperLogEvent;
import com.weiyi.admin.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 跨服务日志消费：会员端登录/操作日志经 MQ 异步汇入 sys 库
 * （替代原先 Feign 同步写数据端；插入幂等天然成立，重复消费只多一条日志可容忍）。
 * 继承 TraceIdMqListener：日志本身也带上产生它的请求 traceId，便于按 traceId 串联。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConst.TOPIC_LOG_EVENT,
        consumerGroup = MqConst.GROUP_ADMIN_LOG,
        selectorExpression = MqConst.TAG_LOG_LOGIN + " || " + MqConst.TAG_LOG_OPER)
public class LogEventConsumer extends TraceIdMqListener {

    private final LogService logService;

    @Override
    protected void handle(MessageExt message) {
        String tag = message.getTags();
        String body = bodyOf(message);
        switch (tag == null ? "" : tag) {
            case MqConst.TAG_LOG_LOGIN -> {
                LoginLogEvent event = JacksonUtil.fromJson(body, LoginLogEvent.class);
                logService.saveLoginLog(Constants.USER_TYPE_MEMBER, event);
            }
            case MqConst.TAG_LOG_OPER -> {
                OperLogEvent event = JacksonUtil.fromJson(body, OperLogEvent.class);
                logService.saveOperLog(event);
            }
            default -> log.warn("[mq] 未知日志事件 tag={}", tag);
        }
    }
}
