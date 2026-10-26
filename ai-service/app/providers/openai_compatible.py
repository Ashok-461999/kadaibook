from __future__ import annotations

import base64
import logging
import time
from typing import Any

import httpx

from app.providers.base import SYSTEM_PROMPT, ProviderError, ProviderResult

logger = logging.getLogger(__name__)

RETRYABLE_STATUS = {408, 409, 425, 429, 500, 502, 503, 504}


class OpenAICompatibleProvider:
    name = "openai_compatible"

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        timeout_seconds: float = 90.0,
        max_output_tokens: int = 4096,
        json_mode: bool = True,
        max_retries: int = 2,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds
        self._max_output_tokens = max_output_tokens
        self._json_mode = json_mode
        self._max_retries = max_retries

    @property
    def model(self) -> str:
        return self._model

    def complete(self, *, image: bytes, mime_type: str, prompt: str) -> ProviderResult:
        data_url = f"data:{mime_type};base64,{base64.b64encode(image).decode('ascii')}"
        payload: dict[str, Any] = {
            "model": self._model,
            "max_tokens": self._max_output_tokens,
            "temperature": 0,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {"type": "image_url", "image_url": {"url": data_url}},
                    ],
                },
            ],
        }
        if self._json_mode:
            payload["response_format"] = {"type": "json_object"}

        headers = {"Content-Type": "application/json"}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"

        response = self._post_with_retries(f"{self._base_url}/chat/completions", payload, headers)
        try:
            body = response.json()
            message = body["choices"][0]["message"]["content"]
            text = _content_to_text(message)
            usage = body.get("usage") or {}
            return ProviderResult(
                text=text,
                model=body.get("model") or self._model,
                input_tokens=int(usage.get("prompt_tokens") or 0),
                output_tokens=int(usage.get("completion_tokens") or 0),
            )
        except (KeyError, IndexError, TypeError, ValueError) as exc:
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


def _content_to_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, dict) and isinstance(block.get("text"), str):
                parts.append(block["text"])
        return "".join(parts)
    raise ValueError("unsupported message content")
