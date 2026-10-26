from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


def _default_schema_path() -> Path:
    env = os.environ.get("EXTRACTION_SCHEMA_PATH")
    if env:
        return Path(env)
    repo_candidate = Path(__file__).resolve().parents[2] / "contracts" / "extraction-schema-v1.0.json"
    docker_candidate = Path("/app/contracts/extraction-schema-v1.0.json")
    return repo_candidate if repo_candidate.exists() else docker_candidate


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    app_name: str = "kadaibook-ai"
    log_level: str = "INFO"

    vision_provider: str = "mock"
    vision_model: str = "mock-v1"
    vision_base_url: str = "https://api.openai.com/v1"
    vision_api_key: str = ""
    vision_timeout_seconds: float = 90.0
    vision_max_output_tokens: int = 4096
    vision_json_mode: bool = True
    vision_max_retries: int = 2
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-sonnet-4-5"
    anthropic_base_url: str = "https://api.anthropic.com/v1"
    google_api_key: str = ""
    google_model: str = "gemini-3.8-flash"
    google_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    google_thinking_level: str = "low"

    input_cost_paise_per_mtok: int = 0
    output_cost_paise_per_mtok: int = 0

    layout_confidence_threshold: float = 0.5
    max_extraction_attempts: int = 3
    prompt_version: str = "extract_v1"

    ai_service_token: str = "dev-internal-token"
    max_image_bytes: int = 20 * 1024 * 1024
    checkpoint_path: Path = Path("./.langgraph_checkpoints/checkpoints.sqlite")
    extraction_schema_path: Path = Field(default_factory=_default_schema_path)
    mock_fixture_path: Path | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
