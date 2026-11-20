# ALBE Customer Limit – Data Migration Note

What has to be loaded **once, before go-live**, so existing customers are checked correctly from day one. No table structure change.

| # | Load | Target | Why |
|---|---|---|---|
| M1 | Limit records for existing customers | EB.ALBE.CIF.LIMIT `<customer>` | the creation routine only runs when a customer is authorised; without a record every transaction of an existing customer gets "limit not defined" and **no** limit check |
| M2 | Current month's usage from before go-live | EB.ALBE.UTILISED.LIMIT `<customer>-YYYYMM`, fields UTILISED.MIGRATED.CREDIT.LIMIT / UTILISED.MIGRATED.DEBIT.LIMIT | the monthly check adds migrated + counted; otherwise the first month starts at 0 |
| M3 | *(optional)* today's usage | EB.ALBE.UTILISED.LIMIT `<customer>-YYYYMMDD` UTILISED.CREDIT/DEBIT.LIMIT | only if go-live is mid-day with transactions already done |

---

## M1 – Limit records for existing customers

**Scope (to be decided by the bank):** all customers / only customers with accounts / only a customer group (e.g. by SECTOR or TARGET).

**Values:** the generic limits of EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT`, or bank-supplied limits per customer (a file with customer id, CCY and the five limits).

**How (choose one):**

| Option | How | Pros / cons |
|---|---|---|
| A – Data load through the version (OFS / DM tool) | one OFS message per customer on `EB.ALBE.CIF.LIMIT,AMEND` (or the bank's data-migration tool) | audit fields filled, can be authorised; slower |
| B – One-off service routine | multi-threaded job: for each customer, if EB.ALBE.CIF.LIMIT does not exist, copy GEN.PARAM `CUSTOMER.LIMIT` (same rule as the creation routine) | fast, re-runnable; records go live directly without inputter / authoriser |

Rules for either option:

- **Never overwrite** an existing EB.ALBE.CIF.LIMIT record (it may already be amended).
- Re-runnable: running it twice creates nothing new.
- Fields: CCY, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, MAX.CIF.BALANCE.

**Reconciliation**

| Check | Expected |
|---|---|
| Number of customers in scope | = number of EB.ALBE.CIF.LIMIT records created + already existing |
| Sample of 20 customers | values = GEN.PARAM (or the supplied file) |
| `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY` | one line per loaded customer |

## M2 – Migrated monthly usage

**Source:** the bank's figures of each customer's credit and debit total **in the go-live month before go-live**, in the limit currency.

**Load:** EB.ALBE.UTILISED.LIMIT record `<customer>-YYYYMM` (go-live month):

| Field | Value |
|---|---|
| UTILISED.MIGRATED.CREDIT.LIMIT | migrated credit total |
| UTILISED.MIGRATED.DEBIT.LIMIT | migrated debit total |
| UTILISED.CREDIT.LIMIT / UTILISED.DEBIT.LIMIT | leave empty – the routines add counted amounts here |

- The routines **never write** the MIGRATED fields; they only add them in the monthly check and the reports.
- Only the go-live month is needed; from the next month the module counts everything itself.

**Reconciliation**

| Check | Expected |
|---|---|
| Sum of migrated credit / debit | = source totals |
| `ENQ ALBE.CUSTOMER.LIMIT.PERIOD MONTH.FROM EQ <go-live month>` | monthly used = migrated for customers with no new activity |

## M3 – Today's usage (optional)

Only if transactions were done on the go-live day **before** the routines were attached. Load UTILISED.CREDIT.LIMIT / UTILISED.DEBIT.LIMIT of `<customer>-<go-live date>` and add the same amounts to the monthly record's UTILISED fields. No TXN.DETAILS records exist for them; if such a transaction is reversed later, the reversal subtracts from the reversal day (fallback) – note it for support.

## Transactions done before go-live

- They have no TXN.DETAILS record and no REMARKS tag.
- A **reversal** of such a transaction subtracts its amount from **today's** records (floored at 0); a deletion changes nothing.
- No action needed; support should know it ([SUPPORT_RUNBOOK.md](SUPPORT_RUNBOOK.md) 3.5).

## Cut-over sequence

| # | Step |
|---|---|
| 1 | Install the release up to step 7 of RELEASE_NOTES (routines **not** attached yet) |
| 2 | M1 – load limit records; reconcile |
| 3 | M2 – load migrated monthly usage; reconcile |
| 4 | Attach the routines (RELEASE_NOTES steps 8–9) |
| 5 | M3 only if needed |
| 6 | Smoke test (RELEASE_NOTES section 4) |

## Decisions needed from the bank

| # | Decision |
|---|---|
| 1 | Scope of M1 (all customers / with accounts / a group) |
| 2 | Generic values or per-customer file for M1 |
| 3 | Option A (OFS / DM, with audit) or B (service routine) |
| 4 | Source and cut-off of the migrated monthly totals (M2) |
