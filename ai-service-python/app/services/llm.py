from langchain_openai import ChatOpenAI

from app.config.settings import settings


def create_default_model(streaming: bool = True) -> ChatOpenAI:
    return ChatOpenAI(
        api_key=settings.DEFAULT_MODEL_API_KEY,
        base_url=settings.DEFAULT_MODEL_BASE_URL,
        model=settings.DEFAULT_MODEL_NAME,
        max_tokens=settings.DEFAULT_MODEL_MAX_TOKENS,
        timeout=settings.DEFAULT_MODEL_TIMEOUT,
        streaming=streaming,
    )


def create_routing_model() -> ChatOpenAI:
    return ChatOpenAI(
        api_key=settings.ROUTING_MODEL_API_KEY,
        base_url=settings.ROUTING_MODEL_BASE_URL,
        model=settings.ROUTING_MODEL_NAME,
        max_tokens=settings.ROUTING_MODEL_MAX_TOKENS,
        timeout=settings.ROUTING_MODEL_TIMEOUT,
        temperature=0,
    )


def create_reasoning_model(streaming: bool = True) -> ChatOpenAI:
    return ChatOpenAI(
        api_key=settings.REASONING_MODEL_API_KEY,
        base_url=settings.REASONING_MODEL_BASE_URL,
        model=settings.REASONING_MODEL_NAME,
        max_tokens=settings.REASONING_MODEL_MAX_TOKENS,
        timeout=settings.REASONING_MODEL_TIMEOUT,
        streaming=streaming,
    )
