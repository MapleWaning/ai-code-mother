package org.maple.aicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class HtmlCodeResult {

    @Description("HTML代码")
    private String htmlCode;
    @Description("代码描述")
    private String description;
}

