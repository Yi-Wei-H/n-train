package com.weiyi.admin.operlog;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.MqConst;
import com.weiyi.common.mq.core.MqSender;
import com.weiyi.common.mq.event.OperLogEvent;
import com.weiyi.common.redis.util.RequestIdentityUtil;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.common.security.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作日志切面：拦截 @OperLog 标注的管理端写接口，记录操作人、入参（脱敏）、
 * 结果码、耗时与来源 IP，经 MQ 异步落库（fire-and-forget，失败不影响业务）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 入参 JSON 截断上限（列本身是 TEXT，截断只为控制单条日志与 MQ 消息体量） */
    private static final int PARAMS_MAX_LEN = 2000;

    /** 敏感字段名（小写子串匹配）：密码/密钥类字段的值脱敏为 *** */
    private static final Pattern SENSITIVE_KEY = Pattern.compile("password|passwd|secret|apikey|api_key|token");

    private final MqSender mqSender;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Integer code = null;
        try {
            Object result = pjp.proceed();
            code = result instanceof Result<?> r ? r.getCode() : null;
            return result;
        } catch (BusinessException e) {
            code = e.getCode();
            throw e;
        } catch (Throwable t) {
            code = ErrorCode.SYSTEM_ERROR.getCode();
            throw t;
        } finally {
            publish(operLog, pjp, code, start);
        }
    }

    /** 组装事件并投递；无登录上下文（联调直连未带身份头）不记录 */
    private void publish(OperLog operLog, ProceedingJoinPoint pjp, Integer code, long start) {
        LoginUser user = LoginContext.get();
        if (user == null) {
            return;
        }
        try {
            OperLogEvent event = new OperLogEvent();
            event.setUserId(user.getUserId());
            event.setUsername(user.getUsername());
            event.setModule(operLog.module());
            event.setOperation(operLog.operation());
            event.setMethod(requestDesc());
            event.setParams(params(pjp));
            event.setResultCode(code);
            event.setIp(RequestIdentityUtil.currentIp());
            event.setCostMs((int) (System.currentTimeMillis() - start));
            String key = user.getUsername() != null ? user.getUsername() : String.valueOf(user.getUserId());
            mqSender.sendQuietly(MqConst.TOPIC_LOG_EVENT, MqConst.TAG_LOG_OPER, key, event);
        } catch (Exception e) {
            log.warn("操作日志投递失败: {}", e.getMessage());
        }
    }

    /** 方法标识：HTTP 方法 + 服务内 URI（网关 StripPrefix 后的路径），如 POST /system/user */
    private String requestDesc() {
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getMethod() + " " + request.getRequestURI();
    }

    /** 入参 JSON：敏感字段值脱敏、超长截断；不可序列化时退化为占位串 */
    private String params(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args.length == 0) {
            return null;
        }
        try {
            JsonNode node = JacksonUtil.MAPPER.valueToTree(args);
            mask(node);
            String json = JacksonUtil.MAPPER.writeValueAsString(node);
            return json.length() > PARAMS_MAX_LEN ? json.substring(0, PARAMS_MAX_LEN) + "...(截断)" : json;
        } catch (Exception e) {
            return "(unserializable args)";
        }
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            for (String name : List.copyOf(obj.propertyNames())) {
                if (SENSITIVE_KEY.matcher(name.toLowerCase()).find()) {
                    obj.put(name, "***");
                } else {
                    mask(obj.get(name));
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::mask);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
