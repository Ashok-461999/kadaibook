from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator
from jsonschema.exceptions import ValidationError

from app.schema import PageExtraction


class ContractViolation(ValueError):
    """Raised when a model result does not satisfy the canonical contract."""

    def __init__(self, message: str, *, path: str = "") -> None:
        self.path = path
        super().__init__(message)


@lru_cache
def _load_raw(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


@lru_cache
def _validator(path: str) -> Draft202012Validator:
    schema = json.loads(_load_raw(path))
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER)


def schema_text(path: Path) -> str:
    """Compact canonical schema for prompts."""
    return json.dumps(json.loads(_load_raw(str(path))), separators=(",", ":"))


def validate_contract(payload: dict[str, Any], schema_path: Path) -> None:
    """Validate an already-parsed object against the canonical JSON Schema."""
    errors = sorted(_validator(str(schema_path)).iter_errors(payload), key=lambda e: list(e.path))
    if errors:
        first = errors[0]
        location = "/".join(str(part) for part in first.path) or "<root>"
        raise ContractViolation(f"{location}: {first.message}", path=location)


def validate_model_output(payload: dict[str, Any], schema_path: Path) -> PageExtraction:
    """Pydantic validation first, then the canonical contract. Both must pass."""
    try:
        extraction = PageExtraction.model_validate(payload)
    except Exception as exc:  # pydantic.ValidationError, kept generic at boundary
        raise ContractViolation(_summarise(exc)) from exc
    data = extraction.model_dump(mode="json")
    try:
        validate_contract(data, schema_path)
    except ValidationError as exc:  # pragma: no cover - defensive
        raise ContractViolation(str(exc)) from exc
    return extraction


def _summarise(exc: Exception) -> str:
    text = str(exc).strip().replace("\n", " ")
    return text[:800] if text else exc.__class__.__name__
