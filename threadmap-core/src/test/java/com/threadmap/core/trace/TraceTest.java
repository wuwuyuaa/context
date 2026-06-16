package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceTest {
    @Test
    void constructorRoundTrips() {
        TraceNode root = new TraceNode(0, "A#a()", "A.java", 0);
        Trace trace = new Trace("A#a()", "2026-06-16T00:00:00Z", root);
        assertEquals("A#a()", trace.getEntrySignature());
        assertEquals("2026-06-16T00:00:00Z", trace.getCapturedAt());
        assertSame(root, trace.getRoot());
    }
}
