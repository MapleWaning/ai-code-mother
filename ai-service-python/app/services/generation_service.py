from collections.abc import AsyncIterator
from pathlib import Path

from langchain_community.chat_message_histories import RedisChatMessageHistory
from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage, message_chunk_to_message
from langchain_core.messages.ai import AIMessageChunk

from app.config.settings import settings
from app.models.schemas import (
    AiResponseMessage,
    CodeGenType,
    GenerateRequest,
    ToolExecutedMessage,
    ToolRequestMessage,
)
from app.services.llm import create_default_model, create_reasoning_model
from app.services.prompts import HTML_SYSTEM_PROMPT, MULTI_FILE_SYSTEM_PROMPT, VUE_SYSTEM_PROMPT
from app.tools.file_tools import build_file_tools, tool_arguments_to_json

STREAMED_TOOL_RESULT = "__streamed_to_frontend__"


def _history_messages(app_id: int):
    history = RedisChatMessageHistory(
        session_id=str(app_id),
        url=settings.REDIS_URL,
        key_prefix=settings.REDIS_KEY_PREFIX,
        ttl=settings.REDIS_TTL,
    )
    return history.messages


def _format_sse(data: str) -> str:
    if data == "":
        return "data: \n\n"
    lines = data.splitlines()
    if data.endswith("\n"):
        lines.append("")
    return "".join(f"data: {line}\n" for line in lines) + "\n"


def _tool_display_name(tool_name: str | None) -> str:
    return {
        "writeFile": "写入文件",
        "readFile": "读取文件",
        "modifyFile": "修改文件",
        "deleteFile": "删除文件",
        "listFiles": "读取目录",
    }.get(tool_name or "", tool_name or "未知工具")


def _file_suffix(relative_file_path: str | None) -> str:
    if not relative_file_path:
        return ""
    suffix = Path(relative_file_path).suffix
    return suffix[1:] if suffix.startswith(".") else suffix


def _split_text(text: str, chunk_size: int = 80) -> AsyncIterator[str]:
    async def generator() -> AsyncIterator[str]:
        for start in range(0, len(text), chunk_size):
            yield text[start : start + chunk_size]

    return generator()


async def _stream_tool_frontend_output(tool_name: str | None, tool_args: dict) -> AsyncIterator[str]:
    display_name = _tool_display_name(tool_name)
    if tool_name == "writeFile":
        relative_file_path = tool_args.get("relativeFilePath") or ""
        content = tool_args.get("content") or ""
        header = f"\n\n[工具调用] {display_name} {relative_file_path}\n```{_file_suffix(relative_file_path)}\n"
        yield AiResponseMessage(data=header).model_dump_json()
        async for chunk in _split_text(content):
            yield AiResponseMessage(data=chunk).model_dump_json()
        yield AiResponseMessage(data="\n```\n").model_dump_json()
        return

    if tool_name == "modifyFile":
        relative_file_path = tool_args.get("relativeFilePath") or ""
        old_content = tool_args.get("oldContent") or ""
        new_content = tool_args.get("newContent") or ""
        header = f"\n\n[工具调用] {display_name} {relative_file_path}\n\n替换前：\n```\n"
        yield AiResponseMessage(data=header).model_dump_json()
        async for chunk in _split_text(old_content):
            yield AiResponseMessage(data=chunk).model_dump_json()
        middle = "\n```\n\n替换后：\n```\n"
        yield AiResponseMessage(data=middle).model_dump_json()
        async for chunk in _split_text(new_content):
            yield AiResponseMessage(data=chunk).model_dump_json()
        yield AiResponseMessage(data="\n```\n").model_dump_json()


async def stream_generate(request: GenerateRequest) -> AsyncIterator[str]:
    if request.codeGenType == CodeGenType.VUE_PROJECT:
        async for chunk in _stream_vue_project(request):
            yield _format_sse(chunk)
        return

    prompt = HTML_SYSTEM_PROMPT if request.codeGenType == CodeGenType.HTML else MULTI_FILE_SYSTEM_PROMPT
    model = create_default_model(streaming=True)
    messages = [
        SystemMessage(content=prompt),
        *_history_messages(request.appId),
        HumanMessage(content=request.userMessage),
    ]
    async for chunk in model.astream(messages):
        if chunk.content:
            yield _format_sse(AiResponseMessage(data=str(chunk.content)).model_dump_json())


async def _stream_vue_project(request: GenerateRequest) -> AsyncIterator[str]:
    tools = build_file_tools(request.appId)
    tool_map = {tool.name: tool for tool in tools}
    model = create_reasoning_model(streaming=True).bind_tools(tools, parallel_tool_calls=False)
    messages = [
        SystemMessage(content=VUE_SYSTEM_PROMPT),
        *_history_messages(request.appId),
        HumanMessage(content=request.userMessage),
    ]

    for _ in range(30):
        aggregated: AIMessageChunk | None = None
        async for chunk in model.astream(messages):
            aggregated = chunk if aggregated is None else aggregated + chunk
            if chunk.content:
                text = str(chunk.content)
                yield AiResponseMessage(data=text).model_dump_json()

        if aggregated is None:
            return
        messages.append(message_chunk_to_message(aggregated))
        if not aggregated.tool_calls:
            return

        for tool_call in aggregated.tool_calls:
            tool_name = tool_call.get("name")
            tool_id = tool_call.get("id")
            tool_args = tool_call.get("args") or {}
            arguments = tool_arguments_to_json(tool_args)
            yield ToolRequestMessage(id=tool_id, name=tool_name, arguments=arguments).model_dump_json()

            tool = tool_map.get(tool_name)
            if tool is None:
                result = f"Error: there is no tool called {tool_name}"
            else:
                result = await tool.ainvoke(tool_args)
            streamed_to_frontend = tool_name in {"writeFile", "modifyFile"}
            if streamed_to_frontend:
                async for frontend_chunk in _stream_tool_frontend_output(tool_name, tool_args):
                    yield frontend_chunk
            yield ToolExecutedMessage(
                id=tool_id,
                name=tool_name,
                arguments=arguments,
                result=STREAMED_TOOL_RESULT if streamed_to_frontend else str(result),
            ).model_dump_json()
            messages.append(ToolMessage(content=str(result), tool_call_id=tool_id or tool_name))

    yield AiResponseMessage(data="工具调用次数超过限制，已停止生成。").model_dump_json()
