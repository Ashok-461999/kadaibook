from __future__ import annotations

import json
from pathlib import Path
from uuid import uuid4

from app.config import Settings
from app.pipeline import ExtractionPipeline, run_pipeline
from app.providers.base import ProviderError, ProviderResult


class ScriptedProvider:
    name = "scripted"

    def __init__(self, *, layout: str = "customer_ledger", confidence: float = 0.95, extractions: list[str] | None = None, fail_classify: bool = False):
        self.layout = layout
        self.confidence = confidence
        self.extractions = list(extractions or [])
        self.fail_classify = fail_classify
        self.calls = 0

    @property
    def model(self) -> str:
        return "scripted-1"

    def complete(self, *, image: bytes, mime_type: str, prompt: str) -> ProviderResult:
        self.calls += 1
        if "classifying" in prompt:
            if self.fail_classify:
                raise ProviderError("classification outage")
            return ProviderResult(
                text=json.dumps({"layout_type": self.layout, "confidence": self.confidence}),
                model="scripted-1",
                input_tokens=10,
                output_tokens=5,
            )
        text = self.extractions.pop(0) if self.extractions else "{}"
        return ProviderResult(text=text, model="scripted-1", input_tokens=100, output_tokens=50)


def make_settings(tmp_path: Path, fixture: dict) -> Settings:
    fixture_path = tmp_path / "fixture.json"
    fixture_path.write_text(json.dumps(fixture), encoding="utf-8")
    return Settings(
        checkpoint_path=tmp_path / "checkpoints.sqlite",
        extraction_schema_path=Path("../contracts/extraction-schema-v1.0.json").resolve(),
        ai_service_token="",
        vision_provider="mock",
        mock_fixture_path=fixture_path,
        max_extraction_attempts=3,
    )


def run(tmp_path: Path, settings: Settings, provider, run_id: str | None = None):
    return run_pipeline(
        settings=settings,
        provider=provider,
        page_id=str(uuid4()),
        run_id=run_id or str(uuid4()),
        image_bytes=PNG,
        mime_type="image/png",
        suffix=".png",
    )


PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d49444154789c6360000000020001e221bc330000000049454e44ae426082"
)


def test_retries_invalid_json_then_succeeds(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    provider = ScriptedProvider(extractions=["{not json", json.dumps(mock_fixture)])
    result = run(tmp_path, settings, provider)

    assert result.ok is True
    assert result.status == "ok"
    assert result.attempts == 2
    assert result.extraction["page_id"]
    assert result.cost_paise == 0
    assert result.input_tokens > 0
    assert (tmp_path / "checkpoints.sqlite").exists()


def test_bounded_retries_then_failure(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    provider = ScriptedProvider(extractions=["{}", "{}", "{}", "{}"])
    result = run(tmp_path, settings, provider)

    assert result.ok is False
    assert result.status == "extraction_failed"
    assert result.attempts == 3
    assert result.error


def test_unknown_layout_is_unsupported(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    provider = ScriptedProvider(layout="unknown", extractions=[json.dumps(mock_fixture)])
    result = run(tmp_path, settings, provider)

    assert result.status == "unsupported_layout"
    assert result.extraction is None


def test_low_confidence_layout_is_unsupported(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    provider = ScriptedProvider(layout="customer_ledger", confidence=0.2)
    result = run(tmp_path, settings, provider)
    assert result.status == "unsupported_layout"


def test_classification_failure_is_reported(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    provider = ScriptedProvider(fail_classify=True)
    result = run(tmp_path, settings, provider)
    assert result.status == "extraction_failed"
    assert "classification" in (result.error or "")
    assert result.attempts == 0


def test_same_run_id_resumes_without_repeating_provider_calls(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    provider = ScriptedProvider(extractions=[json.dumps(mock_fixture)])
    pipeline = ExtractionPipeline(settings, provider)
    run_id = str(uuid4())

    first = pipeline.run_with_checkpointer(
        page_id=str(uuid4()),
        run_id=run_id,
        image_bytes=PNG,
        mime_type="image/png",
        suffix=".png",
    )
    calls_after_first = provider.calls
    second = pipeline.run_with_checkpointer(
        page_id=str(uuid4()),
        run_id=run_id,
        image_bytes=PNG,
        mime_type="image/png",
        suffix=".png",
    )

    assert first.ok and second.ok
    assert provider.calls == calls_after_first
    assert second.extraction["page_id"] == first.extraction["page_id"]


def test_page_id_and_layout_are_forced_to_classifier_values(tmp_path, mock_fixture):
    settings = make_settings(tmp_path, mock_fixture)
    payload = dict(mock_fixture)
    payload["page_id"] = "99999999-9999-4999-8999-999999999999"
    provider = ScriptedProvider(extractions=[json.dumps(payload)])
    result = run(tmp_path, settings, provider)
    assert result.ok
    assert result.extraction["page_id"] != "99999999-9999-4999-8999-999999999999"
    assert result.extraction["layout_type"] == "customer_ledger"
