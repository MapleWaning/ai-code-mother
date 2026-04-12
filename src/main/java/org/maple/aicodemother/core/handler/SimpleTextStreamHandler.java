package org.maple.aicodemother.core.handler;

import lombok.extern.slf4j.Slf4j;
import org.maple.aicodemother.ai.model.enums.CodeGenTypeEnum;
import org.maple.aicodemother.model.entity.User;
import org.maple.aicodemother.model.enums.ChatHistoryMessageTypeEnum;
import org.maple.aicodemother.parser.CodeParserExecutor;
import org.maple.aicodemother.saver.CodeFileSaverExecutor;
import org.maple.aicodemother.service.ChatHistoryService;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param codeStream  代码流
     * @param chatHistoryService 历史服务
     * @return 流式响应
     * @param appId        应用ID
     * @param loginUser    登录用户
     */
    public Flux<String> handle(Flux<String> codeStream,
                                           ChatHistoryService chatHistoryService,
                                           Long appId, User loginUser) {
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream.doOnNext(codeBuilder::append).doOnComplete(() -> {
            // 流式返回完成后保存代码
            //添加历史记录
            String completeCode = codeBuilder.toString();
            chatHistoryService.addChatMessage(appId,completeCode, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        }).doOnError(error ->{
            String errorMessage = "AI回复失败：" + error.getMessage();
            chatHistoryService.addChatMessage(appId,errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        });
    }
}

