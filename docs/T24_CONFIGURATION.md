# ALBE Customer Limit – T24 Configuration Checklist

Everything that has to be set up **in T24 / TAFJ** for the Customer Limit code to work, in the order to do it.
For how the code works see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md).

> Field and screen names below follow the HLSD and the code. Where a name can differ between T24 releases it is
> marked **(confirm)** – use the same set-up your team uses for other local Java routines.

| Step | What | Done |
|---|---|---|
| 1 | Local tables (EB.TABLE.DEFINITION) | ☐ |
| 2 | Generate the table Java API (JARs) | ☐ |
| 3 | Parameter record EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` | ☐ |
| 4 | EB.OVERRIDE records | ☐ |
| 5 | Build and deploy the routine JAR | ☐ |
| 6 | EB.API records | ☐ |
| 7 | VERSION CUSTOMER,ALBE.CORP (creation) | ☐ |
| 8 | ACTIVITY.API on the AA products (checks + utilisation) | ☐ |
| 9 | Versions for EB.ALBE.CIF.LIMIT and EB.ALBE.GEN.PARAM | ☐ |
| 10 | Logging | ☐ |
| 11 | Data before go-live (existing customers, migrated utilisation) | ☐ |
| 12 | Exchange rate (pending) | ☐ |
| 13 | DEV / SIT test cases | ☐ |
| 14 | Reports – enquiries (summary, period, transactions) | ☐ |
| 15 | User menu and access | ☐ |

---

## 1. Local tables

Create (or check) these four EB.TABLE.DEFINITION records. **Field names must match exactly**: the generated Java getters (`getDailyCreditLimit()` …) come from them.

### 1.0 Table type (which files T24 creates)

| Type | Files | Meaning |
|---|---|---|
| H (history) | live + $NAU + $HIS | maker-checker with full history |
| U (unauthorised) | live + $NAU | maker-checker, no history |
| L (live only) | live | no authorisation, no history, written directly |

The field that holds the type in EB.TABLE.DEFINITION can differ by release **(confirm)**; FILE.CONTROL > SUFFIXES shows the result ($NAU / $HIS).

| Table | Suggested type | Why |
|---|---|---|
| EB.ALBE.CIF.LIMIT | **H** | users amend it through a version; the bank needs authorisation and a history of limit changes |
| EB.ALBE.GEN.PARAM | **H** (or U) | parameter changes should be authorised and traceable |
| EB.ALBE.UTILISED.LIMIT | **L** | written only by the code on every transaction; no authorisation, no history (would grow huge) |
| EB.ALBE.TXN.DETAILS.LIMIT | **L** | internal bookkeeping, one record per transaction, deleted on rejection / reversal |

The code always reads and writes the **live** file. If UTILISED.LIMIT / TXN.DETAILS already exist as H or U it still works, but L avoids users creating $NAU copies on screen and saves space.

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

### 1.4 EB.ALBE.TXN.DETAILS.LIMIT – what was counted per transaction  *(exists – no change)*

ID (HLSD): **`<customer>-<YYYYMMDD>-<AAA id>`**, e.g. `100234-20261120-AAACT2632400001` (written by the routine). YYYYMMDD = T24 date the amount was counted on, kept in the activity REMARKS (see step 8).

| Field | Type | Value |
|---|---|---|
| ACCOUNT.NO | Account | arrangement's account |
| CREDIT.DEBIT.FLAG | text | `CR` / `DR` |
| EFF.DATE | Date | effective date of the transaction |
| TXN.AMOUNT | Amount | amount counted (limit currency) |
The table structure is the approved one; the code writes only these four fields.

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

## 3. Parameter record EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT`

Input and authorise record **`CUSTOMER.LIMIT`** – the default limits copied to each new customer. The id is set in `Constants.GEN_PARAM_ID`.

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
| ALBE.CL.DAILY.MONTHLY | `com.temenos.ALBE.CustomerLimit.controller` | `DailyMonthlyLimitController` | `defaultFieldValues`, `validateRecord`, `postCoreTableUpdate` |
| ALBE.CL.MAX.CIF | `com.temenos.ALBE.CustomerLimit.controller` | `MaximumCifLimitController` | `validateRecord` |
| ALBE.CL.FT.LIMIT | `com.temenos.ALBE.CustomerLimit.controller` | `FundsTransferLimitController` | `validateRecord`, `postUpdateRequest` |
| ALBE.CL.TT.LIMIT | `com.temenos.ALBE.CustomerLimit.controller` | `TellerLimitController` | `validateRecord`, `postUpdateRequest` |
| ALBE.CL.ACCT.ENTRY.CHECK / .UPD | `com.temenos.ALBE.CustomerLimit.controller` | `AccountingEntryLimitController` | `raiseOverrides` / `exportEntries` |
| ALBE.E.CUSTOMER.LIMIT.SUMMARY | `com.temenos.ALBE.CustomerLimit.controller` | `CustomerLimitSummaryEnquiryController` | `setIds` |
| ALBE.E.CUSTOMER.LIMIT.PERIOD | `com.temenos.ALBE.CustomerLimit.controller` | `CustomerLimitPeriodEnquiryController` | `setIds` |
| ALBE.E.CUSTOMER.LIMIT.TXN | `com.temenos.ALBE.CustomerLimit.controller` | `CustomerLimitTxnEnquiryController` | `setIds` |

Typical EB.API fields: SOURCE.TYPE = `JAVA`, JAVA.PACKAGE, JAVA.CLASS, JAVA.METHOD **(confirm)**.
If your release needs one EB.API record per hook method, create three for `DailyMonthlyLimitController`
(e.g. `ALBE.CL.DAILY.MONTHLY.DEF` → `defaultFieldValues`, `ALBE.CL.DAILY.MONTHLY.VAL` → `validateRecord`,
`ALBE.CL.DAILY.MONTHLY.UPD` → `postCoreTableUpdate`).

---

## 7. VERSION CUSTOMER,ALBE.CORP – customer limit creation

| Field | Value |
|---|---|
| **AUTH.ROUTINE** | `ALBE.CL.LIMIT.CREATE` (with `@` prefix if your release needs it) |
| AFTER.UNAU.RTN | **nothing** for this routine (it was After Unau in the HLSD; changed to Auth) |

Result: when a customer is **authorised** through this version, EB.ALBE.CIF.LIMIT `<customer>` is created from GEN.PARAM `CUSTOMER.LIMIT` – only if it does not exist. Amending and re-authorising the customer leaves the limit record unchanged.

> Customers created through **other** CUSTOMER versions will not get a limit record. Attach the same routine to those versions if they also need one.

---

## 8. ACTIVITY.API – checks and utilisation

Attach on the **ACTIVITY.API** property condition of every AA product that needs the validation (HLSD: "products for which user wants to have this validation").

| Activity | Routine | Hook | Must run for |
|---|---|---|---|
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `defaultFieldValues` | input (UNAUTH) – writes `ALBE.LIMIT.DATE=YYYYMMDD` into REMARKS |
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `validateRecord` | input (UNAUTH) |
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `postCoreTableUpdate` | **UNAUTH, DELETE and AUTH-REV** |
| ACCOUNTS-DEBIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `defaultFieldValues` | input (UNAUTH) – writes `ALBE.LIMIT.DATE=YYYYMMDD` into REMARKS |
| ACCOUNTS-DEBIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `validateRecord` | input (UNAUTH) |
| ACCOUNTS-DEBIT-ARRANGEMENT | ALBE.CL.DAILY.MONTHLY | `postCoreTableUpdate` | **UNAUTH, DELETE and AUTH-REV** |
| ACCOUNTS-CREDIT-ARRANGEMENT | ALBE.CL.MAX.CIF | `validateRecord` | input (UNAUTH) |

- Use the same ACTIVITY.API slots (validation / post-update routine fields) your team uses for other `ActivityLifecycle` routines **(confirm)**.
- **REMARKS:** `defaultFieldValues` appends `ALBE.LIMIT.DATE=YYYYMMDD` to the activity REMARKS at first input (user text is kept; a re-input keeps the first date). Users should not delete this tag. Without it a rejection / reversal uses today's date.
- **The routines can be attached to any activity.** An activity is counted when (1) the **action** of its ACTIVITY.CLASS (`<PRODUCT.LINE>-<ACTION>-<PROPERTY.CLASS>`, filled by the system) is `CREDIT` (credit) or `DEBIT` (debit) – any product line, user-defined activities included; (2) it has an amount – ORIG.TXN.AMT if filled, else TXN.AMOUNT; (3) its INITIATION.TYPE is in `Constants.COUNTED_INITIATION_TYPES` (all types today). Anything else is skipped – attaching the routines there does nothing. More actions can be added in `Constants.CREDIT_ACTIONS` / `DEBIT_ACTIONS`.
- **Date:** the T24 date – REMARKS tag, else the activity's ORG.SYSTEM.DATE, else T24 TODAY (server clock only as a logged last resort).
- **COB (check):** AA.DECISION.PARAMETER UNAUTH.PROCESS. With `IGNORE.TRANSACTION.ACTIVITIES` unauthorised credits/debits stay over COB. With `DEFAULT` COB deletes them (our DELETE reverses) and re-initiates them after COB (counted again; the REMARKS tag, if kept, keeps the original day).
- The maximum CIF check is for **credit** activities only.
- ⚠ If `postCoreTableUpdate` does not fire on DELETE (rejected input) or AUTH-REV (reversal), the utilisation is **not** reversed. Test both (step 13).
- Rebuild / re-authorise the product (AA.PRODUCT.DESIGNER proof and publish) as usual so the arrangements pick up the new condition.

---

## 8a. FUNDS.TRANSFER and TELLER versions – checks and utilisation

> Optional attach point (22-Nov-2026). Limit management works **wherever the bank attaches a routine** – see 8c for how to choose. **TELLER versions: parked** – do not attach `ALBE.CL.TT.LIMIT` yet; teller transactions are covered by 8b (or 8 on AA accounts).

The bank chooses the FT / TT versions. On each:

| VERSION field | Routine | Hook | What it does |
|---|---|---|---|
| INPUT.ROUTINE | ALBE.CL.FT.LIMIT / ALBE.CL.TT.LIMIT | `validateRecord` | daily / monthly check (debit and credit side) + maximum CIF check (credit side); acceptable overrides on the amount field |
| AFTER.UNAU.RTN | same | `postUpdateRequest` | input → **count**; delete → **take back** |
| AUTH.ROUTINE | same | `postUpdateRequest` | authorising a reversal (RECORD.STATUS RNAU / REVE) → **take back** from the day it was counted; normal authorisation → nothing |

- **Both sides:** the debit side (FT DEBIT.ACCT.NO, TT side marked DEBIT) and the credit side are read; each **customer account** side is checked and counted for **its own customer** (FT DEBIT.CUSTOMER / CREDIT.CUSTOMER or TT CUSTOMER.1/2, else ACCOUNT CUSTOMER). Internal and cash accounts are skipped.
- **Counted id:** EB.ALBE.TXN.DETAILS.LIMIT `<customer>-<YYYYMMDD>-<FT/TT id>.<CR|DR>` (same table, no structure change); the side keeps an own-account transfer's debit and credit apart.
- **Date:** FT PROCESSING.DATE, else the T24 date; TT the T24 date. A delete or reversal finds the counted day by the id (one SELECT on EB.ALBE.TXN.DETAILS.LIMIT, only then).
- **Amount:** FT DEBIT.AMOUNT / CREDIT.AMOUNT (else AMOUNT.DEBITED / AMOUNT.CREDITED); TT AMOUNT.FCY (else AMOUNT.LOCAL). Local-currency amount LOC.AMT.DEBITED / CREDITED, AMOUNT.LOCAL.1 / 2 – used when the limit is in local currency.
- **No double counting:** every routine counts a transaction under the same key `<FT/TT/AAA root reference>.<CR|DR>`, so an FT on an AA account seen by both the FT routine and the activity routine is counted **once** (the second replaces the first). The **overrides**, however, would be shown twice – attach the *check* in one place (8c).
- ⚠ **To test in DEV:** does AFTER.UNAU.RTN fire on delete (function D)? Does AUTH.ROUTINE fire when a reversal is authorised, and which RECORD.STATUS does the routine see (RNAU / REVE)? If not, use the slots your team uses for the same purpose and adjust `Constants.REVERSAL_STATUSES`.

---

## 8b. ACCOUNT.PARAMETER – limit management for every transaction

One attach point that sees **every transaction that posts to an account** (AA, FT, TELLER, standing orders, payments, …), with no dependency on the application. The routine reads the accounting entry (STMT.ENTRY) and gives transaction info + customer limit info to `CustomerLimitEngine`.

| ACCOUNT.PARAMETER field | EB.API (suggested) | Java class / method | What it does |
|---|---|---|---|
| ACCT.NAU.SUBRTN | ALBE.CL.ACCT.ENTRY.CHECK | `AccountingEntryLimitController.raiseOverrides` | input of any transaction: daily / monthly + maximum CIF check; returns the acceptable overrides |
| ACCOUNTING.SUBRTN | ALBE.CL.ACCT.ENTRY.UPD | `AccountingEntryLimitController.exportEntries` | accounting of any transaction: action VAL, VALAUT, ADD, ADD.AUT, CHG → **count**; DEL, REV, RSAO → **take back**; others nothing |

- **Customer entries only:** entries without CUSTOMER.ID (internal / GL accounts) and entry types other than STMT are skipped at once (keeps every posting fast).
- **Direction:** the sign of the entry on the customer's account (+ credit, − debit); a reversing entry is matched to the original side.
- **Amount:** AMOUNT.FCY (foreign-currency account), else AMOUNT.LCY; AMOUNT.LCY used when the limit is in local currency.
- **Key:** TRANS.REFERENCE root (`FT…\BNK` → `FT…`) + `.CR` / `.DR` – same key as the activity and FT/TT routines.
- **Date:** BOOKING.DATE, else the T24 date.
- **Skip lists (bank decides):** `Constants.STMT_SKIP_SYSTEM_IDS` (e.g. COB interest) and `STMT_SKIP_TRANSACTION_CODES` (e.g. charge codes); empty = everything counted.
- ⚠ ACCOUNT.PARAMETER is **one bank-wide record** – if ACCT.NAU.SUBRTN / ACCOUNTING.SUBRTN already hold a routine, combine them first.
- ⚠ **To test in DEV:** are the hooks called for unauthorised input; which action types arrive on delete / reversal / amendment; how the returned overrides show on the screen; several entries of one transaction on the same account (amount + charges – the last one wins today, see GAPS S2).

## 8c. Where to attach – choose per transaction path

| Attach point | Sees | Use when |
|---|---|---|
| ACCOUNT.PARAMETER (8b) | every posting to a customer account | one place for everything (recommended once DEV confirms the hooks) |
| ACTIVITY.API (8) | AA activities (also FT/TT on AA accounts) | AA accounts only |
| FT versions (8a) | the chosen FT versions | only some FT versions, or classic accounts (TELLER parked) |

All three use the same engine and the same counted key, so attaching more than one never **counts** a transaction twice; only the **overrides** would appear twice – so attach the checks for one transaction path in one place.

---

## 9. Versions for EB.ALBE.CIF.LIMIT and EB.ALBE.GEN.PARAM

HLSD: "Version for amending EB.ALBE.CIF.LIMIT to be added to user menu." The menu itself is step 15.

| VERSION | Purpose | Fields shown | Authorisers | Notes |
|---|---|---|---|---|
| `EB.ALBE.CIF.LIMIT,AMEND` | amend one customer's limits | CCY, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, MAX.CIF.BALANCE | ≥ 1 | ID = customer id; record normally exists (created at customer authorisation) |
| `EB.ALBE.CIF.LIMIT,VIEW` *(optional)* | look at a customer's limits | same, all no-input | – | for front-office users who must not amend |
| `EB.ALBE.GEN.PARAM,LIMIT` | maintain the generic limits record `CUSTOMER.LIMIT` | KEY.NAME, KEY.VALUE | ≥ 1 | restrict to parameter administrators; changes affect **new** customers only |

- An amended EB.ALBE.CIF.LIMIT record is never overwritten by the creation routine.
- Version names are suggestions – use the team's naming standard.

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
| **Existing customers** | The creation routine only runs when a customer is authorised. Existing customers need EB.ALBE.CIF.LIMIT records from a **one-off load** (separate flow – e.g. copy GEN.PARAM `CUSTOMER.LIMIT` for every customer, or the bank's own limits). Without a record every transaction gets the "limit not defined" override. |
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
| 3 | Credit 3000 (input) | no override; daily + monthly UTILISED.CREDIT.LIMIT = 3000; TXN.DETAILS `<cust>-<today>-<AAA>`; activity REMARKS ends with `ALBE.LIMIT.DATE=<today>` | `checkLimit \| OK`, `addToRecord \| UPDATED` |
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

---

## 14. Reports – enquiries

Three NOFILE enquiries. What each report shows, the columns and examples with sample data: **[REPORTS.md](REPORTS.md)**. This step is the T24 set-up only.

| Report | ENQUIRY | STANDARD.SELECTION | EB.API (step 6) |
|---|---|---|---|
| Limit summary (one day) | `ALBE.CUSTOMER.LIMIT.SUMMARY` | `NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY` | `ALBE.E.CUSTOMER.LIMIT.SUMMARY` |
| Period report (per day / month) | `ALBE.CUSTOMER.LIMIT.PERIOD` | `NOFILE.ALBE.CUSTOMER.LIMIT.PERIOD` | `ALBE.E.CUSTOMER.LIMIT.PERIOD` |
| Transaction report (incl. overridden) | `ALBE.CUSTOMER.LIMIT.TXN` | `NOFILE.ALBE.CUSTOMER.LIMIT.TXN` | `ALBE.E.CUSTOMER.LIMIT.TXN` |

Build them the same way as the team's existing NOFILE enquiries (same STANDARD.SELECTION and ENQUIRY pattern) **(confirm)**.

### 14.1 STANDARD.SELECTION

One NOFILE STANDARD.SELECTION per report: the routine field pointing to the EB.API id, plus the selection fields below. Names must be exactly as shown (`Constants.ENQ_SEL_*`). Allow operand **EQ** only.

**NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY**

| Selection field | Format | Mandatory | Meaning |
|---|---|---|---|
| CUSTOMER | customer id | no | blank = every customer with an EB.ALBE.CIF.LIMIT record |
| DATE | YYYYMMDD | no | blank = today |
| OVER.LIMIT | Y | no | Y = only customers over a limit |

**NOFILE.ALBE.CUSTOMER.LIMIT.PERIOD**

| Selection field | Format | Mandatory | Meaning |
|---|---|---|---|
| DATE.FROM | YYYYMMDD | date **or** month range | daily rows |
| DATE.TO | YYYYMMDD | | blank = DATE.FROM |
| MONTH.FROM | YYYYMM | | monthly rows |
| MONTH.TO | YYYYMM | | blank = MONTH.FROM |
| CUSTOMER | customer id | no | blank = all customers |
| OVER.LIMIT | Y | no | Y = only rows over a limit |

**NOFILE.ALBE.CUSTOMER.LIMIT.TXN**

| Selection field | Format | Mandatory | Meaning |
|---|---|---|---|
| CUSTOMER | customer id | customer **or** a date | blank = all customers (date range then max 92 days) |
| DATE.FROM | YYYYMMDD | | counted date from |
| DATE.TO | YYYYMMDD | | counted date to (blank, no customer = today) |
| OVERRIDDEN | Y | no | Y = only transactions accepted with a daily / monthly / max CIF override |

The routines check the selection themselves (step 14.3 errors), so no mandatory flags are needed in STANDARD.SELECTION; set them if the bank wants the screen to enforce them too.

### 14.2 ENQUIRY records

Each routine returns one line per row, columns separated by `*`. In the ENQUIRY record, define one display field per column and take its value by column position (field extraction on `*`, as in the existing NOFILE enquiries) **(confirm)**.

**ALBE.CUSTOMER.LIMIT.SUMMARY** – FILE.NAME `NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY`

| Col | Heading | Format |
|---|---|---|
| 1 | Customer | text |
| 2 | Ccy | text |
| 3 | Report date | date |
| 4 | Daily CR limit | amount |
| 5 | Daily CR used | amount |
| 6 | Daily CR available | amount (may be negative) |
| 7 | Daily DR limit | amount |
| 8 | Daily DR used | amount |
| 9 | Daily DR available | amount (may be negative) |
| 10 | Monthly CR limit | amount |
| 11 | Monthly CR used | amount |
| 12 | Monthly CR available | amount (may be negative) |
| 13 | Monthly DR limit | amount |
| 14 | Monthly DR used | amount |
| 15 | Monthly DR available | amount (may be negative) |
| 16 | Max CIF balance | amount |
| 17 | Over limit | Y / N |

**ALBE.CUSTOMER.LIMIT.PERIOD** – FILE.NAME `NOFILE.ALBE.CUSTOMER.LIMIT.PERIOD`

| Col | Heading | Format |
|---|---|---|
| 1 | Type | DAILY / MONTHLY |
| 2 | Period | YYYYMMDD / YYYYMM |
| 3 | Customer | text |
| 4 | Ccy | text |
| 5 | CR limit | amount |
| 6 | CR used | amount |
| 7 | CR available | amount (may be negative) |
| 8 | DR limit | amount |
| 9 | DR used | amount |
| 10 | DR available | amount (may be negative) |
| 11 | Over limit | Y / N |

**ALBE.CUSTOMER.LIMIT.TXN** – FILE.NAME `NOFILE.ALBE.CUSTOMER.LIMIT.TXN`

| Col | Heading | Format |
|---|---|---|
| 1 | Customer | text |
| 2 | Activity (AAA) | text – can drill down to AA.ARRANGEMENT.ACTIVITY |
| 3 | Account | text – can drill down to ACCOUNT |
| 4 | CR / DR | text |
| 5 | Amount | amount (limit currency) |
| 6 | Effective date | date |
| 7 | Counted date | date |
| 8 | Record id | text |
| 9 | Overrides | text (EB-ALBE.* ids) |

Suggested: header shows the selection used; amounts right-aligned with 2 decimals; blank amount = limit not defined.

### 14.3 EB.ERROR records

| ID | Message (suggested) | Raised when |
|---|---|---|
| EB-NO.REC.SELECT | No records selected | nothing to show (normally already exists) |
| EB-ALBE.ENQ.INVALID.DATE | Invalid date, enter YYYYMMDD | not a real date, e.g. 20261131 |
| EB-ALBE.ENQ.INVALID.MONTH | Invalid month, enter YYYYMM | not a real month, e.g. 202613 |
| EB-ALBE.ENQ.DATE.RANGE | From cannot be after to | FROM after TO |
| EB-ALBE.ENQ.PERIOD.MANDATORY | Enter a date range or a month range | period report: neither or both |
| EB-ALBE.ENQ.RANGE.TOO.LONG | Period too long (max 92 days / 24 months) | range above the limit |
| EB-ALBE.ENQ.CUSTOMER.OR.DATE | Enter a customer or a date | transaction report without both |

### 14.4 Checks after set-up

| # | Run | Expected |
|---|---|---|
| 1 | `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY CUSTOMER EQ <test customer>` | one line; columns in the right place |
| 2 | `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY OVER.LIMIT EQ Y` | only customers over a limit |
| 3 | `ENQ ALBE.CUSTOMER.LIMIT.PERIOD DATE.FROM EQ <d1> DATE.TO EQ <d2>` | one line per customer per day with usage |
| 4 | `ENQ ALBE.CUSTOMER.LIMIT.PERIOD MONTH.FROM EQ <m1> MONTH.TO EQ <m2>` | one line per customer per month |
| 5 | `ENQ ALBE.CUSTOMER.LIMIT.TXN CUSTOMER EQ <test customer>` | counted transactions; rejected ones not shown |
| 6 | `ENQ ALBE.CUSTOMER.LIMIT.TXN DATE.FROM EQ <d1> DATE.TO EQ <d2> OVERRIDDEN EQ Y` | only overridden transactions, Overrides column filled |
| 7 | Each error of 14.3 once | the EB.ERROR message, no Java error in the log |

The full test list with sample data is in [REPORTS.md](REPORTS.md#tests-dev--sit).

---

## 15. User menu and access

### 15.1 Menu entries

Add a **Customer Limits** sub-menu to the relevant user menus (HELPTEXT.MENU / Browser menu, as used for the bank's other menus) **(confirm)**:

```
Customer Limits
 ├─ Maintain
 │   ├─ Amend customer limits          EB.ALBE.CIF.LIMIT,AMEND   I
 │   ├─ View customer limits           EB.ALBE.CIF.LIMIT,VIEW    S      (optional)
 │   └─ Generic limits (parameter)     EB.ALBE.GEN.PARAM,LIMIT   I      (administrators only)
 └─ Reports
     ├─ Limit summary                  ENQ ALBE.CUSTOMER.LIMIT.SUMMARY
     ├─ Limit usage by period          ENQ ALBE.CUSTOMER.LIMIT.PERIOD
     ├─ Counted transactions           ENQ ALBE.CUSTOMER.LIMIT.TXN
     └─ Overridden transactions        ENQ ALBE.CUSTOMER.LIMIT.TXN    (OVERRIDDEN preset to Y, optional)
```

| Menu item | Type | Application / enquiry | Function |
|---|---|---|---|
| Amend customer limits | version | `EB.ALBE.CIF.LIMIT,AMEND` | input (I) |
| View customer limits | version | `EB.ALBE.CIF.LIMIT,VIEW` | see (S) |
| Generic limits | version | `EB.ALBE.GEN.PARAM,LIMIT` | input (I), record `CUSTOMER.LIMIT` |
| Limit summary | enquiry | `ALBE.CUSTOMER.LIMIT.SUMMARY` | selection screen |
| Limit usage by period | enquiry | `ALBE.CUSTOMER.LIMIT.PERIOD` | selection screen |
| Counted transactions | enquiry | `ALBE.CUSTOMER.LIMIT.TXN` | selection screen |
| Overridden transactions | enquiry | `ALBE.CUSTOMER.LIMIT.TXN` with OVERRIDDEN = Y | selection screen (optional shortcut) |

### 15.2 Who gets what (suggested)

| Role | Maintain | Reports |
|---|---|---|
| Branch / front office | View customer limits | Limit summary, Counted transactions |
| Limit / risk officer | Amend customer limits | all reports |
| Authoriser | authorise EB.ALBE.CIF.LIMIT changes | all reports |
| Parameter administrator | Generic limits | – |
| Compliance / audit | View | all reports (incl. Overridden transactions) |

Give access through the bank's usual user / role set-up (USER, SMS groups) so only these roles can open the versions and enquiries **(confirm)**. The bank decides the final role list.

### 15.3 Checks

| # | Check | Expected |
|---|---|---|
| 1 | Log in as each role | sees only its menu items |
| 2 | Amend a limit via the menu, authorise it | new limit used by the next transaction (summary report shows it) |
| 3 | Front-office user opens EB.ALBE.CIF.LIMIT,AMEND directly | refused by security |
| 4 | Open each report from the menu | selection screen opens; report runs |
