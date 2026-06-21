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
 * 直连任意 OpenAI 兼容 {@code /chat/completions} 的 {@link ChatFn}。
 * 覆盖 OpenAI / DeepSeek / Kimi(Moonshot)/ 智谱 GLM / 通义 compatible-mode / Ollama 等——
 * 它们用同一套请求/响应格式,只是 baseUrl + model + key 不同。让插件可选服务商。
 */
public final class OpenAiCompatibleChat implements ChatFn {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public OpenAiCompatibleChat(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.replaceAll("/+$", ""); // 去掉结尾斜杠
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String chat(String prompt) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", prompt);

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode content = MAPPER.readTree(resp.body())
                    .path("choices").path(0).path("message").path("content");
            return content.asText("");
        } catch (Exception e) {
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
}
