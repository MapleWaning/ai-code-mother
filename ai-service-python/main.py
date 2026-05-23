# main.py
from fastapi import FastAPI
import uvicorn
from app.api.endpoints import router as api_router

app = FastAPI(title="AI Code Generator - Demo")

# 挂载路由，并加上 /api 前缀
app.include_router(api_router, prefix="/api")

if __name__ == "__main__":
    # 启动服务
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)