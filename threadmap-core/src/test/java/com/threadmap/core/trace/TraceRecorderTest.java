package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceRecorderTest {

    @Test
    void buildsNestedTreeWithTiming() {
        TraceRecorder r = new TraceRecorder();
        r.start();
        r.enter("A#a()", "A.java", 0);
        r.enter("B#b()", "B.java", 0);
        r.exit(30);
        r.exit(100);
        r.stop();

        TraceNode root = r.getRoot();
        assertNotNull(root);
        assertEquals("A#a()", root.getSignature());
        assertEquals(1, root.getChildren().size());
        assertEquals("B#b()", root.getChildren().get(0).getSignature());
        assertEquals(70, root.selfMs());
        assertEquals(30, root.getChildren().get(0).selfMs());
    }

    @Test
    void assignsIncrementingIdsInEnterOrder() {
        TraceRecorder r = new TraceRecorder();
        r.start();
        r.enter("A#a()", "A.java", 0);
        r.enter("B#b()", "B.java", 0);
        r.exit(1);
        r.exit(2);
        r.stop();

        assertEquals(0, r.getRoot().getId());
        assertEquals(1, r.getRoot().getChildren().get(0).getId());
    }

    @Test
    void recordsNothingWhenNotRecording() {
        TraceRecorder r = new TraceRecorder();
        r.enter("X#x()", "X.java", 0);
        r.exit(5);
        assertNull(r.getRoot());
    }

    @Test
    void startResetsPreviousRun() {
        TraceRecorder r = new TraceRecorder();
        r.start();
        r.enter("Old#old()", "Old.java", 0);
        r.exit(1);
        r.stop();

        r.start();
        r.enter("New#fresh()", "New.java", 0);
        r.exit(1);
        r.stop();

        assertEquals("New#fresh()", r.getRoot().getSignature());
        assertEquals(0, r.getRoot().getId());
    }
}
