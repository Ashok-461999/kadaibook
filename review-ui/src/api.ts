import type {
  AuditEntry,
  AuthResponse,
  BalanceConfirmationInput,
  BulkConfirmRequest,
  Customer,
  CustomerLedger,
  PageDetail,
  PageSummary,
  ReviewRequest,
  Shop,
  UploadResponse,
  User,
} from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? "";
const TOKEN_KEY = "kadaibook.token";

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    throw new ApiError("Cannot reach the KadaiBook server. Is the API running?", 0);
  }
  if (response.status === 401) {
    setToken(null);
    window.dispatchEvent(new Event("kadaibook:logout"));
    throw new ApiError("Your session expired. Sign in again.", 401);
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    let code: string | undefined;
    try {
      const body = await response.json();
      message = body.detail ?? body.title ?? message;
      code = body.code;
      if (body.errors && typeof body.errors === "object") {
        const first = Object.values(body.errors)[0];
        if (typeof first === "string") message = first;
      }
    } catch {
      // Keep the generic message for non-JSON failures.
    }
    throw new ApiError(message, response.status, code);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

function json<T>(path: string, method: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const api = {
  login(phone: string, password: string): Promise<AuthResponse> {
    return json("/api/v1/auth/login", "POST", { phone, password });
  },
  me(): Promise<User> {
    return request("/api/v1/auth/me");
  },
  shops(): Promise<Shop[]> {
    return request("/api/v1/shops");
  },
  createShop(name: string): Promise<Shop> {
    return json("/api/v1/shops", "POST", { name, consent: true });
  },
  pages(shopId: string): Promise<PageSummary[]> {
    return request(`/api/v1/shops/${shopId}/pages`);
  },
  page(shopId: string, pageId: string): Promise<PageDetail> {
    return request(`/api/v1/shops/${shopId}/pages/${pageId}`);
  },
  upload(shopId: string, file: File): Promise<UploadResponse> {
    const form = new FormData();
    form.append("file", file);
    return request(`/api/v1/shops/${shopId}/pages`, { method: "POST", body: form });
  },
  extract(shopId: string, pageId: string): Promise<PageDetail> {
    return request(`/api/v1/shops/${shopId}/pages/${pageId}/extract`, { method: "POST" });
  },
  review(
    shopId: string,
    pageId: string,
    entryId: string,
    body: ReviewRequest,
  ): Promise<PageDetail> {
    return json(`/api/v1/shops/${shopId}/pages/${pageId}/entries/${entryId}/review`, "PUT", body);
  },
  confirm(
    shopId: string,
    pageId: string,
    confirmations: BalanceConfirmationInput[],
  ): Promise<PageDetail> {
    return json(`/api/v1/shops/${shopId}/pages/${pageId}/confirm`, "POST", { confirmations });
  },
  bulkConfirm(
    shopId: string,
    pageId: string,
    body: BulkConfirmRequest,
  ): Promise<PageDetail> {
    return json(`/api/v1/shops/${shopId}/pages/${pageId}/bulk-confirm`, "POST", body);
  },
  deletePage(shopId: string, pageId: string): Promise<void> {
    return request(`/api/v1/shops/${shopId}/pages/${pageId}`, { method: "DELETE" });
  },
  customers(shopId: string): Promise<Customer[]> {
    return request(`/api/v1/shops/${shopId}/customers`);
  },
  createCustomer(shopId: string, displayName: string, aliases: string[]): Promise<Customer> {
    return json(`/api/v1/shops/${shopId}/customers`, "POST", { displayName, aliases });
  },
  ledger(shopId: string, customerId: string): Promise<CustomerLedger> {
    return request(`/api/v1/shops/${shopId}/customers/${customerId}/ledger`);
  },
  audit(shopId: string): Promise<AuditEntry[]> {
    return request(`/api/v1/shops/${shopId}/audit?limit=100`);
  },
  async image(shopId: string, pageId: string): Promise<string> {
    const token = getToken();
    const response = await fetch(`${API_BASE}/api/v1/shops/${shopId}/pages/${pageId}/image`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) throw new ApiError("Could not load the page image.", response.status);
    return URL.createObjectURL(await response.blob());
  },
};
