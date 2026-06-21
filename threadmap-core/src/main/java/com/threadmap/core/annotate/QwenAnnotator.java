package com.threadmap.core.annotate;

import java.util.Objects;

/**
 * 真实 LLM 标注器:构 prompt → ChatFn.chat → 解析 JSON → Annotation。
 * <p>两种失败语义:
 * <ul>
 *   <li><b>降级模式</b>(传入非 null fallback):网络/解析异常时降级到 fallback(离线 CLI 用)。</li>
 *   <li><b>严格模式</b>(fallback 为 null,见 {@link #QwenAnnotator(ChatFn)}):异常原样抛出,
 *       <b>绝不伪造</b>。插件交互标注必须用严格模式——否则失败会被假摘要冒充成功,误导用户。</li>
 * </ul>
 */
public class QwenAnnotator implements Annotator {
    private final ChatFn chat;
    private final PromptBuilder promptBuilder;
    private final AnnotationJsonParser jsonParser;
    private final Annotator fallback;

    /** 严格模式:失败抛真错,不降级、不伪造。 */
    public QwenAnnotator(ChatFn chat) {
        this(chat, new PromptBuilder(), new AnnotationJsonParser(), null);
    }

    /** 降级模式:失败降级到 fallback。 */
    public QwenAnnotator(ChatFn chat, Annotator fallback) {
        this(chat, new PromptBuilder(), new AnnotationJsonParser(),
                Objects.requireNonNull(fallback, "fallback"));
    }

    /** For testing: inject collaborators directly. fallback 可为 null(严格模式)。 */
    QwenAnnotator(ChatFn chat, PromptBuilder promptBuilder,
                  AnnotationJsonParser jsonParser, Annotator fallback) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.jsonParser = Objects.requireNonNull(jsonParser, "jsonParser");
        this.fallback = fallback;
    }

    @Override
    public Annotation annotate(AnnotationRequest request) {
        try {
            String reply = chat.chat(promptBuilder.build(request));
            return jsonParser.parse(reply);
        } catch (RuntimeException e) {
            if (fallback == null) {
                throw e; // 严格模式:抛真实错误,让上层显示失败,而不是伪造成功
            }
            // TODO(M2b-2): log e once a logging framework is on the classpath
            return fallback.annotate(request);
        }
    }
}
