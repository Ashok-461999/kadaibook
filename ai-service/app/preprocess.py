from __future__ import annotations

import io
import logging

import cv2
import numpy as np
from PIL import Image, ImageOps

logger = logging.getLogger(__name__)

MAX_DIMENSION = 2400
MAX_SKEW_DEGREES = 15.0


class PreprocessError(ValueError):
    pass


def preprocess_image(data: bytes) -> bytes:
    """EXIF-correct, deskew, crop the page, improve contrast, bound the size.

    Deterministic OpenCV only: no model is involved in cleanup. Returns an
    encoded PNG. Raises PreprocessError when the bytes are not a decodable image.
    """
    try:
        with Image.open(io.BytesIO(data)) as pil_image:
            pil_image = ImageOps.exif_transpose(pil_image).convert("RGB")
            rgb = np.asarray(pil_image)
    except Exception as exc:
        raise PreprocessError("image could not be decoded") from exc

    if rgb.size == 0:
        raise PreprocessError("image is empty")

    image = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    image = _deskew(image)
    image = _crop_to_content(image)
    image = _enhance_contrast(image)
    image = _resize(image)

    ok, encoded = cv2.imencode(".png", image)
    if not ok:  # pragma: no cover - OpenCV failure is effectively impossible here
        raise PreprocessError("preprocessed image could not be encoded")
    return encoded.tobytes()


def _foreground_mask(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)
    _, mask = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    return mask


def _deskew(image: np.ndarray) -> np.ndarray:
    mask = _foreground_mask(image)
    coords = cv2.findNonZero(mask)
    if coords is None or len(coords) < 50:
        return image

    angle = cv2.minAreaRect(coords)[-1]
    if angle > 45:
        angle -= 90
    elif angle < -45:
        angle += 90
    if abs(angle) < 0.2 or abs(angle) > MAX_SKEW_DEGREES:
        return image

    height, width = image.shape[:2]
    center = (width / 2, height / 2)
    matrix = cv2.getRotationMatrix2D(center, angle, 1.0)
    cos = abs(matrix[0, 0])
    sin = abs(matrix[0, 1])
    new_width = int(height * sin + width * cos)
    new_height = int(height * cos + width * sin)
    matrix[0, 2] += new_width / 2 - center[0]
    matrix[1, 2] += new_height / 2 - center[1]
    rotated = cv2.warpAffine(
        image,
        matrix,
        (new_width, new_height),
        flags=cv2.INTER_CUBIC,
        borderMode=cv2.BORDER_REPLICATE,
    )
    logger.debug("deskewed page by %.2f degrees", angle)
    return rotated


def _crop_to_content(image: np.ndarray) -> np.ndarray:
    mask = _foreground_mask(image)
    coords = cv2.findNonZero(mask)
    if coords is None:
        return image
    x, y, width, height = cv2.boundingRect(coords)
    if width < 40 or height < 40:
        return image
    pad_x = max(8, int(width * 0.02))
    pad_y = max(8, int(height * 0.02))
    x0 = max(0, x - pad_x)
    y0 = max(0, y - pad_y)
    x1 = min(image.shape[1], x + width + pad_x)
    y1 = min(image.shape[0], y + height + pad_y)
    if (x1 - x0) < image.shape[1] * 0.5 or (y1 - y0) < image.shape[0] * 0.5:
        return image
    return image[y0:y1, x0:x1]


def _enhance_contrast(image: np.ndarray) -> np.ndarray:
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
    lightness, channel_a, channel_b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    balanced = clahe.apply(lightness)
    merged = cv2.merge((balanced, channel_a, channel_b))
    return cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)


def _resize(image: np.ndarray) -> np.ndarray:
    height, width = image.shape[:2]
    longest = max(height, width)
    if longest <= MAX_DIMENSION:
        return image
    scale = MAX_DIMENSION / float(longest)
    return cv2.resize(
        image,
        (max(1, int(width * scale)), max(1, int(height * scale))),
        interpolation=cv2.INTER_AREA,
    )
