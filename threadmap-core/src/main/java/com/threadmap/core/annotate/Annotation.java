package com.threadmap.core.annotate;

import java.util.List;

/** 一个节点的 AI 标注:只描述"做了什么",绑定证据,禁止空话。 */
public record Annotation(
        String summary,
        String inputs,
        String outputs,
        List<String> sideEffects,
        Evidence evidence,
        boolean digWorthy,
        String digReason
) {}
