import json
from pathlib import Path
from typing import Callable

from langchain_core.tools import StructuredTool

from app.config.settings import settings


IMPORTANT_FILES = {
    "package.json",
    "package-lock.json",
    "yarn.lock",
    "pnpm-lock.yaml",
    "vite.config.js",
    "vite.config.ts",
    "vue.config.js",
    "tsconfig.json",
    "tsconfig.app.json",
    "tsconfig.node.json",
    "index.html",
    "main.js",
    "main.ts",
    "App.vue",
    ".gitignore",
    "README.md",
}


def _project_root(app_id: int) -> Path:
    return Path(settings.CODE_OUTPUT_ROOT_DIR).resolve() / f"vue_project_{app_id}"


def _resolve_project_path(app_id: int, relative_file_path: str) -> Path:
    root = _project_root(app_id).resolve()
    path = (root / relative_file_path).resolve()
    if not path.is_relative_to(root):
        raise ValueError("文件路径不能跳出项目目录")
    return path


def _generate_project_tree(root: Path) -> str:
    if not root.exists():
        return "(空项目)"
    lines: list[str] = []
    for path in sorted(root.rglob("*")):
        if path.is_dir() and path.name in {"node_modules", "dist"}:
            continue
        depth = len(path.relative_to(root).parts) - 1
        prefix = "  " * depth + "- "
        lines.append(prefix + path.name)
    return "\n".join(lines) if lines else "(空项目)"


def _safe_call(action: Callable[[], str]) -> str:
    try:
        return action()
    except Exception as exc:
        return f"工具执行失败: {exc}"


def build_file_tools(app_id: int) -> list[StructuredTool]:
    def write_file(relativeFilePath: str, content: str) -> str:
        def action() -> str:
            target = _resolve_project_path(app_id, relativeFilePath)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
            tree = _generate_project_tree(_project_root(app_id))
            return (
                f"文件 [{relativeFilePath}] 写入成功！\n"
                f"当前已存在的文件：{tree}\n"
                "请检查是否已经完成了基础项目结构。不要过度设计！如果核心文件已生成完毕，请立刻结束任务！"
            )

        return _safe_call(action)

    def read_file(relativeFilePath: str) -> str:
        def action() -> str:
            target = _resolve_project_path(app_id, relativeFilePath)
            if not target.is_file():
                return f"错误：文件不存在或不是文件 - {relativeFilePath}"
            return target.read_text(encoding="utf-8")

        return _safe_call(action)

    def modify_file(relativeFilePath: str, oldContent: str, newContent: str) -> str:
        def action() -> str:
            target = _resolve_project_path(app_id, relativeFilePath)
            if not target.is_file():
                return f"错误：文件不存在或不是文件 - {relativeFilePath}"
            original = target.read_text(encoding="utf-8")
            if oldContent not in original:
                return f"警告：文件中未找到要替换的内容，文件未修改 - {relativeFilePath}"
            target.write_text(original.replace(oldContent, newContent), encoding="utf-8")
            return f"文件修改成功: {relativeFilePath}"

        return _safe_call(action)

    def delete_file(relativeFilePath: str) -> str:
        def action() -> str:
            target = _resolve_project_path(app_id, relativeFilePath)
            if not target.exists():
                return f"警告：文件不存在，无需删除 - {relativeFilePath}"
            if not target.is_file():
                return f"错误：指定路径不是文件，无法删除 - {relativeFilePath}"
            if target.name in IMPORTANT_FILES:
                return f"错误：不允许删除重要文件 - {target.name}"
            target.unlink()
            return f"文件删除成功: {relativeFilePath}"

        return _safe_call(action)

    def list_files() -> str:
        return _generate_project_tree(_project_root(app_id))

    return [
        StructuredTool.from_function(
            func=write_file,
            name="writeFile",
            description="写入文件到指定相对路径，参数 relativeFilePath 和 content。",
        ),
        StructuredTool.from_function(
            func=read_file,
            name="readFile",
            description="读取指定相对路径的文件内容，参数 relativeFilePath。",
        ),
        StructuredTool.from_function(
            func=modify_file,
            name="modifyFile",
            description="用新内容替换文件中的旧内容，参数 relativeFilePath、oldContent、newContent。",
        ),
        StructuredTool.from_function(
            func=delete_file,
            name="deleteFile",
            description="删除指定相对路径的文件，参数 relativeFilePath。",
        ),
        StructuredTool.from_function(
            func=list_files,
            name="listFiles",
            description="列出当前 Vue 项目的文件树。",
        ),
    ]


def tool_arguments_to_json(args: object) -> str:
    if isinstance(args, str):
        return args
    return json.dumps(args or {}, ensure_ascii=False)
