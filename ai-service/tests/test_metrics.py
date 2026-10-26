from __future__ import annotations

from evals.metrics import evaluate, report_markdown


def _entry(
    *,
    date="2024-11-01",
    description="Cement",
    debit=100000,
    credit=None,
    status="active",
    issues=None,
    customer_name=None,
    running=200000,
    entry_id="e1",
):
    return {
        "entry_id": entry_id,
        "date": date,
        "date_status": "readable",
        "description": description,
        "debit_paise": debit,
        "credit_paise": credit,
        "entry_status": status,
        "source_text": description,
        "issues": issues or [],
        "running_balance_paise": running,
        "customer_name": customer_name,
    }


def _page(page_id, entries, *, opening=100000, closing=150000, customer="Ravi", review=False, issues=None):
    return {
        "schema_version": "1.0",
        "page_id": page_id,
        "layout_type": "customer_ledger",
        "currency": "INR",
        "customer_name": {"value": customer, "status": "readable" if customer else "unreadable"},
        "opening_balance_paise": opening,
        "closing_balance_paise": closing,
        "entries": entries,
        "page_issues": issues or [],
        "review_required": review,
    }


def _labels():
    return {
        "a": _page(
            "a",
            [
                _entry(entry_id="a1", debit=100000, running=200000),
                _entry(entry_id="a2", description="Cash", debit=None, credit=50000, running=150000),
            ],
            closing=150000,
        ),
        "b": _page(
            "b",
            [_entry(entry_id="b1", debit=70000, running=70000, description="Paint")],
            opening=0,
            closing=70000,
            customer="Meena",
        ),
    }


def test_reports_numerator_and_denominator_and_missed_errors():
    labels = _labels()
    predictions = {
        "a": _page(
            "a",
            [
                _entry(entry_id="a1", debit=100000, running=200000),
                _entry(entry_id="a2", description="Cash", debit=None, credit=60000, running=140000),
            ],
            closing=150000,
        )
    }

    report = evaluate(labels, predictions)

    assert report.pages == 2
    assert report.pages_missing_prediction == 1
    assert report.metric("entry_extraction").display == "1/3 = 33.3%"
    assert report.metric("amount_exact").display == "1/3 = 33.3%"
    assert report.metric("customer_matching").display == "2/3 = 66.7%"
    assert report.metric("balance_reconciliation").display == "0/2 = 0.0%"
    assert report.metric("review_rate").display == "0/2 = 0.0%"
    assert report.metric("missed_error_rate").display == "2/2 = 100.0%"
    assert report.metric("page_exact").display == "0/2 = 0.0%"
    assert report.metric("description_exact").display == "2/3 = 66.7%"


def test_flags_reduce_missed_error_rate():
    labels = _labels()
    predictions = {
        "a": _page(
            "a",
            [
                _entry(entry_id="a1", debit=100000, running=200000),
                _entry(
                    entry_id="a2",
                    description="Cash",
                    debit=None,
                    credit=60000,
                    running=140000,
                    issues=["amount_uncertain"],
                ),
            ],
            closing=150000,
            review=True,
        )
    }

    report = evaluate(labels, predictions)
    assert report.metric("review_rate").display == "2/2 = 100.0%"
    assert report.metric("missed_error_rate").display == "1/2 = 50.0%"


def test_extra_prediction_is_a_wrong_unflagged_entry():
    labels = {
        "a": _page(
            "a",
            [_entry(entry_id="a1", debit=100000, running=200000)],
            closing=200000,
        )
    }
    predictions = {
        "a": _page(
            "a",
            [
                _entry(entry_id="a1", debit=100000, running=200000),
                _entry(entry_id="extra", debit=5000, running=205000),
            ],
            closing=200000,
        )
    }
    report = evaluate(labels, predictions)
    assert report.metric("entry_extraction").display == "1/1 = 100.0%"
    assert report.metric("page_exact").display == "0/1 = 0.0%"
    assert report.metric("missed_error_rate").display == "1/1 = 100.0%"


def test_crossed_out_entries_do_not_count_in_computed_balance():
    labels = {
        "a": _page(
            "a",
            [_entry(entry_id="a1", debit=100000, running=200000)],
            closing=200000,
        )
    }
    predictions = {
        "a": _page(
            "a",
            [
                _entry(entry_id="a1", debit=100000, running=200000),
                _entry(entry_id="x", debit=90000, status="crossed_out", running=200000),
            ],
            closing=200000,
            review=True,
        )
    }
    report = evaluate(labels, predictions)
    assert report.metric("balance_reconciliation").display == "1/1 = 100.0%"


def test_customer_names_are_normalised_for_matching():
    labels = {
        "a": _page(
            "a",
            [_entry(customer_name={"value": "Ravi  Kumar", "status": "readable"})],
            closing=200000,
            customer="Ravi Kumar",
        )
    }
    predictions = {
        "a": _page(
            "a",
            [_entry(customer_name={"value": "RAVI-KUMAR", "status": "readable"})],
            closing=200000,
            customer="Ravi Kumar",
        )
    }
    report = evaluate(labels, predictions)
    assert report.metric("customer_matching").display == "1/1 = 100.0%"


def test_markdown_contains_all_metrics():
    report = evaluate(_labels(), {})
    markdown = report_markdown(report, title="Test")
    assert "Missed-error rate" in markdown
    assert "0/3" in markdown
    assert "n/a (0/0)" in markdown
