package com.threadmap.core.annotate;

import java.util.List;

/** 标注证据:源文件、行号区间(如 "32-62")、关键被调方法。 */
public record Evidence(String file, String lines, List<String> calls) {}
