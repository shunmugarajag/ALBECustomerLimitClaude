# ALBE Customer Limit – Code Review Checklist

Tick every line before a change is merged to `main`. These are the project's standards (see also `CLAUDE.md` §2).

## 1. Tables and data

- [ ] **No change to the table structure** – no new fields, no new tables (EB.ALBE.GEN.PARAM, CIF.LIMIT, UTILISED.LIMIT, TXN.DETAILS.LIMIT are pre-approved).
- [ ] Only the approved fields are read / written (TXN.DETAILS: ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT).
- [ ] Record ids unchanged: CIF.LIMIT `<customer>`, UTILISED `<cust>-YYYYMMDD` / `<cust>-YYYYMM`, TXN.DETAILS `<cust>-<YYYYMMDD>-<AAA>`, GEN.PARAM `CUSTOMER.LIMIT`.
- [ ] UTILISED.MIGRATED.* fields are never written.
- [ ] An existing EB.ALBE.CIF.LIMITS record is never overwritten.

## 2. Structure (MVC)

- [ ] Controllers only read the T24 objects, build / validate the request, call the service and the view – **no business rules, no DB access**.
- [ ] **All DB access** (DataAccess, Session, Table classes, selects) only in `repository/CustomerLimitRepository`, each call logged.
- [ ] Business rules only in `service/`.
- [ ] Overrides and enquiry lines only built in `view/`.
- [ ] Data passed between layers in `model/` classes.

## 3. Java standards

- [ ] **No inner classes, no interfaces**, no nested types.
- [ ] **No `final` classes except `Constants`**; utility classes have a private constructor.
- [ ] **All literals in `Constants`** (table names, ids, field / key names, override and error ids, messages, selection field names, limits, formats).
- [ ] Shared lists in `Constants` are `List.of(...)` (read-only), never public arrays. A log method / event / label used more than twice is a `private static final` in the class (like `CLASS_NAME`).
- [ ] **SonarQube for IDE** (Design Studio: right-click project → SonarQube → Analyze) shows no issues; no unused imports, no commented-out code.
- [ ] No static mutable state (no cached session / records between calls).
- [ ] Amounts as `BigDecimal` (never `double`), 2 decimals HALF_UP for converted amounts.
- [ ] Dates parsed with `ValidationUtil.toDate` / `toMonth` (strict).
- [ ] Lines ≤ 120 characters; imports sorted (java.* first).
- [ ] `'þ'` / `'ý'` kept as ASCII escapes – `LC_ALL=C grep -P '[^\x00-\x7F]' -r src` prints nothing.
- [ ] Every class has the standard header (Description, Modification History with date).

## 4. Validation and errors

- [ ] Request validated **once**, in the controller (`CustomerLimitRequestValidator` / `CustomerLimitEnquiryValidator`); services do not repeat the checks.
- [ ] Common value checks through `ValidationUtil` (no own isBlank / parse helpers).
- [ ] Every controller body in `try`; unexpected exceptions logged with `LogUtil.error(..., e)`.
- [ ] No empty `catch` blocks.
- [ ] Limit breaches raise **acceptable overrides** (not errors); enquiry selection problems raise EB.ERROR ids from `Constants`.

## 5. Logging

- [ ] Logging only through `LogUtil` – no own logger.
- [ ] Each class has `CLASS_NAME`; calls use `LogUtil.info / error / detail(CLASS_NAME, "method", "EVENT", details)`.
- [ ] Events from the standard list (START END STEP SKIP ACTION · OK INVALID BREACH NOT DEFINED OVERRIDE · CALC UPDATED ADJUSTED · READ WRITE DELETE SELECT FOUND NOT FOUND NEW CREATED WRITTEN DELETED · FAILED ERROR NO RATE NOT CONFIGURED).
- [ ] Per-record / per-row trace lines use `detail` (switchable).
- [ ] Every DB write result is logged (`WRITTEN` / `FAILED`).

## 6. Behaviour

- [ ] Counted date taken from the activity REMARKS tag (`ALBE.LIMIT.DATE=`), else ORG.SYSTEM.DATE / T24 TODAY – never `LocalDate.now()` except as the logged last resort.
- [ ] Every routine only builds transaction info and calls `CustomerLimitEngine` (no check / count logic in controllers); counted key from `CustomerLimitEngine.transactionKey`.
- [ ] Activity mapping only through `ActivityRequestBuilder` (amount, direction, initiation type, date) – no activity rules in the controllers.
- [ ] Breach only when used + amount is **above** the limit.
- [ ] Re-input: the AAA's earlier input is reversed / excluded first.
- [ ] Totals never below 0.
- [ ] Comparison and storage in the limit currency; reversals use the stored amount.
- [ ] A technical failure does not block the transaction (unless G3 is decided otherwise).

## 7. Performance

- [ ] No SELECT inside a transaction routine (direct reads by key); SELECTs only in enquiries / documented fallbacks.
- [ ] No repeated reads of the same record in a loop (read once, reuse).
- [ ] Enquiries filter in the SELECT where possible and respect the range limits.

## 8. Tests and documents

- [ ] Compiles with no errors; the unit test programs give the expected results ([UNIT_TEST_CASES.md](UNIT_TEST_CASES.md)); new behaviour has a new case.
- [ ] No stand-in / stub classes committed.
- [ ] Docs updated where affected: README, TECHNICAL_REFERENCE (change history), T24_CONFIGURATION, REPORTS, GAPS, TRACEABILITY, `CHANGELOG.md`, `CLAUDE.md`.
- [ ] Commit message says what changed and why; pushed to `main`.

| Reviewer | Date | Change / commit | Result |
|---|---|---|---|
| | | | |
