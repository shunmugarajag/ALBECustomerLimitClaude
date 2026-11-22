# ALBE Customer Limit – Query and Gap Register

One place for every **functional query** (to the bank / BA), every **technical query** (to the DEV / T24 team) and every **gap** – raised and solved.
Details of each gap: [GAPS.md](GAPS.md). Set-up: [T24_CONFIGURATION.md](T24_CONFIGURATION.md). History: [CHANGELOG.md](../CHANGELOG.md).

Last updated: 22-Nov-2026

**Status legend:** 🔴 Open · 🟡 Partly solved / assumption in code · 🟢 Solved / decided · ⚪ Closed (not needed any more)

---

## 1. Summary

| Area | Open 🔴 | Assumption 🟡 | Solved 🟢 / Closed ⚪ |
|---|---|---|---|
| Functional queries (bank / BA) | 10 | 4 | – |
| Technical queries (DEV / T24 team) | 15 | – | – |
| Decisions taken | – | – | 24 |
| Gaps | 7 | 2 | 10 |

---

## 2. Functional queries – to the bank / BA

| # | Query | Options | What the code does today | Impact if not answered | Owner | Status |
|---|---|---|---|---|---|---|
| FQ1 | Which AA activities / products get the limit routines (daily / monthly; maximum CIF)? | list per product | the bank attaches in ACTIVITY.API; routines work on any activity | nothing is checked where not attached | Bank | 🔴 |
| FQ2 | Activity actions other than CREDIT / DEBIT (e.g. `DEPOSITS-APPLYPAYMENT-…` = money into a deposit): count as credit, debit, or not at all? | credit / debit / not counted | logged, **not counted** | deposit movements not in the limits | Bank | 🟡 |
| FQ3 | Deposit funding from the current account: count the deposit side as a credit too? | yes (same money counted twice) / no | **no** – counted once, as the current-account debit | – | Bank | 🟡 |
| FQ4 | Deposit maturity paid back to the current account (`ACCOUNTS-CREDIT-ARRANGEMENT`): count as a credit? | yes / no (needs a skip rule) | **counted** | customer's credit limit may be used by his own deposit | Bank | 🟡 |
| FQ5 | AA-internal movements (interest credited, charges debited – `TXN.SYSTEM.ID = AAAA`, INITIATION.TYPE SECONDARY / SCHEDULED): count, or only FT / TT? | all / FT-TT only / list | **all initiation types counted** (`Constants.COUNTED_INITIATION_TYPES`) | interest / charges use up the limits | Bank | 🟡 |
| FQ6 | Own-account transfer (same customer on both sides): the first activity may still show a limit override before the second side cancels it. Acceptable? | accept / needs a different design | utilisation correct (not counted); override possible on the first side | user sees an override that is not needed | Bank | 🔴 |
| FQ7 | Non-AA (classic) accounts: do any still exist? They get no activity, so no limit check. | none / list → separate routine | **not covered** | transactions on those accounts are not checked | Bank | 🔴 |
| FQ8 | Exchange rate source for limits in a foreign currency and for foreign-currency accounts in the maximum CIF check: table, field, mid / buy / sell, market, quote direction | core CURRENCY (suggested) / bank table | LCY limits use TXN.AMOUNT.LCY; otherwise override `EB-ALBE.EXCH.RATE.MISSING` and the check is skipped | foreign-currency checks skipped | Bank / Treasury | 🔴 |
| FQ9 | Partial failure (G3): if one of the limit writes fails, block the transaction or let it through (and log)? | block / let through | **let through**, logged | utilisation can be short for that transaction | Bank / team | 🔴 |
| FQ10 | EB.ALBE.CIF.LIMIT created at customer authorisation by **direct write** (no INPUTTER / AUTHORISER) – or by **OFS** for a full audit trail? | direct write / OFS zero-auth | direct write | no audit fields on the auto-created limit record | Bank / Audit | 🔴 |
| FQ11 | One-off creation of EB.ALBE.CIF.LIMIT for **existing** customers (M1) and load of the month's **migrated usage** (M2) | migration job / OFS upload | not built – see DATA_MIGRATION.md | existing customers have no limit → override "limit not defined" | Bank / Dev | 🔴 |
| FQ12 | Housekeeping of old EB.ALBE.TXN.DETAILS.LIMIT records – how long to keep? | e.g. 13 months | none | table grows | Bank | 🔴 |
| FQ13 | HLSD update for the agreed deviations (Auth routine, GEN.PARAM `CUSTOMER.LIMIT`, counted date, currency conversion, any activity, own transfer not counted, blank CCY error) | – | code follows the agreed deviations | HLSD and code differ | BA | 🔴 |
| FQ14 | Concurrent updates (G2): accept a rare wrong total (fixed by a rebuild job), or require record locking? | lock / rebuild job / both | no lock; TXN.DETAILS always correct | two parallel transactions of one customer can lose one count | Bank / team | 🔴 |

---

## 3. Technical queries – to the DEV / T24 team

| # | Query / check in DEV | Why | Status |
|---|---|---|---|
| TQ1 | Generate the **ALBE table JAR** (EB.ALBE.*) and confirm getter / setter / Table method names | the code is compiled against placeholders for these 4 tables | 🔴 |
| TQ2 | Does `EbAlbeUtilisedLimitTable.read` lock the record until commit, or is there a lock method? | G2 lost update | 🔴 |
| TQ3 | Is the `ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT` activity created when the FT / TT is **input** (not only at authorisation)? | counting at input | 🔴 |
| TQ4 | Are the overrides raised on the activity shown on the **FT / TT screen** and can be accepted there? | user sees the limit overrides | 🔴 |
| TQ5 | FT / TT delete and reversal → activity DELETE / AUTH-REV, and `postCoreTableUpdate` runs for both? | utilisation taken back | 🔴 |
| TQ6 | On an unauthorised activity: ACTIVITY.CLASS, INITIATION.TYPE, ORIG.TXN.AMT, TXN.AMOUNT.LCY, ORG.SYSTEM.DATE, TXN.CONTRACT.ID already filled? | values used at input | 🔴 |
| TQ7 | ORG.SYSTEM.DATE unchanged on re-input, DELETE and AUTH-REV? | if yes the REMARKS tag (and G5) can go | 🔴 |
| TQ8 | REMARKS tag `ALBE.LIMIT.DATE=` kept on DELETE / AUTH-REV; REMARKS maximum length (G5) | counted date | 🔴 |
| TQ9 | `Session.getCurrentVariable("!TODAY")` returns the T24 date (DEV: 01 APR 2026) | counted date fallback | 🔴 |
| TQ10 | AA.DECISION.PARAMETER UNAUTH.PROCESS: `IGNORE.TRANSACTION.ACTIVITIES` or `DEFAULT`? | COB deletes / re-creates unauthorised activities | 🔴 |
| TQ11 | Reverse and replay: do replayed activities get new AAA ids? | possible double count (key = FT id helps; AA-only activities not) | 🔴 |
| TQ12 | Override value format: `<id> FM <p1> VM <p2>` fills both `&` in the EB.OVERRIDE text | override text | 🔴 |
| TQ13 | "Record not found" message text of the generated Table `read` (Constants.RECORD_NOT_FOUND_MARKERS) | limit creation never overwrites | 🔴 |
| TQ14 | CUSTOMER.ACCOUNT filled for all customers (G8); accounts of other companies (G7) | maximum CIF total | 🔴 |
| TQ15 | Reading `$NAU` / live AA.ARRANGEMENT.ACTIVITY and FUNDS.TRANSFER from an enquiry (report overrides) | transaction report | 🔴 |

---

## 4. Queries answered – decisions

| # | Date | Query | Decision (by) |
|---|---|---|---|
| D1 | ≤20-Nov | Table structure may change? | **No** – pre-approved; no new fields or tables (user) |
| D2 | ≤20-Nov | Do unauthorised transactions count? | **Yes** – counted at input; taken back on delete / reversal (user) |
| D3 | ≤20-Nov | Limit breach: error or override? | **Acceptable override**, also for missing record / blank limit (user) |
| D4 | ≤20-Nov | Limit creation hook | **Auth routine** on CUSTOMER,ALBE.CORP; amendment never overwrites; no delete flow (user) |
| D5 | ≤20-Nov | GEN.PARAM record id | **`CUSTOMER.LIMIT`** (user) |
| D6 | ≤20-Nov | Where to keep the counted date | **AAA REMARKS** tag (user's idea) |
| D7 | ≤20-Nov | Currency | convert to the limit currency (user) |
| D8 | ≤20-Nov | Reports | summary, period (daily / monthly, ranges), transactions, over-limit and overridden filters (user) |
| D9 | 21-Nov | Date for counting | **T24 date** (ORG.SYSTEM.DATE / !TODAY), not the server clock – DEV runs months away from the clock |
| D10 | 21-Nov | Which activities | routines **generic** – the bank decides where they are attached (user) |
| D11 | 21-Nov | Credit or debit | **ACTIVITY.CLASS action** (CREDIT / DEBIT, any product line) for now (user) |
| D12 | 21-Nov | Initiation types | **all** counted (user) |
| D13 | 21-Nov | Amount when part goes to a secondary activity | **ORIG.TXN.AMT** if filled (user) |
| D14 | 21-Nov | Limit in local currency | use **TXN.AMOUNT.LCY** – no rate lookup (user) |
| D15 | 21-Nov | Transaction report | new column **TXN.REF** (FT / TT reference) |
| D16 | 22-Nov | FT / TELLER / ACCOUNT.PARAMETER routines | built, then **removed** – FT / TT on AA accounts arrive as activities (user) |
| D17 | 22-Nov | One engine for all checks | `CustomerLimitEngine` – transaction info + customer limit info (user) |
| D18 | 22-Nov | Record id suffix `.CR` / `.DR` | **dropped** – id `<customer>-<date>-<FT or AAA id>` (user) |
| D19 | 22-Nov | Own-account transfer | **not counted** (user) |
| D20 | 22-Nov | Blank CCY on EB.ALBE.CIF.LIMIT | **error**, transaction blocked (user) |
| D21 | 22-Nov | Controller packages | sub-packages **customer / activity / enquiry** (user) |
| D22 | 22-Nov | Utilisation tables: write or OFS | **direct write** (same transaction, rollback-safe); OFS only considered for CIF.LIMIT audit (FQ10) |
| D23 | 21-Nov | Sample routines' gaps | ignored – not part of this work (user) |
| D24 | 21-Nov | Migrated monthly usage | read-only `UTILISED.MIGRATED.*`, added to the monthly check only |

---

## 5. Gaps – raised and solved

| # | Raised | Gap | Status | Solution / next step |
|---|---|---|---|---|
| G1 | 21-Nov | Counted date used the server clock, not the T24 date | 🟢 Solved 21-Nov | REMARKS tag → ORG.SYSTEM.DATE → T24 TODAY |
| G2 | 21-Nov | Parallel inputs for one customer can lose an update | 🔴 Open | TQ2 (lock) / FQ14; safety net: rebuild totals from TXN.DETAILS |
| G3 | 21-Nov | Partial update when one write fails | 🔴 Open | FQ9 (block or let through) |
| G4 | 21-Nov | REMARKS tag removed on re-input → double count | 🟡 Mitigated 21-Nov | ORG.SYSTEM.DATE fallback; gone if TQ7 = yes |
| G5 | 21-Nov | REMARKS may exceed its maximum length | 🔴 Open | TQ8; gone if TQ7 = yes |
| G6 | 21-Nov | Blank CIF.LIMIT CCY compared without conversion | 🟢 Solved 22-Nov | error `EB-ALBE.CIF.LIMIT.CCY.MISSING` (D20) |
| G7 | 21-Nov | Accounts in other companies count as 0 (max CIF) | 🔴 Open | TQ14 |
| G8 | 21-Nov | Slow fallback SELECT on ACCOUNT | 🔴 Open | TQ14 |
| G9 | 21-Nov | Credit / debit by activity name | 🟢 Solved 21-Nov | ACTIVITY.CLASS action (D11) |
| G10 | 21-Nov | Teller re-input on a later day counted twice | ⚪ Closed 22-Nov | teller routine removed (D16) |
| E1 | 20-Nov | Exchange-rate lookup not connected | 🟡 Partly solved 21-Nov | LCY limits use TXN.AMOUNT.LCY (D14); rest FQ8 |
| A1–A7 | 21-Nov | Activity fields not used (ORG.SYSTEM.DATE, INITIATION.TYPE, ORIG.TXN.AMT, TXN.AMOUNT.LCY, TXN.CONTRACT.ID, direction) | 🟢 Solved 21-Nov | ActivityRequestBuilder (D9–D15) |
| A8 | 21-Nov | Deposit funding activity (APPLYPAYMENT) | 🟢 Checked 21-Nov | not counted; money counted once on the current account (FQ2/FQ3 confirm) |
| R1 | 21-Nov | `\BNK` company suffix in TXN.CONTRACT.ID | 🟢 Solved 22-Nov | root reference cut at `\` and `;` |
| R2 | 22-Nov | Same transaction counted by two routines | 🟢 Solved 22-Nov | one key (FT behind the activity); other routines removed |
| R3 | 22-Nov | Own-account transfer counted on both sides | 🟢 Solved 22-Nov | second side takes the first back, 0-amount marker (D19); FQ6 open for the override |
| R4 | 20-Nov | Strict dates: 20261131 accepted as 30-Nov | 🟢 Solved 20-Nov | strict parsing |
| R5 | 22-Nov | Code tidy-up: 5 unused imports, 1 unused constant, old "AAA id" names, `"-"` literals, 4 long methods | 🔴 Open | small clean-up, no behaviour change (offered) |
| R6 | 22-Nov | "Validate once in the controller" – validation now in CustomerLimitEngine | 🔴 Open | decide: controller or engine |

---

## 6. How to use this register

- New query or gap: add a row with the next number, date and owner; set 🔴.
- Answered: move the answer to **section 4** (decision) and set the query to 🟢; if code changes, add a CHANGELOG entry and update GAPS.md.
- Review before every release: no 🔴 in sections 2 and 3 that blocks go-live (FQ1, FQ7, FQ8, FQ11, TQ1–TQ6 are go-live blockers).
