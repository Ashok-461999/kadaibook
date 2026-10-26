from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable
from dataclasses import dataclass, field
from typing import Any

_WHITESPACE = re.compile(r"\s+")


@dataclass(frozen=True)
class Metric:
    key: str
    name: str
    numerator: int
    denominator: int
    definition: str

    @property
    def value(self) -> float | None:
        if self.denominator == 0:
            return None
        return self.numerator / self.denominator

    def as_dict(self) -> dict[str, Any]:
        return {
            "key": self.key,
            "name": self.name,
            "numerator": self.numerator,
            "denominator": self.denominator,
            "value": self.value,
            "display": self.display,
            "definition": self.definition,
        }

    @property
    def display(self) -> str:
        if self.denominator == 0:
            return "n/a (0/0)"
        return f"{self.numerator}/{self.denominator} = {self.value:.1%}"


@dataclass
class PageResult:
    page_id: str
    labelled_entries: int
    predicted_entries: int
    wrong_entries: int
    missed_errors: int
    balance_checked: bool = False
    balance_correct: bool = False
    fully_correct: bool = False
    missing_prediction: bool = False
    details: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class EvaluationReport:
    pages: int
    pages_missing_prediction: int
    labelled_entries: int
    predicted_entries: int
    metrics: list[Metric]
    page_results: list[PageResult]
    notes: list[str] = field(default_factory=list)

    def metric(self, key: str) -> Metric:
        for metric in self.metrics:
            if metric.key == key:
                return metric
        raise KeyError(key)

    def as_dict(self) -> dict[str, Any]:
        return {
            "pages": self.pages,
            "pages_missing_prediction": self.pages_missing_prediction,
            "labelled_entries": self.labelled_entries,
            "predicted_entries": self.predicted_entries,
            "metrics": [metric.as_dict() for metric in self.metrics],
            "page_results": [result.__dict__ for result in self.page_results],
            "notes": self.notes,
        }


def normalise_text(value: str | None) -> str:
    if value is None:
        return ""
    text = unicodedata.normalize("NFKC", value).casefold().strip()
    return _WHITESPACE.sub(" ", text)


def normalise_name(value: str | None) -> str:
    if value is None:
        return ""
    text = unicodedata.normalize("NFKC", value).casefold()
    return "".join(char for char in text if char.isalnum())


def effective_customer(page: dict[str, Any], entry: dict[str, Any]) -> str | None:
    entry_name = entry.get("customer_name") or {}
    if entry_name.get("value"):
        return entry_name["value"]
    page_name = page.get("customer_name") or {}
    if page_name.get("value"):
        return page_name["value"]
    return None


def _amounts(entry: dict[str, Any]) -> tuple[int | None, int | None]:
    return entry.get("debit_paise"), entry.get("credit_paise")


def _field_mismatches(
    label_page: dict[str, Any],
    label_entry: dict[str, Any],
    pred_page: dict[str, Any],
    pred_entry: dict[str, Any],
) -> list[str]:
    mismatches: list[str] = []
    if label_entry.get("date") != pred_entry.get("date"):
        mismatches.append("date")
    if _amounts(label_entry) != _amounts(pred_entry):
        mismatches.append("amount")
    if label_entry.get("entry_status") != pred_entry.get("entry_status"):
        mismatches.append("entry_status")
    if normalise_text(label_entry.get("description")) != normalise_text(
        pred_entry.get("description")
    ):
        mismatches.append("description")
    if normalise_name(effective_customer(label_page, label_entry)) != normalise_name(
        effective_customer(pred_page, pred_entry)
    ):
        mismatches.append("customer")
    return mismatches


def _is_flagged(pred_page: dict[str, Any], pred_entry: dict[str, Any]) -> bool:
    if pred_page.get("review_required") is True:
        return True
    return bool(pred_entry.get("issues"))


def _computed_balance(pred_page: dict[str, Any]) -> int:
    balance = pred_page.get("opening_balance_paise") or 0
    for entry in pred_page.get("entries") or []:
        if entry.get("entry_status") == "crossed_out":
            continue
        balance += (entry.get("debit_paise") or 0) - (entry.get("credit_paise") or 0)
    return int(balance)


def evaluate(
    labels: dict[str, dict[str, Any]],
    predictions: dict[str, dict[str, Any]],
    *,
    notes: Iterable[str] = (),
) -> EvaluationReport:
    """Score predictions against hand-labelled pages.

    Alignment is positional: the nth predicted entry is compared with the nth
    labelled entry. This is exact for reviewed label sets and avoids fuzzy
    matching hiding omissions. A missing or extra entry is counted as a wrong
    entry, never silently skipped.

    Definitions:
    - entry_extraction: all required fields (date, debit, credit, entry_status,
      description, effective customer) exact, after whitespace/case normalisation
      of text. The effective customer is the entry customer if present, otherwise
      the page customer.
    - amount_exact: debit and credit pair exact / labels with any non-null amount.
    - customer_matching: effective customer exact (punctuation-insensitive) /
      labels with a customer.
    - balance_reconciliation: computed balance (opening + non-crossed-out debits
      - credits from the prediction) equals the shopkeeper-verified closing
      balance / pages with a verified closing balance.
    - review_rate: predicted entries with issues or on a review_required page /
      predicted entries.
    - missed_error_rate: wrong entries that were not flagged / all wrong entries,
      including missing predictions and extra predictions. This is the key safety
      metric.
    """
    labels = {str(page_id): page for page_id, page in labels.items()}
    predictions = {str(page_id): page for page_id, page in predictions.items()}

    labelled_total = 0
    predicted_total = 0
    matched_correct = 0
    amount_denominator = 0
    amount_correct = 0
    customer_denominator = 0
    customer_correct = 0
    balance_denominator = 0
    balance_correct = 0
    review_denominator = 0
    review_numerator = 0
    wrong_total = 0
    missed_errors = 0
    description_denominator = 0
    description_correct = 0
    fully_correct_pages = 0
    missing_predictions = 0
    page_results: list[PageResult] = []

    for page_id, label_page in labels.items():
        label_entries = list(label_page.get("entries") or [])
        pred_page = predictions.get(page_id)
        pred_entries = list((pred_page or {}).get("entries") or [])
        labelled_total += len(label_entries)
        predicted_total += len(pred_entries)

        page_result = PageResult(
            page_id=page_id,
            labelled_entries=len(label_entries),
            predicted_entries=len(pred_entries),
            wrong_entries=0,
            missed_errors=0,
            missing_prediction=pred_page is None,
        )
        if pred_page is None:
            missing_predictions += 1

        if pred_page is not None:
            for entry in pred_entries:
                review_denominator += 1
                if _is_flagged(pred_page, entry):
                    review_numerator += 1

        page_has_extra = len(pred_entries) > len(label_entries)

        for index in range(max(len(label_entries), len(pred_entries))):
            label_entry = label_entries[index] if index < len(label_entries) else None
            pred_entry = pred_entries[index] if index < len(pred_entries) else None

            if label_entry is not None and pred_entry is not None:
                mismatches = _field_mismatches(label_page, label_entry, pred_page, pred_entry)
                flagged = _is_flagged(pred_page, pred_entry)

                if not mismatches:
                    matched_correct += 1
                else:
                    wrong_total += 1
                    page_result.wrong_entries += 1
                    if not flagged:
                        missed_errors += 1
                        page_result.missed_errors += 1
                    page_result.details.append(
                        {
                            "index": index,
                            "kind": "mismatch",
                            "fields": mismatches,
                            "flagged": flagged,
                            "label": label_entry,
                            "prediction": pred_entry,
                        }
                    )

                label_debit, label_credit = _amounts(label_entry)
                if label_debit is not None or label_credit is not None:
                    amount_denominator += 1
                    if _amounts(pred_entry) == (label_debit, label_credit):
                        amount_correct += 1

                label_customer = effective_customer(label_page, label_entry)
                if label_customer:
                    customer_denominator += 1
                    if normalise_name(label_customer) == normalise_name(
                        effective_customer(pred_page, pred_entry)
                    ):
                        customer_correct += 1

                if label_entry.get("description") is not None:
                    description_denominator += 1
                    if normalise_text(label_entry.get("description")) == normalise_text(
                        pred_entry.get("description")
                    ):
                        description_correct += 1

            elif label_entry is not None:
                wrong_total += 1
                missed_errors += 1
                page_result.wrong_entries += 1
                page_result.missed_errors += 1
                page_result.details.append(
                    {"index": index, "kind": "missing_prediction", "label": label_entry}
                )
                label_debit, label_credit = _amounts(label_entry)
                if label_debit is not None or label_credit is not None:
                    amount_denominator += 1
                if effective_customer(label_page, label_entry):
                    customer_denominator += 1
                if label_entry.get("description") is not None:
                    description_denominator += 1

            elif pred_entry is not None:
                wrong_total += 1
                page_result.wrong_entries += 1
                flagged = _is_flagged(pred_page, pred_entry)
                if not flagged:
                    missed_errors += 1
                    page_result.missed_errors += 1
                page_result.details.append(
                    {"index": index, "kind": "extra_prediction", "flagged": flagged, "prediction": pred_entry}
                )

        if label_page.get("closing_balance_paise") is not None:
            balance_denominator += 1
            page_result.balance_checked = True
            if pred_page is not None and _computed_balance(pred_page) == label_page["closing_balance_paise"]:
                balance_correct += 1
                page_result.balance_correct = True

        page_fully_correct = (
            pred_page is not None
            and page_result.wrong_entries == 0
            and not page_has_extra
        )
        if page_fully_correct:
            fully_correct_pages += 1
        page_result.fully_correct = page_fully_correct
        page_results.append(page_result)

    total_pages = len(labels)
    metrics = [
        Metric(
            "entry_extraction",
            "Entry extraction accuracy",
            matched_correct,
            labelled_total,
            "Entries with all required fields exactly correct / labelled entries.",
        ),
        Metric(
            "amount_exact",
            "Amount exact-match accuracy",
            amount_correct,
            amount_denominator,
            "Correct debit and credit pair / labelled entries with any amount.",
        ),
        Metric(
            "customer_matching",
            "Customer matching accuracy",
            customer_correct,
            customer_denominator,
            "Correct effective customer / labelled entries with a customer.",
        ),
        Metric(
            "balance_reconciliation",
            "Balance reconciliation accuracy",
            balance_correct,
            balance_denominator,
            "Computed page balance equals the verified closing balance / pages with a verified balance.",
        ),
        Metric(
            "review_rate",
            "Review rate",
            review_numerator,
            review_denominator,
            "Predicted entries flagged by an issue or page flag / predicted entries.",
        ),
        Metric(
            "missed_error_rate",
            "Missed-error rate",
            missed_errors,
            wrong_total,
            "Wrong entries that were not flagged / all wrong, missing, or extra entries.",
        ),
        Metric(
            "page_exact",
            "Page fully-correct rate",
            fully_correct_pages,
            total_pages,
            "Pages with every labelled entry correct and no extras / labelled pages.",
        ),
        Metric(
            "description_exact",
            "Description exact-match rate",
            description_correct,
            description_denominator,
            "Descriptions equal after whitespace/case normalisation / entries with a description.",
        ),
    ]

    return EvaluationReport(
        pages=total_pages,
        pages_missing_prediction=missing_predictions,
        labelled_entries=labelled_total,
        predicted_entries=predicted_total,
        metrics=metrics,
        page_results=page_results,
        notes=list(notes),
    )


def report_markdown(report: EvaluationReport, *, title: str = "KadaiBook extraction evaluation") -> str:
    lines = [
        f"# {title}",
        "",
        f"- Pages: {report.pages}",
        f"- Labeled entries: {report.labelled_entries}",
        f"- Predicted entries: {report.predicted_entries}",
        f"- Pages with no prediction: {report.pages_missing_prediction}",
        "",
    ]
    if report.notes:
        lines.extend(["> " + note for note in report.notes] + [""])
    lines.extend(
        [
            "| Metric | Result | Definition |",
            "| --- | --- | --- |",
        ]
    )
    for metric in report.metrics:
        lines.append(f"| {metric.name} | {metric.display} | {metric.definition} |")
    lines.extend(
        [
            "",
            "The missed-error rate is the safety metric: it counts wrong values that",
            "reached the reviewer without a warning, including missing and extra entries.",
            "Pilot results from a small label set describe that label set only.",
        ]
    )
    return "\n".join(lines) + "\n"
