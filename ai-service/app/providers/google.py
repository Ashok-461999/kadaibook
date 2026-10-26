from __future__ import annotations

import base64
import logging
import time
import urllib.parse
from typing import Any

import httpx

from app.providers.base import SYSTEM_PROMPT, ProviderError, ProviderResult

logger = logging.getLogger(__name__)

RETRYABLE_STATUS = {408, 409, 425, 429, 500, 502, 503, 504}
SAFETY_SETTINGS = [
    {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_ONLY_HIGH"},
    {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_ONLY_HIGH"},
    {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_ONLY_HIGH"},
    {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_ONLY_HIGH"},
]


class GoogleGeminiProvider:
    """Google AI Studio / Gemini API vision provider.

    The API key is sent in a header, never in the URL, so it does not end up in
    request logs. The pipeline still validates every response against the same
    canonical contract as every other provider.
    """

    name = "google"

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        timeout_seconds: float = 120.0,
        max_output_tokens: int = 8192,
        json_mode: bool = True,
        max_retries: int = 2,
        thinking_level: str = "low",
    ) -> None:
        if not api_key:
            raise ProviderError("GOOGLE_API_KEY is required for the google provider")
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds
        self._max_output_tokens = max_output_tokens
        self._json_mode = json_mode
        self._max_retries = max_retries
        self._thinking_level = thinking_level.strip().lower() or "low"

    @property
    def model(self) -> str:
        return self._model

    def complete(self, *, image: bytes, mime_type: str, prompt: str) -> ProviderResult:
        # Gemini 3.6+ ignores or rejects sampling parameters such as temperature.
        # responseMimeType plus the contract validator provide the determinism.
        generation_config: dict[str, Any] = {
            "maxOutputTokens": self._max_output_tokens,
        }
        if self._model.startswith(("gemini-3", "gemini-4")):
            generation_config["thinkingConfig"] = {"thinkingLevel": self._thinking_level}
        elif self._model.startswith("gemini-2.5"):
            generation_config["thinkingConfig"] = {"thinkingBudget": 0}
        if self._json_mode:
            generation_config["responseMimeType"] = "application/json"
        payload: dict[str, Any] = {
            "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {"text": prompt},
                        {
                            "inlineData": {
                                "mimeType": mime_type,
                                "data": base64.b64encode(image).decode("ascii"),
                            }
                        },
                    ],
                }
            ],
            "generationConfig": generation_config,
            "safetySettings": SAFETY_SETTINGS,
        }
        model = urllib.parse.quote(self._model, safe="")
        url = f"{self._base_url}/models/{model}:generateContent"
        headers = {
            "Content-Type": "application/json",
            "x-goog-api-key": self._api_key,
        }
        response = self._post_with_retries(url, payload, headers)
        try:
            body = response.json()
            feedback = body.get("promptFeedback") or {}
            if feedback.get("blockReason"):
                raise ProviderError(
                    f"provider blocked the image ({str(feedback['blockReason']).lower()})"
                )
            candidates = body.get("candidates") or []
            if not candidates:
                raise ProviderError("provider returned no candidates")
            candidate = candidates[0]
            finish_reason = str(candidate.get("finishReason") or "").upper()
            if finish_reason in {"SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII"}:
                raise ProviderError(
                    f"provider stopped the extraction ({finish_reason.lower()})"
                )
            if finish_reason == "MAX_TOKENS":
                raise ProviderError("provider truncated the extraction (max_tokens)")
            parts = (candidate.get("content") or {}).get("parts") or []
            text = "".join(
                str(part.get("text") or "")
                for part in parts
                if isinstance(part, dict) and not part.get("thought")
            )
            if not text.strip():
                raise ProviderError("provider returned an empty extraction")
            usage = body.get("usageMetadata") or {}
            return ProviderResult(
                text=text,
                model=body.get("modelVersion") or self._model,
                input_tokens=int(usage.get("promptTokenCount") or 0),
                output_tokens=int(usage.get("candidatesTokenCount") or 0),
                metadata={"finish_reason": finish_reason},
            )
        except ProviderError:
            raise
        except (TypeError, ValueError, AttributeError) as exc:
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
                    raise ProviderError(
                        f"provider rejected the request (HTTP {response.status_code}): {detail}"
                    )
                else:
                    return response
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                last_error = ProviderError(f"provider transport error: {exc.__class__.__name__}")
            if attempt < self._max_retries:
                delay = min(8.0, 0.5 * (2**attempt))
                logger.warning(
                    "vision provider retry %s in %.1fs: %s", attempt + 1, delay, last_error
                )
                time.sleep(delay)
        raise last_error or ProviderError("provider request failed")
