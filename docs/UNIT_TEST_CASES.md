# ALBE Customer Limit – Unit Test Cases and Results

**Scope:** business logic, tested **outside T24** on 26-Nov-2026 after the clean rewrite (D47).
**How:** the real code in `src/` compiled with stand-in TAFJ classes and an in-memory repository (`extends CustomerLimitRepository`), both kept **outside** the repository. The same code also compiles with 0 errors against the real R25 libraries. The controllers with real T24 objects are DEV tests ([T24_CONFIGURATION.md](T24_CONFIGURATION.md) §13).
**Result codes:** **Pass** = run and matched · **DEV** = can only be run in T24.

Traceability to the HLSD: [TRACEABILITY.md](TRACEABILITY.md). The test cases before D47 (old routines, REMARKS tag, `DailyMonthlyLimitService`) are in Git history; their rules are covered again below.

## Summary

| Suite | Area | Cases | Pass |
|---|---|---|---|
| TE | Engine: input, take back, overrides, conversion, blocking, save failure (D47) | 40 | 40 |
| TA | Activity → request: mapping, amount, date, currency, conversion | 21 | 21 |
| TO | Activity credit / debit mapping (D41) | 7 | 7 |
| TQ | Override text and fields (D43 / D46) | 13 | 13 |
| TK | GEN.PARAM keys (DEV record) | 4 | 4 |
| TN | Record-not-found text (TQ13) and limit creation | 2 | 2 |
| – | Controllers in T24 (DEV only) | 6 | DEV |
| **Total** | | **93** | **87** |

## TE – Engine: input, take back, overrides, conversion, blocking, save failure (D47)

| Id | Case and expected result | Result |
|---|---|---|
| UT-E01 | actions | Pass |
| UT-E02 | root reference | Pass |
| UT-E03 | credit 1000 within limits: no override, counted day+month+txn | Pass |
| UT-E04 | IO first input: each record read once, written once | Pass |
| UT-E05 | no override on the screen | Pass |
| UT-E06 | daily 1000+4500=5500 > 5000: breach 500, counted | Pass |
| UT-E07 | daily override on TXN.AMOUNT, one parameter, credit id | Pass |
| UT-E08 | same FT again: counted once, same breach, nothing rewritten | Pass |
| UT-E09 | re-input 4500 -> 2000: used 3000, no breach, TXN.DETAILS 2000 | Pass |
| UT-E10 | re-input 2000 -> 6000: used 1000 + 6000 = 7000 > 5000 by 2000, counted 7000 | Pass |
| UT-E11 | monthly debit: migrated 19900 + 500 > 20000 by 400 on TXN.AMOUNT.LCY | Pass |
| UT-E12 | migrated field never changed | Pass |
| UT-E13 | debit exactly 2000 = daily limit: no breach | Pass |
| UT-E14 | daily + max CIF (50 + 6000 > 100 by 5950), each on its own field | Pass |
| UT-E15 | debit: max CIF not checked | Pass |
| UT-E16 | blank DAILY.CREDIT.LIMIT: not-defined override on CURRENCY, counted | Pass |
| UT-E17 | no limit record: not-defined override, counted 10.50 | Pass |
| UT-E18 | blank limit CCY: ERROR, not counted | Pass |
| UT-E19 | USD, no LCY amount, no TXN.EXCH.RATE: ERROR, not counted | Pass |
| UT-E20 | credit, no CUSTOMER.ACCOUNT: ERROR, not counted | Pass |
| UT-E21 | debit of the same customer: counted | Pass |
| UT-E22 | USD 10 x TXN.EXCH.RATE 48.5 = 485.00 counted and stored | Pass |
| UT-E23 | LCY amount wins over TXN.EXCH.RATE | Pass |
| UT-E24 | limit currency USD with EGP transaction: ERROR, not counted | Pass |
| UT-E25 | LCY not readable: rate override on EFFECTIVE.DATE, not blocked, not counted | Pass |
| UT-E26 | take back FT2 (7000 counted -> 1000 left), record deleted | Pass |
| UT-E27 | IO take back: TXN.DETAILS read once, records read once each, no SELECT | Pass |
| UT-E28 | take back again (second AUTH-REV call): nothing, FT1 keeps 1000 | Pass |
| UT-E29 | take back never counted (e.g. blocked FT9): nothing | Pass |
| UT-E30 | reversal later day (no ORG.SYSTEM.DATE): taken back on 01-Apr | Pass |
| UT-E31 | own transfer: debit and credit counted, two records | Pass |
| UT-E32 | take back the debit side only | Pass |
| UT-E33 | activity without FT: key = AAA id | Pass |
| UT-E34 | two customers: one record each | Pass |
| UT-E35 | TXN.DETAILS save fails -> flagged | Pass |
| UT-E36 | UTILISED save fails -> flagged | Pass |
| UT-E37a | input ok | Pass |
| UT-E37 | take back: TXN.DETAILS delete fails -> flagged | Pass |
| UT-E38 | normal input -> not flagged | Pass |
| UT-E39 | invalid request: nothing done | Pass |

## TA – Activity → request: mapping, amount, date, currency, conversion

| Id | Case and expected result | Result |
|---|---|---|
| UT-A05 | ACCOUNTS-CREDIT credit 100 | Pass |
| UT-A06 | ACCOUNTS-DEBIT debit 40 | Pass |
| UT-A07 | DEPOSITS-CREDIT not in the mapping -> not counted (D41) | Pass |
| UT-A08 | DEPOSITS-NEW-ARRANGEMENT with amount skipped | Pass |
| UT-A09 | credit class, no amount skipped | Pass |
| UT-A10 | SECONDARY counted (all types agreed) | Pass |
| UT-A11 | initiation type not in list skipped | Pass |
| UT-A12 | ORIG.TXN.AMT used when filled | Pass |
| UT-A13 | TXN.AMOUNT when ORIG blank | Pass |
| UT-D01 | REMARKS ignored, date = ORG.SYSTEM.DATE (D42) | Pass |
| UT-D02 | ORG.SYSTEM.DATE when no tag | Pass |
| UT-D03 | T24 TODAY when no ORG.SYSTEM.DATE | Pass |
| UT-D04 | server clock last resort | Pass |
| UT-C01 | AAA CURRENCY used | Pass |
| UT-C02 | arrangement CCY when AAA CURRENCY blank | Pass |
| UT-F05 | USD 100, LCY 4850, limit EGP=LCY -> 4850, no rate lookup | Pass |
| UT-F06 | limit USD (not LCY) -> blocked (D44), no lookup | Pass |
| UT-F07 | no LCY amount, no TXN.EXCH.RATE -> blocked (D44) | Pass |
| UT-F09 | no LCY amount, TXN.EXCH.RATE 48.75 -> 4875.00 (D44) | Pass |
| UT-F10 | LCY amount wins over TXN.EXCH.RATE (D44) | Pass |
| UT-F08 | same currency unchanged | Pass |

## TO – Activity credit / debit mapping (D41)

| Id | Case and expected result | Result |
|---|---|---|
| UT-O01 | mapped CREDIT | Pass |
| UT-O02 | mapped debit (lower case) -> DEBIT | Pass |
| UT-O03 | not mapped -> null (controller raises EB-ALBE.ACTIVITY.NOT.MAPPED) | Pass |
| UT-O04 | invalid value X -> null | Pass |
| UT-O05 | custom activity mapped CREDIT counts as credit (class says DEBIT) | Pass |
| UT-O06 | not-mapped activity not counted | Pass |
| UT-O07 | mapping record missing -> null (error raised) | Pass |

## TQ – Override text and fields (D43 / D46)

| Id | Case and expected result | Result |
|---|---|---|
| UT-Q01 | build with one parameter | Pass |
| UT-Q02 | no params -> id only | Pass |
| UT-Q03 | commas/brackets removed from params | Pass |
| UT-Q04 | null param -> empty | Pass |
| UT-Q05 | two overrides raised | Pass |
| UT-Q06 | daily on TXN.AMOUNT | Pass |
| UT-Q07 | monthly on TXN.AMOUNT.LCY | Pass |
| UT-Q08 | three overrides raised | Pass |
| UT-Q09 | max CIF on ORIG.TXN.AMT | Pass |
| UT-Q10 | not defined on CURRENCY | Pass |
| UT-Q11 | rate missing on EFFECTIVE.DATE | Pass |
| UT-Q12 | TXN.AMOUNT untouched | Pass |
| UT-Q13 | no breach -> nothing | Pass |

## TK – GEN.PARAM keys (DEV record)

| Id | Case and expected result | Result |
|---|---|---|
| UT-K01 | CURRENCY -> ccy EGP | Pass |
| UT-K02 | daily credit 10000 | Pass |
| UT-K03 | monthly debit 10000 | Pass |
| UT-K04 | MAXIMUM.OVERALL.CIF.BALANCE -> maxCifBalance 10000 | Pass |

## TN – Record-not-found text (TQ13) and limit creation

| Id | Case and expected result | Result |
|---|---|---|
| UT-N01 | 'Record does not exist' -> not found (FALSE) -> limit created | Pass |
| UT-N02 | other failure (file missing) -> unknown (null) -> not created | Pass |

## Controllers in T24 (DEV only)

| Id | What | Where tested |
|---|---|---|
| UT-T01 | `validateRecord` as Post Routine raises the overrides on the FT screen, `&` filled with "<customer> by <amount>" (TQ12 / TQ19) | §13 #4 |
| UT-T02 | routine called for DELETE and AUTH-REV (ACTION DELETE / REVERSAL in api.log), take-back done (TQ20) | §13 #6, #9 |
| UT-T03 | rejected override / cancelled FT → T24 rolls our writes back (TQ22 – **passed** 26-Nov) | §13 #5a |
| UT-T04 | Auth routine on CUSTOMER,ALBE.CORP creates the record; "record not found" text recognised | §13 #1, #2 |
| UT-T05 | ORG.SYSTEM.DATE, ORIG.TXN.AMT, TXN.AMOUNT.LCY, TXN.EXCH.RATE filled at UNAUTH; ORG.SYSTEM.DATE unchanged on re-input / DELETE / AUTH-REV (TQ21) | GAPS A1, A4, A5 |
| UT-T06 | same commit: TXN.DETAILS written earlier is found (Table read) – FT re-committed after the override screen counts once (TQ23) | §13 #7 |
