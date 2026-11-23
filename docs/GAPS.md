# ALBE Customer Limit – Known Gaps and Open Points

Review of the current code (21-Nov-2026). Every fix proposed here works **without changing the pre-approved table structure**.
All queries and gaps in one register (raised / solved): [QUERIES_AND_GAPS.md](QUERIES_AND_GAPS.md). For how the code works see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md); for the T24 set-up see [T24_CONFIGURATION.md](T24_CONFIGURATION.md).

| # | Gap | Risk | Fix needs | Status |
|---|---|---|---|---|
| G1 | Counted date follows the JVM clock, not the T24 date | High – seen in DEV | code only | **fixed 21-Nov-2026** (T24 date) |
| G2 | Parallel inputs for the same customer can lose an update | High | lock API (confirm) | open – team decision |
| G3 | Partial update when one of the three writes fails | High | behaviour decision | open – team decision |
| G4 | REMARKS date tag can be removed on re-input | Low (was Medium) | code only | **mitigated** – ORG.SYSTEM.DATE fallback |
| G5 | REMARKS can exceed its maximum length | Medium | field length (confirm) | open |
| G6 | Blank CCY on EB.ALBE.CIF.LIMITS skips conversion | Medium | code only | **fixed 22-Nov-2026** – error, transaction blocked |
| G7 | Accounts in other companies count as 0 (maximum CIF) | Low | multi-company set-up (confirm) | open |
| G8 | Slow fallback SELECT on ACCOUNT | Low | confirm CUSTOMER.ACCOUNT | open |
| G9 | Credit / debit decided by the activity name | Low | code only | **fixed 21-Nov-2026** (ACTIVITY.CLASS) |
| E1 | Exchange rate lookup not connected | Medium (was High) | bank answers (section E1) | **partly solved** – LCY limits use TXN.AMOUNT.LCY; foreign-CCY limits and max CIF accounts still pending |

Already fine (checked): no shared state between calls (no static data, session per call); reversals use the stored amount (no new conversion); totals never below 0; validation and counting use the same REMARKS date even across midnight; enquiries never write.

---

## G1 – Counted date follows the JVM time zone – FIXED

| | |
|---|---|
| **Where** | `DailyMonthlyLimitController` (`defaultFieldValues`, `buildRequest`), enquiry controllers – `LocalDate.now()` |
| **Problem** | `LocalDate.now()` uses the **JVM default time zone**. TAFJ servers often run in UTC while the bank is in Egypt (UTC+2). |
| **Example** | Credit input at 01:30 Egypt time on 21-Nov = 23:30 UTC on 20-Nov → counted on **20-Nov**, so it uses (and can breach) the wrong day's daily limit. |
| **Impact** | Wrong daily bucket for about 2 hours every night (month buckets wrong at month end). |
| **Fix** | One method `CustomerLimitRepository.getToday()` used everywhere instead of `LocalDate.now()`. Either T24 `TODAY` from `Session` (business date – moves at COB) **or** the server date in a fixed bank time zone (`Constants.BANK_ZONE = "Africa/Cairo"`). |
| **Decision** | Business date (TODAY) or calendar date in Cairo time? (Agreed so far: "server date" → calendar date in Cairo time.) |
| **Fixed (21-Nov-2026)** | `ActivityRequestBuilder.getT24Date`: REMARKS tag → AAA **ORG.SYSTEM.DATE** → T24 TODAY (`Session.getCurrentVariable("!TODAY")`, `CustomerLimitRepository.getT24Today`) → server clock only as a logged last resort. The REMARKS tag is written with the same T24 date; enquiry defaults ("today") use T24 TODAY. Decision taken: **T24 date**. |
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
| **Mitigated (21-Nov-2026)** | Without the tag the activity's ORG.SYSTEM.DATE is used (and written back as the tag), which is the T24 date of the first input – so the re-input finds the same record. Remaining risk only if ORG.SYSTEM.DATE changes on re-input (DEV check A1). |

## G5 – REMARKS can exceed its maximum length

| | |
|---|---|
| **Where** | `DailyMonthlyLimitController.defaultFieldValues` |
| **Problem** | The tag adds 25 characters (` ALBE.LIMIT.DATE=20261120`). A long user remark plus the tag may be longer than the field allows. |
| **Impact** | T24 may cut the text (tag lost → G4 situation) or reject the input. |
| **Fix** | Check the length; if it does not fit, keep the tag and shorten the user text (or put the tag first). |
| **Decision** | Confirm the REMARKS maximum length on AA.ARRANGEMENT.ACTIVITY and which text may be shortened. |

## G6 – Blank CCY on EB.ALBE.CIF.LIMITS skips conversion – FIXED

| | |
|---|---|
| **Where** | `CurrencyConversionService.getRate` |
| **Problem** | A blank currency is treated as "same currency" (rate 1). |
| **Example** | CIF.LIMIT CCY blank, transaction 1,000 USD → compared as 1,000 against EGP limits. |
| **Impact** | Checks far too lenient for foreign-currency transactions of that customer. |
| **Fix** | Blank CIF.LIMIT CCY → use the EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` CCY, else the local currency; log it. |
| **Decision (22-Nov-2026)** | User: the CCY can't be blank – **error at transaction time, the transaction is blocked**. Done: both checks set `limitCcyMissing`; the view raises the **error** `EB-ALBE.CIF.LIMIT.CCY.MISSING` (ACTIVITY.API, FT). The ACCOUNT.PARAMETER hook can only return overrides, so there it comes back as an override with the same id – check in DEV whether it can be made non-approvable. |

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

## Findings from the activity record (AAA) – implemented 21-Nov-2026

Found in the R25 javadoc field help (local `Transact_L3_Javadoc`) and on a DEV AAA. None needs a table change. The routines are now **common for all activities** (`controller/activity/ActivityRequestBuilder`).

| # | Point | Implemented | Still to confirm in DEV |
|---|---|---|---|
| A1 | **ORG.SYSTEM.DATE** ("initial T24 system date while processing the activity") | Counted date: REMARKS tag → ORG.SYSTEM.DATE → T24 TODAY → clock (logged). The tag is kept (written with the T24 date) until DEV confirms ORG.SYSTEM.DATE is stable; then the tag (G5) and `defaultFieldValues` can go. | filled at UNAUTH `defaultFieldValues` / `validateRecord`; unchanged on re-input, DELETE and AUTH-REV |
| A2 | COB: AA.DECISION.PARAMETER UNAUTH.PROCESS | No code – configuration. `IGNORE.TRANSACTION.ACTIVITIES` keeps unauthorised credits/debits over COB. With `DEFAULT` COB deletes them (our DELETE reverses) and re-initiates them (counted again, on the tagged day if REMARKS is kept). | which decision is set; whether the re-initiated AAA keeps its REMARKS |
| A3 | INITIATION.TYPE | **All types counted** (agreed). List in `Constants.COUNTED_INITIATION_TYPES`; remove a value (e.g. SECONDARY) to stop counting it. Blank = counted. | – |
| A4 | ORIG.TXN.AMT vs TXN.AMOUNT | **ORIG.TXN.AMT if filled**, else TXN.AMOUNT (agreed). Overrides are still raised on TXN.AMOUNT. | ORIG.TXN.AMT filled for FT/TT credits |
| A5 | TXN.AMOUNT.LCY | When the limit CCY is the **local currency** (Session LCY) and the currencies differ, the activity's LCY amount (ORIG.TXN.AMT.LCY / TXN.AMOUNT.LCY) is used – no rate lookup (agreed). | TXN.AMOUNT.LCY filled at UNAUTH |
| A6 | TXN.CONTRACT.ID | New report column 10 **TXN.REF** (read from the activity at report time, like the overrides). | – |
| A8 | DEV check 21-Nov-2026: `AAACT26091QJJZFQQB` (`DEPOSITS-APPLYPAYMENT-PAYMENT.RULES`, deposit funding, INITIATION.TYPE Transaction) | All fields filled on the authorised record: TXN.AMOUNT = ORIG.TXN.AMT = 9,994.00, LCY amounts 9,994.00, CURRENCY EGP, ORG.SYSTEM.DATE 01 APR 2026 (clock 29 OCT 26). TXN.CONTRACT.ID = `AAACT26091QJJZFQQ9\BNK` (AA-internal: activity id + company, not an FT), TXN.SYSTEM.ID = AAAA. Action APPLYPAYMENT → not counted (the money is counted once, on the current-account ACCOUNTS-DEBIT side). | unauthorised ACCOUNTS-CREDIT-ARRANGEMENT still to check |
| A7 | **Direction for any activity** | Credit / debit by the **action** of ACTIVITY.CLASS (`<PRODUCT.LINE>-<ACTION>-<PROPERTY.CLASS>`): `Constants.CREDIT_ACTIONS` {CREDIT}, `DEBIT_ACTIONS` {DEBIT}, any product line; activity without an amount or with another action → skipped. The sign of the balance movement (asked for) is **not available** when the limit is checked: `validateRecord` runs before any accounting, and R25 `TransactionData` has no amount/sign – so the class action is the only reliable source at input. | **Decision 21-Nov-2026:** the **bank decides which activities** get the routines; the code stays generic. Direction stays the **class action for now** (CREDIT / DEBIT = T24's own source/destination decision for the customer's account); reading the FT/TT source/destination accounts was discussed and not chosen (source-specific, record maybe not readable at validateRecord, extra read). **To decide later:** direction for other actions the bank attaches (e.g. APPLYPAYMENT), and whether deposit payouts credited to the current account should count. Until then such activities are logged and not counted. |

---

## FT / TELLER / ACCOUNT.PARAMETER routines – removed (22-Nov-2026)

Built 21/22-Nov-2026 and removed the same day (user): FT, TT and other postings on **AA accounts** arrive as activities, so ACTIVITY.API covers them. Their open points (F1–F5, S1–S4, G10) are closed with them. Kept from that work: one engine (`CustomerLimitEngine`), counted key = root reference (FT behind the activity, else AAA id), **own-account transfer not counted** (S5: the first activity's check may still show an override).

To confirm in DEV: activity created at FT input; activity overrides shown on the FT; FT delete / reversal reach the activity. Not covered: non-AA accounts.

---

## Open questions

All open questions (bank and DEV) are in [QUERIES_AND_GAPS.md](QUERIES_AND_GAPS.md).

---

## E1 – Exchange rate lookup not connected

**Today**

| Case | Result |
|---|---|
| Transaction currency = limit currency | rate 1, checks run normally |
| Limit currency blank | **error** EB-ALBE.CIF.LIMIT.CCY.MISSING, transaction blocked (G6) |
| Different currencies, limit CCY = local currency | the activity's **TXN.AMOUNT.LCY** (or ORIG.TXN.AMT.LCY) is used – no rate needed (21-Nov-2026) |
| Different currencies, limit CCY ≠ local currency | `CustomerLimitRepository.readExchangeRate` returns "no rate" → override **EB-ALBE.EXCH.RATE.MISSING**, limit check **skipped** |
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
| One-off creation of EB.ALBE.CIF.LIMITS for existing customers (separate flow) | bank / dev |
| Verify the T24 API calls listed in TECHNICAL_REFERENCE.md §12 in DEV | dev |
| Set `Constants.LOG_DETAIL_ENABLED = false` before production | dev |
| Housekeeping of old EB.ALBE.TXN.DETAILS.LIMIT records (keep as long as a reversal is possible) | bank / dev |

## Suggested order

1. **G5** – small, code only (G6 fixed), no behaviour risk (G1 fixed, G4 mitigated). DEV check A1 may remove G5 altogether.
2. **E1** – as soon as the bank answers the five questions.
3. **G2, G3** – after the team decision (lock API, fail vs. let through).
4. **G7, G8** – after the confirmations listed (G9 done).
