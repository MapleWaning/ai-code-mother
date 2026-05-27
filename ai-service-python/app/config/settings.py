import os
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import computed_field

BASE_DIR = Path(__file__).resolve().parent.parent.parent

class Settings(BaseSettings):
    # ---------------- 基础 ----------------
    APP_ENV: str = "local"
    
    # ---------------- Redis ----------------
    REDIS_HOST: str
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str
    REDIS_DB: int = 0
    REDIS_TTL: int = 3600
    REDIS_KEY_PREFIX: str = "message_store:"

    # 动态计算字段：组装为 redis://:password@host:port/db 的标准格式
    @computed_field
    def REDIS_URL(self) -> str:
        return f"redis://:{self.REDIS_PASSWORD}@{self.REDIS_HOST}:{self.REDIS_PORT}/{self.REDIS_DB}"

    # ---------------- 默认流式模型 (DeepSeek) ----------------
    DEFAULT_MODEL_BASE_URL: str
    DEFAULT_MODEL_API_KEY: str
    DEFAULT_MODEL_NAME: str
    DEFAULT_MODEL_MAX_TOKENS: int = 8192
    DEFAULT_MODEL_TIMEOUT: int = 600

    # ---------------- 意图路由模型 (DeepSeek) ----------------
    ROUTING_MODEL_BASE_URL: str
    ROUTING_MODEL_API_KEY: str
    ROUTING_MODEL_NAME: str
    ROUTING_MODEL_MAX_TOKENS: int = 100
    ROUTING_MODEL_TIMEOUT: int = 600

    # ---------------- 复杂推理模型 (Qwen) ----------------
    REASONING_MODEL_BASE_URL: str
    REASONING_MODEL_API_KEY: str
    REASONING_MODEL_NAME: str
    REASONING_MODEL_MAX_TOKENS: int = 8192
    REASONING_MODEL_TIMEOUT: int = 600
    REASONING_MODEL_PARALLEL_TOOL_CALLS: bool = False

    # ---------------- 文件生成目录 ----------------
    CODE_OUTPUT_ROOT_DIR: str = str((Path(__file__).resolve().parents[3] / "tmp" / "code_output").resolve())

    # 开启 LangChain 的全局 Debug 和 Request 日志 (对应你 yaml 里的 log-requests: true)
    LANGCHAIN_VERBOSE: bool = True

    model_config = SettingsConfigDict(
        env_file=(str(BASE_DIR / '.env'), str(BASE_DIR / '.env.local')), 
        env_file_encoding='utf-8',
        extra='ignore'
    )

# 实例化全局单例
settings = Settings()