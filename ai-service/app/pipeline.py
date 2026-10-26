from __future__ import annotations

import logging
import time
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Any, TypedDict

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from app.assets import RunAssetStore
from app.config import Settings
from app.contract import ContractViolation, validate_model_output
from app.parsing import JsonParseError, parse_json_object
from app.preprocess import PreprocessError, preprocess_image
from app.prompts import CLASSIFY_PROMPT_V1, build_extract_prompt
from app.providers.base import ProviderError, VisionProvider
from app.schema import PageExtraction
from app.tagging import tag_issues

logger = logging.getLogger(__name__)

try:  # pragma: no cover - availability depends on the installed extras
    from langgraph.checkpoint.sqlite import SqliteSaver

    _HAS_SQLITE = True
except ImportError:  # pragma: no cover
    SqliteSaver = None  # type: ignore[assignment]
    _HAS_SQLITE = False


class PipelineState(TypedDict, total=False):
    page_id: str
    run_id: str
    original_path: str
    processed_path: str
    mime_type: str
    layout_type: str
    layout_confidence: float
    attempts: int
    raw_text: str
    last_error: str | None
    extraction: dict[str, Any] | None
    page_issues: list[str]
    review_required: bool
    status: str
    error: str | None
    model: str
    input_tokens: int
    output_tokens: int


@dataclass
class PipelineResult:
    ok: bool
    status: str
    extraction: dict[str, Any] | None
    attempts: int
    error: str | None
    model: str
    input_tokens: int
    output_tokens: int
    cost_paise: int
    layout_type: str
    layout_confidence: float
    state: dict[str, Any]

    @property
    def review_required(self) -> bool:
        return bool((self.extraction or {}).get("review_required"))


@contextmanager
def open_checkpointer(settings: Settings) -> Iterator[Any]:
    if _HAS_SQLITE:
        path = Path(settings.checkpoint_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        with SqliteSaver.from_conn_string(str(path)) as saver:  # type: ignore[union-attr]
            yield saver
    else:  # pragma: no cover - only in minimal installs
        logger.warning("langgraph-checkpoint-sqlite unavailable; using in-memory checkpoints")
        yield MemorySaver()


class ExtractionPipeline:
    def __init__(self, settings: Settings, provider: VisionProvider) -> None:
        self.settings = settings
        self.provider = provider
        self.assets = RunAssetStore(Path(settings.checkpoint_path).parent / "run-assets")

    def run(
        self,
        *,
        page_id: str,
        run_id: str,
        image_bytes: bytes,
        mime_type: str,
        suffix: str = ".bin",
        checkpointer: Any | None = None,
    ) -> PipelineResult:
        started = time.perf_counter()
        original = self.assets.save(self.assets.original_path(run_id, suffix), image_bytes)
        initial: PipelineState = {
            "page_id": page_id,
            "run_id": run_id,
            "original_path": str(original),
            "mime_type": mime_type,
            "attempts": 0,
            "status": "running",
            "input_tokens": 0,
            "output_tokens": 0,
            "page_issues": [],
        }

        graph = self._build_graph().compile(checkpointer=checkpointer)
        config = {"configurable": {"thread_id": run_id}, "recursion_limit": 30}

        # A retried request must be idempotent: if this run already completed,
        # return its checkpointed state instead of calling the model again. If a
        # previous process died mid-run, resume from the checkpoint with None.
        state: PipelineState = initial
        if checkpointer is not None:
            snapshot = graph.get_state(config)
            if snapshot is not None and snapshot.values:
                if not snapshot.next:
                    state = snapshot.values
                else:
                    state = graph.invoke(None, config=config)
            else:
                state = graph.invoke(initial, config=config)
        else:
            state = graph.invoke(initial, config=config)

        latency_ms = int((time.perf_counter() - started) * 1000)
        cost_paise = self._cost(state.get("input_tokens", 0), state.get("output_tokens", 0))
        status = state.get("status", "extraction_failed")
        extraction = state.get("extraction")

        result = PipelineResult(
            ok=status == "ok" and extraction is not None,
            status=status,
            extraction=extraction,
            attempts=state.get("attempts", 0),
            error=state.get("error"),
            model=state.get("model", getattr(self.provider, "name", "unknown")),
            input_tokens=state.get("input_tokens", 0),
            output_tokens=state.get("output_tokens", 0),
            cost_paise=cost_paise,
            layout_type=state.get("layout_type", "unknown"),
            layout_confidence=float(state.get("layout_confidence") or 0.0),
            state=dict(state),
        )
        result.state["latency_ms"] = latency_ms
        self.assets.cleanup([original, Path(self.assets.processed_path(run_id))])
        self._sweep()
        return result

    def run_with_checkpointer(
        self,
        *,
        page_id: str,
        run_id: str,
        image_bytes: bytes,
        mime_type: str,
        suffix: str,
    ) -> PipelineResult:
        if _HAS_SQLITE:
            with open_checkpointer(self.settings) as saver:
                return self.run(
                    page_id=page_id,
                    run_id=run_id,
                    image_bytes=image_bytes,
                    mime_type=mime_type,
                    suffix=suffix,
                    checkpointer=saver,
                )
        return self.run(  # pragma: no cover - only in minimal installs
            page_id=page_id,
            run_id=run_id,
            image_bytes=image_bytes,
            mime_type=mime_type,
            suffix=suffix,
            checkpointer=MemorySaver(),
        )

    def _sweep(self) -> None:
        try:
            removed = self.assets.sweep()
            if removed:
                logger.info("swept %s stale extraction assets", removed)
        except Exception:  # pragma: no cover
            logger.exception("stale extraction asset sweep failed")

    def _cost(self, input_tokens: int, output_tokens: int) -> int:
        total = (
            input_tokens * self.settings.input_cost_paise_per_mtok
            + output_tokens * self.settings.output_cost_paise_per_mtok
        )
        return (total + 999_999) // 1_000_000

    # ------------------------------------------------------------------ graph
    def _build_graph(self) -> StateGraph:
        graph = StateGraph(PipelineState)
        graph.add_node("preprocess", self._preprocess)
        graph.add_node("classify", self._classify)
        graph.add_node("extract", self._extract)
        graph.add_node("validate", self._validate)
        graph.add_node("tag_issues", self._tag)
        graph.add_node("finalize", self._finalize)

        graph.add_edge(START, "preprocess")
        graph.add_edge("preprocess", "classify")
        graph.add_conditional_edges(
            "classify",
            self._route_after_classify,
            {"extract": "extract", "finalize": "finalize"},
        )
        graph.add_edge("extract", "validate")
        graph.add_conditional_edges(
            "validate",
            self._route_after_validate,
            {"tag_issues": "tag_issues", "extract": "extract", "finalize": "finalize"},
        )
        graph.add_edge("tag_issues", "finalize")
        graph.add_edge("finalize", END)
        return graph

    def _preprocess(self, state: PipelineState) -> PipelineState:
        original = Path(state["original_path"])
        processed = self.assets.processed_path(state["run_id"])
        issues = list(state.get("page_issues", []))
        try:
            data = original.read_bytes()
            cleaned = preprocess_image(data)
            self.assets.save(processed, cleaned)
            processed_path = str(processed)
        except (OSError, PreprocessError) as exc:
            logger.warning("preprocessing failed for run %s: %s", state.get("run_id"), exc)
            if "preprocess_failed" not in issues:
                issues.append("preprocess_failed")
            processed_path = str(original)
        return {"processed_path": processed_path, "page_issues": issues}

    def _image_bytes(self, state: PipelineState) -> bytes:
        path = Path(state.get("processed_path") or state["original_path"])
        return path.read_bytes()

    def _classify(self, state: PipelineState) -> PipelineState:
        try:
            result = self.provider.complete(
                image=self._image_bytes(state),
                mime_type="image/png" if state.get("processed_path", "").endswith(".png") else state.get("mime_type", "image/jpeg"),
                prompt=CLASSIFY_PROMPT_V1,
            )
        except (ProviderError, OSError, ValueError) as exc:
            return {
                "status": "extraction_failed",
                "error": _safe_error("layout classification failed", exc),
                "model": getattr(self.provider, "name", "unknown"),
            }

        usage = _usage(state, result.input_tokens, result.output_tokens)
        try:
            payload = parse_json_object(result.text)
            layout = str(payload.get("layout_type", "")).strip()
            confidence = float(payload.get("confidence", 0.0))
            if layout not in {"customer_ledger", "date_wise", "summary", "unknown"}:
                raise ValueError("invalid layout_type")
            if not 0.0 <= confidence <= 1.0:
                raise ValueError("confidence outside 0..1")
        except (JsonParseError, ValueError, TypeError) as exc:
            return {
                **usage,
                "status": "extraction_failed",
                "error": _safe_error("layout classification returned invalid output", exc),
                "model": result.model,
            }

        return {
            **usage,
            "layout_type": layout,
            "layout_confidence": confidence,
            "model": result.model,
            "status": "classified",
        }

    def _route_after_classify(self, state: PipelineState) -> str:
        if state.get("status") != "classified":
            return "finalize"
        if state.get("layout_type") == "unknown":
            return "finalize"
        if float(state.get("layout_confidence") or 0.0) < self.settings.layout_confidence_threshold:
            return "finalize"
        return "extract"

    def _extract(self, state: PipelineState) -> PipelineState:
        attempts = int(state.get("attempts", 0)) + 1
        if attempts > self.settings.max_extraction_attempts:
            return {
                "attempts": attempts,
                "status": "extraction_failed",
                "error": "retry budget exhausted",
            }

        prompt = build_extract_prompt(
            layout=state.get("layout_type", "unknown"),
            page_id=state["page_id"],
            schema_path=self.settings.extraction_schema_path,
            validation_error=state.get("last_error"),
        )
        try:
            result = self.provider.complete(
                image=self._image_bytes(state),
                mime_type="image/png" if state.get("processed_path", "").endswith(".png") else state.get("mime_type", "image/jpeg"),
                prompt=prompt,
            )
        except (ProviderError, OSError, ValueError) as exc:
            return {
                "attempts": attempts,
                "status": "extraction_failed",
                "error": _safe_error("extraction call failed", exc),
                "model": getattr(self.provider, "name", "unknown"),
            }

        usage = _usage(state, result.input_tokens, result.output_tokens)
        logger.info(
            "extraction attempt %s/%s run=%s model=%s",
            attempts,
            self.settings.max_extraction_attempts,
            state.get("run_id"),
            result.model,
        )
        return {
            **usage,
            "attempts": attempts,
            "raw_text": result.text,
            "model": result.model,
            "status": "extracted",
        }

    def _validate(self, state: PipelineState) -> PipelineState:
        try:
            payload = parse_json_object(state.get("raw_text", ""))
            payload["page_id"] = state["page_id"]
            payload["layout_type"] = state.get("layout_type", payload.get("layout_type", "unknown"))
            payload["schema_version"] = "1.0"
            if len(payload.get("entries") or []) > 500:
                raise ContractViolation("too many entries")
            extraction = validate_model_output(payload, self.settings.extraction_schema_path)
        except (JsonParseError, ContractViolation, ValueError, TypeError) as exc:
            logger.warning("validation failed for run %s: %s", state.get("run_id"), exc)
            return {
                "last_error": str(exc)[:1200],
                "status": "invalid",
                "extraction": None,
            }
        return {
            "last_error": None,
            "status": "validated",
            "extraction": extraction.model_dump(mode="json"),
        }

    def _route_after_validate(self, state: PipelineState) -> str:
        status = state.get("status")
        if status == "validated":
            return "tag_issues"
        if status == "extraction_failed":
            return "finalize"
        if int(state.get("attempts", 0)) < self.settings.max_extraction_attempts:
            return "extract"
        return "finalize"

    def _tag(self, state: PipelineState) -> PipelineState:
        try:
            extraction = PageExtraction.model_validate(state["extraction"])
            tagged = tag_issues(
                extraction,
                layout_confidence=float(state.get("layout_confidence") or 0.0),
                confidence_threshold=self.settings.layout_confidence_threshold,
            )
            data = tagged.model_dump(mode="json")
        except Exception as exc:  # pragma: no cover - defensive
            logger.exception("issue tagging failed")
            return {
                "status": "extraction_failed",
                "error": _safe_error("issue tagging failed", exc),
                "extraction": None,
            }
        return {
            "status": "ok",
            "extraction": data,
            "page_issues": data["page_issues"],
            "review_required": data["review_required"],
            "error": None,
        }

    def _finalize(self, state: PipelineState) -> PipelineState:
        status = state.get("status")
        if status == "ok":
            return {"status": "ok", "error": None}
        if status == "classified" and (
            state.get("layout_type") == "unknown"
            or float(state.get("layout_confidence") or 0.0) < self.settings.layout_confidence_threshold
        ):
            return {
                "status": "unsupported_layout",
                "error": "page layout could not be classified confidently",
            }
        error = state.get("error") or state.get("last_error")
        if not error:
            error = "extraction failed"
        return {
            "status": "extraction_failed",
            "error": str(error)[:800],
            "extraction": None,
        }


def _usage(state: PipelineState, input_tokens: int, output_tokens: int) -> dict[str, int]:
    return {
        "input_tokens": int(state.get("input_tokens", 0)) + int(input_tokens or 0),
        "output_tokens": int(state.get("output_tokens", 0)) + int(output_tokens or 0),
    }


def _safe_error(context: str, exc: Exception) -> str:
    message = str(exc).replace("\n", " ").strip()
    return f"{context}: {message[:500]}" if message else context


def run_pipeline(
    *,
    settings: Settings,
    provider: VisionProvider,
    page_id: str,
    run_id: str,
    image_bytes: bytes,
    mime_type: str,
    suffix: str,
) -> PipelineResult:
    """Convenience wrapper used by the API and the evaluation harness."""
    pipeline = ExtractionPipeline(settings, provider)
    return pipeline.run_with_checkpointer(
        page_id=page_id,
        run_id=run_id,
        image_bytes=image_bytes,
        mime_type=mime_type,
        suffix=suffix,
    )
