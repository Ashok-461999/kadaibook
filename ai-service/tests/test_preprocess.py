from __future__ import annotations

import io

import cv2
import numpy as np
import pytest
from PIL import Image, ImageDraw

from app.preprocess import PreprocessError, preprocess_image


def _page_bytes() -> bytes:
    image = Image.new("RGB", (1600, 2200), "white")
    draw = ImageDraw.Draw(image)
    draw.text((100, 100), "KADAI STORES", fill="black")
    for index in range(15):
        draw.line((100, 250 + index * 110, 1500, 250 + index * 110), fill="black")
        draw.text((120, 210 + index * 110), f"row {index}  1250", fill="black")
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=92)
    return buffer.getvalue()


def test_preprocess_returns_decodable_bounded_png():
    output = preprocess_image(_page_bytes())
    assert output.startswith(b"\x89PNG")
    array = np.frombuffer(output, np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    assert image is not None
    height, width = image.shape[:2]
    assert max(height, width) <= 2400
    assert height > 100 and width > 100


def test_preprocess_deskews_a_rotated_page():
    image = Image.open(io.BytesIO(_page_bytes()))
    rotated = image.rotate(4, expand=True, fillcolor="white")
    buffer = io.BytesIO()
    rotated.save(buffer, format="PNG")
    output = preprocess_image(buffer.getvalue())
    assert output.startswith(b"\x89PNG")


def test_invalid_image_raises():
    with pytest.raises(PreprocessError):
        preprocess_image(b"definitely not an image")
