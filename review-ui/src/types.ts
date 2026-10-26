export type PageStatus =
  | "uploaded"
  | "extracting"
  | "needs_review"
  | "extraction_failed"
  | "imported";

export type LayoutType = "customer_ledger" | "date_wise" | "summary" | "unknown";
export type ValueStatus = "readable" | "ambiguous" | "unreadable";
export type EntryStatus = "active" | "crossed_out" | "uncertain" | "corrected" | "rejected";
export type ReviewAction = "confirmed" | "corrected" | "rejected";
export type MatchStatus = "MATCHED" | "AMBIGUOUS" | "NONE";
export type RunStatus =
  | "running"
  | "success"
  | "extraction_failed"
  | "unsupported_layout"
  | "transport_error";

export interface User {
  id: string;
  phone: string;
  displayName: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Shop {
  id: string;
  name: string;
  consentAt: string;
  createdAt: string;
}

export interface PageSummary {
  id: string;
  status: PageStatus;
  layoutType?: LayoutType;
  originalFilename: string;
  createdAt: string;
  importedAt?: string;
  customerName?: string;
  reviewRequired: boolean;
  entryCount: number;
  flaggedEntryCount: number;
  error?: string;
}

export interface CustomerRef {
  id: string;
  displayName: string;
}

export interface CustomerMatch {
  status: MatchStatus;
  candidates: CustomerRef[];
}

export interface ReviewView {
  id: string;
  action: ReviewAction;
  date?: string;
  dateStatus?: ValueStatus;
  description?: string;
  debitPaise?: number;
  creditPaise?: number;
  entryStatus?: EntryStatus;
  customerId?: string;
  customerName?: string;
  note?: string;
  decidedAt: string;
  edited: boolean;
}

export interface PageEntry {
  id: string;
  index: number;
  date?: string;
  dateStatus: ValueStatus;
  description?: string;
  debitPaise?: number;
  creditPaise?: number;
  entryStatus: EntryStatus;
  issues: string[];
  sourceText?: string;
  runningBalancePaise?: number;
  customerName?: string;
  customerNameStatus: ValueStatus;
  customerMatch: CustomerMatch;
  review?: ReviewView;
  effectiveDate?: string;
  effectiveDateStatus: ValueStatus;
  effectiveDescription?: string;
  effectiveDebitPaise?: number;
  effectiveCreditPaise?: number;
  effectiveEntryStatus: EntryStatus;
  effectiveCustomerId?: string;
  effectiveCustomerName?: string;
  calculatedBalancePaise?: number;
  balanceMismatch: boolean;
  importable: boolean;
}

export interface Blocker {
  code: string;
  message: string;
  entryId?: string;
  entryIndex?: number;
}

export interface CustomerBalancePreview {
  customerId: string;
  displayName: string;
  entryCount: number;
  existingBalancePaise: number;
  openingImported: boolean;
  openingBalancePaise?: number;
  calculatedBalancePaise: number;
  writtenClosingBalancePaise?: number;
  closingMismatch: boolean;
  confirmedBalancePaise?: number;
}

export interface Confirmation {
  customerId: string;
  customerName: string;
  confirmedBalancePaise: number;
  calculatedBalancePaise: number;
  confirmedAt: string;
}

export interface RunInfo {
  id: string;
  status: RunStatus;
  model?: string;
  promptVersion?: string;
  schemaVersion?: string;
  attempts: number;
  latencyMs: number;
  costPaise: number;
  inputTokens: number;
  outputTokens: number;
  layoutConfidence?: number;
  error?: string;
  createdAt: string;
}

export interface PageDetail {
  id: string;
  shopId: string;
  status: PageStatus;
  layoutType?: LayoutType;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  importedAt?: string;
  imageUrl: string;
  imageAvailable: boolean;
  customerName?: string;
  customerStatus?: ValueStatus;
  openingBalancePaise?: number;
  closingBalancePaise?: number;
  pageIssues: string[];
  reviewRequired: boolean;
  run?: RunInfo;
  entries: PageEntry[];
  customerPreviews: CustomerBalancePreview[];
  blockers: Blocker[];
  warnings: string[];
  canExtract: boolean;
  canReview: boolean;
  canConfirm: boolean;
  confirmations: Confirmation[];
}

export interface UploadResponse {
  page: PageSummary;
  duplicate: boolean;
}

export interface Customer {
  id: string;
  displayName: string;
  aliases: string[];
  createdAt: string;
  balancePaise: number;
}

export interface LedgerRow {
  id: string;
  date?: string;
  description?: string;
  debitPaise: number;
  creditPaise: number;
  sourcePageId?: string;
  createdAt: string;
}

export interface CustomerLedger {
  customerId: string;
  displayName: string;
  balancePaise: number;
  entries: LedgerRow[];
}

export interface AuditEntry {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  actorName?: string;
  beforeJson?: string;
  afterJson?: string;
  at: string;
}

export interface ReviewRequest {
  action: ReviewAction;
  date?: string | null;
  dateStatus?: ValueStatus;
  description?: string | null;
  debitPaise?: number | null;
  creditPaise?: number | null;
  entryStatus?: EntryStatus;
  customerId?: string | null;
  note?: string | null;
}

export interface BalanceConfirmationInput {
  customerId: string;
  confirmedBalancePaise: number;
}

export interface BulkConfirmRequest {
  customerId?: string | null;
  customerName?: string | null;
  date?: string | null;
}
