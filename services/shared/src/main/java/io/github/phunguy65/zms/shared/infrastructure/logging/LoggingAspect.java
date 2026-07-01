package io.github.phunguy65.zms.shared.infrastructure.logging;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;

/**
 * AOP aspect that intercepts all {@code @Service}-annotated beans and emits DEBUG-level log
 * entries with {@code -->} on method entry and {@code <--} on exit (with duration). Exceptions are
 * logged at ERROR level and re-thrown. Argument logging is opt-in via
 * {@code logging.aspect.include-args=true}.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
@Aspect
@ConditionalOnWebApplication(type = Type.SERVLET)
public class LoggingAspect {

    @Value("${logging.aspect.include-args:false}")
    private boolean includeArgs;

    @Around("@within(org.springframework.stereotype.Service)")
    public Object logServiceMethod(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        Logger log = LoggerFactory.getLogger(pjp.getTarget().getClass());

        if (includeArgs) {
            log.debug("--> {}.{}({})", className, methodName, Arrays.toString(pjp.getArgs()));
        } else {
            log.debug("--> {}.{}()", className, methodName);
        }

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long duration = System.currentTimeMillis() - start;
            log.debug("<-- {}.{}() ({}ms)", className, methodName, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error(
                    "<-- {}.{}() ({}ms) error={}",
                    className,
                    methodName,
                    duration,
                    ex.getMessage());
            throw ex;
        }
    }
}
