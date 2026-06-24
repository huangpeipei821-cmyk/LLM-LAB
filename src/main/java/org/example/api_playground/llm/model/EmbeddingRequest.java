package org.example.api_playground.llm.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmbeddingRequest {
    private String model; // 例如 text-embedding-v1
    private String input; // 你想要转换的文本
}
