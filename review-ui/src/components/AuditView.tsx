import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { formatDateTime, statusLabel } from "../format";
import type { AuditEntry, Shop } from "../types";

export default function AuditView({ shop }: { shop: Shop }) {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEntries(await api.audit(shop.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the audit trail.");
    } finally {
      setLoading(false);
    }
  }, [shop.id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="audit-view">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Accountability</p>
          <h1>Audit trail</h1>
          <p className="muted">
            Every review, correction, confirmation, import, deletion, and retention action for this
            shop.
          </p>
        </div>
        <button className="button" onClick={() => void load()} disabled={loading}>
          Refresh
        </button>
      </section>

      {error && <div className="notice error">{error}</div>}

      {loading ? (
        <div className="loading-row">
          <div className="spinner dark" /> Loading audit events…
        </div>
      ) : entries.length === 0 ? (
        <div className="empty-state tall">
          <h3>No audit events yet</h3>
          <p>Start by uploading and reviewing a page.</p>
        </div>
      ) : (
        <div className="audit-list">
          {entries.map((entry) => (
            <article key={entry.id} className="audit-row">
              <div className="audit-time">
                <strong>{formatDateTime(entry.at)}</strong>
                <span>{entry.actorName || "System"}</span>
              </div>
              <div className="audit-action">
                <span className="action-name">{statusLabel(entry.action)}</span>
                <span className="soft-badge">{entry.entityType}</span>
                <code>{entry.entityId}</code>
              </div>
              {(entry.beforeJson || entry.afterJson) && (
                <details className="audit-details">
                  <summary>View changes</summary>
                  <div className="audit-json">
                    {entry.beforeJson && (
                      <div>
                        <small>Before</small>
                        <pre>{pretty(entry.beforeJson)}</pre>
                      </div>
                    )}
                    {entry.afterJson && (
                      <div>
                        <small>After</small>
                        <pre>{pretty(entry.afterJson)}</pre>
                      </div>
                    )}
                  </div>
                </details>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}

function pretty(json: string): string {
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}
