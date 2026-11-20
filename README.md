# ALBECustomerLimit

Eclipse **Java Project** for the ALBE Customer Limit implementation.

## Documents

| Document | For |
|---|---|
| [docs/TECHNICAL_REFERENCE.md](docs/TECHNICAL_REFERENCE.md) | How the code works: architecture, classes, flows, data, overrides, logging, design decisions, HLSD deviations, open items |
| [docs/T24_CONFIGURATION.md](docs/T24_CONFIGURATION.md) | What to set up in T24 / TAFJ: tables, JARs, GEN.PARAM, EB.OVERRIDE, EB.API, VERSION, ACTIVITY.API, logging, go-live data, DEV/SIT test cases |

## Project configuration

- Project type: Eclipse Java Project
- Java version: 21
- Source folder: `src`
- Output folder: `bin`
- Build system: None (no Maven or Gradle)

## Import into Eclipse

1. Clone this repository.
2. In Eclipse, select **File → Import → General → Existing Projects into Workspace**.
3. Browse to the cloned `ALBECustomerLimit` folder.
4. Select the project and finish.

## TAFJ libraries

Add the required TAFJ and generated Table JARs through **Project → Properties → Java Build Path → Libraries → Add External JARs** (or Add JARs if stored in the project).

The repository does not include proprietary TAFJ libraries. Configure the JARs from your local TAFJ installation.

## Code structure (MVC)

```
T24 hook ──> controller ──> service ──> repository ──> T24 tables
                 │             │
                 │             └── model (CustomerLimitDTO, Limits, LimitBreach)
                 └── view (CustomerLimitOverride: overrides shown to the user)
```

| Layer | Package | Classes | Role |
|---|---|---|---|
| Controller | `controller` | DailyMonthlyLimitController, MaximumCifLimitController, CustomerLimitCreationController, CustomerLimitSummaryEnquiryController, CustomerLimitPeriodEnquiryController, CustomerLimitTxnEnquiryController | T24 entry points: read the activity / record, build the request, call the service, raise overrides through the view. No rules, no DB access. |
| Service | `service` | DailyMonthlyLimitService, MaximumCifLimitService, CustomerLimitCreationService, CustomerLimitReader, CustomerLimitUtilizationUpdater, TxnDetailsLimitRecorder, CurrencyConversionService, CustomerLimitEnquiryService | Business rules (limit checks, utilisation count / reverse, limit creation, report data). |
| Validation | `validation` | CustomerLimitRequestValidator, CustomerLimitEnquiryValidator | Checks the request built from T24 **once**, in the controller (customer, AAA id, date, one positive amount). Services trust a validated request. |
| Utility | `util` | LogUtil | Writes every log line in one standard format (see *Logging standard*). |
| Utility | `util` | ValidationUtil | Common value checks shared by every layer: `isBlank`, `nvl`, `isPositive`, `toAmount` (blank/invalid = 0), `toOptionalAmount` (blank = limit not defined), `toText`, `toDate` (YYYYMMDD), `sameCurrency`. No business rules. |
| Repository | `repository` | CustomerLimitRepository | **All** database reads / writes / selects / deletes, each logged. |
| Model | `model` | CustomerLimitDTO, Limits, LimitBreach, LimitSummaryRow, LimitPeriodRow, LimitTxnRow | Data passed between layers. |
| View | `view` | CustomerLimitOverride, CustomerLimitEnquiryView | Overrides on TXN.AMOUNT; enquiry lines (`*`-separated columns). |
| Constants | `constants` | Constants | All constants: tables, record ids, statuses, activity keywords, field / key names, override ids, messages, date formats. The only `final` class. |

## Routines to register (EB.API)

| Class | Attach to | Hooks |
|---|---|---|
| `com.temenos.ALBE.CustomerLimit.controller.CustomerLimitCreationController` | VERSION CUSTOMER,ALBE.CORP – **Auth routine** | `postUpdateRequest`: when the customer is authorised, creates EB.ALBE.CIF.LIMIT `<customer>` from EB.ALBE.GEN.PARAM CUSTOMER.LIMIT if not present. No delete flow: an unauthorised customer that is deleted never had a limit record. On customer amendment (authorised again) an existing record is **never overwritten**; if the read fails for any reason other than "not found", nothing is written. (HLSD 7.2.1 says After Unau; changed to Auth.) |
| `com.temenos.ALBE.CustomerLimit.controller.DailyMonthlyLimitController` | ACTIVITY.API – ACCOUNTS-CREDIT-ARRANGEMENT, ACCOUNTS-DEBIT-ARRANGEMENT | `validateRecord` (UNAUTH): daily + monthly check, overrides. `postCoreTableUpdate`: UNAUTH counts (re-input replaces the earlier amount), DELETE and AUTH-REV reverse, AUTH no change. |
| `com.temenos.ALBE.CustomerLimit.controller.CustomerLimitSummaryEnquiryController` | STANDARD.SELECTION NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY | `setIds`: limit summary per customer (limits, used today / this month, available). Selection CUSTOMER, DATE, OVER.LIMIT (optional). |
| `com.temenos.ALBE.CustomerLimit.controller.CustomerLimitPeriodEnquiryController` | STANDARD.SELECTION NOFILE.ALBE.CUSTOMER.LIMIT.PERIOD | `setIds`: per customer per day (DATE.FROM–DATE.TO) or per month (MONTH.FROM–MONTH.TO) with usage; CUSTOMER, OVER.LIMIT optional. |
| `com.temenos.ALBE.CustomerLimit.controller.CustomerLimitTxnEnquiryController` | STANDARD.SELECTION NOFILE.ALBE.CUSTOMER.LIMIT.TXN | `setIds`: transactions counted (EB.ALBE.TXN.DETAILS.LIMIT) for one customer, or all customers in a date range. |
| `com.temenos.ALBE.CustomerLimit.controller.MaximumCifLimitController` | ACTIVITY.API – ACCOUNTS-CREDIT-ARRANGEMENT | `validateRecord` (UNAUTH): sum of working balances of all customer accounts + credit amount vs MAX.CIF.BALANCE, override. |

The validators read limits only from EB.ALBE.CIF.LIMIT `<customer>`. If the customer has no record, or a limit field is blank, the acceptable override EB-ALBE.CIF.LIMIT.NOT.DEFINED ("Customer limit not defined for Customer &") is raised once per transaction. EB.ALBE.GEN.PARAM CUSTOMER.LIMIT is used only by the creation service.

Credit/debit is taken from the activity name (`CREDIT` / `DEBIT`). Dates use the server date.

### Currency conversion

All checks and utilisation use the **limit currency** (EB.ALBE.CIF.LIMIT `CCY`):

- transaction currency = AA.ARRANGEMENT `CURRENCY`; if it differs from `CCY` the TXN.AMOUNT is converted;
- for the maximum CIF check every account's working balance is converted from the ACCOUNT currency;
- amounts are rounded to 2 decimals; EB.ALBE.UTILISED.LIMIT and EB.ALBE.TXN.DETAILS.LIMIT hold the converted amount;
- no rate => override EB-ALBE.EXCH.RATE.MISSING ("Exchange rate not available from & to &") and the check is skipped.

**To configure:** set `Constants.EXCH_RATE_TABLE` and `Constants.EXCH_RATE_FIELD`, and complete the read in
`CustomerLimitRepository.readExchangeRate(fromCcy, toCcy)` (returns the rate for 1 unit of fromCcy in toCcy).
Until then only same-currency transactions are checked.

## Local table field names assumed by the code

Create the tables with these field names so the generated getters match.

**EB.ALBE.GEN.PARAM** (EB.TABLE.DEFINITION `ALBE.GEN.PARAM`, confirmed) – generic limits, record `CUSTOMER.LIMIT`, KEY.NAME / KEY.VALUE:

| KEY.NAME | KEY.VALUE |
|---|---|
| CCY | LCY |
| DAILY.CREDIT.LIMIT | daily credit limit |
| DAILY.DEBIT.LIMIT | daily debit limit |
| MONTHLY.CREDIT.LIMIT | monthly credit limit |
| MONTHLY.DEBIT.LIMIT | monthly debit limit |
| MAX.CIF.BALANCE | maximum CIF balance |

**EB.ALBE.CIF.LIMIT** (id: customer id) – create EB.TABLE.DEFINITION `ALBE.CIF.LIMIT` with these field names

| No | Field | Description | Type | Getter / Setter |
|---|---|---|---|---|
| 1 | CCY | Currency | Text | getCcy / setCcy |
| 2 | DAILY.CREDIT.LIMIT | Daily credit limit | Amount | getDailyCreditLimit / setDailyCreditLimit |
| 3 | DAILY.DEBIT.LIMIT | Daily debit limit | Amount | getDailyDebitLimit / setDailyDebitLimit |
| 4 | MONTHLY.CREDIT.LIMIT | Monthly credit limit | Amount | getMonthlyCreditLimit / setMonthlyCreditLimit |
| 5 | MONTHLY.DEBIT.LIMIT | Monthly debit limit | Amount | getMonthlyDebitLimit / setMonthlyDebitLimit |
| 6 | MAX.CIF.BALANCE | Maximum overall CIF balance | Amount | getMaxCifBalance / setMaxCifBalance |

**EB.ALBE.UTILISED.LIMIT** (EB.TABLE.DEFINITION `ALBE.UTILISED.LIMIT`, confirmed)

| Field | Daily record `<customer>-YYYYMMDD` | Monthly record `<customer>-YYYYMM` |
|---|---|---|
| UTILISED.CREDIT.LIMIT | daily credit utilised (updated) | monthly credit utilised (updated) |
| UTILISED.DEBIT.LIMIT | daily debit utilised (updated) | monthly debit utilised (updated) |
| UTILISED.MIGRATED.CREDIT.LIMIT | – | migrated credit (read only) |
| UTILISED.MIGRATED.DEBIT.LIMIT | – | migrated debit (read only) |

Monthly utilisation = UTILISED.MIGRATED.* + UTILISED.* of the monthly record.

**EB.ALBE.TXN.DETAILS.LIMIT** (EB.TABLE.DEFINITION `ALBE.TXN.DETAILS.LIMIT`) – **add field SYSTEM.DATE**

Record id: `<customer>-<AAA id>` (AAA id from `arrangementContext.getArrangementActivityId()`), e.g. `100234-AAACT2632400001`.
The id is known at every step, so the record is always read **directly by key** (no SELECT).

| Field | Value |
|---|---|
| ACCOUNT.NO | arrangement's linked account |
| CREDIT.DEBIT.FLAG | CR / DR |
| EFF.DATE | effective (value) date of the transaction |
| TXN.AMOUNT | amount counted, in the limit currency |
| **SYSTEM.DATE** (new, DATE) | server date the amount was counted on; a rejection / reversal subtracts from this day and month |

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
| EB-ALBE.DAILY.LIMIT.EXCEED | Daily credit/debit exceed for Customer & by & |
| EB-ALBE.MONTHLY.LIMIT.EXCEED | Monthly credit/debit exceed for Customer & by & |
| EB-ALBE.MAX.CIF.BAL.EXCEED | Overall credit/debit exceed for Customer & by & |
| EB-ALBE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & |
| EB-ALBE.EXCH.RATE.MISSING | Exchange rate not available from & to & |

`view.CustomerLimitOverride` raises them on TXN.AMOUNT as `<id> FM <customer> VM <difference>`. Verify this parameter format on your TAFJ release before SIT.

## Logging standard

All logging goes through `util/LogUtil`. No class creates its own logger.

```
ALBE.CUSTOMER.LIMIT | <Class>.<method> | <EVENT> | key=value key=value [| Exception: message @ frame]
INFO  ALBE.CUSTOMER.LIMIT | DailyMonthlyLimitService.checkLimit | OK | DAILY.CREDIT.LIMIT projected=3000.00 limit=5000 headroom=2000.00
```

Each class has `private static final String CLASS_NAME = "<ClassName>";` and calls:

| Call | Use for |
|---|---|
| `LogUtil.info(CLASS_NAME, "method", "EVENT", details)` | normal flow |
| `LogUtil.error(CLASS_NAME, "method", "EVENT", details[, e])` | failure or bad data |
| `LogUtil.detail(CLASS_NAME, "method", "READ", details)` | DB trace before the call, written only when `Constants.LOG_DETAIL_ENABLED = true` (set `false` for production) |

**Events** (use only these):

| Group | Events |
|---|---|
| Flow | `START` `END` `STEP` `SKIP` `ACTION` |
| Checks | `OK` `INVALID` `BREACH` `NOT DEFINED` `OVERRIDE` |
| Calculations | `CALC` `UPDATED` `ADJUSTED` |
| Data | `READ` `WRITE` `DELETE` `SELECT` (before the call) / `FOUND` `NOT FOUND` `NEW` `CREATED` `WRITTEN` `DELETED` (result) |
| Problems | `FAILED` (DB call failed) `ERROR` (unexpected, caught in the controller) `NO RATE` `NOT CONFIGURED` |

Useful greps on the TAFJ log: `grep "ALBE.CUSTOMER.LIMIT"` (everything), `grep "ALBE.CUSTOMER.LIMIT.*| BREACH\|OVERRIDE |"`, `grep "ALBE.CUSTOMER.LIMIT.*customer=100234"`.
Logging never throws, so a logging problem cannot stop a transaction.
