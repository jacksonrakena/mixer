# Mixer

Mixer is a portfolio tracking application for managing financial assets, transactions, and aggregated valuations with multi-currency support.

## Tech Stack

- **Backend**: Kotlin 2.2 + Spring Boot + Exposed ORM + JobRunr (async jobs)
- **Frontend**: React 19 + TypeScript 5.9 + MUI Joy UI + Vite 8 + MUI X Charts
- **Database**: H2 file-based (`jdbc:h2:file:./data/mixer`) for development, PostgreSQL (latest) for production. Exposed auto-DDL, data persists across restarts
- **DB Compatibility**: Only use SQL features supported by both H2 and PostgreSQL. Notably: no `INSERT IGNORE` (H2 requires MySQL mode), use `upsert` (Exposed's `MERGE`/`ON CONFLICT`) instead. No H2-specific SQL syntax.
- **Sessions**: Spring Session JDBC (`@EnableJdbcHttpSession`), stored in H2 `SPRING_SESSION` tables
- **Build**: Gradle (Kotlin DSL), Java 21 target
- **Frontend build**: Yarn, `npx tsc --noEmit` for type checking

## Build & Test Commands

```bash
# Backend
./gradlew compileKotlin          # Compile backend
./gradlew compileTestKotlin      # Compile tests
./gradlew test                   # Run all tests (63 tests)

# Frontend
cd frontend && npx tsc --noEmit  # Type check frontend
cd frontend && yarn dev          # Dev server

# Full app
./gradlew bootRun                # Run backend (port 8080)
```

## Project Structure

```
src/main/kotlin/com/jacksonrakena/mixer/
├── MixerApplication.kt              # Spring Boot entry point
├── MixerConfiguration.kt            # Config properties (mixer.*), RestClient, JSON beans
├── controller/
│   ├── admin/AdminController.kt     # Admin endpoints: force reaggregation, create user, seed data
│   ├── auth/AuthController.kt       # Auth: login, signup, logout, profile CRUD, timezone change triggers reaggregation
│   ├── asset/
│   │   ├── AssetController.kt       # CRUD for assets
│   │   ├── AssetDto.kt              # Asset response DTO (includes staleAfter, aggregatedThrough)
│   │   ├── transaction/             # Transaction CRUD, marks assets stale on change
│   │   └── stale/                   # Staleness polling endpoint
│   └── values/AggregateController.kt # Aggregation data endpoints with FX conversion
├── data/
│   ├── AggregationService.kt        # Pure aggregation logic: groups transactions by day, computes holdings/values
│   ├── UserAggregationManager.kt    # Orchestrates aggregation: staleness checks, batch insert, market data
│   ├── ExchangeRateHelper.kt        # FX rate lookup with ±5-day fallback and inverse pair support
│   ├── AssetTransactionAggregation.kt # Data class for aggregation results (date is String "YYYY-MM-DD")
│   ├── market/                      # MarketDataProvider interface + Yahoo Finance implementation
│   ├── tables/concrete/             # Exposed table definitions: User, Asset, Transaction, UserRole
│   ├── tables/virtual/              # AssetAggregate (generated aggregation table)
│   └── tables/markets/              # ExchangeRate table
├── security/SecurityConfig.kt       # Spring Security: session-based auth, CSRF disabled, CORS open
├── core/
│   ├── bootstrap/
│   │   ├── AggregationRefreshScheduler.kt  # Runs every 5 min, refreshes stale aggregations
│   │   └── CurrencyBootstrap.kt     # On startup: enqueues FX backfill jobs + seed data
│   └── requests/                    # JobRunr job request classes
│       ├── InsertSeedDataRequest.kt  # Loads seed data from CSV files
│       ├── RecomputeAssetAggregationRequest.kt  # Reaggregates single asset
│       ├── RecomputeUserAggregationRequest.kt   # Reaggregates all assets for a user
│       └── BackfillCurrencyPairRequest.kt       # Fetches historical FX rates
├── upstream/                        # External API clients (Oanda FX service)
├── web/MdcRequestInterceptor.kt     # MDC logging context
└── logging/ShortMdcConverter.kt     # Log format helper

frontend/src/
├── main.tsx              # React entry, MUI Joy theme (primary=teal #009688)
├── App.tsx               # App shell: collapsible sidebar, nav, currency selector, user menu
├── AuthContext.tsx        # Auth state management (login/signup/logout/refreshUser)
├── api.ts                # All API client functions, types, and helpers
├── AssetChart.tsx         # Asset chart with staleness overlay, tooltips, date range selector
├── AssetList.tsx          # CreateAssetModal and DeleteAssetModal components
├── TransactionPanel.tsx   # Transaction list with create/delete, timezone-aware dates
└── pages/
    ├── HomePage.tsx       # Portfolio chart + asset breakdown cards, staleness polling
    ├── AssetPage.tsx      # Single asset view: chart + transactions
    ├── ProfilePage.tsx    # User profile: name, email, timezone (searchable dropdown), display currency
    ├── LoginPage.tsx      # Login form
    ├── SignupPage.tsx     # Signup form
    └── AdminPage.tsx      # Admin controls

src/main/resources/
├── application.properties  # Spring config
└── seed/
    ├── assets.csv          # 7 seed assets (AAPL, MSFT, VOO, TSLA, AMZN, NVDA, TEAM)
    └── transactions.csv    # ~193 seed transactions
```

## Database Schema

### User
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK, auto-generated |
| email | String | Unique |
| password_hash | String | BCrypt |
| display_name | String | |
| timezone | String | IANA timezone (e.g., "Australia/Sydney") |
| display_currency | String | Default "AUD" |
| created_at | Long | Epoch millis |
| email_verified | Boolean | |

### Asset
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| name | String | |
| owner_id | UUID | FK → User |
| currency | String | Native currency code |
| provider | String | "USER" or "YFIN" |
| provider_data | String? | JSON, e.g., `{"tickerCode":"AAPL"}` |
| stale_after | Long | Epoch millis, 0 = not stale |
| aggregated_through | LocalDate? | Last date aggregated, null = never |

### Transaction (table: `translog`)
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| timestamp | Long | Epoch millis, random time-of-day for seed data |
| asset_id | UUID | FK → Asset |
| transaction_type | Enum | Trade, Reconciliation |
| amount | Double? | Units (positive=buy, negative=sell) |
| value | Double? | Total monetary value of transaction |

### AssetAggregate (table: `generated_asset_aggregate`)
| Column | Type | Notes |
|--------|------|-------|
| asset_id | UUID | Composite PK |
| aggregation_period | Enum | DAILY |
| period_end_date | LocalDate | Composite PK |
| total_value | Double | Native currency value (holding × unit price) |
| holding | Double | Current holding amount |
| delta_trades | Double | Amount change from trades that day |
| delta_reconciliation | Double | Amount change from reconciliations |
| delta_other | Double | Other changes |
| unit_price | Double? | Per-unit price |
| value_date | LocalDate? | Date the unit price was sourced from |

### ExchangeRate
| Column | Type | Notes |
|--------|------|-------|
| base | String | Composite PK |
| counter | String | Composite PK |
| reference_date | LocalDate | Composite PK |
| rate | Double | |

### UserRole
| Column | Type | Notes |
|--------|------|-------|
| user_id | UUID | Composite PK, FK → User |
| role | String | Composite PK, e.g., "GLOBAL_ADMIN" |

## Authentication

- **Session-based** via Spring Security (not JWT)
- Login creates `UsernamePasswordAuthenticationToken`, saves to HTTP session
- Sessions stored in H2 via Spring Session JDBC (lost on restart due to in-memory DB)
- CSRF disabled, CORS allows all origins
- Admin routes (`/admin/**`) require `GLOBAL_ADMIN` role
- Frontend uses `AuthContext` to manage auth state, calls `fetchMe()` on mount to restore session

## Aggregation System

This is the most complex subsystem. Key concepts:

### How Aggregation Works
1. **`AggregationService.forwardAggregate()`** — Pure function that takes an asset's transactions and produces daily aggregation points from first transaction to today.
2. For each day: sums trades/reconciliations, tracks `currentHolding`, derives `unitPrice` from transaction value/amount or market data.
3. **Market-priced assets** (provider="YFIN"): prices come from Yahoo Finance via `MarketDataProvider`.
4. **User assets** (provider="USER"): unit price derived from transaction's value÷amount, carried forward.
5. If no unit price is available, `nativeValue = 0.0` (not the holding amount).

### Date Handling (Critical)
- **`AssetTransactionAggregation.date` is a `String`** in ISO format ("YYYY-MM-DD"), NOT an `Instant`.
- This was changed to fix a timezone bug where `Instant` serialization shifted dates by the user's UTC offset.
- `fromResultRow()` simply does `row[periodEndDate].toString()`.
- All date extraction in `AggregateController` uses `LocalDate.parse(it.date)`.
- Frontend keys dates by `d.date.slice(0, 10)`.

### Timezone Threading
- User's timezone (from `users.tz` column) is read once at the entry point and passed through the call chain.
- `UserAggregationManager.forceAggregateUserAssets()` reads timezone once, passes to each `regenerateAggregatesForAsset(assetId, userTimezone)`.
- `ensureAllAggregationsUpToDate()` does `Asset innerJoin User` to get per-user timezones.
- Timezone is used to: (1) determine what "today" is, (2) group transactions into days, (3) compute date spans.
- **No hardcoded timezone references** — always uses the user's configured timezone.

### Staleness & Reaggregation
- When a transaction is created/deleted, `Asset.staleAfter` is set to the transaction timestamp.
- A `RecomputeAssetAggregationRequest` JobRunr job is enqueued.
- The job calls `regenerateAggregatesForAsset()`, which clears and rebuilds all aggregates.
- After completion, `staleAfter` is reset to 0 and `aggregatedThrough` is set to today.
- Frontend polls `/asset/{id}/staleness` every 2 seconds while stale, shows "Recalculating…" overlay.
- **Changing user timezone** triggers full reaggregation of all user assets (detected in `AuthController.updateProfile()`).

### Staleness UI Behavior
- Charts show a blurred overlay with spinner when `staleAfter > 0` OR `aggregatedThrough === null`.
- Tooltips and crosshairs are hidden during recalculation.
- Cursor changes from crosshair to default during recalculation.
- Portfolio chart polls all assets and shows overlay if any asset is stale.

### Scheduled Refresh
- `AggregationRefreshScheduler` runs every 5 minutes.
- Calls `ensureAllAggregationsUpToDate()` which checks each asset's `aggregatedThrough` vs today (in user's timezone).
- Stale assets are re-aggregated automatically.

## FX Conversion

- `ExchangeRateHelper.findRate()` looks up rates with ±5-day fallback (backward first, then forward).
- If direct pair not found, tries inverse (e.g., USD/AUD → 1/AUD/USD).
- `findRatesInRange()` bulk-loads rates for a date range.
- `AggregateController.applyFxConversion()` converts aggregation values from asset's native currency to user's display currency.
- Portfolio aggregation sums all assets' values after FX conversion.
- Supported currencies: EUR, GBP, AUD, NZD, USD, HKD.
- FX data sourced from Oanda via `CurrencyService`.
- `CurrencyBootstrap` enqueues backfill jobs for all pairs on startup.

## Frontend Architecture

### App Shell (App.tsx)
- Full-height collapsible sidebar with:
  - "Mixer" branding at top
  - "Dashboard" nav item
  - "Assets" section listing each asset as a nav link
  - "Create asset" button (opens `CreateAssetModal`)
  - Display currency selector (shows code + full name)
  - User avatar/name/email at bottom with popup menu (Profile, Admin, Logout)
  - Collapse toggle button (full-width when expanded)

### Charts
- Use MUI X Charts `LineChart` component.
- Color: `var(--joy-palette-primary-500)` (teal from theme).
- Custom tooltip system: tracks mouse position, finds nearest data point via SVG path parsing, renders positioned tooltip box.
- `fillDateRange()` fills gaps in data with carry-forward values (last known value/holding/price).
- Date range selector: 7D, 30D, 90D, 1Y, All.
- Portfolio chart legend is hidden.

### API Types (api.ts)
- `AssetDto`: id, name, ownerId, currency, staleAfter, aggregatedThrough
- `AssetAggregation`: assetId, date (string), amount, nativeValue, displayValue, fxConversion, unitPrice, valueDate
- `PortfolioAggregationPoint`: date, totalValue, displayCurrency, assetCount, assetBreakdown
- `toLocalDateString()` uses local date components (not `toISOString()` which gives UTC).

### Theme
- MUI Joy UI with custom teal primary palette (#009688 at 500).
- Defined in `main.tsx`.

## Seed Data

- `InsertSeedDataRequest` loads from CSV files in `src/main/resources/seed/`.
- `assets.csv`: 7 assets with ref, name, currency.
- `transactions.csv`: ~193 transactions with asset_ref, days_ago, type, amount, value.
- Transactions get random time-of-day offsets (fixed seed 42 for reproducibility).
- Creates admin user (admin@mixer.local / admin123) with GLOBAL_ADMIN role.
- Enqueues aggregation job after seeding.

## Background Jobs (JobRunr)

- Dashboard at port 8000.
- Job types:
  - `RecomputeAssetAggregationRequest(assetId)` — single asset reaggregation
  - `RecomputeUserAggregationRequest(userId)` — all assets for a user
  - `BackfillCurrencyPairRequest(base, counter)` — historical FX rates
  - `InsertSeedDataRequest` — seed data loading
- Admin force-reaggregation enqueues per-user jobs (non-blocking).
- `forceAggregateUserAssets()` gracefully handles unknown user IDs (returns early).

## Key Design Decisions

1. **Dates as strings, not Instants** — Aggregation dates are ISO strings to avoid timezone-dependent serialization bugs.
2. **nativeValue fallback is 0.0** — If no unit price is available, value is 0 (not the holding amount).
3. **Timezone read once, passed through** — Avoids repeated DB queries during aggregation.
4. **Session-based auth** — Uses Spring Security sessions, not JWT. Sessions lost on H2 restart.
5. **Aggregation range queries use `>=` and `<=`** — Inclusive bounds to include today's data point.
6. **fillDateRange carries forward correctly** — Tracks `lastAmount` (holding) separately from `lastNativeValue` (monetary value).

## Common Pitfalls

- The H2 database is in-memory (`jdbc:h2:mem:test`). All data is lost on backend restart.
- `regenerateAggregatesForAsset()` requires a `userTimezone` parameter (second arg). Tests use `TimeZone.UTC`.
- Frontend `toLocalDateString()` must use local date components, not `toISOString()` (which returns UTC date).
- `Asset innerJoin User` works because `Asset.ownerId` has `.references(User.id)` — it's an Exposed infix function.
- When adding new currencies, update the pairs list in `CurrencyBootstrap` and `CURRENCY_NAMES` in `App.tsx`.