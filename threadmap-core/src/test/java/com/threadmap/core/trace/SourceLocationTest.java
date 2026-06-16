package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceLocationTest {

    static class Inner { }

    @Test
    void derivesSourceRelativePathFromClass() {
        assertEquals("com/threadmap/core/trace/SourceLocation.java",
                SourceLocation.fileFor(SourceLocation.class));
    }

    @Test
    void usesOuterClassForNestedTypes() {
        assertEquals("com/threadmap/core/trace/SourceLocationTest.java",
                SourceLocation.fileFor(Inner.class));
    }
}
