package com.threadmap.core.annotate;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/** 构造 DashScope(Qwen)的 ChatFn。本模块唯一直接依赖 LangChain4j 的地方。 */
public final class QwenChatModels {

    /** 百炼/Model Studio 文本生成默认模型。 */
    public static final String DEFAULT_MODEL = "qwen3.6-flash";

    private QwenChatModels() {
    }

    public static ChatFn create(String apiKey, String modelName) {
        ChatModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        return model::chat; // ChatModel.chat(String) -> String
    }
}
