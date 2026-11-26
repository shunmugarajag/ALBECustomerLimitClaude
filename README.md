# ALBECustomerLimit

Eclipse **Java Project** for the ALBE Customer Limit implementation.

## Documents

| Document | For |
|---|---|
| [docs/TECHNICAL_REFERENCE.md](docs/TECHNICAL_REFERENCE.md) | How the code works: architecture, classes, flows, data, overrides, logging, design decisions, HLSD deviations, open items |
| [docs/TRACEABILITY.md](docs/TRACEABILITY.md) | HLSD requirement → code → test, use cases 8.1–8.6, agreed deviations |
| [docs/UNIT_TEST_CASES.md](docs/UNIT_TEST_CASES.md) | test cases with results (run outside T24, plus DEV-only cases) |
| [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) | release 1.0: objects to deploy, installation order, smoke test, rollback, per-environment settings |
| [docs/DATA_MIGRATION.md](docs/DATA_MIGRATION.md) | one-off loads before go-live: limits for existing customers, migrated monthly usage, cut-over |
| [docs/SUPPORT_RUNBOOK.md](docs/SUPPORT_RUNBOOK.md) | production support: monitoring, common cases, error lines, business FAQ, escalation |
| [docs/CODE_REVIEW_CHECKLIST.md](docs/CODE_REVIEW_CHECKLIST.md) | project standards as a review checklist |
| [CHANGELOG.md](CHANGELOG.md) | plain-language history of changes |
| [CLAUDE.md](CLAUDE.md) | Handover for a new (Claude) session: rules, decisions and why, code map, open items, how to test |
| [docs/GAPS.md](docs/GAPS.md) | Known gaps and open points (no table change needed): risk, example, proposed fix, decision needed; exchange rate questions for the bank |
| [docs/QUERIES_AND_GAPS.md](docs/QUERIES_AND_GAPS.md) | Register: functional queries (bank / BA), technical queries (DEV), decisions taken, gaps raised and solved |
| [docs/T24_CONFIGURATION.md](docs/T24_CONFIGURATION.md) | What to set up in T24 / TAFJ: tables, JARs, GEN.PARAM, EB.OVERRIDE, EB.API, VERSION, ACTIVITY.API, versions, logging, go-live data, tests, **user menu and access (step 15)** |

## Project configuration

- Project type: Eclipse Java Project
- Java version: 21
- Source folder: `src`
- Output folder: `bin`
- Build system: Maven (`mvn clean package` builds `ALBE_CustomerLimit.jar`; `mvn clean deploy` copies it to the T24 server)

## Import into Eclipse

1. Clone this repository.
2. In Eclipse, select **File → Import → General → Existing Projects into Workspace**.
3. Browse to the cloned `ALBECustomerLimit` folder.
4. Select the project and finish.

## TAFJ libraries

Add the required TAFJ and generated Table JARs through **Project → Properties → Java Build Path → Libraries → Add External JARs** (or Add JARs if stored in the project).

The repository does not include proprietary TAFJ libraries. Configure the JARs from your local TAFJ installation.

## How it works (one routine)

One Post Routine, `ALBE.CL.ACTIVITY.LIMIT` (`CustomerLimitActivityController.validateRecord`), is attached to every mapped activity:

| Activity status | What happens |
|---|---|
| UNAUTH (input / amendment) | 1. EB.ALBE.CIF.LIMITS read once (none → override "limit not defined", still counted; blank CURRENCY → error) 2. amount converted to the limit currency (cannot → error) 3. earlier input of the same FT found by its TXN.DETAILS key 4. daily + monthly records read once 5. daily / monthly check (used − earlier input + this > limit → override) 6. credit: max CIF check (no CUSTOMER.ACCOUNT → error) 7. not blocked → count (each record = used − earlier input + this, written once) and TXN.DETAILS written |
| DELETE / AUTH-REV | take back exactly what TXN.DETAILS shows was counted, from the day it was counted on; nothing counted → nothing taken back |
| AUTH / REVERSE | nothing (no read) |

The count is written in the same commit as the FT, so a rejected override or a cancelled FT is undone by T24 (TQ22).

## Simple version (package `CustomerLimitSimple`, 26-Nov)

The same logic in **3 routine classes + constants**, in the style of the team's core Java routines (DataAccess / Session / generated tables used directly, one logger), next to the MVC version - the MVC code is not changed:

| Class | EB.API (suggested) | Attach as |
|---|---|---|
| `com.temenos.ALBE.CustomerLimitSimple.CustomerLimitActivity` | `ALBE.CL.ACTIVITY.SIMPLE` | ACTIVITY.API Post Routine 1 on every mapped activity, `validateRecord` (daily / monthly check + overrides + count, DELETE / AUTH-REV take back) |
| `com.temenos.ALBE.CustomerLimitSimple.CustomerLimitMaxCif` | `ALBE.CL.MAX.CIF.SIMPLE` | ACTIVITY.API Post Routine 2 on **credit** activities only, `validateRecord` (maximum CIF balance at input; no CUSTOMER.ACCOUNT -> error; counts nothing) |
| `com.temenos.ALBE.CustomerLimitSimple.CustomerLimitCreation` | `ALBE.CL.LIMIT.CREATE.SIMPLE` | VERSION CUSTOMER,ALBE.CORP Auth routine, `postUpdateRequest` |
| `com.temenos.ALBE.CustomerLimitSimple.CustomerLimitConstants` | - | all literals |

Same rules, tables, override / error ids and log tag (`ALBE.CUSTOMER.LIMIT |`, one ACTION + one RESULT line per call). Both versions are in the same JAR: attach **either** the simple **or** the MVC routine to an activity / version, never both (double count).

## Code structure (MVC)

```
T24 hook ──> controller ──> service ──> repository ──> T24 tables
                 │             │
                 │             └── model (CustomerLimitDTO, Limits, LimitBreach)
                 └── view (CustomerLimitOverride: overrides shown to the user)
```

| Layer | Package | Classes | Role |
|---|---|---|---|
| Controller | `controller.customer` | CustomerLimitCreationController (CUSTOMER version) |
| | `controller.activity` | **CustomerLimitActivityController** (the only activity routine), ActivityRequestBuilder (AAA → request) |
| Service | `service` | CustomerLimitReader, CustomerLimitUtilizationUpdater | Shared: read limits / activity mapping; utilisation record ids / amounts / add. |
| | `service.activity` | CustomerLimitEngine (`processInput` / `reverse`), MaximumCifLimitService, TxnDetailsLimitRecorder, CurrencyConversionService | Daily / monthly check, max CIF check, count and take back. |
| | `service.customer` | CustomerLimitCreationService | Limit creation (CUSTOMER version). |
| Validation | `validation` | CustomerLimitRequestValidator | Checks the request built from T24 **once**, in the controller (customer, AAA id, date, one positive amount). Services trust a validated request. |
| Utility | `util` | LogUtil | Writes every log line in one standard format (see *Logging standard*). |
| Utility | `util` | ValidationUtil | Common value checks shared by every layer: `isBlank`, `nvl`, `isPositive`, `toAmount` (blank/invalid = 0), `toOptionalAmount` (blank = limit not defined), `toText`, `toDate` (YYYYMMDD), `sameCurrency`. No business rules. |
| Repository | `repository` | CustomerLimitRepository | **All** database reads / writes / selects / deletes, each logged. |
| Model | `model` | CustomerLimitDTO, Limits, LimitBreach | Data passed between layers. |
| View | `view` | CustomerLimitOverride | Overrides / errors on the AAA fields (`[ID,param]`, one parameter, D46). |
| Constants | `constants` | Constants | All constants: tables, record ids, statuses, activity keywords, field / key names, override ids, messages, date formats. The only `final` class. |

## Routines to register (EB.API)

| Class | Attach to | Hooks |
|---|---|---|
| `com.temenos.ALBE.CustomerLimit.controller.customer.CustomerLimitCreationController` | VERSION CUSTOMER,ALBE.CORP – **Auth routine** | `postUpdateRequest`: when the customer is authorised, creates EB.ALBE.CIF.LIMITS `<customer>` from EB.ALBE.GEN.PARAM CUSTOMER.LIMIT if not present. No delete flow: an unauthorised customer that is deleted never had a limit record. On customer amendment (authorised again) an existing record is **never overwritten**; if the read fails for any reason other than "not found", nothing is written. (HLSD 7.2.1 says After Unau; changed to Auth.) |
| `com.temenos.ALBE.CustomerLimit.controller.activity.CustomerLimitActivityController` *(the only activity routine, D47)* | ACTIVITY.API **Post Routine** (EB.API `ALBE.CL.ACTIVITY.LIMIT`) – any mapped activity | `validateRecord`: UNAUTH = check daily / monthly + max CIF (credit), overrides, count; DELETE / AUTH-REV = take back; AUTH / REVERSE = nothing. ACTION log line INPUT / DELETE / REVERSAL / AUTHORISE / REVERSAL INPUT. Each record read once per call (no cache). |

The routine reads limits only from EB.ALBE.CIF.LIMITS `<customer>`. If the customer has no record, or a limit field is blank, the acceptable override EB-ABLE.CIF.LIMIT.NOT.DEFINED ("Customer limit not defined for Customer &") is raised once per transaction. EB.ALBE.GEN.PARAM CUSTOMER.LIMIT is used only by the creation service.

**FT, TELLER and other transactions** on AA accounts arrive as an `ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT` activity, so the one ACTIVITY.API routine covers them; it calls `service/activity/CustomerLimitEngine` (`processInput` at input, `reverse` for DELETE / AUTH-REV). An activity is counted under the FT/TT behind it (TXN.CONTRACT.ID), else its own id – the id also carries the arrangement, so each side of an FT is its own record. An **own-account transfer** (same customer on both sides) gives two activities (debit and credit arrangement); each calls the routine and is checked and counted on its own (D39). There are no non-AA accounts.

The routine can be attached to **any activity** (`controller/activity/ActivityRequestBuilder`): an activity is counted when it has an amount (ORIG.TXN.AMT, else TXN.AMOUNT) and its ACTIVITY is mapped CREDIT or DEBIT in EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (not mapped → error EB-ALBE.ACTIVITY.NOT.MAPPED, D41). Dates use the **T24 date** (ORG.SYSTEM.DATE → T24 TODAY), not the server clock. A transaction in another currency is converted with the activity's LCY amount, else TXN.EXCH.RATE, else blocked (D44).

### Currency conversion

All checks and utilisation use the **limit currency** (EB.ALBE.CIF.LIMITS `CURRENCY`):

- transaction currency = AA.ARRANGEMENT `CURRENCY`; if it differs from `CCY` the TXN.AMOUNT is converted;
- for the maximum CIF check every account's working balance is converted from the ACCOUNT currency;
- amounts are rounded to 2 decimals; EB.ALBE.UTILISED.LIMIT and EB.ALBE.TXN.DETAILS.LIMIT hold the converted amount;
- **transaction amount (D44):** only from the activity – LCY amount (TXN.AMOUNT.LCY / ORIG.TXN.AMT.LCY), else amount × TXN.EXCH.RATE; neither, or a limit not in LCY => **error EB-ALBE.EXCH.RATE.MISSING, transaction blocked**;
- max CIF, another account in a foreign currency with no rate => override EB-ABLE.EXCH.RATE.MISSING and the max CIF check is skipped (parked, E1).

**To configure (max CIF accounts only):** set `Constants.EXCH_RATE_TABLE` and `Constants.EXCH_RATE_FIELD`, and complete the read in
`CustomerLimitRepository.readExchangeRate(fromCcy, toCcy)` (returns the rate for 1 unit of fromCcy in toCcy).
Until then only same-currency transactions are checked.

## Local table field names assumed by the code

Create the tables with these field names so the generated getters match.

**EB.ALBE.GEN.PARAM** (EB.TABLE.DEFINITION `ALBE.GEN.PARAM`, confirmed) – generic limits, record `CUSTOMER.LIMIT`, KEY.NAME / KEY.VALUE:

| KEY.NAME | KEY.VALUE |
|---|---|
| CURRENCY | LCY (→ CIF.LIMIT CCY) |
| DAILY.CREDIT.LIMIT | daily credit limit |
| DAILY.DEBIT.LIMIT | daily debit limit |
| MONTHLY.CREDIT.LIMIT | monthly credit limit |
| MONTHLY.DEBIT.LIMIT | monthly debit limit |
| MAXIMUM.OVERALL.CIF.BALANCE | maximum CIF balance (→ CIF.LIMIT MAX.CIF.BALANCE) |

**EB.ALBE.CIF.LIMITS** (id: customer id) – create EB.TABLE.DEFINITION `ALBE.CIF.LIMITS` with these field names

| No | Field | Description | Type | Getter / Setter |
|---|---|---|---|---|
| 1 | CURRENCY | Currency | Text | getCurrency / setCurrency |
| 2 | DAILY.CREDIT.LIMIT | Daily credit limit | Amount | getDailyCreditLimit / setDailyCreditLimit |
| 3 | DAILY.DEBIT.LIMIT | Daily debit limit | Amount | getDailyDebitLimit / setDailyDebitLimit |
| 4 | MONTHLY.CREDIT.LIMIT | Monthly credit limit | Amount | getMonthlyCreditLimit / setMonthlyCreditLimit |
| 5 | MONTHLY.DEBIT.LIMIT | Monthly debit limit | Amount | getMonthlyDebitLimit / setMonthlyDebitLimit |
| 6 | MAXIMUM.OVERALL.CIF.AMOUNT | Maximum overall CIF balance | Amount | getMaximumOverallCifAmount / setMaximumOverallCifAmount |

**EB.ALBE.UTILISED.LIMIT** (EB.TABLE.DEFINITION `ALBE.UTILISED.LIMIT`, confirmed)

| Field | Daily record `<customer>-YYYYMMDD` | Monthly record `<customer>-YYYYMM` |
|---|---|---|
| UTILISED.CREDIT.LIMIT | daily credit utilised (updated) | monthly credit utilised (updated) |
| UTILISED.DEBIT.LIMIT | daily debit utilised (updated) | monthly debit utilised (updated) |
| UTILISED.MIGRATED.CREDIT.LIMIT | – | migrated credit (read only) |
| UTILISED.MIGRATED.DEBIT.LIMIT | – | migrated debit (read only) |

Monthly utilisation = UTILISED.MIGRATED.* + UTILISED.* of the monthly record.

**EB.ALBE.TXN.DETAILS.LIMIT** (EB.TABLE.DEFINITION `ALBE.TXN.DETAILS.LIMIT`) – **structure as approved, no change**

Record id: `<customer>-<YYYYMMDD>-<FT/TT or AAA id>-<arrangement id>`, e.g. `100234-20261120-FT26324ABCDE-AA26324XYZ12`; YYYYMMDD = T24 date the amount was counted on.
That date is the activity's own **ORG.SYSTEM.DATE** (the T24 date of the activity, kept by T24 on authorisation, rejection and reversal; else T24 TODAY), so every later step – also a rejection or a reversal on another day – rebuilds the id and reads the record **directly by key**; if not found, one SELECT by transaction finds the original day. (D42, 26-Nov: the REMARKS tag and the DEF routine were removed – the date comes from the activity's ORG.SYSTEM.DATE)

| Field | Value |
|---|---|
| ACCOUNT.NO | arrangement's linked account |
| CREDIT.DEBIT.FLAG | CR / DR |
| EFF.DATE | effective (value) date of the transaction |
| TXN.AMOUNT | amount counted, in the limit currency |

Utilisation is counted when the transaction is initiated:

| Activity status | Utilisation | EB.ALBE.TXN.DETAILS.LIMIT |
|---|---|---|
| UNAUTH (input / re-input) | added; earlier input of the same AAA subtracted first | written / replaced |
| DELETE (unauthorised rejected) | reversed | read and deleted |
| AUTH | no change | kept |
| AUTH-REV (authorised reversed) | reversed on the original day/month | read and deleted |

Validation on re-input leaves out the AAA's own earlier input amount.

## EB.OVERRIDE records

| Id | Message |
|---|---|
| EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED / EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED | Daily debit (credit) limit exceeded for Customer & |
| EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED / EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED | Monthly debit (credit) limit exceeded for Customer & |
| EB-ABLE.MAX.CIF.BAL.EXCEED | Overall balance limit exceeded for Customer & |
| EB-ABLE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & |
| EB-ABLE.EXCH.RATE.MISSING | Exchange rate not available & (max CIF only) |
| EB-ALBE.LIMIT.UPDATE.FAILED | **Error** (EB.ERROR, blocks): Customer limit could not be updated, transaction cancelled – saving the utilisation (UTILISED.LIMIT / TXN.DETAILS) failed (D37) |
| EB-ALBE.CIF.LIMIT.CCY.MISSING | **Error** (EB.ERROR, blocks): Limit currency not defined for Customer & – the customer's EB.ALBE.CIF.LIMITS CCY is blank |
| EB-ALBE.ACTIVITY.NOT.MAPPED | **Error** (EB.ERROR, blocks): Activity & not defined in customer limit parameter – activity missing in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (D41) |
| EB-ALBE.CUSTOMER.ACCOUNT.MISSING | **Error** (EB.ERROR, blocks, D45): No accounts found in CUSTOMER.ACCOUNT for Customer & – max CIF check with an empty CUSTOMER.ACCOUNT (no ACCOUNT SELECT fallback) |
| EB-ALBE.EXCH.RATE.MISSING | **Error** (EB.ERROR, blocks, D44): Exchange rate not available &, transaction not allowed – no LCY amount and no TXN.EXCH.RATE on the activity, or the limit is not in LCY |

`view.CustomerLimitOverride` raises them as `[ID,"<customer> by <amount over>"]` – **one parameter** (D46: a second `&` is not filled in DEV), **each on its own AAA field** because a field keeps only one override: daily → TXN.AMOUNT, monthly → TXN.AMOUNT.LCY, max CIF → ORIG.TXN.AMT, not defined → CURRENCY, rate missing → EFFECTIVE.DATE; errors on TXN.AMOUNT (D43).

In ACTIVITY.API the one routine `ALBE.CL.ACTIVITY.LIMIT` is the **Post Routine** – for FT-created activities only the Post Routine is called, and the override still shows on the FT screen (DEV 26-Nov). See `docs/T24_CONFIGURATION.md` §8.

## Logging standard

All logging goes through `util/LogUtil`, **INFO level only** (user, 26-Nov). No class creates its own logger.

```
ALBE.CUSTOMER.LIMIT | <Class>.<method> | <EVENT> | details [| Exception: message @ frame]
ALBE.CUSTOMER.LIMIT | CustomerLimitActivityController.validateRecord | RESULT | INPUT FT26093BGWJR (ACCOUNTS-CREDIT-ARRANGEMENT) customer 100199 CREDIT 11,000.00 EGP on 2026-04-03 ... | daily credit (100199-20260403): used 10,000.00 + this 11,000.00 = 21,000.00 > limit 10,000.00 -> EXCEEDED by 11,000.00 | ... | raised: EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED
```

Each class has `private static final String CLASS_NAME = "<ClassName>";` and calls
`LogUtil.info(CLASS_NAME, "method", "EVENT", details[, e])`. The **event** tells the kind of line:

| Group | Events |
|---|---|
| Summary | `ACTIVITY` (activity fields at the start) `ACTION` (INPUT / DELETE / REVERSAL ... and what is done) `RESULT` (plain-language outcome with every number) |
| Checks | `CALC` `INVALID` `SKIP` `OVERRIDE` `ERROR` (raised on the screen) |
| Data | `FOUND` `NOT FOUND` `NEW` `CREATED` `WRITTEN` `UPDATED` `DELETED` `SELECT` `ADJUSTED` |
| Problems | `FAILED` `BLOCKED` `NO RATE` `NO LCY` `NO CCY` `NOT CONFIGURED` `NOT MAPPED` |

The lines go to `TAFJ/log/api.log` through DIAGNOSTIC.API – set it to **INFO** to see them.
Useful greps: `grep "ALBE.CUSTOMER.LIMIT"` (everything), `grep "ALBE.CUSTOMER.LIMIT.*| RESULT |"` (one line per call), `grep "ALBE.CUSTOMER.LIMIT.*| FAILED\|BLOCKED |"` (problems).
Logging never throws, so a logging problem cannot stop a transaction.
