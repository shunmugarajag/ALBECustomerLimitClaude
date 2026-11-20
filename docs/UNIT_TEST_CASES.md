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
| Currency conversion | UT-F01 … F04 | 4 | 4 | – |
| Limit creation | UT-C01 … C03 | 3 | 3 | – |
| Counted date (REMARKS) | UT-R01 … R05 | 5 | 5 | – |
| Request validation | UT-V01 … V06 | 6 | 6 | – |
| Reports | UT-E01 … E16 | 16 | 16 | – |
| Controllers in T24 | UT-T01 … T06 | 6 | – | 6 |
| **Total** | | **67** | **61** | **6** |

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

## Limit creation

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-C01 | EB.ALBE.CIF.LIMIT exists (amended customer) | customer authorised | nothing written | Pass |
| UT-C02 | no record; GEN.PARAM `CUSTOMER.LIMIT` CCY EGP, daily 5000 … | customer authorised | record created with the generic values | Pass |
| UT-C03 | read of CIF.LIMIT fails (not "not found") | customer authorised | nothing written, error logged (never overwrite) | Pass |

## Counted date in REMARKS

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-R01 | REMARKS "Salary credit Nov" | first input (UNAUTH) | REMARKS "Salary credit Nov ALBE.LIMIT.DATE=<today>" | Pass |
| UT-R02 | tag present | re-input | REMARKS unchanged (first date kept) | Pass |
| UT-R03 | REMARKS "x ALBE.LIMIT.DATE=20261120", today 21-Nov | read date | 20-Nov used | Pass |
| UT-R04 | status AUTH-REV | defaulting | REMARKS not touched | Pass |
| UT-R05 | no tag / "ALBE.LIMIT.DATE=20261131" / empty | read date | no date (today used); invalid date rejected | Pass |

## Request validation (controller, once)

| Id | Request | Expected | Result |
|---|---|---|---|
| UT-V01 | customer, AAA, date, credit > 0 | valid (daily/monthly and maximum CIF) | Pass |
| UT-V02 | no customer | invalid | Pass |
| UT-V03 | no AAA id | invalid (daily/monthly) | Pass |
| UT-V04 | amount 0 | invalid | Pass |
| UT-V05 | debit for maximum CIF | invalid (credit only) | Pass |
| UT-V06 | null request / blank customer id (creation) | invalid | Pass |

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
