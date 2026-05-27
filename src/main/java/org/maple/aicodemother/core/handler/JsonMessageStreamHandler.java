package org.maple.aicodemother.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.maple.aicodemother.ai.model.message.*;
import org.maple.aicodemother.model.entity.User;
import org.maple.aicodemother.model.enums.ChatHistoryMessageTypeEnum;
import org.maple.aicodemother.service.ChatHistoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    private static final String STREAMED_TOOL_RESULT = "__streamed_to_frontend__";

    /**
     * 处理 Python VUE_PROJECT JSON 流
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    //异步构建vue项目
//                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
//                    vueProjectBuilder.buildProjectAsync(projectPath);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.error("不支持的消息类型: {}", streamMessage.getType());
            return "";
        }
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                String seenKey = StrUtil.blankToDefault(toolId, toolName + ":" + toolRequestMessage.getArguments());
                // 检查是否是第一次看到这个工具 ID
                if (!seenToolIds.contains(seenKey)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(seenKey);
                    return generateToolRequestResponse(toolName);
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                if (STREAMED_TOOL_RESULT.equals(toolExecutedMessage.getResult())) {
                    return "";
                }
                JSONObject arguments = parseToolArguments(toolExecutedMessage.getArguments());
                String result = generateToolExecutedResult(toolExecutedMessage.getName(), arguments, toolExecutedMessage.getResult());
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    private String generateToolRequestResponse(String toolName) {
        return String.format("\n\n[选择工具] %s\n\n", getToolDisplayName(toolName));
    }

    private String generateToolExecutedResult(String toolName, JSONObject arguments, String toolResult) {
        return switch (StrUtil.blankToDefault(toolName, "")) {
            case "writeFile" -> {
                String relativeFilePath = arguments.getStr("relativeFilePath");
                String suffix = FileUtil.getSuffix(relativeFilePath);
                String content = arguments.getStr("content");
                yield String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getToolDisplayName(toolName), relativeFilePath, suffix, content);
            }
            case "modifyFile" -> {
                String relativeFilePath = arguments.getStr("relativeFilePath");
                String oldContent = arguments.getStr("oldContent");
                String newContent = arguments.getStr("newContent");
                yield String.format("""
                        [工具调用] %s %s
                        
                        替换前：
                        ```
                        %s
                        ```
                        
                        替换后：
                        ```
                        %s
                        ```
                        """, getToolDisplayName(toolName), relativeFilePath, oldContent, newContent);
            }
            case "readFile", "deleteFile" -> String.format("[工具调用] %s %s",
                    getToolDisplayName(toolName), arguments.getStr("relativeFilePath"));
            case "listFiles" -> String.format("[工具调用] %s", getToolDisplayName(toolName));
            default -> StrUtil.blankToDefault(toolResult, String.format("[工具调用] %s", toolName));
        };
    }

    private JSONObject parseToolArguments(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return JSONUtil.createObj();
        }
        try {
            return JSONUtil.parseObj(arguments);
        } catch (Exception e) {
            log.warn("工具参数 JSON 解析失败: {}", arguments);
            return JSONUtil.createObj();
        }
    }

    private String getToolDisplayName(String toolName) {
        return switch (StrUtil.blankToDefault(toolName, "")) {
            case "writeFile" -> "写入文件";
            case "readFile" -> "读取文件";
            case "modifyFile" -> "修改文件";
            case "deleteFile" -> "删除文件";
            case "listFiles" -> "读取目录";
            default -> StrUtil.blankToDefault(toolName, "未知工具");
        };
    }
}

