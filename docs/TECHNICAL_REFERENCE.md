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
| 1 | **Customer limit creation** | Customer authorised (VERSION CUSTOMER,ALBE.CORP) | Copies the generic limits from EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` into EB.ALBE.CIF.LIMIT `<customer>`, only if the customer has no record yet |
| 2 | **Daily / monthly limit check** | AA credit or debit activity input | Override if today's or this month's credit (or debit) total would go over the customer's limit |
| 3 | **Utilisation update** | Same activities: input, rejection, reversal | Keeps the daily and monthly totals in EB.ALBE.UTILISED.LIMIT correct |
| 4 | **Maximum CIF balance check** | AA credit activity input | Override if the total balance of all the customer's accounts plus the credit would go over MAX.CIF.BALANCE |
| 5 | **Overrides** | After each check | Acceptable overrides from EB.OVERRIDE, raised on TXN.AMOUNT |
| 6 | **Enquiries** | On demand | Summary (one day), period report (per day / month over a range), transaction report; one or all customers, optionally over-limit only |

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
| `controller` | `CustomerLimitCreationController` | `RecordLifecycle.postUpdateRequest` – customer authorised: calls `CustomerLimitCreationService.create` |
| `controller` | `DailyMonthlyLimitController` | `ActivityLifecycle.validateRecord` (check) and `postCoreTableUpdate` (count / reverse utilisation) |
| `controller` | `MaximumCifLimitController` | `ActivityLifecycle.validateRecord` – maximum CIF balance check (credit only) |
| `controller` | `CustomerLimitSummaryEnquiryController` | `Enquiry.setIds` – NOFILE limit summary report |
| `controller` | `CustomerLimitPeriodEnquiryController` | `Enquiry.setIds` – NOFILE period report (daily / monthly rows) |
| `controller` | `CustomerLimitTxnEnquiryController` | `Enquiry.setIds` – NOFILE transaction report |
| `validation` | `CustomerLimitEnquiryValidator` | Enquiry selection checks: dates, months, ranges, range length, period given, customer or date |
| `service` | `CustomerLimitEnquiryService` | Builds the report rows (read only) |
| `model` | `LimitSummaryRow`, `LimitPeriodRow`, `LimitTxnRow` | One report row each (`isOverLimit()` on summary / period) |
| `view` | `CustomerLimitEnquiryView` | Formats the rows as `*`-separated enquiry lines (column order documented in the class) |
| `validation` | `CustomerLimitRequestValidator` | `isValidForDailyMonthly`, `isValidForMaxCif`, `isValidCustomerId` |
| `service` | `CustomerLimitCreationService` | `create(customerId)` – never overwrites an existing record |
| `service` | `DailyMonthlyLimitService` | `validate`, `countAtInput`, `reverse` |
| `service` | `MaximumCifLimitService` | `validate` – sums converted account balances |
| `service` | `CustomerLimitReader` | Reads customer limits (CIF.LIMIT) and generic limits (GEN.PARAM) into `Limits` |
| `service` | `CustomerLimitUtilizationUpdater` | Adds / subtracts amounts on the daily and monthly EB.ALBE.UTILISED.LIMIT records |
| `service` | `TxnDetailsLimitRecorder` | Writes / reads / deletes EB.ALBE.TXN.DETAILS.LIMIT (what was counted, for reversal) |
| `service` | `CurrencyConversionService` | Rate lookup and conversion to the limit currency |
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
| EB.ALBE.CIF.LIMIT | customer id | creation; user (amend via version) | all checks |
| EB.ALBE.UTILISED.LIMIT | daily `<customer>-YYYYMMDD`, monthly `<customer>-YYYYMM` | utilisation update | daily / monthly check |
| EB.ALBE.TXN.DETAILS.LIMIT | `<customer>-<YYYYMMDD>-<AAA id>` (date = counted date from the activity REMARKS) | utilisation update (input) | rejection / reversal / re-input |
| ACCOUNT, CUSTOMER.ACCOUNT, COMPANY | core | – | maximum CIF check |

Field lists are in [T24_CONFIGURATION.md §1](T24_CONFIGURATION.md#1-local-tables).

### 4.2 Request (`CustomerLimitDTO`)

| Field | Filled from |
|---|---|
| customerId | AA.ARRANGEMENT CUSTOMER (first) |
| transactionId | AAA id (`arrangementContext.getArrangementActivityId()`); arrangement id as fallback. Maximum CIF check: arrangement id |
| transactionDate | counted date from the activity REMARKS tag `ALBE.LIMIT.DATE=YYYYMMDD` (written at first input = server date); today if the tag is missing |
| creditAmount / debitAmount | TXN.AMOUNT; credit or debit decided by the activity name containing `CREDIT` / `DEBIT` |
| transactionCurrency | AA.ARRANGEMENT CURRENCY |
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
                   found     -> write EB.ALBE.CIF.LIMIT <customer> (CCY + 5 limits)
```

- Runs on **every** authorisation of the customer, including amendments – the exists check makes it safe.
- No delete flow: the record is created only at authorisation, so a deleted unauthorised customer never had one.
- "Not found" is recognised from the T24 read exception text (`Constants.RECORD_NOT_FOUND_MARKERS`). Any other read problem leaves the data untouched.

### 5.2 Daily / monthly check (`validateRecord`, status UNAUTH)

```
build request -> validate request
  -> read EB.ALBE.CIF.LIMIT <customer>       none -> "limit not defined" override, stop
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
read EB.ALBE.CIF.LIMIT          none -> "limit not defined"; MAX.CIF.BALANCE blank -> "limit not defined"
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
| no rate found | override EB-ALBE.EXCH.RATE.MISSING, check skipped |

Converted amounts are rounded to 2 decimals, HALF_UP (`Constants.AMOUNT_SCALE`).
**Pending:** the rate table and field are not configured yet (`Constants.EXCH_RATE_TABLE` / `EXCH_RATE_FIELD` are empty), so today only same-currency transactions are checked; other currencies raise the rate-missing override.

### 5.6 Enquiries

Full description, columns, examples and T24 set-up: **[REPORTS.md](REPORTS.md)**.

| Enquiry | Selection | Reads |
|---|---|---|
| Summary | CUSTOMER (opt.), DATE (opt., default today), OVER.LIMIT | per customer: CIF.LIMIT + daily + monthly UTILISED.LIMIT; without CUSTOMER a SELECT of CIF.LIMIT ids |
| Period | DATE.FROM–DATE.TO **or** MONTH.FROM–MONTH.TO, CUSTOMER (opt.), OVER.LIMIT | all customers: **one** SELECT on UTILISED.LIMIT (`@ID LIKE ...-YYYYMM...` per month), then one read per row; one customer: direct reads per day/month. CIF.LIMIT read once per customer (cached in the service for the run) |
| Transactions | CUSTOMER and/or DATE.FROM / DATE.TO (counted date in the id) | one SELECT on TXN.DETAILS (`@ID LIKE <cust>-...` or `...-<day>-...` per day), then reads |

- Only days / months **with usage** appear in the period report (no UTILISED.LIMIT record = no row).
- Available = limit − used (negative = over the limit after an accepted override; blank when the limit is not defined).
- Ranges are capped (`ENQ_MAX_DAYS` = 92, `ENQ_MAX_MONTHS` = 24); the transaction report needs a customer or a date and, without a customer, a range of at most 92 days.
- Bad selection raises an EB.ERROR (`EB-ALBE.ENQ.*`); nothing found raises `EB-NO.REC.SELECT`.
- Enquiries never write. Dates and months are parsed strictly (`DATE_PARSE_FORMAT`, `MONTH_PARSE_FORMAT`): 20261131 and 202613 are rejected.

---

## 6. Overrides

| Id | Message (EB.OVERRIDE) | Parameters | Raised when |
|---|---|---|---|
| EB-ALBE.DAILY.LIMIT.EXCEED | Daily credit/debit exceed for Customer & by & | customer, difference | daily breach |
| EB-ALBE.MONTHLY.LIMIT.EXCEED | Monthly credit/debit exceed for Customer & by & | customer, difference | monthly breach |
| EB-ALBE.MAX.CIF.BAL.EXCEED | Overall credit/debit exceed for Customer & by & | customer, difference | maximum CIF breach |
| EB-ALBE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & | customer | no CIF.LIMIT record or a blank limit (once per transaction) |
| EB-ALBE.EXCH.RATE.MISSING | Exchange rate not available from & to & | from CCY, to CCY | no rate |

Raised by `CustomerLimitOverride.raiseFor` as `TField.setOverride(id FM param1 VM param2)` on TXN.AMOUNT.

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

Trade-off: a technical failure lets the transaction through without the check. This is the deliberate choice so that a code or data problem cannot stop the bank's transactions; the `ERROR` / `FAILED` log lines must be monitored.

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
| Per-record read lines (FOUND / NOT FOUND of CIF.LIMIT, UTILISED.LIMIT, TXN.DETAILS, ACCOUNT) and per-row enquiry lines are `detail` | done – with `LOG_DETAIL_ENABLED=false` a 1,000-customer summary writes 3 lines instead of ~3,000 |
| Enquiries filter in the SELECT (not in Java), read only by key, no file writing, BigDecimal amounts | done |
| EB.ALBE.TXN.DETAILS.LIMIT grows with every counted transaction | consider a housekeeping job for old records (keep at least as long as a reversal is possible) |

---

## 11. Design decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Limits only from EB.ALBE.CIF.LIMIT; GEN.PARAM only for creation | One source of truth per customer |
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
| 12 | Local table structures are pre-approved: the code adds no fields (accepted overrides are read from AA.ARRANGEMENT.ACTIVITY at report time) | Table design is fixed |

### Deviations from the HLSD (to be reflected in the HLSD)

| HLSD 7.2.1 | Implemented | Agreed |
|---|---|---|
| Creation routine: After Unau on CUSTOMER,ALBE.CORP; delete CIF.LIMIT when the unauthorised customer is deleted | Auth routine; no delete flow | 20-Nov-2026 |
| Table EB.ALBE.GEN.CIF.LIMIT for generic limits | EB.ALBE.GEN.PARAM record `CUSTOMER.LIMIT` (KEY.NAME / KEY.VALUE) | yes |
| – | Currency conversion to the limit currency | added |

---

## 12. T24 API calls to verify in DEV

These are used by the code but are **not** in the team's sample routines. Check each once in DEV (the log lines show the values).

| Call | Used in |
|---|---|
| `ActivityLifecycle.validateRecord` + `getValidationResponse()` | both AA controllers |
| `ArrangementContext.getArrangementActivityId()`, `getActivityStatus()` | DailyMonthlyLimitController |
| `AaArrangementActivityRecord.getTxnAmount()` / `getEffectiveDate()` / `getActivity()` | AA controllers |
| `AaArrangementRecord.getCurrency()`, `getCustomer()`, `getLinkedAppl()` | AA controllers |
| `TField.setOverride(id FM p1 VM p2)` – parameter format | CustomerLimitOverride |
| `AaArrangementActivityRecord.getOverride()` and reading AA.ARRANGEMENT.ACTIVITY live / `$NAU`; override value format `<id>}...` | repository `readActivityOverrides` (overridden-transactions report) |
| `TransactionContext.getCurrentFunction()` | CustomerLimitCreationController (logged only) |
| `DataAccess.getConcatValues("CUSTOMER.ACCOUNT", id)` | repository |
| `AccountRecord.getWorkingBalance()` / `getCurrency()` | MaximumCifLimitService |
| Generated getters: `KeyNameClass.getKeyName/getKeyValue`, UTILISED.* getters | services |
| `ActivityLifecycle.defaultFieldValues` can set `AaArrangementActivityRecord.setRemarks(...)` at input, and REMARKS is kept on the same AAA for DELETE / AUTH-REV | DailyMonthlyLimitController |
| Text of the T24 "record not found" exception (`RECORD_NOT_FOUND_MARKERS`) | repository `cifLimitExists` |

---

## 13. Testing

- The code was compiled and tested **outside T24**, using stand-in versions of the TAFJ classes and an in-memory repository (`MemRepo extends CustomerLimitRepository`). Those stand-ins are **not** in this repository.
- Covered: credit and debit daily / monthly breach, migrated monthly amounts, re-input, rejection, reversal on a later day, blank limit, missing record, currency conversion and missing rate, maximum CIF with mixed-currency accounts, request validation, creation (exists / not found / read failure).
- Not covered (needs T24): the three controllers with real T24 objects. See the DEV test cases in [T24_CONFIGURATION.md §9](T24_CONFIGURATION.md#9-dev--sit-test-cases).

---

## 14. How to extend

| Change | Where |
|---|---|
| New limit type | add the field to EB.ALBE.CIF.LIMIT and GEN.PARAM key -> `Constants.KEY_*`, `Limits`, `CustomerLimitReader`, `CustomerLimitCreationService`, check in the service, override in `Constants.OVR_*` |
| New override | EB.OVERRIDE record + `Constants.OVR_*` / `MSG_*` + `LimitBreach` via `request.addBreach(...)` |
| Exchange rate table | `Constants.EXCH_RATE_TABLE`, `EXCH_RATE_FIELD`, `CustomerLimitRepository.readExchangeRate` |
| Any new DB access | a method in `CustomerLimitRepository` only, logged like the others |
| New log lines | `LogUtil` with an event from section 9 |

---

## 15. Open items

| # | Item | Owner |
|---|---|---|
| 1 | Exchange rate table / field and the read in `readExchangeRate` | bank / dev |
| 2 | Attach `defaultFieldValues` of ALBE.CL.DAILY.MONTHLY in ACTIVITY.API (counted date into REMARKS) | T24 |
| 3 | HLSD update for the deviations in section 11 | BA |
| 4 | DEV verification of the API calls in section 12 | dev |
| 5 | One-off creation of EB.ALBE.CIF.LIMIT for customers that already exist (separate flow) | bank / dev |
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
