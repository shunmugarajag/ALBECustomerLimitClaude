# Query and Gap Register

🔴 open · 🟡 assumed in code · 🟢 solved · ⚪ closed — updated 23-Nov-2026
Details: [GAPS.md](GAPS.md)

## 1. Bank / BA queries

| # | Query | Code today | Status |
|---|---|---|---|
| FQ1 | Which activities / products get the routines? | all account products (current + savings), ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT | 🟢 D31 |
| FQ2 | Other actions (e.g. APPLYPAYMENT) – count? | not counted | 🟡 bank to confirm (23-Nov) |
| FQ3 | Deposit funding – count deposit side too? | depends on the activity: counted only where the routine is attached (D31: account products → deposit side not counted) | 🟢 D32 |
| FQ4 | Deposit payout to current account – count? | yes – account credit activity has the routine (D33) | 🟢 D33 |
| FQ5 | Interest / charges (AA-internal) – count? | yes, when posted by an activity that has the routine (D33) | 🟢 D33 |
| FQ6 | Own transfer: first side may show an override – OK? | override possible | 🔴 |
| FQ7 | Any non-AA accounts? | none – all accounts are AA (bank, 23-Nov) | 🟢 D34 |
| FQ8 | Exchange-rate source (table, field, rate type) | LCY only | 🔴 |
| FQ9 | Limit write fails – block or let through? | let through | 🔴 |
| FQ10 | CIF.LIMIT creation – direct write or OFS (audit)? | direct write | 🟢 D24 |
| FQ11 | Limits for existing customers + migrated usage | data migration loads them (DATA_MIGRATION.md) | 🟢 D29 |
| FQ12 | Keep TXN.DETAILS records how long? | forever | 🔴 |
| FQ13 | HLSD update for agreed deviations | – | 🔴 |
| FQ14 | ~~Parallel updates~~ – merged into TQ2 | – | ⚪ |
| FQ15 | GEN.PARAM `CUSTOMER.LIMIT` values (CCY + 5 limits) | DEV: EGP, all 10000; keys CURRENCY / MAXIMUM.OVERALL.CIF.BALANCE read by code | 🟢 D26 (production values from bank) |
| FQ16 | Which CUSTOMER versions create limits? | CUSTOMER,ALBE.CORP | 🟢 D25 |
| FQ17 | Count transactions that are authorised straight away (zero-auth / STP FT, COB interest / charges / standing orders)? | not counted if T24 never shows them as UNAUTH (DM1) | 🔴 |
| FQ18 | Joint account: whose limit counts – first holder or every holder? | first holder only (DM2) | 🔴 |

## 2. DEV checks

| # | Check | Status |
|---|---|---|
| TQ1 | Generate ALBE table JAR | 🟢 generated (lib/l3-tables); code compiles with 0 errors against it + R25 libs, 23-Nov |
| TQ2 | Table read locks the record? If not: lock or rebuild job (bank decides) | 🔴 |
| TQ3 | Activity created at FT input? | 🔴 |
| TQ4 | Activity overrides shown on FT? | 🔴 |
| TQ5 | FT delete / reversal reach the activity? | 🔴 |
| TQ6 | AAA fields filled at input | 🔴 |
| TQ7 | ORG.SYSTEM.DATE stable on change / reversal? | 🔴 |
| TQ8 | REMARKS kept + max length | 🔴 |
| TQ9 | `!TODAY` = T24 date | 🔴 |
| TQ10 | COB UNAUTH.PROCESS setting | 🔴 |
| TQ11 | Reverse and replay – new AAA ids? | 🔴 |
| TQ12 | Override text fills both `&` | 🔴 |
| TQ13 | "Record not found" text | 🔴 |
| TQ14 | CUSTOMER.ACCOUNT complete; other companies | 🔴 |
| TQ15 | Report reads AAA / FT ($NAU, live) | ⏸ parked (D28) |
| TQ16 | Authorise a new customer via CUSTOMER,ALBE.CORP: is EB.ALBE.CIF.LIMIT created? (proves the Auth routine runs and can write) | 🟢 yes – DEV 23-Nov |

## 3. Decisions

| # | Decision |
|---|---|
| D1 | No table change |
| D2 | Count at input, take back on delete / reversal |
| D3 | Breach = acceptable override |
| D4 | Limit creation = Auth routine, never overwrite |
| D5 | GEN.PARAM id `CUSTOMER.LIMIT` |
| D6 | Counted date in REMARKS tag |
| D7 | Convert to limit currency |
| D8 | Reports: summary, period, transactions |
| D9 | T24 date, not server clock |
| D10 | Bank decides where routines are attached |
| D11 | Credit / debit = activity-class action |
| D12 | All initiation types counted |
| D13 | Amount = ORIG.TXN.AMT if filled |
| D14 | LCY limit → use TXN.AMOUNT.LCY |
| D15 | Report column TXN.REF |
| D16 | FT / Teller / ACCOUNT.PARAMETER routines removed |
| D17 | One engine (`CustomerLimitEngine`) |
| D18 | Record id without `.CR/.DR` |
| D19 | Own transfer not counted |
| D20 | Blank limit CCY = error |
| D21 | Controller sub-packages |
| D22 | Utilisation = direct write |
| D23 | Migrated usage: monthly check only |
| D24 | CIF.LIMIT created by direct write (no OFS) |
| D25 | Limit creation only on VERSION CUSTOMER,ALBE.CORP |
| D26 | Code follows the GEN.PARAM key names as set up in DEV |
| D27 | GEN.PARAM `CUSTOMER.LIMIT` is mandatory config, complete and valid – no code checks |
| D29 | Existing customers' CIF.LIMIT (and migrated monthly usage) loaded by data migration – no code |
| D30 | C3 accepted: customer reversal may leave an unused limit record – no code change |
| D31 | Daily/monthly routines on all account products (FQ1) |
| D32 | Deposit side counted only if the bank attaches the routine to that activity (FQ3) |
| D33 | Rule: every activity that has the routine attached is counted (FQ3/FQ4/FQ5) |
| D34 | No non-AA accounts – ACTIVITY.API covers every account (FQ7) |
| D28 | Reports parked for the first DEV set-up (23-Nov): no ENQUIRY / STANDARD.SELECTION / report EB.API yet; code stays in the JAR |

## 4. Gaps

| # | Gap | Status | Fix |
|---|---|---|---|
| G1 | Server clock used | 🟢 | T24 date |
| G2 | Parallel updates lose a count | 🔴 | TQ2 |
| G3 | Partial write | 🔴 | FQ9 |
| G4 | REMARKS tag lost | 🟡 | ORG.SYSTEM.DATE fallback |
| G5 | REMARKS too long | 🔴 | TQ8 |
| G6 | Blank limit CCY | 🟢 | error |
| G7 | Other-company accounts | 🔴 | TQ14 |
| G8 | Slow ACCOUNT select | 🔴 | TQ14 |
| G9 | Direction by activity name | 🟢 | activity class |
| G10 | Teller re-input | ⚪ | routine removed |
| E1 | No exchange rate | 🟡 | LCY done; FQ8 |
| A1–A8 | Activity fields unused | 🟢 | ActivityRequestBuilder |
| R1 | `\BNK` in reference | 🟢 | cut off |
| R2 | Double count across routines | 🟢 | one key |
| R3 | Own transfer counted | 🟢 | not counted |
| R4 | Loose date parsing | 🟢 | strict |
| R5 | Code tidy-up | 🔴 | offered |
| R6 | Validation place | 🔴 | decide |
| DM1 | Activity that reaches ACTIVITY.API only as AUTH (zero-auth / STP / COB) is never checked or counted | 🔴 | FQ17 + DEV test; fix: count at AUTH if no TXN.DETAILS |
| DM2 | Only the first CUSTOMER of a joint account is checked / counted | 🔴 | FQ18 |
| C1 | GEN.PARAM CURRENCY blank → CIF.LIMIT with blank CCY → every txn blocked | ⚪ | GEN.PARAM is mandatory config (D27) |
| C2 | CIF.LIMIT write fails → customer without limits, log only | 🟡 | logged; fix via CIF.LIMIT,AMEND (GEN.PARAM missing: D27) |
| C3 | Customer reversal also runs the Auth routine: it only creates a limit record if none exists (normally one already does) – at worst a harmless record for a reversed customer | ⚪ | accepted, no code change (D30) |
| C4 | GEN.PARAM value not a number → limit blank | ⚪ | GEN.PARAM is mandatory config (D27) |

**Go-live blockers:** FQ8, TQ2–TQ6. Dependency: data migration (D29).
