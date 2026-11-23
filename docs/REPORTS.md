# ALBE Customer Limit – Reports

The customer limit reports (T24 NOFILE enquiries): what each one shows, how to run it, how it works, and how to set it up in T24.
For the rest of the module see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) and [T24_CONFIGURATION.md](T24_CONFIGURATION.md).

| Report | Enquiry | Question it answers |
|---|---|---|
| 1. Limit summary | `ALBE.CUSTOMER.LIMIT.SUMMARY` | Where does each customer stand **on one day** (that day and its month)? |
| 2. Period report | `ALBE.CUSTOMER.LIMIT.PERIOD` | How much did customers use **per day or per month over a period**? |
| 3. Transaction report | `ALBE.CUSTOMER.LIMIT.TXN` | **Which transactions** are counted – incl. **only the overridden ones**? |

All three: **one customer or all customers**. Summary and period: **only over-limit rows** with `OVER.LIMIT EQ Y`. Transactions: **only overridden transactions** with `OVERRIDDEN EQ Y`.

---

## How every report controller works

```
1. read the selection (CUSTOMER, DATE ...)
2. check it (CustomerLimitEnquiryValidator)        -> problem: EB.ERROR
3. CustomerLimitEnquiryService reads the data       -> model rows (LimitSummaryRow / LimitPeriodRow / LimitTxnRow)
4. CustomerLimitEnquiryView turns each row into one line, columns separated by "*"
5. lines returned to T24                            -> none: EB-NO.REC.SELECT
```

The controllers hold no business rules and no DB access. Inside the module everything works on the model classes; the `*` text is made only at the end because T24's `Enquiry.setIds` must return `List<String>` (the ENQUIRY record splits each line into its columns).

| Layer | Class |
|---|---|
| Controller | `CustomerLimitSummaryEnquiryController`, `CustomerLimitPeriodEnquiryController`, `CustomerLimitTxnEnquiryController` |
| Validation | `CustomerLimitEnquiryValidator` |
| Service | `service.enquiry.CustomerLimitEnquiryService` (read only) |
| Model | `model.enquiry`: `LimitSummaryRow`, `LimitPeriodRow`, `LimitTxnRow` |
| View | `CustomerLimitEnquiryView` (column order documented in the class) |
| Repository | `CustomerLimitRepository` – `selectCifLimitIds`, `selectUtilisedLimitIds`, `selectTxnDetailsIds`, reads |

---

## Sample data used in the examples (reports 1 and 2)

| Customer | Limits (EB.ALBE.CIF.LIMITS, EGP) | Transactions counted |
|---|---|---|
| 100234 | daily CR 500,000 / DR 300,000 · monthly CR 5,000,000 / DR 3,000,000 · max CIF 10,000,000 | 30 Oct CR 300,000 · 18 Nov CR 250,000 · 19 Nov CR 520,000 (daily limit override accepted) |
| 100345 | daily CR 200,000 / DR 200,000 · monthly CR 2,000,000 / DR 2,000,000 · max CIF 5,000,000 · **1,950,000 migrated monthly debit** | 19 Nov DR 75,000 · 20 Nov CR 230,000 (daily limit override accepted) |
| 999999 | **no limit record** | 20 Nov CR 1,000 |

Reading the numbers in every report:

- **Available** = limit − used. **Negative** = over the limit (transactions accepted with the override).
- **Blank limit** = not defined in EB.ALBE.CIF.LIMITS (available blank too).
- **Monthly used** = migrated (UTILISED.MIGRATED.*) + counted (UTILISED.*).
- **Rejected and reversed** transactions are in no report and in no used amount.

---

## 1. Limit summary – `ALBE.CUSTOMER.LIMIT.SUMMARY`

One line per customer for one day: each limit, used that day / that month, available.

### Selection

| Field | Required | Meaning |
|---|---|---|
| CUSTOMER | no | one customer; blank = every customer with an EB.ALBE.CIF.LIMITS record |
| DATE | no | YYYYMMDD; blank = today (T24 date) |
| OVER.LIMIT | no | `Y` = only customers with a used amount above its limit |

### Columns

| Col | Field | Meaning |
|---|---|---|
| 1 | CUSTOMER | customer id |
| 2 | CCY | limit currency |
| 3 | REPORT.DATE | date reported |
| 4 / 5 / 6 | DAILY.CR.LIMIT / .UTILISED / .AVAILABLE | daily credit |
| 7 / 8 / 9 | DAILY.DR.LIMIT / .UTILISED / .AVAILABLE | daily debit |
| 10 / 11 / 12 | MONTHLY.CR.LIMIT / .UTILISED / .AVAILABLE | monthly credit |
| 13 / 14 / 15 | MONTHLY.DR.LIMIT / .UTILISED / .AVAILABLE | monthly debit |
| 16 | MAX.CIF.BALANCE | maximum CIF balance limit |
| 17 | OVER.LIMIT | Y / N |

### How it works

1. Customers: the one given, or one SELECT of all EB.ALBE.CIF.LIMITS ids.
2. Per customer **3 reads**: EB.ALBE.CIF.LIMITS `<cust>`, EB.ALBE.UTILISED.LIMIT `<cust>-YYYYMMDD` and `<cust>-YYYYMM`.
3. One row per customer; with OVER.LIMIT = Y only rows where `isOverLimit()` is true.

### Example

`ENQ ALBE.CUSTOMER.LIMIT.SUMMARY DATE EQ 20261119 OVER.LIMIT EQ Y`

```
100234*EGP*20261119*500000.00*520000.00*-20000.00*300000.00*0.00*300000.00*5000000.00*770000.00*4230000.00*3000000.00*0.00*3000000.00*10000000.00*Y
100345*EGP*20261119*200000.00*0.00*200000.00*200000.00*75000.00*125000.00*2000000.00*230000.00*1770000.00*2000000.00*2025000.00*-25000.00*5000000.00*Y
```

| Customer | Daily CR used / limit / avail. | Monthly DR used / limit / avail. | Over |
|---|---|---|---|
| 100234 | 520,000 / 500,000 / **−20,000** | 0 / 3,000,000 / 3,000,000 | Y |
| 100345 | 0 / 200,000 / 200,000 | 2,025,000 / 2,000,000 / **−25,000** (1,950,000 migrated + 75,000) | Y |

999999 is not listed: the summary covers customers with a limit record.

---

## 2. Period report – `ALBE.CUSTOMER.LIMIT.PERIOD`

One line per customer **per day** (date range) or **per month** (month range) **that has usage**.

### Selection

| Field | Required | Meaning |
|---|---|---|
| DATE.FROM / DATE.TO | one of the two ranges | YYYYMMDD – daily rows |
| MONTH.FROM / MONTH.TO | (dates **or** months) | YYYYMM – monthly rows |
| CUSTOMER | no | blank = all customers |
| OVER.LIMIT | no | `Y` = only rows above a limit |

- A missing TO = FROM (one day / one month), and the other way round.
- At most **92 days** or **24 months** (`Constants.ENQ_MAX_DAYS`, `ENQ_MAX_MONTHS`).

### Columns

| Col | Field | Meaning |
|---|---|---|
| 1 | PERIOD.TYPE | DAILY or MONTHLY |
| 2 | PERIOD | YYYYMMDD or YYYYMM |
| 3 | CUSTOMER | customer id |
| 4 | CCY | limit currency |
| 5 / 6 / 7 | CR.LIMIT / CR.UTILISED / CR.AVAILABLE | credit (daily or monthly limit) |
| 8 / 9 / 10 | DR.LIMIT / DR.UTILISED / DR.AVAILABLE | debit |
| 11 | OVER.LIMIT | Y / N |

### How it works

1. **All customers:** **one SELECT** on EB.ALBE.UTILISED.LIMIT for the months of the range (`@ID LIKE ...-YYYYMM...` for daily, `...-YYYYMM` for monthly). Only customers who transacted have records, so idle customers cost nothing. Days outside the range are dropped.
   **One customer:** direct reads `<cust>-<day>` / `<cust>-<month>` for each day / month.
2. Each record found is read; the customer's limits are read **once** and reused for all their rows.
3. One row per record; with OVER.LIMIT = Y only rows above a limit.

### Examples

`ENQ ALBE.CUSTOMER.LIMIT.PERIOD DATE.FROM EQ 20261118 DATE.TO EQ 20261120`

```
DAILY*20261118*100234*EGP*500000.00*250000.00*250000.00*300000.00*0.00*300000.00*N
DAILY*20261119*100234*EGP*500000.00*520000.00*-20000.00*300000.00*0.00*300000.00*Y
DAILY*20261119*100345*EGP*200000.00*0.00*200000.00*200000.00*75000.00*125000.00*N
DAILY*20261120*100345*EGP*200000.00*230000.00*-30000.00*200000.00*0.00*200000.00*Y
DAILY*20261120*999999***1000.00***0.00**N
```

No line for 100234 on 20 Nov (no usage). 999999 has usage but no limit record: limits blank.

`ENQ ALBE.CUSTOMER.LIMIT.PERIOD MONTH.FROM EQ 202610 MONTH.TO EQ 202611`

```
MONTHLY*202610*100234*EGP*5000000.00*300000.00*4700000.00*3000000.00*0.00*3000000.00*N
MONTHLY*202611*100234*EGP*5000000.00*770000.00*4230000.00*3000000.00*0.00*3000000.00*N
MONTHLY*202611*100345*EGP*2000000.00*230000.00*1770000.00*2000000.00*2025000.00*-25000.00*Y
MONTHLY*202611*999999***1000.00***0.00**N
```

With `OVER.LIMIT EQ Y` only the 100345 November line remains.

---

## 3. Transaction report – `ALBE.CUSTOMER.LIMIT.TXN`

The transactions currently counted in utilisation (EB.ALBE.TXN.DETAILS.LIMIT), oldest first – for one customer, or for all customers in a date range, optionally **only the overridden ones**.

### Selection

| Field | Required | Meaning |
|---|---|---|
| CUSTOMER | customer **or** a date | blank = all customers |
| DATE.FROM / DATE.TO | | on the counted date (the date in the record id `<customer>-<YYYYMMDD>-<AAA>`) |
| OVERRIDDEN | no | `Y` = only transactions accepted with a **limit-exceed** override (daily, monthly or max CIF), read from the activity |

- Without CUSTOMER: a missing DATE.TO = today (T24 date), a missing DATE.FROM = DATE.TO; at most 92 days.
- "Limit not defined" and "exchange rate missing" are **shown** in the OVERRIDES column but do **not** make a transaction "overridden" (`Constants.LIMIT_EXCEED_OVERRIDES`).

### Columns

| Col | Field | Meaning |
|---|---|---|
| 1 | CUSTOMER | customer id |
| 2 | TXN.ID | counted transaction key – the FT / TT behind the activity if any (e.g. `FT26091ABCDE`), else the AAA id (e.g. `AAACT26091XYZ`) |
| 3 | ACCOUNT.NO | account |
| 4 | CR.DR | CR / DR |
| 5 | TXN.AMOUNT | amount counted (limit currency) |
| 6 | EFF.DATE | effective date |
| 7 | COUNTED.DATE | T24 date it was counted on (from the record id) |
| 8 | RECORD.ID | EB.ALBE.TXN.DETAILS.LIMIT id |
| 9 | OVERRIDES | EB-ALBE.* overrides accepted on the transaction (from AA.ARRANGEMENT.ACTIVITY OVERRIDE, space separated) |
| 10 | TXN.REF | FT / TT reference: AA.ARRANGEMENT.ACTIVITY TXN.CONTRACT.ID (read at report time); for transactions counted by the FT / TT version routines the FT / TT id itself |

OVERRIDES come from the transaction itself: AA.ARRANGEMENT.ACTIVITY for `AAA…` keys, FUNDS.TRANSFER / TELLER (live, then `$NAU`) for `FT…` / `TT…` keys (the activity's overrides are expected on the FT / TT – check in DEV).

### Where the overrides come from

No field is added to the (pre-approved) local tables. At report time, for each transaction listed, the service reads its **AA.ARRANGEMENT.ACTIVITY** record (the AAA id is the last part of the EB.ALBE.TXN.DETAILS.LIMIT id) – live first, `$NAU` for activities not yet authorised – and takes the `EB-ALBE.*` ids from its **OVERRIDE** field (the overrides the user accepted), each once. This also shows the maximum CIF override (raised by the other routine on the same activity). Cost: **one extra read per transaction listed**.

### How it works

1. **One SELECT** on EB.ALBE.TXN.DETAILS.LIMIT by id: one customer `@ID LIKE <cust>-...` (dates then filtered in Java); all customers `@ID LIKE ...-<YYYYMMDD>-...` for each day of the range (max 92). Rows sorted oldest first.
2. Per record: one read of EB.ALBE.TXN.DETAILS.LIMIT and one of AA.ARRANGEMENT.ACTIVITY (overrides).
3. With OVERRIDDEN = Y only rows where `isLimitOverridden()` is true are kept.

### Examples

Data for this section: 100234 – A1 CR 250,000 (no override), A2 CR 300,000 (daily limit override accepted), A3 CR 90,000 (max CIF override accepted), A4 DR 400,000 (daily override accepted, then **rejected**); 100456 – B1 CR 5,000 (only "limit not defined").

`ENQ ALBE.CUSTOMER.LIMIT.TXN DATE.FROM EQ 20261119 DATE.TO EQ 20261120`

```
100234*A1*ACC1*CR*250000.00*20261119*20261119*100234-20261119-A1*
100234*A2*ACC1*CR*300000.00*20261119*20261119*100234-20261119-A2*EB-ALBE.DAILY.LIMIT.EXCEED
100234*A3*ACC1*CR*90000.00*20261120*20261120*100234-20261120-A3*EB-ALBE.MAX.CIF.BAL.EXCEED
100456*B1*ACC9*CR*5000.00*20261120*20261120*100456-20261120-B1*EB-ALBE.CIF.LIMIT.NOT.DEFINED
```

`ENQ ALBE.CUSTOMER.LIMIT.TXN DATE.FROM EQ 20261119 DATE.TO EQ 20261120 OVERRIDDEN EQ Y` – **only overridden transactions**:

```
100234*A2*ACC1*CR*300000.00*20261119*20261119*100234-20261119-A2*EB-ALBE.DAILY.LIMIT.EXCEED
100234*A3*ACC1*CR*90000.00*20261120*20261120*100234-20261120-A3*EB-ALBE.MAX.CIF.BAL.EXCEED
```

B1 is left out (only "limit not defined"); a transaction that was overridden and then **rejected** (A4 in the test) is gone.

---

## T24 set-up

The T24 set-up for the reports – EB.API, STANDARD.SELECTION (selection fields), ENQUIRY records (column mapping and headings), EB.ERROR records – and the **user menu and access** are in [T24_CONFIGURATION.md](T24_CONFIGURATION.md), **steps 6, 14 and 15**. No table change is needed for the reports.

---

## Run examples

```
ENQ ALBE.CUSTOMER.LIMIT.SUMMARY CUSTOMER EQ 100234
ENQ ALBE.CUSTOMER.LIMIT.SUMMARY OVER.LIMIT EQ Y
ENQ ALBE.CUSTOMER.LIMIT.PERIOD  DATE.FROM EQ 20261101 DATE.TO EQ 20261130
ENQ ALBE.CUSTOMER.LIMIT.PERIOD  MONTH.FROM EQ 202601 MONTH.TO EQ 202611 OVER.LIMIT EQ Y
ENQ ALBE.CUSTOMER.LIMIT.PERIOD  MONTH.FROM EQ 202611 CUSTOMER EQ 100234
ENQ ALBE.CUSTOMER.LIMIT.TXN     CUSTOMER EQ 100234
ENQ ALBE.CUSTOMER.LIMIT.TXN     DATE.FROM EQ 20261101 DATE.TO EQ 20261130 OVERRIDDEN EQ Y
```

---

## Performance

| Report | DB work |
|---|---|
| Summary, one customer | 3 reads |
| Summary, all customers | 1 SELECT + 3 reads per customer |
| Period, all customers | 1 SELECT on UTILISED.LIMIT + 1 read per row + 1 CIF.LIMIT read per customer |
| Period, one customer | 1 read per day / month + 1 CIF.LIMIT read |
| Transactions | 1 SELECT on TXN.DETAILS + 2 reads per row (TXN.DETAILS + AA.ARRANGEMENT.ACTIVITY) |

- Range caps (92 days / 24 months) keep the online enquiries bounded; bigger extracts belong offline.
- Per-row trace lines are `detail` level: with `LOG_DETAIL_ENABLED = false` a large report writes only a few log lines.
- Enquiries never write.

---

## Tests (DEV / SIT)

| # | Test | Expected |
|---|---|---|
| R1 | Summary for a customer after an accepted daily override | daily used above limit, available negative, OVER.LIMIT Y |
| R2 | Summary after rejecting that transaction | used back below limit, OVER.LIMIT N |
| R3 | Summary `OVER.LIMIT EQ Y` | only customers over a limit |
| R4 | Period, 3 days, all customers | one line per customer per day with usage |
| R5 | Period, 2 months | one line per customer per month; monthly used includes migrated |
| R6 | Period with both DATE.FROM and MONTH.FROM | EB-ALBE.ENQ.PERIOD.MANDATORY |
| R7 | Period DATE.FROM 20260101 DATE.TO 20261120 | EB-ALBE.ENQ.RANGE.TOO.LONG |
| R8 | Transactions for a customer | counted transactions; rejected ones not shown |
| R9 | Transactions by date, no customer | all customers' counted transactions |
| R10 | Transactions `OVERRIDDEN EQ Y` | only transactions with a daily / monthly / max CIF override; OVERRIDES column filled |
| R11 | Transactions with no customer and no date | EB-ALBE.ENQ.CUSTOMER.OR.DATE |
| R12 | Date 20261131 / month 202613 | INVALID.DATE / INVALID.MONTH |

### To confirm once in DEV

| Point | How |
|---|---|
| AA.ARRANGEMENT.ACTIVITY OVERRIDE values start with the override id followed by `}` | run R10 after accepting an override; if the OVERRIDES column is empty or wrong, change `Constants.OVERRIDE_ID_END` |
| An unauthorised activity is found in `$NAU` | R10 with a transaction not yet authorised |
| `@ID LIKE` selections work on your TAFJ database | run R4, R9 |
