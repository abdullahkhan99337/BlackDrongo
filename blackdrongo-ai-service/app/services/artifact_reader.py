import json
import re
import xml.etree.ElementTree as et
from pathlib import Path
from typing import Iterable, List, Optional

from app.config import settings
from app.models import ArtifactPaths, HealFailureRequest


class ArtifactReader:
    def enrich(self, request: HealFailureRequest) -> tuple[HealFailureRequest, List[str]]:
        notes: List[str] = []
        if request.artifact_paths is None:
            request.artifact_paths = ArtifactPaths()

        text_blobs = self._collect_text_blobs(request)
        if not text_blobs:
            return request, notes

        if not request.error_message:
            extracted_error = self._extract_error_message(text_blobs)
            if extracted_error:
                request.error_message = extracted_error
                notes.append("Loaded error message from failure artifacts.")

        if not request.dom_excerpt:
            dom_excerpt = self._extract_dom_excerpt(text_blobs)
            if dom_excerpt:
                request.dom_excerpt = dom_excerpt
                notes.append("Loaded DOM excerpt from failure artifacts.")

        if not request.current_locator:
            locator = self._extract_locator(text_blobs)
            if locator:
                request.current_locator = locator
                notes.append("Loaded locator candidate from failure artifacts.")

        if request.screenshot_path:
            notes.append(f"Screenshot reference: {request.screenshot_path}")

        return request, notes

    def _collect_text_blobs(self, request: HealFailureRequest) -> List[str]:
        blobs: List[str] = []
        for path in self._candidate_paths(request):
            if not path.exists() or not path.is_file():
                continue
            text = self._read_file(path)
            if text:
                blobs.append(text)
        return blobs

    def _candidate_paths(self, request: HealFailureRequest) -> Iterable[Path]:
        direct = [
            getattr(request.artifact_paths, "testng_report_path", None),
            getattr(request.artifact_paths, "cucumber_report_path", None),
            getattr(request.artifact_paths, "console_log_path", None),
            getattr(request.artifact_paths, "junit_report_path", None),
        ]
        for raw in direct:
            path = self._resolve_path(raw)
            if path is not None:
                yield path

        failure_dir = self._resolve_path(getattr(request.artifact_paths, "failure_artifact_dir", None))
        if failure_dir and failure_dir.exists() and failure_dir.is_dir():
            for child in failure_dir.rglob("*"):
                if child.is_file() and child.suffix.lower() in {".txt", ".log", ".json", ".xml", ".html"}:
                    yield child

        normalized_name = request.scenario_name.lower().replace(" ", "_")
        for root_name in settings.artifact_roots.split(","):
            root = self._resolve_path(root_name.strip())
            if root and root.exists() and root.is_dir():
                for child in root.rglob("*"):
                    if not child.is_file():
                        continue
                    lowered = child.name.lower()
                    if normalized_name in lowered or any(
                        token in lowered for token in ("cucumber", "testng", "surefire", "report", "result", "failure")
                    ):
                        yield child

    def _resolve_path(self, raw: Optional[str]) -> Optional[Path]:
        if raw is None or not raw.strip():
            return None
        path = Path(raw.strip())
        if path.is_absolute():
            return path
        return (Path.cwd() / path).resolve()

    def _read_file(self, path: Path) -> str:
        suffix = path.suffix.lower()
        try:
            if suffix == ".json":
                return self._read_json(path)
            if suffix == ".xml":
                return self._read_xml(path)
            return path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            return ""

    def _read_json(self, path: Path) -> str:
        try:
            payload = json.loads(path.read_text(encoding="utf-8", errors="ignore"))
        except json.JSONDecodeError:
            return path.read_text(encoding="utf-8", errors="ignore")

        lines: List[str] = []
        if isinstance(payload, list):
            for feature in payload:
                if not isinstance(feature, dict):
                    continue
                for element in feature.get("elements", []):
                    for step in element.get("steps", []):
                        result = step.get("result", {})
                        error_message = result.get("error_message")
                        if error_message:
                            lines.append(str(error_message))
        elif isinstance(payload, dict):
            lines.append(json.dumps(payload, ensure_ascii=True))
        return "\n".join(lines)

    def _read_xml(self, path: Path) -> str:
        try:
            tree = et.parse(path)
        except et.ParseError:
            return path.read_text(encoding="utf-8", errors="ignore")

        root = tree.getroot()
        lines: List[str] = []
        for node in root.iter():
            if node.tag.lower().endswith("exception") and node.text:
                lines.append(node.text.strip())
            for attr_name, attr_value in node.attrib.items():
                if attr_name.lower() in {"message", "name", "class"} and attr_value.strip():
                    lines.append(attr_value.strip())
        return "\n".join(lines)

    def _extract_error_message(self, blobs: List[str]) -> Optional[str]:
        patterns = [
            r"([A-Za-z]+Exception:[^\r\n]+)",
            r"(AssertionError:[^\r\n]+)",
            r"(expected[^\r\n]+but[^\r\n]+)",
        ]
        for blob in blobs:
            for pattern in patterns:
                match = re.search(pattern, blob, re.IGNORECASE)
                if match:
                    return match.group(1).strip()
        return None

    def _extract_dom_excerpt(self, blobs: List[str]) -> Optional[str]:
        for blob in blobs:
            match = re.search(r"(<[^>]+(?:id|data-testid|name)=['\"][^>]+>)", blob, re.IGNORECASE)
            if match:
                return match.group(1).strip()
        return None

    def _extract_locator(self, blobs: List[str]) -> Optional[str]:
        patterns = [
            r"(//[^\s'\"]+)",
            r"(By\.[A-Za-z]+\([^)]+\))",
            r"(css selector[:=]\s*[^\r\n]+)",
        ]
        for blob in blobs:
            for pattern in patterns:
                match = re.search(pattern, blob, re.IGNORECASE)
                if match:
                    return match.group(1).strip()
        return None
