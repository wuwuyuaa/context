package com.threadmap.core.trace;

/** 由类推导源码相对路径(行号留待下游用 PSI 按签名反查)。 */
public final class SourceLocation {
    private SourceLocation() { }

    public static String fileFor(Class<?> type) {
        String name = type.getName();
        int dollar = name.indexOf('$');
        if (dollar >= 0) {
            name = name.substring(0, dollar); // 内部类归到外层类的源文件
        }
        return name.replace('.', '/') + ".java";
    }
}
