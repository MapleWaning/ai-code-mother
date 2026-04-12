package org.maple.aicodemother.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.maple.aicodemother.constant.AppConstant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
public class FileWriteTool {

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeFilePath);
            // 相对路径处理，创建基于 appId 的项目目录
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
            if (!path.isAbsolute()) {
                path = projectRoot.resolve(relativeFilePath);
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            String existingFiles = scanProjectFiles(projectRoot);
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return String.format(
                    "文件 [%s] 写入成功！\n" +
                            "当前已存在的文件：%s\n" +
                            "请检查是否已经完成了基础项目结构。不要过度设计！如果核心文件已生成完毕，绝不要再画蛇添足创建新文件，请立刻结束任务！",
                    relativeFilePath,
                    existingFiles
            );
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
    /**
     * 辅助方法：递归扫描目录，返回相对路径列表
     */
    private String scanProjectFiles(Path projectRoot) {
        if (!Files.exists(projectRoot)) {
            return "空";
        }
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            List<String> fileList = walk.filter(Files::isRegularFile)
                    .map(projectRoot::relativize) // 获取相对路径
                    .map(Path::toString)
                    .map(p -> p.replace("\\", "/")) // 统一使用正斜杠
                    .collect(Collectors.toList());

            if (fileList.isEmpty()) {
                return "空";
            }
            return String.join(", ", fileList);
        } catch (IOException e) {
            log.error("扫描目录失败", e);
            return "无法获取文件列表";
        }
    }
}

