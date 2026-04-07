import re
from dataclasses import dataclass
from typing import List, Optional

from app.config import settings
from app.models import GenerateTestsRequest, GenerateTestsResponse, GeneratedArtifact
from app.services.history_store import HistoryStore
from app.services.llm_client import LlmClient
from app.services.prompt_loader import load_prompt


@dataclass
class ParsedStep:
    keyword: str
    text: str
    method_name: str


class TestGenerationService:
    def __init__(self) -> None:
        self._llm = LlmClient()
        self._history = HistoryStore()

    def generate(self, request: GenerateTestsRequest) -> GenerateTestsResponse:
        steps = self._parse_steps(request.plain_text)
        page_class_name = request.page_class_name or self._page_class_name(request.feature_name)
        steps_class_name = self._steps_class_name(request.feature_name)

        feature_content = self._feature_content(request, steps)
        step_def_content = self._step_definitions(request, steps, steps_class_name, page_class_name)
        page_object_content = self._page_object(steps, page_class_name)
        provider = settings.ai_provider

        llm_payload = self._llm_artifacts(request, steps_class_name, page_class_name)
        if llm_payload is not None:
            feature_content = llm_payload.get("feature_content", feature_content)
            step_def_content = llm_payload.get("step_definitions_content", step_def_content)
            page_object_content = llm_payload.get("page_object_content", page_object_content)
            provider = "openai"

        assumptions = [
            "Generated output is a starter template and requires locator implementation.",
            "Assertions are inferred from the final verification-oriented sentence.",
            "Natural language was converted deterministically unless OpenAI-backed generation is enabled.",
        ]

        history_id = self._history.append(
            "generation-history",
            {
                "provider": provider,
                "request": request.model_dump(),
                "artifacts": {
                    "feature": feature_content,
                    "step_definitions": step_def_content,
                    "page_object": page_object_content,
                },
            },
        )

        return GenerateTestsResponse(
            feature=GeneratedArtifact(
                file_name=f"{self._safe_name(request.feature_name)}.feature",
                content=feature_content,
            ),
            step_definitions=GeneratedArtifact(
                file_name=f"{steps_class_name}.java",
                content=step_def_content,
            ),
            page_object=GeneratedArtifact(
                file_name=f"{page_class_name}.java",
                content=page_object_content,
            ),
            assumptions=assumptions,
            provider=provider,
            history_id=history_id,
        )

    def _llm_artifacts(
        self,
        request: GenerateTestsRequest,
        steps_class_name: str,
        page_class_name: str,
    ) -> Optional[dict]:
        system_prompt = load_prompt("test_generation_system.txt")
        if not system_prompt:
            return None

        return self._llm.complete_json(
            system_prompt,
            {
                "project_name": request.project_name,
                "base_url": request.base_url,
                "feature_name": request.feature_name,
                "scenario_name": request.scenario_name,
                "plain_text": request.plain_text,
                "package_name": request.package_name,
                "steps_class_name": steps_class_name,
                "page_class_name": page_class_name,
                "response_contract": {
                    "feature_content": "string",
                    "step_definitions_content": "string",
                    "page_object_content": "string",
                },
            },
        )

    def _parse_steps(self, plain_text: str) -> List[ParsedStep]:
        parts = [part.strip() for part in re.split(r"[\r\n]+|\.\s+", plain_text) if part.strip()]
        steps: List[ParsedStep] = []
        for index, part in enumerate(parts):
            lowered = part.lower()
            if index == 0:
                keyword = "Given"
            elif lowered.startswith(("verify", "validate", "assert", "check")):
                keyword = "Then"
            else:
                keyword = "When"
            method_name = self._method_name(part)
            steps.append(ParsedStep(keyword=keyword, text=part.rstrip("."), method_name=method_name))
        return steps

    def _feature_content(self, request: GenerateTestsRequest, steps: List[ParsedStep]) -> str:
        lines = [f"Feature: {request.feature_name}", "", f"  Scenario: {request.scenario_name}"]
        for step in steps:
            lines.append(f"    {step.keyword} {step.text}")
        return "\n".join(lines) + "\n"

    def _step_definitions(
        self,
        request: GenerateTestsRequest,
        steps: List[ParsedStep],
        steps_class_name: str,
        page_class_name: str,
    ) -> str:
        imports = [
            "package " + request.package_name + ";",
            "",
            'import io.cucumber.java.en.Given;',
            'import io.cucumber.java.en.When;',
            'import io.cucumber.java.en.Then;',
            f'import pages.{page_class_name};',
            "",
            f"public class {steps_class_name} {{",
            f"    private final {page_class_name} page = new {page_class_name}();",
            "",
        ]
        methods = []
        for step in steps:
            escaped = step.text.replace('"', '\\"')
            methods.extend(
                [
                    f'    @{step.keyword}("{escaped}")',
                    f"    public void {step.method_name}() {{",
                    f"        page.{step.method_name}();",
                    "    }",
                    "",
                ]
            )
        return "\n".join(imports + methods + ["}", ""])

    def _page_object(self, steps: List[ParsedStep], page_class_name: str) -> str:
        body = [
            "package pages;",
            "",
            "import steps.Step;",
            "",
            f"public class {page_class_name} extends Step {{",
            "",
        ]
        for step in steps:
            body.extend(
                [
                    f"    public void {step.method_name}() {{",
                    '        throw new UnsupportedOperationException("Implement locator and action logic");',
                    "    }",
                    "",
                ]
            )
        body.append("}")
        body.append("")
        return "\n".join(body)

    def _safe_name(self, value: str) -> str:
        return re.sub(r"[^a-zA-Z0-9._-]", "_", value.strip())

    def _method_name(self, sentence: str) -> str:
        normalized = re.sub(r"[^a-zA-Z0-9 ]", " ", sentence).lower()
        parts = [part for part in normalized.split() if part]
        if not parts:
            return "stepAction"
        return parts[0] + "".join(part.capitalize() for part in parts[1:])

    def _page_class_name(self, feature_name: str) -> str:
        return self._class_name(feature_name) + "Page"

    def _steps_class_name(self, feature_name: str) -> str:
        return self._class_name(feature_name) + "Steps"

    def _class_name(self, value: str) -> str:
        parts = re.findall(r"[A-Za-z0-9]+", value)
        if not parts:
            return "Generated"
        return "".join(part.capitalize() for part in parts)
