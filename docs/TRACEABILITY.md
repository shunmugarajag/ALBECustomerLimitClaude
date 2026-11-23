# ALBE Customer Limit – Requirement Traceability Matrix

Source: HLSD *Albaraka T24 project – Account module – 3 CBE Mandate CRs V2* (v0.2, 19-Nov-2026), sections 1.1.1, 1.1.2, 7.2.1 and use cases 8.2–8.6.
Approved table structures: EB.TABLE.DEFINITION in DEV (ALBE.GEN.PARAM, ALBE.CIF.LIMIT, ALBE.UTILISED.LIMIT, ALBE.TXN.DETAILS.LIMIT).
Unit tests: [UNIT_TEST_CASES.md](UNIT_TEST_CASES.md) (`UT-…`). DEV/SIT tests: [T24_CONFIGURATION.md](T24_CONFIGURATION.md) §13 (`#1…#16`) and §14.4, [REPORTS.md](REPORTS.md) (`R1…R12`).

Status: **Done** = implemented and unit-tested outside T24 · **DEV** = needs confirmation in DEV · **Deviation** = agreed difference to the HLSD · **Pending** = waits for input.

---

## 1. Requirements

| Req | HLSD reference | Requirement (short) | Implementation (class / method) | Tests | Status |
|---|---|---|---|---|---|
| R01 | 1.1.1 Daily limit | Daily credit and debit limit per customer, valid for the current working day | `DailyMonthlyLimitService.validateDailyLimit`, `checkLimit`; record `<cust>-YYYYMMDD` | UT-D01…D05, #3 #4 #10 | Done |
| R02 | 1.1.1 Monthly limit | Monthly credit and debit limit, from day one of the month | `DailyMonthlyLimitService.validateMonthlyLimit`; record `<cust>-YYYYMM` (migrated + utilised) | UT-M01…M03, #11 | Done |
| R03 | 1.1.1 Overall limit | Maximum overall balance per customer | `MaximumCifLimitService.validate`, `getTotalCifBalance` | UT-X01…X04, #12 | Done |
| R04 | 1.1.1 | Products and activities eligible for the validation chosen by the user | ACTIVITY.API product condition per product / activity (no parameter table – see D7) | #3 | Done / Deviation D7 |
| R05 | 1.1.2 | EB.ALBE.GEN.CIF.LIMIT generic limits, id SYSTEM | EB.ALBE.GEN.PARAM record **`CUSTOMER.LIMIT`** (KEY.NAME / KEY.VALUE); `CustomerLimitReader.getGenericLimits` | UT-C02 | Deviation D2 |
| R06 | 1.1.2 | EB.ALBE.CIF.LIMITS per customer: CCY, daily / monthly deposit and withdrawal limits, maximum CIF balance | `CustomerLimitReader.getCustomerLimits`; fields CCY, DAILY/MONTHLY.CREDIT/DEBIT.LIMIT, MAX.CIF.BALANCE | UT-D01, UT-L01 | Done |
| R07 | 1.1.2 | EB.ALBE.TXN.DETAILS.LIMIT id `Custid-YYYYMMDD-TXN.ID` | `TxnDetailsLimitRecorder.recordId` = `<cust>-<YYYYMMDD>-<AAA id>` | UT-U01, UT-U05 | Done |
| R08 | 1.1.2 | TXN.DETAILS fields | approved table has ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT – all written by `TxnDetailsLimitRecorder.record` | UT-U01 | Deviation D3 |
| R09 | 1.1.2 | EB.ALBE.UTILISED.LIMIT daily `Custid-YYYYMMDD` and monthly `Custid-YYYYMM`; migrated fields only for migrated contracts | `CustomerLimitUtilizationUpdater.addToRecord`; migrated fields read only | UT-U02, UT-M02 | Done / Deviation D4 |
| R10 | 7.2.1 | Generic limit record created by the user | step 3 of T24_CONFIGURATION | – | Done (config) |
| R11 | 7.2.1 Creation | On customer creation copy generic limits to EB.ALBE.CIF.LIMITS `<customer id>` | `CustomerLimitCreationController.postUpdateRequest` → `CustomerLimitCreationService.create` | UT-C01…C03, #1 | Done |
| R12 | 7.2.1 Creation | User can amend a customer's limits any time; amend version on the user menu | VERSION `EB.ALBE.CIF.LIMITS,AMEND`, menu (T24_CONFIGURATION §9, §15); creation never overwrites | UT-C01, #2 | Done |
| R13 | 7.2.1 Creation | Attached as After Unau routine on CUSTOMER,ALBE.CORP | attached as **Auth routine** | #1 | Deviation D1 |
| R14 | 7.2.1 Creation | Delete EB.ALBE.CIF.LIMITS when the unauthorised customer is deleted | not needed: record created only at authorisation | – | Deviation D1 |
| R15 | 7.2.1 Daily/monthly | Attached in ACTIVITY.API for the selected products and activities | `DailyMonthlyLimitController` (`defaultFieldValues`, `validateRecord`, `postCoreTableUpdate`) | #3 | Done |
| R16 | 7.2.1 Daily | Credit: utilised daily credit (`custid-YYYYMMDD`, server date) + TXN amount > limit → override "Daily credit/debit exceed for Customer & by &" | `validateDailyLimit` → `checkLimit` → `CustomerLimitOverride.raiseFor` | UT-D02, #4, use case 8.2 | Done |
| R17 | 7.2.1 Daily | Debit: same with daily debit | same, debit side | UT-D04, #10, use case 8.5 | Done |
| R18 | 7.2.1 Monthly | Credit: utilised monthly credit (`custid-YYYYMM`) + amount > limit → override "Monthly credit/debit exceed …" | `validateMonthlyLimit` | UT-M01, use case 8.3 | Done |
| R19 | 7.2.1 Monthly | Debit: same with monthly debit | same, debit side | UT-M02, #11, use case 8.6 | Done |
| R20 | 7.2.1 | EB.OVERRIDE record for each message | EB-ALBE.DAILY.LIMIT.EXCEED, EB-ALBE.MONTHLY.LIMIT.EXCEED, EB-ALBE.MAX.CIF.BAL.EXCEED (+ 2 module overrides) | #4 | Done (config) |
| R21 | 7.2.1 | If not exceeded, update the utilised limit | counted at input in `postCoreTableUpdate` (UNAUTH) – **also when the override is accepted** | UT-U01, UT-U03 | Deviation D5 |
| R22 | 1 / 7.2.1 | Input and deletion logic to be handled | re-input replaces the earlier amount; DELETE and AUTH-REV reverse from the original day / month | UT-U03…U06, #6 #7 #9 | Done |
| R23 | 7.2.1 Maximum | Credit: total working balance of all customer accounts + amount > MAX.CIF.BALANCE → override "Overall credit/debit exceed …" | `MaximumCifLimitController` → `MaximumCifLimitService` | UT-X01, #12, use case 8.4 | Done |
| R24 | 1.1.2 CCY | Limits in local currency | limits compared in EB.ALBE.CIF.LIMITS CCY; other currencies converted (`CurrencyConversionService`) | UT-F01…F04 | Done / Pending E1 |
| R25 | 10 Assumptions | Reports not included (separate requirement) | three enquiries delivered additionally | R1…R12 | Additional |

## 2. HLSD use cases

| Use case | Scenario | Expected | Unit test | DEV test | Status |
|---|---|---|---|---|---|
| 8.1 | Financial-inclusion CIF – maximum balance configured | core maker-checker; limits created at authorisation | UT-C01 | #1 | Done |
| 8.2 | Daily deposit limit 5000, utilised 3000, credit 4000 | "Daily credit/debit exceed for Customer … by 2000" | UT-D02 | #4 | Done |
| 8.3 | Monthly deposit 15000, utilised 13000, credit 7000 | "Monthly credit/debit exceed … by 5000" | UT-M01 | #11 | Done |
| 8.4 | Maximum balance 100000, working balance 70000, credit 50000 | "Overall credit/debit exceed … by 20000" | UT-X01 | #12 | Done |
| 8.5 | Daily withdrawal 5000, utilised 3000, debit 4000 | "Daily credit/debit exceed … by 2000" | UT-D04 | #10 | Done |
| 8.6 | Monthly withdrawal 15000, utilised 13000, debit 7000 | "Monthly credit/debit exceed … by 5000" | UT-M02 | #11 | Done |

## 3. Agreed deviations from the HLSD

| # | HLSD | Implemented | Reason | Agreed |
|---|---|---|---|---|
| D1 | Creation as After Unau; delete CIF.LIMIT when the unauthorised customer is deleted | **Auth routine**; no delete flow | record exists only for authorised customers – nothing to delete | 20-Nov-2026 |
| D2 | Table EB.ALBE.GEN.CIF.LIMIT, id SYSTEM | EB.ALBE.GEN.PARAM, record **CUSTOMER.LIMIT**, KEY.NAME / KEY.VALUE | approved table structure | 20-Nov-2026 |
| D3 | TXN.DETAILS also TXN.CODE, TXN.CCY, EXCH.RATE, TXN.AMOUNT.LCY, SYSTEMDATE | approved table has ACCOUNT.NO, CREDIT.DEBIT.FLAG, EFF.DATE, TXN.AMOUNT only; **TXN.AMOUNT holds the amount in the limit currency**; the counted date is in the id and the activity REMARKS | pre-approved table structure – no new fields | 21-Nov-2026 |
| D4 | UTILISED.LIMIT: daily record fields 1–2, monthly record fields 3–6 | approved table: UTILISED.CREDIT/DEBIT.LIMIT on both records, UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT on the monthly record | approved table structure | 20-Nov-2026 |
| D5 | Update utilised only if not exceeded | counted at input **also when the override is accepted**; reversed on rejection and reversal | accepted transactions are real usage | 20-Nov-2026 |
| D6 | Server date (now: T24 date) | server date of the **first input**, kept in the activity REMARKS (`ALBE.LIMIT.DATE=YYYYMMDD`) and used for re-input, rejection and reversal | correct day/month even when reversed later; no table field | 21-Nov-2026 |
| D7 | Parameter table EGP.LIMIT.VALIDATION.PARAM for products / activities (to be confirmed) | eligibility by attaching the routines in ACTIVITY.API per product / activity | HLSD 7.2.1 attaches the routines per product and activity | per HLSD 7.2.1 |
| D8 | – | currency conversion to the limit currency (rate source pending) | transactions / accounts in another currency | 20-Nov-2026 |
| D11 | – | FT / TELLER / ACCOUNT.PARAMETER routines built and **removed** 22-Nov-2026: FT / TT on AA accounts arrive as activities (ACTIVITY.API) | user 22-Nov | 22-Nov-2026 |
| D9 | Credit / debit activities (ACCOUNTS-CREDIT/DEBIT-ARRANGEMENT) | routines can be attached to **any** activity: counted when it has an amount and its ACTIVITY.CLASS action is CREDIT / DEBIT; amount ORIG.TXN.AMT if filled, else TXN.AMOUNT | user: common for all activities; full original amount | 21-Nov-2026 |

The BA should reflect D1–D6 in the next HLSD version.

## 4. Coverage summary

| | Count | Done | DEV confirm | Pending |
|---|---|---|---|---|
| Requirements R01–R25 | 25 | 24 | all hooks / API calls (see TECHNICAL_REFERENCE §12) | R24 rate source (E1) |
| Use cases 8.1–8.6 | 6 | 6 (unit) | 6 (DEV) | – |
| Deviations | 8 | – | – | HLSD update by BA |
