# CLAUDE.md – context for working on this repo

Read this first. It is the handover so a new session is up to speed without re-asking.
Details live in `docs/`; this file says **what matters, what was decided, and why**.

---

## 1. What this is

- **Project:** ALBE (Albaraka Egypt) **Customer Limit Management** on **Temenos Transact (T24) / TAFJ**, Java 21, Eclipse Java project (no Maven/Gradle). Source: `src/`, package root `com.temenos.ALBE.CustomerLimit`.
- **Spec:** HLSD *Albaraka T24 project – Account module – 3 CBE Mandate CRs V2*, section **7.2.1** (customer limit creation; daily/monthly limit check; maximum CIF balance check; overrides).
- **Repo:** `shunmugarajag/ALBECustomerLimitClaude`, branch **`main`** (the only branch used). The old repo `shunmugarajag/ALBECustomerLimit` (PR #1) is **not used any more**.
- **User:** T24 Java developer, 15 years' experience, works in Eclipse on Windows (`D:\Claude\ALBECustomerLimitClaude`). Often asks to have things "explained with easy words".

## 2. Rules from the user – always apply

| Rule | Note |
|---|---|
| **Do not change the table structure** – it is pre-approved | No new fields, no new tables. (OVERRIDE.IDS and SYSTEM.DATE were added once and **removed** for this reason.) |
| **Ask on design points, don't assume** | Use a short question with options; if unanswered, state the assumption made. |
| MVC layers; hook classes are `…Controller` | controller / service / repository / model / view / validation / util / constants |
| **All DB access only in `repository/CustomerLimitRepository`**, each call logged | no `DataAccess`/`Session`/Table class anywhere else |
| **No inner classes, no interfaces** (no lambdas-as-interfaces design, no nested types) | plain public classes; `Comparator` lambda in a `sort` call is fine |
| **No `final` classes except `Constants`** | utility classes use a private constructor instead |
| All literals in `constants/Constants.java` | table names, ids, override ids, messages, selection field names, limits |
| Validate a request **once**, in the controller | services trust it; common value checks in `util/ValidationUtil` |
| Logging only through `util/LogUtil` (standard format) | `ALBE.CUSTOMER.LIMIT \| Class.method \| EVENT \| details` |
| Server date for counting | kept in the activity REMARKS (see 4) |
| Don't critique the user's **sample** routines | user said "ignore gaps in the sample" |
| Keep `'\u00FE'` / `'\u00FD'` as **ASCII escapes** | file tools turn them into raw bytes – check with `LC_ALL=C grep -P '[^\x00-\x7F]' -r src` (must print nothing) |
| Commit + push to `main` when work is done | end commit messages with the attribution lines the session provides |

## 3. Tables (approved structure – read/write only these fields)

| Table | Id | Fields used |
|---|---|---|
| EB.ALBE.GEN.PARAM | **`CUSTOMER.LIMIT`** (not SYSTEM) | KEY.NAME / KEY.VALUE: CCY, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, MAX.CIF.BALANCE |
| EB.ALBE.CIF.LIMIT | customer id | CCY + the 5 limits (same names) |
| EB.ALBE.UTILISED.LIMIT | daily `<cust>-YYYYMMDD`, monthly `<cust>-YYYYMM` | UTILISED.CREDIT/DEBIT.LIMIT (written); UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT (read only, monthly) |
| EB.ALBE.TXN.DETAILS.LIMIT | **`<cust>-<YYYYMMDD>-<AAA id>`** (HLSD format) | ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE, TXN.AMOUNT – nothing else |

Generated API classes: `com.temenos.t24.api.records.ebalbe…` / `com.temenos.t24.api.tables.ebalbe…` (need the generated table JARs on the Eclipse build path).

## 4. How it works (one screen)

| Function | Hook (EB.API) | Flow |
|---|---|---|
| **Limit creation** | `CustomerLimitCreationController` – VERSION CUSTOMER,ALBE.CORP **Auth routine**, `postUpdateRequest` | at customer authorisation: if `cifLimitExists` = FALSE copy GEN.PARAM `CUSTOMER.LIMIT` → CIF.LIMIT. TRUE (amendment) or null (read failed) → **never overwrite**. No delete flow. |
| **Daily / monthly** | `DailyMonthlyLimitController` – ACTIVITY.API on ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT | `defaultFieldValues` (UNAUTH): append `ALBE.LIMIT.DATE=YYYYMMDD` to AAA **REMARKS** (first input only). `validateRecord` (UNAUTH): check + overrides. `postCoreTableUpdate`: UNAUTH count (earlier input of same AAA reversed first), DELETE reverse, AUTH nothing, AUTH-REV reverse (fallback: today if no record). |
| **Maximum CIF** | `MaximumCifLimitController` – ACTIVITY.API, credit only, `validateRecord` | sum of WORKING.BALANCE of all customer accounts (converted) + credit vs MAX.CIF.BALANCE |
| **Overrides** | `view/CustomerLimitOverride.raiseFor` on TXN.AMOUNT | `id FM p1 VM p2`; acceptable overrides (not errors) |
| **Reports** | `CustomerLimitSummary/Period/TxnEnquiryController` – NOFILE `Enquiry.setIds` | return `List<String>` of `*`-separated lines (T24 requires strings; model rows used inside, view formats at the end) |

Key rules: date for every step = REMARKS tag (else today); limit check is **above** limit only; utilised minus own earlier input on re-input; monthly = migrated + utilised; totals floored at 0; amounts in **limit currency** (converted, 2 dp, stored converted – reversals never reconvert); missing CIF.LIMIT record or blank limit → `EB-ALBE.CIF.LIMIT.NOT.DEFINED` once; technical failure → logged, transaction **not** blocked.

## 5. Code map

```
controller/   CustomerLimitCreationController, DailyMonthlyLimitController, MaximumCifLimitController,
              CustomerLimitSummaryEnquiryController, CustomerLimitPeriodEnquiryController, CustomerLimitTxnEnquiryController
service/      DailyMonthlyLimitService, MaximumCifLimitService, CustomerLimitCreationService, CustomerLimitReader,
              CustomerLimitUtilizationUpdater (record ids, utilisedAmount/migratedAmount, add/reverse),
              TxnDetailsLimitRecorder (record/findOriginal/delete, id <cust>-<date>-<AAA>),
              CurrencyConversionService, CustomerLimitEnquiryService (read only)
repository/   CustomerLimitRepository – 3 constructors (ActivityLifecycle / RecordLifecycle / Enquiry); all reads/writes/selects
model/        CustomerLimitDTO (isCredit/getAmount/logKey), Limits, LimitBreach, LimitSummaryRow, LimitPeriodRow, LimitTxnRow
view/         CustomerLimitOverride, CustomerLimitEnquiryView (column order documented in the class)
validation/   CustomerLimitRequestValidator, CustomerLimitEnquiryValidator
util/         LogUtil (info/error/detail; detail switch LOG_DETAIL_ENABLED; never throws), ValidationUtil (isBlank, nvl, toAmount, toOptionalAmount, toDate/toMonth strict, …)
constants/    Constants (the only final class)
```

## 6. Decisions log (why things are the way they are)

| Decision | Why / history |
|---|---|
| Count utilisation at **input** (UNAUTH); reverse on DELETE and AUTH-REV | user: unauthorised transactions must count; rejected and reversed ones must be taken back; accepted-override transactions count |
| Breach → **acceptable override**, incl. missing record / blank limit | user changed "reject with error" to override |
| Counted date in **AAA REMARKS** tag, TXN.DETAILS id `<cust>-<date>-<AAA>` | user's idea; keeps table structure; direct read by key even for reversal on a later day (an earlier SYSTEM.DATE field was removed) |
| Creation as **Auth** routine (HLSD says After Unau) | no delete flow needed; amendment never overwrites (`cifLimitExists` TRUE/FALSE/null) |
| GEN.PARAM record id **`CUSTOMER.LIMIT`** | user; was SYSTEM |
| Currency conversion to the limit CCY; txn CCY = AA.ARRANGEMENT CURRENCY | user; rate source still pending (E1) |
| Overridden-transactions report reads **AA.ARRANGEMENT.ACTIVITY OVERRIDE** at report time (live, then `$NAU`) | no table field allowed; "overridden" = limit-exceed overrides only (`Constants.LIMIT_EXCEED_OVERRIDES`) |
| Enquiry range caps 92 days / 24 months; TXN report needs customer or date | protect online system |
| Per-record trace logs at `detail` level | performance; `LOG_DETAIL_ENABLED=false` in production |
| Strict date/month parsing | Java's default turned 20261131 into 30-Nov |

HLSD deviations to be reflected by the BA: creation as Auth routine, GEN.PARAM instead of EB.ALBE.GEN.CIF.LIMIT, counted date in REMARKS, currency conversion.

## 7. Open items (see `docs/GAPS.md`)

- **E1 exchange rate**: `CustomerLimitRepository.readExchangeRate` returns null until the bank answers (table – suggested core CURRENCY –, field, mid/buy/sell, market, quote direction). Until then other currencies → `EB-ALBE.EXCH.RATE.MISSING`, check skipped.
- **G1** JVM time zone for `LocalDate.now()` (use T24 TODAY or Cairo zone) – code only
- **G2** lost update on parallel inputs (needs lock API) – team decision
- **G3** partial writes (fail the transaction or let through) – team decision
- **G4** REMARKS tag removed on re-input → double count – code only
- **G5** REMARKS max length – confirm length
- **G6** blank CIF.LIMIT CCY → use GEN.PARAM CCY – code only
- **G7** accounts in other companies, **G8** ACCOUNT SELECT fallback, **G9** credit/debit by activity name
- Not yet verified in DEV: API calls in `docs/TECHNICAL_REFERENCE.md` §12 (validateRecord/getValidationResponse, setOverride format, getArrangementActivityId, defaultFieldValues + setRemarks, getOverride, getConcatValues, generated getters, "record not found" exception text → `RECORD_NOT_FOUND_MARKERS`).
- Pending from the bank: one-off CIF.LIMIT load for existing customers; HLSD update; TXN.DETAILS housekeeping.
- Last offer to the user not yet answered: fix G1, G4, G5, G6 now.

## 8. Testing (outside T24)

- No TAFJ JARs here. Tests were done with **stand-in classes outside the repo** (`/tmp/stubs/src`, minimal versions of TField, TStructure, DataAccess, Session, hooks, generated records/tables, logger) and small test programs with an in-memory repository (`class MemRepo extends CustomerLimitRepository` overriding the read/write/select methods). **Never commit stubs** – the user confirmed they must stay out of the repo.
- Compile check: `javac -d out $(find <stubs>/src src -name '*.java')` – must give no errors. If the sandbox was reset, recreate the stubs from the imports in `src`.
- Covered so far: credit/debit daily & monthly breach incl. migrated, re-input, rejection, reversal on a later day, blank limit, missing record, conversion & missing rate, max CIF mixed currencies, request validation, creation (exists / not found / read failed), REMARKS tag (first input, re-input, AUTH-REV untouched, bad date), all three reports incl. filters and selection errors.
- Not covered: controllers with real T24 objects → DEV tests in `docs/T24_CONFIGURATION.md` §13 and §14.4.

## 9. Docs map

| File | Content |
|---|---|
| `README.md` | overview, MVC table, routines, tables, overrides, logging |
| `docs/TECHNICAL_REFERENCE.md` | architecture, classes, flows, decisions, HLSD deviations, APIs to verify, testing |
| `docs/T24_CONFIGURATION.md` | T24 set-up steps 1–15 (tables, JARs, GEN.PARAM, EB.OVERRIDE, EB.API, VERSION, ACTIVITY.API, versions, logging, go-live data, exchange rate, tests, **reports**, **menu & access**) |
| `docs/REPORTS.md` | the three reports: selection, columns, sample data, how they work |
| `docs/GAPS.md` | known gaps, fixes without table change, exchange-rate questions |

When code changes, keep these docs in step (README class/routine tables, TECHNICAL_REFERENCE change history, T24_CONFIGURATION set-up, REPORTS columns) and update this file's decisions/open items.
