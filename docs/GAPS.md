# ALBE Customer Limit – Known Gaps and Open Points

Review of the current code (21-Nov-2026). Every fix proposed here works **without changing the pre-approved table structure**.
For how the code works see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md); for the T24 set-up see [T24_CONFIGURATION.md](T24_CONFIGURATION.md).

| # | Gap | Risk | Fix needs | Status |
|---|---|---|---|---|
| G1 | Counted date follows the JVM clock, not the T24 date | **High – seen in DEV** | code only | open |
| G2 | Parallel inputs for the same customer can lose an update | High | lock API (confirm) | open – team decision |
| G3 | Partial update when one of the three writes fails | High | behaviour decision | open – team decision |
| G4 | REMARKS date tag can be removed on re-input | Medium | code only | open |
| G5 | REMARKS can exceed its maximum length | Medium | field length (confirm) | open |
| G6 | Blank CCY on EB.ALBE.CIF.LIMIT skips conversion | Medium | code only | open |
| G7 | Accounts in other companies count as 0 (maximum CIF) | Low | multi-company set-up (confirm) | open |
| G8 | Slow fallback SELECT on ACCOUNT | Low | confirm CUSTOMER.ACCOUNT | open |
| G9 | Credit / debit decided by the activity name | Low | code only | **fixed 21-Nov-2026** (ACTIVITY.CLASS) |
| E1 | Exchange rate lookup not connected | High (for other currencies) | bank answers (section E1) | **pending bank** |

Already fine (checked): no shared state between calls (no static data, session per call); reversals use the stored amount (no new conversion); totals never below 0; validation and counting use the same REMARKS date even across midnight; enquiries never write.

---

## G1 – Counted date follows the JVM time zone

| | |
|---|---|
| **Where** | `DailyMonthlyLimitController` (`defaultFieldValues`, `buildRequest`), enquiry controllers – `LocalDate.now()` |
| **Problem** | `LocalDate.now()` uses the **JVM default time zone**. TAFJ servers often run in UTC while the bank is in Egypt (UTC+2). |
| **Example** | Credit input at 01:30 Egypt time on 21-Nov = 23:30 UTC on 20-Nov → counted on **20-Nov**, so it uses (and can breach) the wrong day's daily limit. |
| **Impact** | Wrong daily bucket for about 2 hours every night (month buckets wrong at month end). |
| **Fix** | One method `CustomerLimitRepository.getToday()` used everywhere instead of `LocalDate.now()`. Either T24 `TODAY` from `Session` (business date – moves at COB) **or** the server date in a fixed bank time zone (`Constants.BANK_ZONE = "Africa/Cairo"`). |
| **Decision** | Business date (TODAY) or calendar date in Cairo time? (Agreed so far: "server date" → calendar date in Cairo time.) |
| **Seen in DEV (21-Nov-2026)** | AAA `AAACT260914FPVCZWL`: ORG.SYSTEM.DATE **01 APR 2026** (T24 date) but DATE.TIME **29 OCT 26** (server clock). DEV runs on a T24 date months away from the clock, so `LocalDate.now()` gives day/month buckets that do not match T24. Strong case for the T24 date – the AAA field **ORG.SYSTEM.DATE** ("initial T24 system date while processing the activity", system maintained) could be used directly; see A1 below. |

## G2 – Parallel inputs for the same customer can lose an update

| | |
|---|---|
| **Where** | `CustomerLimitUtilizationUpdater.addToRecord` → `CustomerLimitRepository.readUtilisedLimit` / `writeUtilisedLimit` |
| **Problem** | Read – add – write without a record lock. |
| **Example** | Two credits of 4,000 for customer 100234 at the same moment: both read 3,000, both write 7,000 → total 7,000 instead of 11,000. The second check also did not see the first. |
| **Impact** | Utilisation too low → later transactions pass a limit they should breach. Likely only under heavy parallel load (bulk / interface postings). |
| **Fix** | Read EB.ALBE.UTILISED.LIMIT **with lock** before updating (T24 Table API read-with-lock, if available on your release) – repository change only. |
| **Decision** | Confirm the lock API on your TAFJ release; agree the behaviour when the record is locked (wait / retry). |

## G3 – Partial update when one write fails

| | |
|---|---|
| **Where** | `DailyMonthlyLimitService.countAtInput` → daily record, monthly record, EB.ALBE.TXN.DETAILS.LIMIT |
| **Problem** | Each write logs its own error and the flow continues. |
| **Example** | Daily and monthly totals written, TXN.DETAILS write fails → a later rejection / reversal finds no record → the amount **stays counted for ever**. |
| **Impact** | Utilisation drifts upwards; customers hit limits they did not use. |
| **Fix** | If any of the three writes fails, throw so T24 rolls back the whole transaction. |
| **Decision** | Behaviour change: today a technical failure lets the transaction through; with the fix the user gets an error and the transaction is not saved. Which does the bank prefer? |

## G4 – REMARKS date tag can be removed on re-input

| | |
|---|---|
| **Where** | `DailyMonthlyLimitController.defaultFieldValues` |
| **Problem** | If a user edits REMARKS and deletes `ALBE.LIMIT.DATE=YYYYMMDD`, the re-input writes **today's** date. |
| **Example** | Input on 20-Nov (counted under `100234-20261120-AAA…`), tag deleted, re-input on 21-Nov → looks for `100234-20261121-AAA…`, not found → **counted twice** (20-Nov and 21-Nov). |
| **Impact** | Double utilisation for that transaction. |
| **Fix** | On re-input with no tag, look for an earlier record of the AAA (`@ID LIKE <customer>-...-<AAA>`, only in this rare case) and restore its date in REMARKS; log a warning. |
| **Decision** | None needed (code only). Optionally: ask users not to edit the tag. |

## G5 – REMARKS can exceed its maximum length

| | |
|---|---|
| **Where** | `DailyMonthlyLimitController.defaultFieldValues` |
| **Problem** | The tag adds 25 characters (` ALBE.LIMIT.DATE=20261120`). A long user remark plus the tag may be longer than the field allows. |
| **Impact** | T24 may cut the text (tag lost → G4 situation) or reject the input. |
| **Fix** | Check the length; if it does not fit, keep the tag and shorten the user text (or put the tag first). |
| **Decision** | Confirm the REMARKS maximum length on AA.ARRANGEMENT.ACTIVITY and which text may be shortened. |

## G6 – Blank CCY on EB.ALBE.CIF.LIMIT skips conversion

| | |
|---|---|
| **Where** | `CurrencyConversionService.getRate` |
| **Problem** | A blank currency is treated as "same currency" (rate 1). |
| **Example** | CIF.LIMIT CCY blank, transaction 1,000 USD → compared as 1,000 against EGP limits. |
| **Impact** | Checks far too lenient for foreign-currency transactions of that customer. |
| **Fix** | Blank CIF.LIMIT CCY → use the EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` CCY, else the local currency; log it. |
| **Decision** | None needed (code only). |

## G7 – Accounts in other companies count as 0 (maximum CIF)

| | |
|---|---|
| **Where** | `MaximumCifLimitService.getTotalCifBalance`, `CustomerLimitRepository.readAccount` |
| **Problem** | Accounts are read with the **current** company's financial mnemonic. |
| **Impact** | In a multi-company set-up a customer's accounts in another company are missed → total balance too low. |
| **Fix** | Read each account's company and use that company's mnemonic (repository change). |
| **Decision** | Is the bank multi-company with customers holding accounts in several companies? |

## G8 – Slow fallback SELECT on ACCOUNT

| | |
|---|---|
| **Where** | `CustomerLimitRepository.getCustomerAccountIds` |
| **Problem** | If CUSTOMER.ACCOUNT has no entry, a SELECT `ACCOUNT WITH CUSTOMER EQ …` runs – a full scan on a large ACCOUNT file. |
| **Impact** | Slow credit input for customers without a CUSTOMER.ACCOUNT entry. |
| **Fix** | Remove the fallback (no entry = no accounts). |
| **Decision** | Confirm CUSTOMER.ACCOUNT is always maintained. |

## G9 – Credit / debit decided by the activity name – FIXED

| | |
|---|---|
| **Where** | `DailyMonthlyLimitController` (`defaultFieldValues`, `buildRequest`), `MaximumCifLimitController` |
| **Was** | `CREDIT` / `DEBIT` found anywhere in the activity name; the maximum CIF check ran for every activity whose name had no `DEBIT` (also non-money activities). |
| **Now (21-Nov-2026)** | The system-filled AAA field **ACTIVITY.CLASS** is compared exactly with `ACCOUNTS-CREDIT-ARRANGEMENT` / `ACCOUNTS-DEBIT-ARRANGEMENT` (`Constants.ACTIVITY_CLASS_CREDIT/DEBIT`, `ValidationUtil.isCreditActivityClass/isDebitActivityClass`). User-defined activities linked to these classes are covered; any other class is skipped (no REMARKS tag, no count, no override). Confirmed in R25 javadoc that both are core AA.ACTIVITY.CLASS records and that ACTIVITY.CLASS is filled by the system (seen in DEV). |

---

## Findings from the activity record (AAA) – to confirm in DEV

Found in the R25 javadoc field help (local `Transact_L3_Javadoc`) and on a DEV AAA. None needs a table change.

| # | Point | What core says | Possible use | To confirm in DEV |
|---|---|---|---|---|
| A1 | **ORG.SYSTEM.DATE** | "initial T24 system date while processing the activity", system maintained | Counted date instead of the REMARKS tag – closes G1, G4, G5 and removes `defaultFieldValues` | filled already at UNAUTH `validateRecord`; unchanged on DELETE and AUTH-REV |
| A2 | COB and unauthorised credits/debits | AA.DECISION.PARAMETER UNAUTH.PROCESS: `IGNORE.TRANSACTION.ACTIVITIES` keeps them over COB; `DEFAULT` deletes them at COB and re-initiates after COB | With DEFAULT: our DELETE reverses, the re-initiated input counts again – on the **next** day | which decision is set |
| A3 | INITIATION.TYPE | USER, TRANSACTION, SECONDARY, SCHEDULED… | Count only TRANSACTION (FT/TT) if secondary debits (e.g. charges) must not count | bank rule |
| A4 | ORIG.TXN.AMT vs TXN.AMOUNT | when part of an amount goes to a secondary activity, TXN.AMOUNT holds only the difference | use ORIG.TXN.AMT for the limit if the full amount must count | bank rule |
| A5 | TXN.AMOUNT.LCY / TXN.EXCH.RATE | transaction amount in local currency and the rate used | if the limit CCY is always EGP, use TXN.AMOUNT.LCY – no rate table needed (E1) | limit CCY always EGP? |
| A6 | TXN.CONTRACT.ID / TXN.SYSTEM.ID | FT / TT id and source of the transaction | show the FT/TT reference in the transaction report (read at report time, like overrides) | – |

---

## E1 – Exchange rate lookup not connected

**Today**

| Case | Result |
|---|---|
| Transaction currency = limit currency | rate 1, checks run normally |
| A currency blank | treated as same currency (see G6) |
| Different currencies | `CustomerLimitRepository.readExchangeRate` returns "no rate" → override **EB-ALBE.EXCH.RATE.MISSING**, limit check **skipped** |
| Maximum CIF, account in another currency | same – rate-missing override, check skipped |

Ready: conversion of the transaction and of account balances, 2-decimal rounding, converted amounts stored in UTILISED.LIMIT / TXN.DETAILS (reversals do not convert again). Missing: only the **rate lookup**.

**Questions for the bank**

| # | Question | Why |
|---|---|---|
| 1 | Which table – the bank's rate table, or core **CURRENCY**? | where to read |
| 2 | Record id and field (e.g. CURRENCY `USD`, MID.REVAL.RATE) | what to read |
| 3 | Mid, buy or sell rate? | mid is usual for limit checks |
| 4 | Which currency market? | CURRENCY holds a list per market |
| 5 | Quote direction and multiplier (local per 1 foreign, or the reverse; per 1 or per 100) | so the rate is not applied upside down |

**Suggestion:** core **CURRENCY** (no new table, maintained daily by treasury). Rates are against the local currency; two foreign currencies are converted through local currency (USD → EGP → EUR), both reads cached for the call.

**Change once answered:** `Constants.EXCH_RATE_TABLE` / `EXCH_RATE_FIELD` (+ market, rate type) and `CustomerLimitRepository.readExchangeRate(from, to)`. Nothing else changes; the rate-missing override stays as the safety net.

---

## Other open items (not code gaps)

| Item | Owner |
|---|---|
| HLSD update for the agreed deviations (creation as Auth routine, GEN.PARAM `CUSTOMER.LIMIT`, counted date in REMARKS, currency conversion) | BA |
| One-off creation of EB.ALBE.CIF.LIMIT for existing customers (separate flow) | bank / dev |
| Verify the T24 API calls listed in TECHNICAL_REFERENCE.md §12 in DEV | dev |
| Set `Constants.LOG_DETAIL_ENABLED = false` before production | dev |
| Housekeeping of old EB.ALBE.TXN.DETAILS.LIMIT records (keep as long as a reversal is possible) | bank / dev |

## Suggested order

1. **G1, G4, G5, G6** – small, code only, no behaviour risk.
2. **E1** – as soon as the bank answers the five questions.
3. **G2, G3** – after the team decision (lock API, fail vs. let through).
4. **G7, G8, G9** – after the confirmations listed.
