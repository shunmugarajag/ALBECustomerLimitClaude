# ALBE Customer Limit – Unit Test Cases and Results

**Scope:** the three routines in `com.temenos.albe` (`AlbeClActivityLimit`, `AlbeClMaxCif`, `AlbeClLimitCreate`), tested **outside T24** on 26-Nov-2026.
**How:** the real `validateRecord` / `postUpdateRequest` methods are called with stand-in TAFJ classes and in-memory tables (DataAccess, Session, generated Table classes), kept **outside** the repository. Credit activities run `AlbeClActivityLimit` then `AlbeClMaxCif`, as in ACTIVITY.API. The code also compiles with 0 errors against the R25 libraries.
**Test data:** customer 100128, account 1000001208, arrangement AA26093C0Y4T; limits daily credit 10,000 / debit 5,000, monthly credit 50,000 / debit 20,000, maximum CIF 100,000 (EGP).
**Result codes:** **Pass** = run and matched · **DEV** = can only be run in T24.

Traceability to the HLSD: [TRACEABILITY.md](TRACEABILITY.md). Earlier cases (MVC version, removed 26-Nov) are in Git history.

## Summary

| Group | Cases | Pass |
|---|---|---|
| Flows T01–T26 (input, re-input, authorise, delete, reversal, currency and errors, rollback, save failures) | 26 | 26 |
| Extra cases | 5 | 5 |
| Max CIF routine | 3 | 3 |
| Limit creation | 3 | 3 |
| DEV only | 6 | DEV |
| **Total** | **43** | **37** |

## Flows T01–T26 (input, re-input, authorise, delete, reversal, currency and errors, rollback, save failures)

| Id | Case | Result |
|---|---|---|
| UT-T01 | credit within limits | Pass |
| UT-T02 | debit within limits | Pass |
| UT-T03 | daily credit override | Pass |
| UT-T04 | monthly credit override | Pass |
| UT-T05 | max CIF override | Pass |
| UT-T06 | three overrides together | Pass |
| UT-T07 | amend 4500 -> 2000 | Pass |
| UT-T08 | re-input same amount | Pass |
| UT-T09 | amend over limit | Pass |
| UT-T10 | authorise | Pass |
| UT-T11 | authorise after amend | Pass |
| UT-T12 | delete | Pass |
| UT-T13 | delete again | Pass |
| UT-T14 | reversal | Pass |
| UT-T15 | reversal later day | Pass |
| UT-T16 | reversal again | Pass |
| UT-T17 | USD with LCY amount | Pass |
| UT-T18 | USD with rate | Pass |
| UT-T19 | no conversion info | Pass |
| UT-T20 | limit CCY blank | Pass |
| UT-T21 | activity not mapped | Pass |
| UT-T22 | no limit record | Pass |
| UT-T23 | no CUSTOMER.ACCOUNT (max CIF routine blocks; the count of routine 1 is rolled back by T24 with the FT) | Pass |
| UT-T24 | declined override: writes in the same commit (T24 rolls back) | Pass |
| UT-T25 | UTILISED save fails | Pass |
| UT-T26 | TXN.DETAILS save fails | Pass |

## Extra cases

| Id | Case | Result |
|---|---|---|
| UT-E01 | debit equal to daily limit | Pass |
| UT-E02 | monthly with migrated | Pass |
| UT-E03 | account in USD: rate override, counted | Pass |
| UT-E04 | blank daily limit | Pass |
| UT-E05 | own transfer both sides | Pass |

## Max CIF routine

| Id | Case | Result |
|---|---|---|
| UT-M01 | MAX.CIF blank: not-defined override, counted | Pass |
| UT-M02 | debit: max CIF not checked | Pass |
| UT-M03 | only routine 1 attached: no max CIF check | Pass |

## Limit creation

| Id | Case | Result |
|---|---|---|
| UT-C01 | new customer: record created from GEN.PARAM | Pass |
| UT-C02 | existing record not overwritten | Pass |
| UT-C03 | read failed: not created | Pass |

## DEV only

| Id | What | Where tested |
|---|---|---|
| UT-DEV1 | Post Routine overrides shown on the FT screen, `&` = "<customer> by <amount>" | T24_CONFIGURATION §13 #4 |
| UT-DEV2 | DELETE and AUTH-REV reach `AlbeClActivityLimit` (TQ20), take-back done | §13 #6, #9 |
| UT-DEV3 | declined override / error in either routine → T24 rolls back the count (TQ22 passed with the MVC routine 26-Nov; retest) | §13 #5a |
| UT-DEV4 | `AlbeClLimitCreate` on CUSTOMER,ALBE.CORP creates the record; "record not found" text recognised | §13 #1, #2 |
| UT-DEV5 | ORG.SYSTEM.DATE, ORIG.TXN.AMT, TXN.AMOUNT.LCY, TXN.EXCH.RATE filled at UNAUTH (TQ21) | GAPS A1, A4, A5 |
| UT-DEV6 | `AlbeClMaxCif` reads the real WORKING.BALANCE (DEV showed 0.00 with the MVC version) | §13 #12 |
