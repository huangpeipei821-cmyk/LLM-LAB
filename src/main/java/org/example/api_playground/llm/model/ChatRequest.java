package org.example.api_playground.llm.model;
//聊天请求模型
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {
    private String model;                               // 模型名称
    private List<ChatMessage> messages;                 // 对话历史
    private Boolean stream;                             // 是否流式返回
    private Double temperature;                         // 随机性参数
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;         // 结构化输出，如 {"type": "json_object"}
    private List<Object> tools;                         // Function Calling 工具定义
}