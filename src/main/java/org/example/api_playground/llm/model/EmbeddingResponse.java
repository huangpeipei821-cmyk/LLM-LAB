package org.example.api_playground.llm.model;

import lombok.Data;
import java.util.List;

@Data
public class EmbeddingResponse {
    private List<EmbeddingData> data;
    private ChatResponse.Usage usage;

    @Data
    public static class EmbeddingData {
        private List<Double> embedding; // 核心：这就是你的向量数组
        private Integer index;
    }
}
