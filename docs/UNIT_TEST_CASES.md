# ALBE Customer Limit – Unit Test Cases and Results

**Scope:** business logic and validation, tested **outside T24** on 20/21-Nov-2026.
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
| Currency conversion | UT-F01 … F10 | 10 | 10 | – |
| Limit creation | UT-C01 … C03 | 3 | 3 | – |
| Counted date (REMARKS – removed, D42) | UT-R01 … R08 | 8 | 8 | – |
| Request validation | UT-V01 … V06 | 6 | 6 | – |
| Activity mapping (any activity) | UT-A01 … A13 | 13 | 13 | – |
| Counted date (T24 date) | UT-D06 … D09 | 4 | 4 | – |
| Currency from the activity | UT-C04 … C05 | 2 | 2 | – |
| Engine, key, own transfer | UT-G01 … G21 | 12 | 12 | – |
| Blank limit currency (error) | UT-H01 … H04 | 3 | 3 | – |
| GEN.PARAM keys (DEV record) | UT-K01 … K04 | 4 | 4 | – |
| Block on save failure (D37) | UT-B01 … B05 | 5 | 5 | – |
| Record-not-found text (TQ13) | UT-N01 … N02 | 2 | 2 | – |
| Activity credit/debit mapping (D41) | UT-O01 … O07 | 7 | 7 | – |
| Override text and fields (D43) | UT-W01 … W08 | 8 | 8 | – |
| Single routine (D44 / D45 / D46) | UT-S01 … S20 | 20 | 20 | – |
| Controllers in T24 | UT-T01 … T10 (no T05/T06) | 8 | – | 8 |
| **Total** | | **142** | **134** | **8** |

---

## HLSD use cases (exact numbers from HLSD section 8)

| Id | Given | When | Expected | Actual | Result |
|---|---|---|---|---|---|
| UC-8.2 | daily credit limit 5000, utilised daily credit 3000 | credit 4000 | EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED, **2000** | `[EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED,100234 by 2000.00]` | Pass |
| UC-8.3 | monthly credit limit 15000, utilised monthly 13000 | credit 7000 | EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED, **5000** | `[EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED,100234 by 5000.00]` | Pass |
| UC-8.4 | MAX.CIF.BALANCE 100000, working balance 70000 | credit 50000 | EB-ABLE.MAX.CIF.BAL.EXCEED, **20000** | `EB-ABLE.MAX.CIF.BAL.EXCEED^100234]20000.00` | Pass |
| UC-8.5 | daily debit limit 5000, utilised daily debit 3000 | debit 4000 | EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED, **2000** | `[EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED,100234 by 2000.00]` | Pass |
| UC-8.6 | monthly debit limit 15000, utilised monthly 13000 | debit 7000 | EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED, **5000** | `[EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED,100234 by 5000.00]` | Pass |

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
| UT-X03 | account in EUR, no EUR rate | credit | EB-ABLE.EXCH.RATE.MISSING "from EUR to EGP"; check skipped | Pass |
| UT-X04 | debit activity | validate | maximum CIF check skipped (credit only) | Pass |

## Limit not defined

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-L01 | DAILY.CREDIT.LIMIT blank | credit | EB-ABLE.CIF.LIMIT.NOT.DEFINED once | Pass |
| UT-L02 | no EB.ALBE.CIF.LIMITS record | credit | EB-ABLE.CIF.LIMIT.NOT.DEFINED once; no limit check | Pass |
| UT-L03 | MAX.CIF.BALANCE blank | credit | EB-ABLE.CIF.LIMIT.NOT.DEFINED (maximum CIF) | Pass |

## Utilisation (count at input, reverse)

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-U01 | nothing used | input credit 3000 (UNAUTH) | daily + monthly used 3000; TXN.DETAILS `<cust>-<date>-<FT/AAA>-<arrangement>` with ACCOUNT.NO, CR, EFF.DATE, 3000.00 | Pass |
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
| UT-F04 | no EUR rate | credit 100 EUR | EB-ABLE.EXCH.RATE.MISSING "from EUR to EGP"; check skipped | Pass |
| UT-F05 | limit EGP = LCY, TXN.AMOUNT.LCY 4850 | credit 100 USD | 4850.00 EGP, rate 48.5, **no rate lookup** | Pass |
| UT-F06 | limit USD (not LCY) | credit 100 EGP | **blocked** (D44), no lookup | Pass |
| UT-F07 | no LCY amount, no TXN.EXCH.RATE | credit 100 USD | **blocked** (D44), no lookup | Pass |
| UT-F08 | same currency | credit 300 EGP | 300.00, no lookup | Pass |
| UT-F09 | no LCY amount, TXN.EXCH.RATE 48.75 | debit 100 USD | 4875.00 EGP (D44) | Pass |
| UT-F10 | LCY amount and TXN.EXCH.RATE both given | debit 100 USD, LCY 4850 | 4850.00 – LCY amount wins | Pass |

## Limit creation

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-C01 | EB.ALBE.CIF.LIMITS exists (amended customer) | customer authorised | nothing written | Pass |
| UT-C02 | no record; GEN.PARAM `CUSTOMER.LIMIT` CCY EGP, daily 5000 … | customer authorised | record created with the generic values | Pass |
| UT-C03 | read of CIF.LIMIT fails (not "not found") | customer authorised | nothing written, error logged (never overwrite) | Pass |

## GEN.PARAM keys (DEV record, 23-Nov-2026)

Given: GEN.PARAM `CUSTOMER.LIMIT` exactly as in DEV – CURRENCY EGP, the four daily / monthly limits 10000, MAXIMUM.OVERALL.CIF.BALANCE 10000. When: `CustomerLimitReader.getGenericLimits`.

| Id | Expected | Result |
|---|---|---|
| UT-K01 | key CURRENCY → limit CCY EGP | Pass |
| UT-K02 | DAILY.CREDIT.LIMIT → 10000 | Pass |
| UT-K03 | MONTHLY.DEBIT.LIMIT → 10000 | Pass |
| UT-K04 | key MAXIMUM.OVERALL.CIF.BALANCE → max CIF balance 10000 | Pass |

## Counted date in REMARKS

| Id | Given | When | Expected | Result |
|---|---|---|---|---|
| UT-R01 | REMARKS "Salary credit Nov" | first input (UNAUTH) | REMARKS "Salary credit Nov ALBE.LIMIT.DATE=<T24 date>" | Retired (D42) |
| UT-R02 | tag present | re-input | REMARKS unchanged (first date kept) | Retired (D42) |
| UT-R03 | REMARKS "x ALBE.LIMIT.DATE=20261120", today 21-Nov | read date | 20-Nov used | Retired (D42) |
| UT-R04 | status AUTH-REV | defaulting | REMARKS not touched | Retired (D42) |
| UT-R05 | no tag / "ALBE.LIMIT.DATE=20261131" / empty | read date | no date (T24 date used); invalid date rejected | Retired (D42) |
| UT-R06 | REMARKS "Salary" (D42) | first input | REMARKS unchanged – the code never writes REMARKS | Pass |
| UT-R07 | no ORG.SYSTEM.DATE (D42) | first input | REMARKS unchanged | Pass |
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

## Engine, counted key, own transfer – 22-Nov-2026

| Id | Given | Expected | Result |
|---|---|---|---|
| UT-G01 | reference `FT26091ABC\BNK` | root `FT26091ABC` | Pass |
| UT-G02 | reference `FT26091ABC;1` | root `FT26091ABC` | Pass |
| UT-G08 | debit activity 3000 from an FT, daily debit limit 2000 | breach | Pass |
| UT-G10 | FT 3000 customer 100 → 200: debit and credit activity counted | 3000 debit for 100, 3000 credit for 200 | Pass |
| UT-G11 | activities with TXN.CONTRACT.ID `FT26091ABCDE\BNK` | key `FT26091ABCDE` | Pass |
| UT-G12 | same FT, two customers | `100-20260401-FT26091ABCDE`, `200-20260401-FT26091ABCDE` | Pass |
| UT-G14 | reversal on 05-Apr without a date on the activity | taken back from 01-Apr | Pass |
| UT-G15 | activity without FT | key = AAA id | Pass |
| UT-G17 | own transfer: credit activity (other arrangement) after the debit | checked like any credit (FQ6) | Pass |
| UT-G18 | same, count | both sides counted: debit 700 and credit 700, two records | Pass |
| UT-G19 | reversal of the debit side | only the debit taken back; credit stays | Pass |
| UT-G20 | re-input of the credit side | replaced, still 700 (no double count) | Pass |

## Blank limit currency – 22-Nov-2026

| Id | Given | Expected | Result |
|---|---|---|---|
| UT-H01 | EB.ALBE.CIF.LIMITS CCY blank, credit 5000 | daily/monthly and maximum CIF checks flagged limitCcyMissing | Pass |
| UT-H02 | same | **error** EB-ALBE.CIF.LIMIT.CCY.MISSING on the field (not an override) | Pass |
| UT-H04 | CCY filled again | normal check | Pass |

## Record-not-found text (TQ13) – 24-Nov-2026

| Id | Given | Expected | Result |
|---|---|---|---|
| UT-N01 | CIF.LIMITS read throws the R25 DEV text `EB-INTERNAL.ERROR … Record does not exist` | not found → limit record created | Pass |
| UT-N02 | read throws another error (`File does not exist …`) | existence unknown → nothing created (never overwrite) | Pass |

### Activity credit/debit mapping (D41)

| Id | Case | Expected | Result |
|---|---|---|---|
| UT-O01 | ACCOUNTS-CREDIT-ARRANGEMENT = CREDIT in the mapping | CREDIT | Pass |
| UT-O02 | value `debit` (lower case) | DEBIT | Pass |
| UT-O03 | activity not in the mapping | null → EB-ALBE.ACTIVITY.NOT.MAPPED | Pass |
| UT-O04 | value `X` (not CREDIT/DEBIT) | null → error | Pass |
| UT-O05 | custom activity mapped CREDIT, ACTIVITY.CLASS says DEBIT | counted as credit (mapping wins) | Pass |
| UT-O06 | not-mapped activity in the request builder | not counted | Pass |
| UT-O07 | mapping record missing | null → error | Pass |

## Override text and fields (D43) – 26-Nov-2026

| Id | Case | Expected | Result |
|---|---|---|---|
| UT-W01 | build id + 1 param (D46) | `[EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED,100199 by 12750.00]` | Pass |
| UT-W02 | build without params | id only | Pass |
| UT-W03 | params with `,` `[` `]` (e.g. `1,000.00`) | removed: `[EB-X,1000.00,a]` | Pass |
| UT-W04 | null param | empty part `[EB-X,100,]` | Pass |
| UT-W05 | daily + monthly breach | 2 overrides: daily on TXN.AMOUNT, monthly on TXN.AMOUNT.LCY | Pass |
| UT-W06 | max CIF + limit missing + rate missing | max CIF on ORIG.TXN.AMT, not defined on CURRENCY, rate on EFFECTIVE.DATE, TXN.AMOUNT untouched | Pass |
| UT-W07 | nothing breached | 0 overrides | Pass |
| UT-W08 | blank limit CCY | error `[EB-ALBE.CIF.LIMIT.CCY.MISSING,<cust>]` on TXN.AMOUNT | Pass |

## Single routine (D44) – 26-Nov-2026

| Id | Case | Expected | Result |
|---|---|---|---|
| UT-S01 | status → action | UNAUTH INPUT, DELETE DELETE, AUTH-REV REVERSAL, AUTH AUTHORISE, REVERSE REVERSAL INPUT | Pass |
| UT-S02 | credit 1000 within limits | no override, daily + monthly 1000.00, TXN.DETAILS written | Pass |
| UT-S03 | reads for one input | CIF.LIMITS 1, UTILISED 2, TXN.DETAILS 1 | Pass |
| UT-S04 | credit 4500, daily used 1000, limit 5000 | breach by 500.00, counted 5500.00 | Pass |
| UT-S05 | override text | `[EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED,100 by 500.00]` on TXN.AMOUNT | Pass |
| UT-S06 | same FT called again (after "Accept Overrides" / re-input) | still 5500.00, one record, same breach | Pass |
| UT-S07 | debit 300 | counted, reads 1 / 2 / 1 | Pass |
| UT-S08 | credit with daily + max CIF breach | both breaches in one result | Pass |
| UT-S09 | max CIF in the same call | CIF.LIMITS read once | Pass |
| UT-S10 | USD, no LCY amount, no rate | ERROR `[EB-ALBE.EXCH.RATE.MISSING,USD,EGP]`, not counted | Pass |
| UT-S11 | USD 10, TXN.EXCH.RATE 48.5 | 485.00 counted and stored | Pass |
| UT-S12 | blank limit CCY | blocked, not counted | Pass |
| UT-S13 | no CIF.LIMITS record | not-defined override, counted as is | Pass |
| UT-S14 | take back (DELETE / AUTH-REV) | 5500 → 1000, record deleted, TXN.DETAILS read once | Pass |
| UT-S15 | take back again | nothing | Pass |
| UT-S16 | AUTH-REV called twice (review fix) | first takes back 700, second nothing; the other FT keeps 1000 | Pass |
| UT-S17 | credit, CUSTOMER.ACCOUNT empty | ERROR `[EB-ALBE.CUSTOMER.ACCOUNT.MISSING,500]`, not counted | Pass |
| UT-S18 | debit, CUSTOMER.ACCOUNT empty | not affected, counted | Pass |
| UT-S19 | LCY not readable, USD transaction | rate-missing override, not blocked | Pass |
| UT-S20 | debit 2500 > daily debit 2000 (D46) | `[EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED,100 by 500.00]` | Pass |

## Block on save failure (D37) – 23-Nov-2026

| Id | Given | Expected | Result |
|---|---|---|---|
| UT-B01 | normal count | not blocked | Pass |
| UT-B02 | TXN.DETAILS write fails at count | blocked (`isSaveFailed`) → controller raises EB-ALBE.LIMIT.UPDATE.FAILED | Pass |
| UT-B03 | UTILISED.LIMIT write fails at count | blocked | Pass |
| UT-B04 | count ok, then reversal | not blocked after count | Pass |
| UT-B05 | TXN.DETAILS delete fails at reversal | blocked | Pass |

## Controllers in T24 (DEV only)

| Id | What | Where tested |
|---|---|---|
| UT-T01 | `defaultFieldValues` writes the tag to the AAA REMARKS and it is kept for DELETE / AUTH-REV | T24_CONFIGURATION §13 #3, #9 |
| UT-T02 | `validateRecord` as Post Routine raises the overrides on the FT screen, both `&` filled, daily **and** monthly shown (D43, TQ12 / TQ19) | §13 #4 |
| UT-T10 | single routine called for DELETE and AUTH-REV (ACTION DELETE / REVERSAL in api.log), take-back done (D44, TQ20) | §8 |
| UT-T03 | `postCoreTableUpdate` fires for UNAUTH, DELETE and AUTH-REV | §13 #5, #6, #9 |
| UT-T04 | Auth routine on CUSTOMER,ALBE.CORP creates the record; "record not found" text recognised | §13 #1, #2 |
| UT-T07 | AAA ACTIVITY.CLASS filled at UNAUTH for FT/TT credits and debits (and for user-defined activities linked to the two classes) | §13 #3 |
| UT-T08 | ORG.SYSTEM.DATE, ORIG.TXN.AMT, TXN.AMOUNT.LCY, INITIATION.TYPE filled at UNAUTH; ORG.SYSTEM.DATE unchanged on re-input / DELETE / AUTH-REV | GAPS A1, A4, A5 |
| UT-T09 | `Session.getCurrentVariable("!TODAY")` returns the T24 date (DEV: 01 APR 2026, not the server clock) | GAPS G1 |
