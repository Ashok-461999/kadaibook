from __future__ import annotations

import httpx
import pytest

from app.providers.base import ProviderError
from app.providers.google import GoogleGeminiProvider


def provider(**overrides) -> GoogleGeminiProvider:
    values = {
        "base_url": "https://generativelanguage.googleapis.com/v1beta",
        "api_key": "test-key",
        "model": "gemini-3.8-flash",
    }
    values.update(overrides)
    return GoogleGeminiProvider(**values)


def test_requires_api_key() -> None:
    with pytest.raises(ProviderError, match="GOOGLE_API_KEY"):
        provider(api_key="")


def test_parses_gemini_response(monkeypatch: pytest.MonkeyPatch) -> None:
    sut = provider()
    captured: dict = {}

    def fake_post(url, payload, headers):
        captured["url"] = url
        captured["payload"] = payload
        captured["headers"] = headers
        return httpx.Response(
            200,
            json={
                "candidates": [
                    {
                        "content": {"parts": [{"text": '{"layout_type":"customer_ledger"}'}]},
                        "finishReason": "STOP",
                    }
                ],
                "usageMetadata": {"promptTokenCount": 120, "candidatesTokenCount": 30},
                "modelVersion": "gemini-3.8-flash",
            },
        )

    monkeypatch.setattr(sut, "_post_with_retries", fake_post)
    result = sut.complete(image=b"png-bytes", mime_type="image/png", prompt="read this")

    assert result.text == '{"layout_type":"customer_ledger"}'
    assert result.input_tokens == 120
    assert result.output_tokens == 30
    assert result.model == "gemini-3.8-flash"
    assert captured["url"].endswith("/models/gemini-3.8-flash:generateContent")
    assert "key=" not in captured["url"]
    assert captured["headers"]["x-goog-api-key"] == "test-key"
    generation = captured["payload"]["generationConfig"]
    assert "temperature" not in generation
    assert generation["thinkingConfig"] == {"thinkingLevel": "low"}
    assert generation["responseMimeType"] == "application/json"


def test_gemini_25_disables_thinking_budget(monkeypatch: pytest.MonkeyPatch) -> None:
    sut = provider(model="gemini-2.5-flash")
    captured: dict = {}

    def fake_post(url, payload, headers):
        captured["generation"] = payload["generationConfig"]
        return httpx.Response(
            200,
            json={"candidates": [{"content": {"parts": [{"text": "{}"}]}}]},
        )

    monkeypatch.setattr(sut, "_post_with_retries", fake_post)
    sut.complete(image=b"x", mime_type="image/png", prompt="read")

    assert captured["generation"]["thinkingConfig"] == {"thinkingBudget": 0}


def test_rejects_safety_block(monkeypatch: pytest.MonkeyPatch) -> None:
    sut = provider()
    monkeypatch.setattr(
        sut,
        "_post_with_retries",
        lambda url, payload, headers: httpx.Response(
            200, json={"promptFeedback": {"blockReason": "SAFETY"}}
        ),
    )
    with pytest.raises(ProviderError, match="blocked"):
        sut.complete(image=b"x", mime_type="image/png", prompt="read")


def test_rejects_empty_text(monkeypatch: pytest.MonkeyPatch) -> None:
    sut = provider()
    monkeypatch.setattr(
        sut,
        "_post_with_retries",
        lambda url, payload, headers: httpx.Response(
            200,
            json={
                "candidates": [
                    {
                        "content": {"parts": [{"text": "   "}]},
                        "finishReason": "STOP",
                    }
                ]
            },
        ),
    )
    with pytest.raises(ProviderError, match="empty"):
        sut.complete(image=b"x", mime_type="image/png", prompt="read")
