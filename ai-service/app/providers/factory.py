from __future__ import annotations

from pathlib import Path

from app.config import Settings
from app.providers.anthropic import AnthropicProvider
from app.providers.base import ProviderError, VisionProvider
from app.providers.google import GoogleGeminiProvider
from app.providers.mock import MockVisionProvider
from app.providers.openai_compatible import OpenAICompatibleProvider


def build_provider(
    settings: Settings,
    *,
    provider: str | None = None,
    model: str | None = None,
    fixture_path: Path | None = None,
) -> VisionProvider:
    name = (provider or settings.vision_provider or "mock").strip().lower()
    chosen_model = model or settings.vision_model

    if name == "mock":
        return MockVisionProvider(fixture_path or settings.mock_fixture_path, model=chosen_model)

    if name in {"openai", "openai_compatible", "compatible"}:
        return OpenAICompatibleProvider(
            base_url=settings.vision_base_url,
            api_key=settings.vision_api_key,
            model=chosen_model,
            timeout_seconds=settings.vision_timeout_seconds,
            max_output_tokens=settings.vision_max_output_tokens,
            json_mode=settings.vision_json_mode,
            max_retries=settings.vision_max_retries,
        )

    if name == "anthropic":
        anthropic_model = chosen_model
        if chosen_model in {"", "mock-v1"}:
            anthropic_model = settings.anthropic_model
        return AnthropicProvider(
            base_url=settings.anthropic_base_url,
            api_key=settings.anthropic_api_key,
            model=anthropic_model,
            timeout_seconds=settings.vision_timeout_seconds,
            max_output_tokens=settings.vision_max_output_tokens,
            max_retries=settings.vision_max_retries,
        )

    if name in {"google", "gemini"}:
        google_model = chosen_model
        if chosen_model in {"", "mock-v1"}:
            google_model = settings.google_model
        return GoogleGeminiProvider(
            base_url=settings.google_base_url,
            api_key=settings.google_api_key,
            model=google_model,
            timeout_seconds=settings.vision_timeout_seconds,
            max_output_tokens=settings.vision_max_output_tokens,
            json_mode=settings.vision_json_mode,
            max_retries=settings.vision_max_retries,
            thinking_level=settings.google_thinking_level,
        )

    raise ProviderError(f"unsupported vision provider: {name}")
