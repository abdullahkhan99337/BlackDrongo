from fastapi import FastAPI

from app.config import settings
from app.models import HealthResponse
from app.routers.generate import router as generate_router
from app.routers.heal import router as heal_router

app = FastAPI(title="BlackDrongo AI Service", version="0.1.0")
app.include_router(generate_router, prefix="/api/v1")
app.include_router(heal_router, prefix="/api/v1")


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=settings.ai_service_name,
        provider=settings.ai_provider,
    )
