# app/services/chat_service.py
from langchain_openai import ChatOpenAI
from app.config.settings import settings

# 1. 实例化大模型 (直接读取我们在 config.py 中写好的配置)
# 注意：确保你的 .env.local 文件中 DEFAULT_MODEL_API_KEY 已经填入了真实的秘钥
llm = ChatOpenAI(
    openai_api_key=settings.DEFAULT_MODEL_API_KEY,
    openai_api_base=settings.DEFAULT_MODEL_BASE_URL,
    model_name=settings.DEFAULT_MODEL_NAME,
    max_tokens=settings.DEFAULT_MODEL_MAX_TOKENS,
    timeout=settings.DEFAULT_MODEL_TIMEOUT
)

async def simple_chat(user_message: str) -> str:
    """
    最基础的对话方法：接收字符串，返回字符串
    """
    print(f"收到 Java 传来的消息: {user_message}")
    
    # ainvoke 是 LangChain 的异步调用方法
    response = await llm.ainvoke(user_message)
    
    print(f"大模型思考完毕，准备返回...")
    return response.content