from langchain_core.messages import HumanMessage, SystemMessage

from app.models.schemas import RouteDecision, RouteRequest, RouteResponse
from app.services.llm import create_routing_model
from app.services.prompts import ROUTING_SYSTEM_PROMPT


async def route_code_gen_type(request: RouteRequest) -> RouteResponse:
    model = create_routing_model().with_structured_output(RouteDecision, method="json_mode")
    decision = await model.ainvoke(
        [
            SystemMessage(content=ROUTING_SYSTEM_PROMPT),
            HumanMessage(content=request.initPrompt),
        ]
    )
    return RouteResponse(
        codeGenType=decision.codeGenType.value,
        enumName=decision.codeGenType.name,
        reason=decision.reason,
    )
