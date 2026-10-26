import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { api } from "../api";
import {
  formatBytes,
  formatDate,
  formatDateTime,
  plainRupees,
  rupees,
  statusLabel,
} from "../format";
import type { Blocker, Customer, PageDetail, ReviewRequest } from "../types";
import EntryCard from "./EntryCard";
import Modal from "./Modal";

interface Props {
  shopId: string;
  pageId: string;
  customers: Customer[];
  onBack: () => void;
  onCustomersChanged: () => void;
  onCreateCustomer: (name: string) => Promise<Customer>;
  onNewPage: () => void;
}

export default function PageWorkspace({
  shopId,
  pageId,
  customers,
  onBack,
  onCustomersChanged,
  onCreateCustomer,
  onNewPage,
}: Props) {
  const [detail, setDetail] = useState<PageDetail | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showCustomerModal, setShowCustomerModal] = useState(false);
  const [zoom, setZoom] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await api.page(shopId, pageId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load this page.");
    } finally {
      setLoading(false);
    }
  }, [shopId, pageId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let active = true;
    let url: string | null = null;
    api
      .image(shopId, pageId)
      .then((next) => {
        url = next;
        if (active) setImageUrl(next);
        else URL.revokeObjectURL(next);
      })
      .catch(() => {
        if (active) setImageUrl(null);
      });
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
      setImageUrl(null);
    };
  }, [shopId, pageId]);

  async function extract() {
    setBusy("extract");
    setError(null);
    try {
      setDetail(await api.extract(shopId, pageId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Extraction failed. Try again.");
      await load();
    } finally {
      setBusy(null);
    }
  }

  async function review(entryId: string, body: ReviewRequest) {
    setBusy(`review:${entryId}`);
    setError(null);
    setNotice(null);
    try {
      setDetail(await api.review(shopId, pageId, entryId, body));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the review.");
      throw err;
    } finally {
      setBusy(null);
    }
  }

  async function bulkConfirm(options: { customerName?: string; date?: string } = {}) {
    if (!detail) return;
    const customerName = options.customerName?.trim() || undefined;
    const date = options.date || undefined;
    const unreviewedBefore = detail.entries.filter((entry) => !entry.review).length;
    const unlinkedBefore = detail.entries.filter((entry) => !entry.effectiveCustomerId).length;
    const undatedBefore = detail.entries.filter(
      (entry) => !entry.review && !entry.effectiveDate && entry.effectiveEntryStatus !== "rejected",
    ).length;
    setBusy(customerName ? "bulk-assign" : "bulk-confirm");
    setError(null);
    setNotice(null);
    try {
      const next = await api.bulkConfirm(shopId, pageId, {
        ...(customerName ? { customerName } : {}),
        ...(date ? { date } : {}),
      });
      setDetail(next);
      onCustomersChanged();
      const confirmed = unreviewedBefore - next.entries.filter((entry) => !entry.review).length;
      const linked = unlinkedBefore - next.entries.filter((entry) => !entry.effectiveCustomerId).length;
      const dated =
        undatedBefore -
        next.entries.filter(
          (entry) => !entry.review && !entry.effectiveDate && entry.effectiveEntryStatus !== "rejected",
        ).length;
      if (confirmed === 0 && linked === 0 && dated === 0) {
        setNotice(
          "Nothing could be cleared automatically. Add a customer or set a date for the flagged rows, then try again.",
        );
      } else {
        const parts: string[] = [];
        if (confirmed > 0) {
          parts.push(`Confirmed ${confirmed} row${confirmed === 1 ? "" : "s"}`);
        }
        if (dated > 0) {
          parts.push(`set the date on ${dated} row${dated === 1 ? "" : "s"}`);
        }
        if (linked > 0) {
          parts.push(
            `linked ${linked} row${linked === 1 ? "" : "s"}${customerName ? ` to ${customerName}` : ""}`,
          );
        }
        setNotice(parts.join(", ") + ".");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not apply the bulk review.");
    } finally {
      setBusy(null);
    }
  }

  async function confirm(confirmations: { customerId: string; confirmedBalancePaise: number }[]) {
    setBusy("confirm");
    setError(null);
    try {
      setDetail(await api.confirm(shopId, pageId, confirmations));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not import this page.");
    } finally {
      setBusy(null);
    }
  }

  async function remove() {
    if (!window.confirm("Delete this page, its image, and its extracted rows? This cannot be undone.")) {
      return;
    }
    setBusy("delete");
    try {
      await api.deletePage(shopId, pageId);
      onBack();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete this page.");
      setBusy(null);
    }
  }

  async function addCustomer(name: string) {
    const created = await onCreateCustomer(name);
    onCustomersChanged();
    return created;
  }

  const pendingCount = useMemo(
    () => detail?.entries.filter((entry) => !entry.review && entry.effectiveEntryStatus !== "rejected").length ?? 0,
    [detail],
  );

  if (loading) {
    return (
      <div className="workspace-loading">
        <div className="spinner dark" />
        <p>Opening the page…</p>
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="workspace-loading">
        <p className="form-error">{error || "Page not found."}</p>
        <button className="button" onClick={onBack}>
          Back to pages
        </button>
      </div>
    );
  }

  return (
    <div className="workspace">
      <header className="workspace-header">
        <button className="back-button" onClick={onBack}>
          ← Pages
        </button>
        <div className="workspace-title">
          <h1>{detail.customerName || detail.originalFilename}</h1>
          <div className="title-meta">
            <span className={`status-badge ${detail.status}`}>{statusLabel(detail.status)}</span>
            {detail.layoutType && <span className="soft-badge">{statusLabel(detail.layoutType)}</span>}
            <span>{formatDate(detail.createdAt)}</span>
            <span>{formatBytes(detail.sizeBytes)}</span>
          </div>
        </div>
        <div className="workspace-actions">
          {detail.canExtract && (
            <button className="button primary" onClick={() => void extract()} disabled={Boolean(busy)}>
              {busy === "extract" ? "Reading…" : detail.status === "extraction_failed" ? "Retry reading" : "Read with AI"}
            </button>
          )}
          {detail.status === "needs_review" && (
            <a className="button ghost" href="#review-queue">
              {pendingCount > 0 ? `${pendingCount} to review` : "Review entries"}
            </a>
          )}
          {detail.status !== "imported" && (
            <button className="button danger-ghost" onClick={() => void remove()} disabled={Boolean(busy)}>
              Delete
            </button>
          )}
        </div>
      </header>

      {error && <div className="notice error">{error}</div>}
      {notice && <div className="notice success">{notice}</div>}
      {detail.status === "extracting" && (
        <div className="notice">
          This page is being read right now. Refresh in a moment if it does not update automatically.
        </div>
      )}

      <div className="workspace-grid">
        <section className="image-panel">
          <div className="image-card">
            <div className="image-toolbar">
              <span>Original notebook page</span>
              {imageUrl && (
                <div className="image-tools">
                  <button className="link-button" onClick={() => setZoom(true)}>
                    Zoom
                  </button>
                  <a className="link-button" href={imageUrl} target="_blank" rel="noreferrer">
                    Open
                  </a>
                </div>
              )}
            </div>
            {imageUrl ? (
              <img className="page-photo" src={imageUrl} alt={`Notebook page ${detail.originalFilename}`} />
            ) : (
              <div className="image-missing">
                <span className="missing-icon">▧</span>
                <h3>Image not available</h3>
                <p>
                  {detail.imageAvailable
                    ? "The image could not be loaded. Refresh the page."
                    : "The original image has been removed by the retention policy. Imported ledger rows are unaffected."}
                </p>
              </div>
            )}
          </div>
          <div className="image-note">
            Evidence only. Correct anything that does not match the page before importing.
          </div>
        </section>

        <section className="detail-panel">
          <SummaryCard detail={detail} />

          <BlockersCard detail={detail} busy={busy} onBulkConfirm={bulkConfirm} />

          {detail.canConfirm && (
            <ConfirmCard detail={detail} busy={busy === "confirm"} onConfirm={confirm} />
          )}

          {detail.status === "imported" && (
            <div className="imported-card">
              <span className="imported-tick">✓</span>
              <div>
                <h2>Imported to the khata</h2>
                <p>
                  Imported {formatDateTime(detail.importedAt)}.{" "}
                  {detail.confirmations.length > 0
                    ? detail.confirmations
                        .map((item) => `${item.customerName}: ${rupees(item.confirmedBalancePaise)}`)
                        .join(" · ")
                    : "Balances confirmed."}
                </p>
                <div className="button-row">
                  <button className="button" onClick={onNewPage}>
                    Back to pages
                  </button>
                  <a className="button ghost" href="#confirmations">
                    View confirmations
                  </a>
                </div>
              </div>
            </div>
          )}

          <section className="entries-section" id="review-queue">
            <div className="section-title">
              <div>
                <p className="eyebrow">Extracted rows</p>
                <h2>
                  {detail.entries.length} {detail.entries.length === 1 ? "entry" : "entries"}
                </h2>
              </div>
              {detail.status === "needs_review" && (
                <span className="review-counter">{pendingCount} awaiting you</span>
              )}
            </div>

            {detail.entries.length === 0 ? (
              <div className="empty-state">
                <h3>No rows on this page</h3>
                <p>
                  {detail.status === "uploaded"
                    ? "Read the page with AI to extract its rows."
                    : "The extraction found no transaction rows. Check the image or delete this page."}
                </p>
              </div>
            ) : (
              <div className="entry-list">
                {detail.entries.map((entry) => (
                  <EntryCard
                    key={entry.id}
                    entry={entry}
                    page={detail}
                    customers={customers}
                    disabled={detail.status !== "needs_review"}
                    saving={busy === `review:${entry.id}`}
                    onReview={(body) => review(entry.id, body)}
                    onCreateCustomer={addCustomer}
                  />
                ))}
              </div>
            )}
          </section>

          {detail.status === "needs_review" && (
            <button className="link-button add-customer-link" onClick={() => setShowCustomerModal(true)}>
              + Add a customer to this shop
            </button>
          )}
        </section>
      </div>

      {showCustomerModal && (
        <Modal title="Add a customer" onClose={() => setShowCustomerModal(false)}>
          <CustomerForm
            onCreated={async (name) => {
              await addCustomer(name);
              setShowCustomerModal(false);
            }}
          />
        </Modal>
      )}

      {zoom && imageUrl && (
        <div className="lightbox" onClick={() => setZoom(false)}>
          <img src={imageUrl} alt="Notebook page enlarged" />
          <button className="lightbox-close" onClick={() => setZoom(false)} aria-label="Close">
            ×
          </button>
        </div>
      )}
    </div>
  );
}

function SummaryCard({ detail }: { detail: PageDetail }) {
  return (
    <section className="summary-card">
      <div className="summary-top">
        <div>
          <p className="eyebrow">Page summary</p>
          <h2>{detail.customerName || "Customer not identified"}</h2>
          {detail.customerStatus && detail.customerStatus !== "readable" && (
            <span className="warn-text">Name marked {detail.customerStatus}</span>
          )}
        </div>
        {detail.run && (
          <div className="run-chip" title={`Prompt ${detail.run.promptVersion ?? "—"}`}>
            <span>{detail.run.model || "AI"}</span>
            <small>{(detail.run.latencyMs / 1000).toFixed(1)}s · {detail.run.attempts} attempt(s)</small>
          </div>
        )}
      </div>

      <div className="balance-strip">
        <div>
          <small>Opening written</small>
          <strong>{rupees(detail.openingBalancePaise)}</strong>
        </div>
        <div>
          <small>Closing written</small>
          <strong>{rupees(detail.closingBalancePaise)}</strong>
        </div>
        <div>
          <small>Rows</small>
          <strong>{detail.entries.length}</strong>
        </div>
        <div>
          <small>Layout</small>
          <strong>{detail.layoutType ? statusLabel(detail.layoutType) : "—"}</strong>
        </div>
      </div>

      {detail.pageIssues.length > 0 && (
        <div className="issue-strip">
          {detail.pageIssues.map((issue) => (
            <span key={issue} className="issue-chip">
              {statusLabel(issue)}
            </span>
          ))}
        </div>
      )}

      {detail.customerPreviews.length > 0 && (
        <div className="preview-list">
          {detail.customerPreviews.map((preview) => (
            <div key={preview.customerId} className="preview-row">
              <div>
                <strong>{preview.displayName}</strong>
                <small>
                  {preview.entryCount} rows
                  {preview.openingImported ? " · opening included" : ""}
                </small>
              </div>
              <div className="preview-money">
                <span>Existing {rupees(preview.existingBalancePaise)}</span>
                <strong>{rupees(preview.calculatedBalancePaise)}</strong>
                {preview.closingMismatch && <em>written closing ≠ calculated</em>}
              </div>
            </div>
          ))}
        </div>
      )}

      {detail.warnings.length > 0 && (
        <ul className="warning-list">
          {detail.warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function BlockersCard({
  detail,
  busy,
  onBulkConfirm,
}: {
  detail: PageDetail;
  busy: string | null;
  onBulkConfirm: (options?: { customerName?: string; date?: string }) => void;
}) {
  const [pageCustomer, setPageCustomer] = useState("");
  const [pageDate, setPageDate] = useState("");
  if (detail.status === "imported") return null;
  const actionable = detail.blockers.filter(
    (blocker) => blocker.code !== "confirmation_missing" && blocker.code !== "confirmation_mismatch",
  );
  const confirmation = detail.blockers.filter(
    (blocker) => blocker.code === "confirmation_missing" || blocker.code === "confirmation_mismatch",
  );

  if (actionable.length === 0) {
    if (detail.status !== "needs_review") return null;
    return (
      <div className="checks-card complete">
        <span className="check-icon">�o"</span>
        <div>
          <h3>All entry checks passed</h3>
          <p>
            {detail.canConfirm
              ? "Confirm each customer balance below to import this page."
              : "No importable rows yet. Review or correct the extracted rows."}
          </p>
        </div>
      </div>
    );
  }

  const groups = groupBlockers(actionable);
  const unreviewed = detail.entries.filter((entry) => !entry.review).length;
  const needsCustomer = detail.entries.filter(
    (entry) => !entry.effectiveCustomerId && entry.effectiveEntryStatus !== "rejected",
  ).length;
  const missingDates = detail.entries.filter(
    (entry) => !entry.review && !entry.effectiveDate && entry.effectiveEntryStatus !== "rejected",
  ).length;
  const canAssign = Boolean(detail.customerName) && needsCustomer > 0;
  const canConfirmClean = unreviewed > 0;
  const needsPageCustomer = !detail.customerName && needsCustomer > 0;
  const canBulkFix = needsPageCustomer || missingDates > 0;
  const bulkFixDisabled = Boolean(busy) || (!pageCustomer.trim() && !pageDate);

  return (
    <section className="checks-card">
      <div className="checks-heading">
        <span className="check-icon warn">!</span>
        <div>
          <h3>Before you can import</h3>
          <p>
            {unreviewed} row{unreviewed === 1 ? "" : "s"} need review
            {needsCustomer > 0 ? ` and ${needsCustomer} need a customer` : ""}. Routine checks can
            be cleared in bulk.
          </p>
        </div>
      </div>

      {(canAssign || canConfirmClean) && (
        <div className="bulk-actions">
          {canAssign && (
            <button
              className="button small primary"
              type="button"
              disabled={Boolean(busy)}
              onClick={() => onBulkConfirm({ customerName: detail.customerName })}
            >
              {busy === "bulk-assign"
                ? "Assigning…"
                : `Assign all to “${detail.customerName}”`}
            </button>
          )}
          {canConfirmClean && (
            <button
              className="button small"
              type="button"
              disabled={Boolean(busy)}
              onClick={() => onBulkConfirm()}
            >
              {busy === "bulk-confirm" ? "Confirming…" : "Confirm all clean rows"}
            </button>
          )}
        </div>
      )}
      {canBulkFix && (
        <div className="bulk-form">
          <p className="fine-print">
            {needsPageCustomer
              ? "This page has no customer name. Add one to assign the rows."
              : "Rows without a date cannot be confirmed."}
          </p>
          {needsPageCustomer && (
            <label>
              Customer name for these rows
              <input
                value={pageCustomer}
                onChange={(event) => setPageCustomer(event.target.value)}
                maxLength={200}
                placeholder="e.g. Ramesh Traders"
              />
            </label>
          )}
          {missingDates > 0 && (
            <label>
              Date for {missingDates} row{missingDates === 1 ? "" : "s"} without one
              <input
                type="date"
                value={pageDate}
                onChange={(event) => setPageDate(event.target.value)}
              />
            </label>
          )}
          <button
            className="button small primary"
            type="button"
            disabled={bulkFixDisabled}
            onClick={() =>
              onBulkConfirm({
                customerName: pageCustomer.trim() || undefined,
                date: pageDate || undefined,
              })
            }
          >
            {busy === "bulk-assign" || busy === "bulk-confirm" ? "Applying..." : "Apply & confirm"}
          </button>
        </div>
      )}
      <p className="fine-print">
        Clean rows are confirmed exactly as read. A missing customer is created from the name on
        the page. Rows without a date stay for manual review unless you set a date here.
      </p>

      <ul className="blocker-list">
        {groups.map((group) => (
          <li key={group.code}>
            <span className="blocker-code">{statusLabel(group.code)}</span>
            <span>{group.message}</span>
            {group.indexes.length > 0 && (
              <span className="blocker-rows">
                {group.indexes.length === 1 ? "Row " : "Rows "}
                {group.indexes.map((index, position) => (
                  <span key={index}>
                    {position > 0 && ", "}
                    <a href={`#entry-${index}`}>{index + 1}</a>
                  </span>
                ))}
              </span>
            )}
          </li>
        ))}
        {confirmation.map((blocker) => (
          <li key={`${blocker.code}-${blocker.message}`} className="soft-blocker">
            <span className="blocker-code">balance confirmation</span>
            <span>{blocker.message}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function groupBlockers(blockers: Blocker[]) {
  const groups = new Map<
    string,
    { code: string; message: string; indexes: number[] }
  >();
  for (const blocker of blockers) {
    const existing = groups.get(blocker.code);
    if (existing) {
      if (blocker.entryIndex !== undefined && !existing.indexes.includes(blocker.entryIndex)) {
        existing.indexes.push(blocker.entryIndex);
      }
      continue;
    }
    groups.set(blocker.code, {
      code: blocker.code,
      message: blocker.message,
      indexes: blocker.entryIndex === undefined ? [] : [blocker.entryIndex],
    });
  }
  for (const group of groups.values()) {
    group.indexes.sort((left, right) => left - right);
  }
  return Array.from(groups.values());
}

function ConfirmCard({
  detail,
  busy,
  onConfirm,
}: {
  detail: PageDetail;
  busy: boolean;
  onConfirm: (values: { customerId: string; confirmedBalancePaise: number }[]) => void;
}) {
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      detail.customerPreviews.map((preview) => [
        preview.customerId,
        plainRupees(preview.confirmedBalancePaise ?? preview.calculatedBalancePaise),
      ]),
    ),
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setValues(
      Object.fromEntries(
        detail.customerPreviews.map((preview) => [
          preview.customerId,
          plainRupees(preview.confirmedBalancePaise ?? preview.calculatedBalancePaise),
        ]),
      ),
    );
  }, [detail]);

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const confirmations: { customerId: string; confirmedBalancePaise: number }[] = [];
    for (const preview of detail.customerPreviews) {
      const raw = values[preview.customerId] ?? "";
      const value = parseBalance(raw);
      if (value === null) {
        setError(`Enter a valid balance for ${preview.displayName}.`);
        return;
      }
      confirmations.push({ customerId: preview.customerId, confirmedBalancePaise: value });
    }
    onConfirm(confirmations);
  }

  return (
    <section className="confirm-card" id="confirmations">
      <div className="section-title">
        <div>
          <p className="eyebrow">Final human check</p>
          <h2>Confirm the balance</h2>
        </div>
        <span className="confirm-lock">Required</span>
      </div>
      <p className="muted">
        KadaiBook has calculated these balances from the reviewed rows. Type what you expect the
        customer to owe; the page imports only when they match exactly.
      </p>
      <form onSubmit={submit}>
        <div className="confirm-grid">
          {detail.customerPreviews.map((preview) => (
            <div key={preview.customerId} className="confirm-row">
              <div className="confirm-person">
                <strong>{preview.displayName}</strong>
                <small>
                  Existing {rupees(preview.existingBalancePaise)} · {preview.entryCount} rows
                </small>
                {preview.closingMismatch && (
                  <span className="warn-text">Written closing differs from calculation</span>
                )}
              </div>
              <div className="calculated-box">
                <small>Calculated</small>
                <strong>{rupees(preview.calculatedBalancePaise)}</strong>
              </div>
              <label className="balance-input">
                <span>Confirm ₹</span>
                <input
                  value={values[preview.customerId] ?? ""}
                  onChange={(event) =>
                    setValues((current) => ({ ...current, [preview.customerId]: event.target.value }))
                  }
                  inputMode="decimal"
                  aria-label={`Confirmed balance for ${preview.displayName}`}
                  required
                />
              </label>
            </div>
          ))}
        </div>
        {error && <div className="form-error">{error}</div>}
        <div className="confirm-actions">
          <button className="button primary" disabled={busy} type="submit">
            {busy ? "Importing…" : "Confirm & import to khata"}
          </button>
          <span className="fine-print">
            This writes {detail.customerPreviews.reduce((sum, item) => sum + item.entryCount, 0)} ledger
            rows and records the confirmation in the audit trail.
          </span>
        </div>
      </form>

      {detail.confirmations.length > 0 && (
        <div className="confirmed-list">
          {detail.confirmations.map((confirmation) => (
            <span key={confirmation.customerId}>
              ✓ {confirmation.customerName} · {rupees(confirmation.confirmedBalancePaise)}
            </span>
          ))}
        </div>
      )}
    </section>
  );
}

function parseBalance(input: string): number | null {
  const text = input.trim().replace(/,/g, "");
  if (!/^-?\d+(\.\d{0,2})?$/.test(text)) return null;
  const negative = text.startsWith("-");
  const [whole, fraction = ""] = text.replace("-", "").split(".");
  const value = Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
  return negative ? -value : value;
}

function CustomerForm({ onCreated }: { onCreated: (name: string) => Promise<void> }) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <form
      className="stack-form"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await onCreated(name.trim());
        } catch (err) {
          setError(err instanceof Error ? err.message : "Could not add the customer.");
          setBusy(false);
        }
      }}
    >
      <label>
        Customer name, exactly as in the notebook
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={200}
          required
          placeholder="e.g. Ravi Kumar"
        />
      </label>
      <p className="fine-print">
        Add aliases later if the same person appears under a different spelling. This name will be
        matched against the page.
      </p>
      {error && <div className="form-error">{error}</div>}
      <button className="button primary" disabled={busy} type="submit">
        {busy ? "Adding…" : "Add customer"}
      </button>
    </form>
  );
}
