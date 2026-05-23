# app/api/endpoints.py
from fastapi import APIRouter
from pydantic import BaseModel
from app.service.chat_service import simple_chat

router = APIRouter()

# 定义接收 Java 数据的 DTO
class SimpleChatRequest(BaseModel):
    message: str

# 定义 POST 接口
@router.post("/chat")
async def chat_endpoint(req: SimpleChatRequest):
    # 调用 Service 层
    answer = await simple_chat(req.message)
    
    # 包装成 JSON 返回给 Java
    return {
        "code": 200,
        "msg": "success",
        "data": answer
    }