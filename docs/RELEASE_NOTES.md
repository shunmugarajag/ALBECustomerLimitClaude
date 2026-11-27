# ALBE Customer Limit – Release / Deployment Note

| | |
|---|---|
| Release | **1.0** – first delivery to SIT |
| Date | 21-Nov-2026 |
| Source | `shunmugarajag/ALBECustomerLimitClaude`, branch `main` (tag the commit that is packaged) |
| Requirement | HLSD 3 CBE Mandate CRs V2, section 7.2.1 – see [TRACEABILITY.md](TRACEABILITY.md) |
| Detailed set-up of every object | [T24_CONFIGURATION.md](T24_CONFIGURATION.md) (step numbers below refer to it) |

---

## 1. What this release delivers

| Function | Delivered |
|---|---|
| Customer limit creation | EB.ALBE.CIF.LIMIT created from EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` when a customer is authorised (CUSTOMER,ALBE.CORP.INPUT); never overwritten on amendment |
| Daily / monthly limit check | acceptable overrides on credit / debit activities; utilisation counted at input, reversed on rejection and reversal |
| Maximum CIF balance check | acceptable override on credit activities |
| Currency conversion | to the limit currency – **rate source pending** (other currencies get EB-ALBE.EXCH.RATE.MISSING) |
| Logging | standard `ALBE.CUSTOMER.LIMIT | …` lines through logger `API` → `TAFJ/log/api.log`, all at INFO (DIAGNOSTIC.API), D47 |

Known limitations: [GAPS.md](GAPS.md) (G1–G9, E1).

## 2. Objects in the release

### 2.1 Java

| Object | Content |
|---|---|
| `ALBE_CustomerLimit.jar` | package `com.temenos.albe` – 4 classes: `AlbeClActivityLimit`, `AlbeClMaxCif`, `AlbeClLimitCreate` (one per EB.API), `AlbeClConstants` |
| Generated table JARs | Java API of EB.ALBE.CIF.LIMIT, EB.ALBE.GEN.PARAM, EB.ALBE.UTILISED.LIMIT, EB.ALBE.TXN.DETAILS.LIMIT (step 2) |

Logging: every line is INFO (D47) – DIAGNOSTIC.API must be at INFO to see any line, including FAILED / BLOCKED.

### 2.2 T24 records

| # | Application | Record(s) | New / changed | Step |
|---|---|---|---|---|
| 1 | EB.TABLE.DEFINITION | ALBE.CIF.LIMIT (new), ALBE.GEN.PARAM, ALBE.UTILISED.LIMIT, ALBE.TXN.DETAILS.LIMIT (approved, unchanged) | new / existing | 1 |
| 2 | EB.ALBE.GEN.PARAM | `CUSTOMER.LIMIT` (CURRENCY EGP, daily 10,000 / 10,000, monthly 100,000 / 100,000, max CIF 200,000 in DEV) and `CUSTOMER.LIMIT.ACTIVITY` (ACCOUNTS-CREDIT / -TRANSFERIN-ARRANGEMENT = CREDIT, ACCOUNTS-DEBIT / -TRANSFEROUT-ARRANGEMENT = DEBIT) | new | 3, 3a |
| 3 | **OVERRIDE** (application name; not EB.OVERRIDE) | EB-ALBE.DAILY.CREDIT.LIMIT.EXCEED, EB-ALBE.DAILY.DEBIT.LIMIT.EXCEED, EB-ALBE.MONTHLY.CREDIT.LIMIT.EXCEED, EB-ALBE.MONTHLY.DEBIT.LIMIT.EXCEED, EB-ALBE.MAX.CIF.BAL.EXCEED, EB-ALBE.EXCH.RATE.MISSING – TYPE Warning (EB-ALBE.CIF.LIMIT.NOT.DEFINED: created in DEV, not raised since D52), one `&` (D51) | new | 4 |
| 3a | OVERRIDE / EB.ERROR – **remove** | EB-ABLE.DAILY.LIMIT.EXCEED, EB-ABLE.MONTHLY.LIMIT.EXCEED, EB-ABLE.MAX.CIF.BAL.EXCEED, EB-ABLE.CIF.LIMIT.NOT.DEFINED, EB-ABLE.EXCH.RATE.MISSING, EB-ABLE.LIMIT.UPDATE.FAILED (misspelt; reversed in DEV 27-Nov); EB-ALBE.ENQ.INVALID.DATE (unused, optional) | removed | 4 |
| 4 | EB.ERROR | EB-ALBE.CIF.LIMIT.CCY.MISSING, EB-ALBE.ACTIVITY.NOT.MAPPED, EB-ALBE.EXCH.RATE.MISSING, EB-ALBE.CUSTOMER.ACCOUNT.MISSING, EB-ALBE.LIMIT.UPDATE.FAILED – all block the transaction, one `&` | new | 14.3 |
| 5 | EB.API | ALBE.CL.ACTIVITY.LIMIT (`validateRecord`), ALBE.CL.MAX.CIF (`validateRecord`), ALBE.CL.LIMIT.CREATE (`postUpdateRequest`) – SOURCE.TYPE METHOD, JAVA.PACKAGE `com.temenos.albe`, JAVA.CLASS AlbeClActivityLimit / AlbeClMaxCif / AlbeClLimitCreate. T24 checks the class at input: **deploy the JAR first**. **Remove** ALBE.CL.DAILY.MONTHLY.VAL / .UPD / .DEF | new / removed | 6 |
| 6 | VERSION | **CUSTOMER,ALBE.CORP.INPUT** – AUTH.ROUTINE = ALBE.CL.LIMIT.CREATE (CUSTOMER,ALBE.CORP does not exist) | **changed** | 7 |
| 7 | VERSION | EB.ALBE.CIF.LIMIT,AMEND · EB.ALBE.CIF.LIMIT,VIEW (optional) · EB.ALBE.GEN.PARAM,LIMIT | new | 9 |
| 8 | AA.PRD.DES.ACTIVITY.API | `ACCT.MANDATE.API-20260401`: TRANSFERIN Post Routine 1 ALBE.CL.ACTIVITY.LIMIT + 2 ALBE.CL.MAX.CIF; TRANSFEROUT Post Routine 1 ALBE.CL.ACTIVITY.LIMIT. Same pattern for every other product condition that needs the check. `ALBE.STMT.FEE.API-20260403` still holds the removed DAILY.MONTHLY routines (left as is, user 27-Nov) | **changed** | 8 |
| 9 | AA.PRODUCT.DESIGNER | products using the condition (DEV: `ACCT.MAN.SAV.ACCOUNT`) – **proof and publish** after the condition change, so AA.PRD.CAT.ACTIVITY.API is updated | republished | 8 |
| 11 | Menu | Customer Limits sub-menu (maintain) | new / changed | 15.1 |
| 12 | Security | access of the roles in 15.2 | changed | 15.2 |

Package the T24 records (items 2–11) in a **DL.DEFINE** (or the team's usual release package) in the order of section 3.

## 3. Installation order

| # | Action | Check |
|---|---|---|
| 1 | Install / authorise the EB.TABLE.DEFINITION records; create the files | FILE.CONTROL entries exist |
| 2 | Generate the table Java API; put the generated JARs and `ALBE_CustomerLimit.jar` on the TAFJ classpath; restart / refresh TAFJ if your environment needs it | no class-not-found in the TAFJ log |
| 3 | EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` with the bank's values | record authorised |
| 4 | OVERRIDE and EB.ERROR records | records authorised |
| 5 | EB.API records | records authorised |
| 6 | Versions EB.ALBE.CIF.LIMIT,AMEND / ,VIEW, EB.ALBE.GEN.PARAM,LIMIT | versions open |
| 8 | ACTIVITY.API conditions on the selected products; proof and publish the products | conditions active on new activities |
| 9 | VERSION CUSTOMER,ALBE.CORP.INPUT AUTH.ROUTINE | – |
| 10 | Menu and security | menu visible for the roles |
| 11 | Go-live data: limits for existing customers, migrated monthly totals – [DATA_MIGRATION.md](DATA_MIGRATION.md) | reconciliation counts match |
| 12 | Post-installation checks (section 4) | all pass |

Routines are attached **last** (steps 8 and 9), so nothing runs before all records they need exist.

## 4. Post-installation checks (smoke test)

`grep "<FT id>" TAFJ/log/api.log | grep -E "ACTION|RESULT|ERROR"` (DIAGNOSTIC.API at INFO during the test).

| # | Check | Expected | Log line |
|---|---|---|---|
| 1 | Authorise a new test customer via CUSTOMER,ALBE.CORP.INPUT | EB.ALBE.CIF.LIMIT created with the generic values | `AlbeClLimitCreate: CREATED` |
| 2 | FT credit below the limit to a configured account | no override; daily + monthly used updated | `ACTION \| INPUT …`, `RESULT \| INPUT … <= limit … -> OK` |
| 3 | FT credit above the daily limit | override "Daily credit limit exceeded for Customer <id> by <amount>" (and monthly if over) | `RESULT \| INPUT … EXCEEDED by … raised: EB-ALBE.DAILY.LIMIT.EXCEED` |
| 4 | Delete the unauthorised FT of check 3 | used back to the value after check 2 | `ACTION \| DELETE …`, `RESULT \| TAKEN BACK (input rejected)` |
| 5 | Reverse an authorised FT and authorise the reversal | used goes down by its amount | `ACTION \| REVERSAL …`, `RESULT \| TAKEN BACK (reversal)` |
| 6 | Search the log for errors | none | `grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR\|FAILED\|BLOCKED) \|"` |

Full SIT tests: T24_CONFIGURATION §13, §15.3, [GO_LIVE_CHECKLIST.md](GO_LIVE_CHECKLIST.md) and [UNIT_TEST_CASES.md](UNIT_TEST_CASES.md).

## 5. Rollback

Remove the routines first so nothing new is counted, then the rest. Data written so far does no harm if left.

| # | Action |
|---|---|
| 1 | Remove the routines from the ACTIVITY.API conditions; proof and publish the products |
| 2 | Clear AUTH.ROUTINE ALBE.CL.LIMIT.CREATE on CUSTOMER,ALBE.CORP.INPUT |
| 3 | Remove the menu entries and role access |
| 4 | Reverse the release package (EB.API, versions, OVERRIDE, EB.ERROR) |
| 5 | **Do not just delete `ALBE_CustomerLimit.jar`**: JBoss `module.xml` loads `./localLib/ALBE_CustomerLimit.jar` by name and a missing file takes down every T24 deployment (DEV 25-Nov). Leave the JAR (no routine points to it), or remove its entry from module.xml first in a planned change with a restart. |
| 6 | *(optional)* keep or clear the local tables – EB.ALBE.UTILISED.LIMIT / TXN.DETAILS hold only bookkeeping; EB.ALBE.CIF.LIMIT holds the customers' limits (keep if a re-install is planned) |

There is no configuration-only rollback any more (D47: the old routines are not in the JAR). To go back to the previous behaviour, redeploy the previous JAR together with its ACTIVITY.API set-up.

## 6. Configuration per environment

| Setting | SIT / UAT | Production |
|---|---|---|
| Logger `API` (DIAGNOSTIC.API in TAFJTrace.properties) | INFO | **decide**: INFO = all lines incl. FAILED / BLOCKED; ERROR = no Customer Limit lines at all (all lines are INFO, D47) |
| EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` values | test values | bank-approved values |
| Exchange rate (E1) | as soon as configured | must be configured before go-live if foreign-currency transactions are in scope |

## 7. Sign-off

| Role | Name | Date | Signature |
|---|---|---|---|
| Developer | | | |
| Reviewer | | | |
| QA / SIT | | | |
| Business owner | | | |
