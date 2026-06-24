package org.example.api_playground.llm.model;
//聊天消息模型
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    private String role; // system, user, assistant, tool
    private String content;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;//调用函数的列表

    @JsonProperty("tool_call_id")
    private String toolCallId; // 当 role=tool 时必须提供

    @Data
    public static class ToolCall {
        private String id;
        private String type; // 通常为 "function"
        private FunctionCall function;
    }
//对应 OpenAI 协议中的 tool_calls 数组元素
//  "id": "call_abc123",
//  "type": "function",
//  "function": { "name": "get_weather", "arguments": "{\"city\":\"Beijing\"}" }

    @Data
    public static class FunctionCall {
        private String name;
        private String arguments; // 序列化后的 JSON 字符串
    }
}
