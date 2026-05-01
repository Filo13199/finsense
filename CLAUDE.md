# Finsense — Project Reference

## What this app does
Android-only personal finance tracker. Reads bank SMS automatically, extracts transactions, categorizes them, and tracks budgets. All data stays on device (SQLite). No network calls, no cloud sync.

## Tech stack
| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Android standard |
| UI | Jetpack Compose + Material 3 | Modern declarative UI, no XML layouts |
| Database | Room (SQLite) | On-device only, type-safe queries |
| DI | Hilt | Android-standard, less boilerplate than manual |
| Background work | WorkManager + HiltWorker | SMS import on first permission grant |
| Navigation | Navigation Compose | Single-activity, bottom nav |
| Async | Coroutines + Flow | StateFlow for UI state |

## Package structure
```
com.finsense/
├── data/
│   ├── entity/         Room entities + enums
│   ├── dao/            Room DAOs
│   ├── db/             FinsenseDatabase (Room) + TypeConverters + migrations
│   ├── model/          BudgetWithSpent (computed view model)
│   ├── preferences/    AppCurrency enum, UserPreferences (SharedPreferences wrapper)
│   └── repository/     TransactionRepository, CategoryRepository, BudgetRepository
├── sms/
│   ├── SmsParser       Regex extraction: amount+currency, debit/credit type, vendor, date
│   ├── SmsReader       Reads Telephony.Sms.Inbox content provider (historical SMS)
│   └── SmsReceiver     BroadcastReceiver — real-time incoming SMS
├── worker/
│   └── SmsSyncWorker   HiltWorker — imports last 3 months of SMS on first run
├── di/
│   └── AppModule       Hilt module — Room DB, DAOs, default category seeding
├── ui/
│   ├── theme/          Color, Theme (Material 3), Type
│   ├── navigation/     Navigation.kt — NavGraph + bottom nav Scaffold
│   ├── dashboard/      DashboardScreen + DashboardViewModel
│   ├── transactions/   TransactionsScreen + TransactionsViewModel
│   ├── budget/         BudgetScreen + BudgetViewModel
│   ├── categories/     CategoriesScreen + CategoriesViewModel
│   ├── settings/       SettingsScreen + SettingsViewModel (currency picker, month start day)
│   └── permission/     PermissionScreen (onboarding, SMS permission request)
├── FinsenseApplication  @HiltAndroidApp + WorkManager Configuration.Provider
└── MainActivity        Permission launcher, triggers SmsSyncWorker on grant
```

## Database schema

### transactions
| Column | Type | Notes |
|---|---|---|
| id | Long PK autoGenerate | |
| amount | Double | Always positive |
| type | TEXT (enum) | DEBIT or CREDIT |
| vendor | String | Extracted from SMS or entered manually |
| description | String | Full SMS body (truncated) or manual note |
| categoryId | Long? FK → categories.id | SET_NULL on delete |
| date | Long | Unix timestamp ms; parsed from SMS body when available, falls back to SMS metadata or system time |
| currency | String | ISO 4217 code (e.g. "EGP", "USD"); added in DB v2 |
| smsId | String? UNIQUE | SMS content provider _id; null for manual entries |
| smsBody | String? | Original SMS text |
| isManual | Boolean | false = from SMS, true = user-entered |

**DB version: 4.** `Migration1To2` adds the `currency` column (back-filled from SharedPreferences). `Migration2To3` adds the `recurringId` column and creates the `recurring_transactions` table. `Migration3To4` adds the `excludedCategoryIds` column to `budgets`. Manual transactions default to the preferred currency at the time of entry.

### categories
| Column | Type | Notes |
|---|---|---|
| id | Long PK autoGenerate | |
| name | String | e.g. "Food & Dining" |
| icon | String | Emoji character |
| color | Long | ARGB as unsigned Long (e.g. 0xFFE57373L) |
| keywords | String | Comma-separated; used for auto-categorization |

11 default categories seeded on first DB creation via `AppModule.seedDefaultCategories()` using raw SQL in the Room callback (avoids circular DI dependency).

### budgets
| Column | Type | Notes |
|---|---|---|
| id | Long PK autoGenerate | |
| name | String | Display name |
| categoryId | Long? FK → categories.id | CASCADE on delete; null = all categories |
| amount | Double | Budget limit |
| period | TEXT (enum) | DAILY, WEEKLY, MONTHLY |
| excludedCategoryIds | String | Comma-separated category IDs to exclude; only used when `categoryId` is null; added in DB v4 |

Budget "spent" is **never stored** — always computed dynamically by summing DEBIT transactions in the category for the current period. This avoids stale data.

When `categoryId` is null and `excludedCategoryIds` is non-empty, `BudgetRepository` calls `TransactionDao.totalDebitForPeriodExcluding()` which uses `AND (categoryId IS NULL OR categoryId NOT IN (:excludedIds))`. The repository branches on `excludedIds.isNotEmpty()` so the `NOT IN` query is never called with an empty list (which would cause a Room binding error).

### vendors
| Column | Type | Notes |
|---|---|---|
| id | Long PK autoGenerate | |
| name | String | Canonical vendor name |
| aliases | String | Comma-separated alternative names |
| categoryId | Long? FK → categories.id | SET_NULL on delete |

## Key architectural decisions

### Budget tracking is computed, not stored
`BudgetWithSpent` is a derived model (not a Room entity). Spent amount = `SUM(amount) WHERE type=DEBIT AND categoryId=X AND date BETWEEN periodStart AND periodEnd`. Avoids sync issues if transactions are deleted or re-categorized.

### Dashboard/Budget reactive updates
`DashboardViewModel` and `BudgetViewModel` observe `transactionRepository.getRecentWithCategory()` (a Room Flow that re-emits on any change to the `transactions` table) and call `refreshTotalsAndBudgets()` / `refreshBudgets()` inside `onEach`. This means monthly totals and budget "spent" amounts update live when SMS are imported in the background or when a manual transaction is added from another screen. Both ViewModels also observe `userPreferences.currencyFlow` and `userPreferences.monthStartDayFlow` and recalculate on change.

### Auto-categorization order
1. Vendor name → exact/partial match in `vendors` table → use its `categoryId`
2. Combined vendor+description text → keyword match against `categories.keywords`
3. Falls through uncategorized (categoryId = null)

### SMS deduplication
Each SMS-derived transaction stores `smsId` (the `_id` from `Telephony.Sms.Inbox`). The column has a UNIQUE index. `TransactionDao.insert()` uses `OnConflictStrategy.IGNORE`, so re-importing the same SMS is a no-op.

### Room `@Transaction` vs entity `Transaction` name clash
`TransactionDao.kt` imports the Room annotation as an alias:
```kotlin
import androidx.room.Transaction as RoomTransaction
```
This is required because `import androidx.room.*` and `import com.finsense.data.entity.Transaction` would both put `Transaction` in scope, and KSP would resolve `@Transaction` to the entity (not an annotation class), causing a build error.

### WorkManager + Hilt
`FinsenseApplication` implements `Configuration.Provider` to supply `HiltWorkerFactory`. The default WorkManager auto-init is disabled in `AndroidManifest.xml` via the `InitializationProvider` meta-data removal. Without this, WorkManager initializes before Hilt and ignores the custom factory.

### Currency preference
`AppCurrency` is an enum in `data/preferences/` with two values: `EGP` (Egyptian Pound) and `INR` (Indian Rupee). Each entry owns its own amount regex patterns and a `formatAmount(Double): String` method. Adding a new currency means adding one enum entry with its patterns — nothing else changes.

`UserPreferences` wraps SharedPreferences and exposes two `StateFlow`s:
- `currencyFlow: StateFlow<AppCurrency>` — selected currency
- `monthStartDayFlow: StateFlow<Int>` — the day of month (1–28) on which the "month" period begins (default 1)

ViewModels observe both flows and recalculate totals/budgets immediately when either changes. No DataStore dependency — plain SharedPreferences.

### Month start day
`UserPreferences.monthStartDay` (1–28, default 1) allows the period used for monthly totals and MONTHLY budgets to be aligned to any day of the month — e.g. a salary date of the 25th means the period runs from the 25th of one month to the 24th of the next.

`TransactionRepository.periodMonthRange(monthStartDay)` and `BudgetRepository.customMonthRange(monthStartDay)` implement the same algorithm:
- If today's day-of-month ≥ `monthStartDay`: start = `monthStartDay` of the current month
- Otherwise: start = `monthStartDay` of the previous month
- End = start + 1 month − 1 day (23:59:59.999)

Setting it to 1 produces the standard calendar month. The setting is exposed in the Settings screen via a `−`/`+` picker (capped to 1–28 to be safe across all months). DAILY and WEEKLY budget periods are unaffected.

### Multi-currency / international transactions
`SmsParser.extractAmountAndCurrency()` tries the preferred-currency patterns first, then falls back to a generic ISO-code pattern that catches `USD 50.00`, `EUR 1,200.00`, `GBP 45`, etc. The parsed currency code is stored in `Transaction.currency`.

Totals and budget calculations are filtered by `currency = :currency` in the DAO queries, so foreign-currency transactions are stored and visible in the transaction list but are excluded from the monthly totals and budget progress. No exchange-rate conversion is performed (the app has no network access).

Transaction rows display amounts in the preferred currency's symbol for matching rows, and as `"<CODE> <amount>"` (e.g. `−USD 50.00`) for foreign rows.

### SMS vendor extraction
`SmsParser.vendorPatterns` is an ordered list of regexes tried in sequence; first match wins. Patterns currently (in priority order):

1. `at\s+([0-9][A-Za-z0-9 &.\-'_]{1,49}?)\s+on\b` — digit-starting names between "at" and "on" (e.g. `at 30 NORTH Mall O on`)
2. `(?:at|At)\s+([A-Z][A-Za-z0-9 &.\-'_]{2,40}?)(?:\s+on\b|\s+via\b|\s+\(|\.|;|\|)` — uppercase-starting names between "at" and a delimiter
3. Additional patterns for other SMS formats

Patterns 1 and 2 are non-overlapping by first character (`[0-9]` vs `[A-Z]`), so there is no ambiguity. Add new patterns before the catch-all entries and keep them most-specific-first.

### SMS filtering
`SmsParser.isFinancialSms()` checks two conditions before parsing:
1. Sender ID matches alphanumeric bank-code pattern (`VM-HDFCBK`, `SBIINB`, etc.) — filters out personal contacts
2. Body contains financial keywords (debited, credited, EGP, INR, USD, EUR, GBP, balance, transaction, etc.)

### SMS date extraction
`SmsParser` extracts the transaction date from the SMS body using the pattern `DD/MM/YY at hh:mm` (24 h, e.g. `14/04/26 at 16:08`). Parsed with `DateTimeFormatter.ofPattern("dd/MM/yy 'at' HH:mm")` and converted to epoch ms using `ZoneId.systemDefault()`. If extraction fails, falls back to the SMS metadata timestamp (`sms.date`) in `SmsSyncWorker`, or `System.currentTimeMillis()` in `SmsReceiver`.

### Color storage
Category colors stored as `Long` (not `Int`) to avoid signed overflow with full-alpha ARGB values. `0xFFE57373L` = valid Long; `0xFFE57373.toInt()` = negative Int which causes issues in Compose's `Color(Int)` constructor. Use `Color(category.color)` in Compose (accepts Long).

## Build notes

### Generating APK
```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

### Installing to device
```bash
~/Android/Sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
```

### App icons
Icons must be in `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` as `ic_launcher.png` and `ic_launcher_round.png`. Generate via Android Studio: right-click `res/` → New → Image Asset. Currently removed from AndroidManifest to unblock builds without icons.

## Bugs fixed during development
| Error | Cause | Fix |
|---|---|---|
| `resource android:style/Theme.Material.Light.NoTitleBar not found` | `NoTitleBar` doesn't exist in `Theme.Material` family | Changed to `NoActionBar` |
| `Entities must have a usable public constructor` | Stray `annotation` keyword: `annotation data class Transaction` | Removed `annotation` keyword |
| `Illegal annotation class 'Transaction'` | `@Transaction` resolved to entity class instead of Room annotation due to name clash | Aliased Room annotation: `import androidx.room.Transaction as RoomTransaction` |
| Monthly totals and budget "spent" never updated after initial load | `loadMonthlyTotals()` ran once at ViewModel init; `loadBudgets()` only re-ran on budget table changes, not transaction changes | Replaced with `observeRecentTransactions().onEach { refreshTotalsAndBudgets() }` — Room re-emits that Flow on any `transactions` table change |
| FAB not visible in Categories screen | `CategoriesScreen` used a nested `Scaffold` with `floatingActionButton`; the outer bottom-nav `Scaffold` caused the inner FAB to be rendered outside the visible area | Replaced nested `Scaffold` with `Box` + `FloatingActionButton` aligned to `BottomEnd` using `contentPadding.calculateBottomPadding()` — same pattern as `BudgetScreen` |

## Permissions
- `READ_SMS` — read historical SMS from content provider (dangerous, runtime prompt)
- `RECEIVE_SMS` — receive incoming SMS via BroadcastReceiver (dangerous, runtime prompt)
- `android.hardware.telephony` declared with `required="false"` — allows install on tablets

## Screens flow
```
App launch → PermissionScreen
  ├─ Permission granted → SmsSyncWorker triggered → Dashboard
  └─ Skip → Dashboard (manual entry only)

Bottom nav: Dashboard | Transactions | Budgets | Categories | Settings
```

### Amount formatting
`AppCurrency.formatAmount(Double)` is the single source of truth for full-precision amount display. It uses `NumberFormat.getNumberInstance(Locale.US)` with 2 decimal places and prepends the currency symbol (e.g. `EGP 1,500.00`, `₹ 1,500.00`). All screens receive the current `AppCurrency` via their ViewModel's UI state — there are no hardcoded locale or currency formatters in the UI layer.

`AppCurrency.formatCompact(Double)` is used in summary contexts where horizontal space is constrained (currently: `AmountColumn` in the Dashboard monthly overview card). It abbreviates large numbers to avoid overflow in the 3-column layout:
- `< 10,000` → full format (`EGP 9,999.00`)
- `>= 10,000` → K suffix (`EGP 150K`, `EGP 15.5K`)
- `>= 1,000,000` → M suffix (`EGP 1.5M`, `EGP 2M`)

Negative amounts are handled correctly (e.g. `EGP -1.5M` for a negative balance).
