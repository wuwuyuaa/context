package com.threadmap.core.trace;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 把一个方法格式化为 "全限定类名#方法名(简单参数类型, ...)"。 */
public final class SignatureFormatter {
    private SignatureFormatter() { }

    public static String format(Method m) {
        String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return m.getDeclaringClass().getName() + "#" + m.getName() + "(" + params + ")";
    }
}
