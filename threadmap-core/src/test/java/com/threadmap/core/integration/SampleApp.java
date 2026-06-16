package com.threadmap.core.integration;

import com.threadmap.core.aspect.ThreadmapAspect;
import com.threadmap.core.trace.TraceRecorder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
class SampleApp {

    @Bean
    TraceRecorder traceRecorder() { return new TraceRecorder(); }

    @Bean
    ThreadmapAspect threadmapAspect(TraceRecorder recorder) {
        return new ThreadmapAspect(recorder);
    }
}
