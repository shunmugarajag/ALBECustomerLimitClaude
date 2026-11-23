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
| MVC layers; hook classes are `…Controller` | controller (sub-packages customer / activity / enquiry) / service / repository / model / view / validation / util / constants |
| **All DB access only in `repository/CustomerLimitRepository`**, each call logged | no `DataAccess`/`Session`/Table class anywhere else |
| **No inner classes, no interfaces** (no lambdas-as-interfaces design, no nested types) | plain public classes; `Comparator` lambda in a `sort` call is fine |
| **No `final` classes except `Constants`** | utility classes use a private constructor instead |
| All literals in `constants/Constants.java` | table names, ids, override ids, messages, selection field names, limits |
| Validate a request **once**, in the controller | services trust it; common value checks in `util/ValidationUtil` |
| Logging only through `util/LogUtil` (standard format) | `ALBE.CUSTOMER.LIMIT \| Class.method \| EVENT \| details` |
| **T24 date** for counting (not the server clock) | REMARKS tag → ORG.SYSTEM.DATE → T24 TODAY (see 4) |
| Don't critique the user's **sample** routines | user said "ignore gaps in the sample" |
| Keep `'\u00FE'` / `'\u00FD'` as **ASCII escapes** | file tools turn them into raw bytes – check with `LC_ALL=C grep -P '[^\x00-\x7F]' -r src` (must print nothing) |
| Commit + push to `main` when work is done | end commit messages with the attribution lines the session provides |

## 3. Tables (approved structure – read/write only these fields)

| Table | Id | Fields used |
|---|---|---|
| EB.ALBE.GEN.PARAM | **`CUSTOMER.LIMIT`** (not SYSTEM) | KEY.NAME / KEY.VALUE (DEV record 23-Nov): **CURRENCY**, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, **MAXIMUM.OVERALL.CIF.BALANCE** (`Constants.PARAM_KEY_*`; CURRENCY → CIF.LIMIT CCY, MAXIMUM.OVERALL… → MAX.CIF.BALANCE) |
| EB.ALBE.CIF.LIMIT | customer id | CCY + the 5 limits (same names) |
| EB.ALBE.UTILISED.LIMIT | daily `<cust>-YYYYMMDD`, monthly `<cust>-YYYYMM` | UTILISED.CREDIT/DEBIT.LIMIT (written); UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT (read only, monthly) |
| EB.ALBE.TXN.DETAILS.LIMIT | **`<cust>-<YYYYMMDD>-<root ref>`** (root ref = FT/TT behind the AAA, else AAA id, or entry TRANS.REFERENCE; no .CR/.DR) | ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE, TXN.AMOUNT – nothing else |

Generated API classes: `com.temenos.t24.api.records.ebalbe…` / `com.temenos.t24.api.tables.ebalbe…` (need the generated table JARs on the Eclipse build path).

## 4. How it works (one screen)

| Function | Hook (EB.API) | Flow |
|---|---|---|
| **Limit creation** | `CustomerLimitCreationController` – VERSION CUSTOMER,ALBE.CORP **Auth routine**, `postUpdateRequest` | at customer authorisation: if `cifLimitExists` = FALSE copy GEN.PARAM `CUSTOMER.LIMIT` → CIF.LIMIT. TRUE (amendment) or null (read failed) → **never overwrite**. No delete flow. |
| **Daily / monthly** | `DailyMonthlyLimitController` – ACTIVITY.API, **any activity** (normally ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT); mapping in `ActivityRequestBuilder` | `defaultFieldValues` (UNAUTH, counted activities only): append `ALBE.LIMIT.DATE=<T24 date>` to AAA **REMARKS** (first input only). `validateRecord` (UNAUTH): check + overrides. `postCoreTableUpdate`: UNAUTH count (earlier input of same AAA reversed first), DELETE reverse, AUTH nothing, AUTH-REV reverse (fallback: today if no record). |
| **FT / TT / other** | no routine – FT, TT and other postings on AA accounts arrive as ACCOUNTS-CREDIT/DEBIT activities (ACTIVITY.API) | key = FT behind the activity (TXN.CONTRACT.ID root) per customer; own transfer not counted; non-AA accounts not covered |
| **Maximum CIF** | `MaximumCifLimitController` – ACTIVITY.API, credit only, `validateRecord` | sum of WORKING.BALANCE of all customer accounts (converted) + credit vs MAX.CIF.BALANCE |
| **Overrides** | `view/CustomerLimitOverride.raiseFor` on TXN.AMOUNT | `id FM p1 VM p2`; acceptable overrides (not errors) |
| **Reports** | `CustomerLimitSummary/Period/TxnEnquiryController` – NOFILE `Enquiry.setIds` | return `List<String>` of `*`-separated lines (T24 requires strings; model rows used inside, view formats at the end) |

Key rules: counted = amount (ORIG.TXN.AMT else TXN.AMOUNT) + ACTIVITY.CLASS action CREDIT/DEBIT (any product line) + INITIATION.TYPE in list (all); date for every step = REMARKS tag → ORG.SYSTEM.DATE → T24 TODAY (clock only logged last resort); limit CCY = LCY → AAA TXN.AMOUNT.LCY used (no rate); limit check is **above** limit only; utilised minus own earlier input on re-input; monthly = migrated + utilised; totals floored at 0; amounts in **limit currency** (converted, 2 dp, stored converted – reversals never reconvert); missing CIF.LIMIT record or blank limit → `EB-ALBE.CIF.LIMIT.NOT.DEFINED` once; **blank CIF.LIMIT CCY → ERROR `EB-ALBE.CIF.LIMIT.CCY.MISSING` (blocks)**; technical failure → logged, transaction **not** blocked.

## 5. Code map

```
controller/   sub-packages by where the routine is attached:
  customer/   CustomerLimitCreationController (VERSION CUSTOMER,ALBE.CORP)
  activity/   DailyMonthlyLimitController, MaximumCifLimitController (ACTIVITY.API),
              ActivityRequestBuilder (common AAA -> request mapping for any activity)
  enquiry/    CustomerLimitSummaryEnquiryController, CustomerLimitPeriodEnquiryController, CustomerLimitTxnEnquiryController (NOFILE)
service/      shared: CustomerLimitReader, CustomerLimitUtilizationUpdater (record ids, utilisedAmount/migratedAmount, add/reverse)
  activity/   CustomerLimitEngine (ONE entry point: transaction info + customer limit info -> checkDailyMonthly/checkMaxCif/count/reverse; transactionKey; own transfer),
              DailyMonthlyLimitService, MaximumCifLimitService, TxnDetailsLimitRecorder (record/findOriginal/delete, id <cust>-<date>-<root ref>),
              CurrencyConversionService
  customer/   CustomerLimitCreationService
  enquiry/    CustomerLimitEnquiryService (report rows, read only)
repository/   CustomerLimitRepository – one constructor (T24Context); all reads/writes/selects
model/        CustomerLimitDTO (isCredit/getAmount/logKey), Limits, LimitBreach
  enquiry/    LimitSummaryRow, LimitPeriodRow, LimitTxnRow (report rows)
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
| Key **without .CR/.DR**; **own-account transfer not counted** (same customer both sides) – 2nd activity takes 1st back + 0-amount marker (reversal takes nothing back) | user 22-Nov: "customer id differs" → drop suffix, skip own transfers |
| Blank CIF.LIMIT **CCY → error** (blocks) | user 22-Nov: "CCY can't be blank, if so throw error"; chose "at transaction time – block" |
| Counted date in **AAA REMARKS** tag, TXN.DETAILS id `<cust>-<date>-<AAA>` | user's idea; keeps table structure; direct read by key even for reversal on a later day (an earlier SYSTEM.DATE field was removed) |
| Creation as **Auth** routine (HLSD says After Unau) | no delete flow needed; amendment never overwrites (`cifLimitExists` TRUE/FALSE/null) |
| GEN.PARAM record id **`CUSTOMER.LIMIT`** | user; was SYSTEM |
| CIF.LIMIT created by **direct write** (no OFS); only VERSION **CUSTOMER,ALBE.CORP** | user 23-Nov (FQ10 / FQ16) |
| Currency conversion to the limit CCY; txn CCY = AAA CURRENCY (else AA.ARRANGEMENT); limit CCY = LCY → AAA TXN.AMOUNT.LCY | user; rate source still pending for foreign-CCY limits and max CIF accounts (E1) |
| Overridden-transactions report reads **AA.ARRANGEMENT.ACTIVITY OVERRIDE** at report time (live, then `$NAU`) | no table field allowed; "overridden" = limit-exceed overrides only (`Constants.LIMIT_EXCEED_OVERRIDES`) |
| Enquiry range caps 92 days / 24 months; TXN report needs customer or date | protect online system |
| Per-record trace logs at `detail` level | performance; `LOG_DETAIL_ENABLED=false` in production |
| Strict date/month parsing | Java's default turned 20261131 into 30-Nov |
| Routines common for **any activity**: credit/debit by the **ACTIVITY.CLASS action** (`Constants.CREDIT_ACTIONS/DEBIT_ACTIONS`, any product line), INITIATION.TYPE list (all), ORIG.TXN.AMT, LCY amount | user (21-Nov): "common for all activities"; chose "any activity with TXN.AMOUNT, balance sign" – sign not available at validateRecord (no accounting yet; R25 TransactionData has no amount) → class action used; all initiation types; ORIG.TXN.AMT; TXN.AMOUNT.LCY |
| **ACTIVITY.API only** – FT / TELLER / ACCOUNT.PARAMETER routines built then **removed** (22-Nov); one engine; key = root ref | user 22-Nov: "FT will trigger activity" → remove the others (Git history has them) |
| T24 date instead of server clock | DEV: ORG.SYSTEM.DATE 01 APR 2026 vs clock 29 OCT 26 |
| Bank decides where the routines are attached; code generic; direction = ACTIVITY.CLASS action **for now** | user 21-Nov; FT/TT source/destination account reading discussed, not chosen; other actions (APPLYPAYMENT…) and deposit payouts: decide later (GAPS A7/A8) |

HLSD deviations to be reflected by the BA: creation as Auth routine, GEN.PARAM instead of EB.ALBE.GEN.CIF.LIMIT, counted date in REMARKS, currency conversion.

## 7. Open items (see `docs/GAPS.md`)

- **E1 exchange rate**: `CustomerLimitRepository.readExchangeRate` returns null until the bank answers (table – suggested core CURRENCY –, field, mid/buy/sell, market, quote direction). Until then other currencies → `EB-ALBE.EXCH.RATE.MISSING`, check skipped.
- **G1 fixed** (T24 date); **G4 mitigated** (ORG.SYSTEM.DATE fallback)
- **G2** lost update on parallel inputs (needs lock API) – team decision
- **G3** partial writes (fail the transaction or let through) – team decision
- **G5** REMARKS max length – confirm length
- **G6 fixed** (22-Nov): blank CIF.LIMIT CCY → **error** EB-ALBE.CIF.LIMIT.CCY.MISSING (EB.ERROR), transaction blocked (user)
- **G7** accounts in other companies, **G8** ACCOUNT SELECT fallback (G9 fixed: credit/debit by ACTIVITY.CLASS)
- AAA findings **A1–A7** implemented (GAPS.md). DEV checks left: ORG.SYSTEM.DATE stable on re-input/DELETE/AUTH-REV (then drop the REMARKS tag + G5), COB UNAUTH.PROCESS decision, ORIG.TXN.AMT / TXN.AMOUNT.LCY filled at UNAUTH, `!TODAY` via Session. Waiting for a DEV credit/debit AAA from the user (user's Edge is granted read-only for screenshots).
- **Compile-verified against the real R25 libs** (21-Nov-2026, user's `C:\Temenos\R25\TAFJ\lib`, `TAFJ\ext`, `T24\bnk\t24lib`): 0 errors – all core hook signatures and API calls exist (`docs/TECHNICAL_REFERENCE.md` §12). Still open: the **ALBE table JAR** (must be generated in T24 – cannot be generated outside T24) and its getter/setter names; DEV behaviour (REMARKS kept for DELETE/AUTH-REV, override value format, `$NAU` read, "record not found" text → `RECORD_NOT_FOUND_MARKERS`).
- To compile against the real libs in a sandbox: javac needs `-sourcepath <empty dir> -implicit:none` (the T24 JARs also contain `.java` sources); `org.json` from `TAFJ/ext/json.jar` is required.
- Pending from the bank: one-off CIF.LIMIT load for existing customers; HLSD update; TXN.DETAILS housekeeping.
- **FT / TELLER / ACCOUNT.PARAMETER routines removed (22-Nov-2026)** – ACTIVITY.API covers FT/TT on AA accounts. DEV: activity created at FT input; activity overrides on the FT screen; FT delete/reversal → DELETE/AUTH-REV. Non-AA accounts not covered. S5: first activity of an own transfer may still show an override.
- **Open questions** live only in `docs/QUERIES_AND_GAPS.md` (FQ/TQ); the old Q1–Q13 list in GAPS.md was removed as a duplicate (22-Nov).
- Remaining small code gap: G5. (`\<mnemonic>` strip done via root reference.) (Outdated `<AAA id>` comments fixed 22-Nov.)

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
| `docs/QUERIES_AND_GAPS.md` | **register**: functional queries FQ1–FQ16 (bank/BA), technical queries TQ1–TQ15 (DEV), decisions D1–D25, gaps raised/solved – keep it short; update when a query is answered |
| `docs/TRACEABILITY.md` | HLSD 7.2.1 / use cases → code → tests; deviations D1–D8 |
| `docs/UNIT_TEST_CASES.md` | 116 cases (107 pass outside T24, 9 DEV only) – add a case for every new behaviour |
| `docs/RELEASE_NOTES.md` | release 1.0 objects, installation order, smoke test, rollback |
| `docs/DATA_MIGRATION.md` | M1 limits for existing customers, M2 migrated monthly usage, cut-over |
| `docs/SUPPORT_RUNBOOK.md` | monitoring, common cases, error lines, FAQ |
| `docs/CODE_REVIEW_CHECKLIST.md` | standards as tick-boxes – use before every merge |
| `CHANGELOG.md` | plain-language history – add an entry for every change |

When code changes, keep these docs in step (README class/routine tables, TECHNICAL_REFERENCE change history, T24_CONFIGURATION set-up, REPORTS columns, TRACEABILITY, UNIT_TEST_CASES, CHANGELOG) and update this file's decisions/open items.

Key facts found in the HLSD vs the approved DEV tables: the HLSD lists 9 TXN.DETAILS fields (incl. TXN.CCY, EXCH.RATE, TXN.AMOUNT.LCY, SYSTEMDATE) but the **approved** ALBE.TXN.DETAILS.LIMIT has only ID (max 100), ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT (File Type Int) – the code follows the approved table (deviation D3; TXN.AMOUNT holds the limit-currency amount). The HLSD says "update utilised only if not exceeded"; agreed: count also accepted-override transactions (D5).
