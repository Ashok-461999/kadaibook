from __future__ import annotations

import json
import re
from typing import Any


class JsonParseError(ValueError):
    pass


_FENCE = re.compile(r"^\s*```(?:json)?\s*|\s*```\s*$", re.IGNORECASE)


def parse_json_object(text: str) -> dict[str, Any]:
    """Parse the first JSON object in model text, tolerating fences and prose."""
    if not text or not text.strip():
        raise JsonParseError("model returned an empty response")

    cleaned = _FENCE.sub("", text).strip()
    decoder = json.JSONDecoder()
    for index, char in enumerate(cleaned):
        if char != "{":
            continue
        try:
            value, _ = decoder.raw_decode(cleaned[index:])
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            return value
    raise JsonParseError("model response did not contain a JSON object")
