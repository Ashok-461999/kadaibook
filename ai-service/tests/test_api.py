from __future__ import annotations

from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.providers.base import ProviderResult
from app.providers.mock import MockVisionProvider


def make_settings(tmp_path: Path) -> Settings:
    return Settings(
        checkpoint_path=tmp_path / "checkpoints.sqlite",
        extraction_schema_path=Path("../contracts/extraction-schema-v1.0.json").resolve(),
        ai_service_token="test-token",
        vision_provider="mock",
        max_image_bytes=1024 * 1024,
    )


def client(tmp_path: Path, provider=None, settings: Settings | None = None) -> TestClient:
    settings = settings or make_settings(tmp_path)
    app = create_app(settings=settings, provider=provider or MockVisionProvider())
    return TestClient(app)


def test_healthz(tmp_path):
    response = client(tmp_path).get("/healthz")
    assert response.status_code == 200
    assert response.json()["provider"] == "mock"
    assert response.json()["schema_version"] == "1.0"


def test_extract_requires_internal_token(tmp_path, png_bytes):
    response = client(tmp_path).post(
        "/extract",
        data={"page_id": str(uuid4()), "run_id": str(uuid4())},
        files={"file": ("page.png", png_bytes, "image/png")},
    )
    assert response.status_code == 401


def test_extract_returns_canonical_payload(tmp_path, png_bytes):
    page_id = str(uuid4())
    response = client(tmp_path).post(
        "/extract",
        data={"page_id": page_id, "run_id": str(uuid4())},
        files={"file": ("page.png", png_bytes, "image/png")},
        headers={"X-Internal-Token": "test-token"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["status"] == "ok"
    assert body["extraction"]["page_id"] == page_id
    assert body["extraction"]["schema_version"] == "1.0"
    assert body["attempts"] == 1
    assert body["prompt_version"] == "extract_v1"
    assert body["extraction"]["review_required"] is True


def test_rejects_non_image_file(tmp_path):
    response = client(tmp_path).post(
        "/extract",
        data={"page_id": str(uuid4()), "run_id": str(uuid4())},
        files={"file": ("notes.txt", b"hello", "text/plain")},
        headers={"X-Internal-Token": "test-token"},
    )
    assert response.status_code == 415


def test_rejects_oversized_image(tmp_path, png_bytes):
    settings = make_settings(tmp_path)
    settings.max_image_bytes = 10
    response = client(tmp_path, settings=settings).post(
        "/extract",
        data={"page_id": str(uuid4()), "run_id": str(uuid4())},
        files={"file": ("page.png", png_bytes, "image/png")},
        headers={"X-Internal-Token": "test-token"},
    )
    assert response.status_code == 413


def test_extraction_failure_is_a_200_envelope(tmp_path, png_bytes):
    class BrokenProvider:
        name = "broken"
        model = "broken-1"

        def complete(self, *, image: bytes, mime_type: str, prompt: str) -> ProviderResult:
            if "classifying" in prompt:
                return ProviderResult(
                    text='{"layout_type":"customer_ledger","confidence":0.99}',
                    model="broken-1",
                )
            return ProviderResult(text="not json", model="broken-1")

    response = client(tmp_path, provider=BrokenProvider()).post(
        "/extract",
        data={"page_id": str(uuid4()), "run_id": str(uuid4())},
        files={"file": ("page.png", png_bytes, "image/png")},
        headers={"X-Internal-Token": "test-token"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "extraction_failed"
    assert body["extraction"] is None
    assert body["attempts"] == 3
