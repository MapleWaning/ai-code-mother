package org.maple.aicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class MultiFileCodeResult {

    @Description("HTML代码")
    private String htmlCode;
    @Description("CSS代码")
    private String cssCode;
    @Description("JavaScript代码")
    private String jsCode;
    @Description("代码描述")
    private String description;
}

