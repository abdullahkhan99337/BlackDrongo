from pathlib import Path

from app.config import settings


def load_prompt(name: str) -> str:
    prompt_path = Path(settings.prompts_dir).resolve() / name
    if not prompt_path.exists():
        return ""
    return prompt_path.read_text(encoding="utf-8").strip()
