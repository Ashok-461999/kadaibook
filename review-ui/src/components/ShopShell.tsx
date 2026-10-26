import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { api } from "../api";
import type { Customer, Shop, User } from "../types";
import AuditView from "./AuditView";
import CustomersView from "./CustomersView";
import Modal from "./Modal";
import PagesView from "./PagesView";
import PageWorkspace from "./PageWorkspace";

type Tab = "pages" | "customers" | "audit";
const SHOP_KEY = "kadaibook.shop";

interface Props {
  user: User;
  onLogout: () => void;
}

export default function ShopShell({ user, onLogout }: Props) {
  const [shops, setShops] = useState<Shop[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(localStorage.getItem(SHOP_KEY));
  const [tab, setTab] = useState<Tab>("pages");
  const [pageId, setPageId] = useState<string | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showShopModal, setShowShopModal] = useState(false);

  const selected = useMemo(
    () => shops.find((shop) => shop.id === selectedId) ?? shops[0] ?? null,
    [shops, selectedId],
  );

  const loadShops = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.shops();
      setShops(result);
      if (result.length > 0 && !result.some((shop) => shop.id === selectedId)) {
        setSelectedId(result[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load your shops.");
    } finally {
      setLoading(false);
    }
  }, [selectedId]);

  useEffect(() => {
    void loadShops();
  }, [loadShops]);

  const loadCustomers = useCallback(async () => {
    if (!selected) return;
    try {
      setCustomers(await api.customers(selected.id));
    } catch {
      setCustomers([]);
    }
  }, [selected]);

  useEffect(() => {
    void loadCustomers();
  }, [loadCustomers]);

  useEffect(() => {
    if (selected) localStorage.setItem(SHOP_KEY, selected.id);
  }, [selected]);

  function changeShop(id: string) {
    setSelectedId(id);
    setPageId(null);
    setTab("pages");
  }

  async function createCustomer(displayName: string): Promise<Customer> {
    if (!selected) throw new Error("No shop selected.");
    const customer = await api.createCustomer(selected.id, displayName, []);
    setCustomers((current) => [...current, customer]);
    return customer;
  }

  if (loading) {
    return (
      <div className="boot-screen">
        <div className="spinner" />
        <p>Loading your shops…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="boot-screen">
        <p className="form-error">{error}</p>
        <button className="button primary" onClick={() => void loadShops()}>
          Try again
        </button>
      </div>
    );
  }

  if (!selected) {
    return (
      <div className="empty-shop-page">
        <div className="brand-row">
          <span className="brand-mark small">க</span>
          <span className="brand-name">KadaiBook</span>
        </div>
        <div className="empty-shop-card">
          <p className="eyebrow">First step</p>
          <h1>Create your shop workspace</h1>
          <p className="muted">
            Your notebooks stay separate from every other shop. Confirm that you have permission to
            digitise the pages you upload.
          </p>
          <CreateShopForm
            onCreated={(shop) => {
              setShops([shop]);
              setSelectedId(shop.id);
            }}
          />
        </div>
        <button className="link-button" onClick={onLogout}>
          Sign out
        </button>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-inner">
          <button className="brand-row brand-button" onClick={() => setPageId(null)}>
            <span className="brand-mark small">க</span>
            <span className="brand-name">KadaiBook</span>
          </button>

          <div className="shop-switcher">
            <label htmlFor="shop-select">Shop</label>
            <select
              id="shop-select"
              value={selected.id}
              onChange={(e) => changeShop(e.target.value)}
            >
              {shops.map((shop) => (
                <option key={shop.id} value={shop.id}>
                  {shop.name}
                </option>
              ))}
            </select>
            <button className="icon-button plus" onClick={() => setShowShopModal(true)} title="New shop">
              +
            </button>
          </div>

          <div className="topbar-account">
            <span className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</span>
            <span className="account-name">{user.displayName}</span>
            <button className="link-button" onClick={onLogout}>
              Sign out
            </button>
          </div>
        </div>
      </header>

      <nav className="tabbar">
        <div className="tabbar-inner">
          {(["pages", "customers", "audit"] as const).map((item) => (
            <button
              key={item}
              className={tab === item && !pageId ? "tab active" : "tab"}
              onClick={() => {
                setTab(item);
                setPageId(null);
              }}
            >
              {item === "pages" ? "Notebook pages" : item === "customers" ? "Customers & khata" : "Audit trail"}
            </button>
          ))}
          <span className="tabbar-note">AI extracts · you confirm · KadaiBook calculates</span>
        </div>
      </nav>

      <main className={pageId ? "workspace-main" : "page-main"}>
        {pageId ? (
          <PageWorkspace
            shopId={selected.id}
            pageId={pageId}
            customers={customers}
            onBack={() => setPageId(null)}
            onCustomersChanged={() => void loadCustomers()}
            onCreateCustomer={createCustomer}
            onNewPage={() => {
              setPageId(null);
              setTab("pages");
            }}
          />
        ) : tab === "pages" ? (
          <PagesView shop={selected} onOpenPage={setPageId} />
        ) : tab === "customers" ? (
          <CustomersView
            shop={selected}
            customers={customers}
            onCustomersChanged={() => void loadCustomers()}
          />
        ) : (
          <AuditView shop={selected} />
        )}
      </main>

      <footer className="app-footer">
        <span>KadaiBook pilot</span>
        <span>Amounts in Indian rupees · stored as integer paise</span>
      </footer>

      {showShopModal && (
        <Modal title="Create another shop" onClose={() => setShowShopModal(false)}>
          <CreateShopForm
            onCreated={(shop) => {
              setShops((current) => [...current, shop]);
              setSelectedId(shop.id);
              setShowShopModal(false);
              setPageId(null);
            }}
          />
        </Modal>
      )}
    </div>
  );
}

function CreateShopForm({ onCreated }: { onCreated: (shop: Shop) => void }) {
  const [name, setName] = useState("");
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createShop(name.trim()));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the shop.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack-form" onSubmit={submit}>
      <label>
        Shop name
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g. Kumar Stores, Mylapore"
          maxLength={160}
          required
        />
      </label>
      <label className="check-row">
        <input
          type="checkbox"
          checked={consent}
          onChange={(e) => setConsent(e.target.checked)}
          required
        />
        <span>I have permission from the shop to digitise these notebook pages.</span>
      </label>
      {error && <div className="form-error">{error}</div>}
      <button className="button primary" disabled={busy || !consent} type="submit">
        {busy ? "Creating…" : "Create shop"}
      </button>
    </form>
  );
}
