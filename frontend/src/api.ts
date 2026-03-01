// API base URL - in production, set window.__MIXER_CONFIG__.apiBase via env var
const BASE = (window as any).__MIXER_CONFIG__?.apiBase || "/api";

/**
 * Wrapper around fetch that always includes credentials (cookies).
 * Required because the frontend and API are on different subdomains
 * (e.g. finance.rakena.com.au vs finance-api.rakena.com.au).
 */
function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return fetch(input, { ...init, credentials: "include" });
}

// ── Types ────────────────────────────────────────────────────────────────────

export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  timezone: string;
  displayCurrency: string;
  roles: string[];
  createdAt: number;
}

export interface AssetDto {
  id: string;
  name: string;
  ownerId: string;
  currency: string;
  staleAfter: number; // epoch millis, 0 = not stale
  aggregatedThrough: string | null; // ISO date or null if never aggregated
  provider: string; // "USER" or "YFIN"
  providerData: string | null; // JSON string, e.g. {"tickerCode":"AAPL"}
}

export interface CreateAssetRequest {
  name: string;
  currency: string;
  provider?: string;
  providerData?: string | null;
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
  jobId: string | null;
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
  date: string; // ISO date string (YYYY-MM-DD)
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
  /** Per-unit price used for valuation */
  unitPrice: number | null;
  /** The date from which the unit price was sourced (may differ from aggregation date due to carry-forward) */
  valueDate: string | null; // ISO local date (YYYY-MM-DD)
}

// ── Assets ───────────────────────────────────────────────────────────────────

export async function fetchAssets(): Promise<AssetDto[]> {
  const res = await apiFetch(`${BASE}/asset`);
  if (!res.ok) throw new Error(`Failed to fetch assets: ${res.status}`);
  return res.json();
}

export async function createAsset(
  req: CreateAssetRequest,
): Promise<CreateAssetResponse> {
  const res = await apiFetch(`${BASE}/asset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `Failed to create asset: ${res.status}`);
  }
  return res.json();
}

export async function deleteAsset(
  assetId: string,
): Promise<DeleteAssetResponse> {
  const res = await apiFetch(`${BASE}/asset/${assetId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(`Failed to delete asset: ${res.status}`);
  return res.json();
}

export async function renameAsset(
  assetId: string,
  name: string,
): Promise<AssetDto> {
  return updateAsset(assetId, { name });
}

export async function updateAsset(
  assetId: string,
  updates: { name?: string; provider?: string; providerData?: string | null },
): Promise<AssetDto> {
  const res = await apiFetch(`${BASE}/asset/${assetId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(updates),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `Failed to update asset: ${res.status}`);
  }
  return res.json();
}

// ── Transactions ─────────────────────────────────────────────────────────────

export async function createTransaction(
  assetId: string,
  req: CreateTransactionRequest,
): Promise<CreateTransactionResponse> {
  const res = await apiFetch(`${BASE}/asset/${assetId}/transaction`, {
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
  const res = await apiFetch(
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
  const res = await apiFetch(
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
  const res = await apiFetch(
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
  const res = await apiFetch(`${BASE}/agg/asset/${assetId}/all${params}`);
  if (!res.ok)
    throw new Error(`Failed to fetch all aggregations: ${res.status}`);
  return res.json();
}

// ── Portfolio aggregation ────────────────────────────────────────────────────

export interface PortfolioAssetValue {
  assetId: string;
  assetName: string;
  nativeCurrency: string;
  value: number;
}

export interface PortfolioAggregationPoint {
  date: string;
  totalValue: number;
  displayCurrency: string;
  assetCount: number;
  assetBreakdown: PortfolioAssetValue[];
}

export async function fetchPortfolioAggregation(
  start: string,
  end: string,
  displayCurrency?: string,
): Promise<PortfolioAggregationPoint[]> {
  const params = displayCurrency
    ? `?displayCurrency=${encodeURIComponent(displayCurrency)}`
    : "";
  const res = await apiFetch(
    `${BASE}/agg/portfolio/${start}/${end}${params}`,
  );
  if (!res.ok)
    throw new Error(`Failed to fetch portfolio: ${res.status}`);
  return res.json();
}

export async function fetchAllPortfolioAggregation(
  displayCurrency?: string,
): Promise<PortfolioAggregationPoint[]> {
  const params = displayCurrency
    ? `?displayCurrency=${encodeURIComponent(displayCurrency)}`
    : "";
  const res = await apiFetch(`${BASE}/agg/portfolio/all${params}`);
  if (!res.ok)
    throw new Error(`Failed to fetch portfolio: ${res.status}`);
  return res.json();
}

// ── Supported currencies ─────────────────────────────────────────────────────

export interface ClientConfig {
  currencies: string[];
  enabledMarketSources: string[];
}

let _configCache: ClientConfig | null = null;

export async function fetchConfig(): Promise<ClientConfig> {
  if (_configCache) return _configCache;
  const res = await apiFetch(`${BASE}/config`);
  if (!res.ok) throw new Error("Failed to fetch config");
  _configCache = await res.json();
  return _configCache!;
}

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
  aggregatedThrough: string | null; // ISO date or null if never aggregated
}

export async function fetchAssetStaleness(
  assetId: string,
): Promise<AssetStalenessResponse> {
  const res = await apiFetch(`${BASE}/asset/${assetId}/staleness`);
  if (!res.ok) throw new Error(`Failed to fetch staleness: ${res.status}`);
  return res.json();
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns YYYY-MM-DD for a Date object */
export function toLocalDateString(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
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

// ── Auth ──────────────────────────────────────────────────────────────────────

export async function login(
  email: string,
  password: string,
): Promise<UserResponse> {
  const res = await apiFetch(`${BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    const msg =
      res.status === 401
        ? "Invalid email or password"
        : `Login failed (${res.status})`;
    throw new Error(msg);
  }
  return res.json();
}

export async function signup(
  email: string,
  password: string,
  displayName: string,
): Promise<UserResponse> {
  const res = await apiFetch(`${BASE}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, displayName }),
  });
  if (!res.ok) {
    if (res.status === 409) throw new Error("Email already in use");
    const body = await res.text();
    throw new Error(body || `Signup failed (${res.status})`);
  }
  return res.json();
}

export async function logout(): Promise<void> {
  await apiFetch(`${BASE}/auth/logout`, { method: "POST" });
}

export async function fetchMe(): Promise<UserResponse> {
  const res = await apiFetch(`${BASE}/auth/me`);
  if (!res.ok) throw new Error("Not authenticated");
  return res.json();
}

export async function updateProfile(req: {
  displayName?: string;
  timezone?: string;
  displayCurrency?: string;
}): Promise<UserResponse> {
  const res = await apiFetch(`${BASE}/auth/profile`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`Failed to update profile: ${res.status}`);
  return res.json();
}

export async function changePassword(req: {
  currentPassword: string;
  newPassword: string;
}): Promise<void> {
  const res = await apiFetch(`${BASE}/auth/password`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    if (res.status === 401) throw new Error("Current password is incorrect");
    if (res.status === 400) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || "Invalid password");
    }
    throw new Error(`Failed to change password: ${res.status}`);
  }
}

// ── Admin ─────────────────────────────────────────────────────────────────────

export async function fetchAdminUsers(): Promise<UserResponse[]> {
  const res = await apiFetch(`${BASE}/admin/users`);
  if (!res.ok) throw new Error(`Failed to fetch users: ${res.status}`);
  return res.json();
}

export interface AdminCreateUserRequest {
  email: string;
  password: string;
  displayName: string;
  emailVerified?: boolean;
}

export async function adminCreateUser(
  request: AdminCreateUserRequest,
): Promise<UserResponse> {
  const res = await apiFetch(`${BASE}/admin/users`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `Failed to create user: ${res.status}`);
  }
  return res.json();
}

export async function adminDeleteUser(userId: string): Promise<void> {
  const res = await apiFetch(`${BASE}/admin/users/${userId}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `Failed to delete user: ${res.status}`);
  }
}

export async function adminForceReaggregateAll(): Promise<{
  status: string;
  usersProcessed: number;
}> {
  const res = await apiFetch(`${BASE}/admin/aggregations/force-all`, {
    method: "POST",
  });
  if (!res.ok) throw new Error(`Failed to reaggregate: ${res.status}`);
  return res.json();
}

export interface EntityCounts {
  users: number;
  assets: number;
  transactions: number;
  aggregates: number;
  exchangeRates: number;
  userRoles: number;
}

export async function adminFetchDebugCounts(): Promise<EntityCounts> {
  const res = await apiFetch(`${BASE}/admin/debug/counts`);
  if (!res.ok) throw new Error(`Failed to fetch counts: ${res.status}`);
  return res.json();
}

export type SystemInfo = Record<string, string | number | string[] | null>;

export async function adminFetchSystemInfo(): Promise<SystemInfo> {
  const res = await apiFetch(`${BASE}/admin/debug/system`);
  if (!res.ok) throw new Error(`Failed to fetch system info: ${res.status}`);
  return res.json();
}
