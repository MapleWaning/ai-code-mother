package org.maple.aicodemother.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    //独立配置qwen模型参数，避免影响其他模型的使用
    private String modelName;
    private int maxTokens ;
    private String baseUrl ;
    private String apiKey;

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用）
     */
    @Bean
    public StreamingChatModel reasoningStreamingChatModel() {
        //测试环境开启并行调用提高效率，生产环境根据实际情况调整
        boolean parallelToolCalls = true;
        // 生产环境使用：
//         final String modelName = "deepseek-reasoner";
//         final int maxTokens = 32768;
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .parallelToolCalls(parallelToolCalls)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}

