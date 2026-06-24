package org.example.api_playground.llm.controller;

import lombok.RequiredArgsConstructor;
import org.example.api_playground.llm.client.LlmApiClient;
import org.example.api_playground.llm.model.ChatRequest;
import org.example.api_playground.llm.model.EmbeddingRequest;
import org.example.api_playground.llm.model.EmbeddingResponse;
import org.example.api_playground.llm.model.EmbeddingSimilarityRequest;
import org.example.api_playground.llm.model.ApiCallRecord;
import org.example.api_playground.llm.model.Conversation;
import org.example.api_playground.llm.model.ConversationMessage;
import org.example.api_playground.llm.service.ChatService;
import org.example.api_playground.llm.service.ConversationService;
import org.example.api_playground.llm.service.EmbeddingService;
import org.example.api_playground.llm.service.RecordService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final LlmApiClient llmApiClient;
    private final EmbeddingService embeddingService;
    private final RecordService recordService;
    private final ConversationService conversationService;

    @GetMapping("/tools")
    public Map<String, Object> tools() {
        return Map.of("tools", chatService.getAvailableTools());
    }

    // 1. 带 Function Calling 的对话调用
    @PostMapping("/chat")
    public Mono<Map<String, Object>> chat(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-Base-Url") String baseUrl,
            @RequestBody ChatRequest request) {

        long startTime = System.currentTimeMillis();

        return chatService.chatWithTools(request, apiKey, baseUrl)
                .map(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    return Map.of(
                            "duration_ms", duration,
                            "model", response.getModel(),
                            "usage", response.getUsage(),
                            "output", response.getChoices().get(0).getMessage().getContent()
                    );
                });
    }

    // 2. 流式响应 (SSE) —— 支持完整工具调用链路
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-Base-Url") String baseUrl,
            @RequestParam(required = false) Long conversationId,
            @RequestBody ChatRequest request) {
        return chatService.chatStreamWithTools(request, apiKey, baseUrl, conversationId);
    }

    // 3. 模型/供应商对比 —— 同一问题并发发给两个模型，对比结果
    @PostMapping("/compare")
    public Mono<Map<String, Object>> compareModels(
            @RequestHeader("X-API-Key-A") String apiKeyA,
            @RequestHeader("X-Base-Url-A") String baseUrlA,
            @RequestHeader("X-Model-A") String modelA,
            @RequestHeader("X-API-Key-B") String apiKeyB,
            @RequestHeader("X-Base-Url-B") String baseUrlB,
            @RequestHeader("X-Model-B") String modelB,
            @RequestBody ChatRequest request) {

        return chatService.compareModels(request,
                apiKeyA, baseUrlA, modelA,
                apiKeyB, baseUrlB, modelB);
    }

    // 4. 结构化输出 —— 强制大模型输出合法 JSON，失败自动重试
    @PostMapping("/structured-output")
    public Mono<Map<String, Object>> structuredOutput(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-Base-Url") String baseUrl,
            @RequestBody ChatRequest request) {

        long startTime = System.currentTimeMillis();

        // 设置 response_format 要求大模型输出 JSON
        if (request.getResponseFormat() == null) {
            request.setResponseFormat(Map.of("type", "json_object"));
        }

        return chatService.chatWithJsonRetry(request, apiKey, baseUrl, 3)
                .map(jsonStr -> {
                    long duration = System.currentTimeMillis() - startTime;
                    return Map.of(
                            "duration_ms", duration,
                            "output", jsonStr
                    );
                });
    }

    // 5. 查询调用记录
    @GetMapping("/records")
    public List<ApiCallRecord> getRecords() {
        return recordService.findAll();
    }

    // 6. Embedding 相似度计算
    @PostMapping("/embedding/similarity")
    public Mono<Map<String, Object>> calculateSimilarity(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-Base-Url") String baseUrl,
            @RequestBody EmbeddingSimilarityRequest req) {

        EmbeddingRequest req1 = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(req.getText1()).build();
        EmbeddingRequest req2 = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(req.getText2()).build();

        Mono<EmbeddingResponse> mono1 = llmApiClient.createEmbedding(req1, apiKey, baseUrl);
        Mono<EmbeddingResponse> mono2 = llmApiClient.createEmbedding(req2, apiKey, baseUrl);

        return Mono.zip(mono1, mono2).map(tuple -> {
            List<Double> vector1 = tuple.getT1().getData().get(0).getEmbedding();
            List<Double> vector2 = tuple.getT2().getData().get(0).getEmbedding();

            double similarity = embeddingService.calculateCosineSimilarity(vector1, vector2);

            return Map.of(
                    "text1", req.getText1(),
                    "text2", req.getText2(),
                    "similarity", similarity,
                    "is_similar", similarity > 0.8
            );
        });
    }

    // ======================== 对话历史 ========================

    // 7. 对话列表
    @GetMapping("/conversations")
    public List<Conversation> getConversations() {
        return conversationService.getConversations();
    }

    // 8. 新建对话
    @PostMapping("/conversation")
    public Conversation createConversation(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "新对话");
        String mode = body.getOrDefault("mode", "general");
        return conversationService.createConversation(title, mode);
    }

    // 9. 删除对话
    @DeleteMapping("/conversation/{id}")
    public Map<String, Object> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Map.of("success", true);
    }

    // 10. 获取对话消息
    @GetMapping("/conversation/{id}/messages")
    public List<ConversationMessage> getMessages(@PathVariable Long id) {
        return conversationService.getMessages(id);
    }

    // 11. 保存消息
    @PostMapping("/conversation/{id}/message")
    public ConversationMessage saveMessage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return conversationService.saveMessage(id, body.get("role"), body.get("content"));
    }
}