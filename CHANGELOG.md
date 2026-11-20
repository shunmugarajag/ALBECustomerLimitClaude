# Changelog – ALBE Customer Limit

Plain-language history of changes. Newest first. Commit ids refer to `shunmugarajag/ALBECustomerLimitClaude`, branch `main`.

## [1.0] – 21-Nov-2026 – first delivery to SIT

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
