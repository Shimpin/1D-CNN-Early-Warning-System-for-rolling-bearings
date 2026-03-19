package org.sxk.dcnn.earlywarning.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.sxk.dcnn.earlywarning.config.LlmProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 调用 LM Studio 本地服务（原生 v1 REST API）实现 AI 对话。
 * 文档：<a href="https://lmstudio.ai/docs/developer/rest">LM Studio REST API</a>
 * 接口：POST /api/v1/chat，请求体 model + input，响应 output[].content（type=message）
 */
@Service
public class LlmChatService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmProperties llmProperties;
    /** 非流式请求也直接读取原始响应流，避免 LM Studio 返回 octet-stream 时转换失败 */
    private final SimpleClientHttpRequestFactory requestFactory;
    /** 流式请求用：直接拿原始响应流，避免 RestClient 对 text/event-stream 做转换 */
    private final SimpleClientHttpRequestFactory streamRequestFactory;
    private final String streamBaseUrl;

    public LlmChatService(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        String baseUrl = llmProperties.getApiUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = (baseUrl != null && !baseUrl.isBlank())
            ? baseUrl
            : "http://127.0.0.1:1234";
        this.requestFactory = new SimpleClientHttpRequestFactory();
        this.requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        this.requestFactory.setReadTimeout(java.time.Duration.ofSeconds(90));
        this.streamRequestFactory = new SimpleClientHttpRequestFactory();
        this.streamRequestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        this.streamRequestFactory.setReadTimeout(java.time.Duration.ofSeconds(300));
        this.streamBaseUrl = url;
    }

    /**
     * 发送用户问题，返回模型回复文本。
     * 若未配置 apiKey 或请求失败，返回友好提示。
     */
    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "请输入您要咨询的问题。";
        }
        if (llmProperties.getApiKey() == null || llmProperties.getApiKey().isBlank()) {
            return "未配置 LM Studio API Token，请在 application.properties 中设置 app.llm.api-key。";
        }

        String model = llmProperties.getModel() != null && !llmProperties.getModel().isBlank()
            ? llmProperties.getModel()
            : "default";
        // LM Studio 原生 API：POST /api/v1/chat，body 为 model + input（字符串或数组）
        Map<String, Object> body = Map.of(
            "model", model,
            "input", userMessage.trim()
        );

        try {
            LmStudioChatResponse response = executeChatRequest(body);

            if (response != null && response.getOutput() != null) {
                for (OutputItem item : response.getOutput()) {
                    if (item != null && "message".equals(item.getType()) && item.getContent() != null) {
                        return item.getContent().trim();
                    }
                }
            }
            return "模型未返回有效内容，请重试。";
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("401") || msg.contains("Unauthorized")) {
                return "API Token 无效，请检查 app.llm.api-key 或在 LM Studio 中配置认证。";
            }
            if (msg.contains("connection refused") || msg.contains("Connection refused") || msg.contains("Connection reset")) {
                return "无法连接 LM Studio，请先启动 LM Studio 并开启 Local Server（如 lms server start --port 1234）。";
            }
            if (msg.contains("Read timed out") || msg.contains("timeout") || msg.contains("Timeout")) {
                return "请求 LM Studio 超时（90 秒），请确认模型已加载且 Local Server 已启动，或稍后重试。";
            }
            return "请求 LM Studio 失败：" + msg;
        }
    }

    /**
     * 流式对话：请求 LM Studio 时带 stream: true，解析 SSE 中的 message.delta 并回调 onDelta。
     * 用于前端边收边显。异常会抛出给调用方（如 SseEmitter 线程）处理。
     */
    public void streamChat(String userMessage, Consumer<String> onDelta) throws Exception {
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        if (llmProperties.getApiKey() == null || llmProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 LM Studio API Token");
        }
        String model = llmProperties.getModel() != null && !llmProperties.getModel().isBlank()
            ? llmProperties.getModel()
            : "default";
        Map<String, Object> body = Map.of(
            "model", model,
            "input", userMessage.trim(),
            "stream", true
        );
        URI uri = URI.create(streamBaseUrl + "/api/v1/chat");
        ClientHttpRequest request = streamRequestFactory.createRequest(uri, HttpMethod.POST);
        request.getHeaders().set("Authorization", "Bearer " + llmProperties.getApiKey());
        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        request.getBody().write(OBJECT_MAPPER.writeValueAsBytes(body));
        ClientHttpResponse response = request.execute();
        if (response.getStatusCode().isError()) {
            throw new RuntimeException("LM Studio 返回 " + response.getStatusCode());
        }
        InputStream stream = response.getBody();
        if (stream == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String eventType = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:") && eventType != null) {
                    String json = line.substring(5).trim();
                    if (json.isEmpty()) continue;
                    try {
                        JsonNode node = OBJECT_MAPPER.readTree(json);
                        String type = node.has("type") ? node.get("type").asText() : "";
                        if ("message.delta".equals(type) && node.has("content")) {
                            String content = node.get("content").asText();
                            if (content != null && !content.isEmpty()) {
                                onDelta.accept(content);
                            }
                        }
                        if ("error".equals(type) && node.has("error")) {
                            JsonNode err = node.get("error");
                            String msg = err.has("message") ? err.get("message").asText() : "Unknown error";
                            throw new RuntimeException("LM Studio: " + msg);
                        }
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) throw e;
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private LmStudioChatResponse executeChatRequest(Map<String, Object> body) throws Exception {
        URI uri = URI.create(streamBaseUrl + "/api/v1/chat");
        ClientHttpRequest request = requestFactory.createRequest(uri, HttpMethod.POST);
        request.getHeaders().set("Authorization", "Bearer " + llmProperties.getApiKey());
        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        request.getBody().write(OBJECT_MAPPER.writeValueAsBytes(body));
        ClientHttpResponse response = request.execute();
        if (response.getStatusCode().isError()) {
            throw new RuntimeException("LM Studio 返回 " + response.getStatusCode());
        }
        try (InputStream stream = response.getBody()) {
            if (stream == null) {
                return null;
            }
            return OBJECT_MAPPER.readValue(stream, LmStudioChatResponse.class);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LmStudioChatResponse {
        private String modelInstanceId;
        private List<OutputItem> output;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputItem {
        private String type;
        private String content;
    }
}
