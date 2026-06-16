package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceNodeTest {

    @Test
    void selfMsExcludesChildrenElapsed() {
        TraceNode root = new TraceNode(0, "A#a()", "A.java", 0);
        root.setElapsedMs(100);
        TraceNode child = new TraceNode(1, "B#b()", "B.java", 0);
        child.setElapsedMs(30);
        root.addChild(child);

        assertEquals(70, root.selfMs());
        assertEquals(30, child.selfMs());
        assertEquals(1, root.getChildren().size());
    }

    @Test
    void selfMsNeverNegative() {
        TraceNode root = new TraceNode(0, "A#a()", "A.java", 0);
        root.setElapsedMs(10);
        TraceNode child = new TraceNode(1, "B#b()", "B.java", 0);
        child.setElapsedMs(50); // 子节点计时大于父(时钟抖动)
        root.addChild(child);

        assertEquals(0, root.selfMs());
    }
}
