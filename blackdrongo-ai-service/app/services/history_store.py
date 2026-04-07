import json
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict

from app.config import settings


class HistoryStore:
    def __init__(self) -> None:
        self._root = Path(settings.history_dir).resolve()
        self._root.mkdir(parents=True, exist_ok=True)

    def append(self, stream_name: str, payload: Dict[str, Any]) -> str:
        history_id = str(uuid.uuid4())
        record = {
            "id": history_id,
            "recorded_at": datetime.now(timezone.utc).isoformat(),
            **payload,
        }
        target = self._root / f"{stream_name}.jsonl"
        with target.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, ensure_ascii=True) + "\n")
        return history_id
