import { useEffect, useState } from "react";
import { formatDate, plainRupees, rupees, statusLabel } from "../format";
import type {
  Customer,
  EntryStatus,
  PageDetail,
  PageEntry,
  ReviewRequest,
} from "../types";

interface Props {
  entry: PageEntry;
  page: PageDetail;
  customers: Customer[];
  disabled: boolean;
  saving: boolean;
  onReview: (body: ReviewRequest) => Promise<void>;
  onCreateCustomer: (name: string) => Promise<Customer>;
}

type Mode = "correct" | "reject" | null;

export default function EntryCard({
  entry,
  page,
  customers,
  disabled,
  saving,
  onReview,
  onCreateCustomer,
}: Props) {
  const [mode, setMode] = useState<Mode>(null);
  const [date, setDate] = useState(entry.effectiveDate ?? entry.date ?? "");
  const [description, setDescription] = useState(
    entry.effectiveDescription ?? entry.description ?? "",
  );
  const [debit, setDebit] = useState(amountInput(entry.effectiveDebitPaise ?? entry.debitPaise));
  const [credit, setCredit] = useState(amountInput(entry.effectiveCreditPaise ?? entry.creditPaise));
  const [customerId, setCustomerId] = useState(
    entry.effectiveCustomerId ?? entry.customerMatch.candidates[0]?.id ?? "",
  );
  const [entryStatus, setEntryStatus] = useState<EntryStatus>(
    entry.effectiveEntryStatus === "rejected" ? "active" : entry.effectiveEntryStatus,
  );
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [showNewCustomer, setShowNewCustomer] = useState(false);
  const [newCustomerName, setNewCustomerName] = useState("");
  const [creatingCustomer, setCreatingCustomer] = useState(false);

  useEffect(() => {
    setDate(entry.effectiveDate ?? entry.date ?? "");
    setDescription(entry.effectiveDescription ?? entry.description ?? "");
    setDebit(amountInput(entry.effectiveDebitPaise ?? entry.debitPaise));
    setCredit(amountInput(entry.effectiveCreditPaise ?? entry.creditPaise));
    setCustomerId(entry.effectiveCustomerId ?? entry.customerMatch.candidates[0]?.id ?? "");
    setMode(null);
  }, [
    entry.review?.decidedAt,
    entry.effectiveDate,
    entry.effectiveDebitPaise,
    entry.effectiveCreditPaise,
    entry.effectiveCustomerId,
    entry.date,
    entry.debitPaise,
    entry.creditPaise,
    entry.customerMatch.candidates,
  ]);

  const reviewed = entry.review;
  const imported = page.status === "imported";
  const canReview = !disabled && !imported;
  const activeCustomer = customers.find((customer) => customer.id === customerId);

  async function quickConfirm() {
    setError(null);
    try {
      await onReview({
        action: "confirmed",
        customerId: customerId || null,
        note: note.trim() || null,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not confirm this entry.");
    }
  }

  async function submitCorrection() {
    setError(null);
    const debitPaise = parseAmount(debit);
    const creditPaise = parseAmount(credit);
    if (!date) {
      setError("Enter the date written on the page.");
      return;
    }
    if (!description.trim()) {
      setError("Enter a description.");
      return;
    }
    if (debitPaise === null || creditPaise === null) {
      setError("Enter amounts as numbers with up to two decimal places.");
      return;
    }
    if (debitPaise === 0 && creditPaise === 0) {
      setError("Enter a debit or a credit amount greater than zero.");
      return;
    }
    try {
      await onReview({
        action: "corrected",
        date,
        dateStatus: "readable",
        description: description.trim(),
        debitPaise,
        creditPaise,
        entryStatus,
        customerId: customerId || null,
        note: note.trim() || null,
      });
      setMode(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the correction.");
    }
  }

  async function submitRejection() {
    setError(null);
    try {
      await onReview({ action: "rejected", note: note.trim() || null });
      setMode(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not reject this entry.");
    }
  }

  async function createCustomer() {
    if (!newCustomerName.trim()) return;
    setCreatingCustomer(true);
    try {
      const created = await onCreateCustomer(newCustomerName.trim());
      setCustomerId(created.id);
      setShowNewCustomer(false);
      setNewCustomerName("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add the customer.");
    } finally {
      setCreatingCustomer(false);
    }
  }

  const rowClass = [
    "entry-card",
    entry.entryStatus === "crossed_out" ? "crossed" : "",
    reviewed ? "reviewed" : "",
    entry.importable ? "" : "not-importable",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <article className={rowClass} id={`entry-${entry.index}`}>
      <div className="entry-main">
        <div className="entry-index">#{entry.index + 1}</div>
        <div className="entry-body">
          <div className="entry-title-row">
            <h3 className={entry.entryStatus === "crossed_out" ? "strike" : ""}>
              {entry.effectiveDescription || entry.description || "Description unreadable"}
            </h3>
            <div className="entry-amounts">
              {entry.effectiveDebitPaise ? (
                <span className="debit">− {rupees(entry.effectiveDebitPaise)}</span>
              ) : null}
              {entry.effectiveCreditPaise ? (
                <span className="credit">+ {rupees(entry.effectiveCreditPaise)}</span>
              ) : null}
              {!entry.effectiveDebitPaise && !entry.effectiveCreditPaise && (
                <span className="amount-missing">amount missing</span>
              )}
            </div>
          </div>

          <div className="entry-meta">
            <span>{formatDate(entry.effectiveDate || entry.date)}</span>
            <span className="dot">·</span>
            <span className={entry.effectiveCustomerId ? "" : "warn-text"}>
              {entry.effectiveCustomerName || entry.customerName || "customer not identified"}
            </span>
            {entry.effectiveDateStatus !== "readable" && (
              <span className="soft-badge warn">{statusLabel(entry.effectiveDateStatus)} date</span>
            )}
            {entry.effectiveEntryStatus !== "active" && (
              <span className="soft-badge">{statusLabel(entry.effectiveEntryStatus)}</span>
            )}
            {reviewed && (
              <span className={`soft-badge review-${reviewed.action}`}>
                {statusLabel(reviewed.action)}
              </span>
            )}
          </div>

          {entry.sourceText && (
            <p className="source-text">
              <span>From the page</span> “{entry.sourceText}”
            </p>
          )}

          {entry.issues.length > 0 && (
            <div className="entry-issues">
              {entry.issues.map((issue) => (
                <span key={issue} className="issue-chip">
                  {statusLabel(issue)}
                </span>
              ))}
            </div>
          )}

          {entry.customerMatch.status !== "NONE" && !entry.effectiveCustomerId && (
            <p className="match-note">
              {entry.customerMatch.status === "AMBIGUOUS" ? "Customer is ambiguous." : "Suggested customer:"}{" "}
              {entry.customerMatch.candidates.map((candidate) => candidate.displayName).join(", ")}
            </p>
          )}

          {entry.runningBalancePaise !== null && entry.runningBalancePaise !== undefined && (
            <div className="entry-balance">
              <span>Running balance on page: {rupees(entry.runningBalancePaise)}</span>
              <span>KadaiBook calculated: {rupees(entry.calculatedBalancePaise)}</span>
              {entry.balanceMismatch && <em>mismatch</em>}
            </div>
          )}

          {reviewed?.note && <p className="review-note">Note: {reviewed.note}</p>}
          {!entry.importable && (
            <p className="not-imported-note">This row will not be imported into the khata.</p>
          )}
          {error && <div className="form-error">{error}</div>}

          {mode === "correct" && (
            <div className="correction-form">
              <div className="field-grid">
                <label>
                  Date
                  <input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
                </label>
                <label>
                  Description
                  <input
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    maxLength={500}
                  />
                </label>
                <label>
                  Debit ₹ (customer owes)
                  <input
                    value={debit}
                    onChange={(event) => setDebit(event.target.value)}
                    inputMode="decimal"
                    placeholder="0.00"
                  />
                </label>
                <label>
                  Credit ₹ (payment)
                  <input
                    value={credit}
                    onChange={(event) => setCredit(event.target.value)}
                    inputMode="decimal"
                    placeholder="0.00"
                  />
                </label>
                <label>
                  Customer
                  <select
                    value={customerId}
                    onChange={(event) => setCustomerId(event.target.value)}
                  >
                    <option value="">Choose a customer…</option>
                    {customers.map((customer) => (
                      <option key={customer.id} value={customer.id}>
                        {customer.displayName}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Row status
                  <select
                    value={entryStatus}
                    onChange={(event) => setEntryStatus(event.target.value as EntryStatus)}
                  >
                    <option value="active">Active</option>
                    <option value="uncertain">Uncertain but import</option>
                    <option value="corrected">Corrected</option>
                  </select>
                </label>
              </div>
              <p className="fine-print">
                Enter both debit and credit when one row shows a sale and a part payment.
                Leaving the customer empty creates one from the name written on the page.
              </p>
              {showNewCustomer ? (
                <div className="inline-customer">
                  <input
                    value={newCustomerName}
                    onChange={(event) => setNewCustomerName(event.target.value)}
                    placeholder="New customer name"
                  />
                  <button
                    className="button small"
                    onClick={() => void createCustomer()}
                    disabled={creatingCustomer || !newCustomerName.trim()}
                    type="button"
                  >
                    {creatingCustomer ? "Adding…" : "Add"}
                  </button>
                  <button
                    className="link-button"
                    onClick={() => setShowNewCustomer(false)}
                    type="button"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button className="link-button" onClick={() => setShowNewCustomer(true)} type="button">
                  + Customer not in the list
                </button>
              )}
              <label>
                Review note (optional)
                <input
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  maxLength={2000}
                  placeholder="Why did you change this row?"
                />
              </label>
              <div className="button-row">
                <button
                  className="button primary"
                  onClick={() => void submitCorrection()}
                  disabled={saving}
                  type="button"
                >
                  {saving ? "Saving…" : "Save correction"}
                </button>
                <button className="button ghost" onClick={() => setMode(null)} type="button">
                  Cancel
                </button>
              </div>
            </div>
          )}

          {mode === "reject" && (
            <div className="correction-form">
              <p className="muted">
                Rejected rows stay visible for the audit trail but are excluded from the khata.
              </p>
              <label>
                Reason (optional)
                <input
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="e.g. duplicate row"
                  maxLength={2000}
                />
              </label>
              <div className="button-row">
                <button
                  className="button danger"
                  onClick={() => void submitRejection()}
                  disabled={saving}
                  type="button"
                >
                  {saving ? "Rejecting…" : "Reject this row"}
                </button>
                <button className="button ghost" onClick={() => setMode(null)} type="button">
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>

        {canReview && (
          <div className="entry-actions">
            {entry.importable && (
              <button
                className="button small primary"
                onClick={() => void quickConfirm()}
                disabled={saving || Boolean(mode)}
              >
                {reviewed?.action === "confirmed" ? "Re-confirm" : "Confirm as read"}
              </button>
            )}
            <button
              className="button small"
              onClick={() => setMode(mode === "correct" ? null : "correct")}
              disabled={saving}
            >
              Correct
            </button>
            <button
              className="button small danger-ghost"
              onClick={() => setMode(mode === "reject" ? null : "reject")}
              disabled={saving}
            >
              Reject
            </button>
            {activeCustomer && (
              <span className="action-customer" title="Customer for this row">
                {activeCustomer.displayName}
              </span>
            )}
          </div>
        )}
      </div>
    </article>
  );
}

function amountInput(paise: number | undefined | null): string {
  if (paise === null || paise === undefined) return "";
  return plainRupees(paise);
}

function parseAmount(input: string): number | null {
  const text = input.trim().replace(/,/g, "");
  if (!text) return 0;
  if (!/^\d+(\.\d{0,2})?$/.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  return Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
}
