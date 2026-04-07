from typing import List, Optional

from pydantic import BaseModel, Field


class ArtifactPaths(BaseModel):
    failure_artifact_dir: Optional[str] = None
    testng_report_path: Optional[str] = None
    cucumber_report_path: Optional[str] = None
    console_log_path: Optional[str] = None
    junit_report_path: Optional[str] = None


class GenerateTestsRequest(BaseModel):
    project_name: str = Field(..., min_length=1)
    base_url: Optional[str] = None
    feature_name: str = Field(..., min_length=1)
    scenario_name: str = Field(..., min_length=1)
    plain_text: str = Field(..., min_length=1)
    package_name: str = "steps.generated"
    page_class_name: Optional[str] = None


class GeneratedArtifact(BaseModel):
    file_name: str
    content: str


class GenerateTestsResponse(BaseModel):
    feature: GeneratedArtifact
    step_definitions: GeneratedArtifact
    page_object: GeneratedArtifact
    assumptions: List[str]
    provider: str = "mock"
    history_id: Optional[str] = None


class HealFailureRequest(BaseModel):
    scenario_name: str = Field(..., min_length=1)
    failed_step: str = Field(..., min_length=1)
    error_message: str = ""
    dom_excerpt: Optional[str] = None
    current_locator: Optional[str] = None
    screenshot_path: Optional[str] = None
    page_url: Optional[str] = None
    artifact_paths: Optional[ArtifactPaths] = None


class HealingSuggestion(BaseModel):
    category: str
    confidence: float
    summary: str
    proposed_change: str
    rationale: str


class HealFailureResponse(BaseModel):
    suggestions: List[HealingSuggestion]
    recommended_action: str
    safe_to_auto_apply: bool
    artifact_summary: List[str] = Field(default_factory=list)
    provider: str = "mock"
    history_id: Optional[str] = None


class HealthResponse(BaseModel):
    status: str
    service: str
    provider: str
