# Mixer

Mixer is a portfolio tracking application for managing financial assets, transactions, and aggregated valuations with multi-currency support.

## Tech Stack

- **Backend**: Kotlin 2.2 + Spring Boot 4 + Exposed ORM + JobRunr (async jobs)
- **Frontend**: React 19 + TypeScript 5.9 + MUI Joy UI + Vite 8 + MUI X Charts
- **Database**: H2 file-based (`jdbc:h2:file:./data/mixer`) for development, PostgreSQL (latest) for production. Data persists across restarts
- **Schema management**: Liquibase (`spring-boot-liquibase` + `liquibase-core`). Changelog at `src/main/resources/db/changelog/db.changelog-master.yaml`. Exposed auto-DDL is disabled (`spring.exposed.generate-ddl=false`). Changesets have `preConditions: onFail: MARK_RAN` so they skip gracefully on existing databases.
- **DB Compatibility**: Only use SQL features supported by both H2 and PostgreSQL. Notably: no `INSERT IGNORE` (H2 requires MySQL mode), use `upsert` (Exposed's `MERGE`/`ON CONFLICT`) instead. No H2-specific SQL syntax.
- **Sessions**: Spring Session JDBC (`@EnableJdbcHttpSession` in `SecurityConfig`), schema managed by Liquibase (changeset `2-spring-session`)
- **Build**: Gradle (Kotlin DSL), Java 21 target
- **Frontend build**: Yarn, `npx tsc --noEmit` for type checking
- **CI**: GitHub Actions → test + build Docker image → push to GHCR

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
│   ├── config/ConfigController.kt   # Public /config endpoint: serves supported currencies to frontend
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
├── security/SecurityConfig.kt       # Spring Security: session-based auth, CSRF disabled, CORS open, @EnableJdbcHttpSession + schema init
├── core/
│   ├── bootstrap/
│   │   ├── RecurringTaskScheduler.kt   # Scheduled tasks: aggregation refresh + FX backfill (intervals configurable)
│   │   └── BootstrapTaskExecutor.kt    # On startup: enqueues seed data (if mixer.data.seed.insert=true)
│   └── requests/                    # JobRunr job request classes
│       ├── InsertSeedDataRequest.kt  # Loads seed data from CSV files (skips if user already exists)
│       ├── RecomputeAssetAggregationRequest.kt  # Reaggregates single asset
│       ├── RecomputeUserAggregationRequest.kt   # Reaggregates all assets for a user
│       └── BackfillCurrencyPairRequest.kt       # Fetches historical FX rates (incremental, uses upsert)
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
├── db/changelog/
│   └── db.changelog-master.yaml  # Liquibase schema changelog
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
- Sessions stored in DB via Spring Session JDBC (`@EnableJdbcHttpSession` + `DataSourceInitializer` for schema)
- Session cookie name is `SESSION` (not `JSESSIONID`) — confirms Spring Session is active
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
- `RecurringTaskScheduler` runs aggregation refresh and FX backfill on configurable intervals.
- Intervals configured via `mixer.refresh.aggregations.*` and `mixer.refresh.fx.*` properties.
- Aggregation refresh calls `ensureAllAggregationsUpToDate()` which checks each asset's `aggregatedThrough` vs today (in user's timezone).
- FX backfill enqueues `BackfillCurrencyPairRequest` jobs for all configured currency pairs.
- Stale assets are re-aggregated automatically.

## FX Conversion

- `ExchangeRateHelper.findRate()` looks up rates with ±5-day fallback (backward first, then forward).
- If direct pair not found, tries inverse (e.g., USD/AUD → 1/AUD/USD).
- `findRatesInRange()` bulk-loads rates for a date range.
- `AggregateController.applyFxConversion()` converts aggregation values from asset's native currency to user's display currency.
- Portfolio aggregation sums all assets' values after FX conversion.
- Supported currencies configured via `mixer.fx.currencies` property (default: EUR, GBP, AUD, NZD, USD, HKD).
- FX data sourced from Oanda via `CurrencyService`.
- `RecurringTaskScheduler` enqueues incremental backfill jobs for all pairs on a configurable schedule.
- Backfill is incremental: finds the latest existing rate per pair, fetches only new data, uses `upsert` to handle overlaps.

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
- **Skips insertion if the seed user already exists** (safe for restarts with persistent DB).
- Controlled by `mixer.data.seed.insert` property (default: `true`).

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
4. **Session-based auth** — Uses Spring Security sessions, not JWT. Sessions persist across restarts via Spring Session JDBC.
5. **Schema managed by Liquibase** — All tables (app + session) defined in YAML changelog. Exposed auto-DDL disabled. Changesets use `preConditions` to skip on existing databases.
6. **Aggregation range queries use `>=` and `<=`** — Inclusive bounds to include today's data point.
6. **fillDateRange carries forward correctly** — Tracks `lastAmount` (holding) separately from `lastNativeValue` (monetary value).
7. **Shared DataSource** — Exposed ORM and Spring Session JDBC share the same `DataSource` bean (not separate connections).
8. **Incremental FX backfill** — Only fetches new rates since the latest existing record per currency pair.
9. **All config externalised** — Intervals, currencies, seed data toggle all via `application.properties` / env vars.

## Common Pitfalls

- `regenerateAggregatesForAsset()` requires a `userTimezone` parameter (second arg). Tests use `TimeZone.UTC`.
- Frontend `toLocalDateString()` must use local date components, not `toISOString()` (which returns UTC date).
- `Asset innerJoin User` works because `Asset.ownerId` has `.references(User.id)` — it's an Exposed infix function.
- When adding new currencies, update `mixer.fx.currencies` in `application.properties` and `CURRENCY_NAMES` in `App.tsx`.
- The `Database.connect()` bean in `MixerApplication.kt` must use the injected `DataSource`, not a hardcoded URL — otherwise Exposed and Spring Session use different databases.
- Spring Boot 4 removed session auto-configuration — `@EnableJdbcHttpSession` is required, and session tables are managed by Liquibase (changeset `2-spring-session`).
- Spring Boot 4 modularized autoconfiguration — Liquibase requires both `spring-boot-liquibase` (autoconfigure module) AND `liquibase-core`. Just `liquibase-core` alone won't trigger autoconfiguration.
- `RecurringTaskScheduler` initial delays prevent race conditions with Exposed DDL creation on startup.

## Coding Conventions

### Kotlin
- **One class per file.** Every data class, DTO, request/response object must live in its own file. Do not combine multiple classes in a single file unless there is an egregious reason.
- **Logger must be inside the class.** Declare `private val logger = KotlinLogging.logger {}` as a class member, not as a top-level `private val` outside the class.