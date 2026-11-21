# ALBE Customer Limit – Unit Test Cases and Results

**Scope:** business logic, reports and validation, tested **outside T24** on 20/21-Nov-2026.
**How:** the real code in `src/` compiled with stand-in TAFJ classes (kept outside the repository) and an in-memory repository that replaces the database. The controllers' T24 objects (hooks, AAA, arrangement) are covered by the DEV / SIT tests in [T24_CONFIGURATION.md](T24_CONFIGURATION.md) §13 and §14.4.
**Result codes:** **Pass** = run and matched · **DEV** = can only be run in T24.

Traceability to the HLSD: [TRACEABILITY.md](TRACEABILITY.md).

---

## Summary

| Area | Ids | Cases | Pass | DEV only |
|---|---|---|---|---|
| HLSD use cases | UC-8.2 … UC-8.6 | 5 | 5 | – |
| Daily limit | UT-D01 … D05 | 5 | 5 | – |
| Monthly limit | UT-M01 … M03 | 3 | 3 | – |
| Maximum CIF balance | UT-X01 … X04 | 4 | 4 | – |
| Limit not defined | UT-L01 … L03 | 3 | 3 | – |
| Utilisation (count / reverse) | UT-U01 … U07 | 7 | 7 | – |
| Currency conversion | UT-F01 … F08 | 8 | 8 | – |
| Limit creation | UT-C01 … C03 | 3 | 3 | – |
| Counted date (REMARKS) | UT-R01 … R08 | 8 | 8 | – |
| Request validation | UT-V01 … V06 | 6 | 6 | – |
| Activity mapping (any activity) | UT-A01 … A13 | 13 | 13 | – |
| Counted date (T24 date) | UT-D06 … D09 | 4 | 4 | – |
| Currency from the activity | UT-C04 … C05 | 2 | 2 | – |
| Reports | UT-E01 … E17 | 17 | 17 | – |
| FT / TELLER routines | UT-FT01 … FT23 | 23 | 23 | – |
| Controllers in T24 | UT-T01 … T11 | 11 | – | 11 |
| **Total** | | **122** | **111** | **11** |

---

## HLSD use cases (exact numbers from HLSD section 8)

| Id | Given | When | Expected | Actual | Result |
|---|---|---|---|---|---|
| UC-8.2 | daily credit limit 5000, utilised daily credit 3000 | credit 4000 | EB-ALBE.DAILY.LIMIT.EXCEED, customer, **2000** | `EB-ALBE.DAILY.LIMIT.EXCEED^100234]2000.00` | Pass |
| UC-8.3 | monthly credit limit 15000, utilised monthly 13000 | credit 7000 | EB-ALBE.MONTHLY.LIMIT.EXCEED, **5000** | `EB-ALBE.MONTHLY.LIMIT.EXCEED^100234]5000.00` | Pass |
| UC-8.4 | MAX.CIF.BALANCE 100000, working balance 70000 | credit 50000 | EB-ALBE.MAX.CIF.BAL.EXCEED, **20000** | `EB-ALBE.MAX.CIF.BAL.EXCEED^100234]20000.00` | Pass |
| UC-8.5 | daily debit limit 5000, utilised daily debit 3000 | debit 4000 | EB-ALBE.DAILY.LIMIT.EXCEED, **2000** | `EB-ALBE.DAILY.LIMIT.EXCEED^100234]2000.00` | Pass |
| UC-8.6 | monthly debit limit 15000, utilised monthly 13000 | debit 7000 | EB-ALBE.MONTHLY.LIMIT.EXCEED, **5000** | `EB-ALBE.MONTHLY.LIMIT.EXCEED^100234]5000.00` | Pass |

(`^` = field mark, `]` = value mark in the override text.)

## Daily limit

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-D01 | daily credit 5000, nothing used | credit 3000 | no override; daily used 3000 | Pass |
| UT-D02 | used 3000 | credit 4000 | daily override, difference 2000 | Pass |
| UT-D03 | used 3000 | credit 2000 (exactly the limit) | **no** override (only above the limit is a breach) | Pass |
| UT-D04 | daily debit 2000, used 1500 | debit 600 | daily override, difference 100 | Pass |
| UT-D05 | daily credit and monthly credit both exceeded | one credit | **two** overrides (daily and monthly) | Pass |

## Monthly limit

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-M01 | monthly credit 15000, used 13000 | credit 7000 | monthly override 5000 | Pass |
| UT-M02 | monthly debit 6000, migrated 4000, used 1000 | debit 1500 | monthly override **500** (4000 + 1000 + 1500 − 6000) | Pass |
| UT-M03 | migrated debit 4000 | count and reverse debits | UTILISED.MIGRATED.DEBIT.LIMIT stays 4000 (never written) | Pass |

## Maximum CIF balance

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-X01 | max 100000, balance 70000 | credit 50000 | override 20000 | Pass |
| UT-X02 | max 10000; accounts 6000 EGP + 50 USD (rate 48.50 = 2425) | credit 2000 EGP | total 10425 → override **425** | Pass |
| UT-X03 | account in EUR, no EUR rate | credit | EB-ALBE.EXCH.RATE.MISSING "from EUR to EGP"; check skipped | Pass |
| UT-X04 | debit activity | validate | maximum CIF check skipped (credit only) | Pass |

## Limit not defined

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-L01 | DAILY.CREDIT.LIMIT blank | credit | EB-ALBE.CIF.LIMIT.NOT.DEFINED once | Pass |
| UT-L02 | no EB.ALBE.CIF.LIMIT record | credit | EB-ALBE.CIF.LIMIT.NOT.DEFINED once; no limit check | Pass |
| UT-L03 | MAX.CIF.BALANCE blank | credit | EB-ALBE.CIF.LIMIT.NOT.DEFINED (maximum CIF) | Pass |

## Utilisation (count at input, reverse)

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-U01 | nothing used | input credit 3000 (UNAUTH) | daily + monthly used 3000; TXN.DETAILS `<cust>-<date>-<AAA>` with ACCOUNT.NO, CR, EFF.DATE, 3000.00 | Pass |
| UT-U02 | daily used 3000 | input credit 4000 with accepted override | daily used 7000 (accepted override counts) | Pass |
| UT-U03 | AAA2 counted 4000 | re-input AAA2 with 3500 | earlier 4000 removed, 3500 added → 6500; check excludes own 4000 | Pass |
| UT-U04 | AAA2 counted | rejected (DELETE) | its amount reversed; its TXN.DETAILS record deleted | Pass |
| UT-U05 | input on 20-Nov | reversal (AUTH-REV) on 25-Nov | reversed from **20-Nov** and Nov records; 25-Nov untouched | Pass |
| UT-U06 | AUTH-REV, no TXN.DETAILS record (counted before go-live) | reversal | amount subtracted from today's records | Pass |
| UT-U07 | used 1000 | reverse 1500 | total floored at **0**, never negative | Pass |

## Currency conversion

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-F01 | limits EGP | credit 3000 EGP | rate 1, no conversion | Pass |
| UT-F02 | limits EGP, USD rate 48.50 | credit 100 USD | compared as 4850 EGP → daily override 2850; stored 4850.00 | Pass |
| UT-F03 | USD input counted | rejected | 4850 EGP reversed (stored amount, no re-conversion) | Pass |
| UT-F04 | no EUR rate | credit 100 EUR | EB-ALBE.EXCH.RATE.MISSING "from EUR to EGP"; check skipped | Pass |
| UT-F05 | limit EGP = LCY, TXN.AMOUNT.LCY 4850 | credit 100 USD | 4850.00 EGP, rate 48.5, **no rate lookup** | Pass |
| UT-F06 | limit USD (not LCY) | credit 100 EGP | rate lookup → missing | Pass |
| UT-F07 | no LCY amount on the activity | credit 100 USD | rate lookup | Pass |
| UT-F08 | same currency | credit 300 EGP | 300.00, no lookup | Pass |

## Limit creation

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-C01 | EB.ALBE.CIF.LIMIT exists (amended customer) | customer authorised | nothing written | Pass |
| UT-C02 | no record; GEN.PARAM `CUSTOMER.LIMIT` CCY EGP, daily 5000 … | customer authorised | record created with the generic values | Pass |
| UT-C03 | read of CIF.LIMIT fails (not "not found") | customer authorised | nothing written, error logged (never overwrite) | Pass |

## Counted date in REMARKS

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-R01 | REMARKS "Salary credit Nov" | first input (UNAUTH) | REMARKS "Salary credit Nov ALBE.LIMIT.DATE=<T24 date>" | Pass |
| UT-R02 | tag present | re-input | REMARKS unchanged (first date kept) | Pass |
| UT-R03 | REMARKS "x ALBE.LIMIT.DATE=20261120", today 21-Nov | read date | 20-Nov used | Pass |
| UT-R04 | status AUTH-REV | defaulting | REMARKS not touched | Pass |
| UT-R05 | no tag / "ALBE.LIMIT.DATE=20261131" / empty | read date | no date (T24 date used); invalid date rejected | Pass |
| UT-R06 | REMARKS "Salary", ORG.SYSTEM.DATE 20260401, clock 21-Nov | first input | "Salary ALBE.LIMIT.DATE=20260401" (T24 date, not clock) | Pass |
| UT-R07 | no ORG.SYSTEM.DATE, T24 TODAY 20260402 | first input | "ALBE.LIMIT.DATE=20260402" | Pass |
| UT-R08 | `DEPOSITS-NEW-ARRANGEMENT` | first input | REMARKS untouched (not counted) | Pass |

## Request validation (controller, once)

| Id | Request | Expected | Result |
|---|---|---|---|
| UT-V01 | customer, AAA, date, credit > 0 | valid (daily/monthly and maximum CIF) | Pass |
| UT-V02 | no customer | invalid | Pass |
| UT-V03 | no AAA id | invalid (daily/monthly) | Pass |
| UT-V04 | amount 0 | invalid | Pass |
| UT-V05 | debit for maximum CIF | invalid (credit only) | Pass |
| UT-V06 | null request / blank customer id (creation) | invalid | Pass |

## Activity mapping – any activity (ActivityRequestBuilder) – 21-Nov-2026

| Id | ACTIVITY.CLASS | Expected | Result |
|---|---|---|---|
| UT-A01 | `ACCOUNTS-CREDIT-ARRANGEMENT` (also with spaces around) | credit | Pass |
| UT-A02 | `ACCOUNTS-DEBIT-ARRANGEMENT` | debit; not credit (maximum CIF skipped) | Pass |
| UT-A03 | `DEPOSITS-NEW-ARRANGEMENT`, `ACCOUNTS-CHANGE.PRODUCT-ARRANGEMENT` | neither – daily/monthly skipped (no REMARKS tag, no count), maximum CIF skipped | Pass |
| UT-A04 | blank, null | neither | Pass |
| UT-A05 | `ACCOUNTS-CREDIT-ARRANGEMENT`, TXN.AMOUNT 100 | credit 100 | Pass |
| UT-A06 | `ACCOUNTS-DEBIT-ARRANGEMENT`, TXN.AMOUNT 40 | debit 40 | Pass |
| UT-A07 | `DEPOSITS-CREDIT-ARRANGEMENT` (other product line) | credit (action CREDIT) | Pass |
| UT-A08 | `DEPOSITS-NEW-ARRANGEMENT` with an amount | skipped (action NEW) | Pass |
| UT-A09 | credit class, no TXN.AMOUNT / ORIG.TXN.AMT | skipped | Pass |
| UT-A10 | INITIATION.TYPE SECONDARY | counted (all types agreed) | Pass |
| UT-A11 | INITIATION.TYPE not in `COUNTED_INITIATION_TYPES` | skipped | Pass |
| UT-A12 | TXN.AMOUNT 500, ORIG.TXN.AMT 2000, ORIG.TXN.AMT.LCY 97000 | amount 2000, LCY 97000 | Pass |
| UT-A13 | TXN.AMOUNT 500, ORIG.TXN.AMT blank, TXN.AMOUNT.LCY 24250 | amount 500, LCY 24250 | Pass |

## Counted date (T24 date) and currency – 21-Nov-2026

| Id | Given | Expected | Result |
|---|---|---|---|
| UT-D06 | REMARKS tag 20260315, ORG.SYSTEM.DATE 20260401 | 15-Mar-2026 (tag wins) | Pass |
| UT-D07 | no tag, ORG.SYSTEM.DATE 20260401, T24 TODAY 20260402 | 01-Apr-2026 | Pass |
| UT-D08 | no tag, no ORG.SYSTEM.DATE, T24 TODAY 20260402 | 02-Apr-2026 | Pass |
| UT-D09 | none of them | server clock (logged as FALLBACK) | Pass |
| UT-C04 | AAA CURRENCY USD | transaction currency USD | Pass |
| UT-C05 | AAA CURRENCY blank, arrangement EGP | EGP | Pass |

## FUNDS.TRANSFER / TELLER routines – 21-Nov-2026

| Id | Given | Expected | Result |
|---|---|---|---|
| UT-FT01 | function I, status INAU | COUNT | Pass |
| UT-FT02 | function D | REVERSE.DELETE | Pass |
| UT-FT03 | function A, status INAU | NONE | Pass |
| UT-FT04 | function A, status RNAU | REVERSE | Pass |
| UT-FT05 | function R (reversal input) | NONE | Pass |
| UT-FT06 | FT DEBIT.ACCT.NO 1001, DEBIT.AMOUNT 3000 | debit side DR 1001 3000 | Pass |
| UT-FT07 | FT CREDIT.AMOUNT blank, AMOUNT.CREDITED "EGP3000.00" | credit side CR 2002 3000.00 | Pass |
| UT-FT08 | FT only DEBIT.AMOUNT entered | credit side overrides on DEBIT.AMOUNT | Pass |
| UT-FT09 | TT cash deposit, DR.CR.MARKER DEBIT | side 1 DR (cash), side 2 CR customer 200 6000 | Pass |
| UT-FT10 | FT 3000 from customer 100 (daily debit 2000) to 200 | override(s) raised | Pass |
| UT-FT11 | input | debit 3000 counted for 100 | Pass |
| UT-FT12 | input | credit 3000 counted for 200 | Pass |
| UT-FT13 | input | TXN.DETAILS `100-20260401-FT…AAA.DR`, `200-20260401-FT…AAA.CR` | Pass |
| UT-FT14 | re-input same day with 2500 | 2500 (earlier 3000 taken back first) | Pass |
| UT-FT15 | authorise | no change | Pass |
| UT-FT16 | reversal authorised on 05-Apr | taken back from 01-Apr; 05-Apr untouched | Pass |
| UT-FT17 | after reversal | TXN.DETAILS records deleted | Pass |
| UT-FT18 | input then delete | taken back, records deleted | Pass |
| UT-FT19 | TT cash deposit | only customer 200 counted; cash account skipped | Pass |
| UT-FT20 | own transfer (same customer both sides) | `…DDD.CR` and `…DDD.DR` kept apart | Pass |
| UT-FT21 | USD 10 debit, LOC.AMT.DEBITED 485, limit EGP | 485.00 counted (no rate lookup) | Pass |
| UT-FT22 | report for an FT-counted row | TXN.REF = FT id, OVERRIDES from FUNDS.TRANSFER | Pass |
| UT-FT23 | AA activity with TXN.SYSTEM.ID FT, skip list empty (default) | still counted by the activity routine | Pass |

## Reports

| Id | Report | Selection | Expected | Result |
|---|---|---|---|---|
| UT-E01 | Summary | all customers | one line per CIF.LIMIT record, 17 columns | Pass |
| UT-E02 | Summary | customer over daily limit | available −2000, OVER.LIMIT Y | Pass |
| UT-E03 | Summary | OVER.LIMIT Y | only customers over a limit | Pass |
| UT-E04 | Summary | blank limit | limit and available blank | Pass |
| UT-E05 | Summary | DATE 20261131 / 2026-11-20 | EB-ALBE.ENQ.INVALID.DATE | Pass |
| UT-E06 | Period | 3 days, all customers | one line per customer per day **with usage** | Pass |
| UT-E07 | Period | one customer, 20 days | only the 2 days with usage | Pass |
| UT-E08 | Period | 2 months | monthly lines; used includes migrated (1,950,000 + 75,000 = 2,025,000) | Pass |
| UT-E09 | Period | OVER.LIMIT Y | only over-limit lines | Pass |
| UT-E10 | Period | none / both ranges / 202613 / from > to / 2026-01-01…2026-11-20 / 2024-01…2026-11 | PERIOD.MANDATORY / PERIOD.MANDATORY / INVALID.MONTH / DATE.RANGE / RANGE.TOO.LONG / RANGE.TOO.LONG | Pass |
| UT-E11 | Period | customer without CIF.LIMIT | line with usage, limits blank | Pass |
| UT-E12 | Transactions | one customer | counted transactions oldest first; rejected one not shown | Pass |
| UT-E13 | Transactions | dates, all customers | all customers' transactions in the range | Pass |
| UT-E14 | Transactions | OVERRIDDEN Y | only daily / monthly / max CIF overrides (not "limit not defined"); OVERRIDES column from the activity | Pass |
| UT-E15 | Transactions | no customer, no date / from > to / 20261131 / > 92 days | CUSTOMER.OR.DATE / DATE.RANGE / INVALID.DATE / RANGE.TOO.LONG | Pass |
| UT-E17 | Transactions | any | column 10 TXN.REF = AAA TXN.CONTRACT.ID (e.g. FT26091ABCDE); 10 columns | Pass |
| UT-E16 | Summary, 1,000 customers | `LOG_DETAIL_ENABLED` false | 3 log lines (instead of ~3,000) | Pass |

## Controllers in T24 (DEV only)

| Id | What | Where tested |
|---|---|---|
| UT-T01 | `defaultFieldValues` writes the tag to the AAA REMARKS and it is kept for DELETE / AUTH-REV | T24_CONFIGURATION §13 #3, #9 |
| UT-T02 | `validateRecord` raises the overrides on TXN.AMOUNT (`id FM p1 VM p2` filled into `&`) | §13 #4 |
| UT-T03 | `postCoreTableUpdate` fires for UNAUTH, DELETE and AUTH-REV | §13 #5, #6, #9 |
| UT-T04 | Auth routine on CUSTOMER,ALBE.CORP creates the record; "record not found" text recognised | §13 #1, #2 |
| UT-T05 | Enquiry `setIds` lines shown in the right columns; EB.ERROR messages | §14.4 |
| UT-T06 | AA.ARRANGEMENT.ACTIVITY OVERRIDE read (live and `$NAU`) for the overridden-transactions report | REPORTS R10 |
| UT-T07 | AAA ACTIVITY.CLASS filled at UNAUTH for FT/TT credits and debits (and for user-defined activities linked to the two classes) | §13 #3 |
| UT-T08 | ORG.SYSTEM.DATE, ORIG.TXN.AMT, TXN.AMOUNT.LCY, INITIATION.TYPE filled at UNAUTH; ORG.SYSTEM.DATE unchanged on re-input / DELETE / AUTH-REV | GAPS A1, A4, A5 |
| UT-T09 | `Session.getCurrentVariable("!TODAY")` returns the T24 date (DEV: 01 APR 2026, not the server clock) | GAPS G1 |
| UT-T10 | FT/TT: AFTER.UNAU.RTN fires on input and delete; AUTH.ROUTINE fires on reversal authorisation (RECORD.STATUS RNAU/REVE) | T24_CONFIGURATION §8a |
| UT-T11 | FT/TT overrides raised from INPUT.ROUTINE appear on the amount field and can be accepted | §8a |
