from __future__ import annotations

import argparse
import json
import sys
import tempfile
import time
from pathlib import Path
from uuid import UUID, uuid4

from app.config import Settings
from app.pipeline import run_pipeline
from app.providers.factory import build_provider
from evals.metrics import EvaluationReport, evaluate, report_markdown

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SCHEMA = REPO_ROOT / "contracts" / "extraction-schema-v1.0.json"


def load_manifest(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    pages = data.get("pages")
    if not isinstance(pages, list) or not pages:
        raise SystemExit("manifest must contain a non-empty 'pages' array")
    base = path.parent

    resolved: list[dict] = []
    for index, page in enumerate(pages):
        if "page_id" not in page or "image" not in page or "label" not in page:
            raise SystemExit(f"manifest page #{index} needs page_id, image, and label")
        UUID(page["page_id"])
        resolved.append(
            {
                "page_id": page["page_id"],
                "image": (base / page["image"]).resolve(),
                "label": (base / page["label"]).resolve(),
            }
        )
    return resolved


def load_labels(pages: list[dict]) -> dict[str, dict]:
    labels: dict[str, dict] = {}
    for page in pages:
        label = json.loads(Path(page["label"]).read_text(encoding="utf-8"))
        label["page_id"] = page["page_id"]
        labels[page["page_id"]] = label
    return labels


def load_predictions(predictions_dir: Path, pages: list[dict]) -> dict[str, dict]:
    predictions: dict[str, dict] = {}
    for page in pages:
        path = predictions_dir / f"{page['page_id']}.json"
        if path.exists():
            predictions[page["page_id"]] = json.loads(path.read_text(encoding="utf-8"))
    return predictions


def extract_predictions(
    pages: list[dict],
    *,
    provider_name: str,
    model: str | None,
    fixture: Path | None,
    out_dir: Path,
) -> dict[str, dict]:
    work = Path(tempfile.mkdtemp(prefix="kadaibook-eval-"))
    settings = Settings(
        vision_provider=provider_name,
        vision_model=model or "mock-v1",
        extraction_schema_path=DEFAULT_SCHEMA,
        checkpoint_path=work / "checkpoints.sqlite",
        ai_service_token="",
        mock_fixture_path=fixture,
        log_level="WARNING",
    )
    provider = build_provider(settings, provider=provider_name, model=model, fixture_path=fixture)
    predictions_dir = out_dir / "predictions"
    predictions_dir.mkdir(parents=True, exist_ok=True)
    meta: list[dict] = []
    predictions: dict[str, dict] = {}

    for page in pages:
        image_bytes = Path(page["image"]).read_bytes()
        started = time.perf_counter()
        result = run_pipeline(
            settings=settings,
            provider=provider,
            page_id=page["page_id"],
            run_id=str(uuid4()),
            image_bytes=image_bytes,
            mime_type=_guess_mime(page["image"]),
            suffix=Path(page["image"]).suffix or ".bin",
        )
        elapsed = int((time.perf_counter() - started) * 1000)
        record = {
            "page_id": page["page_id"],
            "image": str(page["image"]),
            "status": result.status,
            "attempts": result.attempts,
            "latency_ms": elapsed,
            "model": result.model,
            "layout_type": result.layout_type,
            "layout_confidence": result.layout_confidence,
            "input_tokens": result.input_tokens,
            "output_tokens": result.output_tokens,
            "cost_paise": result.cost_paise,
            "error": result.error,
        }
        meta.append(record)
        print(
            f"[{result.status}] {page['page_id']} {Path(page['image']).name} "
            f"attempts={result.attempts} {elapsed}ms",
            file=sys.stderr,
        )
        if result.ok and result.extraction is not None:
            predictions[page["page_id"]] = result.extraction
            (predictions_dir / f"{page['page_id']}.json").write_text(
                json.dumps(result.extraction, indent=2, ensure_ascii=False),
                encoding="utf-8",
            )
    (out_dir / "runs.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return predictions


def print_report(report: EvaluationReport, markdown_path: Path) -> None:
    markdown = report_markdown(report)
    markdown_path.write_text(markdown, encoding="utf-8")
    print(markdown)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run or score KadaiBook extraction evals")
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--out-dir", type=Path, default=Path("evals/out"))
    parser.add_argument("--predictions-dir", type=Path, help="Score existing predictions, no model calls")
    parser.add_argument("--provider", default="mock", help="mock | openai_compatible | anthropic")
    parser.add_argument("--model", help="Model name; required for real providers")
    parser.add_argument("--fixture", type=Path, help="Mock provider fixture")
    parser.add_argument("--limit", type=int, help="Evaluate only the first N pages")
    parser.add_argument(
        "--notes",
        action="append",
        default=[],
        help="Extra note printed in the report (repeatable)",
    )
    args = parser.parse_args(argv)

    if args.provider != "mock" and not args.model and not args.predictions_dir:
        parser.error("--model is required when using a real provider")

    manifest_path = args.manifest.resolve()
    pages = load_manifest(manifest_path)
    if args.limit:
        pages = pages[: args.limit]
    labels = load_labels(pages)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    notes = list(args.notes)
    if args.predictions_dir:
        predictions = load_predictions(args.predictions_dir, pages)
        notes.append("Scored from an existing predictions directory; no model calls were made.")
    else:
        predictions = extract_predictions(
            pages,
            provider_name=args.provider,
            model=args.model,
            fixture=args.fixture,
            out_dir=args.out_dir,
        )
        if args.provider == "mock":
            notes.append(
                "MOCK PROVIDER: these numbers demonstrate the harness only. "
                "They are not model accuracy and must never be presented as pilot results."
            )

    report = evaluate(labels, predictions, notes=notes)
    report_path = args.out_dir / "report.json"
    report_path.write_text(json.dumps(report.as_dict(), indent=2, ensure_ascii=False), encoding="utf-8")
    print_report(report, args.out_dir / "report.md")
    print(f"Report written to {report_path}", file=sys.stderr)
    return 0


def _guess_mime(path: Path) -> str:
    suffix = path.suffix.lower()
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".heic": "image/heic",
    }.get(suffix, "image/jpeg")


if __name__ == "__main__":
    raise SystemExit(main())
