from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.models.schemas import GenerateRequest, RouteRequest, RouteResponse
from app.services.generation_service import stream_generate
from app.services.routing_service import route_code_gen_type

router = APIRouter()

@router.post("/route", response_model=RouteResponse)
async def route_endpoint(req: RouteRequest) -> RouteResponse:
    return await route_code_gen_type(req)


@router.post("/generate")
async def generate_endpoint(req: GenerateRequest) -> StreamingResponse:
    return StreamingResponse(stream_generate(req), media_type="text/event-stream")