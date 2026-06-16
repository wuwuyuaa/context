package com.threadmap.core.integration;

import com.threadmap.core.trace.Trace;
import com.threadmap.core.trace.TraceJsonWriter;
import com.threadmap.core.trace.TraceNode;
import com.threadmap.core.trace.TraceRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BeanLevelTraceIntegrationTest {

    @Autowired
    SampleController controller;

    @Autowired
    TraceRecorder recorder;

    @AfterEach
    void stopRecording() {
        recorder.stop(); // recorder 是单例 Bean,确保用例之间不串状态
    }

    @Test
    void capturesBeanLevelCallTreeAndWritesTraceJson(@TempDir Path tmp) throws Exception {
        recorder.start();
        controller.handle();
        recorder.stop();

        TraceNode root = recorder.getRoot();
        assertNotNull(root, "切面应当记录到一个根节点");
        assertTrue(root.getSignature().endsWith("SampleController#handle()"),
                "根应为控制器入口,实际:" + root.getSignature());

        assertEquals(1, root.getChildren().size());
        TraceNode service = root.getChildren().get(0);
        assertTrue(service.getSignature().contains("SampleService#doWork"),
                "实际:" + service.getSignature());

        assertEquals(1, service.getChildren().size());
        TraceNode repo = service.getChildren().get(0);
        assertTrue(repo.getSignature().contains("SampleRepository#load"),
                "实际:" + repo.getSignature());

        Path out = tmp.resolve("trace.json");
        String json = new TraceJsonWriter()
                .toJson(new Trace(root.getSignature(), Instant.now().toString(), root));
        Files.writeString(out, json);
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }
}
