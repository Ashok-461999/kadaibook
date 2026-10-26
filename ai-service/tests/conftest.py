from __future__ import annotations

import io
import json
from pathlib import Path

import pytest
from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = REPO_ROOT / "contracts" / "extraction-schema-v1.0.json"
MOCK_FIXTURE = REPO_ROOT / "ai-service" / "app" / "providers" / "mock_fixture.json"


@pytest.fixture
def schema_path() -> Path:
    return SCHEMA_PATH


@pytest.fixture
def mock_fixture() -> dict:
    return json.loads(MOCK_FIXTURE.read_text(encoding="utf-8"))


@pytest.fixture
def png_bytes() -> bytes:
    image = Image.new("RGB", (900, 1200), "white")
    draw = ImageDraw.Draw(image)
    draw.text((60, 60), "KADAI STORES - SYNTHETIC TEST PAGE", fill="black")
    for row in range(8):
        y = 160 + row * 90
        draw.line((60, y, 840, y), fill="#444444")
        draw.text((70, y - 30), f"item {row}    {(row + 1) * 125}", fill="black")
        draw.text((650, y - 30), f"{(row + 1) * 1500}", fill="black")
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()
