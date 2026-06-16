package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class SignatureFormatterTest {

    static class Sample {
        public String greet(String name, int times) { return null; }
        public void noArgs() { }
    }

    @Test
    void formatsFqnHashMethodWithSimpleParamTypes() throws Exception {
        Method m = Sample.class.getMethod("greet", String.class, int.class);
        assertEquals(
            "com.threadmap.core.trace.SignatureFormatterTest$Sample#greet(String, int)",
            SignatureFormatter.format(m));
    }

    @Test
    void formatsNoArgMethodWithEmptyParens() throws Exception {
        Method m = Sample.class.getMethod("noArgs");
        assertEquals(
            "com.threadmap.core.trace.SignatureFormatterTest$Sample#noArgs()",
            SignatureFormatter.format(m));
    }
}
