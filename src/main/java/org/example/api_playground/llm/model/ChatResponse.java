package org.example.api_playground.llm.model;
//聊天响应模型
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ChatResponse {
    private String id;
    private String model;        // 回复模型名字
    private List<Choice> choices;// 核心的回复内容列表
    private Usage usage;         // 账单

    @Data
    public static class Choice {
        private Integer index;
        private ChatMessage message;// 大模型的具体回复
        private ChatMessage delta; // 流式响应时使用 delta 替代 message
        @JsonProperty("finish_reason")
        private String finishReason; // stop, tool_calls, length
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;//输入提示词
        @JsonProperty("completion_tokens")
        private Integer completionTokens;//模型的回答
        @JsonProperty("total_tokens")
        private Integer totalTokens;// 总消耗 Token 数
    }
}
