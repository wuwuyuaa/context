package com.threadmap.core.annotate;

/** 极简对话接缝:把 prompt 发给某个 LLM,返回纯文本回复。
 *  隔离具体 SDK(LangChain4j),让 QwenAnnotator 可用桩离线测试。 */
@FunctionalInterface
public interface ChatFn {
    String chat(String prompt);
}
