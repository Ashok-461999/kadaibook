from __future__ import annotations

from app.schema import Entry, PageExtraction


def _add(code: str, bucket: list[str]) -> None:
    if code not in bucket:
        bucket.append(code)


def tag_issues(
    extraction: PageExtraction,
    *,
    layout_confidence: float,
    confidence_threshold: float = 0.5,
) -> PageExtraction:
    """Apply deterministic, explainable review rules to a validated extraction.

    These rules decide what the human must look at. They deliberately do not use
    a model's self-reported confidence as a probability of correctness.
    """
    page_issues = list(extraction.page_issues)

    if extraction.customer_name.status == "unreadable" or not extraction.customer_name.value:
        if extraction.layout_type != "date_wise":
            _add("missing_customer_name", page_issues)
    elif extraction.customer_name.status == "ambiguous":
        _add("ambiguous_customer", page_issues)
    if extraction.layout_type == "unknown":
        _add("unknown_layout", page_issues)
    if extraction.layout_type == "summary":
        _add("summary_layout", page_issues)
    if layout_confidence < confidence_threshold:
        _add("low_layout_confidence", page_issues)
    if not extraction.entries:
        _add("no_entries", page_issues)

    if extraction.entries and all(entry.running_balance_paise is None for entry in extraction.entries):
        _add("no_running_totals", page_issues)

    if extraction.layout_type == "date_wise":
        names = {
            (entry.customer_name.value or "").strip().casefold()
            for entry in extraction.entries
            if entry.customer_name and entry.customer_name.value
        }
        if len(names) > 1:
            _add("multiple_customers", page_issues)

    tagged_entries: list[Entry] = []
    for entry in extraction.entries:
        issues = list(entry.issues)

        if entry.date is None:
            _add("missing_date", issues)
        elif entry.date_status == "unreadable":
            _add("missing_date", issues)
        elif entry.date_status == "ambiguous":
            _add("ambiguous_date", issues)

        if entry.debit_paise is None and entry.credit_paise is None:
            _add("missing_amount", issues)
        if entry.debit_paise is not None and entry.credit_paise is not None:
            _add("both_debit_and_credit", issues)
        if entry.debit_paise == 0 or entry.credit_paise == 0:
            _add("zero_amount", issues)

        if entry.entry_status == "crossed_out":
            _add("crossed_out_entry", issues)
        elif entry.entry_status == "corrected":
            _add("corrected_entry", issues)
        elif entry.entry_status == "uncertain":
            _add("uncertain_entry", issues)

        if entry.customer_name:
            if entry.customer_name.status == "unreadable" or not entry.customer_name.value:
                _add("unreadable_customer", issues)
            elif entry.customer_name.status == "ambiguous":
                _add("ambiguous_customer", issues)
        elif extraction.layout_type == "date_wise":
            _add("missing_entry_customer", issues)

        tagged_entries.append(entry.model_copy(update={"issues": issues}))

    review_required = bool(page_issues) or any(entry.issues for entry in tagged_entries)
    return extraction.model_copy(
        update={
            "entries": tagged_entries,
            "page_issues": page_issues,
            "review_required": review_required,
        }
    )
