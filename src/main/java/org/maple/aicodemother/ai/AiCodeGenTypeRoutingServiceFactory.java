package org.maple.aicodemother.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.maple.aicodemother.ai.guardrail.PromptSafetyInputGuardrail;
import org.maple.aicodemother.ai.guardrail.RetryOutputGuardrail;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由服务工厂
 *
 * @author yupi
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiCodeGenTypeRoutingServiceFactory {


    @Qualifier("routingChatModelPrototype")
    private final ObjectProvider<ChatModel> routingChatModelProvider;

    /**
     * 创建AI代码生成类型路由服务实例
     */
    @Bean
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        ChatModel chatModel = routingChatModelProvider.getObject();
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .inputGuardrails(new PromptSafetyInputGuardrail())
                .build();
    }
}

