import { useState, type FormEvent } from "react";
import { api } from "../api";
import type { User } from "../types";

interface Props {
  onAuthenticated: (token: string, user: User) => void;
}

export default function Login({ onAuthenticated }: Props) {
  const [phone, setPhone] = useState("+919000000001");
  const [password, setPassword] = useState("kadaibook123");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const result = await api.login(phone.trim(), password);
      onAuthenticated(result.token, result.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not sign in.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-story">
        <div className="brand-row">
          <span className="brand-mark small">க</span>
          <span className="brand-name">KadaiBook</span>
        </div>
        <p className="eyebrow">Notebook → trusted ledger</p>
        <h1>
          Your paper khata,
          <br />
          without the retyping.
        </h1>
        <p className="story-copy">
          Photograph a page. AI reads what is written, flags anything uncertain, and asks you to
          confirm. The balance is always calculated by KadaiBook, never guessed by AI.
        </p>
        <ul className="story-points">
          <li>
            <span>01</span> Upload a notebook page
          </li>
          <li>
            <span>02</span> Review only the flagged rows
          </li>
          <li>
            <span>03</span> Confirm the balance and import
          </li>
        </ul>
        <div className="sketch-card" aria-hidden="true">
          <div className="sketch-line wide" />
          <div className="sketch-line" />
          <div className="sketch-line medium" />
          <div className="sketch-stamp">PAID</div>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <p className="eyebrow">Shop sign in</p>
          <h2>Welcome back</h2>
          <p className="muted">Use the demo account to explore the verified sample ledger.</p>
          <form onSubmit={submit}>
            <label>
              Phone number
              <input
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                autoComplete="username"
                inputMode="tel"
                required
              />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            {error && <div className="form-error">{error}</div>}
            <button className="button primary wide" disabled={busy} type="submit">
              {busy ? "Signing in…" : "Open my shops"}
            </button>
          </form>
          <div className="demo-note">
            <strong>Demo:</strong> +919000000001 · kadaibook123
            <button
              type="button"
              className="link-button"
              onClick={() => {
                setPhone("+919000000001");
                setPassword("kadaibook123");
              }}
            >
              Fill demo
            </button>
          </div>
          <p className="fine-print">
            Only upload notebooks you have permission to digitise. Page images are kept for 30 days
            by default.
          </p>
        </div>
      </section>
    </main>
  );
}
