# CLAUDE.md – context for working on this repo

Read this first. It is the handover so a new session is up to speed without re-asking.
Details live in `docs/`; this file says **what matters, what was decided, and why**.

---

## 1. What this is

- **Project:** ALBE (Albaraka Egypt) **Customer Limit Management** on **Temenos Transact (T24) / TAFJ**, Java 21, Eclipse project built with **Maven** (`pom.xml`, 25-Nov: `mvn clean package` / `mvn clean deploy` = copy to `/srv/Temenos/T24/bnk/ALBElib` over SSH, old JAR removed; sources stay in `src/`; T24 JARs are system-scope compile deps via `t24.home`; credentials never committed – `deploy.local.properties` is git-ignored). Source: `src/`, package **`com.temenos.albe`** (4 classes, D50).
- **Spec:** HLSD *Albaraka T24 project – Account module – 3 CBE Mandate CRs V2*, section **7.2.1** (customer limit creation; daily/monthly limit check; maximum CIF balance check; overrides).
- **Repo:** `shunmugarajag/ALBECustomerLimitClaude`, branch **`main`** (the only branch used). The old repo `shunmugarajag/ALBECustomerLimit` (PR #1) is **not used any more**.
- **User:** T24 Java developer, 15 years' experience, works in Eclipse on Windows (`D:\Claude\ALBECustomerLimitClaude`). Often asks to have things "explained with easy words".

## 2. Rules from the user – always apply

| Rule | Note |
|---|---|
| **Do not change the table structure** – it is pre-approved | No new fields, no new tables. (OVERRIDE.IDS and SYSTEM.DATE were added once and **removed** for this reason.) |
| **Ask on design points, don't assume** | Use a short question with options; if unanswered, state the assumption made. |
| **One class per EB.API**, named after it (D50) | `ALBE.CL.ACTIVITY.LIMIT` → `AlbeClActivityLimit`, `ALBE.CL.MAX.CIF` → `AlbeClMaxCif`, `ALBE.CL.LIMIT.CREATE` → `AlbeClLimitCreate`; written like the core Java sample routines; DataAccess / Session / Table used directly in them (the MVC version and its repository / LogUtil rules are gone) |
| Logging: `logger.info` only (logger `API`), no helper (26-Nov) | `ALBE.CUSTOMER.LIMIT \| AlbeClActivityLimit: EVENT \| details` |
| **No inner classes, no interfaces** (no lambdas-as-interfaces design, no nested types) | plain public classes; `Comparator` lambda in a `sort` call is fine |
| **No `final` classes except `AlbeClConstants`** | utility classes use a private constructor instead |
| All literals in `AlbeClConstants` | table names, ids, override ids, messages, selection field names, limits |
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
| EB.ALBE.CIF.LIMIT (EB.TABLE.DEFINITION `ALBE.CIF.LIMIT`, prefix EB.ALB13, 27-Nov; was ALBE.CIF.LIMITS, D55) | customer id | CURRENCY + the 5 limits – same names as GEN.PARAM except CURRENCY (= CCY) and MAXIMUM.OVERALL.CIF.AMOUNT (= MAX.CIF.BALANCE) |
| EB.ALBE.UTILISED.LIMIT | daily `<cust>-YYYYMMDD`, monthly `<cust>-YYYYMM` | UTILISED.CREDIT/DEBIT.LIMIT (written); UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT (read only, monthly) |
| EB.ALBE.TXN.DETAILS.LIMIT | **`<cust>-<YYYYMMDD>-<root ref>-<arrangement id>`** (root ref = FT/TT behind the AAA, else AAA id; arrangement since 23-Nov, FQ6; no .CR/.DR) | ACCOUNT.NO, CREDIT.DEBIT.FLAG (CR/DR), EFF.DATE, TXN.AMOUNT – nothing else |

Generated API classes: `com.temenos.t24.api.records.ebalbe…` / `com.temenos.t24.api.tables.ebalbe…` – generated in Design Studio (DStablejar workspace) and kept in `lib/l3-tables`: `ALBE_CUSTOMER_LIMIT_TABLE.jar` (CIF.LIMIT, UTILISED.LIMIT, TXN.DETAILS.LIMIT – name as in pom.xml), `ALBE_GEN_PARAM_TABLE.jar`, `ALBE_COMM_REGOFF_TABLE.jar`; all three on `.classpath`.

## 4. How it works (one screen)

| Function | Routine (EB.API → class) | Flow |
|---|---|---|
| **Limit creation** | `ALBE.CL.LIMIT.CREATE` → `AlbeClLimitCreate` – VERSION CUSTOMER,ALBE.CORP.INPUT **Auth routine**, `postUpdateRequest` | at customer authorisation: CIF.LIMIT record missing → copy GEN.PARAM `CUSTOMER.LIMIT` (direct write). Exists (amendment) or read failed → **never overwrite**. Review findings 1–6 (not-found marker, write result, blank CURRENCY, number check, reversal auth, unknown keys) await the user. |
| **Daily / monthly** | `ALBE.CL.ACTIVITY.LIMIT` → `AlbeClActivityLimit` – **Post Routine 1** per mapped activity, `validateRecord` | UNAUTH: direction (GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY`, unmapped → error) → amount → CIF.LIMIT → convert → earlier TXN.DETAILS → check daily + monthly → count (used − earlier + new) → write TXN.DETAILS → overrides. DELETE / AUTH-REV: take back what TXN.DETAILS shows (by key, else SELECT for the counted date; nothing → nothing). AUTH / REVERSE: nothing. Save failure → error LIMIT.UPDATE.FAILED. |
| **Maximum CIF** | `ALBE.CL.MAX.CIF` → `AlbeClMaxCif` – **Post Routine 2 on credit activities** (D49) | UNAUTH credit only: CUSTOMER.ACCOUNT → each EB.CONTRACT.BALANCES WORKING.BALANCE (Account API, D54) converted to the limit CCY (Currency API, market 1) + credit vs MAXIMUM.OVERALL.CIF.AMOUNT → override; empty CUSTOMER.ACCOUNT → error; no rate → rate override, not checked; counts nothing. Not attached → not checked. |
| **FT / TT / other** | no routine – postings on AA accounts arrive as activities | key = root of TXN.CONTRACT.ID + arrangement; own transfer counted on both sides (D39); no non-AA accounts (D34) |
| **Overrides** | inside the routines | text **`[ID,param]` – one parameter** (D46), e.g. "100128 by 12000.00"; debit / credit ids EB-ALBE.DAILY\|MONTHLY.DEBIT\|CREDIT.LIMIT.EXCEED; one override per AAA field (daily TXN.AMOUNT, monthly TXN.AMOUNT.LCY, max CIF ORIG.TXN.AMT, not defined CURRENCY, rate EFFECTIVE.DATE; errors TXN.AMOUNT) |

Key rules: counted = amount (ORIG.TXN.AMT else TXN.AMOUNT) + ACTIVITY mapped CREDIT/DEBIT (D41) + INITIATION.TYPE in list (all); date = activity ORG.SYSTEM.DATE → T24 TODAY (D42); conversion only from the activity (D44): LCY amount, else × TXN.EXCH.RATE, else error; limit not in LCY → error; LCY unreadable → override, not counted; limit check is **above** limit only; monthly = migrated + utilised; totals floored at 0; amounts in limit currency, 2 dp, stored converted; missing CIF.LIMIT record → **skipped, not counted, no override**; blank single limit → that check skipped, still counted (D52); blank CIF.LIMIT CCY → error; technical failure → logged, not blocked – except a failed save → error (D37). A rejected override or any error rolls the count back with the FT (TQ22).

## 5. Code map

```
src/com/temenos/albe/
  AlbeClActivityLimit   ALBE.CL.ACTIVITY.LIMIT  daily / monthly check, count, take back
  AlbeClMaxCif          ALBE.CL.MAX.CIF         maximum CIF balance check (credits)
  AlbeClLimitCreate     ALBE.CL.LIMIT.CREATE    CIF.LIMIT creation at customer authorisation
  AlbeClConstants       all literals (the only final class)
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
| CIF.LIMIT created by **direct write** (no OFS); only VERSION **CUSTOMER,ALBE.CORP.INPUT** | user 23-Nov (FQ10 / FQ16) |
| Customer limit table is **EB.ALBE.CIF.LIMIT** (23-Nov) | user created `ALBE.CIF.LIMIT` and generated the JAR; record class `EbAlbeCifLimitRecord`, getters `getCurrency` / `getMaximumOverallCifAmount` replace `getCcy` / `getMaxCifBalance` |
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
| **Simple 2-class version** (D48, 26-Nov) | user: logic in 1-2 classes like the core Java samples, separate package, existing code untouched → `CustomerLimitSimple`: `CustomerLimitActivity` (Post Routine) + `CustomerLimitCreation` (Auth) + `CustomerLimitConstants`; DataAccess / Session / Table used directly inside them (the MVC rules – repository only, LogUtil – apply to package `CustomerLimit` only). Same behaviour as D47. Never attach both versions to one activity. **D49:** max CIF split into its own routine `CustomerLimitMaxCif` (Post Routine 2 on credits, EB.API `ALBE.CL.MAX.CIF.SIMPLE`). Tests: `/tmp/tc2/TS2.java` with in-memory stubs `/tmp/stubs2` (37 pass) |
| **One package, names from the EB.API** (D50, 26-Nov) | user: "clean package except simpler version, rename as per EB.API"; answers: delete MVC, drop SIMPLE, package `com.temenos.albe`. MVC package and `CustomerLimitSimple` removed (Git history keeps them). DEV: repoint the 3 EB.API records, remove `*.SIMPLE` ones |
| **T24 ids and config aligned with DEV** (D51, 27-Nov) | user: "fix ABLE to ALBE" + "fix the code as per override, error change and t24 config". OVERRIDE / EB.ERROR all `EB-ALBE.*`, one `&`, created by OFS (`tRun tSS GCS` works; FUNDS.TRANSFER.OFS blocked by the user channel); `EB-ABLE.*` reversed. Application is **OVERRIDE**, version **CUSTOMER,ALBE.CORP.INPUT**. EB.API repoint refused until the JAR is deployed (T24 checks the class). Old DAILY.MONTHLY EB.API reversed; `ALBE.STMT.FEE.API-20260403` still had them → OFS fix given. FQ19 / FQ20 open |
| **No limit record → skip** (D52, 27-Nov) | user: "if customer record not there for limit can we skip validation"; chose skip fully (no override, no count, no TXN.DETAILS) and, for a blank limit, skip that check only (still counted). EB-ALBE.CIF.LIMIT.NOT.DEFINED no longer raised (constant removed). No fallback to GEN.PARAM defaults – user confirmed "keep skip" (existing customers unchecked until the data load). Tests T22 / E04 / M01 updated, 37 pass |
| **Max CIF balance = ACCOUNT record only** (D53, 27-Nov) | DEV: AA account ACCOUNT record has no WORKING.BALANCE (balance only in EB.CONTRACT.BALANCES) → max CIF reads 0 for every account. Proposed EB.CONTRACT.BALANCES / Account API; user: "do not refer EB.CONTRACT.BALANCES only ACCOUNT" and chose "ACCOUNT record only". Open item G10 |
| **Max CIF = EB.CONTRACT.BALANCES + conversion** (D54, 27-Nov, supersedes D53) | user: "go ahead with Account API used contract balance and convert using exchange rate"; chose T24 standard rate (Currency API `calculateBuyAmount`, market 1, today) and "no rate → override, skip check". Account currency still from ACCOUNT. G10 fixed. Tests E06 / E07 added (39 pass). `EbContractBalancesRecord` is in `BF_ConBalanceUpdates.jar` (added to pom.xml 27-Nov) |
| **Customer limit table renamed to EB.ALBE.CIF.LIMIT** (D55, 27-Nov) | user created EB.TABLE.DEFINITION `ALBE.CIF.LIMIT` (same fields, prefix EB.ALB13) in place of `ALBE.CIF.LIMITS`: package `ebalbeciflimit`, classes `EbAlbeCifLimitRecord` / `EbAlbeCifLimitTable`, `CIF_LIMIT_TABLE = EB.ALBE.CIF.LIMIT`. The table JAR must be regenerated in Design Studio; the new table starts empty → existing customers skipped (D52) until their records are created again |
| **Reports removed** (26-Nov) | user: "remove report sources" – the 3 NOFILE enquiries, their service / rows / view / validator, report-only repository reads and constants, REPORTS.md deleted (Git history keeps them; D28 had parked them) |
| Bank decides where the routines are attached; code generic; direction = ACTIVITY.CLASS action **for now** | user 21-Nov; FT/TT source/destination account reading discussed, not chosen; other actions (APPLYPAYMENT…) and deposit payouts: decide later (GAPS A7/A8) |

HLSD deviations to be reflected by the BA: creation as Auth routine, GEN.PARAM instead of EB.ALBE.GEN.CIF.LIMIT, counted date in REMARKS, currency conversion.

## 7. Open items (see `docs/GAPS.md`)

- **E1 exchange rate** (D54): max CIF accounts in other currencies converted with `Currency.calculateBuyAmount` (EXCHRATE, market 1, today); no rate → `EB-ALBE.EXCH.RATE.MISSING` override, check skipped. Bank to confirm market 1 / rate type.
- **G1 fixed** (T24 date); **G4 mitigated** (ORG.SYSTEM.DATE fallback)
- **G2** lost update on parallel inputs (needs lock API) – team decision
- **G8 closed** (26-Nov, D45): no ACCOUNT SELECT; empty CUSTOMER.ACCOUNT blocks.
- **G3 fixed** (23-Nov, D37): utilisation / TXN.DETAILS save failure → `AlbeClActivityLimit` raises EB.ERROR `EB-ALBE.LIMIT.UPDATE.FAILED` (transaction cancelled). DEV: TQ18
- **G4 / G5 closed** (26-Nov, D42: no REMARKS tag)
- **G6 fixed** (22-Nov): blank CIF.LIMIT CCY → **error** EB-ALBE.CIF.LIMIT.CCY.MISSING (EB.ERROR), transaction blocked (user)
- **G10 fixed** (D54): max CIF reads EB.CONTRACT.BALANCES WORKING.BALANCE via the Account API and converts other currencies (Currency API, market 1); **DEV-46 passed 27-Nov** (log `1000001232 1150 EGP`); DEV-42 (USD account) open
- **DEV status 27-Nov (D54 + D55 JAR):** 30 Pass / 0 Fail / 21 Pending of 51 (`docs/DEV_TEST_RESULTS.xlsx`). Limit records recreated in EB.ALBE.CIF.LIMIT (100128, 100237-100241; 100242 none on purpose). Server table JAR replaced after `NoClassDefFoundError` (TQ24). Test data + one-shot OFS: `docs/DEV_TEST_DATA.md`. One-shot block passed (DEV-51, DEV-22, DEV-39). Pending: browser cases DEV-18/43/44, DEV-23, USD cases (product EGP only - needs 4 USD conditions or SIT)
- **G7** accounts in other companies (G9 fixed: credit/debit by ACTIVITY.CLASS)
- AAA findings **A1–A7** implemented (GAPS.md). DEV checks left: ORG.SYSTEM.DATE stable on re-input/DELETE/AUTH-REV (then drop the REMARKS tag + G5), COB UNAUTH.PROCESS decision, ORIG.TXN.AMT / TXN.AMOUNT.LCY filled at UNAUTH, `!TODAY` via Session. Waiting for a DEV credit/debit AAA from the user (user's Edge is granted read-only for screenshots).
- **Compile-verified against the real R25 libs** (21-Nov-2026, user's `C:\Temenos\R25\TAFJ\lib`, `TAFJ\ext`, `T24\bnk\t24lib`): 0 errors – all core hook signatures and API calls exist (`docs/TECHNICAL_REFERENCE.md` §12). **ALBE table JARs generated** (laptop `lib/l3-tables`: ALBE_CustomerLimitTable.jar, ALBE_GEN_PARAM_TABLE.jar) – merged code compiles with 0 errors against them + R25 libs (23-Nov, TQ1 closed). `.classpath` is kept local (absolute C:/Temenos paths), not on GitHub. Still open: DEV behaviour (REMARKS kept for DELETE/AUTH-REV, override value format, `$NAU` read, "record not found" text: **TQ13 closed 24-Nov** – R25 says `EB-INTERNAL.ERROR … Record does not exist`, in `RECORD_NOT_FOUND_MARKERS`). Logs: logger `API` → server `TAFJ/log/api.log` via ALERT.API (errors) / DIAGNOSTIC.API (info, level ERROR by default – set INFO in DEV to see them).
- To compile against the real libs in a sandbox: javac needs `-sourcepath <empty dir> -implicit:none` (the T24 JARs also contain `.java` sources); `org.json` from `TAFJ/ext/json.jar` is required.
- Pending from the bank: one-off CIF.LIMIT load for existing customers; HLSD update; TXN.DETAILS housekeeping.
- **FT / TELLER / ACCOUNT.PARAMETER routines removed (22-Nov-2026)** – ACTIVITY.API covers FT/TT on AA accounts. DEV: activity created at FT input; activity overrides on the FT screen; FT delete/reversal → DELETE/AUTH-REV. No non-AA accounts exist (D34). Own transfers: both sides checked and counted (D39).
- **D44 DEV steps:** create EB.API `ALBE.CL.ACTIVITY.LIMIT` + EB.ERROR `EB-ALBE.EXCH.RATE.MISSING`; deploy; one Post Routine per activity (T24_CONFIGURATION §8); TQ20 (DELETE / AUTH-REV reach validateRecord), TQ21 (TXN.EXCH.RATE meaning), **TQ22** (override declined / FT cancelled → our writes rolled back?). Also create EB.ERROR `EB-ALBE.CUSTOMER.ACCOUNT.MISSING`; D46: create the 4 debit/credit OVERRIDE ids and change all messages to one `&` (T24_CONFIGURATION §4). **D47: remove the old VAL / UPD / DEF / MAX.CIF from ACTIVITY.API and EB.API** (their classes are gone; together with ALBE.CL.ACTIVITY.LIMIT they double count). TQ22 passed.
- **D43 retest in DEV (TQ12 / TQ19):** deploy the new JAR, FT breaching daily + monthly → both overrides with `&` filled; "by" amount = used + this − limit (FT26093GZC6N showed 13,000 for an expected 12,750 – log not yet seen); debit FT; credit with MAX.CIF filled. Still to see: AUTH-REV take-back (FT26093BG24V reversal), DELETE (FT26093RL3T1).
- **Open questions** live only in `docs/QUERIES_AND_GAPS.md` (FQ/TQ); the old Q1–Q13 list in GAPS.md was removed as a duplicate (22-Nov).
- No remaining small code gap (G5 closed 26-Nov). (`\<mnemonic>` strip done via root reference.) (Outdated `<AAA id>` comments fixed 22-Nov.)

## 8. Testing (outside T24)

- No TAFJ JARs here. Tests use **stand-in classes outside the repo** (`/tmp/stubs2/src`: minimal TField, TStructure, DataAccess / Session with in-memory DB and CONCAT maps, hooks, generated records / tables with static maps and failWrite flags, logger). **Never commit stubs.**
- Test program `/tmp/tc2/TS2.java` calls the real `validateRecord` / `postUpdateRequest` (credits: AlbeClActivityLimit then AlbeClMaxCif): 39 cases T01–T26, E01–E07, M01–M03, C01–C03, all pass (stubs now include Account, Currency, TNumber, TDate).
  Run: `javac -d /tmp/out2 $(find /tmp/stubs2/src src -name '*.java') /tmp/tc2/TS2.java && java -cp /tmp/out2 TS2` (`-Dshowinfo=1` shows log lines). If the sandbox was reset, recreate the stubs from the imports in `src`.
- Real-lib compile: `javac -proc:none -sourcepath <empty> -implicit:none -cp <R25 + lib/l3-tables jars>` (uploads `R25/` and `ALBECustomerLimitClaude/lib/l3-tables`; `org.json` from `TAFJ/ext/json.jar`) – 0 errors after D50.
- Not covered: real T24 behaviour → DEV tests in `docs/T24_CONFIGURATION.md` §13 and `docs/GO_LIVE_CHECKLIST.md`; OFS test lines were given to the user (USER RAJA channel BROWSERTC blocks OFS – admin to fix).

## 9. Docs map

| File | Content |
|---|---|
| `README.md` | overview, how it works, classes, tables, overrides, logging |
| `docs/TECHNICAL_REFERENCE.md` | architecture, classes, flows, decisions, HLSD deviations, APIs to verify, testing |
| `docs/T24_CONFIGURATION.md` | T24 set-up steps 1–15 (tables, JARs, GEN.PARAM, OVERRIDE, EB.API, VERSION, ACTIVITY.API, versions, logging, go-live data, exchange rate, tests, **menu & access**) |
| `docs/GAPS.md` | known gaps, fixes without table change, exchange-rate questions |
| `docs/QUERIES_AND_GAPS.md` | **register**: functional queries FQ1–FQ20 (bank/BA), technical queries TQ1–TQ24 (DEV), decisions D1–D55, customer-routine gaps C1–C4, daily/monthly gaps DM1–DM2, gaps raised/solved – keep it short; update when a query is answered |
| `docs/TRACEABILITY.md` | HLSD 7.2.1 / use cases → code → tests; deviations D1–D8 |
| `docs/UNIT_TEST_CASES.md` | 43 cases after D50 (37 pass outside T24, 6 DEV only) – add a case for every new behaviour |
| `docs/RELEASE_NOTES.md` | release 1.0 objects, installation order, smoke test, rollback |
| `docs/GO_LIVE_CHECKLIST.md` | tick-box go-live list: open items, install order, data load, smoke test, rollback, after go-live, per-product worksheet |
| `docs/DATA_MIGRATION.md` | M1 limits for existing customers, M2 migrated monthly usage, cut-over |
| `docs/SUPPORT_RUNBOOK.md` | monitoring, common cases, error lines, FAQ |
| `docs/CODE_REVIEW_CHECKLIST.md` | standards as tick-boxes – use before every merge |
| `docs/DEV_TEST_DATA.md` | DEV test customers / accounts, standard limits, how to run OFS (heredoc, `C` lines, RAJA1 / AUTHOR), one-shot scenario block, follow-up flows (Word copy `docs/ALBE_Unit_Test_Data.docx`) |
| `docs/DEV_TEST_RESULTS.xlsx` | DEV results per case (Pass / Pending), T24 components, test data, FTs, messages – built by a script outside the repo |
| `CHANGELOG.md` | plain-language history – add an entry for every change |

When code changes, keep these docs in step (README class/routine tables, TECHNICAL_REFERENCE change history, T24_CONFIGURATION set-up, TRACEABILITY, UNIT_TEST_CASES, CHANGELOG) and update this file's decisions/open items.

Key facts found in the HLSD vs the approved DEV tables: the HLSD lists 9 TXN.DETAILS fields (incl. TXN.CCY, EXCH.RATE, TXN.AMOUNT.LCY, SYSTEMDATE) but the **approved** ALBE.TXN.DETAILS.LIMIT has only ID (max 100), ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT (File Type Int) – the code follows the approved table (deviation D3; TXN.AMOUNT holds the limit-currency amount). The HLSD says "update utilised only if not exceeded"; agreed: count also accepted-override transactions (D5).
