# ALBE Customer Limit – Go-Live Checklist (T24 R25 / TAFJ)

For the T24 developer and the bank's go-live team. Tick each box when done.
Target set-up = **decision D44**: one ACTIVITY.API Post Routine `ALBE.CL.ACTIVITY.LIMIT` per mapped activity per product. Only ACTIVITY.API is used – no FT / TELLER version routines.
Details of every step: `docs/T24_CONFIGURATION.md` (step numbers in brackets, e.g. [§8]).

`OPEN` = not decided or not tested yet. The id (FQ / TQ / G / E / M) points to `docs/QUERIES_AND_GAPS.md`, `docs/GAPS.md` or `docs/DATA_MIGRATION.md`.

---

## 1. Before go-live – decisions and data still needed

### 1.1 From the bank / BA

| Done | Id | What is needed | Today in the code | Status |
|---|---|---|---|---|
| [ ] | FQ15 | Production values for GEN.PARAM `CUSTOMER.LIMIT` (CURRENCY + 5 limits) | DEV: EGP, all 10000 | OPEN (bank) |
| [ ] | FQ1 / D31 | Final list of products and activities that get the routine (fill the worksheet, section 7) | all account products (current + savings), ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT | decided – confirm list |
| [ ] | FQ2 | Other actions (e.g. APPLYPAYMENT) – count or not? | not counted | OPEN (assumed) |
| [ ] | FQ8 / E1 | Exchange-rate source for **max CIF** when the customer has accounts in another currency (table, field, mid/buy/sell, market, quote direction) | override EB-ABLE.EXCH.RATE.MISSING, max CIF check skipped | OPEN (parked) – **listed as go-live blocker** |
| [ ] | FQ12 | How long to keep EB.ALBE.TXN.DETAILS.LIMIT records (housekeeping) | kept forever | OPEN |
| [ ] | FQ13 | HLSD update for the agreed deviations (Auth routine, GEN.PARAM `CUSTOMER.LIMIT`, counted date, currency conversion) | – | OPEN (BA) |
| [ ] | G2 / TQ2 | Parallel inputs for one customer can lose a count – lock or rebuild job? | no lock | OPEN – **go-live blocker** |
| [ ] | G7 / TQ14 | Multi-company: do customers hold accounts in more than one company? (max CIF counts other-company accounts as 0) | current company only | OPEN |
| [ ] | TQ14 | Is CUSTOMER.ACCOUNT always maintained? (empty → max CIF blocks the credit, D45) | no SELECT fallback any more | OPEN (data check before go-live) |
| [ ] | M1 | Scope of the limit load (all customers / with accounts / a group) | – | OPEN |
| [ ] | M1 | Generic values or a per-customer file | – | OPEN |
| [ ] | M1 | Load method: A (OFS / DM, with audit) or B (service routine) | – | OPEN |
| [ ] | M2 | Source and cut-off of the migrated monthly totals | – | OPEN |
| [ ] | §15.2 | Final role list (who can amend / view limits) | suggested roles only | OPEN (bank decides) |

### 1.2 DEV / SIT tests that must pass first

| Done | Id | Test | Status |
|---|---|---|---|
| [ ] | **TQ20** | Single routine: is the Post Routine `validateRecord` called for **DELETE and AUTH-REV**? Log `CustomerLimitActivityController.validateRecord \| ACTION \| DELETE` / `REVERSAL`. **If not → go live with the fallback** (VAL + MAX.CIF + UPD). | OPEN – decides the set-up |
| [ ] | **TQ22** | Single routine counts in `validateRecord`: decline the override / cancel the FT – is UTILISED.LIMIT unchanged (our writes rolled back)? **If not → fallback set-up.** | OPEN |
| [ ] | TQ5 | FT delete and FT reversal reach the activity (DELETE / AUTH-REV) | OPEN (REVERSE seen; DELETE, AUTH-REV not yet) – **go-live blocker** |
| [ ] | TQ6 | AAA fields filled at input (ORIG.TXN.AMT, TXN.AMOUNT.LCY) | OPEN – **go-live blocker** |
| [ ] | TQ7 | ORG.SYSTEM.DATE stays the same on re-input / DELETE / AUTH-REV | OPEN |
| [ ] | TQ9 | `!TODAY` = T24 date | OPEN |
| [ ] | TQ10 | COB setting AA.DECISION.PARAMETER UNAUTH.PROCESS (IGNORE.TRANSACTION.ACTIVITIES or DEFAULT) | OPEN |
| [ ] | TQ11 | Reverse and replay – new AAA ids? | OPEN |
| [ ] | TQ12 / TQ19 | Daily + monthly breach on one FT: both overrides shown, both `&` filled, "by" = used + this − limit (FT26093GZC6N showed 13,000 vs expected 12,750) | OPEN – retest with new JAR |
| [ ] | TQ17 | Zero-auth FT and COB posting pass UNAUTH (else not counted, D35) | OPEN |
| [ ] | TQ18 | Forced save failure → EB-ALBE.LIMIT.UPDATE.FAILED, nothing posted | OPEN |
| [ ] | TQ21 | TXN.EXCH.RATE on a USD FT: 1 USD = rate EGP (multiply)? | OPEN |
| [ ] | – | Full SIT run: T24_CONFIGURATION §13, §15.3, UNIT_TEST_CASES.md | [ ] |

---

## 2. Objects to install – in this order

Rule: **routines are attached last** (rows 2.8 and 2.9), so nothing runs before the records it needs exist. Package the T24 records in a DL.DEFINE (or the team's usual package).

### 2.1 JAR – build, deploy, restart [§5]

| Done | Step | Check |
|---|---|---|
| [ ] | Tag the packaged commit on `main` (repo `shunmugarajag/ALBECustomerLimitClaude`) | tag noted: ______ |
| [ ] | `Constants.LOG_DETAIL_ENABLED = false` (default) in the build | – |
| [ ] | `mvn clean package` → `target/ALBE_CustomerLimit.jar` | JAR exists |
| [ ] | **JAR name is exactly `ALBE_CustomerLimit.jar`** – JBoss `modules/com/temenos/t24/main/module.xml` loads `./localLib/ALBE_CustomerLimit.jar` by name. Missing / renamed JAR = **all T24 deployments fail** (DEV outage 25-Nov) | name checked |
| [ ] | Confirm the production target folder (pom default `deploy.dir` = `/srv/Temenos/T24/bnk/ALBElib`, `deploy.host` = DEV) and that it is the folder module.xml loads from | folder: ______ |
| [ ] | Deploy (`mvn clean deploy` or the bank's production process): copy as `.jar.new`, then rename – the JAR is never missing; old `ALBECustomerLimit*.jar` copies removed | `ls -l` shows the new JAR |
| [ ] | Generated table JARs on the runtime classpath: `ALBE_CustomerLimitTable.jar`, `ALBE_GEN_PARAM_TABLE.jar` (+ `ALBE_COMM_REGOFF_TABLE.jar`) [§2] | no class-not-found |
| [ ] | Restart T24 / JBoss (`mvn antrun:run@restart-t24` in DEV: kill -15, start `standalone.sh -c Transact.xml`, wait for `WFLYSRV0025`) | started; no `.failed` deployments |
| [ ] | On the server change **no file except the JAR** | – |

### 2.2 Tables [§1, §2]

| Done | Table | Type | Check |
|---|---|---|---|
| [ ] | EB.ALBE.GEN.PARAM (exists) | H (or U) | FILE.CONTROL ok |
| [ ] | EB.ALBE.CIF.LIMITS (EB.TABLE.DEFINITION `ALBE.CIF.LIMITS`) – CURRENCY, DAILY/MONTHLY CREDIT/DEBIT.LIMIT, MAXIMUM.OVERALL.CIF.AMOUNT | H | FILE.CONTROL ok |
| [ ] | EB.ALBE.UTILISED.LIMIT (exists) | L | FILE.CONTROL ok |
| [ ] | EB.ALBE.TXN.DETAILS.LIMIT (exists, no change) | L | FILE.CONTROL ok |
| [ ] | Structure = approved structure – **no new fields** | – | compared |

### 2.3 EB.ERROR (blocking) [§4]

| Done | Id | Raised when |
|---|---|---|
| [ ] | EB-ALBE.CIF.LIMIT.CCY.MISSING | customer's CIF.LIMITS CURRENCY blank |
| [ ] | EB-ALBE.LIMIT.UPDATE.FAILED | utilisation / TXN.DETAILS save failed (D37) |
| [ ] | EB-ALBE.ACTIVITY.NOT.MAPPED | activity not in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (D41) |
| [ ] | **EB-ALBE.CUSTOMER.ACCOUNT.MISSING** *(new, D45)* | "No accounts found in CUSTOMER.ACCOUNT for Customer &, transaction not allowed" – max CIF check with an empty CUSTOMER.ACCOUNT |
| [ ] | **EB-ALBE.EXCH.RATE.MISSING** *(new, D44)* | "Exchange rate not available from & to &, transaction not allowed" – no LCY amount and no TXN.EXCH.RATE, or limit not in LCY |

### 2.4 EB.OVERRIDE (all acceptable) [§4]

| Done | Id | AAA field it is raised on |
|---|---|---|
| [ ] | EB-ABLE.DAILY.LIMIT.EXCEED – "Daily credit/debit exceed for Customer & by &" | TXN.AMOUNT |
| [ ] | EB-ABLE.MONTHLY.LIMIT.EXCEED – "Monthly credit/debit exceed for Customer & by &" | TXN.AMOUNT.LCY |
| [ ] | EB-ABLE.MAX.CIF.BAL.EXCEED – "Overall credit/debit exceed for Customer & by &" | ORIG.TXN.AMT |
| [ ] | EB-ABLE.CIF.LIMIT.NOT.DEFINED – "Customer limit not defined for Customer &" | CURRENCY |
| [ ] | EB-ABLE.EXCH.RATE.MISSING – "Exchange rate not available from & to &" (max CIF only) | EFFECTIVE.DATE |

Note: override ids are **EB-ABLE.*** (as created in DEV, D40); error ids are **EB-ALBE.***.

### 2.5 EB.API [§6]

| Done | EB.API id | Class (`com.temenos.ALBE.CustomerLimit.controller…`) | Method | Use |
|---|---|---|---|---|
| [ ] | **ALBE.CL.ACTIVITY.LIMIT** | `activity.CustomerLimitActivityController` | validateRecord | **target** (D44) |
| [ ] | ALBE.CL.DAILY.MONTHLY.VAL | `activity.DailyMonthlyLimitController` | validateRecord | fallback / rollback |
| [ ] | ALBE.CL.MAX.CIF | `activity.MaximumCifLimitController` | validateRecord | fallback / rollback (credit) |
| [ ] | ALBE.CL.DAILY.MONTHLY.UPD | `activity.DailyMonthlyLimitController` | postCoreTableUpdate | fallback / rollback |
| [ ] | ALBE.CL.LIMIT.CREATE | `customer.CustomerLimitCreationController` | postUpdateRequest | customer version |
| [ ] | ALBE.CL.DAILY.MONTHLY.DEF – **not installed** (no longer used, D42) | – | – | – |

SOURCE.TYPE = JAVA, JAVA.PACKAGE / JAVA.CLASS / JAVA.METHOD (confirm field names on R25).

### 2.6 GEN.PARAM records [§3, §3a]

| Done | Record | Content | Check |
|---|---|---|---|
| [ ] | `CUSTOMER.LIMIT` | KEY.NAME exactly: CURRENCY, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, MAXIMUM.OVERALL.CIF.BALANCE – values from the bank (FQ15 OPEN) | authorised; CURRENCY not blank (blank → every txn of new customers blocked) |
| [ ] | `CUSTOMER.LIMIT.ACTIVITY` (mandatory, D41) | one line per attached activity: KEY.NAME = AAA ACTIVITY, KEY.VALUE = CREDIT / DEBIT | every activity of the worksheet (section 7) is in it |

Changing `CUSTOMER.LIMIT` later affects **new** customers only.

### 2.7 Versions and menus [§9, §15]

| Done | Object | Check |
|---|---|---|
| [ ] | VERSION EB.ALBE.CIF.LIMITS,AMEND (≥ 1 authoriser) | opens |
| [ ] | VERSION EB.ALBE.CIF.LIMITS,VIEW (optional) | opens, no input |
| [ ] | VERSION EB.ALBE.GEN.PARAM,LIMIT (administrators only) | opens |
| [ ] | Menu "Customer Limits" (Maintain) | visible for the roles |
| [ ] | Security / role access (§15.2; role list OPEN) | front office cannot open ,AMEND |

### 2.8 ACTIVITY.API – per product and activity [§8]  *(after data load M1/M2 – see section 3)*

| Done | Step |
|---|---|
| [ ] | TQ20 passed? **Yes** → target set-up below. **No** → fallback set-up below. |
| [ ] | **Target (D44):** Post Routine.1 = `ALBE.CL.ACTIVITY.LIMIT`; every other routine field blank |
| [ ] | **Fallback:** credit = Post Routine 1 `ALBE.CL.DAILY.MONTHLY.VAL`, 2 `ALBE.CL.MAX.CIF`, 3 `ALBE.CL.DAILY.MONTHLY.UPD`; debit = 1 VAL, 2 UPD (checks **before** UPD) |
| [ ] | Pre Routine, Record Rtn, Validate Rtn, Pre Validate Rtn **blank** (not called for FT activities) |
| [ ] | No routine on FUNDS.TRANSFER / TELLER versions or ACCOUNT.PARAMETER |
| [ ] | Every activity with the routine is in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (else EB-ALBE.ACTIVITY.NOT.MAPPED blocks) |
| [ ] | Proof and publish each product (AA.PRODUCT.DESIGNER) |
| [ ] | Fill the worksheet (section 7) for every product / activity |

### 2.9 VERSION CUSTOMER,ALBE.CORP [§7]

| Done | Step | Check |
|---|---|---|
| [ ] | AUTH.ROUTINE = `ALBE.CL.LIMIT.CREATE` (`@` prefix if needed) | – |
| [ ] | AFTER.UNAU.RTN – nothing for this routine | – |
| [ ] | Only this version creates limits (D25). Other CUSTOMER versions get **no** limit record | accepted by bank |

### 2.10 Logging level [§10]

| Done | Step |
|---|---|
| [ ] | For go-live day / hypercare: server admin sets `logger.DIAGNOSTIC_API.level = INFO` in `TAFJ/conf/TAFJTrace.properties` + restart, to see ACTION / RESULT lines in `TAFJ/log/api.log` (bank's own change – the deploy itself touches only the JAR) |
| [ ] | ERROR / FAILED lines are always written (ALERT.API) |

---

## 3. Data load (cut-over)

| Done | # | Step | Reconciliation |
|---|---|---|---|
| [ ] | 1 | Install everything up to 2.7 – **routines not attached yet** | – |
| [ ] | 2 | **M1** – EB.ALBE.CIF.LIMITS for existing customers (scope, values, method OPEN – section 1.1). Never overwrite an existing record; re-runnable | customers in scope = created + already existing; sample of 20 = GEN.PARAM / file; `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY` one line per customer |
| [ ] | 3 | **M2** – go-live month record `<customer>-YYYYMM`: UTILISED.MIGRATED.CREDIT.LIMIT / UTILISED.MIGRATED.DEBIT.LIMIT (limit currency). Leave UTILISED.CREDIT/DEBIT.LIMIT empty (source / cut-off OPEN) | sum = source totals; `ENQ ALBE.CUSTOMER.LIMIT.PERIOD MONTH.FROM EQ <go-live month>` |
| [ ] | 4 | Attach routines: 2.8 ACTIVITY.API, then 2.9 CUSTOMER,ALBE.CORP | – |
| [ ] | 5 | **M3** (optional) – only if transactions were done on go-live day before step 4: load today's daily record + add to monthly UTILISED fields | – |
| [ ] | 6 | Smoke test (section 4) | all pass |

Remember: transactions from before go-live have no TXN.DETAILS record – a later reversal subtracts from **today's** records (floored at 0); a deletion changes nothing.

---

## 4. Smoke test after install

Needs DIAGNOSTIC.API at INFO to see ACTION / RESULT lines. Find one transaction: `grep "ALBE.CUSTOMER.LIMIT" api.log | grep <FT or AAA id>`.

| Done | # | Test | Expected | api.log line to look for |
|---|---|---|---|---|
| [ ] | 1 | Authorise a new test customer via CUSTOMER,ALBE.CORP | EB.ALBE.CIF.LIMITS created with the GEN.PARAM values | `CustomerLimitCreationController.postUpdateRequest \| END \| … created=true` |
| [ ] | 2 | Amend + re-authorise that customer | limit record unchanged | `CustomerLimitCreationService.create \| SKIP \| … already exists` |
| [ ] | 3 | Credit below the limit (FT on an AA account) | no override; daily + monthly used updated; TXN.DETAILS `<cust>-<date>-<FT>-<arrangement>` | `CustomerLimitActivityController.validateRecord \| ACTION \| INPUT`, then `RESULT \| COUNTED …` |
| [ ] | 4 | Credit above the daily limit | EB-ABLE.DAILY.LIMIT.EXCEED with customer and amount over (both `&` filled) | `RESULT \| CHECK daily/monthly … EXCEEDED by …`, `OVERRIDE \| … sent=…` |
| [ ] | 5 | Delete the unauthorised transaction of test 4 | used back to the value after test 3; TXN.DETAILS deleted | `ACTION \| DELETE` |
| [ ] | 6 | Authorise test 3 | no change | `ACTION \| AUTHORISE` |
| [ ] | 7 | Reverse the authorised test 3 (input + authorise) | amount taken back from the original day / month | `ACTION \| REVERSAL INPUT`, then `ACTION \| REVERSAL` |
| [ ] | 8 | Debit over the daily debit limit | EB-ABLE.DAILY.LIMIT.EXCEED | `RESULT \| CHECK …` |
| [ ] | 9 | Activity with the routine but not in `CUSTOMER.LIMIT.ACTIVITY` (test product only) | error EB-ALBE.ACTIVITY.NOT.MAPPED, blocked | error line (NOT MAPPED) |
| [ ] | 10 | `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY CUSTOMER EQ <test customer>` | numbers match tests 3–7 | – |
| [ ] | 11 | Search for errors | none | `grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR\|FAILED) \|" api.log` |

If tests 5 or 7 show no `ACTION | DELETE` / `REVERSAL` line → TQ20 failed → switch to the fallback (section 5, step R1).

---

## 5. Rollback

### 5.1 Single routine not working → fallback (no redeploy)

| Done | # | Step |
|---|---|---|
| [ ] | R1 | In each ACTIVITY.API: replace `ALBE.CL.ACTIVITY.LIMIT` by Post Routines VAL (+ MAX.CIF for credit) + UPD (section 2.8 fallback) |
| [ ] | R2 | Proof and publish the products |
| [ ] | R3 | Repeat smoke tests 3–7 (log lines now from `DailyMonthlyLimitController`) |

### 5.2 Full rollback (remove the module)

Remove the routines first so nothing new is counted. Data already written does no harm.

| Done | # | Step |
|---|---|---|
| [ ] | 1 | Remove the routines from all ACTIVITY.API conditions; proof and publish |
| [ ] | 2 | Clear AUTH.ROUTINE `ALBE.CL.LIMIT.CREATE` on CUSTOMER,ALBE.CORP |
| [ ] | 3 | Remove menu entries and role access |
| [ ] | 4 | Reverse the release package (EB.API, versions, EB.OVERRIDE, EB.ERROR) |
| [ ] | 5 | JAR: **do not just delete `ALBE_CustomerLimit.jar`** – module.xml loads it by name and a missing JAR takes down all T24 deployments. Put back the previous JAR under the same name (or have module.xml changed first), then restart |
| [ ] | 6 | *(optional)* tables: UTILISED.LIMIT / TXN.DETAILS = bookkeeping only; keep EB.ALBE.CIF.LIMITS if a re-install is planned |

---

## 6. After go-live

| Done | Item | When |
|---|---|---|
| [ ] | Set `logger.DIAGNOSTIC_API.level` back to **ERROR** (server admin) + restart | end of hypercare |
| [ ] | `Constants.LOG_DETAIL_ENABLED` stays **false** (turn on only when development asks) | always |
| [ ] | Daily: `grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR\|FAILED) \|"` – a technical failure does **not** block the transaction, so this is the only signal a check was skipped | daily |
| [ ] | Daily: `grep "ALBE.CUSTOMER.LIMIT.*\| NO RATE \|"` → report currency pairs (E1) | daily |
| [ ] | Daily: `getCustomerLimits \| NOT FOUND` → create the missing limit record (RUNBOOK 3.3) | daily |
| [ ] | `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY OVER.LIMIT EQ Y` – business follow-up | daily |
| [ ] | `ENQ ALBE.CUSTOMER.LIMIT.TXN DATE.FROM EQ <yesterday> OVERRIDDEN EQ Y` – compliance review | daily |
| [ ] | Housekeeping of EB.ALBE.TXN.DETAILS.LIMIT – retention period **OPEN (FQ12)**; keep as long as a reversal is possible | after FQ12 |
| [ ] | Remove the old routines after SIT: EB.API ALBE.CL.DAILY.MONTHLY.VAL / .UPD, ALBE.CL.MAX.CIF (and the old controllers in code) – only once the single routine is proven (TQ20) | after SIT sign-off |
| [ ] | Exchange rate for max CIF other-currency accounts (E1 / FQ8): set `Constants.EXCH_RATE_TABLE` / `EXCH_RATE_FIELD`, complete `CustomerLimitRepository.readExchangeRate` | when bank answers |
| [ ] | Support team has `docs/SUPPORT_RUNBOOK.md` (error lines §4, common cases §3) | go-live day |
| [ ] | Sign-off (RELEASE_NOTES §7): developer, reviewer, QA / SIT, business owner | go-live |

---

## 7. Per-product worksheet (template)

One line per product **and** activity. Credit activities also do the max CIF check.

| Product | Activity | GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (CREDIT / DEBIT) | ACTIVITY.API Post Routine set (target `ALBE.CL.ACTIVITY.LIMIT` / fallback VAL+MAX.CIF+UPD) | Proofed / Published | Tested (smoke 3–8) |
|---|---|---|---|---|---|
| ______ | ACCOUNTS-CREDIT-ARRANGEMENT | CREDIT [ ] | [ ] | [ ] / [ ] | [ ] |
| ______ | ACCOUNTS-DEBIT-ARRANGEMENT | DEBIT [ ] | [ ] | [ ] / [ ] | [ ] |
| ______ | ACCOUNTS-TRANSFERIN-ARRANGEMENT | CREDIT [ ] | [ ] | [ ] / [ ] | [ ] |
| ______ | ACCOUNTS-TRANSFEROUT-ARRANGEMENT | DEBIT [ ] | [ ] | [ ] / [ ] | [ ] |
| ______ | ______ | [ ] | [ ] | [ ] / [ ] | [ ] |
| ______ | ______ | [ ] | [ ] | [ ] / [ ] | [ ] |

---

(The doc points found while writing this list were corrected on 26-Nov-2026.)
