# ALBE Customer Limit – Technical Reference

Reference for developers who maintain or extend the Customer Limit code.
For the T24 set-up steps (tables, EB.API, VERSION, ACTIVITY.API, overrides) see
[T24_CONFIGURATION.md](T24_CONFIGURATION.md).

| | |
|---|---|
| Source | HLSD *Albaraka T24 project – Account module – 3 CBE Mandate CRs V2*, section 7.2.1 |
| Product | Temenos Transact (T24) on TAFJ, Java 21 |
| Repository | `shunmugarajag/ALBECustomerLimitClaude`, branch `main` |
| Package | `com.temenos.albe` (AlbeClActivityLimit, AlbeClMaxCif, AlbeClLimitCreate, AlbeClConstants) |
| Last updated | 26-Nov-2026 |

---

## 1. What the module does

| # | Function | When | Result |
|---|---|---|---|
| 1 | **Customer limit creation** | Customer authorised (VERSION CUSTOMER,ALBE.CORP) | Copies the generic limits from EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` into EB.ALBE.CIF.LIMITS `<customer>`, only if the customer has no record yet |
| 2 | **Daily / monthly limit check** | AA credit or debit activity input | Override if today's or this month's credit (or debit) total would go over the customer's limit |
| 3 | **Utilisation update** | Same activities: input, rejection, reversal | Keeps the daily and monthly totals in EB.ALBE.UTILISED.LIMIT correct |
| 4 | **Maximum CIF balance check** | AA credit activity input | Override if the total balance of all the customer's accounts plus the credit would go over MAX.CIF.BALANCE |
| 5 | **Overrides** | After each check | Acceptable overrides from EB.OVERRIDE, raised on TXN.AMOUNT |

All limit breaches are **acceptable overrides**, not errors: the user can accept and continue.

---

## 2. Architecture

Three T24 routines in package `com.temenos.albe`, each named after its EB.API, plus one constants class. Written like the team's core Java routines: the hook method reads what it needs with `DataAccess`, `Session` and the generated Table classes, decides, raises overrides / errors on the activity, writes, and logs with `logger.info`. No service / repository / model layers (the MVC version was removed 26-Nov, D50 – Git history keeps it).

```
 ACTIVITY.API Post Routine 1 (all mapped activities)   ALBE.CL.ACTIVITY.LIMIT -> AlbeClActivityLimit
 ACTIVITY.API Post Routine 2 (credit activities)       ALBE.CL.MAX.CIF        -> AlbeClMaxCif
 VERSION CUSTOMER,ALBE.CORP Auth routine              ALBE.CL.LIMIT.CREATE   -> AlbeClLimitCreate
                                    all literals       AlbeClConstants
```

### Rules every class follows

| Rule | Why |
|---|---|
| One class per routine, named after its EB.API | Easy to find from the T24 set-up |
| All literals in `AlbeClConstants` (the only `final` class) | Table names, ids, override / error ids, log tag in one place |
| `logger.info` only, logger `API`, every line starts with `ALBE.CUSTOMER.LIMIT |` | One grep finds everything |
| Every hook body in `try`; unexpected exceptions are logged and do not block | A code or data problem cannot stop the bank's transactions |
| No inner classes, no interfaces | Team standard |
| Generated Table class for UTILISED.LIMIT / TXN.DETAILS reads | Sees records written earlier in the same commit (D46) |

---

## 3. Classes

| Class | Hook | Responsibility |
|---|---|---|
| `AlbeClActivityLimit` | `ActivityLifecycle.validateRecord` (Post Routine 1) | UNAUTH: mapping, amount, limits, conversion, earlier input, daily / monthly check, overrides, count; DELETE / AUTH-REV: take back; AUTH / REVERSE: nothing. Save failure → error EB-ALBE.LIMIT.UPDATE.FAILED |
| `AlbeClMaxCif` | `ActivityLifecycle.validateRecord` (Post Routine 2, credits) | UNAUTH only: WORKING.BALANCE of all CUSTOMER.ACCOUNT accounts + credit vs MAXIMUM.OVERALL.CIF.AMOUNT; no accounts → error; counts nothing |
| `AlbeClLimitCreate` | `RecordLifecycle.postUpdateRequest` (Auth routine) | creates EB.ALBE.CIF.LIMITS from GEN.PARAM `CUSTOMER.LIMIT` when T24 says there is no record; never overwrites |
| `AlbeClConstants` | – | all literals |

---

## 4. Data

### 4.1 Tables

| Table | Record id | Written by | Read by |
|---|---|---|---|
| EB.ALBE.GEN.PARAM | `CUSTOMER.LIMIT` | user (one-off) | creation |
| EB.ALBE.CIF.LIMITS | customer id | creation; user (amend via version) | all checks |
| EB.ALBE.UTILISED.LIMIT | daily `<customer>-YYYYMMDD`, monthly `<customer>-YYYYMM` | utilisation update | daily / monthly check |
| EB.ALBE.TXN.DETAILS.LIMIT | `<customer>-<YYYYMMDD>-<FT/AAA>-<arrangement id>` (date = the activity's ORG.SYSTEM.DATE) | utilisation update (input) | rejection / reversal / re-input |
| ACCOUNT, CUSTOMER.ACCOUNT, COMPANY | core | – | maximum CIF check |

Field lists are in [T24_CONFIGURATION.md §1](T24_CONFIGURATION.md#1-local-tables).

### 4.2 Values taken from the activity

| Value | Filled from |
|---|---|
| customer | AA.ARRANGEMENT CUSTOMER (first) |
| key | root of TXN.CONTRACT.ID (FT / TT / payment behind the activity, cut at `\` and `;`), else the AAA id |
| arrangement | AAA ARRANGEMENT (part of the TXN.DETAILS id: both sides of an own transfer are two records) |
| date | ORG.SYSTEM.DATE, else T24 TODAY (`Session !TODAY`), else the server clock |
| direction | AAA ACTIVITY in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` → CREDIT / DEBIT (not mapped → error at input, D41) |
| amount | ORIG.TXN.AMT if filled, else TXN.AMOUNT; INITIATION.TYPE must be in the counted list |
| LCY amount / rate | ORIG.TXN.AMT.LCY / TXN.AMOUNT.LCY, TXN.EXCH.RATE (conversion, D44) |
| currency | AAA CURRENCY, else AA.ARRANGEMENT CURRENCY |

---

## 5. Flows

### 5.1 Customer limit creation (Auth routine)

```
customer authorised
  -> isValidCustomerId?                         no  -> stop
  -> cifLimitExists(customer)
        TRUE  -> SKIP (amendment: limits kept)
        null  -> SKIP + error log (read failed: never overwrite)
        FALSE -> read EB.ALBE.GEN.PARAM CUSTOMER.LIMIT
                   not found -> error log, nothing written
                   found     -> write EB.ALBE.CIF.LIMITS <customer> (CCY + 5 limits)
```

- Runs on **every** authorisation of the customer, including amendments – the exists check makes it safe.
- No delete flow: the record is created only at authorisation, so a deleted unauthorised customer never had one.
- "Not found" is recognised from the T24 read exception text (`Constants.RECORD_NOT_FOUND_MARKERS`). Any other read problem leaves the data untouched.

### 5.2 Input – `AlbeClActivityLimit` (activity UNAUTH)

```
build request (mapping CREDIT / DEBIT, amount, key, arrangement, date) -> isValid
  -> EB.ALBE.CIF.LIMITS <customer> (read once)
        none           -> "limit not defined" override; still counted (amount as it is)
        CURRENCY blank -> ERROR CIF.LIMIT.CCY.MISSING, stop (not counted)
  -> amount in the limit CCY (LCY amount, else TXN.EXCH.RATE, else ERROR EXCH.RATE.MISSING, stop)
  -> earlier input of the same FT: TXN.DETAILS <cust>-<date>-<FT>-<arrangement> (direct read)
  -> daily record <cust>-YYYYMMDD and monthly record <cust>-YYYYMM (read once)
  -> daily:   used - earlier + amount               > DAILY.<CR|DR>.LIMIT   -> override (excess)
  -> monthly: migrated + used - earlier + amount    > MONTHLY.<CR|DR>.LIMIT -> override (excess)
  -> blank limit field -> "limit not defined" (once)
  -> not blocked: daily / monthly = used - earlier + amount (one write each), TXN.DETAILS written
  -> view raises the overrides / errors, one per AAA field
```

Equal to the limit is allowed; only **above** is a breach. "used - earlier" is never below 0. The count is written in the same commit as the FT: a rejected override or a cancelled FT is rolled back by T24 (TQ22).

### 5.3 Take back – `AlbeClActivityLimit` (activity DELETE / AUTH-REV)

| Activity status | Action | EB.ALBE.UTILISED.LIMIT | EB.ALBE.TXN.DETAILS.LIMIT |
|---|---|---|---|
| UNAUTH (input, re-input) | count (5.2) | used - earlier input + new amount (daily + monthly) | written / replaced |
| DELETE (unauthorised rejected) | take back | the counted amount subtracted from the counted day and month | read by key, then deleted |
| AUTH | – | no change (already counted at input) | kept |
| REVERSE | – | no change (taken back at AUTH-REV) | kept |
| AUTH-REV (authorised reversed) | take back | as DELETE | as DELETE |

- Not found by key (e.g. a reversal on a later day without ORG.SYSTEM.DATE) → one SELECT by customer + FT + arrangement finds the counted day. Nothing found → nothing taken back (never a guess, D45).
- Amounts are stored in the **limit currency**; a take-back uses the stored amount, no new conversion.
- UTILISED.MIGRATED.* fields are **never written**. A total never goes below 0.

### 5.4 Maximum CIF balance check – `AlbeClMaxCif` (input, credit only)

```
own routine: reads the mapping, CIF.LIMITS and converts the credit itself (same rules as 5.2)
MAXIMUM.OVERALL.CIF.AMOUNT blank -> "limit not defined"
company financial mnemonic (COMPANY)
customer accounts: CUSTOMER.ACCOUNT <customer>; empty -> ERROR CUSTOMER.ACCOUNT.MISSING (blocks, D45)
for each account: WORKING.BALANCE converted from ACCOUNT CURRENCY (no rate -> "rate missing" override, stop)
total + credit > MAXIMUM.OVERALL.CIF.AMOUNT -> breach (difference)
```

### 5.5 Currency conversion

| Case | Result |
|---|---|
| transaction: same currency, or either currency blank | unchanged (2 decimals) |
| transaction: other currency, limit in LCY | activity LCY amount, else amount × TXN.EXCH.RATE (D44) |
| transaction: neither, or limit not in LCY | ERROR EB-ALBE.EXCH.RATE.MISSING, blocked, not counted |
| transaction: LCY not readable (technical) | override EB-ABLE.EXCH.RATE.MISSING, not checked, not counted |
| max CIF account in another currency | no rate source yet → override, max CIF not checked (E1) |

Converted amounts are rounded to 2 decimals, HALF_UP (`Constants.AMOUNT_SCALE`).
**Pending (max CIF accounts only):** no rate source yet (E1) – candidate: R25 `com.temenos.t24.api.rates.Currency` (EXCHRATE).

### 5.6 Enquiries – removed (26-Nov-2026)

The three NOFILE reports (summary, period, transactions) and their classes were removed from the code at the user's request (Git history keeps them).

---

## 6. Overrides

| Id | Message (EB.OVERRIDE) | Parameters | Raised when |
|---|---|---|---|
| EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED / .DAILY.CREDIT. | Daily debit (credit) limit exceeded for Customer & | "customer by difference" | daily breach |
| EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED / .MONTHLY.CREDIT. | Monthly debit (credit) limit exceeded for Customer & | "customer by difference" | monthly breach |
| EB-ABLE.MAX.CIF.BAL.EXCEED | Overall balance limit exceeded for Customer & | "customer by difference" | maximum CIF breach |
| EB-ABLE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & | customer | no CIF.LIMIT record or a blank limit (once per transaction) |
| EB-ABLE.EXCH.RATE.MISSING | Exchange rate not available & | "from CCY to CCY" | no rate (max CIF accounts) |

Raised by `AlbeClActivityLimit` / `AlbeClMaxCif` as `TField.setOverride("[ID,param]")` with **one** parameter (D46 – DEV showed a second `&` is not filled) – R25 `EB.HANDLE.OVERRIDES` takes part 1 as the id and fills the `&` with the other parts (anything not starting with `[` is used raw). `TField.setOverride` **replaces** the field's one override value, so each override goes on its own AAA field (`fieldFor`): daily TXN.AMOUNT, monthly TXN.AMOUNT.LCY, max CIF ORIG.TXN.AMT, not defined CURRENCY, rate missing EFFECTIVE.DATE; errors (`setError`, same format, `EB.HANDLE.ERRORS`) on TXN.AMOUNT. D43.

Only `validateRecord` hands overrides back to T24 (R25 invokers: `AA_VALIDATE_RECORD_INVOKER` / `EB_TEMPLATE_HOOK_VALIDATE_RECORD_INVOKER` call `handleOverrides`; the post-update, default and VERSION `postUpdateRequest` invokers only turn an exception into an error). The routine runs as the **Post Routine** – the only stage called for FT activities.

---

## 7. What is checked before counting

| Check | Result |
|---|---|
| status UNAUTH / DELETE / AUTH-REV | other statuses: nothing |
| activity mapped CREDIT / DEBIT | not mapped: error at input, nothing on DELETE / AUTH-REV |
| INITIATION.TYPE in the counted list (blank counted) | otherwise skipped |
| amount > 0 | otherwise skipped |

---

## 8. Error handling

| Where | Behaviour |
|---|---|
| Hook method | Everything inside `try`; an unexpected exception is logged as `ERROR`. The transaction is **not** blocked |
| Reads | Not found → null / nothing (logged `NOT FOUND` where useful) |
| UTILISED.LIMIT / TXN.DETAILS write or delete fails | error **EB-ALBE.LIMIT.UPDATE.FAILED** – the whole transaction is cancelled (D37) |
| Creation | read failure → nothing written (never overwrite) |

Trade-off for every other technical failure: the transaction goes through without the check; the `ERROR` / `FAILED` log lines must be monitored.

---

## 9. Logging

`private static final Logger logger = LoggerFactory.getLogger(AlbeClConstants.LOGGER_NAME)` (logger `API` → `TAFJ/log/api.log`, DIAGNOSTIC.API – set it to INFO) and `logger.info(...)` only:

```
ALBE.CUSTOMER.LIMIT | <Class>: <EVENT> | details
ALBE.CUSTOMER.LIMIT | AlbeClActivityLimit: RESULT | INPUT FT26093T03 customer 100128 CREDIT 4000 on 2026-04-03 | daily: used 8000 + this 4000.00 = 12000.00 > 10000 EXCEEDED by 2000.00 | ...
```

Events: `ACTION`, `RESULT` (one each per call), `SKIP`, `NOT MAPPED`, `NOT FOUND`, `SELECT`, `CREATED`; problems `FAILED`, `BLOCKED`, `ERROR`.

```
grep "ALBE.CUSTOMER.LIMIT" <log> | grep <FT id>                 # one transaction
grep -E "ALBE.CUSTOMER.LIMIT.*\| RESULT \|" <log>               # outcomes
grep -E "ALBE.CUSTOMER.LIMIT.*\| (FAILED|BLOCKED|ERROR) \|" <log> # to monitor
```

---

## 10. Performance notes

| Point | Status |
|---|---|
| EB.ALBE.TXN.DETAILS.LIMIT read **by key** (`<customer>-<date>-<FT/AAA>-<arrangement>`, date = ORG.SYSTEM.DATE); SELECT only for a take-back that finds nothing by key | done |
| One request validation per call | done |
| Each record read once per call and passed along (no cache): CIF.LIMITS, TXN.DETAILS, daily + monthly UTILISED; each written once (re-input = one write with used − earlier + new) | done (D47) |
| CUSTOMER.ACCOUNT empty → error, no ACCOUNT SELECT (D45) | done |
| All log lines at INFO (no trace switch, D47) – keep DIAGNOSTIC.API at ERROR in production if the volume is a concern | user decision |
| EB.ALBE.TXN.DETAILS.LIMIT grows with every counted transaction | consider a housekeeping job for old records (keep at least as long as a reversal is possible) |

---

## 11. Design decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Limits only from EB.ALBE.CIF.LIMITS; GEN.PARAM only for creation | One source of truth per customer |
| 2 | Missing record / blank limit -> acceptable override (not error) | Business decision |
| 3 | Count utilisation at **input**, reverse at rejection and at reversal | Unauthorised transactions must count; accepted-override transactions count |
| 4 | T24 date for counting = the activity's ORG.SYSTEM.DATE (D42); take-back uses the counted day from TXN.DETAILS | Day/month buckets follow when the amount was counted |
| 5 | TXN.DETAILS id `<customer>-<YYYYMMDD>-<FT/AAA>-<arrangement>` | Direct read, no SELECT, no table change; two sides of an own transfer = two records |
| 6 | Re-input: record = used - earlier input + new amount (one write) | An amended transaction is not counted twice or checked against itself |
| 7 | Compare and store in limit currency; transaction CCY = AA.ARRANGEMENT CURRENCY | Consistent totals |
| 8 | Creation as **Auth** routine (HLSD says After Unau) | No delete flow needed |
| 9 | Creation never overwrites, even on read failure | Amended customer limits must never be lost |
| 10 | Technical failure does not block the transaction | Availability; monitored through logs |
| 11 | One class per routine, named after its EB.API, like the team's core Java routines (D48 / D50; MVC removed) | User: simpler to read and support |
| 12 | Local table structures are pre-approved: the code adds no fields | Table design is fixed |

### Deviations from the HLSD (to be reflected in the HLSD)

| HLSD 7.2.1 | Implemented | Agreed |
|---|---|---|
| Creation routine: After Unau on CUSTOMER,ALBE.CORP; delete CIF.LIMIT when the unauthorised customer is deleted | Auth routine; no delete flow | 20-Nov-2026 |
| Table EB.ALBE.GEN.CIF.LIMIT for generic limits | EB.ALBE.GEN.PARAM record `CUSTOMER.LIMIT` (KEY.NAME / KEY.VALUE) | yes |
| – | Currency conversion to the limit currency | added |

---

## 12. T24 API calls – verification status

**Compile check against the real R25 libraries (21-Nov-2026).** The whole `src/` was compiled against the user's local `C:\Temenos\R25\TAFJ\lib`, `TAFJ\ext` and `T24\bnk\t24lib` JARs (only the 4 ALBE tables replaced by stand-ins, as their generated JAR does not exist yet): **0 errors**. `@Override` is checked against the real hook classes, so every method name and signature below exists in R25.

JARs the code needs (all already on the Eclipse build path except the ALBE table JAR):

| JAR | Classes used |
|---|---|
| TAFJ `lib/TAFJClient.jar`, `lib/TAFJAPICommon.jar` | `TField`, `TStructure`, `TValidationResponse`, `T24CoreException` |
| TAFJ `lib/TAFJLogging.jar` | `com.temenos.logging.facade.Logger/LoggerFactory` |
| TAFJ `ext/json.jar` | `org.json` (needed by the generated record classes) |
| t24lib `AA_ActivityHook.jar` | `ActivityLifecycle`, `ArrangementContext` |
| t24lib `EB_TemplateHook.jar`, `EB_EnquiryHook.jar` | `RecordLifecycle`, `TransactionContext/TransactionData`, `Enquiry`, `FilterCriteria`, `EnquiryContext` |
| t24lib `EB_DataAccessApi.jar`, `EB_SessionApi.jar` | `DataAccess`, `Session` |
| t24lib `AA_Framework.jar`, `AA_PaymentSchedule.jar`, `AA_ProductManagement.jar` | `AaArrangementActivityRecord`, `AaArrangementRecord`, `AaAccountDetailsRecord`, `AaProductCatalogRecord` |
| t24lib `AC_AccountOpening.jar`, `ST_CompanyCreation.jar` | `AccountRecord`, `CompanyRecord` |
| **ALBE table JAR (to generate)** | `EbAlbeCifLimitRecord/Table`, `EbAlbeGenParamRecord` (+ `KeyNameClass`), `EbAlbeUtilisedLimitRecord/Table`, `EbAlbeTxnDetailsLimitRecord/Table` |

| Call | Status |
|---|---|
| Hook signatures: `ActivityLifecycle.defaultFieldValues / validateRecord / postCoreTableUpdate`, `RecordLifecycle.postUpdateRequest` | **verified (compile)** |
| `ArrangementContext.getArrangementActivityId()`, `getActivityStatus()` | **verified (compile)** |
| `AaArrangementActivityRecord.getTxnAmount / getEffectiveDate / getActivity / getRemarks / setRemarks / getOverride / getLinkedAppl`, `AaArrangementRecord.getCurrency / getCustomer` | **verified (compile)** |
| `TField.getValue / setValue / setOverride(String) / setError(String)` | **verified (compile + R25 bytecode)** – text `[ID,param]`, one parameter (D46; two parameters land in the first `&` in DEV); one override per field (setOverride replaces) – D43 |
| `DataAccess.getRecord(app, id)` / `getRecord(mne, app, suffix, id)`, `selectRecords`, `getConcatValues`; `Session.getCompanyId`; `CompanyRecord.getFinancialMne`; `AccountRecord.getWorkingBalance / getCurrency` | **verified (compile)** |
| `TransactionContext.getCurrentFunction()`; `T24CoreException(String, String)`; `FilterCriteria.getFieldname / getValue` | **verified (compile)** |
| Generated table class pattern `XxxTable.read(id)` (throws if missing) / `write(id, rec)` / `delete(id)` | **matches** the team's sample `FfPostScheduleUpdate` (`EbFfCollectionDetsTable`) |
| Generated ALBE getters / setters (`getDailyCreditLimit`, `KeyNameClass.getKeyName`, `setUtilisedCreditLimit`, …) | **open** – check once the ALBE table JAR is generated (compile in Eclipse) |
| Behaviour (not visible at compile time): REMARKS kept on the same AAA for DELETE / AUTH-REV; override value format `<id>}...`; AAA `$NAU` read; "record not found" text (`RECORD_NOT_FOUND_MARKERS`); `postCoreTableUpdate` fired for UNAUTH / DELETE / AUTH-REV | **DEV** |

---

## 13. Testing

- Compiled with **0 errors** against the R25 libraries and the ALBE table JARs.
- Tested **outside T24** by calling the real `validateRecord` / `postUpdateRequest` of the three routines with stand-in TAFJ classes and in-memory tables (kept **outside** the repository): 37 cases, all pass – see [UNIT_TEST_CASES.md](UNIT_TEST_CASES.md).
- Needs T24: the rollback itself, DELETE / AUTH-REV reaching the Post Routine (TQ20), the real WORKING.BALANCE – DEV tests in [T24_CONFIGURATION.md §13](T24_CONFIGURATION.md).

---

## 14. How to extend

| Change | Where |
|---|---|
| New limit type | field in EB.ALBE.CIF.LIMITS + GEN.PARAM key → `AlbeClConstants.KEY_*`, copy in `AlbeClLimitCreate`, check in `AlbeClActivityLimit` (or its own routine like `AlbeClMaxCif`) |
| New override | EB.OVERRIDE record + `AlbeClConstants.OVR_*`, raised on its own AAA field |
| Exchange rate for max CIF accounts (E1) | `AlbeClMaxCif` – e.g. R25 `com.temenos.t24.api.rates.Currency.calculateBuyAmount` (market and rate type to be decided) |
| New activity (FT, TELLER, TPH, ...) | GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` line + attach the routines in ACTIVITY.API – no code change |

---

## 15. Open items

Known code gaps with proposed fixes (no table change) and the exchange-rate questions: **[GAPS.md](GAPS.md)**.

| # | Item | Owner |
|---|---|---|
| 1 | Exchange rate for max CIF accounts in other currencies (E1) | bank / dev |
| 2 | EB.API `ALBE.CL.ACTIVITY.LIMIT` / `ALBE.CL.MAX.CIF` / `ALBE.CL.LIMIT.CREATE` pointed to `com.temenos.albe` classes; remove VAL / UPD / DEF | T24 |
| 3 | HLSD update for the deviations in section 11 | BA |
| 4 | DEV verification of the API calls in section 12 | dev |
| 5 | One-off creation of EB.ALBE.CIF.LIMITS for customers that already exist (separate flow) | bank / dev |
| 6 | Decide the DIAGNOSTIC.API level in production (INFO = see every line) | bank / dev |

---

## Change history

| Date | Change |
|---|---|
| 20-Nov-2026 | Initial implementation (creation, daily/monthly, utilisation, maximum CIF, overrides) |
| 20-Nov-2026 | Repository layer, MVC, Constants, LimitBreach model |
| 20-Nov-2026 | Currency conversion |
| 20-Nov-2026 | TXN.DETAILS read by key (no SELECT) |
| 21-Nov-2026 | Counted date in activity REMARKS; TXN.DETAILS id `<customer>-<date>-<AAA>` (HLSD); SYSTEM.DATE removed – no table change |
| 20-Nov-2026 | Validation once in controllers; ValidationUtil; LogUtil logging standard |
| 20-Nov-2026 | Readability refactor (credit/debit merged) |
| 20-Nov-2026 | Creation as Auth routine, no delete flow, never overwrite |
| 20-Nov-2026 | GEN.PARAM record id CUSTOMER.LIMIT |
| 20-Nov-2026 | Enquiries: limit summary and transaction report; strict date parsing |
| 20-Nov-2026 | Period report (daily / monthly over a range), OVER.LIMIT filter, transactions for all customers |
| 20-Nov-2026 | Transaction report OVERRIDDEN filter (overrides read from AA.ARRANGEMENT.ACTIVITY at report time; no table change); separate REPORTS.md |
| 21-Nov-2026 | Credit / debit by AAA ACTIVITY.CLASS instead of activity name (G9); maximum CIF credit class only; REMARKS tag only on credit/debit classes |
| 23-Nov-2026 | FQ6 / D39: arrangement id added to the TXN.DETAILS id; own-account transfers counted and checked on both sides (marker logic removed) |
| 24-Nov-2026 | TQ13: "RECORD DOES NOT EXIST" added to `RECORD_NOT_FOUND_MARKERS` (R25 DataAccess.getRecord text) – customer limit was not created in DEV |
| 23-Nov-2026 | D37: utilisation save failure blocks the transaction (`EB-ALBE.LIMIT.UPDATE.FAILED`, `CustomerLimitRepository.isSaveFailed`) |
| 23-Nov-2026 | GEN.PARAM keys follow the DEV record: CURRENCY, MAXIMUM.OVERALL.CIF.BALANCE (`Constants.PARAM_KEY_*`, read in `CustomerLimitReader`) |
| 23-Nov-2026 | SonarQube findings: Constants lists `List.of` (read-only), repeated log literals as class constants, unused imports removed; no logic change |
| 22-Nov-2026 | Service sub-packages `service.activity` (engine, daily/monthly, max CIF, TXN.DETAILS recorder, conversion) and `service.customer` (creation); shared Reader / UtilizationUpdater stay in `service`; no logic change |
| 22-Nov-2026 | Report sub-packages `model.enquiry` (report rows) and `service.enquiry` (CustomerLimitEnquiryService); no logic change |
| 26-Nov-2026 | D42: counted date only from the activity ORG.SYSTEM.DATE (else T24 TODAY); `defaultFieldValues` (DEF routine) and the REMARKS tag removed; G4 / G5 closed |
| 26-Nov-2026 | D43: checks attached as Post Routines (before UPD) – override shown on the FT screen in DEV; override / error text `[ID,p1,p2]`; one override per AAA field (`CustomerLimitOverride.raiseFor(activity, …)`, `fieldFor`) |
| 26-Nov-2026 | D44: single routine `CustomerLimitActivityController` (one Post Routine, `validateRecord`: INPUT check + max CIF + overrides + count, DELETE / AUTH-REV take back); engine `processInput`, records read once (`UtilisationRecords`, no cache); transaction amount converted only from the activity (LCY amount, else TXN.EXCH.RATE, else EB.ERROR `EB-ALBE.EXCH.RATE.MISSING`); limit not in LCY blocked |
| 26-Nov-2026 | Reports removed (user): 3 NOFILE enquiry controllers, `CustomerLimitEnquiryService`, report rows, `CustomerLimitEnquiryView`, `CustomerLimitEnquiryValidator`, report-only repository methods (`selectCifLimitIds`, `selectUtilisedLimitIds`, `selectTxnDetailsIds(customer, days)`, `readActivity`, `readTransactionOverrides`) and constants; REPORTS.md deleted |
| 26-Nov-2026 | D45 review fixes: single routine take-back without the "from today" fallback, NOTHING TAKEN BACK / SAVE FAILED in RESULT, ACTION line on technical error, blank LCY = override (not blocked); max CIF: CUSTOMER.ACCOUNT empty → EB.ERROR `EB-ALBE.CUSTOMER.ACCOUNT.MISSING`, ACCOUNT SELECT fallback removed (G8) |
| 26-Nov-2026 | **D50**: MVC package `com.temenos.ALBE.CustomerLimit` removed; the simple version is the only code: package `com.temenos.albe`, classes named after the EB.API (`AlbeClActivityLimit`, `AlbeClMaxCif`, `AlbeClLimitCreate`, `AlbeClConstants`) |
| 26-Nov-2026 | **D47 clean rewrite**: one routine only (`DailyMonthlyLimitController`, `MaximumCifLimitController`, `DailyMonthlyLimitService`, `UtilisationRecords` removed); engine = `processInput` (each record read once, written once: used - earlier + new) + `reverse`; max CIF on the converted request; `LimitBreach` without message text; `LogUtil` info only |
| 26-Nov-2026 | D46: separate debit / credit override ids for daily and monthly, one parameter per message ("customer by amount" / "from CCY to CCY"); TXN.DETAILS read through the Table class (sees a write of the same commit – DEV FT2609332FH0 counted twice) |
| 25-Nov-2026 | Credit/debit mapping in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (D41); unmapped activity → EB-ALBE.ACTIVITY.NOT.MAPPED; ACTIVITY.CLASS action logic removed |
| 22-Nov-2026 | Controller sub-packages `controller.customer`, `controller.activity`, `controller.enquiry` (EB.API package names updated) |
| 22-Nov-2026 | FT / TELLER / ACCOUNT.PARAMETER routines **removed** – FT, TT and other postings on AA accounts arrive as activities, covered by ACTIVITY.API (classes in Git history) |
| 22-Nov-2026 | Key without `.CR/.DR`; own-account transfers (same customer both sides) not checked or counted |
| 22-Nov-2026 | Limit management wherever attached: `CustomerLimitEngine` (one entry point), `AccountingEntryLimitController` + `StatementEntryRequestBuilder` (ACCOUNT.PARAMETER, every transaction), same counted key `<root reference>.<CR\|DR>` for all routines (no double count), repository with one `T24Context` constructor, `CustomerLimitOverride.texts`; FT/TT un-parked |
| 21-Nov-2026 | FUNDS.TRANSFER and TELLER version routines (`FundsTransferLimitController`, `TellerLimitController`, `TransactionSideReader`, `TransactionLimitHandler`): both sides, each customer side for its own customer; count at input, take back on delete / authorised reversal; `Constants.AA_SKIP_TXN_SYSTEM_IDS` against double counting; report reads FT/TT overrides |
| 21-Nov-2026 | Common for all activities (`ActivityRequestBuilder`): class action CREDIT/DEBIT any product line, INITIATION.TYPE list, ORIG.TXN.AMT, TXN.AMOUNT.LCY when limit CCY = LCY, T24 date (ORG.SYSTEM.DATE / !TODAY) instead of the server clock (G1); TXN.REF column in the transaction report |
