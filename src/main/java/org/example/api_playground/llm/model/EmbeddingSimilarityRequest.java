package org.example.api_playground.llm.model;

import lombok.Data;

@Data
public class EmbeddingSimilarityRequest {
    private String text1;
    private String text2;
}
