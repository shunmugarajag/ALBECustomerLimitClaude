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
| [docs/T24_CONFIGURATION.md](docs/T24_CONFIGURATION.md) | What to set up in T24 / TAFJ: tables, JARs, GEN.PARAM, OVERRIDE, EB.API, VERSION, ACTIVITY.API, versions, logging, go-live data, tests, **user menu and access (step 15)** |

## Project configuration

- Project type: Eclipse Java Project
- Java version: 21
- Source folder: `src`
- Output folder: `bin`
- Build system: Maven (`mvn clean package` builds `ALBE_CustomerLimit.jar`; `mvn clean deploy` copies it to the T24 server)
- Source: `src/com/temenos/albe` (4 classes)

## Import into Eclipse

1. Clone this repository.
2. In Eclipse, select **File → Import → General → Existing Projects into Workspace**.
3. Browse to the cloned `ALBECustomerLimit` folder.
4. Select the project and finish.

## TAFJ libraries

Add the required TAFJ and generated Table JARs through **Project → Properties → Java Build Path → Libraries → Add External JARs** (or Add JARs if stored in the project).

The repository does not include proprietary TAFJ libraries. Configure the JARs from your local TAFJ installation.

## How it works

Three routines in package **`com.temenos.albe`**, each named after its EB.API (the MVC version was removed 26-Nov; Git history keeps it):

| EB.API | Class | Attach as | Does |
|---|---|---|---|
| `ALBE.CL.ACTIVITY.LIMIT` | `AlbeClActivityLimit` | ACTIVITY.API **Post Routine 1** on every mapped activity, method `validateRecord` | daily / monthly check, overrides, count at input; take back on DELETE / AUTH-REV |
| `ALBE.CL.MAX.CIF` | `AlbeClMaxCif` | ACTIVITY.API **Post Routine 2** on **credit** activities, method `validateRecord` | maximum CIF balance at input; counts nothing |
| `ALBE.CL.LIMIT.CREATE` | `AlbeClLimitCreate` | VERSION CUSTOMER,ALBE.CORP.INPUT **Auth routine**, method `postUpdateRequest` | creates EB.ALBE.CIF.LIMITS from GEN.PARAM `CUSTOMER.LIMIT` if missing; never overwrites |
| – | `AlbeClConstants` | – | all literals (tables, ids, override / error ids, log tag) |

Written like the team's core Java routines: `DataAccess`, `Session` and the generated tables are used directly, one `LoggerFactory` logger (`API`), `logger.info` only.

| Activity status | `AlbeClActivityLimit` | `AlbeClMaxCif` |
|---|---|---|
| UNAUTH (input / amendment) | 1. EB.ALBE.CIF.LIMITS (none → override "not defined", still counted; blank CURRENCY → error) 2. amount in the limit currency (cannot → error) 3. earlier input of the same FT (TXN.DETAILS key) 4. daily / monthly: used − earlier + this > limit → override 5. count: used − earlier + this, TXN.DETAILS written | WORKING.BALANCE of all CUSTOMER.ACCOUNT accounts + this credit > MAXIMUM.OVERALL.CIF.AMOUNT → override; no accounts → error |
| DELETE / AUTH-REV | take back exactly what TXN.DETAILS shows was counted, from the counted day; nothing counted → nothing | – |
| AUTH / REVERSE | nothing | – |

The count is in the same commit as the FT: a rejected override or an error (from either routine) is rolled back by T24 (TQ22). FT, TELLER, TPH and other postings on AA accounts all arrive as an activity, so the same routines cover them – each activity used must be mapped in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` and have the routines attached.

### Currency conversion

All checks and utilisation use the **limit currency** (EB.ALBE.CIF.LIMITS `CURRENCY`):
- transaction in another currency: the activity's LCY amount, else amount × TXN.EXCH.RATE; neither, or a limit not in LCY → **error EB-ALBE.EXCH.RATE.MISSING**, not counted;
- max CIF: an account in another currency → override EB-ALBE.EXCH.RATE.MISSING, max CIF not checked (no rate source yet, E1);
- amounts rounded to 2 decimals; UTILISED.LIMIT / TXN.DETAILS hold the converted amount; a take-back never reconverts.

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
| UNAUTH (input / re-input) | used − earlier input of the same FT + this amount (one write) | written / replaced |
| DELETE (unauthorised rejected) | reversed | read and deleted |
| AUTH | no change | kept |
| AUTH-REV (authorised reversed) | reversed on the original day/month | read and deleted |

Validation on re-input leaves out the AAA's own earlier input amount.

## OVERRIDE records

| Id | Message |
|---|---|
| EB-ALBE.DAILY.DEBIT.LIMIT.EXCEED / EB-ALBE.DAILY.CREDIT.LIMIT.EXCEED | Daily debit (credit) limit exceeded for Customer & |
| EB-ALBE.MONTHLY.DEBIT.LIMIT.EXCEED / EB-ALBE.MONTHLY.CREDIT.LIMIT.EXCEED | Monthly debit (credit) limit exceeded for Customer & |
| EB-ALBE.MAX.CIF.BAL.EXCEED | Overall balance limit exceeded for Customer & |
| EB-ALBE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & |
| EB-ALBE.EXCH.RATE.MISSING | Exchange rate not available & (max CIF only) |
| EB-ALBE.LIMIT.UPDATE.FAILED | **Error** (EB.ERROR, blocks): Customer limit could not be updated, transaction cancelled – saving the utilisation (UTILISED.LIMIT / TXN.DETAILS) failed (D37) |
| EB-ALBE.CIF.LIMIT.CCY.MISSING | **Error** (EB.ERROR, blocks): Limit currency not defined for Customer & – the customer's EB.ALBE.CIF.LIMITS CCY is blank |
| EB-ALBE.ACTIVITY.NOT.MAPPED | **Error** (EB.ERROR, blocks): Activity & not defined in customer limit parameter – activity missing in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (D41) |
| EB-ALBE.CUSTOMER.ACCOUNT.MISSING | **Error** (EB.ERROR, blocks, D45): No accounts found in CUSTOMER.ACCOUNT for Customer & – max CIF check with an empty CUSTOMER.ACCOUNT (no ACCOUNT SELECT fallback) |
| EB-ALBE.EXCH.RATE.MISSING | **Error** (EB.ERROR, blocks, D44): Exchange rate not available &, transaction not allowed – no LCY amount and no TXN.EXCH.RATE on the activity, or the limit is not in LCY |

Raised as `[ID,"<customer> by <amount over>"]` – **one parameter** (D46), **each on its own AAA field** (a field keeps only one override): daily → TXN.AMOUNT, monthly → TXN.AMOUNT.LCY, max CIF → ORIG.TXN.AMT, not defined → CURRENCY, rate missing → EFFECTIVE.DATE; errors on TXN.AMOUNT (D43). Only the Post Routine is called for FT-created activities, and its override shows on the FT screen (DEV 26-Nov). See `docs/T24_CONFIGURATION.md` §8.

## Logging

Each routine has `private static final Logger logger = LoggerFactory.getLogger(AlbeClConstants.LOGGER_NAME)` (logger `API` → `TAFJ/log/api.log`, DIAGNOSTIC.API – set it to **INFO** to see the lines) and calls `logger.info(...)` only:

```
ALBE.CUSTOMER.LIMIT | AlbeClActivityLimit: ACTION | INPUT FT26093BQZQK (ACCOUNTS-TRANSFERIN-ARRANGEMENT) arrangement AA26093C0Y4T status UNAUTH
ALBE.CUSTOMER.LIMIT | AlbeClActivityLimit: RESULT | INPUT FT26093BQZQK customer 100128 CREDIT 1500 on 2026-04-03 | daily: used 33000 + this 1500.00 = 34500.00 > 10000 EXCEEDED by 24500.00 | ... | raised EB-ALBE.DAILY.CREDIT.LIMIT.EXCEED
ALBE.CUSTOMER.LIMIT | AlbeClMaxCif: RESULT | MAX CIF FT26093BQZQK customer 100128 CREDIT 1500.00 EGP | 1000001208 <balance> EGP | balance ... <= 200000 OK
```

Events: `ACTION` and `RESULT` (one each per call), `SKIP`, `NOT MAPPED`, `NOT FOUND`, `SELECT`, `CREATED`, problems `FAILED` / `BLOCKED` / `ERROR`.
Greps: `grep "ALBE.CUSTOMER.LIMIT" api.log | grep <FT id>` (one transaction), `grep "| RESULT |"` (outcomes), `grep -E "\| (FAILED|BLOCKED|ERROR) \|"` (problems).
