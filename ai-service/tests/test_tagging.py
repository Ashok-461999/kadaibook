from __future__ import annotations

from app.schema import Entry, NameValue, PageExtraction
from app.tagging import tag_issues


def _entry(**overrides) -> Entry:
    base = {
        "entry_id": "e1",
        "date": "2024-11-01",
        "date_status": "readable",
        "description": "Sale",
        "debit_paise": 10000,
        "credit_paise": None,
        "entry_status": "active",
        "source_text": "sale 100",
        "issues": [],
        "running_balance_paise": 10000,
        "customer_name": None,
    }
    base.update(overrides)
    return Entry.model_validate(base)


def _page(**overrides) -> PageExtraction:
    base = {
        "page_id": "11111111-1111-4111-8111-111111111111",
        "layout_type": "customer_ledger",
        "customer_name": {"value": "Ravi", "status": "readable"},
        "opening_balance_paise": 0,
        "closing_balance_paise": 10000,
        "entries": [_entry()],
        "page_issues": [],
        "review_required": False,
    }
    base.update(overrides)
    return PageExtraction.model_validate(base)


def test_clean_page_does_not_require_review():
    tagged = tag_issues(_page(), layout_confidence=0.9)
    assert tagged.review_required is False
    assert tagged.entries[0].issues == []


def test_missing_and_ambiguous_fields_are_flagged():
    entry = _entry(date=None, date_status="unreadable", debit_paise=None, credit_paise=None)
    tagged = tag_issues(_page(entries=[entry]), layout_confidence=0.9)
    issues = set(tagged.entries[0].issues)
    assert {"missing_date", "missing_amount"} <= issues
    assert tagged.review_required is True


def test_both_amounts_and_zero_amounts_are_flagged():
    tagged = tag_issues(
        _page(entries=[_entry(debit_paise=500, credit_paise=500), _entry(entry_id="e2", debit_paise=0)]),
        layout_confidence=0.9,
    )
    assert "both_debit_and_credit" in tagged.entries[0].issues
    assert "zero_amount" in tagged.entries[1].issues


def test_crossed_out_and_corrected_entries_are_preserved_and_flagged():
    crossed = _entry(entry_status="crossed_out")
    corrected = _entry(entry_id="e2", entry_status="corrected")
    tagged = tag_issues(_page(entries=[crossed, corrected]), layout_confidence=0.9)
    assert "crossed_out_entry" in tagged.entries[0].issues
    assert "corrected_entry" in tagged.entries[1].issues
    assert [e.entry_status for e in tagged.entries] == ["crossed_out", "corrected"]


def test_missing_running_totals_are_a_page_issue():
    tagged = tag_issues(_page(entries=[_entry(running_balance_paise=None)]), layout_confidence=0.9)
    assert "no_running_totals" in tagged.page_issues
    assert tagged.review_required is True


def test_date_wise_requires_entry_customer_and_notes_multiple_customers():
    entries = [
        _entry(customer_name={"value": "Ravi", "status": "readable"}),
        _entry(entry_id="e2", customer_name={"value": "Meena", "status": "readable"}),
    ]
    tagged = tag_issues(
        _page(
            layout_type="date_wise",
            customer_name={"value": None, "status": "unreadable"},
            entries=entries,
        ),
        layout_confidence=0.9,
    )
    assert "multiple_customers" in tagged.page_issues
    assert "missing_customer_name" not in tagged.page_issues


def test_date_wise_entry_without_customer_is_flagged():
    tagged = tag_issues(
        _page(
            layout_type="date_wise",
            customer_name={"value": None, "status": "unreadable"},
            entries=[_entry(customer_name=None)],
        ),
        layout_confidence=0.9,
    )
    assert "missing_entry_customer" in tagged.entries[0].issues


def test_unknown_layout_and_low_confidence_are_page_issues():
    tagged = tag_issues(
        _page(layout_type="unknown", entries=[]), layout_confidence=0.2
    )
    assert {"unknown_layout", "low_layout_confidence", "no_entries"} <= set(tagged.page_issues)
    assert tagged.review_required is True


def test_ambiguous_page_customer_is_flagged():
    tagged = tag_issues(
        _page(customer_name=NameValue(value="Ravi", status="ambiguous")),
        layout_confidence=0.9,
    )
    assert "ambiguous_customer" in tagged.page_issues


def test_issues_are_deduplicated():
    entry = _entry(issues=["missing_date", "missing_date"])
    tagged = tag_issues(_page(entries=[entry]), layout_confidence=0.9)
    assert tagged.entries[0].issues.count("missing_date") == 1
