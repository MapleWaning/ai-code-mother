from enum import Enum
from typing import Any, Optional

from pydantic import BaseModel, Field, field_validator


class CodeGenType(str, Enum):
    HTML = "html"
    MULTI_FILE = "multi_file"
    VUE_PROJECT = "vue_project"

    @classmethod
    def normalize(cls, value: Any) -> Any:
        if isinstance(value, cls):
            return value
        if isinstance(value, str):
            value = value.strip()
            for code_gen_type in cls:
                if value == code_gen_type.value or value == code_gen_type.name:
                    return code_gen_type
        return value


class RouteRequest(BaseModel):
    initPrompt: str = Field(..., min_length=1)


class RouteDecision(BaseModel):
    codeGenType: CodeGenType = Field(..., description="Recommended code generation type")
    reason: str = Field(..., description="Short reason for the selected type")

    @field_validator("codeGenType", mode="before")
    @classmethod
    def normalize_code_gen_type(cls, value: Any) -> Any:
        return CodeGenType.normalize(value)


class RouteResponse(BaseModel):
    codeGenType: str
    enumName: str
    reason: str


class GenerateRequest(BaseModel):
    userMessage: str = Field(..., min_length=1)
    appId: int = Field(..., gt=0)
    codeGenType: CodeGenType

    @field_validator("codeGenType", mode="before")
    @classmethod
    def normalize_code_gen_type(cls, value: Any) -> Any:
        return CodeGenType.normalize(value)


class StreamMessage(BaseModel):
    type: str


class AiResponseMessage(StreamMessage):
    type: str = "ai_response"
    data: str


class ToolRequestMessage(StreamMessage):
    type: str = "tool_request"
    id: Optional[str] = None
    name: str
    arguments: str


class ToolExecutedMessage(StreamMessage):
    type: str = "tool_executed"
    id: Optional[str] = None
    name: str
    arguments: str
    result: str
