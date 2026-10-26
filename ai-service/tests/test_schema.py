from __future__ import annotations

import copy

import pytest

from app.contract import ContractViolation, validate_contract, validate_model_output
from app.schema import PageExtraction


def test_mock_fixture_satisfies_pydantic_and_contract(mock_fixture, schema_path):
    extraction = PageExtraction.model_validate(mock_fixture)
    assert extraction.schema_version == "1.0"
    assert extraction.currency == "INR"
    validate_contract(extraction.model_dump(mode="json"), schema_path)


def test_unknown_currency_is_rejected(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    payload["currency"] = "USD"
    with pytest.raises(ContractViolation):
        validate_model_output(payload, schema_path)


def test_non_integer_paise_is_rejected(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    payload["entries"][0]["debit_paise"] = 1200.5
    with pytest.raises(ContractViolation):
        validate_model_output(payload, schema_path)


def test_extra_fields_are_rejected(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    payload["entries"][0]["invented_field"] = "x"
    with pytest.raises(ContractViolation):
        validate_model_output(payload, schema_path)


def test_page_id_must_be_uuid_for_contract(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    payload["page_id"] = "not-a-uuid"
    with pytest.raises(ContractViolation):
        validate_model_output(payload, schema_path)


def test_issue_codes_are_normalised(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    payload["entries"][0]["issues"] = ["Missing Date!", "OCR/LOW"]
    extraction = validate_model_output(payload, schema_path)
    assert extraction.entries[0].issues == ["missing_date", "ocr_low"]


def test_negative_amount_is_rejected(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    payload["entries"][0]["debit_paise"] = -1
    with pytest.raises(ContractViolation):
        validate_model_output(payload, schema_path)


def test_missing_required_field_is_rejected(mock_fixture, schema_path):
    payload = copy.deepcopy(mock_fixture)
    del payload["closing_balance_paise"]
    with pytest.raises(ContractViolation):
        validate_model_output(payload, schema_path)
