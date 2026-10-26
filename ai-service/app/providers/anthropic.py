from __future__ import annotations

import base64
import logging
import time
from typing import Any

import httpx

from app.providers.base import SYSTEM_PROMPT, ProviderError, ProviderResult

logger = logging.getLogger(__name__)

RETRYABLE_STATUS = {408, 409, 429, 500, 502, 503, 504}
ANTHROPIC_VERSION = "2023-06-01"


class AnthropicProvider:
    name = "anthropic"

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        timeout_seconds: float = 90.0,
        max_output_tokens: int = 4096,
        max_retries: int = 2,
    ) -> None:
        if not api_key:
            raise ProviderError("ANTHROPIC_API_KEY is required for the anthropic provider")
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds
        self._max_output_tokens = max_output_tokens
        self._max_retries = max_retries

    @property
    def model(self) -> str:
        return self._model

    def complete(self, *, image: bytes, mime_type: str, prompt: str) -> ProviderResult:
        payload: dict[str, Any] = {
            "model": self._model,
            "max_tokens": self._max_output_tokens,
            "temperature": 0,
            "system": SYSTEM_PROMPT,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image",
                            "source": {
                                "type": "base64",
                                "media_type": mime_type,
                                "data": base64.b64encode(image).decode("ascii"),
                            },
                        },
                        {"type": "text", "text": prompt},
                    ],
                }
            ],
        }
        headers = {
            "Content-Type": "application/json",
            "x-api-key": self._api_key,
            "anthropic-version": ANTHROPIC_VERSION,
        }
        response = self._post_with_retries(f"{self._base_url}/messages", payload, headers)
        try:
            body = response.json()
            blocks = body["content"]
            text = "".join(block["text"] for block in blocks if block.get("type") == "text")
            usage = body.get("usage") or {}
            return ProviderResult(
                text=text,
                model=body.get("model") or self._model,
                input_tokens=int(usage.get("input_tokens") or 0),
                output_tokens=int(usage.get("output_tokens") or 0),
            )
        except (KeyError, TypeError, ValueError) as exc:
            raise ProviderError("provider returned an unexpected response shape") from exc

    def _post_with_retries(
        self, url: str, payload: dict[str, Any], headers: dict[str, str]
    ) -> httpx.Response:
        last_error: Exception | None = None
        for attempt in range(self._max_retries + 1):
            try:
                with httpx.Client(timeout=httpx.Timeout(self._timeout, connect=10.0)) as client:
                    response = client.post(url, json=payload, headers=headers)
                if response.status_code in RETRYABLE_STATUS:
                    last_error = ProviderError(
                        f"provider temporarily unavailable (HTTP {response.status_code})"
                    )
                elif response.status_code >= 400:
                    detail = response.text[:300].replace("\n", " ")
                    raise ProviderError(f"provider rejected the request (HTTP {response.status_code}): {detail}")
                else:
                    return response
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                last_error = ProviderError(f"provider transport error: {exc.__class__.__name__}")
            if attempt < self._max_retries:
                delay = min(8.0, 0.5 * (2**attempt))
                logger.warning("vision provider retry %s in %.1fs: %s", attempt + 1, delay, last_error)
                time.sleep(delay)
        raise last_error or ProviderError("provider request failed")
