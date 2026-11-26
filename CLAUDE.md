# CLAUDE.md – context for working on this repo

Read this first. It is the handover so a new session is up to speed without re-asking.
Details live in `docs/`; this file says **what matters, what was decided, and why**.

---

## 1. What this is

- **Project:** ALBE (Albaraka Egypt) **Customer Limit Management** on **Temenos Transact (T24) / TAFJ**, Java 21, Eclipse project built with **Maven** (`pom.xml`, 25-Nov: `mvn clean package` / `mvn clean deploy` = copy to `/srv/Temenos/T24/bnk/ALBElib` over SSH, old JAR removed; sources stay in `src/`; T24 JARs are system-scope compile deps via `t24.home`; credentials never committed – `deploy.local.properties` is git-ignored). Source: `src/`, package root `com.temenos.ALBE.CustomerLimit`.
- **Spec:** HLSD *Albaraka T24 project – Account module – 3 CBE Mandate CRs V2*, section **7.2.1** (customer limit creation; daily/monthly limit check; maximum CIF balance check; overrides).
- **Repo:** `shunmugarajag/ALBECustomerLimitClaude`, branch **`main`** (the only branch used). The old repo `shunmugarajag/ALBECustomerLimit` (PR #1) is **not used any more**.
- **User:** T24 Java developer, 15 years' experience, works in Eclipse on Windows (`D:\Claude\ALBECustomerLimitClaude`). Often asks to have things "explained with easy words".

## 2. Rules from the user – always apply

| Rule | Note |
|---|---|
| **Do not change the table structure** – it is pre-approved | No new fields, no new tables. (OVERRIDE.IDS and SYSTEM.DATE were added once and **removed** for this reason.) |
| **Ask on design points, don't assume** | Use a short question with options; if unanswered, state the assumption made. |
| MVC layers; hook classes are `…Controller` | controller (sub-packages customer / activity) / service / repository / model / view / validation / util / constants |
| **All DB access only in `repository/CustomerLimitRepository`**, each call logged | no `DataAccess`/`Session`/Table class anywhere else |
| **No inner classes, no interfaces** (no lambdas-as-interfaces design, no nested types) | plain public classes; `Comparator` lambda in a `sort` call is fine |
| **No `final` classes except `Constants`** | utility classes use a private constructor instead |
| All literals in `constants/Constants.java` | table names, ids, override ids, messages, selection field names, limits |
| Validate a request **once**, in the controller | services trust it; common value checks in `util/ValidationUtil` |
| Logging only through `util/LogUtil`, **INFO only** (user 26-Nov) | `ALBE.CUSTOMER.LIMIT \| Class.method \| EVENT \| details` |
| **T24 date** for counting (not the server clock) | activity **ORG.SYSTEM.DATE** → T24 TODAY (D42; REMARKS tag removed 26-Nov) |
| Don't critique the user's **sample** routines | user said "ignore gaps in the sample" |
| Source files **ASCII only** | check with `LC_ALL=C grep -P '[^\x00-\x7F]' -r src` (must print nothing); FM/VM constants removed 26-Nov (D43) |
| **JAR name = `ALBE_CustomerLimit.jar`** (25-Nov) | JBoss `modules/com/temenos/t24/main/module.xml` loads `./localLib/ALBE_CustomerLimit.jar` by name – a missing/renamed JAR takes down all T24 deployments (DEV outage 25-Nov) |
| **T24 server: change no file except the JAR deploy** (25-Nov) | never edit server config (e.g. `TAFJTrace.properties`) or leave files there; the restart script is streamed over SSH (`bash -s`), not saved |
| Commit + push to `main` when work is done | end commit messages with the attribution lines the session provides |

## 3. Tables (approved structure – read/write only these fields)

| Table | Id | Fields used |
|---|---|---|
| EB.ALBE.GEN.PARAM | **`CUSTOMER.LIMIT`** (not SYSTEM); **`CUSTOMER.LIMIT.ACTIVITY`** = ACTIVITY → CREDIT/DEBIT (D41) | KEY.NAME / KEY.VALUE (DEV record 23-Nov): **CURRENCY**, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, **MAXIMUM.OVERALL.CIF.BALANCE** (`Constants.PARAM_KEY_*`; CURRENCY → CIF.LIMIT CCY, MAXIMUM.OVERALL… → MAX.CIF.BALANCE) |
| EB.ALBE.CIF.LIMITS (EB.TABLE.DEFINITION `ALBE.CIF.LIMITS`, 23-Nov) | customer id | CURRENCY + the 5 limits – same names as GEN.PARAM except CURRENCY (= CCY) and MAXIMUM.OVERALL.CIF.AMOUNT (= MAX.CIF.BALANCE) |
| EB.ALBE.UTILISED.LIMIT | daily `<cust>-YYYYMMDD`, monthly `<cust>-YYYYMM` | UTILISED.CREDIT/DEBIT.LIMIT (written); UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT (read only, monthly) |
| EB.ALBE.TXN.DETAILS.LIMIT | **`<cust>-<YYYYMMDD>-<root ref>-<arrangement id>`** (root ref = FT/TT behind the AAA, else AAA id; arrangement since 23-Nov, FQ6; no .CR/.DR) | ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE, TXN.AMOUNT – nothing else |

Generated API classes: `com.temenos.t24.api.records.ebalbe…` / `com.temenos.t24.api.tables.ebalbe…` – generated in Design Studio (DStablejar workspace) and kept in `lib/l3-tables`: `ALBE_CUSTOMER_LIMIT_TABLE.jar` (CIF.LIMITS, UTILISED.LIMIT, TXN.DETAILS.LIMIT – name as in pom.xml), `ALBE_GEN_PARAM_TABLE.jar`, `ALBE_COMM_REGOFF_TABLE.jar`; all three on `.classpath`.

## 4. How it works (one screen)

| Function | Hook (EB.API) | Flow |
|---|---|---|
| **Limit creation** | `CustomerLimitCreationController` – VERSION CUSTOMER,ALBE.CORP **Auth routine**, `postUpdateRequest` | at customer authorisation: if `cifLimitExists` = FALSE copy GEN.PARAM `CUSTOMER.LIMIT` → CIF.LIMIT. TRUE (amendment) or null (read failed) → **never overwrite**. No delete flow. |
| **Activity routine (the only one, D47)** | `CustomerLimitActivityController` – EB.API `ALBE.CL.ACTIVITY.LIMIT`, **one Post Routine** per mapped activity, `validateRecord` | UNAUTH (ACTION INPUT): `CustomerLimitEngine.processInput` – limits read once → convert → earlier input by TXN.DETAILS key → daily + monthly read once → checks → max CIF (credit) → count (each record = used − earlier + new, written once) → overrides; DELETE / AUTH-REV: `reverse` – take back only what TXN.DETAILS shows was counted (by key, else SELECT; **no "from today" fallback** – D45); AUTH / REVERSE: nothing, no read. Save failure → setError LIMIT.UPDATE.FAILED. **DEV prerequisite TQ20:** called for DELETE / AUTH-REV. Old VAL / UPD / MAX.CIF routines **removed** (D47). |
| **FT / TT / other** | no routine – FT, TT and other postings on AA accounts arrive as ACCOUNTS-CREDIT/DEBIT activities (ACTIVITY.API) | key = FT behind the activity (TXN.CONTRACT.ID root) + arrangement; own transfer counted on both sides (D39); no non-AA accounts (D34) |
| **Maximum CIF** | `MaximumCifLimitService.check` inside `processInput`, credit only (limits and converted credit passed in) | CUSTOMER.ACCOUNT `<customer>` → each ACCOUNT WORKING.BALANCE (converted) + credit vs MAXIMUM.OVERALL.CIF.AMOUNT; **CUSTOMER.ACCOUNT empty → ERROR `EB-ALBE.CUSTOMER.ACCOUNT.MISSING` (blocks), no SELECT fallback** (D45) |
| **Overrides** | `view/CustomerLimitOverride.raiseFor(activity, request)` | text **`[ID,param]` – ONE parameter** (D46: a second `&` is not filled in DEV), e.g. "100128 by 12000.00"; **debit / credit ids** EB-ABLE.DAILY|MONTHLY.DEBIT|CREDIT.LIMIT.EXCEED; **one override per AAA field** (daily TXN.AMOUNT, monthly TXN.AMOUNT.LCY, max CIF ORIG.TXN.AMT, not defined CURRENCY, rate EFFECTIVE.DATE; errors TXN.AMOUNT) – D43; acceptable overrides (not errors) |

Key rules: counted = amount (ORIG.TXN.AMT else TXN.AMOUNT) + **ACTIVITY mapped CREDIT/DEBIT in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY`** (not mapped → ERROR `EB-ALBE.ACTIVITY.NOT.MAPPED`, blocks; D41) + INITIATION.TYPE in list (all); date for every step = activity ORG.SYSTEM.DATE → T24 TODAY (D42) (clock only logged last resort); **transaction amount converted only from the activity (D44): LCY amount, else × TXN.EXCH.RATE, else ERROR `EB-ALBE.EXCH.RATE.MISSING` (blocks); limit not in LCY → blocked;** max CIF other-currency accounts → override, skipped (parked); limit check is **above** limit only; utilised minus own earlier input on re-input; monthly = migrated + utilised; totals floored at 0; amounts in **limit currency** (converted, 2 dp, stored converted – reversals never reconvert); missing CIF.LIMIT record or blank limit → `EB-ABLE.CIF.LIMIT.NOT.DEFINED` once; **blank CIF.LIMIT CCY → ERROR `EB-ALBE.CIF.LIMIT.CCY.MISSING` (blocks)**; LCY not readable → override, not blocked, **not counted** (technical, D47); technical failure → logged, transaction **not** blocked – **except a failed utilisation save → ERROR `EB-ALBE.LIMIT.UPDATE.FAILED` (blocks, D37)**.

## 5. Code map

```
controller/   sub-packages by where the routine is attached:
  customer/   CustomerLimitCreationController (VERSION CUSTOMER,ALBE.CORP)
  activity/   CustomerLimitActivityController (the only activity routine), ActivityRequestBuilder (AAA -> request, any activity)
service/      shared: CustomerLimitReader (limits, GEN.PARAM, activity direction),
              CustomerLimitUtilizationUpdater (record ids, utilisedAmount/migratedAmount, add(record already read, credit, debit))
  activity/   CustomerLimitEngine (processInput / reverse / transactionKey; daily + monthly check inside),
              MaximumCifLimitService (check), TxnDetailsLimitRecorder (recordId/record/findCounted/delete,
              id <cust>-<date>-<root ref>-<arrangement>), CurrencyConversionService
  customer/   CustomerLimitCreationService
repository/   CustomerLimitRepository – one constructor (T24Context); all reads/writes/selects
model/        CustomerLimitDTO (isCredit/getAmount/logKey/describe, flags, notes), Limits, LimitBreach (id + excess)
view/         CustomerLimitOverride
validation/   CustomerLimitRequestValidator (isValid, isValidCustomerId)
util/         LogUtil (INFO only, D47; never throws), ValidationUtil (isBlank, nvl, toAmount, toOptionalAmount, toDate strict, ...)
constants/    Constants (the only final class)
```

## 6. Decisions log (why things are the way they are)

| Decision | Why / history |
|---|---|
| Count utilisation at **input** (UNAUTH), **kept only if the FT is committed** (rejected override → T24 undoes it, TQ22 passed 26-Nov); reverse on DELETE and AUTH-REV | user: unauthorised transactions must count; rejected and reversed ones must be taken back; accepted-override transactions count |
| Breach → **acceptable override**, incl. missing record / blank limit | user changed "reject with error" to override |
| Key **without .CR/.DR**, **with the arrangement id**; **own-account transfer counted on both sides** (two arrangements = two records), checked like any transaction incl. max CIF | user 22-Nov: drop suffix; user 23-Nov (FQ6, D39): add arrangement id, count both sides (marker logic removed) |
| Blank CIF.LIMIT **CCY → error** (blocks) | user 22-Nov: "CCY can't be blank, if so throw error"; chose "at transaction time – block" |
| ~~Counted date in AAA REMARKS tag~~ (**replaced by D42, 26-Nov: activity ORG.SYSTEM.DATE; DEF routine removed** – DEV: Record/Validate Rtn never called for FT activities, only the Post Routine), TXN.DETAILS id `<cust>-<date>-<FT/AAA>-<arrangement>` | user's idea; keeps table structure; direct read by key even for reversal on a later day (an earlier SYSTEM.DATE field was removed) |
| Creation as **Auth** routine (HLSD says After Unau) | no delete flow needed; amendment never overwrites (`cifLimitExists` TRUE/FALSE/null) |
| GEN.PARAM record id **`CUSTOMER.LIMIT`** | user; was SYSTEM |
| CIF.LIMIT created by **direct write** (no OFS); only VERSION **CUSTOMER,ALBE.CORP** | user 23-Nov (FQ10 / FQ16) |
| Customer limit table is **EB.ALBE.CIF.LIMITS** (23-Nov) | user created `ALBE.CIF.LIMITS` and generated the JAR; record class `EbAlbeCifLimitsRecord`, getters `getCurrency` / `getMaximumOverallCifAmount` replace `getCcy` / `getMaxCifBalance` |
| Currency conversion to the limit CCY; txn CCY = AAA CURRENCY (else AA.ARRANGEMENT); limit CCY = LCY → AAA TXN.AMOUNT.LCY | user; rate source still pending for foreign-CCY limits and max CIF accounts (E1) |
| **LogUtil INFO only** (D47, 26-Nov); events ACTIVITY / ACTION / RESULT tell the outcome, FAILED / BLOCKED the problems; redundant READ / WRITE / START trace lines dropped | user: "no log util - go with just info logs" → chose "keep LogUtil, info only". DIAGNOSTIC.API at ERROR shows nothing |
| Strict date/month parsing | Java's default turned 20261131 into 30-Nov |
| Routines common for **any activity**: credit/debit by the **ACTIVITY.CLASS action** (`Constants.CREDIT_ACTIONS/DEBIT_ACTIONS`, any product line), INITIATION.TYPE list (all), ORIG.TXN.AMT, LCY amount | user (21-Nov): "common for all activities"; chose "any activity with TXN.AMOUNT, balance sign" – sign not available at validateRecord (no accounting yet; R25 TransactionData has no amount) → class action used; all initiation types; ORIG.TXN.AMT; TXN.AMOUNT.LCY |
| **ACTIVITY.API only** – FT / TELLER / ACCOUNT.PARAMETER routines built then **removed** (22-Nov); one engine; key = root ref | user 22-Nov: "FT will trigger activity" → remove the others (Git history has them) |
| T24 date instead of server clock | DEV: ORG.SYSTEM.DATE 01 APR 2026 vs clock 29 OCT 26 |
| **Direction from GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY`** (D41, 25-Nov): KEY.NAME = AAA ACTIVITY, KEY.VALUE CREDIT/DEBIT; replaces the ACTIVITY.CLASS action (`CREDIT_ACTIONS/DEBIT_ACTIONS` removed); unmapped activity → EB.ERROR | user 25-Nov: "mapping table"; chose GEN.PARAM record (no new table), key ACTIVITY, unmapped → error |
| **Checks as Post Routine; override text `[ID,p1,p2]`; one override per AAA field** (D43, 26-Nov) | DEV: Validate / Pre / Record Rtn never called for FT activities; VAL as Post Routine (before UPD) showed the override on the FT screen (FT26093GZC6N). R25 EB.HANDLE.OVERRIDES reads `[ID,p1,p2]` (old `ID FM p1 VM p2` shown raw); `TField.setOverride` replaces – only the last override per field survived (daily lost). Only `validateRecord` can raise overrides (R25 invokers). Post-routine-only check controller (parked, not committed) and FT/TT version routine not needed |
| **Single routine + conversion from the activity** (D44, 26-Nov) | user: one routine (separate class), ACTION log for input/delete/reverse, fewer reads but **no cache** (read once, pass along); different currency → LCY amount, else TXN.EXCH.RATE, else block; limit not in EGP → block; max CIF other-currency accounts parked. Built before the DEV prerequisite test (TQ20) – old routines kept for rollback |
| **Review fixes + CUSTOMER.ACCOUNT block** (D45, 26-Nov) | review agent: second AUTH-REV call took back another FT's amount → no fallback in the single routine; user: "CUSTOMER.ACCOUNT is empty? block transaction" → EB.ERROR, SELECT fallback removed |
| **Override messages: debit/credit ids, one `&`; TXN.DETAILS Table read** (D46, 26-Nov) | DEV: "Customer 100128,23000.00 by" – second `&` empty; user: message must say debit or credit → chose separate ids. DEV FT2609332FH0 counted twice with VAL+UPD+single routine attached: DataAccess.getRecord did not see TXN.DETAILS written earlier in the same commit, Table.read (as UTILISED) does |
| **Clean rewrite** (D47, 26-Nov) | user: "revisit all the logic and start from first"; answers: remove old routines, no limits → override still counted, own transfer both sides, no lock. Old controllers / DailyMonthlyLimitService / UtilisationRecords removed; engine = processInput + reverse; re-input = one write (used − earlier + new); LCY unreadable → not counted |
| **Reports removed** (26-Nov) | user: "remove report sources" – the 3 NOFILE enquiries, their service / rows / view / validator, report-only repository reads and constants, REPORTS.md deleted (Git history keeps them; D28 had parked them) |
| Bank decides where the routines are attached; code generic; direction = ACTIVITY.CLASS action **for now** | user 21-Nov; FT/TT source/destination account reading discussed, not chosen; other actions (APPLYPAYMENT…) and deposit payouts: decide later (GAPS A7/A8) |

HLSD deviations to be reflected by the BA: creation as Auth routine, GEN.PARAM instead of EB.ALBE.GEN.CIF.LIMIT, counted date in REMARKS, currency conversion.

## 7. Open items (see `docs/GAPS.md`)

- **E1 exchange rate** (narrowed by D44 – transaction amount from the activity; only max CIF accounts in other currencies left, parked): `CustomerLimitRepository.readExchangeRate` returns null until the bank answers (table – suggested core CURRENCY –, field, mid/buy/sell, market, quote direction). Until then other currencies → `EB-ABLE.EXCH.RATE.MISSING`, check skipped.
- **G1 fixed** (T24 date); **G4 mitigated** (ORG.SYSTEM.DATE fallback)
- **G2** lost update on parallel inputs (needs lock API) – team decision
- **G8 closed** (26-Nov, D45): no ACCOUNT SELECT; empty CUSTOMER.ACCOUNT blocks.
- **G3 fixed** (23-Nov, D37): utilisation / TXN.DETAILS save failure → `CustomerLimitRepository.isSaveFailed` → `CustomerLimitActivityController.validateRecord` raises EB.ERROR `EB-ALBE.LIMIT.UPDATE.FAILED` (transaction cancelled). DEV: TQ18
- **G4 / G5 closed** (26-Nov, D42: no REMARKS tag)
- **G6 fixed** (22-Nov): blank CIF.LIMIT CCY → **error** EB-ALBE.CIF.LIMIT.CCY.MISSING (EB.ERROR), transaction blocked (user)
- **G7** accounts in other companies (G9 fixed: credit/debit by ACTIVITY.CLASS)
- AAA findings **A1–A7** implemented (GAPS.md). DEV checks left: ORG.SYSTEM.DATE stable on re-input/DELETE/AUTH-REV (then drop the REMARKS tag + G5), COB UNAUTH.PROCESS decision, ORIG.TXN.AMT / TXN.AMOUNT.LCY filled at UNAUTH, `!TODAY` via Session. Waiting for a DEV credit/debit AAA from the user (user's Edge is granted read-only for screenshots).
- **Compile-verified against the real R25 libs** (21-Nov-2026, user's `C:\Temenos\R25\TAFJ\lib`, `TAFJ\ext`, `T24\bnk\t24lib`): 0 errors – all core hook signatures and API calls exist (`docs/TECHNICAL_REFERENCE.md` §12). **ALBE table JARs generated** (laptop `lib/l3-tables`: ALBE_CustomerLimitTable.jar, ALBE_GEN_PARAM_TABLE.jar) – merged code compiles with 0 errors against them + R25 libs (23-Nov, TQ1 closed). `.classpath` is kept local (absolute C:/Temenos paths), not on GitHub. Still open: DEV behaviour (REMARKS kept for DELETE/AUTH-REV, override value format, `$NAU` read, "record not found" text: **TQ13 closed 24-Nov** – R25 says `EB-INTERNAL.ERROR … Record does not exist`, in `RECORD_NOT_FOUND_MARKERS`). Logs: logger `API` → server `TAFJ/log/api.log` via ALERT.API (errors) / DIAGNOSTIC.API (info, level ERROR by default – set INFO in DEV to see them).
- To compile against the real libs in a sandbox: javac needs `-sourcepath <empty dir> -implicit:none` (the T24 JARs also contain `.java` sources); `org.json` from `TAFJ/ext/json.jar` is required.
- Pending from the bank: one-off CIF.LIMIT load for existing customers; HLSD update; TXN.DETAILS housekeeping.
- **FT / TELLER / ACCOUNT.PARAMETER routines removed (22-Nov-2026)** – ACTIVITY.API covers FT/TT on AA accounts. DEV: activity created at FT input; activity overrides on the FT screen; FT delete/reversal → DELETE/AUTH-REV. No non-AA accounts exist (D34). Own transfers: both sides checked and counted (D39).
- **D44 DEV steps:** create EB.API `ALBE.CL.ACTIVITY.LIMIT` + EB.ERROR `EB-ALBE.EXCH.RATE.MISSING`; deploy; one Post Routine per activity (T24_CONFIGURATION §8); TQ20 (DELETE / AUTH-REV reach validateRecord), TQ21 (TXN.EXCH.RATE meaning), **TQ22** (override declined / FT cancelled → our writes rolled back?). Also create EB.ERROR `EB-ALBE.CUSTOMER.ACCOUNT.MISSING`; D46: create the 4 debit/credit EB.OVERRIDE ids and change all messages to one `&` (T24_CONFIGURATION §4). **D47: remove the old VAL / UPD / DEF / MAX.CIF from ACTIVITY.API and EB.API** (their classes are gone; together with ALBE.CL.ACTIVITY.LIMIT they double count). TQ22 passed.
- **D43 retest in DEV (TQ12 / TQ19):** deploy the new JAR, FT breaching daily + monthly → both overrides with `&` filled; "by" amount = used + this − limit (FT26093GZC6N showed 13,000 for an expected 12,750 – log not yet seen); debit FT; credit with MAX.CIF filled. Still to see: AUTH-REV take-back (FT26093BG24V reversal), DELETE (FT26093RL3T1).
- **Open questions** live only in `docs/QUERIES_AND_GAPS.md` (FQ/TQ); the old Q1–Q13 list in GAPS.md was removed as a duplicate (22-Nov).
- No remaining small code gap (G5 closed 26-Nov). (`\<mnemonic>` strip done via root reference.) (Outdated `<AAA id>` comments fixed 22-Nov.)

## 8. Testing (outside T24)

- No TAFJ JARs here. Tests were done with **stand-in classes outside the repo** (`/tmp/stubs/src`, minimal versions of TField, TStructure, DataAccess, Session, hooks, generated records/tables, logger) and small test programs with an in-memory repository (`class MemRepo extends CustomerLimitRepository` overriding the read/write/select methods). **Never commit stubs** – the user confirmed they must stay out of the repo.
- Compile check: `javac -d out $(find <stubs>/src src -name '*.java')` – must give no errors. If the sandbox was reset, recreate the stubs from the imports in `src`.
- Test programs live in `/tmp/tc` (TE engine 40, TA builder 21, TO mapping 7, TQ view 13, TK GEN.PARAM 4, TN not-found 2; helpers TF = in-memory repo, AB = build with direction). Covered: credit/debit daily & monthly breach incl. migrated, equal-to-limit, re-input (same / lower / higher amount), take back incl. second call and later day (SELECT), blank limit, missing record, blocking errors (CCY, rate, CUSTOMER.ACCOUNT), conversion (LCY amount / TXN.EXCH.RATE / limit not LCY / LCY unreadable), max CIF, own transfer, two customers, save failures, read / write counts.
- Real-lib compile: `javac -proc:none -sourcepath <empty> -implicit:none -cp <R25 + lib/l3-tables jars>` (uploads `R25/` and `ALBECustomerLimitClaude/lib/l3-tables`) – 0 errors after D47.
- Not covered: controllers with real T24 objects → DEV tests in `docs/T24_CONFIGURATION.md` §13 and `docs/GO_LIVE_CHECKLIST.md`.

## 9. Docs map

| File | Content |
|---|---|
| `README.md` | overview, MVC table, routines, tables, overrides, logging |
| `docs/TECHNICAL_REFERENCE.md` | architecture, classes, flows, decisions, HLSD deviations, APIs to verify, testing |
| `docs/T24_CONFIGURATION.md` | T24 set-up steps 1–15 (tables, JARs, GEN.PARAM, EB.OVERRIDE, EB.API, VERSION, ACTIVITY.API, versions, logging, go-live data, exchange rate, tests, **menu & access**) |
| `docs/GAPS.md` | known gaps, fixes without table change, exchange-rate questions |
| `docs/QUERIES_AND_GAPS.md` | **register**: functional queries FQ1–FQ18 (bank/BA), technical queries TQ1–TQ23 (DEV), decisions D1–D47, customer-routine gaps C1–C4, daily/monthly gaps DM1–DM2, gaps raised/solved – keep it short; update when a query is answered |
| `docs/TRACEABILITY.md` | HLSD 7.2.1 / use cases → code → tests; deviations D1–D8 |
| `docs/UNIT_TEST_CASES.md` | 93 cases after the rewrite (87 pass outside T24, 6 DEV only) – add a case for every new behaviour |
| `docs/RELEASE_NOTES.md` | release 1.0 objects, installation order, smoke test, rollback |
| `docs/GO_LIVE_CHECKLIST.md` | tick-box go-live list: open items, install order, data load, smoke test, rollback, after go-live, per-product worksheet |
| `docs/DATA_MIGRATION.md` | M1 limits for existing customers, M2 migrated monthly usage, cut-over |
| `docs/SUPPORT_RUNBOOK.md` | monitoring, common cases, error lines, FAQ |
| `docs/CODE_REVIEW_CHECKLIST.md` | standards as tick-boxes – use before every merge |
| `CHANGELOG.md` | plain-language history – add an entry for every change |

When code changes, keep these docs in step (README class/routine tables, TECHNICAL_REFERENCE change history, T24_CONFIGURATION set-up, TRACEABILITY, UNIT_TEST_CASES, CHANGELOG) and update this file's decisions/open items.

Key facts found in the HLSD vs the approved DEV tables: the HLSD lists 9 TXN.DETAILS fields (incl. TXN.CCY, EXCH.RATE, TXN.AMOUNT.LCY, SYSTEMDATE) but the **approved** ALBE.TXN.DETAILS.LIMIT has only ID (max 100), ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT (File Type Int) – the code follows the approved table (deviation D3; TXN.AMOUNT holds the limit-currency amount). The HLSD says "update utilised only if not exceeded"; agreed: count also accepted-override transactions (D5).
