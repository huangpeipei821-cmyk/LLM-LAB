package org.example.api_playground.llm.client;

import org.example.api_playground.llm.model.ChatRequest;
import org.example.api_playground.llm.model.ChatResponse;

import org.example.api_playground.llm.model.EmbeddingRequest;
import org.example.api_playground.llm.model.EmbeddingResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmApiClient {
    // 普通阻塞调用
    Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl);

    // 流式调用返回原始 SSE 文本流
    Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl);

    Mono<EmbeddingResponse> createEmbedding(EmbeddingRequest request, String apiKey, String baseUrl);
}
