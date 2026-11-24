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
| Customer limit creation | EB.ALBE.CIF.LIMITS created from EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` when a customer is authorised (CUSTOMER,ALBE.CORP); never overwritten on amendment |
| Daily / monthly limit check | acceptable overrides on credit / debit activities; utilisation counted at input, reversed on rejection and reversal |
| Maximum CIF balance check | acceptable override on credit activities |
| Currency conversion | to the limit currency – **rate source pending** (other currencies get EB-ABLE.EXCH.RATE.MISSING) |
| Reports | limit summary, period report (daily / monthly), transaction report incl. overridden transactions |
| Logging | standard `ALBE.CUSTOMER.LIMIT | …` lines through logger L3API |

Known limitations: [GAPS.md](GAPS.md) (G1–G9, E1).

## 2. Objects in the release

### 2.1 Java

| Object | Content |
|---|---|
| `ALBECustomerLimit.jar` | package `com.temenos.ALBE.CustomerLimit` – 28 classes (6 controllers, 8 services, 1 repository, 6 models, 2 views, 2 validators, 2 utilities, Constants) |
| Generated table JARs | Java API of EB.ALBE.CIF.LIMITS, EB.ALBE.GEN.PARAM, EB.ALBE.UTILISED.LIMIT, EB.ALBE.TXN.DETAILS.LIMIT (step 2) |

Build setting: `Constants.LOG_DETAIL_ENABLED` = **true** for SIT, **false** for production.

### 2.2 T24 records

| # | Application | Record(s) | New / changed | Step |
|---|---|---|---|---|
| 1 | EB.TABLE.DEFINITION | ALBE.CIF.LIMIT (new), ALBE.GEN.PARAM, ALBE.UTILISED.LIMIT, ALBE.TXN.DETAILS.LIMIT (approved, unchanged) | new / existing | 1 |
| 2 | EB.ALBE.GEN.PARAM | `CUSTOMER.LIMIT` | new | 3 |
| 3 | EB.OVERRIDE | EB-ABLE.DAILY.LIMIT.EXCEED, EB-ABLE.MONTHLY.LIMIT.EXCEED, EB-ABLE.MAX.CIF.BAL.EXCEED, EB-ABLE.CIF.LIMIT.NOT.DEFINED, EB-ABLE.EXCH.RATE.MISSING | new | 4 |
| 4 | EB.ERROR | EB-ALBE.CIF.LIMIT.CCY.MISSING (blank limit currency, blocks), EB-ALBE.LIMIT.UPDATE.FAILED (utilisation save failed, blocks), EB-ALBE.ENQ.INVALID.DATE, EB-ALBE.ENQ.INVALID.MONTH, EB-ALBE.ENQ.DATE.RANGE, EB-ALBE.ENQ.PERIOD.MANDATORY, EB-ALBE.ENQ.RANGE.TOO.LONG, EB-ALBE.ENQ.CUSTOMER.OR.DATE (EB-NO.REC.SELECT exists) | new | 14.3 |
| 5 | EB.API | ALBE.CL.LIMIT.CREATE, ALBE.CL.DAILY.MONTHLY (or .DEF/.VAL/.UPD), ALBE.CL.MAX.CIF, ALBE.E.CUSTOMER.LIMIT.SUMMARY, ALBE.E.CUSTOMER.LIMIT.PERIOD, ALBE.E.CUSTOMER.LIMIT.TXN | new | 6 |
| 6 | VERSION | CUSTOMER,ALBE.CORP – AUTH.ROUTINE = ALBE.CL.LIMIT.CREATE | **changed** | 7 |
| 7 | VERSION | EB.ALBE.CIF.LIMITS,AMEND · EB.ALBE.CIF.LIMITS,VIEW (optional) · EB.ALBE.GEN.PARAM,LIMIT | new | 9 |
| 8 | ACTIVITY.API product condition | per product: ACCOUNTS-CREDIT-ARRANGEMENT (daily/monthly: defaultFieldValues, validateRecord, postCoreTableUpdate; max CIF: validateRecord), ACCOUNTS-DEBIT-ARRANGEMENT (daily/monthly) | **changed** | 8 |
| 9 | STANDARD.SELECTION | NOFILE.ALBE.CUSTOMER.LIMIT.SUMMARY, .PERIOD, .TXN | new | 14.1 |
| 10 | ENQUIRY | ALBE.CUSTOMER.LIMIT.SUMMARY, ALBE.CUSTOMER.LIMIT.PERIOD, ALBE.CUSTOMER.LIMIT.TXN | new | 14.2 |
| 11 | Menu | Customer Limits sub-menu (maintain + reports) | new / changed | 15.1 |
| 12 | Security | access of the roles in 15.2 | changed | 15.2 |

Package the T24 records (items 2–11) in a **DL.DEFINE** (or the team's usual release package) in the order of section 3.

## 3. Installation order

| # | Action | Check |
|---|---|---|
| 1 | Install / authorise the EB.TABLE.DEFINITION records; create the files | FILE.CONTROL entries exist |
| 2 | Generate the table Java API; put the generated JARs and `ALBECustomerLimit.jar` on the TAFJ classpath; restart / refresh TAFJ if your environment needs it | no class-not-found in the TAFJ log |
| 3 | EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` with the bank's values | record authorised |
| 4 | EB.OVERRIDE and EB.ERROR records | records authorised |
| 5 | EB.API records | records authorised |
| 6 | Versions EB.ALBE.CIF.LIMITS,AMEND / ,VIEW, EB.ALBE.GEN.PARAM,LIMIT | versions open |
| 7 | STANDARD.SELECTION and ENQUIRY records | `ENQ ALBE.CUSTOMER.LIMIT.SUMMARY` runs (EB-NO.REC.SELECT is fine at this point) |
| 8 | ACTIVITY.API conditions on the selected products; proof and publish the products | conditions active on new activities |
| 9 | VERSION CUSTOMER,ALBE.CORP AUTH.ROUTINE | – |
| 10 | Menu and security | menu visible for the roles |
| 11 | Go-live data: limits for existing customers, migrated monthly totals – [DATA_MIGRATION.md](DATA_MIGRATION.md) | reconciliation counts match |
| 12 | Post-installation checks (section 4) | all pass |

Routines are attached **last** (steps 8 and 9), so nothing runs before all records they need exist.

## 4. Post-installation checks (smoke test)

| # | Check | Expected | Log line |
|---|---|---|---|
| 1 | Authorise a new test customer via CUSTOMER,ALBE.CORP | EB.ALBE.CIF.LIMITS created with the generic values | `CustomerLimitCreationController.postUpdateRequest \| END \| … created=true` |
| 2 | Credit below the limit | no override; daily + monthly used updated; AAA REMARKS ends with `ALBE.LIMIT.DATE=<today>` | `checkLimit \| OK`, `addToRecord \| UPDATED` |
| 3 | Credit above the daily limit | EB-ABLE.DAILY.LIMIT.EXCEED with customer and difference | `checkLimit \| BREACH` |
| 4 | Delete the unauthorised transaction of check 3 | used back to the value after check 2 | `ACTION \| REVERSE rejected input` |
| 5 | Limit summary for the test customer | numbers match checks 2–4 | – |
| 6 | Search the log for errors | none | `grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR\|FAILED) \|"` |

Full SIT tests: T24_CONFIGURATION §13, §14.4, §15.3 and [UNIT_TEST_CASES.md](UNIT_TEST_CASES.md).

## 5. Rollback

Remove the routines first so nothing new is counted, then the rest. Data written so far does no harm if left.

| # | Action |
|---|---|
| 1 | Remove the routines from the ACTIVITY.API conditions; proof and publish the products |
| 2 | Clear AUTH.ROUTINE ALBE.CL.LIMIT.CREATE on CUSTOMER,ALBE.CORP |
| 3 | Remove the menu entries and role access |
| 4 | Reverse the release package (EB.API, ENQUIRY, STANDARD.SELECTION, versions, EB.OVERRIDE, EB.ERROR) |
| 5 | Remove `ALBECustomerLimit.jar` from the TAFJ classpath |
| 6 | *(optional)* keep or clear the local tables – EB.ALBE.UTILISED.LIMIT / TXN.DETAILS hold only bookkeeping; EB.ALBE.CIF.LIMITS holds the customers' limits (keep if a re-install is planned) |

The `ALBE.LIMIT.DATE=…` text stays in the REMARKS of activities input while the release was active; it is plain text and harmless.

## 6. Configuration per environment

| Setting | SIT / UAT | Production |
|---|---|---|
| `Constants.LOG_DETAIL_ENABLED` | true | **false** |
| Logger L3API level | INFO | INFO |
| EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` values | test values | bank-approved values |
| Exchange rate (E1) | as soon as configured | must be configured before go-live if foreign-currency transactions are in scope |

## 7. Sign-off

| Role | Name | Date | Signature |
|---|---|---|---|
| Developer | | | |
| Reviewer | | | |
| QA / SIT | | | |
| Business owner | | | |
