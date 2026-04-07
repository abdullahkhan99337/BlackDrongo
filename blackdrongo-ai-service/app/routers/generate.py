from fastapi import APIRouter

from app.models import GenerateTestsRequest, GenerateTestsResponse
from app.services.generator import TestGenerationService

router = APIRouter(tags=["generate"])
service = TestGenerationService()


@router.post("/generate/tests", response_model=GenerateTestsResponse)
def generate_tests(request: GenerateTestsRequest) -> GenerateTestsResponse:
    return service.generate(request)
