import { useEffect, useMemo, useState, type FormEvent } from "react";
import { api } from "../api";
import { formatDate, rupees } from "../format";
import type { Customer, CustomerLedger, Shop } from "../types";
import Modal from "./Modal";

interface Props {
  shop: Shop;
  customers: Customer[];
  onCustomersChanged: () => void;
}

export default function CustomersView({ shop, customers, onCustomersChanged }: Props) {
  const [query, setQuery] = useState("");
  const [showAdd, setShowAdd] = useState(false);
  const [ledger, setLedger] = useState<CustomerLedger | null>(null);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [ledgerError, setLedgerError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return customers;
    return customers.filter(
      (customer) =>
        customer.displayName.toLowerCase().includes(term) ||
        customer.aliases.some((alias) => alias.toLowerCase().includes(term)),
    );
  }, [customers, query]);

  const totalOutstanding = customers.reduce((sum, customer) => sum + customer.balancePaise, 0);

  async function openLedger(customer: Customer) {
    setLedgerLoading(true);
    setLedgerError(null);
    setLedger({
      customerId: customer.id,
      displayName: customer.displayName,
      balancePaise: customer.balancePaise,
      entries: [],
    });
    try {
      setLedger(await api.ledger(shop.id, customer.id));
    } catch (err) {
      setLedgerError(err instanceof Error ? err.message : "Could not load the khata.");
    } finally {
      setLedgerLoading(false);
    }
  }

  return (
    <div className="customers-view">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Customers & khata</p>
          <h1>Who owes what</h1>
          <p className="muted">
            Balances come only from confirmed pages. Positive means the customer owes the shop.
          </p>
        </div>
        <div className="heading-stats">
          <div>
            <span>{customers.length}</span>
            <small>customers</small>
          </div>
          <div>
            <span>{rupees(totalOutstanding)}</span>
            <small>outstanding</small>
          </div>
        </div>
      </section>

      <div className="customer-toolbar">
        <input
          className="search-input"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search a name or alias…"
        />
        <button className="button primary" onClick={() => setShowAdd(true)}>
          + Add customer
        </button>
      </div>

      {customers.length === 0 ? (
        <div className="empty-state tall">
          <h3>No customers yet</h3>
          <p>Add customers before reviewing a page, or create them while correcting a row.</p>
          <button className="button" onClick={() => setShowAdd(true)}>
            Add the first customer
          </button>
        </div>
      ) : (
        <div className="customer-table">
          <div className="customer-table-head">
            <span>Customer</span>
            <span>Aliases</span>
            <span>Rows</span>
            <span className="right">Balance</span>
          </div>
          {filtered.map((customer) => (
            <button key={customer.id} className="customer-row" onClick={() => void openLedger(customer)}>
              <span className="customer-name">
                <span className="customer-avatar">{customer.displayName.slice(0, 1).toUpperCase()}</span>
                {customer.displayName}
              </span>
              <span className="muted">{customer.aliases.join(", ") || "—"}</span>
              <span className="muted">—</span>
              <span className={customer.balancePaise > 0 ? "balance-due right" : "right"}>
                {rupees(customer.balancePaise)}
              </span>
            </button>
          ))}
          {filtered.length === 0 && (
            <div className="empty-state">
              <p>No customer matches “{query}”.</p>
            </div>
          )}
        </div>
      )}

      <div className="privacy-strip">
        <strong>How balances work</strong>
        A confirmed page adds its rows to the ledger. An opening balance is imported only when this
        is the customer's first page. KadaiBook never overwrites past ledger rows.
      </div>

      {showAdd && (
        <Modal title="Add a customer" onClose={() => setShowAdd(false)}>
          <AddCustomerForm
            shopId={shop.id}
            onCreated={() => {
              setShowAdd(false);
              onCustomersChanged();
            }}
          />
        </Modal>
      )}

      {ledger && (
        <Modal title={`${ledger.displayName} · khata`} onClose={() => setLedger(null)} wide>
          {ledgerLoading ? (
            <div className="loading-row">
              <div className="spinner dark" /> Loading the ledger…
            </div>
          ) : ledgerError ? (
            <div className="form-error">{ledgerError}</div>
          ) : (
            <div className="ledger-view">
              <div className="ledger-total">
                <div>
                  <small>Current balance</small>
                  <strong>{rupees(ledger.balancePaise)}</strong>
                </div>
                <p>
                  {ledger.balancePaise > 0
                    ? "Amount the customer owes the shop."
                    : ledger.balancePaise < 0
                      ? "Advance credit held by the shop."
                      : "Settled."}
                </p>
              </div>
              {ledger.entries.length === 0 ? (
                <div className="empty-state">
                  <p>No confirmed ledger rows yet.</p>
                </div>
              ) : (
                <div className="ledger-table">
                  <div className="ledger-head">
                    <span>Date</span>
                    <span>Description</span>
                    <span className="right">Debit</span>
                    <span className="right">Credit</span>
                  </div>
                  {ledger.entries.map((row) => (
                    <div key={row.id} className="ledger-row">
                      <span>{formatDate(row.date)}</span>
                      <span>{row.description || "—"}</span>
                      <span className="right debit">{row.debitPaise ? rupees(row.debitPaise) : "—"}</span>
                      <span className="right credit">
                        {row.creditPaise ? rupees(row.creditPaise) : "—"}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}

function AddCustomerForm({ shopId, onCreated }: { shopId: string; onCreated: () => void }) {
  const [name, setName] = useState("");
  const [aliases, setAliases] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setName("");
  }, [shopId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const aliasList = aliases
        .split(",")
        .map((alias) => alias.trim())
        .filter(Boolean);
      await api.createCustomer(shopId, name.trim(), aliasList);
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add the customer.");
      setBusy(false);
    }
  }

  return (
    <form className="stack-form" onSubmit={submit}>
      <label>
        Customer name
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="e.g. Ravi Kumar"
          maxLength={200}
          required
        />
      </label>
      <label>
        Other spellings (optional)
        <input
          value={aliases}
          onChange={(event) => setAliases(event.target.value)}
          placeholder="Ravi, R. Kumar"
        />
      </label>
      <p className="fine-print">
        Matching is case-insensitive. Aliases help the same customer be recognised on different
        pages.
      </p>
      {error && <div className="form-error">{error}</div>}
      <button className="button primary" disabled={busy} type="submit">
        {busy ? "Adding…" : "Add customer"}
      </button>
    </form>
  );
}
