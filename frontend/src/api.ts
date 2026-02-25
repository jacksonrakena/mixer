// API base URL - proxied through Vite dev server
const BASE = "/api";

// ── Types ────────────────────────────────────────────────────────────────────

export interface AssetDto {
  id: string;
  name: string;
  ownerId: string;
  currency: string;
  staleAfter: number; // epoch millis, 0 = not stale
}

export interface CreateAssetRequest {
  name: string;
  ownerId: string;
  currency: string;
}

export interface CreateAssetResponse {
  assetId: string;
}

export interface DeleteAssetResponse {
  assetId: string;
  deleted: boolean;
}

export type TransactionType = "Trade" | "Reconciliation";

export interface CreateTransactionRequest {
  type: TransactionType;
  amount?: number;
  value?: number;
  timestamp: string; // ISO instant string e.g. "2026-02-01T00:00:00Z"
}

export interface CreateTransactionResponse {
  transactionId: string;
  assetId: string;
  jobId: string;
  staleAfter: number; // epoch millis
}

export interface DeleteTransactionResponse {
  transactionId: string;
  assetId: string;
  deleted: boolean;
  jobId: string;
  staleAfter: number; // epoch millis
}

export interface FxConversionInfo {
  rate: number;
  fromCurrency: string;
  toCurrency: string;
  rateDate: string; // ISO local date (YYYY-MM-DD)
}

export interface AssetAggregation {
  assetId: string;
  date: string; // ISO instant string
  amount: number;
  amountDeltaTrades: number;
  amountDeltaReconciliation: number;
  amountDeltaOther: number;
  /** Value in the asset's native currency */
  nativeValue: number;
  /** Value converted to the user's display currency, or null if no FX rate was available */
  displayValue: number | null;
  /** The asset's native currency code */
  nativeCurrency: string | null;
  /** The user's display currency code */
  displayCurrency: string | null;
  /** FX conversion details, or null if no conversion was needed or no rate was available */
  fxConversion: FxConversionInfo | null;
}

// ── Assets ───────────────────────────────────────────────────────────────────

export async function fetchAssets(): Promise<AssetDto[]> {
  const res = await fetch(`${BASE}/asset`);
  if (!res.ok) throw new Error(`Failed to fetch assets: ${res.status}`);
  return res.json();
}

export async function createAsset(
  req: CreateAssetRequest,
): Promise<CreateAssetResponse> {
  const res = await fetch(`${BASE}/asset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`Failed to create asset: ${res.status}`);
  return res.json();
}

export async function deleteAsset(
  assetId: string,
): Promise<DeleteAssetResponse> {
  const res = await fetch(`${BASE}/asset/${assetId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(`Failed to delete asset: ${res.status}`);
  return res.json();
}

// ── Transactions ─────────────────────────────────────────────────────────────

export async function createTransaction(
  assetId: string,
  req: CreateTransactionRequest,
): Promise<CreateTransactionResponse> {
  const res = await fetch(`${BASE}/asset/${assetId}/transaction`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`Failed to create transaction: ${res.status}`);
  return res.json();
}

export async function deleteTransaction(
  assetId: string,
  transactionId: string,
): Promise<DeleteTransactionResponse> {
  const res = await fetch(
    `${BASE}/asset/${assetId}/transaction/${transactionId}`,
    {
      method: "DELETE",
    },
  );
  if (!res.ok) throw new Error(`Failed to delete transaction: ${res.status}`);
  return res.json();
}

// ── Transaction listing ──────────────────────────────────────────────────────

export interface TransactionDto {
  id: string;
  assetId: string;
  type: TransactionType;
  amount: number | null;
  value: number | null;
  timestamp: number; // epoch millis
}

export interface PaginatedTransactionsResponse {
  transactions: TransactionDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function fetchTransactions(
  assetId: string,
  page: number = 0,
  size: number = 10,
): Promise<PaginatedTransactionsResponse> {
  const res = await fetch(
    `${BASE}/asset/${assetId}/transaction?page=${page}&size=${size}`,
  );
  if (!res.ok) throw new Error(`Failed to fetch transactions: ${res.status}`);
  return res.json();
}

// ── Aggregation ───────────────────────────────────────────────────────────────

export async function fetchAggregation(
  assetId: string,
  start: string,
  end: string,
  displayCurrency?: string,
): Promise<AssetAggregation[]> {
  const params = displayCurrency
    ? `?displayCurrency=${encodeURIComponent(displayCurrency)}`
    : "";
  const res = await fetch(
    `${BASE}/agg/asset/${assetId}/${start}/${end}${params}`,
  );
  if (!res.ok) throw new Error(`Failed to fetch aggregation: ${res.status}`);
  return res.json();
}

export async function fetchAllAggregations(
  assetId: string,
  displayCurrency?: string,
): Promise<AssetAggregation[]> {
  const params = displayCurrency
    ? `?displayCurrency=${encodeURIComponent(displayCurrency)}`
    : "";
  const res = await fetch(`${BASE}/agg/asset/${assetId}/all${params}`);
  if (!res.ok)
    throw new Error(`Failed to fetch all aggregations: ${res.status}`);
  return res.json();
}

// ── Supported currencies ─────────────────────────────────────────────────────

export const SUPPORTED_CURRENCIES = [
  "AUD",
  "USD",
  "NZD",
  "EUR",
  "GBP",
  "HKD",
] as const;
export type SupportedCurrency = (typeof SUPPORTED_CURRENCIES)[number];

// ── Staleness ─────────────────────────────────────────────────────────────────

export interface AssetStalenessResponse {
  assetId: string;
  staleAfter: number; // epoch millis, 0 = not stale
}

export async function fetchAssetStaleness(
  assetId: string,
): Promise<AssetStalenessResponse> {
  const res = await fetch(`${BASE}/asset/${assetId}/staleness`);
  if (!res.ok) throw new Error(`Failed to fetch staleness: ${res.status}`);
  return res.json();
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns YYYY-MM-DD for a Date object */
export function toLocalDateString(d: Date): string {
  return d.toISOString().split("T")[0];
}

/** Returns a date N days ago as YYYY-MM-DD */
export function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toLocalDateString(d);
}

/** Today as YYYY-MM-DD */
export function today(): string {
  return toLocalDateString(new Date());
}
