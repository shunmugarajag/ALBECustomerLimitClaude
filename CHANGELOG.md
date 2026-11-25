# Changelog – ALBE Customer Limit

Plain-language history of changes. Newest first. Commit ids refer to `shunmugarajag/ALBECustomerLimitClaude`, branch `main`.

## [1.0] – 21-Nov-2026 – first delivery to SIT

### Changed – credit / debit from a GEN.PARAM mapping (25-Nov-2026, D41)
- New EB.ALBE.GEN.PARAM record `CUSTOMER.LIMIT.ACTIVITY`: KEY.NAME = activity name (AAA ACTIVITY), KEY.VALUE = CREDIT or DEBIT. The routines read the direction from here instead of the ACTIVITY.CLASS action.
- An activity with the routine attached but not in the record → new EB.ERROR `EB-ALBE.ACTIVITY.NOT.MAPPED` (transaction blocked). No table structure change. Tests UT-O01…O07.
- Lookup written as a Java stream (filter / findFirst) instead of a for loop – same result.

### Fixed – customer limit not created in DEV (24-Nov-2026, TQ13)
- R25 reports a missing record as `T24CoreException EB-INTERNAL.ERROR … Record does not exist`. That text was not in `Constants.RECORD_NOT_FOUND_MARKERS`, so a new customer's limit read was treated as a failure and the record was not created. "RECORD DOES NOT EXIST" added; other read errors still create nothing. Tests UT-N01/N02. Logging note: our lines go to `api.log` via ALERT.API / DIAGNOSTIC.API – set `logger.DIAGNOSTIC_API.level = INFO` to see INFO lines.

### Changed – override ids follow the DEV records (24-Nov-2026)
- The 5 EB.OVERRIDE records were created in DEV as **EB-ABLE.*** (DAILY.LIMIT.EXCEED, MONTHLY.LIMIT.EXCEED, MAX.CIF.BAL.EXCEED, CIF.LIMIT.NOT.DEFINED, EXCH.RATE.MISSING). The code now uses these ids (`Constants.OVR_*`, `OVERRIDE_ID_PREFIX`); the EB.ERROR ids stay **EB-ALBE.***.

### Changed – own-account transfers counted on both sides (23-Nov-2026, FQ6 / D39)
- EB.ALBE.TXN.DETAILS.LIMIT id is now `<customer>-<YYYYMMDD>-<FT/AAA>-<arrangement id>`. The two sides of an own-account transfer are on different arrangements, so they are two records: the debit and the credit are **both checked and counted** (max CIF included). The old take-back / 0-amount marker logic is removed. Records written before this change (3-part id) are still read by the reports.

### Changed – block when the usage cannot be saved (23-Nov-2026, D37 / FQ9)
- If saving EB.ALBE.UTILISED.LIMIT or EB.ALBE.TXN.DETAILS.LIMIT fails, the transaction is now **cancelled** with the new EB.ERROR `EB-ALBE.LIMIT.UPDATE.FAILED` (before: logged and let through). New EB.ERROR record needed (T24_CONFIGURATION step 4). Tests UT-B01…B05.

### Merged – laptop edits with GitHub (23-Nov-2026)
- The laptop's table change (EB.ALBE.CIF.LIMITS, CURRENCY, MAXIMUM.OVERALL.CIF.AMOUNT) is merged with the GEN.PARAM key change below. Compiled with 0 errors against the real R25 libs and the generated table JARs; all unit tests pass. `.classpath` stays local.

### Changed – GEN.PARAM key names as set up in DEV (23-Nov-2026)
- The DEV record `CUSTOMER.LIMIT` uses **CURRENCY** and **MAXIMUM.OVERALL.CIF.BALANCE**. The code now reads these (`Constants.PARAM_KEY_*`) and copies them to CIF.LIMIT CCY and MAX.CIF.BALANCE. Before, a new customer would have got a blank CCY and every transaction would have been blocked. Tests UT-K01…K04 added.
### Changed – customer limit table EB.ALBE.CIF.LIMITS (23-Nov-2026)
- The customer limit table is now **EB.ALBE.CIF.LIMITS** (EB.TABLE.DEFINITION `ALBE.CIF.LIMITS`); code uses the generated `EbAlbeCifLimitsRecord` / `EbAlbeCifLimitsTable`.
- Field names on that table: **CURRENCY** (was CCY) and **MAXIMUM.OVERALL.CIF.AMOUNT** (was MAX.CIF.BALANCE); the other four limits are unchanged. GEN.PARAM `CUSTOMER.LIMIT` KEY.NAMEs are CURRENCY / MAXIMUM.OVERALL.CIF.BALANCE (see the entry above). The "limit not defined" override now names MAXIMUM.OVERALL.CIF.AMOUNT for a blank maximum CIF limit.
- `.classpath`: table JARs are now `lib/l3-tables/ALBE_CustomerLimitTable.jar`, `ALBE_GEN_PARAM_TABLE.jar`, `ALBE_COMM_REGOFF_TABLE.jar`.

### Fixed – SonarQube findings (23-Nov-2026)
- Constants lists (CREDIT/DEBIT_ACTIONS, COUNTED_INITIATION_TYPES, REFERENCE_CUT_CHARS, RECORD_NOT_FOUND_MARKERS, LIMIT_EXCEED_OVERRIDES) are now read-only `List.of`, not public arrays.
- Repeated log literals moved to class constants; unused imports removed; a header line read as commented-out code reworded. No logic change.

### Changed – service sub-packages (22-Nov-2026)
- `service.activity`: CustomerLimitEngine, DailyMonthlyLimitService, MaximumCifLimitService, TxnDetailsLimitRecorder, CurrencyConversionService. `service.customer`: CustomerLimitCreationService. Shared CustomerLimitReader and CustomerLimitUtilizationUpdater stay in `service`. No logic change; EB.API entries unchanged.

### Changed – report sub-packages (22-Nov-2026)
- Report classes moved: `model.enquiry` (LimitSummaryRow, LimitPeriodRow, LimitTxnRow) and `service.enquiry` (CustomerLimitEnquiryService). No logic change; EB.API entries unchanged (controllers did not move).

### Added – query and gap register (22-Nov-2026)
- `docs/QUERIES_AND_GAPS.md`: functional queries for the bank / BA, technical queries for DEV, decisions taken, gaps raised and solved.

### Changed – controller sub-packages (22-Nov-2026)
- Controllers grouped by where they are attached: `controller.customer` (CUSTOMER version), `controller.activity` (ACTIVITY.API, incl. ActivityRequestBuilder), `controller.enquiry` (NOFILE reports). **EB.API package names change** – see T24_CONFIGURATION step 5.

### Removed – 22-Nov-2026
- FUNDS.TRANSFER, TELLER and ACCOUNT.PARAMETER routines (`FundsTransferLimitController`, `TellerLimitController`, `TransactionSideReader`, `TransactionLimitHandler`, `TransactionSide`, `AccountingEntryLimitController`, `StatementEntryRequestBuilder`): FT, teller and other postings on AA accounts arrive as `ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT` activities, so the ACTIVITY.API routines cover them. Kept: one engine, counted key = FT behind the activity, own-account transfers not counted. The removed code is in the Git history.

### Changed – 22-Nov-2026
- Counted key without `.CR/.DR`: `<customer>-<date>-<reference>`. **Own-account transfers** (same customer on both sides) are not checked and not counted (user decision).
- Blank CCY on a customer's EB.ALBE.CIF.LIMITS is now an **error** (`EB-ALBE.CIF.LIMIT.CCY.MISSING`) – the transaction is blocked (G6). Before, it was compared without conversion.
- `Constants.AA_SKIP_TXN_SYSTEM_IDS` removed: the common counted key already prevents double counting.
- TELLER routine parked again (not attached; teller covered by ACCOUNT.PARAMETER or ACTIVITY.API); G10 parked with it.

### Added – limit management wherever attached (22-Nov-2026)
- One engine, `CustomerLimitEngine`: works only with transaction info + customer limit info; every routine calls it.
- New attach point for **every transaction**: `AccountingEntryLimitController` on ACCOUNT.PARAMETER (ACCT.NAU.SUBRTN check, ACCOUNTING.SUBRTN count / take back), with `StatementEntryRequestBuilder`.
- Same counted key for every routine: `<root reference>.<CR|DR>` (the FT/TT behind an AA activity if any) – a transaction seen by two routines is counted once.
- FT / TELLER routines available again as an attach point.
- Repository: one constructor for every hook (T24Context). Transaction report column 2 renamed TXN.ID.

### Parked – FUNDS.TRANSFER and TELLER routines (22-Nov-2026)
- Work on the FT / TT routines is parked: code kept, not attached, not part of release 1.0.

### Added – FUNDS.TRANSFER and TELLER routines
- `FundsTransferLimitController` and `TellerLimitController` (version routines): limit check at input and utilisation (count at input, take back on delete and authorised reversal), for **both sides** – each customer account side for its own customer; internal / cash accounts skipped. Common code in `TransactionSideReader` and `TransactionLimitHandler`.
- Counted id `<customer>-<date>-<FT/TT id>.<CR|DR>` in the existing EB.ALBE.TXN.DETAILS.LIMIT; report shows the FT/TT id and its overrides.
- `Constants.AA_SKIP_TXN_SYSTEM_IDS` to avoid counting an FT/TT twice when the AA activity routines are attached too.

### Changed – common for all activities (AAA findings A1–A7)
- Decision: the bank decides which activities get the routines; direction stays the ACTIVITY.CLASS action for now (other actions such as APPLYPAYMENT decided later).
- New `controller/ActivityRequestBuilder` used by both ACTIVITY.API routines – they can be attached to **any activity**. Counted when the activity has an amount and the **action** of its ACTIVITY.CLASS is CREDIT or DEBIT (any product line); INITIATION.TYPE checked against a list (all types counted).
- Amount = **ORIG.TXN.AMT** if filled (full original amount), else TXN.AMOUNT.
- Counted date = REMARKS tag → **ORG.SYSTEM.DATE** → **T24 TODAY** – the server clock is no longer used (G1 fixed; DEV runs on a T24 date months away from the clock). The REMARKS tag is written with the T24 date. Report defaults ("today") also use T24 TODAY.
- Limit in local currency: the activity's **TXN.AMOUNT.LCY** is used instead of a rate lookup (E1 partly solved).
- Transaction report: new column 10 **TXN.REF** (FT / TT reference from the activity).
- Transaction currency from the activity's CURRENCY (arrangement CURRENCY as fallback).

### Changed – credit / debit by activity class (G9)
- Daily/monthly and maximum CIF routines decide credit or debit from the activity's **ACTIVITY.CLASS** (exact `ACCOUNTS-CREDIT-ARRANGEMENT` / `ACCOUNTS-DEBIT-ARRANGEMENT`) instead of looking for CREDIT/DEBIT in the activity name. Other activities are skipped completely; the maximum CIF check no longer runs on non-money activities. User-defined activities linked to these classes are picked up.
- `docs/GAPS.md`: G9 closed; G1 raised (DEV T24 date differs from the server clock); new section with AAA findings A1–A6 to confirm in DEV.

### Documentation
- Release note, traceability matrix, unit test cases, support runbook, data migration note, code review checklist, this changelog.
- `CLAUDE.md` handover context (49fbda9); `docs/GAPS.md` known gaps and exchange-rate questions (ab020c7).
- T24 configuration: report set-up (step 14) and user menu and access (step 15) (a9f210a).

### Changed – keep the approved table structure
- Counted date kept in the activity **REMARKS** (`ALBE.LIMIT.DATE=YYYYMMDD`, written at first input). EB.ALBE.TXN.DETAILS.LIMIT id back to the HLSD format `<customer>-<YYYYMMDD>-<AAA id>`, read directly by key; the extra SYSTEM.DATE field is no longer used (6184b0a).
- Overridden-transactions report reads the accepted overrides from AA.ARRANGEMENT.ACTIVITY at report time; the extra OVERRIDE.IDS field was removed (ef99efb).

## [0.9] – 20-Nov-2026 – reports

### Added
- Overridden-transactions filter (OVERRIDDEN = Y) and OVERRIDES column; separate `docs/REPORTS.md` (eb3cf41).
- Period report (per day / per month over a range), OVER.LIMIT filter, transaction report for all customers in a date range; range limits 92 days / 24 months (bbf0da5).
- Limit summary and transaction report enquiries; strict date parsing (72d838f).

### Changed
- Per-record log lines moved to the switchable detail level – a large report writes a few lines instead of thousands (606f915).

## [0.8] – 20-Nov-2026 – limit creation rules

### Changed
- Generic limits record in EB.ALBE.GEN.PARAM is **`CUSTOMER.LIMIT`** (was SYSTEM) (517c8d6).
- Limit creation never overwrites an existing record, also when the read fails (2198c05).
- Limit creation runs as **Auth routine** instead of After Unau; delete flow removed (d8cb718).

### Documentation
- Technical reference and T24 configuration checklist (43ba808).

## [0.7] – 20-Nov-2026 – code quality

### Changed
- Readability: credit and debit share one path; clearer status handling; tidy imports and lines (3c4323a).
- Standard logging through `LogUtil` (ca175cb).
- Common value checks in `ValidationUtil` (5beb3dd).
- Request validated once, in the controllers (d750ac3).

## [0.6] – 20-Nov-2026 – transaction details

### Changed
- EB.ALBE.TXN.DETAILS.LIMIT read by key instead of SELECT (4212e37, 8826082 – later replaced by 6184b0a).

## [0.5] – 20-Nov-2026 – currency

### Added
- Conversion of the transaction amount and account balances to the limit currency; rate source to be configured (5299221).

## [0.4] – 20-Nov-2026 – structure

### Changed
- All constants in `Constants`, the only final class (f0b8c98).
- `LimitBreach` as its own model class (9869a66).
- MVC layers; hook classes renamed to controllers (7a794fb).
- All database access in `CustomerLimitRepository` (a31f754).
- More info logs (ac71d32).

## [0.1] – 20-Nov-2026 – initial

### Added
- Customer limit creation, daily / monthly limit check with utilisation, maximum CIF balance check, overrides (662af0d).

---

When you change the code: add a line under a new version heading with the date, what changed in plain words, and the commit id.
