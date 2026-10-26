export function rupees(paise: number | null | undefined): string {
  if (paise === null || paise === undefined) return "—";
  const value = paise / 100;
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: value % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(value);
}

export function plainRupees(paise: number | null | undefined): string {
  if (paise === null || paise === undefined) return "—";
  return new Intl.NumberFormat("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(
    paise / 100,
  );
}

export function parseRupeesToPaise(input: string): number | null {
  const text = input.trim().replace(/,/g, "");
  if (!text) return null;
  if (!/^\d+(\.\d{0,2})?$/.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  return Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
}

export function formatDate(value?: string | null): string {
  if (!value) return "No date";
  const date = new Date(`${value.slice(0, 10)}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(
    date,
  );
}

export function formatDateTime(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function statusLabel(status: string): string {
  return status.replaceAll("_", " ");
}
