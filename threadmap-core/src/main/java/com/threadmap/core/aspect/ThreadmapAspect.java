package com.threadmap.core.aspect;

import com.threadmap.core.trace.TraceRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/** Bean 级追踪切面。Task 6 为 stub(透传);Task 7 实现记录逻辑。 */
@Aspect
public class ThreadmapAspect {
    private final TraceRecorder recorder;

    public ThreadmapAspect(TraceRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@within(org.springframework.stereotype.Service) || "
          + "@within(org.springframework.stereotype.Repository) || "
          + "@within(org.springframework.stereotype.Component) || "
          + "@within(org.springframework.stereotype.Controller) || "
          + "@within(org.springframework.web.bind.annotation.RestController)")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        // Task 7 在此实现 enter/exit;当前透传。
        return pjp.proceed();
    }
}
