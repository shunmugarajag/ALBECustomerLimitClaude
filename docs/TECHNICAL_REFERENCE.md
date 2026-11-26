# ALBE Customer Limit – Technical Reference

Reference for developers who maintain or extend the Customer Limit code.
For the T24 set-up steps (tables, EB.API, VERSION, ACTIVITY.API, overrides) see
[T24_CONFIGURATION.md](T24_CONFIGURATION.md).

| | |
|---|---|
| Source | HLSD *Albaraka T24 project – Account module – 3 CBE Mandate CRs V2*, section 7.2.1 |
| Product | Temenos Transact (T24) on TAFJ, Java 21 |
| Repository | `shunmugarajag/ALBECustomerLimitClaude`, branch `main` |
| Package root | `com.temenos.ALBE.CustomerLimit` |
| Last updated | 20-Nov-2026 |

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

## 2. Architecture (MVC)

```
 T24 hook (EB.API)
      |
      v
 controller ----> validation (check request once)
      |
      v
 service  ----> repository ----> T24 tables (DataAccess / generated Table classes)
      |   \
      |    model (CustomerLimitDTO, Limits, LimitBreach)
      v
 view (CustomerLimitOverride -> TXN.AMOUNT overrides)

 util (LogUtil, ValidationUtil) and constants (Constants) are used by every layer.
```

### Rules every class follows

| Rule | Why |
|---|---|
| Only `repository/CustomerLimitRepository` touches the database | One place to see, log, tune or cache every DB call |
| Controllers hold no business rules and no DB access | Hooks stay thin; rules can be tested without T24 |
| The request is validated **once**, in the controller (`CustomerLimitRequestValidator`) | Services trust the request – no repeated null checks |
| Common value checks only in `util/ValidationUtil` | One rule for blank text, amounts, dates |
| All log lines only through `util/LogUtil` | One format, one switch, logging can never break a transaction |
| All literals in `constants/Constants` | Table names, ids, override ids, messages in one place |
| No inner classes, no interfaces | Team standard |
| No `final` classes except `Constants` | Team standard |
| Services are created with `new` and get the repository through their constructor | Simple manual injection; tests pass an in-memory repository |

---

## 3. Classes

| Package | Class | Responsibility |
|---|---|---|
| `controller.customer` | `CustomerLimitCreationController` | `RecordLifecycle.postUpdateRequest` – customer authorised: calls `CustomerLimitCreationService.create` |
| `controller.activity` | `DailyMonthlyLimitController` | `ActivityLifecycle.validateRecord` (check) and `postCoreTableUpdate` (count / reverse utilisation) |
| `controller.activity` | `MaximumCifLimitController` | `ActivityLifecycle.validateRecord` – maximum CIF balance check (credit only) |
| `service.activity` | `CustomerLimitEngine` | **one entry point**: transaction info + customer limit info → `checkDailyMonthly`, `checkMaxCif`, `count`, `reverse` (counted day found by the transaction if needed); `transactionKey(reference)` = root reference used by every routine; own-account transfer (other side already recorded for the customer) → not counted, 0-amount marker, reversal takes nothing back |
| `controller.activity` | `ActivityRequestBuilder` | common AAA → request mapping for **any activity**: counted or not (amount, direction from GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` – D41, INITIATION.TYPE), amount (ORIG.TXN.AMT / TXN.AMOUNT + LCY), currency, counted date (REMARKS → ORG.SYSTEM.DATE → T24 TODAY) |
| `validation` | `CustomerLimitRequestValidator` | `isValidForDailyMonthly`, `isValidForMaxCif`, `isValidCustomerId` |
| `service.customer` | `CustomerLimitCreationService` | `create(customerId)` – never overwrites an existing record |
| `service.activity` | `DailyMonthlyLimitService` | `validate`, `countAtInput`, `reverse` |
| `service.activity` | `MaximumCifLimitService` | `validate` – sums converted account balances |
| `service` (shared) | `CustomerLimitReader` | Reads customer limits (CIF.LIMIT) and generic limits (GEN.PARAM) into `Limits` |
| `service` (shared) | `CustomerLimitUtilizationUpdater` | Adds / subtracts amounts on the daily and monthly EB.ALBE.UTILISED.LIMIT records |
| `service.activity` | `TxnDetailsLimitRecorder` | Writes / reads / deletes EB.ALBE.TXN.DETAILS.LIMIT (what was counted, for reversal) |
| `service.activity` | `CurrencyConversionService` | Rate lookup and conversion to the limit currency |
| `repository` | `CustomerLimitRepository` | Every DB read / write / delete / select, each logged |
| `model` | `CustomerLimitDTO` | The request passed through all layers, plus results (breaches, missing limits, missing rate) |
| `model` | `Limits` | One customer's (or the generic) limits; `null` field = limit not defined |
| `model` | `LimitBreach` | One breached limit: override id, message, difference |
| `view` | `CustomerLimitOverride` | Raises the overrides on TXN.AMOUNT |
| `util` | `LogUtil` | Standard log lines (section 9) |
| `util` | `ValidationUtil` | `isBlank`, `nvl`, `isPositive`, `toAmount`, `toOptionalAmount`, `toText`, `toDate`, `sameCurrency` |
| `constants` | `Constants` | All constants (the only `final` class) |

---

## 4. Data

### 4.1 Tables

| Table | Record id | Written by | Read by |
|---|---|---|---|
| EB.ALBE.GEN.PARAM | `CUSTOMER.LIMIT` | user (one-off) | creation |
| EB.ALBE.CIF.LIMITS | customer id | creation; user (amend via version) | all checks |
| EB.ALBE.UTILISED.LIMIT | daily `<customer>-YYYYMMDD`, monthly `<customer>-YYYYMM` | utilisation update | daily / monthly check |
| EB.ALBE.TXN.DETAILS.LIMIT | `<customer>-<YYYYMMDD>-<FT/AAA>-<arrangement id>` (date = counted date from the activity REMARKS) | utilisation update (input) | rejection / reversal / re-input |
| ACCOUNT, CUSTOMER.ACCOUNT, COMPANY | core | – | maximum CIF check |

Field lists are in [T24_CONFIGURATION.md §1](T24_CONFIGURATION.md#1-local-tables).

### 4.2 Request (`CustomerLimitDTO`)

| Field | Filled from |
|---|---|
| customerId | AA.ARRANGEMENT CUSTOMER (first) |
| transactionId | AAA id (`arrangementContext.getArrangementActivityId()`); arrangement id as fallback. Maximum CIF check: arrangement id |
| transactionDate | counted date: the activity's ORG.SYSTEM.DATE, else T24 TODAY (D42; until 26-Nov: REMARKS tag `ALBE.LIMIT.DATE=YYYYMMDD`) → AAA ORG.SYSTEM.DATE → T24 TODAY (`Session !TODAY`) → server clock (logged last resort) |
| creditAmount / debitAmount | ORIG.TXN.AMT if filled, else TXN.AMOUNT; credit or debit from GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (AAA ACTIVITY → CREDIT/DEBIT, `CustomerLimitReader.getActivityDirection`; not mapped → EB.ERROR EB-ALBE.ACTIVITY.NOT.MAPPED, D41); INITIATION.TYPE must be counted (all types); otherwise skipped – `controller/activity/ActivityRequestBuilder`, common for all activities |
| amountLcy | ORIG.TXN.AMT.LCY / TXN.AMOUNT.LCY – used instead of a rate when the limit currency is the local currency |
| transactionCurrency | AAA CURRENCY, else AA.ARRANGEMENT CURRENCY |
| limitCurrency, exchangeRate, originalAmount | set by `CurrencyConversionService` |
| breaches, undefinedLimits, limitMissing, rateMissing | results, used by the view |

Helpers: `isCredit()`, `getAmount()`, `logKey()` (`customer=<id> aaa=<id>`).

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

### 5.2 Daily / monthly check (`validateRecord`, status UNAUTH)

```
build request -> validate request
  -> read EB.ALBE.CIF.LIMITS <customer>       none -> "limit not defined" override, stop
  -> convert TXN.AMOUNT to limit CCY          no rate -> "rate missing" override, stop
  -> earlier input of the same AAA (TXN.DETAILS by key) -> left out of the totals (re-input)
  -> daily:   UTILISED.<CR|DR>.LIMIT of <cust>-YYYYMMDD  - earlier  + amount  vs DAILY.<CR|DR>.LIMIT
  -> monthly: MIGRATED.<CR|DR> + UTILISED.<CR|DR> of <cust>-YYYYMM - earlier + amount vs MONTHLY.<CR|DR>.LIMIT
  -> blank limit field -> "limit not defined" (once); over the limit -> breach (difference)
  -> view raises the overrides on TXN.AMOUNT
```

Equal to the limit is allowed; only **above** is a breach. Utilised minus earlier is never below 0.

### 5.3 Utilisation (`postCoreTableUpdate`)

| Activity status | Action | EB.ALBE.UTILISED.LIMIT | EB.ALBE.TXN.DETAILS.LIMIT |
|---|---|---|---|
| UNAUTH (input, re-input) | count | earlier input of the AAA subtracted, new amount added (daily + monthly) | written / replaced |
| DELETE (unauthorised rejected) | reverse | amount subtracted from the original day and month | read, then deleted |
| AUTH | – | no change (already counted at input) | kept |
| AUTH-REV (authorised reversed) | reverse | as DELETE; if no TXN.DETAILS record exists (counted before go-live) the amount is subtracted from today's records | read, then deleted |

- Counted at **input**, so accepted-override transactions count and other unauthorised transactions see them.
- The original day/month comes from the activity **REMARKS** (`ALBE.LIMIT.DATE=YYYYMMDD`, written by `defaultFieldValues` at first input), so a reversal on a later day subtracts from the right records and finds TXN.DETAILS `<customer>-<date>-<AAA>` directly.
- Amounts are stored in the **limit currency**; a reversal uses the stored amount, no new conversion.
- UTILISED.MIGRATED.* fields are **never written**.
- A total never goes below 0.

### 5.4 Maximum CIF balance check (`validateRecord`, status UNAUTH, credit only)

```
read EB.ALBE.CIF.LIMITS          none -> "limit not defined"; MAX.CIF.BALANCE blank -> "limit not defined"
convert credit to limit CCY     no rate -> "rate missing"
company financial mnemonic (COMPANY)
customer accounts: CUSTOMER.ACCOUNT concat; fallback SELECT ACCOUNT WITH CUSTOMER EQ <id>
for each account: WORKING.BALANCE converted from ACCOUNT CURRENCY (no rate -> "rate missing", stop)
total + credit > MAX.CIF.BALANCE -> breach (difference)
```

### 5.5 Currency conversion

| Case | Rate |
|---|---|
| same currency, or either currency blank | 1 |
| different currencies | `CustomerLimitRepository.readExchangeRate(from, to)` |
| no rate found | override EB-ABLE.EXCH.RATE.MISSING, check skipped |

Converted amounts are rounded to 2 decimals, HALF_UP (`Constants.AMOUNT_SCALE`).
**Pending:** the rate table and field are not configured yet (`Constants.EXCH_RATE_TABLE` / `EXCH_RATE_FIELD` are empty), so today only same-currency transactions are checked; other currencies raise the rate-missing override.

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

Raised by `CustomerLimitOverride.raiseFor(activity, request)` as `TField.setOverride("[ID,param]")` with **one** parameter (D46 – DEV showed a second `&` is not filled) – R25 `EB.HANDLE.OVERRIDES` takes part 1 as the id and fills the `&` with the other parts (anything not starting with `[` is used raw). `TField.setOverride` **replaces** the field's one override value, so each override goes on its own AAA field (`fieldFor`): daily TXN.AMOUNT, monthly TXN.AMOUNT.LCY, max CIF ORIG.TXN.AMT, not defined CURRENCY, rate missing EFFECTIVE.DATE; errors (`setError`, same format, `EB.HANDLE.ERRORS`) on TXN.AMOUNT. D43.

Only `validateRecord` hands overrides back to T24 (R25 invokers: `AA_VALIDATE_RECORD_INVOKER` / `EB_TEMPLATE_HOOK_VALIDATE_RECORD_INVOKER` call `handleOverrides`; the post-update, default and VERSION `postUpdateRequest` invokers only turn an exception into an error). In DEV the checks run as **Post Routines** (VAL, MAX.CIF before UPD) – the only stage called for FT activities.

---

## 7. Validation of the request (controller, once)

| Check | Daily / monthly | Maximum CIF | Creation |
|---|---|---|---|
| customer id present | yes | yes | yes |
| AAA id present | yes | – | – |
| transaction date present | yes | – | – |
| exactly one positive amount (credit **or** debit) | yes | credit > 0 | – |
| currency present | no (blank = treated as same currency) | no | – |

A request that fails is logged as `INVALID` with the reason, and no check, override or update is done.

---

## 8. Error handling

| Where | Behaviour |
|---|---|
| Controller | Everything inside `try`; an unexpected exception is logged as `ERROR` with type, message and code line. The transaction is **not** blocked |
| Repository read | Not found -> `null` (logged `NOT FOUND`) |
| Repository write | Failure -> `false` (logged `FAILED`) |
| Creation | Read failure -> nothing written (never overwrite) |
| Logging | `LogUtil` never throws |

**Exception (decision D37, 23-Nov):** when saving EB.ALBE.UTILISED.LIMIT / EB.ALBE.TXN.DETAILS.LIMIT fails, the repository marks it (`isSaveFailed`) and `DailyMonthlyLimitController.postCoreTableUpdate` throws `EB-ALBE.LIMIT.UPDATE.FAILED`, so the whole transaction is cancelled and usage is never silently missed.

Trade-off for every other technical failure: it lets the transaction through without the check. This is the deliberate choice so that a code or data problem cannot stop the bank's transactions; the `ERROR` / `FAILED` log lines must be monitored.

---

## 9. Logging

Format:

```
ALBE.CUSTOMER.LIMIT | <Class>.<method> | <EVENT> | details [| Exception: message @ frame]
```

| Call | Level | Use |
|---|---|---|
| `LogUtil.info(CLASS_NAME, method, EVENT, details[, e])` | info | normal flow |
| `LogUtil.error(CLASS_NAME, method, EVENT, details[, e])` | error | failure, bad data |
| `LogUtil.detail(CLASS_NAME, method, EVENT, details)` | info, only when `Constants.LOG_DETAIL_ENABLED` | DB trace before the call |

Events: `START END STEP SKIP ACTION` · `OK INVALID BREACH NOT DEFINED OVERRIDE` · `CALC UPDATED ADJUSTED` ·
`READ WRITE DELETE SELECT FOUND NOT FOUND NEW CREATED WRITTEN DELETED` · `FAILED ERROR NO RATE NOT CONFIGURED`.

Useful searches:

```
grep "ALBE.CUSTOMER.LIMIT" <log>                          # everything
grep "ALBE.CUSTOMER.LIMIT.*customer=100234" <log>          # one customer
grep -E "ALBE.CUSTOMER.LIMIT.*\| (BREACH|OVERRIDE) \|" <log>
grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR|FAILED) \|" <log>  # to monitor
```

Controller `END` lines carry `elapsedMs` for timing.

---

## 10. Performance notes

| Point | Status |
|---|---|
| EB.ALBE.TXN.DETAILS.LIMIT read **by key** (`<customer>-<date>-<AAA>`, date from REMARKS), no SELECT | done |
| One request validation per hook call | done |
| DB trace lines switchable (`LOG_DETAIL_ENABLED=false` in production) | done |
| Per-transaction read cache (CIF.LIMIT, UTILISED.LIMIT and COMPANY are read more than once) | possible next step |
| CUSTOMER.ACCOUNT empty -> SELECT on ACCOUNT fallback (slow on a large file) | consider removing once CUSTOMER.ACCOUNT is confirmed reliable |
| Per-record read lines (FOUND / NOT FOUND of CIF.LIMIT, UTILISED.LIMIT, TXN.DETAILS, ACCOUNT) are `detail` | done – with `LOG_DETAIL_ENABLED=false` a 1,000-customer summary writes 3 lines instead of ~3,000 |
| EB.ALBE.TXN.DETAILS.LIMIT grows with every counted transaction | consider a housekeeping job for old records (keep at least as long as a reversal is possible) |

---

## 11. Design decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Limits only from EB.ALBE.CIF.LIMITS; GEN.PARAM only for creation | One source of truth per customer |
| 2 | Missing record / blank limit -> acceptable override (not error) | Business decision |
| 3 | Count utilisation at **input**, reverse at rejection and at reversal | Unauthorised transactions must count; accepted-override transactions count |
| 4 | Server date for counting, kept in the activity REMARKS; reversal uses that date | Day/month buckets follow when the amount was counted |
| 5 | TXN.DETAILS id `<customer>-<YYYYMMDD>-<AAA id>` (HLSD), date from REMARKS | Direct read, no SELECT, no table change |
| 6 | Re-input subtracts its own earlier input first | An amended transaction is not counted twice or checked against itself |
| 7 | Compare and store in limit currency; transaction CCY = AA.ARRANGEMENT CURRENCY | Consistent totals |
| 8 | Creation as **Auth** routine (HLSD says After Unau) | No delete flow needed |
| 9 | Creation never overwrites, even on read failure | Amended customer limits must never be lost |
| 10 | Technical failure does not block the transaction | Availability; monitored through logs |
| 11 | MVC, single repository, validation once, LogUtil / ValidationUtil | Readability, one place per concern |
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

- The code was compiled and tested **outside T24**, using stand-in versions of the TAFJ classes and an in-memory repository (`MemRepo extends CustomerLimitRepository`). Those stand-ins are **not** in this repository.
- Covered: credit and debit daily / monthly breach, migrated monthly amounts, re-input, rejection, reversal on a later day, blank limit, missing record, currency conversion and missing rate, maximum CIF with mixed-currency accounts, request validation, creation (exists / not found / read failure).
- Not covered (needs T24): the three controllers with real T24 objects. See the DEV test cases in [T24_CONFIGURATION.md §9](T24_CONFIGURATION.md#9-dev--sit-test-cases).

---

## 14. How to extend

| Change | Where |
|---|---|
| New limit type | add the field to EB.ALBE.CIF.LIMITS and GEN.PARAM key -> `Constants.KEY_*` + `PARAM_KEY_*`, `Limits`, `CustomerLimitReader`, `CustomerLimitCreationService`, check in the service, override in `Constants.OVR_*` |
| New override | EB.OVERRIDE record + `Constants.OVR_*` / `MSG_*` + `LimitBreach` via `request.addBreach(...)` |
| Exchange rate table | `Constants.EXCH_RATE_TABLE`, `EXCH_RATE_FIELD`, `CustomerLimitRepository.readExchangeRate` |
| Any new DB access | a method in `CustomerLimitRepository` only, logged like the others |
| New log lines | `LogUtil` with an event from section 9 |

---

## 15. Open items

Known code gaps with proposed fixes (no table change) and the exchange-rate questions: **[GAPS.md](GAPS.md)**.

| # | Item | Owner |
|---|---|---|
| 1 | Exchange rate table / field and the read in `readExchangeRate` | bank / dev |
| 2 | Attach `defaultFieldValues` of ALBE.CL.DAILY.MONTHLY in ACTIVITY.API (counted date into REMARKS) | T24 |
| 3 | HLSD update for the deviations in section 11 | BA |
| 4 | DEV verification of the API calls in section 12 | dev |
| 5 | One-off creation of EB.ALBE.CIF.LIMITS for customers that already exist (separate flow) | bank / dev |
| 6 | Set `LOG_DETAIL_ENABLED = false` before production | dev |

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
| 26-Nov-2026 | D46: separate debit / credit override ids for daily and monthly, one parameter per message ("customer by amount" / "from CCY to CCY"); TXN.DETAILS read through the Table class (sees a write of the same commit – DEV FT2609332FH0 counted twice) |
| 25-Nov-2026 | Credit/debit mapping in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (D41); unmapped activity → EB-ALBE.ACTIVITY.NOT.MAPPED; ACTIVITY.CLASS action logic removed |
| 22-Nov-2026 | Controller sub-packages `controller.customer`, `controller.activity`, `controller.enquiry` (EB.API package names updated) |
| 22-Nov-2026 | FT / TELLER / ACCOUNT.PARAMETER routines **removed** – FT, TT and other postings on AA accounts arrive as activities, covered by ACTIVITY.API (classes in Git history) |
| 22-Nov-2026 | Key without `.CR/.DR`; own-account transfers (same customer both sides) not checked or counted |
| 22-Nov-2026 | Limit management wherever attached: `CustomerLimitEngine` (one entry point), `AccountingEntryLimitController` + `StatementEntryRequestBuilder` (ACCOUNT.PARAMETER, every transaction), same counted key `<root reference>.<CR\|DR>` for all routines (no double count), repository with one `T24Context` constructor, `CustomerLimitOverride.texts`; FT/TT un-parked |
| 21-Nov-2026 | FUNDS.TRANSFER and TELLER version routines (`FundsTransferLimitController`, `TellerLimitController`, `TransactionSideReader`, `TransactionLimitHandler`): both sides, each customer side for its own customer; count at input, take back on delete / authorised reversal; `Constants.AA_SKIP_TXN_SYSTEM_IDS` against double counting; report reads FT/TT overrides |
| 21-Nov-2026 | Common for all activities (`ActivityRequestBuilder`): class action CREDIT/DEBIT any product line, INITIATION.TYPE list, ORIG.TXN.AMT, TXN.AMOUNT.LCY when limit CCY = LCY, T24 date (ORG.SYSTEM.DATE / !TODAY) instead of the server clock (G1); TXN.REF column in the transaction report |
