package com.freshtrace.admin.support;

import com.freshtrace.admin.service.OperationLogService;
import com.freshtrace.security.LoginUser;
import com.freshtrace.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * 操作日志切面：管理端写操作成功后落库审计。
 * <p>
 * 采用 {@code @Around} 且仅在 {@code proceed()} 正常返回后记录 —— 业务校验失败/异常不产生「成功」日志；
 * 日志写入包裹 try/catch，任何异常都不得影响主业务（审计失败仅告警）。
 * 操作人取自 {@link UserContext}，目标ID 由 {@link OperationLog} 注解声明参数位置/属性解析，
 * IP 优先取 X-Forwarded-For 以便网关/代理场景。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            record(pjp, operationLog);
        } catch (Exception e) {
            log.warn("记录操作日志失败: action={}, targetType={}", operationLog.action(), operationLog.targetType(), e);
        }
        return result;
    }

    private void record(ProceedingJoinPoint pjp, OperationLog anno) {
        LoginUser loginUser = UserContext.get();
        // 实体与注解同名（admin.entity.OperationLog / admin.support.OperationLog），此处用全限定名区分
        com.freshtrace.admin.entity.OperationLog entity = new com.freshtrace.admin.entity.OperationLog();
        entity.setOperatorId(loginUser == null ? null : loginUser.getUserId());
        entity.setTargetType(anno.targetType());
        entity.setTargetId(resolveTargetId(pjp.getArgs(), anno));
        entity.setAction(anno.action());
        entity.setIpAddress(resolveIp());
        entity.setDetail(resolveDetail(pjp.getArgs()));
        operationLogService.record(entity);
    }

    /** 解析目标ID：参数本身为 Long，或按属性名（支持点路径）从对象中反射取值 */
    private Long resolveTargetId(Object[] args, OperationLog anno) {
        if (args == null || args.length <= anno.targetIdArgIndex()) {
            return null;
        }
        Object arg = args[anno.targetIdArgIndex()];
        if (arg == null) {
            return null;
        }
        String property = anno.targetIdProperty();
        if (property.isEmpty()) {
            return arg instanceof Number number ? number.longValue() : null;
        }
        Object value = readProperty(arg, property);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Object readProperty(Object target, String path) {
        Object current = target;
        for (String name : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            try {
                Field field = current.getClass().getDeclaredField(name);
                field.setAccessible(true);
                current = field.get(current);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return current;
    }

    /** 参数摘要：过滤 Servlet 对象后序列化业务参数，超长截断至 1000 字符 */
    private String resolveDetail(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Object[] printable = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof HttpServletRequest))
                    .toArray();
            String json = objectMapper.writeValueAsString(printable);
            return json.length() > 1000 ? json.substring(0, 1000) : json;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
