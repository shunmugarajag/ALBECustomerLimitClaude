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
| FQ6 | Own transfer handling | arrangement id in the TXN.DETAILS id; both credit and debit counted and checked (incl. max CIF) | 🟢 D39 |
| FQ7 | Any non-AA accounts? | none – all accounts are AA (bank, 23-Nov) | 🟢 D34 |
| FQ8 | Exchange-rate source | daily/monthly: LCY amount from the activity (D36). Open only for max CIF: other accounts in foreign currency have no activity → no rate | 🟡 |
| FQ9 | Limit write fails – block or let through? | **block** – built: EB-ALBE.LIMIT.UPDATE.FAILED (D37); DEV: confirm the transaction rolls back (TQ18) | 🟢 D37 |
| FQ10 | CIF.LIMIT creation – direct write or OFS (audit)? | direct write | 🟢 D24 |
| FQ11 | Limits for existing customers + migrated usage | data migration loads them (DATA_MIGRATION.md) | 🟢 D29 |
| FQ12 | Keep TXN.DETAILS records how long? | forever | 🔴 |
| FQ13 | HLSD update for agreed deviations | – | 🔴 |
| FQ14 | ~~Parallel updates~~ – merged into TQ2 | – | ⚪ |
| FQ15 | GEN.PARAM `CUSTOMER.LIMIT` values (CCY + 5 limits) | DEV: EGP, all 10000; keys CURRENCY / MAXIMUM.OVERALL.CIF.BALANCE read by code | 🟢 D26 (production values from bank) |
| FQ16 | Which CUSTOMER versions create limits? | CUSTOMER,ALBE.CORP.INPUT (DEV, 27-Nov) | 🟢 D25 |
| FQ17 | Count transactions that are authorised straight away (zero-auth / STP FT, COB postings)? | count at the input stage only (UNAUTH), as today – no AUTH counting | 🟢 D35 |
| FQ18 | Joint account: whose limit counts? | no joint accounts at the bank (23-Nov) | ⚪ D38 |
| FQ19 | Should retail customer versions (e.g. CUSTOMER,ALBE.RETAIL.INPUT) also create limits? DEV has ALBE.CL.LIMIT.CREATE only on CUSTOMER,ALBE.CORP.INPUT | open | 🟡 |
| FQ20 | ACCOUNTS-CREDIT / DEBIT-ARRANGEMENT are mapped in GEN.PARAM but attached to no ACTIVITY.API condition – attach, and to which condition? | open | 🟡 |

## 2. DEV checks

| # | Check | Status |
|---|---|---|
| TQ1 | Generate ALBE table JAR | 🟢 generated (lib/l3-tables); code compiles with 0 errors against it + R25 libs, 23-Nov |
| TQ2 | Table read locks the record? If not: lock or rebuild job (bank decides) | 🔴 |
| TQ3 | Activity created at FT input? | 🟢 yes – DEV 25-Nov (status UNAUTH at FT input) |
| TQ4 | Activity overrides shown on FT? | 🟢 yes – only when the `validateRecord` routine is attached as a **Post Routine** (Validate / Pre / Record Rtn not called for FT activities), DEV 26-Nov FT26093GZC6N (D43) |
| TQ5 | FT delete / reversal reach the activity? | 🟡 reversal input → REVERSE seen (25-Nov); AUTH-REV and DELETE still to see |
| TQ6 | AAA fields filled at input | 🔴 |
| TQ7 | ORG.SYSTEM.DATE stable on change / reversal? | 🔴 |
| TQ8 | REMARKS kept + max length | ✅ not needed (D42) |
| TQ9 | `!TODAY` = T24 date | 🔴 |
| TQ10 | COB UNAUTH.PROCESS setting | 🔴 |
| TQ11 | Reverse and replay – new AAA ids? | 🔴 |
| TQ12 | Override text fills the `&` | 🟡 `ID FM p1 VM p2` shown raw; `[ID,p1,p2]` put both values into the first `&` (DEV 26-Nov) → **one parameter** `[ID,"100128 by 12000.00"]` (D46) – retest |
| TQ13 | "Record not found" text | 🟢 R25: T24CoreException EB-INTERNAL.ERROR "…Record does not exist" – added to RECORD_NOT_FOUND_MARKERS (24-Nov) |
| TQ14 | CUSTOMER.ACCOUNT complete; other companies | 🔴 |
| TQ15 | Report reads AAA / FT ($NAU, live) | ✅ not needed – reports removed (26-Nov) |
| TQ16 | Authorise a new customer via CUSTOMER,ALBE.CORP.INPUT: is EB.ALBE.CIF.LIMIT created? (proves the Auth routine runs and can write) | 🟢 yes – DEV 23-Nov |
| TQ18 | Force a save failure (e.g. lock the UTILISED.LIMIT record): the transaction must be cancelled with EB-ALBE.LIMIT.UPDATE.FAILED and nothing posted | 🔴 |
| TQ20 | Single routine (D44): is the Post Routine `validateRecord` called for **DELETE and AUTH-REV**? (log `CustomerLimitActivityController.validateRecord \| ACTION \| DELETE / REVERSAL`) – if not, keep VAL + UPD | 🔴 |
| TQ21 | TXN.EXCH.RATE meaning on a USD FT: 1 USD = rate EGP (multiply)? | 🔴 |
| TQ22 | ✅ **passed (DEV 26-Nov, FT26093B7DNL committed 3x without accepting: 'used' stayed 22,000)** – count kept only if the FT is committed (user rule: "do not count until validation passed" = committed). Single routine counts inside validateRecord: user declines the override / cancels, or T24 stops the FT later (e.g. insufficient funds) – are the UTILISED.LIMIT / TXN.DETAILS writes rolled back? (check UTILISED.LIMIT unchanged) – if not, keep VAL + UPD | 🔴 |
| TQ23 | Same commit: a TXN.DETAILS record written earlier is now found (Table read, D46) – e.g. FT re-committed after the override screen counts once | 🔴 |
| TQ19 | Daily + monthly breach on one FT: both overrides shown (one AAA field each, D43)? "by" amount = used + this − limit (FT26093GZC6N showed 13,000 for an expected 12,750 – check the log) | 🔴 |
| TQ17 | Zero-auth FT and a COB posting: does the activity pass UNAUTH (input stage)? If not, it is not counted (D35) | 🔴 |

## 3. Decisions

| # | Decision |
|---|---|
| D1 | No table change |
| D2 | Count at input, take back on delete / reversal |
| D3 | Breach = acceptable override |
| D4 | Limit creation = Auth routine, never overwrite |
| D5 | GEN.PARAM id `CUSTOMER.LIMIT` |
| D6 | ~~Counted date in REMARKS tag~~ – replaced by D42 |
| D7 | Convert to limit currency |
| D8 | ~~Reports: summary, period, transactions~~ – removed 26-Nov |
| D9 | T24 date, not server clock |
| D10 | Bank decides where routines are attached |
| D11 | Credit / debit = activity-class action |
| D12 | All initiation types counted |
| D13 | Amount = ORIG.TXN.AMT if filled |
| D14 | LCY limit → use TXN.AMOUNT.LCY |
| D15 | ~~Report column TXN.REF~~ – removed 26-Nov |
| D16 | FT / Teller / ACCOUNT.PARAMETER routines removed |
| D17 | One engine (`CustomerLimitEngine`) |
| D18 | Record id without `.CR/.DR` |
| D19 | ~~Own transfer not counted~~ – replaced by D39 |
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
| D35 | Utilisation counted at the input stage (UNAUTH) only – also for zero-auth / COB (FQ17) |
| D36 | Daily/monthly: amount in EGP (LCY) taken from the activity (TXN.AMOUNT.LCY) – no rate table (FQ8) |
| D37 | Utilisation / TXN.DETAILS write fails → block the transaction (FQ9, G3) |
| D38 | No joint accounts – first CUSTOMER of the arrangement is the only holder (FQ18) |
| D39 | TXN.DETAILS id = <cust>-<date>-<FT/AAA>-<arrangement>; own-account transfers counted and checked on both sides (FQ6) |
| D40 | ~~EB.OVERRIDE ids are EB-ABLE.* as created in DEV~~ – replaced by D51 (all EB-ALBE.*) |
| D41 | Credit / debit from GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (KEY.NAME = AAA ACTIVITY, KEY.VALUE CREDIT/DEBIT) instead of the ACTIVITY.CLASS action; activity not mapped → EB.ERROR EB-ALBE.ACTIVITY.NOT.MAPPED (25-Nov); separate table (key = activity) considered 25-Nov – approval available, **not chosen** (30–40 activities = no performance impact) |
| D42 | Counted date from the activity's ORG.SYSTEM.DATE only (else T24 TODAY); REMARKS tag + DEF routine (`defaultFieldValues`) removed – DEV 25-Nov: Record Rtn never called for FT activities, `orgSystemDate=20260403` on every hook (user approved 26-Nov) |
| D43 | **Checks as Post Routine** (VAL, MAX.CIF before UPD) – the only stage called for FT activities, and the override still reaches the FT screen (DEV 26-Nov). Override / error text **`[ID,p1,p2]`** (R25 EB.HANDLE.OVERRIDES / ERRORS); **one override per AAA field** (daily TXN.AMOUNT, monthly TXN.AMOUNT.LCY, max CIF ORIG.TXN.AMT, not defined CURRENCY, rate missing EFFECTIVE.DATE) – a field keeps only one override. Post-routine-only check controller and FT/TT version routine not needed (user approved 26-Nov) |
| D44 | **Single routine** `CustomerLimitActivityController` (EB.API `ALBE.CL.ACTIVITY.LIMIT`, one Post Routine, `validateRecord`): INPUT = check + max CIF + overrides + count, DELETE / AUTH-REV = take back, AUTH / REVERSE = nothing; ACTION log line; each record read once per call, **no cache**; save failure → setError. **Conversion of the transaction amount only from the activity:** LCY amount, else TXN.EXCH.RATE, else **EB.ERROR EB-ALBE.EXCH.RATE.MISSING (blocks)**; limit not in LCY → blocked; max CIF other-currency accounts parked (override as before). Old VAL / MAX.CIF / UPD kept until after SIT (user approved 26-Nov) |
| D45 | Review fixes (26-Nov): single routine never uses the "take back from today" fallback (a second AUTH-REV call took back another FT's amount); LCY not readable = technical failure (override, not blocked). **Max CIF: CUSTOMER.ACCOUNT empty → EB.ERROR EB-ALBE.CUSTOMER.ACCOUNT.MISSING (blocks), ACCOUNT SELECT fallback removed** (user) |
| D46 | **Override messages specific and with one `&`**: separate ids EB-ABLE.DAILY.DEBIT / DAILY.CREDIT / MONTHLY.DEBIT / MONTHLY.CREDIT.LIMIT.EXCEED; every override / error has one parameter ("100128 by 12000.00", "from USD to EGP") – DEV showed a second `&` is not filled (user chose separate ids). TXN.DETAILS read through the Table class – DataAccess did not see a write of the same commit (FT2609332FH0 counted twice with old + new routines attached) |
| D47 | **Clean rewrite** (26-Nov, user: "revisit all the logic and start from first"): one routine only – old VAL / UPD / MAX.CIF controllers, `DailyMonthlyLimitService`, `UtilisationRecords` removed (user: remove old routines); engine = `processInput` (limits → convert → earlier input by key → daily + monthly read once → checks → max CIF → count: each record = used − earlier + new, written once) and `reverse` (by key, else SELECT; nothing found → nothing); no CIF.LIMITS record → override, still counted; own transfer both sides (two activities); no lock; LogUtil **info only** (user). LCY not readable → override, not checked **and not counted** (was: counted unconverted) |
| D48 | **Simple 2-class version** (26-Nov, user: "write the logic in 1 or 2 classes instead of MVC", "do not touch existing code, separate package, like the T24 sample"; chose 2 classes + Constants): package `CustomerLimitSimple` with `CustomerLimitActivity` + `CustomerLimitCreation` + `CustomerLimitConstants`; same rules as D47; MVC code kept unchanged. Which version goes to SIT: open (user) |
| D49 | **Simple version: max CIF as its own routine** (26-Nov, user: "split max cif as separate class", chose "own routine"): `CustomerLimitMaxCif` = 2nd Post Routine on credit activities (EB.API `ALBE.CL.MAX.CIF.SIMPLE`); reads CIF.LIMITS / mapping again (no shared state); counts nothing; its error stops the whole FT |
| D50 | **One package, names from the EB.API** (26-Nov, user: "clean package except simpler version, rename as per EB.API"; answers: delete MVC, drop SIMPLE, package `com.temenos.albe`): `AlbeClActivityLimit` / `AlbeClMaxCif` / `AlbeClLimitCreate` / `AlbeClConstants`; MVC package removed (Git history keeps it) | done |
| D51 | **T24 ids and config aligned with DEV** (27-Nov, user: "fix ABLE to ALBE", "fix the code as per override, error change and t24 config"): OVERRIDE / EB.ERROR ids all `EB-ALBE.*`, one `&`, created by OFS (source GCS); `EB-ABLE.*` reversed; application name OVERRIDE (not EB.OVERRIDE); version CUSTOMER,ALBE.CORP.INPUT; old DAILY.MONTHLY EB.API reversed; ALBE.STMT.FEE.API-20260403 to be repointed; EB.API repoint only after the JAR deploy (T24 checks the class) | done in code; DEV steps open |
| D52 | **No limit record → skipped; blank limit → that check skipped** (27-Nov, user: "if customer record not there for limit can we skip validation"; answers: skip fully / skip that check only): no override, no counting, no TXN.DETAILS without a record; a blank daily / monthly / max limit skips only that check, amount still counted. Override EB-ALBE.CIF.LIMIT.NOT.DEFINED no longer raised. Also removes the gap where a foreign-currency amount was counted unconverted for a customer without a record. No fallback to the GEN.PARAM defaults (user confirmed "keep skip") | done |
| D53 | **Max CIF balance from the ACCOUNT record only** (27-Nov, user: "do not refer EB.CONTRACT.BALANCES only ACCOUNT"; Account API also declined): balance = ACCOUNT.WORKING.BALANCE. **DEV finding:** AA accounts in R26 have no balance on ACCOUNT (1000001232: ACCOUNT has no WORKING.BALANCE; EB.CONTRACT.BALANCES has ONLINE.ACTUAL.BAL 2,000, WORKING.BALANCE 1,300) → every balance reads 0, so max CIF effectively compares only the new credit with the maximum. Open item G10 | decided (limitation) |
| D28 | Reports parked for the first DEV set-up (23-Nov) – **removed from the code 26-Nov** (user) |

## 4. Gaps

| # | Gap | Status | Fix |
|---|---|---|---|
| G1 | Server clock used | 🟢 | T24 date |
| G2 | Parallel updates lose a count | 🔴 | TQ2 |
| G3 | Partial write | 🟢 | blocked (D37) |
| G4 | REMARKS tag lost | ✅ closed | D42 – no REMARKS tag |
| G5 | REMARKS too long | ✅ closed | D42 – no REMARKS tag |
| G6 | Blank limit CCY | 🟢 | error |
| G7 | Other-company accounts | 🔴 | TQ14 |
| G8 | Slow ACCOUNT select | 🔴 | TQ14 |
| G9 | Direction by activity name | 🟢 | activity class |
| G10 | Teller re-input | ⚪ | routine removed |
| E1 | No exchange rate | 🟡 | LCY done; FQ8 |
| A1–A8 | Activity fields unused | 🟢 | ActivityRequestBuilder |
| R1 | `\BNK` in reference | 🟢 | cut off |
| R2 | Double count across routines | 🟢 | one key |
| R3 | Own transfer counted | 🟢 | counted on both sides by design (D39) |
| R4 | Loose date parsing | 🟢 | strict |
| R5 | Code tidy-up | 🔴 | offered |
| R6 | Validation place | 🔴 | decide |
| DM1 | Activity that never passes the input stage (UNAUTH) is not counted | 🟡 | accepted (D35); DEV check TQ17 |
| DM2 | Only the first CUSTOMER of a joint account is checked / counted | ⚪ | no joint accounts (D38) |
| C1 | GEN.PARAM CURRENCY blank → CIF.LIMIT with blank CCY → every txn blocked | ⚪ | GEN.PARAM is mandatory config (D27) |
| C2 | CIF.LIMIT write fails → customer without limits, log only | 🟡 | logged; fix via CIF.LIMIT,AMEND (GEN.PARAM missing: D27) |
| C3 | Customer reversal also runs the Auth routine: it only creates a limit record if none exists (normally one already does) – at worst a harmless record for a reversed customer | ⚪ | accepted, no code change (D30) |
| C4 | GEN.PARAM value not a number → limit blank | ⚪ | GEN.PARAM is mandatory config (D27) |

**Go-live blockers:** FQ8, TQ2–TQ6. Dependency: data migration (D29).
