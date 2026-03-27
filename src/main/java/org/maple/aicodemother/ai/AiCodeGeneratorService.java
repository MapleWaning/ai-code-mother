package org.maple.aicodemother.ai;

import dev.langchain4j.service.SystemMessage;
import org.maple.aicodemother.ai.model.HtmlCodeResult;
import org.maple.aicodemother.ai.model.MultiFileCodeResult;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    // 生成html
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    //生成多文件代码
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);
}
