package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 直连 DashScope 原生 Generation API 的 {@link ChatFn}——不依赖 LangChain4j,只用 jackson + JDK HttpClient。
 * 让插件(排除了 langchain4j)也能复用整套 QwenAnnotator 逻辑做标注。
 */
public final class DashScopeHttpChat implements ChatFn {

    private static final String URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public DashScopeHttpChat(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String chat(String prompt) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putObject("input").putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            body.putObject("parameters").put("result_format", "message");

            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("DashScope HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode content = root.path("output").path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                content = root.path("output").path("text"); // result_format=text 的形状兜底
            }
            return content.asText("");
        } catch (Exception e) {
            throw new RuntimeException("DashScope 调用失败: " + e.getMessage(), e);
        }
    }
}
