package org.maple.aicodemother.core;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.maple.aicodemother.ai.model.enums.CodeGenTypeEnum;
import org.maple.aicodemother.ai.model.message.StreamMessageTypeEnum;
import org.maple.aicodemother.constant.AppConstant;
import org.maple.aicodemother.core.builder.VueProjectBuilder;
import org.maple.aicodemother.exception.BusinessException;
import org.maple.aicodemother.exception.ErrorCode;
import org.maple.aicodemother.parser.CodeParserExecutor;
import org.maple.aicodemother.saver.CodeFileSaverExecutor;
import org.maple.aicodemother.service.ChatHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCodeGeneratorFacade {

    private final VueProjectBuilder vueProjectBuilder;
    private final ChatHistoryService chatHistoryService;
    private final WebClient.Builder webClientBuilder;

    private static final int CHAT_MEMORY_MAX_COUNT = 20;

    @Value("${ai-service.python-base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType,appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

//    /**
//     * 统一入口：根据类型生成并保存代码
//     *
//     * @param userMessage     用户提示词
//     * @param codeGenTypeEnum 生成类型
//     * @return 保存的目录
//     */
//    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
//        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
//        if (codeGenTypeEnum == null) {
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
//        }
//        return switch (codeGenTypeEnum) {
//            case HTML -> {
//                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML,appId);
//            }
//            case MULTI_FILE -> {
//                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE,appId);
//            }
//            default -> {
//                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
//            }
//        };
//    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        chatHistoryService.preloadChatHistoryToRedis(appId, CHAT_MEMORY_MAX_COUNT);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = requestPythonGenerateStream(userMessage, codeGenTypeEnum, appId);
                yield processCodeStream(unwrapPythonTextStream(codeStream), CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = requestPythonGenerateStream(userMessage, codeGenTypeEnum, appId);
                yield processCodeStream(unwrapPythonTextStream(codeStream), CodeGenTypeEnum.MULTI_FILE,appId);
            }
            case VUE_PROJECT -> {
                Flux<String> codeStream = requestPythonGenerateStream(userMessage, codeGenTypeEnum, appId);
                yield processVueProjectStream(codeStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    private Flux<String> requestPythonGenerateStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return webClientBuilder
                .baseUrl(pythonAiBaseUrl)
                .build()
                .post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new PythonGenerateRequest(userMessage, appId, codeGenTypeEnum.getValue()))
                .retrieve()
                .bodyToFlux(String.class);
    }

    /**
     * HTML/MULTI_FILE 模式下 Python 用 JSON 包装文本块，避免 SSE 丢失纯换行 token。
     */
    private Flux<String> unwrapPythonTextStream(Flux<String> codeStream) {
        return codeStream.map(this::unwrapPythonTextChunk);
    }

    private String unwrapPythonTextChunk(String chunk) {
        if (!JSONUtil.isTypeJSON(chunk)) {
            return chunk;
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(chunk);
            if (StreamMessageTypeEnum.AI_RESPONSE.getValue().equals(jsonObject.getStr("type"))) {
                return jsonObject.getStr("data", "");
            }
        } catch (Exception e) {
            log.warn("Python 文本流 JSON 解包失败，按原始文本处理: {}", chunk);
        }
        return chunk;
    }

    /**
     * Python VUE_PROJECT 流已经是原 JsonMessageStreamHandler 可识别的 JSON 消息格式。
     */
    private Flux<String> processVueProjectStream(Flux<String> codeStream, Long appId) {
        return codeStream.doOnComplete(() -> {
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
            vueProjectBuilder.buildProject(projectPath);
        });
    }

    private record PythonGenerateRequest(String userMessage, Long appId, String codeGenType) {
    }



}
