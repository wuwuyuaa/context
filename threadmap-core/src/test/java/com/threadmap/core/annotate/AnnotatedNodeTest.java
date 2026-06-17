package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnotatedNodeTest {

    @Test
    void defaultsAndMutationsWork() {
        AnnotatedNode n = new AnnotatedNode(0, "A#a()", "A.java", 0, 12);
        assertEquals(0, n.getId());
        assertEquals("A#a()", n.getSignature());
        assertEquals(12, n.getSelfMs());
        assertFalse(n.isCollapsed());
        assertNull(n.getAnnotation());
        assertEquals(Understanding.UNKNOWN, n.getUnderstanding());

        n.setCollapsed(true);
        n.setUnderstanding(Understanding.MASTERED);
        assertTrue(n.isCollapsed());
        assertEquals(Understanding.MASTERED, n.getUnderstanding());

        AnnotatedNode child = new AnnotatedNode(1, "B#b()", "B.java", 0, 3);
        n.addChild(child);
        assertEquals(1, n.getChildren().size());
        assertSame(child, n.getChildren().get(0));
    }

    @Test
    void getChildrenIsUnmodifiable() {
        AnnotatedNode n = new AnnotatedNode(0, "A#a()", "A.java", 0, 1);
        assertThrows(UnsupportedOperationException.class,
                () -> n.getChildren().add(n));
    }
}
