import re
from typing import List

from app.config import settings
from app.models import HealFailureRequest, HealFailureResponse, HealingSuggestion
from app.services.artifact_reader import ArtifactReader
from app.services.history_store import HistoryStore
from app.services.llm_client import LlmClient
from app.services.prompt_loader import load_prompt


class HealingService:
    def __init__(self) -> None:
        self._artifact_reader = ArtifactReader()
        self._llm = LlmClient()
        self._history = HistoryStore()

    def analyze(self, request: HealFailureRequest) -> HealFailureResponse:
        request, artifact_summary = self._artifact_reader.enrich(request)
        suggestions: List[HealingSuggestion] = []
        message = request.error_message.lower()
        dom = (request.dom_excerpt or "").lower()
        locator = request.current_locator or ""
        provider = settings.ai_provider

        if "nosuchelementexception" in message or "unable to locate element" in message:
            proposed_locator = self._suggest_locator(dom, locator)
            suggestions.append(
                HealingSuggestion(
                    category="locator",
                    confidence=0.88 if proposed_locator != locator else 0.62,
                    summary="The current locator likely no longer matches the DOM.",
                    proposed_change=f"Replace locator with: {proposed_locator}",
                    rationale="The failure indicates element lookup broke and the DOM excerpt suggests a more stable attribute.",
                )
            )

        if "timeoutexception" in message or "element not interactable" in message:
            suggestions.append(
                HealingSuggestion(
                    category="wait",
                    confidence=0.74,
                    summary="The step appears to need an explicit wait before interaction.",
                    proposed_change="Add a wait for visibility and clickability before performing the action.",
                    rationale="The failure pattern matches timing and synchronization issues common in Selenium flows.",
                )
            )

        if "staleelementreferenceexception" in message:
            suggestions.append(
                HealingSuggestion(
                    category="stale-element",
                    confidence=0.79,
                    summary="The element reference became stale after a DOM refresh.",
                    proposed_change="Re-locate the element immediately before interaction and avoid storing WebElement references longer than needed.",
                    rationale="Stale element failures typically occur when the page re-renders between find and action.",
                )
            )

        if "assert" in message or "expected" in message:
            suggestions.append(
                HealingSuggestion(
                    category="assertion",
                    confidence=0.55,
                    summary="The failure may be a product or data issue rather than a healing candidate.",
                    proposed_change="Review the expected value and captured state before changing automation.",
                    rationale="Assertion mismatches should usually be reviewed manually to avoid masking real defects.",
                )
            )

        llm_suggestions = self._llm_suggestions(request)
        if llm_suggestions:
            suggestions = llm_suggestions + suggestions
            provider = "openai"

        if not suggestions:
            suggestions.append(
                HealingSuggestion(
                    category="unknown",
                    confidence=0.31,
                    summary="No deterministic healing rule matched this failure.",
                    proposed_change="Escalate for manual triage or add an LLM-backed analyzer for this failure class.",
                    rationale="The current MVP intentionally keeps auto-healing narrow and conservative.",
                )
            )

        recommended = suggestions[0].proposed_change
        safe_to_auto_apply = suggestions[0].category in {"locator", "wait"} and suggestions[0].confidence >= 0.85
        history_id = self._history.append(
            "healing-history",
            {
                "provider": provider,
                "request": request.model_dump(),
                "artifact_summary": artifact_summary,
                "response": {
                    "suggestions": [suggestion.model_dump() for suggestion in suggestions],
                    "recommended_action": recommended,
                    "safe_to_auto_apply": safe_to_auto_apply,
                },
            },
        )
        return HealFailureResponse(
            suggestions=suggestions,
            recommended_action=recommended,
            safe_to_auto_apply=safe_to_auto_apply,
            artifact_summary=artifact_summary,
            provider=provider,
            history_id=history_id,
        )

    def _llm_suggestions(self, request: HealFailureRequest) -> List[HealingSuggestion]:
        system_prompt = load_prompt("healing_system.txt")
        if not system_prompt:
            return []

        payload = self._llm.complete_json(
            system_prompt,
            {
                "scenario_name": request.scenario_name,
                "failed_step": request.failed_step,
                "error_message": request.error_message,
                "dom_excerpt": request.dom_excerpt,
                "current_locator": request.current_locator,
                "page_url": request.page_url,
                "response_contract": {
                    "suggestions": [
                        {
                            "category": "string",
                            "confidence": 0.0,
                            "summary": "string",
                            "proposed_change": "string",
                            "rationale": "string",
                        }
                    ]
                },
            },
        )
        if not payload:
            return []

        raw_suggestions = payload.get("suggestions", [])
        suggestions: List[HealingSuggestion] = []
        for item in raw_suggestions:
            if not isinstance(item, dict):
                continue
            try:
                suggestions.append(HealingSuggestion(**item))
            except ValueError:
                continue
        return suggestions

    def _suggest_locator(self, dom_excerpt: str, current_locator: str) -> str:
        data_testid = re.search(r"data-testid=['\"]([^'\"]+)['\"]", dom_excerpt, re.IGNORECASE)
        if data_testid:
            return f"//*[@data-testid='{data_testid.group(1)}']"
        element_id = re.search(r"id=['\"]([^'\"]+)['\"]", dom_excerpt, re.IGNORECASE)
        if element_id:
            return f"//*[@id='{element_id.group(1)}']"
        name_attr = re.search(r"name=['\"]([^'\"]+)['\"]", dom_excerpt, re.IGNORECASE)
        if name_attr:
            return f"//*[@name='{name_attr.group(1)}']"
        text_match = re.search(r">\s*([^<>]{1,40})\s*<", dom_excerpt)
        if text_match:
            text_value = text_match.group(1).strip()
            return f"//*[normalize-space()='{text_value}']"
        return current_locator or "//TODO[normalize-space()='replace-me']"
