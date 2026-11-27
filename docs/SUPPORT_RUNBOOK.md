# ALBE Customer Limit – Support Runbook

For production support and business-user questions after go-live. How the module works: [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md).

---

## 1. What runs where

| When | Routine | What it does | What you see |
|---|---|---|---|
| Customer authorised (CUSTOMER,ALBE.CORP.INPUT) | ALBE.CL.LIMIT.CREATE | creates EB.ALBE.CIF.LIMIT `<customer>` from GEN.PARAM `CUSTOMER.LIMIT` if missing | new limit record |
| Credit / debit activity input | ALBE.CL.ACTIVITY.LIMIT | checks daily / monthly (+ max CIF for a credit), counts the amount | overrides / errors; UTILISED.LIMIT and TXN.DETAILS records |
| Activity deleted (unauthorised) / reversed | ALBE.CL.ACTIVITY.LIMIT | takes back what was counted, from the day and month it was counted on; deletes the TXN.DETAILS record | totals go down |

Records: daily `EB.ALBE.UTILISED.LIMIT <cust>-YYYYMMDD`, monthly `<cust>-YYYYMM`, per transaction `EB.ALBE.TXN.DETAILS.LIMIT <cust>-<YYYYMMDD>-<root ref>-<arrangement>`.

## 2. Daily monitoring

| Check | How | Action |
|---|---|---|
| Technical errors | `grep -E "ALBE.CUSTOMER.LIMIT.*\| (FAILED\|BLOCKED) \|" <tafj log>` | any line → section 4. A technical failure **does not block** the transaction (except a failed save), so this is the only signal that a check was skipped. All lines are INFO (DIAGNOSTIC.API must be at INFO) |
| Missing rates | `grep "ALBE.CUSTOMER.LIMIT.*\| NO RATE \|"` | report the currency pair to treasury / dev (E1 in GAPS.md) |
| Overridden transactions | `grep "ALBE.CUSTOMER.LIMIT.*\| OVERRIDE \|"` (the reports were removed, 26-Nov) | compliance review |
| Customers without limits | log lines `getCustomerLimits \| NOT FOUND` | create the limit record (3.3) |

Every module log line starts with `ALBE.CUSTOMER.LIMIT |`. **One transaction in plain words:** `grep "ALBE.CUSTOMER.LIMIT" api.log | grep <FT or AAA id> | grep -E "\| (ACTION|ACTIVITY|RESULT|OVERRIDE) \|"` – the **ACTION** line says what happened first: INPUT / DELETE / REVERSAL / AUTHORISE / REVERSAL INPUT; – the RESULT lines show used + this = total vs limit, the overrides / errors raised and what was counted (needs DIAGNOSTIC.API at INFO).

## 3. Common cases

### 3.1 "No limit is checked for a customer" (D52: no limit record → skipped silently)

1. Check EB.ALBE.CIF.LIMIT `<customer>` exists → if not, 3.3.
2. If it exists, check none of the five limit fields is blank (a blank field = that check is skipped, no override). Log: `no EB.ALBE.CIF.LIMIT record -> skipped` or `limit blank -> not checked`.
3. The override is acceptable – the user can accept it; the transaction is still counted.

### 3.2 "The customer gets 'Daily/Monthly … exceed' but has not used that much"

1. List EB.ALBE.TXN.DETAILS.LIMIT records `<id>-<date>-...` – every counted transaction with its counted date and amount (the reports were removed, 26-Nov).
2. Compare with EB.ALBE.UTILISED.LIMIT `<id>-<date>` / `<id>-<month>`.
3. Unauthorised transactions **count** (by design); deleted / reversed ones should not be listed.
4. A listed transaction that was deleted or reversed → 3.5.
5. Monthly: the used amount includes UTILISED.MIGRATED.* (loaded at migration).

### 3.3 Create a missing limit record

Open **EB.ALBE.CIF.LIMIT,AMEND** with the customer id, enter CURRENCY and the five limits (normally the GEN.PARAM values), authorise. The creation routine never overwrites an existing record.

### 3.4 Change a customer's limits

EB.ALBE.CIF.LIMIT,AMEND → amend → authorise. Effective for the next transaction. Changing GEN.PARAM `CUSTOMER.LIMIT` affects **new** customers only.

### 3.5 Utilisation looks wrong (too high or too low)

Possible causes: a write failed (G3), parallel inputs (G2) – see [GAPS.md](GAPS.md).

1. Find the log lines for the customer and day: `grep "ALBE.CUSTOMER.LIMIT.*customer=<id>"` (and `record=<id>-<date>`).
2. List what should be counted: EB.ALBE.TXN.DETAILS.LIMIT records `<id>-<date>-...` and check each FT's status (live, unauthorised, deleted, reversed).
3. Correct total = sum of TXN.AMOUNT of the counted, not-reversed transactions of that day (month: of that month + migrated).
4. Correct the record (only with change approval): EB.ALBE.UTILISED.LIMIT `<cust>-<date>` / `<cust>-<month>` → UTILISED.CREDIT.LIMIT / UTILISED.DEBIT.LIMIT. Never change the MIGRATED fields.
5. Remove a TXN.DETAILS record that belongs to a deleted / reversed transaction (so it is not reversed again or listed).
6. Note the case and the root cause for the dev team.

### 3.6 Reversal subtracted from the wrong day

The day comes from the activity's ORG.SYSTEM.DATE (D42), else T24 TODAY. The reversal looks up the TXN.DETAILS record by customer + FT + arrangement, so it takes back from the day it was counted on. If nothing was found (`NOTHING TAKEN BACK` in the RESULT line) nothing is changed – check whether the transaction was ever counted; correct as in 3.5.

### 3.7 "Exchange rate not available from X to Y"

- **Error** (EB-ALBE.EXCH.RATE.MISSING, transaction blocked, D44): the transaction is in another currency and the activity has neither an LCY amount (TXN.AMOUNT.LCY / ORIG.TXN.AMT.LCY) nor TXN.EXCH.RATE, or the customer's limit is not in EGP. Check the customer's CIF.LIMIT CURRENCY and the FT; nothing was counted.
- **Override** (EB-ALBE.EXCH.RATE.MISSING): max CIF only – another account of the customer is in a currency for which EXCHRATE (CURRENCY.MARKET 1) finds no rate (D54) – check the CURRENCY record; the max CIF check was skipped. Report the pair.

### 3.9 The log says INPUT … counted, but the utilisation did not change

Normal when the override was **rejected** or the FT was not committed: the count is written in the same commit as the transaction, so T24 undoes it (the RESULT line says "count kept only if the transaction is committed"). Only a committed FT (override accepted) keeps its count; it is taken back on DELETE / authorised reversal.

### 3.8 "No accounts found in CUSTOMER.ACCOUNT for Customer X"

Error EB-ALBE.CUSTOMER.ACCOUNT.MISSING (transaction blocked): the max CIF check (credit, max CIF limit filled) found no CUSTOMER.ACCOUNT record for the customer. Data problem – check CUSTOMER.ACCOUNT `<customer>` (e.g. after a migration); nothing was counted.

## 4. Error lines and what to do

| Log line contains | Meaning | Action |
|---|---|---|
| FT / activity rejected with `NoClassDefFoundError com.temenos.t24.api.records.ebalbe…` | the table JAR on the server does not match the routine JAR (e.g. old `ebalbeciflimits` after D55) – **every** activity of the product fails | deploy the matching table JAR, restart JBoss ([T24_CONFIGURATION §2](T24_CONFIGURATION.md)) |
| `writeUtilisedLimit \| FAILED` / `add \| FAILED` | a utilisation total was not written – the transaction was blocked (EB-ALBE.LIMIT.UPDATE.FAILED) | check the cause; normally nothing to correct (T24 rolled back) |
| `writeTxnDetails \| FAILED` / `record \| FAILED` / `deleteTxnDetails \| FAILED` | a TXN.DETAILS save failed – the transaction was blocked (EB-ALBE.LIMIT.UPDATE.FAILED) | check the cause; normally nothing to correct (T24 rolled back) |
| `cifLimitExists \| FAILED` | creation skipped because the read failed | 3.3; if it repeats, the "not found" text is unknown → dev adds it to `RECORD_NOT_FOUND_MARKERS` |
| `getFinancialMnemonic \| FAILED` | company not read → maximum CIF could not total the accounts | dev |
| `validateRecord \| ERROR` and `ACTION \| … technical error` | unexpected exception in a routine (type, message and code line in the line) | dev; the transaction went through without the check / update |
| `readExchangeRate \| NOT CONFIGURED` | rate source not configured | E1 |

## 5. FAQ for business users

| Question | Answer |
|---|---|
| Can I continue after a limit override? | Yes – all limit messages are acceptable overrides. The transaction is counted. |
| Does an unauthorised transaction use the limit? | Yes, from input. If it is deleted, it is given back. |
| A transaction from last week was reversed today – which day is given back? | The original day and month (from its EB.ALBE.TXN.DETAILS.LIMIT record). |
| What is `ALBE.LIMIT.DATE=…` in the remarks of older activities? | The counted date written by an earlier version (until 26-Nov-2026). Not used any more – it can stay. |
| I changed the generic limits – why did a customer not change? | Generic limits are copied only when a new customer is authorised. Amend the customer's own record. |
| Which currency are limits in? | The CCY of the customer's limit record (normally local currency); other currencies are converted. |

## 6. Escalation

| Level | Who | When |
|---|---|---|
| 1 | Branch / operations | override questions |
| 2 | Application support | sections 3 and 4, record corrections with approval |
| 3 | Development | repeated FAILED / ERROR lines, wrong behaviour, rate source, code fixes |
