package com.threadmap.core.annotate;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/** 构造 DashScope(Qwen)的 ChatFn。本模块唯一直接依赖 LangChain4j 的地方。 */
public final class QwenChatModels {

    /**
     * DashScope 原生 API 的默认文本生成模型(flash 系列)。
     *
     * <p>注意:这里必须用 DashScope 原生 model id(如 {@code qwen-flash}),而非 OpenAI
     * compatible-mode 的版本化 id(如 {@code qwen3.6-flash})—— QwenChatModel 走原生
     * Generation API,传 compatible-mode id 会被拒("url error, InvalidParameter")。
     * M4 真实链路验证时发现并修正。
     */
    public static final String DEFAULT_MODEL = "qwen-flash";

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
