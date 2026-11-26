# ALBE Customer Limit – Code Review Checklist

Tick every line before a change is merged to `main`. These are the project's standards (see also `CLAUDE.md` §2).

## 1. Tables and data

- [ ] **No change to the table structure** – no new fields, no new tables (EB.ALBE.GEN.PARAM, CIF.LIMIT, UTILISED.LIMIT, TXN.DETAILS.LIMIT are pre-approved).
- [ ] Only the approved fields are read / written (TXN.DETAILS: ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT).
- [ ] Record ids unchanged: CIF.LIMIT `<customer>`, UTILISED `<cust>-YYYYMMDD` / `<cust>-YYYYMM`, TXN.DETAILS `<cust>-<YYYYMMDD>-<root ref>-<arrangement>`, GEN.PARAM `CUSTOMER.LIMIT`.
- [ ] UTILISED.MIGRATED.* fields are never written.
- [ ] An existing EB.ALBE.CIF.LIMITS record is never overwritten.

## 2. Structure

- [ ] One class per T24 routine in `com.temenos.albe`, **named after its EB.API** (`ALBE.CL.ACTIVITY.LIMIT` → `AlbeClActivityLimit`, `ALBE.CL.MAX.CIF` → `AlbeClMaxCif`, `ALBE.CL.LIMIT.CREATE` → `AlbeClLimitCreate`).
- [ ] Each routine reads with `DataAccess` / `Session` / the generated Table classes itself (core Java routine style); UTILISED.LIMIT and TXN.DETAILS through their Table classes (same-commit reads, D46).
- [ ] `AlbeClMaxCif` counts nothing; only `AlbeClActivityLimit` writes UTILISED.LIMIT / TXN.DETAILS.

## 3. Java standards

- [ ] **No inner classes, no interfaces**, no nested types.
- [ ] **No `final` classes except `AlbeClConstants`**.
- [ ] **All literals in `AlbeClConstants`** (table names, ids, key names, override and error ids, log tag, formats).
- [ ] Shared lists are `List.of(...)` (read-only).
- [ ] **SonarQube for IDE** shows no issues; no unused imports, no commented-out code.
- [ ] No static mutable state (no cached session / records between calls); per-call values in local variables.
- [ ] Amounts as `BigDecimal` (never `double`), 2 decimals HALF_UP for converted amounts.
- [ ] Dates parsed strictly (`AlbeClConstants.DATE_PARSE`).
- [ ] Source files ASCII only – `LC_ALL=C grep -P '[^\x00-\x7F]' -r src` prints nothing.
- [ ] Every class has the standard header (Attached as, EB.API, Attached to, Description, Modification History).

## 4. Errors

- [ ] Every hook body in `try`; an unexpected exception is logged (`ERROR`) and does not block.
- [ ] A failed UTILISED.LIMIT / TXN.DETAILS write or delete raises **EB-ALBE.LIMIT.UPDATE.FAILED** (blocks).
- [ ] Limit breaches raise **acceptable overrides** (not errors), each on its own AAA field.

## 5. Logging

- [ ] `private static final Logger logger = LoggerFactory.getLogger(AlbeClConstants.LOGGER_NAME)`; **`logger.info` only**.
- [ ] Every line starts with `AlbeClConstants.LOG_TAG + NAME` (`ALBE.CUSTOMER.LIMIT | <Class>: <EVENT> | …`).
- [ ] One `ACTION` and one `RESULT` line per call with every number; problems as `FAILED` / `BLOCKED` / `ERROR`.

## 6. Behaviour

- [ ] Counted date = activity ORG.SYSTEM.DATE, else T24 TODAY – never `LocalDate.now()` except as the last resort.
- [ ] Credit / debit only from GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (not mapped → error at input).
- [ ] Breach only when used + amount is **above** the limit.
- [ ] Re-input: each record = used − earlier input + new amount (one write).
- [ ] Take-back only what TXN.DETAILS shows was counted (never a guess).
- [ ] Totals never below 0.
- [ ] Comparison and storage in the limit currency; take-backs use the stored amount.

## 7. Performance

- [ ] No SELECT inside a transaction routine (direct reads by key); SELECTs only in documented fallbacks.
- [ ] No repeated reads of the same record in a loop (read once, reuse).

## 8. Tests and documents

- [ ] Compiles with no errors; the unit test programs give the expected results ([UNIT_TEST_CASES.md](UNIT_TEST_CASES.md)); new behaviour has a new case.
- [ ] No stand-in / stub classes committed.
- [ ] Docs updated where affected: README, TECHNICAL_REFERENCE (change history), T24_CONFIGURATION, GAPS, TRACEABILITY, `CHANGELOG.md`, `CLAUDE.md`.
- [ ] Commit message says what changed and why; pushed to `main`.

| Reviewer | Date | Change / commit | Result |
|---|---|---|---|
| | | | |
