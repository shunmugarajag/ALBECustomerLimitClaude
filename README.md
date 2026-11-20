# ALBECustomerLimit

Eclipse **Java Project** for the ALBE Customer Limit implementation.

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
| Controller | `controller` | DailyMonthlyLimitController, MaximumCifLimitController, CustomerLimitCreationController | T24 entry points: read the activity / record, build the request, call the service, raise overrides through the view. No rules, no DB access. |
| Service | `service` | DailyMonthlyLimitService, MaximumCifLimitService, CustomerLimitCreationService, CustomerLimitReader, CustomerLimitUtilizationUpdater, TxnDetailsLimitRecorder | Business rules (limit checks, utilisation count / reverse, limit creation). |
| Repository | `repository` | CustomerLimitRepository | **All** database reads / writes / selects / deletes, each logged. |
| Model | `model` | CustomerLimitDTO, Limits, LimitBreach | Data passed between layers. |
| View | `view` | CustomerLimitOverride | Builds and raises the overrides on TXN.AMOUNT. |

## Routines to register (EB.API)

| Class | Attach to | Hooks |
|---|---|---|
| `com.temenos.ALBE.CustomerLimit.controller.CustomerLimitCreationController` | VERSION CUSTOMER,ALBE.CORP – After Unau | `postUpdateRequest`: creates EB.ALBE.CIF.LIMIT `<customer>` from EB.ALBE.GEN.PARAM SYSTEM if not present; deletes it when the unauthorised customer is deleted. |
| `com.temenos.ALBE.CustomerLimit.controller.DailyMonthlyLimitController` | ACTIVITY.API – ACCOUNTS-CREDIT-ARRANGEMENT, ACCOUNTS-DEBIT-ARRANGEMENT | `validateRecord` (UNAUTH): daily + monthly check, overrides. `postCoreTableUpdate`: UNAUTH counts (re-input replaces the earlier amount), DELETE and AUTH-REV reverse, AUTH no change. |
| `com.temenos.ALBE.CustomerLimit.controller.MaximumCifLimitController` | ACTIVITY.API – ACCOUNTS-CREDIT-ARRANGEMENT | `validateRecord` (UNAUTH): sum of working balances of all customer accounts + credit amount vs MAX.CIF.BALANCE, override. |

The validators read limits only from EB.ALBE.CIF.LIMIT `<customer>`. If the customer has no record, or a limit field is blank, the acceptable override EB-ALBE.CIF.LIMIT.NOT.DEFINED ("Customer limit not defined for Customer &") is raised once per transaction. EB.ALBE.GEN.PARAM SYSTEM is used only by the creation service.

Credit/debit is taken from the activity name (`CREDIT` / `DEBIT`). Dates use the server date; exchange rate is 1 (LCY).

## Local table field names assumed by the code

Create the tables with these field names so the generated getters match.

**EB.ALBE.GEN.PARAM** (EB.TABLE.DEFINITION `ALBE.GEN.PARAM`, confirmed) – generic limits, record `SYSTEM`, KEY.NAME / KEY.VALUE:

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

**EB.ALBE.TXN.DETAILS.LIMIT** (EB.TABLE.DEFINITION `ALBE.TXN.DETAILS.LIMIT`, confirmed)

Record id: `<customer>-<YYYYMMDD server date>-<AAA id>` (AAA id from `arrangementContext.getArrangementActivityId()`).

| Field | Value |
|---|---|
| ACCOUNT.NO | arrangement's linked account |
| CREDIT.DEBIT.FLAG | CR / DR |
| EFF.DATE | effective (value) date of the transaction |
| TXN.AMOUNT | transaction amount |

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

`override.CustomerLimitOverride` raises them on TXN.AMOUNT as `<id> FM <customer> VM <difference>`. Verify this parameter format on your TAFJ release before SIT.
