# Contracts

`extraction-schema-v1.0.json` is the single boundary contract between the AI
service and the Core API.

- The AI service validates every model result against this file (structurally,
  on top of Pydantic) before returning it.
- The Core API validates every response against the same file before it writes
  anything to PostgreSQL.
- Evaluation labels use the same shape, so predictions and ground truth need no
  conversion step.

Breaking changes require a new `schema_version` and a new file. There is no
silent schema drift: the Maven build and the Python image both bundle this
directory, and CI runs the contract tests on each side.

## Issue codes

Issue codes are short, stable, machine-readable strings. They are advisory for
the reviewer and are used by the evaluation harness to measure the review and
missed-error rates.

Entry-level codes produced or normalised by the pipeline:

| Code | Meaning |
| --- | --- |
| `missing_date` | No readable date |
| `ambiguous_date` | Date is partially legible |
| `missing_amount` | Neither debit nor credit could be read |
| `zero_amount` | Model returned a zero amount |
| `both_debit_and_credit` | Both sides have a value; direction is unclear |
| `crossed_out_entry` | Entry is struck through |
| `corrected_entry` | Entry has a visible correction |
| `uncertain_entry` | Model marked the entry uncertain |
| `unreadable_customer` | Customer name is unreadable |
| `ambiguous_customer` | Customer name is ambiguous |
| `missing_entry_customer` | Date-wise entry has no customer |
| `model_issue` | A model-reported issue was preserved |

Java adds deterministic reconciliation codes at read time:
`balance_mismatch`, `date_out_of_sequence`, `customer_unmatched`,
`customer_ambiguous`, `invalid_amount`. These are not stored as AI output.

Page-level codes:

| Code | Meaning |
| --- | --- |
| `unknown_layout` | Layout classifier did not recognise the page |
| `low_layout_confidence` | Classification below the configured threshold |
| `no_running_totals` | Page has no written running balance to cross-check |
| `no_entries` | No transactions were extracted |
| `missing_customer_name` | Page-level customer is absent or unreadable |
| `opening_balance_unreadable` | Opening balance exists but cannot be read |
| `multiple_customers` | Date-wise page contains several customers |
