from __future__ import annotations

from datetime import date
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

IssueCode = Annotated[str, Field(strict=True, min_length=1, max_length=80)]
NonNegativePaise = Annotated[int, Field(strict=True, ge=0)]
BalancePaise = Annotated[int, Field(strict=True)]


class NameValue(BaseModel):
    model_config = ConfigDict(extra="forbid")

    value: str | None
    status: Literal["readable", "ambiguous", "unreadable"]


class Entry(BaseModel):
    model_config = ConfigDict(extra="forbid")

    entry_id: Annotated[str, Field(strict=True, min_length=1, max_length=80)]
    date: date | None
    date_status: Literal["readable", "ambiguous", "unreadable"]
    description: str | None
    debit_paise: NonNegativePaise | None
    credit_paise: NonNegativePaise | None
    entry_status: Literal["active", "crossed_out", "uncertain", "corrected"]
    source_text: str | None
    issues: list[IssueCode]
    running_balance_paise: BalancePaise | None
    customer_name: NameValue | None = None

    @field_validator("issues")
    @classmethod
    def _normalise_issues(cls, value: list[str]) -> list[str]:
        return list(dict.fromkeys(_normalise_issue_code(code) for code in value))


class PageExtraction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["1.0"] = "1.0"
    page_id: str
    layout_type: Literal["customer_ledger", "date_wise", "summary", "unknown"]
    currency: Literal["INR"] = "INR"
    customer_name: NameValue
    opening_balance_paise: BalancePaise | None
    closing_balance_paise: BalancePaise | None
    entries: list[Entry]
    page_issues: list[IssueCode]
    review_required: bool

    @field_validator("page_issues")
    @classmethod
    def _normalise_page_issues(cls, value: list[str]) -> list[str]:
        return list(dict.fromkeys(_normalise_issue_code(code) for code in value))


def _normalise_issue_code(code: str) -> str:
    cleaned = "".join(
        ch.lower() if (ch.isascii() and ch.isalnum()) else "_" for ch in str(code).strip()
    )
    cleaned = "_".join(part for part in cleaned.split("_") if part)
    return (cleaned or "model_issue")[:80]
