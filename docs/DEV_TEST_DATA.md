# ALBE Customer Limit - DEV Test Data and OFS Guide

DEV (R26), company EG0010001, T24 date 03-APR-2026. Results are kept in [DEV_TEST_RESULTS.xlsx](DEV_TEST_RESULTS.xlsx); a Word copy of this data is [ALBE_Unit_Test_Data.docx](ALBE_Unit_Test_Data.docx).

## 1. How to run OFS in DEV

- Run at the `$` prompt as one heredoc. Do **not** start `tRun tSS GCS` on its own first (the block lines then go to tSS as messages and give "NO SIGN ON NAME").
  ```
  tRun tSS GCS <<'EOF'
  <OFS lines>
  EOF
  ```
- After every FT or new arrangement add two lines `C` - they answer the TAFJ debugger stops in `KIRAN.BP AC.GET.IBAN.b:132`. Spare `C` lines only give a harmless "NO SIGN ON NAME".
- Input with **RAJA1**, authorise with **AUTHOR** (same user: `EB.RTN.SAME.NAME.AUTHORISER/INPUTTER`).
- Replace `***` with the passwords. Never paste passwords into chats, tickets or files.
- OFS accepts overrides by itself. "Override answered No" can only be tested in the browser.
- tSS is its own TAFJ process: it loads the current JARs at each start; JBoss needs a restart after a deploy.
- FUNDS.TRANSFER.OFS source is blocked for RAJA (channel BROWSERTC) - use source GCS as above.

## 2. Customers and accounts

All accounts: product **ACCT.MAN.SAV.ACCOUNT** (EGP only), ACTIVITY.API `ACCT.MANDATE.API` (TRANSFERIN: ALBE.CL.ACTIVITY.LIMIT + ALBE.CL.MAX.CIF; TRANSFEROUT: ALBE.CL.ACTIVITY.LIMIT).

| Customer | Mnemonic | Account(s) | Arrangement | Limit record | Use |
|---|---|---|---|---|---|
| 100128 | - | 1000001208 | - | yes | Post No Credits - debit only |
| 100237 | CLTEST01 | 1000001224 | AA260933W1JN | yes | small balance, minimum balance 500 - debit side, error cases |
| 100238 | CLTEST02 | 1000001232, 1000001259 | AA26093XPKBC (1232) | yes | main source of FTs; two accounts (max CIF, own transfer) |
| 100239 | CLTEST03 | 1000001248 | AA2609399K47 | yes | main credit target; breach cases |
| 100240 | CLTEST04 | none | - | yes | no accounts (DEV-27); USD later |
| 100241 | CLTEST05 | none | - | yes | no accounts; USD later |
| 100242 | CLTEST06 | 1000001267 | - | **no (on purpose)** | no limit record - skipped (D52) |

Account balances are low (1232 about EGP 1,300 on 27-Nov). Large FTs are rejected by T24 (minimum balance) after our checks ran - the overrides still show in the reply and the counts are rolled back (TQ22). For breach tests, lower the customer's limits for the test and restore them afterwards (section 5).

## 3. Standard limits (EB.ALBE.CIF.LIMIT, same as GEN.PARAM CUSTOMER.LIMIT)

| Field | Value |
|---|---|
| CURRENCY | EGP |
| DAILY.CREDIT.LIMIT / DAILY.DEBIT.LIMIT | 10,000 |
| MONTHLY.CREDIT.LIMIT / MONTHLY.DEBIT.LIMIT | 100,000 |
| MAXIMUM.OVERALL.CIF.AMOUNT | 200,000 |

## 4. Create the limit records (new table, D55)

```
tRun tSS GCS <<'EOF'
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,<customer>,CURRENCY:1:1=EGP,DAILY.CREDIT.LIMIT:1:1=10000,DAILY.DEBIT.LIMIT:1:1=10000,MONTHLY.CREDIT.LIMIT:1:1=100000,MONTHLY.DEBIT.LIMIT:1:1=100000,MAXIMUM.OVERALL.CIF.AMOUNT:1:1=200000
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,<customer>
EOF
```
Repeat the two lines for 100128 and 100237-100241 (done in DEV 27-Nov 10:14 T24 time). A blank field is cleared with `FIELD:1:1=`.

## 5. One-shot scenario block (no multi-currency)

Sets small limits, runs the scenarios and restores the standard limits. Expected results:

| # | FT | Scenario | Expected |
|---|---|---|---|
| F1 | 1232 -> 1248, 100 | within limits | no override |
| F2 | 1232 -> 1248, 250 | daily credit + daily debit breach | DAILY.CREDIT 100239 by 50.00; DAILY.DEBIT 100238 by (used + 350 - 900) |
| F3 | 1232 -> 1248, 100 | daily + monthly breach | DAILY.CREDIT by 150.00, MONTHLY.CREDIT by 50.00, DAILY.DEBIT 100238 |
| F4 | 1232 -> 1248, 10 (max CIF = 1) | max CIF breach, real balance (DEV-46) | MAX.CIF 100239 by (balance of 1248 + 10 - 1) |
| F5 | 1232 -> 1259, 10 | own transfer | both sides counted for 100238 |
| F6 | 1232 -> 1267, 10 | no limit record | 100242 skipped |
| F7 | 1224 -> 1248, 10 (100237 CURRENCY USD) | limit not in EGP (DEV-39) | error EB-ALBE.EXCH.RATE.MISSING |
| F8 | 1224 -> 1248, 10 (100237 CURRENCY blank) | blank currency (DEV-22) | error EB-ALBE.CIF.LIMIT.CCY.MISSING |

```
tRun tSS GCS <<'EOF'
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100239,DAILY.CREDIT.LIMIT:1:1=300,MONTHLY.CREDIT.LIMIT:1:1=400
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100239
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100238,DAILY.DEBIT.LIMIT:1:1=900
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100238
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001232,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=100,CREDIT.ACCT.NO:1:1=1000001248
C
C
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001232,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=250,CREDIT.ACCT.NO:1:1=1000001248
C
C
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001232,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=100,CREDIT.ACCT.NO:1:1=1000001248
C
C
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100239,MAXIMUM.OVERALL.CIF.AMOUNT:1:1=1
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100239
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001232,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=10,CREDIT.ACCT.NO:1:1=1000001248
C
C
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001232,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=10,CREDIT.ACCT.NO:1:1=1000001259
C
C
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001232,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=10,CREDIT.ACCT.NO:1:1=1000001267
C
C
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100237,CURRENCY:1:1=USD
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100237
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001224,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=10,CREDIT.ACCT.NO:1:1=1000001248
C
C
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100237,CURRENCY:1:1=
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100237
FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,,TRANSACTION.TYPE:1:1=AC,DEBIT.ACCT.NO:1:1=1000001224,DEBIT.CURRENCY:1:1=EGP,DEBIT.AMOUNT:1:1=10,CREDIT.ACCT.NO:1:1=1000001248
C
C
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100239,DAILY.CREDIT.LIMIT:1:1=10000,MONTHLY.CREDIT.LIMIT:1:1=100000,MAXIMUM.OVERALL.CIF.AMOUNT:1:1=200000
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100239
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100238,DAILY.DEBIT.LIMIT:1:1=10000
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100238
EB.ALBE.CIF.LIMIT,/I/PROCESS,RAJA1/***/EG0010001,100237,CURRENCY:1:1=EGP
EB.ALBE.CIF.LIMIT,/A/PROCESS,AUTHOR/***/EG0010001,100237
EOF
```

Then: `grep 'ALBE.CUSTOMER.LIMIT' <api.log path> | tail -40`

## 6. Follow-up flows (need the FT ids from the replies)

| Flow | OFS | Expected |
|---|---|---|
| Authorise | `FUNDS.TRANSFER,/A/PROCESS,AUTHOR/***/EG0010001,<FT>` | nothing counted again |
| Delete (unauthorised FT) | `FUNDS.TRANSFER,/D/PROCESS,RAJA1/***/EG0010001,<FT>` | counts taken back (reply shows IDEL; the record is then gone) |
| Reverse (authorised FT) | `/R/` by RAJA1, then `/A/` by AUTHOR | AUTH-REV takes back the counts (REVE) |
| Amend (unauthorised FT) | `FUNDS.TRANSFER,/I/PROCESS,RAJA1/***/EG0010001,<FT>,DEBIT.AMOUNT:1:1=<new>` | old amount taken back, new one counted |

## 7. Not testable in DEV with this data

- Multi-currency: ACCT.MAN.SAV.ACCOUNT allows EGP only; adding USD needs USD conditions for ES.ACTIVITY.RESTRICTION, CRINTEREST, SCHEDULE and PRODUCT.QUALIFIER.RULES ("MISSING CONDITION RECORD") - product decision, else SIT. USD rate market 1: mid 53.13, buy 53.08, sell 53.18.
- Override answered No (DEV-18 / 43 / 44): browser only.
- Activity not mapped (DEV-23): needs a temporary GEN.PARAM change (shared DEV).
- COB (DEV-29): bank decision. Save failure (DEV-28) and no rate on the activity (DEV-38): outside T24 only.
