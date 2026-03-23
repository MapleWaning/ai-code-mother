package org.maple.aicodemother.interceptor;


import cn.hutool.core.date.StopWatch;
import cn.hutool.core.lang.UUID;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.ArrayUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
public class LogInterceptor {
    /**
     * 执行拦截
     * 这里的 execution 表达式非常关键，意思是拦截 controller 包下的所有类的所有方法
     * 请把 org.maple.aicodemother 替换为你自己的实际包名！
     */
    @Around("execution(* org.maple.aicodemother.controller.*.*(..))")
    public Object doInterceptor(ProceedingJoinPoint point) throws Throwable {
        // 计时器
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 获取请求路径
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 生成一个唯一的请求ID，方便在茫茫日志中追踪某一次具体的请求
        String requestId = UUID.randomUUID().toString();
        String url = httpServletRequest.getRequestURI();
        // 获取请求参数
        Object[] args = point.getArgs();
        String reqParam = "[" + ArrayUtil.join(args, ", ") + "]";

        // 输出请求日志
        log.info("收到请求，id: {}, path: {}, ip: {}, params: {}",
                requestId, url, httpServletRequest.getRemoteHost(), reqParam);

        // 执行原有的 Controller 业务逻辑
        Object result = point.proceed();

        // 停止计时
        stopWatch.stop();
        long totalTimeMillis = stopWatch.getTotalTimeMillis();

        // 输出响应日志
        log.info("请求结束, id: {}, cost: {}ms", requestId, totalTimeMillis);

        return result;
    }
}
