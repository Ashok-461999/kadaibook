import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api";
import { formatBytes, formatDate, formatDateTime, statusLabel } from "../format";
import type { PageSummary, Shop } from "../types";

interface Props {
  shop: Shop;
  onOpenPage: (pageId: string) => void;
}

export default function PagesView({ shop, onOpenPage }: Props) {
  const [pages, setPages] = useState<PageSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [phase, setPhase] = useState<"idle" | "uploading" | "extracting">("idle");
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPages(await api.pages(shop.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load pages.");
    } finally {
      setLoading(false);
    }
  }, [shop.id]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (!["image/png", "image/jpeg"].includes(file.type)) {
      setError("Choose a PNG or JPEG photo of the notebook page.");
      return;
    }
    if (file.size > 15 * 1024 * 1024) {
      setError(`That file is ${formatBytes(file.size)}. The limit is 15 MB.`);
      return;
    }
    setPhase("uploading");
    try {
      const result = await api.upload(shop.id, file);
      if (result.duplicate && (result.page.status === "needs_review" || result.page.status === "imported")) {
        onOpenPage(result.page.id);
        return;
      }
      if (result.page.status === "uploaded" || result.page.status === "extraction_failed") {
        setPhase("extracting");
        try {
          await api.extract(shop.id, result.page.id);
        } catch (err) {
          setError(
            err instanceof Error
              ? `${err.message} The page is saved; open it to retry.`
              : "Reading failed. Open the page to retry.",
          );
        }
      }
      await load();
      onOpenPage(result.page.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not upload the page.");
    } finally {
      setPhase("idle");
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  const reviewCount = pages.filter((page) => page.status === "needs_review").length;
  const importedCount = pages.filter((page) => page.status === "imported").length;
  const busy = phase !== "idle";

  return (
    <div className="pages-view">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Notebook pages</p>
          <h1>{shop.name}</h1>
          <p className="muted">
            {pages.length === 0
              ? "Start with one clear photo of a khata page."
              : `${reviewCount} waiting for review · ${importedCount} imported`}
          </p>
        </div>
        <div className="heading-stats">
          <div>
            <span>{pages.length}</span>
            <small>pages</small>
          </div>
          <div>
            <span>{reviewCount}</span>
            <small>to review</small>
          </div>
          <div>
            <span>{importedCount}</span>
            <small>imported</small>
          </div>
        </div>
      </section>

      <section
        className={dragging ? "upload-card dragging" : "upload-card"}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          void handleFile(event.dataTransfer.files[0]);
        }}
      >
        {busy ? (
          <ExtractionProgress phase={phase} />
        ) : (
          <>
            <div className="upload-illustration" aria-hidden="true">
              <span className="paper-sheet" />
              <span className="scan-line" />
            </div>
            <div className="upload-copy">
              <h2>Add a notebook page</h2>
              <p>
                Drop a clear PNG or JPEG here, or choose a photo. AI reads the page, then you review
                anything marked uncertain.
              </p>
              <div className="button-row">
                <button className="button primary" onClick={() => inputRef.current?.click()}>
                  Choose page photo
                </button>
                <span className="fine-print">Max 15 MB · keep the whole page in frame</span>
              </div>
            </div>
            <input
              ref={inputRef}
              type="file"
              accept="image/png,image/jpeg"
              hidden
              onChange={(event) => void handleFile(event.target.files?.[0])}
            />
          </>
        )}
      </section>

      {error && <div className="notice error">{error}</div>}

      <section className="page-list-section">
        <div className="section-title">
          <h2>Page history</h2>
          <button className="link-button" onClick={() => void load()} disabled={loading}>
            Refresh
          </button>
        </div>
        {loading ? (
          <div className="loading-row">
            <div className="spinner dark" /> Loading pages…
          </div>
        ) : pages.length === 0 ? (
          <div className="empty-state">
            <h3>No pages yet</h3>
            <p>Upload the first page to see the review workflow.</p>
          </div>
        ) : (
          <div className="page-grid">
            {pages.map((page) => (
              <button key={page.id} className="page-tile" onClick={() => onOpenPage(page.id)}>
                <div className="page-tile-top">
                  <span className={`status-badge ${page.status}`}>{statusLabel(page.status)}</span>
                  {page.reviewRequired && page.status === "needs_review" && (
                    <span className="flag-count">{page.flaggedEntryCount || page.entryCount} flagged</span>
                  )}
                </div>
                <h3>{page.customerName || page.originalFilename}</h3>
                <p className="page-meta">
                  {page.entryCount > 0 ? `${page.entryCount} entries` : "Not read yet"} ·{" "}
                  {formatDate(page.createdAt)}
                </p>
                {page.error && <p className="tile-error">{page.error}</p>}
                <span className="tile-open">Open page →</span>
              </button>
            ))}
          </div>
        )}
      </section>

      <aside className="privacy-strip">
        <strong>Page privacy</strong>
        Images are stored privately and removed after 30 days by default. Imported ledger rows stay
        after the image is deleted. Every import is recorded in the audit trail.
        <span className="privacy-date">Last refresh {formatDateTime(new Date().toISOString())}</span>
      </aside>
    </div>
  );
}

function ExtractionProgress({ phase }: { phase: "uploading" | "extracting" }) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="extraction-progress">
      <div className="reading-animation" aria-hidden="true">
        <span className="reading-page" />
        <span className="reading-beam" />
      </div>
      <div>
        <h2>{phase === "uploading" ? "Uploading the page…" : "Reading the handwriting…"}</h2>
        <p>
          {phase === "uploading"
            ? "Sending the photo securely to your shop workspace."
            : "AI is reading names, dates, amounts, and crossed-out rows. This can take a minute on the first page."}
        </p>
        <div className="progress-track">
          <span />
        </div>
        <span className="progress-time">{seconds}s elapsed · keep this tab open</span>
      </div>
    </div>
  );
}
