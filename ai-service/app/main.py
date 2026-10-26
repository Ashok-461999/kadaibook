from __future__ import annotations

import logging
import secrets
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated, Literal
from uuid import UUID

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from app.assets import RunAssetStore
from app.config import Settings, get_settings
from app.pipeline import ExtractionPipeline
from app.providers.base import VisionProvider
from app.providers.factory import build_provider

logger = logging.getLogger(__name__)

MIME_SUFFIX = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/heic": ".heic",
    "image/heif": ".heif",
}


class HealthResponse(BaseModel):
    status: Literal["ok"]
    provider: str
    model: str
    prompt_version: str
    schema_version: Literal["1.0"] = "1.0"


class ExtractionResponse(BaseModel):
    status: Literal["ok", "extraction_failed", "unsupported_layout"]
    extraction: dict | None
    error: str | None
    attempts: int
    latency_ms: int
    model: str
    prompt_version: str
    schema_version: str
    input_tokens: int
    output_tokens: int
    cost_paise: int
    layout_type: str
    layout_confidence: float


def create_app(
    settings: Settings | None = None,
    provider: VisionProvider | None = None,
    *,
    pipeline: ExtractionPipeline | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        try:
            RunAssetStore(Path(settings.checkpoint_path).parent / "run-assets").sweep()
        except Exception:  # pragma: no cover
            logger.exception("startup asset sweep failed")
        yield

    app = FastAPI(
        title="KadaiBook AI extraction",
        version="0.1.0",
        description=(
            "Vision extraction for handwritten shop credit notebooks. "
            "The service reads and flags; it never writes to the ledger or calculates balances."
        ),
        lifespan=lifespan,
    )
    vision = provider or build_provider(settings)
    active_pipeline = pipeline or ExtractionPipeline(settings, vision)
    app.state.settings = settings
    app.state.provider = vision
    app.state.pipeline = active_pipeline

    @app.get("/healthz", response_model=HealthResponse)
    def healthz() -> HealthResponse:
        return HealthResponse(
            status="ok",
            provider=getattr(vision, "name", "unknown"),
            model=getattr(vision, "model", settings.vision_model),
            prompt_version=settings.prompt_version,
        )

    @app.post("/extract", response_model=ExtractionResponse)
    async def extract(
        page_id: Annotated[UUID, Form()],
        run_id: Annotated[UUID, Form()],
        file: Annotated[UploadFile, File()],
        x_internal_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> ExtractionResponse:
        _require_token(settings, x_internal_token)

        content_type = (file.content_type or "").split(";")[0].strip().lower()
        if content_type and not content_type.startswith("image/"):
            raise HTTPException(status_code=415, detail="only image uploads are accepted")

        payload = await file.read(settings.max_image_bytes + 1)
        await file.close()
        if not payload:
            raise HTTPException(status_code=400, detail="empty image")
        if len(payload) > settings.max_image_bytes:
            raise HTTPException(status_code=413, detail="image too large")

        suffix = MIME_SUFFIX.get(content_type, ".bin")
        mime_type = content_type or "image/jpeg"

        def _run() -> ExtractionResponse:
            result = active_pipeline.run_with_checkpointer(
                page_id=str(page_id),
                run_id=str(run_id),
                image_bytes=payload,
                mime_type=mime_type,
                suffix=suffix,
            )
            status = result.status
            if status not in {"ok", "extraction_failed", "unsupported_layout"}:
                status = "extraction_failed"
            return ExtractionResponse(
                status=status,  # type: ignore[arg-type]
                extraction=result.extraction,
                error=result.error,
                attempts=result.attempts,
                latency_ms=int(result.state.get("latency_ms", 0)),
                model=result.model,
                prompt_version=settings.prompt_version,
                schema_version="1.0",
                input_tokens=result.input_tokens,
                output_tokens=result.output_tokens,
                cost_paise=result.cost_paise,
                layout_type=result.layout_type,
                layout_confidence=result.layout_confidence,
            )

        return await run_in_threadpool(_run)

    @app.exception_handler(Exception)
    async def unhandled(_request, exc: Exception):  # pragma: no cover - safety net
        logger.exception("unhandled AI service error", exc_info=exc)
        return JSONResponse(status_code=500, content={"detail": "internal extraction error"})

    return app


def _require_token(settings: Settings, provided: str | None) -> None:
    expected = settings.ai_service_token
    if not expected:
        return
    if not provided or not secrets.compare_digest(provided, expected):
        raise HTTPException(status_code=401, detail="invalid internal service token")


def main() -> None:  # pragma: no cover
    import uvicorn

    uvicorn.run("app.main:create_app", factory=True, host="0.0.0.0", port=8000)


if __name__ == "__main__":  # pragma: no cover
    main()
