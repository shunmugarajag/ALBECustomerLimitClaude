# ALBE Customer Limit – T24 Configuration Checklist

Everything that has to be set up **in T24 / TAFJ** for the Customer Limit code to work, in the order to do it.
For how the code works see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md).

> Field and screen names below follow the HLSD and the code. Where a name can differ between T24 releases it is
> marked **(confirm)** – use the same set-up your team uses for other local Java routines.

| Step | What | Done |
|---|---|---|
| 1 | Local tables (EB.TABLE.DEFINITION) | ☐ |
| 2 | Generate the table Java API (JARs) | ☐ |
| 3 | Parameter record EB.ALBE.GEN.PARAM `SYSTEM` | ☐ |
| 4 | EB.OVERRIDE records | ☐ |
| 5 | Build and deploy the routine JAR | ☐ |
| 6 | EB.API records | ☐ |
| 7 | VERSION CUSTOMER,ALBE.CORP (creation) | ☐ |
| 8 | ACTIVITY.API on the AA products (checks + utilisation) | ☐ |
| 9 | Version for amending EB.ALBE.CIF.LIMIT + user menu | ☐ |
| 10 | Logging | ☐ |
| 11 | Data before go-live (existing customers, migrated utilisation) | ☐ |
| 12 | Exchange rate (pending) | ☐ |
| 13 | DEV / SIT test cases | ☐ |

---

## 1. Local tables

Create (or check) these four EB.TABLE.DEFINITION records. **Field names must match exactly**: the generated Java getters (`getDailyCreditLimit()` …) come from them.

### 1.1 EB.ALBE.GEN.PARAM – generic parameters  *(exists)*

| Field | Type | Note |
|---|---|---|
| KEY.NAME | multi-value, text | paired with KEY.VALUE |
| KEY.VALUE | multi-value, text | |

### 1.2 EB.ALBE.CIF.LIMIT – one record per customer  *(create)*

ID: **customer id** (validate as CUSTOMER).

| No | Field | Description | Type |
|---|---|---|---|
| 1 | CCY | Currency of the limits | Currency |
| 2 | DAILY.CREDIT.LIMIT | Daily credit limit | Amount |
| 3 | DAILY.DEBIT.LIMIT | Daily debit limit | Amount |
| 4 | MONTHLY.CREDIT.LIMIT | Monthly credit limit | Amount |
| 5 | MONTHLY.DEBIT.LIMIT | Monthly debit limit | Amount |
| 6 | MAX.CIF.BALANCE | Maximum overall CIF balance | Amount |

A blank limit field means "not defined": the transaction gets the EB-ALBE.CIF.LIMIT.NOT.DEFINED override.

### 1.3 EB.ALBE.UTILISED.LIMIT – daily and monthly totals  *(exists)*

ID: daily `<customer>-YYYYMMDD`, monthly `<customer>-YYYYMM` (written by the routine).

| Field | Type | Written by routine? |
|---|---|---|
| UTILISED.CREDIT.LIMIT | Amount | yes |
| UTILISED.DEBIT.LIMIT | Amount | yes |
| UTILISED.MIGRATED.CREDIT.LIMIT | Amount | **no** – migration only (monthly record) |
| UTILISED.MIGRATED.DEBIT.LIMIT | Amount | **no** – migration only (monthly record) |

### 1.4 EB.ALBE.TXN.DETAILS.LIMIT – what was counted per transaction  *(exists – add one field)*

ID: **`<customer>-<AAA id>`**, e.g. `100234-AAACT2632400001` (written by the routine). The ID must allow this format (length ≥ customer id + 1 + AAA id).

| Field | Type | Value |
|---|---|---|
| ACCOUNT.NO | Account | arrangement's account |
| CREDIT.DEBIT.FLAG | text | `CR` / `DR` |
| EFF.DATE | Date | effective date of the transaction |
| TXN.AMOUNT | Amount | amount counted (limit currency) |
| **SYSTEM.DATE** | **Date** | **NEW** – server date the amount was counted on |

> ⚠ **SYSTEM.DATE must be added** and the JAR regenerated (step 2), otherwise the code does not compile (`getSystemDate()` / `setSystemDate()`).

---

## 2. Generate the table Java API (JARs)

After authorising the tables, generate the Java API for the four local tables with your release's generation step (the same one used for other local tables, e.g. Design Studio / TAFJ API generation) **(confirm)**.

The code needs these classes:

| Table | Record class | Table class |
|---|---|---|
| EB.ALBE.CIF.LIMIT | `com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord` | `com.temenos.t24.api.tables.ebalbeciflimit.EbAlbeCifLimitTable` |
| EB.ALBE.GEN.PARAM | `com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord`, `KeyNameClass` | – |
| EB.ALBE.UTILISED.LIMIT | `com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord` | `com.temenos.t24.api.tables.ebalbeutilisedlimit.EbAlbeUtilisedLimitTable` |
| EB.ALBE.TXN.DETAILS.LIMIT | `com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord` | `com.temenos.t24.api.tables.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitTable` |

Then:

1. Add the generated JARs to the **Eclipse build path** (Project → Properties → Java Build Path → Libraries), next to the TAFJ / T24 API JARs.
2. Check the Eclipse **Problems** view (Window → Show View → Problems, filter "All errors on workspace"): it should show **0 errors**.
3. Make sure the same JARs are on the **TAFJ runtime classpath**.

Core API JARs also needed (from the TAFJ installation): `com.temenos.api` (TField, TStructure, TValidationResponse), `com.temenos.t24.api` (hooks, DataAccess, Session, AA / ACCOUNT / COMPANY records), `com.temenos.logging.facade`.

---

## 3. Parameter record EB.ALBE.GEN.PARAM `SYSTEM`

Input and authorise record **`SYSTEM`** – the default limits copied to each new customer.

| KEY.NAME | KEY.VALUE (example) |
|---|---|
| CCY | EGP *(local currency)* |
| DAILY.CREDIT.LIMIT | 500000 |
| DAILY.DEBIT.LIMIT | 500000 |
| MONTHLY.CREDIT.LIMIT | 5000000 |
| MONTHLY.DEBIT.LIMIT | 5000000 |
| MAX.CIF.BALANCE | 10000000 |

The KEY.NAME values must be spelled exactly as above (`Constants.KEY_*`). Values are examples – the bank provides the real ones.
Changing this record later does **not** change existing customers' EB.ALBE.CIF.LIMIT records.

---

## 4. EB.OVERRIDE records

Create and authorise these five records. All are **acceptable** overrides (the user can accept and continue).
`&` is replaced by the parameters in the order shown.

| ID | MESSAGE | & parameters |
|---|---|---|
| EB-ALBE.DAILY.LIMIT.EXCEED | Daily credit/debit exceed for Customer & by & | customer id, amount over |
| EB-ALBE.MONTHLY.LIMIT.EXCEED | Monthly credit/debit exceed for Customer & by & | customer id, amount over |
| EB-ALBE.MAX.CIF.BAL.EXCEED | Overall credit/debit exceed for Customer & by & | customer id, amount over |
| EB-ALBE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & | customer id |
| EB-ALBE.EXCH.RATE.MISSING | Exchange rate not available from & to & | from currency, to currency |

The routine sends `ID FM param1 VM param2` – check in DEV that both `&` are filled in the override text **(confirm)**.

---

## 5. Build and deploy the routine JAR

1. Pull `main` of `shunmugarajag/ALBECustomerLimitClaude` into Eclipse (Java 21).
2. Before **production**: set `Constants.LOG_DETAIL_ENABLED = false` (DB trace lines off).
3. Export the project as a JAR (File → Export → Java → JAR file; source folder `src`), e.g. `ALBECustomerLimit.jar`.
4. Copy it to the folder TAFJ loads local Java from, as for other L3 Java routines **(confirm)**, and restart / refresh the TAFJ runtime if your environment needs it.

---

## 6. EB.API records

One EB.API record per routine. Suggested IDs (any naming standard of the team can be used):

| EB.API id | Java package | Java class | Hook method |
|---|---|---|---|
| ALBE.CL.LIMIT.CREATE | `com.temenos.ALBE.CustomerLimit.controller` | `CustomerLimitCreationController` | `postUpdateRequest` |
| ALBE.CL.DAILY.MONTHLY | `com.temenos.ALBE.CustomerLimit.controller` | `DailyMonthlyLimitController` | `validateRecord`, `postCoreTableUpdate` |
| ALBE.CL.MAX.CIF | `com.temenos.ALBE.CustomerLimit.controller` | `MaximumCifLimitController` | `validateRecord` |

Typical EB.API fields: SOURCE.TYPE = `JAVA`, JAVA.PACKAGE, JAVA.CLASS, JAVA.METHOD **(confirm)**.
If your release needs one EB.API record per hook method, create two for `DailyMonthlyLimitController`
(e.g. `ALBE.CL.DAILY.MONTHLY.VAL` → `validateRecord`, `ALBE.CL.DAILY.MONTHLY.UPD` → `postCoreTableUpdate`).

---

## 7. VERSION CUSTOMER,ALBE.CORP – customer limit creation

| Field | Value |
|---|---|
| **AUTH.ROUTINE** | `ALBE.CL.LIMIT.CREATE` (with `@` prefix if your release needs it) |
| AFTER.UNAU.RTN | **nothing** for this routine (it was After Unau in the HLSD; changed to Auth) |

Result: when a customer is **authorised** through this version, EB.ALBE.CIF.LIMIT `<customer>` is created from GEN.PARAM `SYSTEM` – only if it does not exist. Amending and re-authorising the customer leaves the limit record unchanged.

> Customers created through **other** CUSTOMER versions will not get a limit record. Attach the same routine to those versions if they also need one.

---

## 8. ACTIVITY.API – checks and utilisation

Attach on the **ACTIVITY.API** property condition of every AA product that needs the validation (HLSD: "products for which user wants to have this validation").

| Activity | Routine | Hook | Must run for |
|---|---|---|---|
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `validateRecord` | input (UNAUTH) |
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `postCoreTableUpdate` | **UNAUTH, DELETE and AUTH-REV** |
| ACCOUNTS-DEBIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `validateRecord` | input (UNAUTH) |
| ACCOUNTS-DEBIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `postCoreTableUpdate` | **UNAUTH, DELETE and AUTH-REV** |
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.MAX.CIF | `validateRecord` | input (UNAUTH) |

- Use the same ACTIVITY.API slots (validation / post-update routine fields) your team uses for other `ActivityLifecycle` routines **(confirm)**.
- Credit or debit is taken from the **activity name**: it must contain `CREDIT` or `DEBIT`. If other activities are added, their names must too.
- The maximum CIF check is for **credit** activities only.
- ⚠ If `postCoreTableUpdate` does not fire on DELETE (rejected input) or AUTH-REV (reversal), the utilisation is **not** reversed. Test both (step 13).
- Rebuild / re-authorise the product (AA.PRODUCT.DESIGNER proof and publish) as usual so the arrangements pick up the new condition.

---

## 9. Version for amending EB.ALBE.CIF.LIMIT + user menu

HLSD: "Version for amending EB.ALBE.CIF.LIMIT to be added to user menu."

1. Create a VERSION on EB.ALBE.CIF.LIMIT (e.g. `EB.ALBE.CIF.LIMIT,AMEND`) showing CCY and the five limit fields, with authorisation (no. of authorisers ≥ 1).
2. Add it to the relevant user menu / role.

An amended record is never overwritten by the creation routine.

---

## 10. Logging

- The code logs through logger **`L3API`** (same as the team's other L3 routines). Make sure the TAFJ logging configuration writes `L3API` at **INFO** in DEV / SIT.
- Every line starts with **`ALBE.CUSTOMER.LIMIT |`**. Monitor in SIT and production:
  ```
  grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR|FAILED) \|" <tafj log>
  ```
  A technical failure does **not** block the transaction, so these lines are the only signal that a check was skipped.
- Production: `LOG_DETAIL_ENABLED = false` (step 5).

---

## 11. Data before go-live

| Item | What to do |
|---|---|
| **Existing customers** | The creation routine only runs when a customer is authorised. Existing customers need EB.ALBE.CIF.LIMIT records from a **one-off load** (separate flow – e.g. copy GEN.PARAM `SYSTEM` for every customer, or the bank's own limits). Without a record every transaction gets the "limit not defined" override. |
| **Migrated monthly utilisation** | If the current month's totals come from the old system, load them into UTILISED.MIGRATED.CREDIT.LIMIT / UTILISED.MIGRATED.DEBIT.LIMIT of `<customer>-YYYYMM`. The routine adds them to the monthly total and never changes them. |
| **Reversal of transactions made before go-live** | They have no EB.ALBE.TXN.DETAILS.LIMIT record; an AUTH-REV then subtracts the amount from **today's** records (floored at 0). No action needed, just be aware. |

---

## 12. Exchange rate (pending)

Needed only when the transaction (or an account) currency differs from the customer's limit currency.

1. Bank to confirm the exchange-rate table, record id and rate field (rate for 1 unit of *from* currency in *to* currency).
2. Developer sets `Constants.EXCH_RATE_TABLE`, `Constants.EXCH_RATE_FIELD` and completes `CustomerLimitRepository.readExchangeRate(fromCcy, toCcy)`.
3. Until then: same-currency transactions are checked normally; other currencies get the **EB-ALBE.EXCH.RATE.MISSING** override and the limit check is skipped.

---

## 13. DEV / SIT test cases

Set up a test customer with an EB.ALBE.CIF.LIMIT record, e.g. daily credit 5000, monthly credit 15000, MAX.CIF.BALANCE 100000, all in the arrangement currency.

| # | Test | Expected result | Log line to look for |
|---|---|---|---|
| 1 | Authorise a **new** customer via CUSTOMER,ALBE.CORP | EB.ALBE.CIF.LIMIT `<customer>` = GEN.PARAM values | `CustomerLimitCreationController.postUpdateRequest \| END \| ... created=true` |
| 2 | Amend the limit, then amend + authorise the customer | limit record unchanged | `CustomerLimitCreationService.create \| SKIP \| ... already exists` |
| 3 | Credit 3000 (input) | no override; daily + monthly UTILISED.CREDIT.LIMIT = 3000; TXN.DETAILS `<cust>-<AAA>` with SYSTEM.DATE | `checkLimit \| OK`, `addToRecord \| UPDATED` |
| 4 | Credit 4000 (input) | override EB-ALBE.DAILY.LIMIT.EXCEED "... by 2000" | `checkLimit \| BREACH` |
| 5 | Accept the override and commit test 4 | utilisation 7000 | `postCoreTableUpdate \| ACTION \| COUNT` |
| 6 | **Reject / delete** the unauthorised transaction of test 4 | utilisation back to 3000; its TXN.DETAILS record deleted | `ACTION \| REVERSE rejected input` |
| 7 | Amend an unauthorised transaction's amount (re-input) | counted once with the new amount, not twice | `validate \| CALC \| earlierInputs=1` |
| 8 | Authorise test 3 | no change to utilisation | `postCoreTableUpdate \| SKIP \| status=AUTH` |
| 9 | **Reverse** the authorised transaction of test 3 (next day if possible) | 3000 subtracted from the **original** day's and month's records | `ACTION \| REVERSE reversal` |
| 10 | Debit over the daily debit limit | EB-ALBE.DAILY.LIMIT.EXCEED | `checkLimit \| BREACH \| DAILY.DEBIT.LIMIT` |
| 11 | Monthly breach (with a migrated amount loaded) | EB-ALBE.MONTHLY.LIMIT.EXCEED; MIGRATED field unchanged | `validateMonthlyLimit \| CALC \| ... migrated=` |
| 12 | Credit that takes total balance over MAX.CIF.BALANCE | EB-ALBE.MAX.CIF.BAL.EXCEED "... by <difference>" | `MaximumCifLimitService.validate \| BREACH` |
| 13 | Customer **without** EB.ALBE.CIF.LIMIT | EB-ALBE.CIF.LIMIT.NOT.DEFINED | `getCustomerLimits \| NOT FOUND` |
| 14 | Blank DAILY.CREDIT.LIMIT | EB-ALBE.CIF.LIMIT.NOT.DEFINED (once) | `checkLimit \| NOT DEFINED` |
| 15 | Transaction in another currency (before step 12) | EB-ALBE.EXCH.RATE.MISSING "from USD to EGP" | `convertRequest \| NO RATE` |
| 16 | Any test | no `ERROR` / `FAILED` lines | `grep "\| ERROR \|\|\| FAILED \|"` |

Also note in the DEV log (once):

- the AAA id format (test 3) – the TXN.DETAILS id must fit;
- the override text shows both `&` values (test 4);
- the T24 "record not found" text for a missing EB.ALBE.CIF.LIMIT (test 1). If you see `cifLimitExists | FAILED` instead of `NOT FOUND`, add the word from that log line to `Constants.RECORD_NOT_FOUND_MARKERS`.
