package org.maple.aicodemother.utils;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * 项目文件系统通用工具类 (专供 AI Agent 使用)
 */
@Slf4j
public class ProjectFileSystemUtil {

    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store",
            ".env", "target", ".mvn", ".idea", ".vscode", "coverage"
    );

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock"
    );

    /**
     * 高效生成项目的树状目录结构字符串
     */
    public static String generateProjectTree(Path rootPath) {
        if (!Files.exists(rootPath)) {
            return "目录不存在或为空";
        }

        StringBuilder treeBuilder = new StringBuilder();
        treeBuilder.append("项目结构:\n");

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                // 1. 访问目录前触发
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();

                    // 【核心性能优化】遇到黑名单目录，直接跳过整个子树，绝不深入遍历！
                    if (IGNORED_NAMES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // 根目录不打印，只打印子目录
                    if (!dir.equals(rootPath)) {
                        appendNode(treeBuilder, rootPath, dir, true);
                    }
                    return FileVisitResult.CONTINUE; // 继续遍历子文件
                }

                // 2. 访问文件时触发
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    boolean isIgnoredExt = IGNORED_EXTENSIONS.stream().anyMatch(fileName::endsWith);

                    if (!IGNORED_NAMES.contains(fileName) && !isIgnoredExt) {
                        appendNode(treeBuilder, rootPath, file, false);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("遍历目录树失败", e);
            return "读取目录发生异常";
        }

        return treeBuilder.toString();
    }

    /**
     * 辅助格式化输出
     */
    private static void appendNode(StringBuilder sb, Path root, Path current, boolean isDir) {
        // 计算相对深度来决定缩进
        int depth = root.relativize(current).getNameCount();
        String indent = "  ".repeat(Math.max(0, depth - 1));
        sb.append(indent)
                .append(isDir ? "📁 " : "📄 ")
                .append(current.getFileName().toString())
                .append("\n");
    }
}
