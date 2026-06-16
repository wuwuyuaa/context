package com.threadmap.core.aspect;

import com.threadmap.core.trace.SignatureFormatter;
import com.threadmap.core.trace.SourceLocation;
import com.threadmap.core.trace.TraceRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/** Bean 级追踪切面:在 Bean 间方法调用上记录 enter/exit,组成调用树。 */
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
        if (!recorder.isRecording()) {
            return pjp.proceed();
        }
        if (!(pjp.getSignature() instanceof MethodSignature ms)) {
            return pjp.proceed(); // 非方法连接点(构造器/字段),跳过
        }
        String signature = SignatureFormatter.format(ms.getMethod());
        String file = SourceLocation.fileFor(ms.getDeclaringType());
        recorder.enter(signature, file, 0);
        long startNs = System.nanoTime(); // 计时在 enter 之后开始,记录开销不计入方法耗时
        try {
            return pjp.proceed();
        } finally {
            recorder.exit((System.nanoTime() - startNs) / 1_000_000);
        }
    }
}
