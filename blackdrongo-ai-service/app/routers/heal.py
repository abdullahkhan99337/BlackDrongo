from fastapi import APIRouter

from app.models import HealFailureRequest, HealFailureResponse
from app.services.healer import HealingService

router = APIRouter(tags=["heal"])
service = HealingService()


@router.post("/heal/failure", response_model=HealFailureResponse)
def heal_failure(request: HealFailureRequest) -> HealFailureResponse:
    return service.analyze(request)
