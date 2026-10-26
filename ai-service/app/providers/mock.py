from __future__ import annotations

import json
from pathlib import Path

from app.providers.base import ProviderError, ProviderResult

DEFAULT_FIXTURE = Path(__file__).with_name("mock_fixture.json")


class MockVisionProvider:
    """Deterministic local provider used for development, tests, and demos.

    It ignores the image bytes and returns the fixture. Every response produced
    this way is labelled as mock output by the model name; never present mock
    metrics as pilot metrics.
    """

    name = "mock"

    def __init__(self, fixture_path: Path | None = None, model: str = "mock-v1") -> None:
        self._fixture_path = fixture_path or DEFAULT_FIXTURE
        self._model = model
        try:
            self._fixture = json.loads(self._fixture_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ProviderError(f"mock fixture could not be loaded: {exc}") from exc

    @property
    def model(self) -> str:
        return self._model

    def complete(self, *, image: bytes, mime_type: str, prompt: str) -> ProviderResult:
        if "Return only compact JSON" in prompt and "layout_type" in prompt:
            text = json.dumps(
                {
                    "layout_type": self._fixture.get("layout_type", "customer_ledger"),
                    "confidence": 0.99,
                }
            )
        else:
            text = json.dumps(self._fixture, ensure_ascii=False)
        return ProviderResult(text=text, model=self._model, input_tokens=0, output_tokens=0)
