import json
from typing import Any, Dict, Optional

from app.config import settings


class LlmClient:
    def __init__(self) -> None:
        self.provider = (settings.ai_provider or "mock").strip().lower()

    def enabled(self) -> bool:
        return self.provider == "openai" and bool(settings.openai_api_key.strip())

    def complete_json(self, system_prompt: str, user_payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        if not self.enabled():
            return None

        try:
            from openai import OpenAI
        except ImportError:
            return None

        client = OpenAI(api_key=settings.openai_api_key)
        completion = client.chat.completions.create(
            model=settings.openai_model,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": json.dumps(user_payload, ensure_ascii=True)},
            ],
        )
        content = completion.choices[0].message.content or "{}"
        return json.loads(content)
