package org.example.api_playground.llm.client;

import org.example.api_playground.llm.model.ChatRequest;
import org.example.api_playground.llm.model.ChatResponse;
import org.example.api_playground.llm.model.EmbeddingRequest;
import org.example.api_playground.llm.model.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OpenAiCompatibleClient implements LlmApiClient {

    private final WebClient.Builder webClientBuilder;

    public OpenAiCompatibleClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request, String apiKey, String baseUrl) {
        request.setStream(false);
        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("大模型 API 客户端错误 (4xx): " + body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("大模型 API 服务端错误 (5xx): " + body))))
                .bodyToMono(ChatResponse.class);
    }

    @Override
    public Flux<String> chatStream(ChatRequest request, String apiKey, String baseUrl) {
        request.setStream(true);
        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("大模型 API 客户端错误 (4xx): " + body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("大模型 API 服务端错误 (5xx): " + body))))
                .bodyToFlux(String.class)
                .filter(line -> !line.trim().isEmpty())
                .map(this::stripDataPrefix)
                .filter(payload -> !payload.isEmpty());
    }

    // 去掉 LLM API 返回的 "data:" 前缀，避免与 Spring SSE 框架的 data: 双重嵌套
    private String stripDataPrefix(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("data:")) {
            String payload = trimmed.substring(5).trim();
            // DeepSeek 用 DONE，OpenAI 用 [DONE]，都兼容
            if (payload.equals("[DONE]") || payload.equals("DONE")) return "";
            return payload;
        }
        return trimmed;
    }

    @Override
    public Mono<EmbeddingResponse> createEmbedding(EmbeddingRequest request, String apiKey, String baseUrl) {
        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("大模型 API 客户端错误 (4xx): " + body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("大模型 API 服务端错误 (5xx): " + body))))
                .bodyToMono(EmbeddingResponse.class);
    }
}
