package com.threadmap.core.annotate;

import java.util.Objects;

/**
 * 真实 LLM 标注器:构 prompt → ChatFn.chat → 解析 JSON → Annotation。
 * 任何异常(网络/解析失败)降级到注入的 fallback 标注器(离线降级)。
 */
public class QwenAnnotator implements Annotator {
    private final ChatFn chat;
    private final PromptBuilder promptBuilder;
    private final AnnotationJsonParser jsonParser;
    private final Annotator fallback;

    public QwenAnnotator(ChatFn chat, Annotator fallback) {
        this(chat, new PromptBuilder(), new AnnotationJsonParser(), fallback);
    }

    /** For testing: inject collaborators directly. */
    QwenAnnotator(ChatFn chat, PromptBuilder promptBuilder,
                  AnnotationJsonParser jsonParser, Annotator fallback) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.jsonParser = Objects.requireNonNull(jsonParser, "jsonParser");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public Annotation annotate(AnnotationRequest request) {
        try {
            String reply = chat.chat(promptBuilder.build(request));
            return jsonParser.parse(reply);
        } catch (RuntimeException e) {
            // TODO(M2b-2): log e once a logging framework is on the classpath
            return fallback.annotate(request);
        }
    }
}
