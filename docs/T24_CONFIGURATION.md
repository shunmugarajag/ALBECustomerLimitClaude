# ALBE Customer Limit – T24 Configuration Checklist

Everything that has to be set up **in T24 / TAFJ** for the Customer Limit code to work, in the order to do it.
For how the code works see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md).

> Field and screen names below follow the HLSD and the code. Where a name can differ between T24 releases it is
> marked **(confirm)** – use the same set-up your team uses for other local Java routines.

| Step | What | Done |
|---|---|---|
| 1 | Local tables (EB.TABLE.DEFINITION) | ☐ |
| 2 | Generate the table Java API (JARs) | ☐ |
| 3 | Parameter record EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT` | ☐ |
| 4 | EB.OVERRIDE records | ☐ |
| 5 | Build and deploy the routine JAR | ☐ |
| 6 | EB.API records | ☐ |
| 7 | VERSION CUSTOMER,ALBE.CORP (creation) | ☐ |
| 8 | ACTIVITY.API on the AA products (checks + utilisation) | ☐ |
| 9 | Versions for EB.ALBE.CIF.LIMITS and EB.ALBE.GEN.PARAM | ☐ |
| 10 | Logging | ☐ |
| 11 | Data before go-live (existing customers, migrated utilisation) | ☐ |
| 12 | Exchange rate (pending) | ☐ |
| 13 | DEV / SIT test cases | ☐ |
| 14 | ~~Reports~~ – removed | – |
| 15 | User menu and access | ☐ |

---

## 1. Local tables

Create (or check) these four EB.TABLE.DEFINITION records. **Field names must match exactly**: the generated Java getters (`getDailyCreditLimit()` …) come from them.

### 1.0 Table type (which files T24 creates)

| Type | Files | Meaning |
|---|---|---|
| H (history) | live + $NAU + $HIS | maker-checker with full history |
| U (unauthorised) | live + $NAU | maker-checker, no history |
| L (live only) | live | no authorisation, no history, written directly |

The field that holds the type in EB.TABLE.DEFINITION can differ by release **(confirm)**; FILE.CONTROL > SUFFIXES shows the result ($NAU / $HIS).

| Table | Suggested type | Why |
|---|---|---|
| EB.ALBE.CIF.LIMITS | **H** | users amend it through a version; the bank needs authorisation and a history of limit changes |
| EB.ALBE.GEN.PARAM | **H** (or U) | parameter changes should be authorised and traceable |
| EB.ALBE.UTILISED.LIMIT | **L** | written only by the code on every transaction; no authorisation, no history (would grow huge) |
| EB.ALBE.TXN.DETAILS.LIMIT | **L** | internal bookkeeping, one record per transaction, deleted on rejection / reversal |

The code always reads and writes the **live** file. If UTILISED.LIMIT / TXN.DETAILS already exist as H or U it still works, but L avoids users creating $NAU copies on screen and saves space.

### 1.1 EB.ALBE.GEN.PARAM – generic parameters  *(exists)*

| Field | Type | Note |
|---|---|---|
| KEY.NAME | multi-value, text | paired with KEY.VALUE |
| KEY.VALUE | multi-value, text | |

### 1.2 EB.ALBE.CIF.LIMITS – one record per customer  *(create)*

ID: **customer id** (validate as CUSTOMER).

| No | Field | Description | Type |
|---|---|---|---|
| 1 | CURRENCY | Currency of the limits | Currency |
| 2 | DAILY.CREDIT.LIMIT | Daily credit limit | Amount |
| 3 | DAILY.DEBIT.LIMIT | Daily debit limit | Amount |
| 4 | MONTHLY.CREDIT.LIMIT | Monthly credit limit | Amount |
| 5 | MONTHLY.DEBIT.LIMIT | Monthly debit limit | Amount |
| 6 | MAXIMUM.OVERALL.CIF.AMOUNT | Maximum overall CIF balance | Amount |

A blank limit field means "not defined": the transaction gets the EB-ABLE.CIF.LIMIT.NOT.DEFINED override.

### 1.3 EB.ALBE.UTILISED.LIMIT – daily and monthly totals  *(exists)*

ID: daily `<customer>-YYYYMMDD`, monthly `<customer>-YYYYMM` (written by the routine).

| Field | Type | Written by routine? |
|---|---|---|
| UTILISED.CREDIT.LIMIT | Amount | yes |
| UTILISED.DEBIT.LIMIT | Amount | yes |
| UTILISED.MIGRATED.CREDIT.LIMIT | Amount | **no** – migration only (monthly record) |
| UTILISED.MIGRATED.DEBIT.LIMIT | Amount | **no** – migration only (monthly record) |

### 1.4 EB.ALBE.TXN.DETAILS.LIMIT – what was counted per transaction  *(exists – no change)*

ID: **`<customer>-<YYYYMMDD>-<root ref>-<arrangement id>`** – root ref = the FT / TT behind the activity (TXN.CONTRACT.ID), else the AAA id – e.g. `100234-20261120-FT26324ABCDE-AA26324XYZ12` (ID max 100 characters) (written by the routine). YYYYMMDD = T24 date the amount was counted on, kept in the activity REMARKS (see step 8).

| Field | Type | Value |
|---|---|---|
| ACCOUNT.NO | Account | arrangement's account |
| CREDIT.DEBIT.FLAG | text | `CR` / `DR` |
| EFF.DATE | Date | effective date of the transaction |
| TXN.AMOUNT | Amount | amount counted (limit currency) |
The table structure is the approved one; the code writes only these four fields.

---

## 2. Generate the table Java API (JARs)

After authorising the tables, generate the Java API for the four local tables with your release's generation step (the same one used for other local tables, e.g. Design Studio / TAFJ API generation) **(confirm)**.

The code needs these classes:

| Table | Record class | Table class |
|---|---|---|
| EB.ALBE.CIF.LIMITS | `com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord` | `com.temenos.t24.api.tables.ebalbeciflimit.EbAlbeCifLimitTable` |
| EB.ALBE.GEN.PARAM | `com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord`, `KeyNameClass` | – |
| EB.ALBE.UTILISED.LIMIT | `com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord` | `com.temenos.t24.api.tables.ebalbeutilisedlimit.EbAlbeUtilisedLimitTable` |
| EB.ALBE.TXN.DETAILS.LIMIT | `com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord` | `com.temenos.t24.api.tables.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitTable` |

Then:

1. Add the generated JARs to the **Eclipse build path** (Project → Properties → Java Build Path → Libraries), next to the TAFJ / T24 API JARs.
2. Check the Eclipse **Problems** view (Window → Show View → Problems, filter "All errors on workspace"): it should show **0 errors**.
3. Make sure the same JARs are on the **TAFJ runtime classpath**.

Core API JARs also needed (from the TAFJ installation): `com.temenos.api` (TField, TStructure, TValidationResponse), `com.temenos.t24.api` (hooks, DataAccess, Session, AA / ACCOUNT / COMPANY records), `com.temenos.logging.facade`.

---

## 3. Parameter record EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT`

Input and authorise record **`CUSTOMER.LIMIT`** – the default limits copied to each new customer. The id is set in `Constants.GEN_PARAM_ID`.

| KEY.NAME | KEY.VALUE (DEV, 23-Nov-2026) | Copied to CIF.LIMIT field |
|---|---|---|
| CURRENCY | EGP | CCY |
| DAILY.CREDIT.LIMIT | 10000 | DAILY.CREDIT.LIMIT |
| DAILY.DEBIT.LIMIT | 10000 | DAILY.DEBIT.LIMIT |
| MONTHLY.CREDIT.LIMIT | 10000 | MONTHLY.CREDIT.LIMIT |
| MONTHLY.DEBIT.LIMIT | 10000 | MONTHLY.DEBIT.LIMIT |
| MAXIMUM.OVERALL.CIF.BALANCE | 10000 | MAX.CIF.BALANCE |

The KEY.NAME values must be spelled exactly as above (`Constants.PARAM_KEY_*`). A misspelt CURRENCY gives new customers a blank CCY, and every transaction is then blocked (EB-ALBE.CIF.LIMIT.CCY.MISSING). Values are the DEV set-up – the bank provides the production ones.
Changing this record later does **not** change existing customers' EB.ALBE.CIF.LIMITS records.

### 3a. Parameter record EB.ALBE.GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` – credit / debit mapping *(mandatory, D41)*

Tells the routines whether an activity is a **credit** or a **debit**. KEY.NAME = the AAA **ACTIVITY** name, KEY.VALUE = `CREDIT` or `DEBIT`. Id in `Constants.GEN_PARAM_ACTIVITY_ID`.

| KEY.NAME (ACTIVITY) | KEY.VALUE |
|---|---|
| ACCOUNTS-CREDIT-ARRANGEMENT | CREDIT |
| ACCOUNTS-DEBIT-ARRANGEMENT | DEBIT |

- Add one line for **every** activity the routines are attached to (ACTIVITY.API).
- Activity has the routine but is **not in this record** (or value is not CREDIT/DEBIT, or the record is missing) → **EB.ERROR `EB-ALBE.ACTIVITY.NOT.MAPPED`**, transaction blocked.
- Value is not case-sensitive (`debit` = DEBIT).

---

## 4. EB.OVERRIDE records

Create and authorise these five records. All are **acceptable** overrides (the user can accept and continue).
`&` is replaced by the parameters in the order shown.

| ID | MESSAGE | & parameters |
|---|---|---|
| EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED *(D46)* | Daily debit limit exceeded for Customer & | "<customer> by <amount over>" |
| EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED *(D46)* | Daily credit limit exceeded for Customer & | "<customer> by <amount over>" |
| EB-ABLE.MONTHLY.DEBIT.LIMIT.EXCEED *(D46)* | Monthly debit limit exceeded for Customer & | "<customer> by <amount over>" |
| EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED *(D46)* | Monthly credit limit exceeded for Customer & | "<customer> by <amount over>" |
| EB-ABLE.MAX.CIF.BAL.EXCEED | Overall balance limit exceeded for Customer & *(changed, D46)* | "<customer> by <amount over>" |
| EB-ABLE.CIF.LIMIT.NOT.DEFINED | Customer limit not defined for Customer & | customer id |
| EB-ABLE.EXCH.RATE.MISSING | Exchange rate not available & *(changed, D46)* | "from <currency> to <limit currency>" – **max CIF only** (another account of the customer in a foreign currency, parked until E1) |

**EB.ERROR** (blocks the transaction):

| ID | MESSAGE | Raised when |
|---|---|---|
| EB-ALBE.CIF.LIMIT.CCY.MISSING | Limit currency not defined for Customer & | the customer's EB.ALBE.CIF.LIMITS has a blank CURRENCY |
| EB-ALBE.LIMIT.UPDATE.FAILED | Customer limit could not be updated, transaction cancelled | saving EB.ALBE.UTILISED.LIMIT or EB.ALBE.TXN.DETAILS.LIMIT failed (decision D37) – the whole transaction is cancelled |
| EB-ALBE.ACTIVITY.NOT.MAPPED | Activity & not defined in customer limit parameter | the activity is not in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (D41) – & = activity name |
| **EB-ALBE.CUSTOMER.ACCOUNT.MISSING** *(new, D45)* | No accounts found in CUSTOMER.ACCOUNT for Customer &, transaction not allowed | max CIF check (credit, MAXIMUM.OVERALL.CIF.AMOUNT filled) and CUSTOMER.ACCOUNT `<customer>` is empty / missing – & = customer id |
| **EB-ALBE.EXCH.RATE.MISSING** *(new, D44)* | Exchange rate not available &, transaction not allowed *(& = "from USD to EGP")* | the transaction currency differs from the limit currency and the activity has neither an LCY amount (TXN.AMOUNT.LCY / ORIG.TXN.AMT.LCY) nor TXN.EXCH.RATE – or the customer's limit is not in the local currency. & = transaction currency, limit currency |

The routine sends `[ID,param]` – **one parameter per message** (D46, 26-Nov): R25 `EB.HANDLE.OVERRIDES` / `EB.HANDLE.ERRORS` take part 1 as the id and fill the `&` with part 2. DEV 26-Nov: with two parameters both values landed in the first `&` ("for Customer 100128,23000.00 by"), so each message has **one `&`** and the code sends e.g. "100128 by 12000.00". **Change the EB.OVERRIDE / EB.ERROR message texts in T24 as in the tables above** (create the four debit / credit ids; the old EB-ABLE.DAILY.LIMIT.EXCEED / MONTHLY.LIMIT.EXCEED are no longer used).
Each override goes on its **own AAA field** – a field keeps only one override (DEV 26-Nov: daily + monthly on TXN.AMOUNT showed only the monthly one):

| Override | AAA field |
|---|---|
| EB-ABLE.DAILY.DEBIT / CREDIT.LIMIT.EXCEED | TXN.AMOUNT |
| EB-ABLE.MONTHLY.DEBIT / CREDIT.LIMIT.EXCEED | TXN.AMOUNT.LCY |
| EB-ABLE.MAX.CIF.BAL.EXCEED | ORIG.TXN.AMT |
| EB-ABLE.CIF.LIMIT.NOT.DEFINED | CURRENCY |
| EB-ABLE.EXCH.RATE.MISSING | EFFECTIVE.DATE |
| errors (EB-ALBE.*) | TXN.AMOUNT |

---

## 5. Build and deploy the routine JAR

The project is a **Maven** project (`pom.xml`, 25-Nov-2026). Sources stay in `src/`.

**One-time set-up (laptop)**
1. Eclipse: right-click the project → **Configure → Convert to Maven Project** (or File → Import → Existing Maven Projects).
2. In `pom.xml` check `t24.home` (default `C:/Temenos/R25`) and that `lib/l3-tables` holds `ALBE_CUSTOMER_LIMIT_TABLE.jar` and `ALBE_GEN_PARAM_TABLE.jar` (names in `l3.table.*.jar`).
3. Server user / password: in `pom.xml` (uncomment `deploy.user` / `deploy.password` – then **do not commit** the pom with the password), or in `deploy.local.properties` next to the pom (ignored by Git), or on the command line `-Ddeploy.user=… -Ddeploy.password=…`.
4. **JAR name must be `ALBE_CustomerLimit.jar`**: JBoss `modules/com/temenos/t24/main/module.xml` loads `./localLib/ALBE_CustomerLimit.jar` by name. If that file is missing, the whole T24 module fails and every deployment is marked `.failed` (DEV 25-Nov). The deploy copies to `ALBE_CustomerLimit.jar.new` and renames, so the file is never missing; `deploy.old.jars` (`ALBECustomerLimit*.jar`) removes wrong-name copies.

**Every build**

| Command | Result |
|---|---|
| `mvn clean package` | `target/ALBE_CustomerLimit.jar` (only our classes; T24 / table JARs are compile-only) |
| `mvn clean deploy` | build, then on the T24 server: list + remove `deploy.old.jars` in `deploy.dir` (`/srv/Temenos/T24/bnk/ALBElib`), copy the new JAR, list it (SSH / SCP, port `deploy.port`) |

**Short commands** (project folder, PowerShell: `.\deploy`, cmd: `deploy`):

| Command | Does |
|---|---|
| `.\deploy` | `mvn clean deploy` – build + copy to the server |
| `.\restart` | `mvn antrun:run@restart-t24` – restart T24 / JBoss |
| `.\deploy-restart` | both, restart only if the deploy worked |

Design Studio / Eclipse: **Run → Run Configurations… → Maven Build** shows **T24 deploy**, **T24 restart**, **T24 deploy + restart** (files in `.launch/`); after the first run they are in the Run (▶) drop-down. Or Run As → Maven build… → Goals `clean deploy`. Then restart / refresh TAFJ so the new JAR is loaded – or with Maven, as a separate command: **`mvn antrun:run@restart-t24`** streams `deploy/restart_t24.sh` to the server over SSH (run in memory, no file saved there), which stops JBoss (`kill -15` on the `jboss-modules.jar … Transact.xml` process, waits up to `deploy.stop.wait` s, never `kill -9`), starts `standalone.sh -c Transact.xml` with nohup (log `/tmp/tafj-startup.log`) and waits for `WFLYSRV0025` (started) up to `deploy.start.wait` s. A restart affects everyone on DEV. Before **production** set `Constants.LOG_DETAIL_ENABLED = false` (already the default).

---

## 6. EB.API records

One EB.API record per routine. Suggested IDs (any naming standard of the team can be used):

| EB.API id | Java package | Java class | Hook method |
|---|---|---|---|
| ALBE.CL.LIMIT.CREATE | `com.temenos.ALBE.CustomerLimit.controller.customer` | `CustomerLimitCreationController` | `postUpdateRequest` |
| ALBE.CL.DAILY.MONTHLY | `com.temenos.ALBE.CustomerLimit.controller.activity` | `DailyMonthlyLimitController` | `validateRecord`, `postCoreTableUpdate` |
| ALBE.CL.MAX.CIF | `com.temenos.ALBE.CustomerLimit.controller.activity` | `MaximumCifLimitController` | `validateRecord` |
| **ALBE.CL.ACTIVITY.LIMIT** *(single routine, D44)* | `com.temenos.ALBE.CustomerLimit.controller.activity` | `CustomerLimitActivityController` | `validateRecord` |

Typical EB.API fields: SOURCE.TYPE = `JAVA`, JAVA.PACKAGE, JAVA.CLASS, JAVA.METHOD **(confirm)**.
If your release needs one EB.API record per hook method, create two for `DailyMonthlyLimitController`
(`ALBE.CL.DAILY.MONTHLY.VAL` → `validateRecord`, `ALBE.CL.DAILY.MONTHLY.UPD` → `postCoreTableUpdate`).
**`ALBE.CL.DAILY.MONTHLY.DEF` (`defaultFieldValues`) is no longer used (D42, 26-Nov)** – remove it from Record Rtn in ACTIVITY.API.
**`ALBE.CL.ACTIVITY.LIMIT`** (D44) replaces VAL + MAX.CIF + UPD with one routine; easiest: copy `ALBE.CL.DAILY.MONTHLY.VAL` and change the Java class to `CustomerLimitActivityController` (method stays `validateRecord`). The old three stay until after SIT (rollback by configuration).

---

## 7. VERSION CUSTOMER,ALBE.CORP – customer limit creation

| Field | Value |
|---|---|
| **AUTH.ROUTINE** | `ALBE.CL.LIMIT.CREATE` (with `@` prefix if your release needs it) |
| AFTER.UNAU.RTN | **nothing** for this routine (it was After Unau in the HLSD; changed to Auth) |

Result: when a customer is **authorised** through this version, EB.ALBE.CIF.LIMITS `<customer>` is created from GEN.PARAM `CUSTOMER.LIMIT` – only if it does not exist. Amending and re-authorising the customer leaves the limit record unchanged.

> Customers created through **other** CUSTOMER versions will not get a limit record. Attach the same routine to those versions if they also need one.

---

## 8. ACTIVITY.API – checks and utilisation

Attach on the **ACTIVITY.API** property condition of every AA product that needs the validation (HLSD: "products for which user wants to have this validation").

**Set-up proven in DEV (26-Nov-2026, D43)** – AA.PRD.DES.ACTIVITY.API, one set per activity, **all routines in Post Routine**, in this order:

| Activity Id | Property Class | Pc Action | Post Routine.1 | Post Routine.2 | Post Routine.3 |
|---|---|---|---|---|---|
| ACCOUNTS-TRANSFERIN-ARRANGEMENT (credit) | ACCOUNT | CR.MOVEMENT | ALBE.CL.DAILY.MONTHLY.VAL | ALBE.CL.MAX.CIF | ALBE.CL.DAILY.MONTHLY.UPD |
| ACCOUNTS-TRANSFEROUT-ARRANGEMENT (debit) | ACCOUNT | DR.MOVEMENT | ALBE.CL.DAILY.MONTHLY.VAL | ALBE.CL.DAILY.MONTHLY.UPD | |

Same pattern for any other mapped activity (e.g. ACCOUNTS-CREDIT-ARRANGEMENT / ACCOUNTS-DEBIT-ARRANGEMENT).
Pre Routine, Record Rtn, Validate Rtn and Pre Validate Rtn stay **blank**.

**Single routine (D44, 26-Nov) – target set-up.** One Post Routine does everything:

| Activity Id | Property Class | Pc Action | Post Routine.1 | Other routine fields |
|---|---|---|---|---|
| ACCOUNTS-TRANSFERIN-ARRANGEMENT (credit) | ACCOUNT | CR.MOVEMENT | **ALBE.CL.ACTIVITY.LIMIT** | blank |
| ACCOUNTS-TRANSFEROUT-ARRANGEMENT (debit) | ACCOUNT | DR.MOVEMENT | **ALBE.CL.ACTIVITY.LIMIT** | blank |

| Status | api.log `ACTION` line | What it does |
|---|---|---|
| UNAUTH | `INPUT` | daily / monthly check, max CIF (credit), overrides, then count (not when blocked) |
| DELETE | `DELETE` | take back |
| AUTH-REV | `REVERSAL` | take back |
| AUTH | `AUTHORISE` | nothing, no read |
| REVERSE | `REVERSAL INPUT` | nothing, no read (taken back at AUTH-REV) |

- **Prerequisite (DEV, TQ20):** T24 must call the Post Routine `validateRecord` for **DELETE and AUTH-REV** too – look for `CustomerLimitActivityController.validateRecord | ACTION | DELETE …` / `REVERSAL …`. If those lines never come, go back to VAL + MAX.CIF + UPD (above).
- Reads per call (no cache, each record once) **on a first input**: debit about 4 (mapping, CIF.LIMITS, daily + monthly UTILISED, TXN.DETAILS); credit the same + CUSTOMER.ACCOUNT and the customer's accounts when MAX.CIF is filled. A re-input / second call of the same FT reads again (the earlier count is taken back first).
- Take-back (DELETE / AUTH-REV) only takes back what TXN.DETAILS shows was counted – a second call finds nothing and changes nothing (`NOTHING TAKEN BACK`).
- **TQ22 (DEV):** override declined / FT cancelled after our Post Routine – UTILISED.LIMIT must be unchanged.
- Save failure → `setError EB-ALBE.LIMIT.UPDATE.FAILED` (blocks; DEV test TQ18).
- **Rollback:** put VAL / MAX.CIF / UPD back in the Post Routines, proof and publish – no redeploy.

- **Why Post Routine:** for FT-created activities T24 calls **only the Post Routine** – Pre, Record and Validate routines are never called (DEV 25/26-Nov). A `validateRecord` routine (VAL, MAX.CIF) attached as a Post Routine runs there and its override **is shown on the FT screen** (DEV 26-Nov, FT26093GZC6N). Record Rtn / Pre Validate Rtn reject a `validateRecord` routine ("Method does not implement a hook interface").
- **Order matters:** the checks (VAL, MAX.CIF) **before** UPD – they compare "used so far + this transaction" with the limit; UPD then counts the transaction.
- **No Record Rtn (DEF) any more** (D42): REMARKS is not touched.
- Only `validateRecord` can raise an **override** (R25 invokers: only the validate invoker handles overrides; `postCoreTableUpdate` / `defaultFieldValues` / VERSION `postUpdateRequest` can only stop the transaction with an error).
- **The routines can be attached to any activity.** An activity is counted when (1) its **ACTIVITY** is mapped to `CREDIT` or `DEBIT` in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` (step 3a, D41 – not mapped → error EB-ALBE.ACTIVITY.NOT.MAPPED); (2) it has an amount – ORIG.TXN.AMT if filled, else TXN.AMOUNT; (3) its INITIATION.TYPE is in `Constants.COUNTED_INITIATION_TYPES` (all types today). Initiation type / amount missing → skipped. New activity = new line in GEN.PARAM `CUSTOMER.LIMIT.ACTIVITY` + attach the routines here (no code change).
- **Date:** the activity's **ORG.SYSTEM.DATE** (T24 date), else T24 TODAY (server clock only as a logged last resort). Not DATE.TIME – that is the server clock.
- **COB (check):** AA.DECISION.PARAMETER UNAUTH.PROCESS. With `IGNORE.TRANSACTION.ACTIVITIES` unauthorised credits/debits stay over COB. With `DEFAULT` COB deletes them (our DELETE reverses) and re-initiates them after COB (counted again; the REMARKS tag, if kept, keeps the original day).
- The maximum CIF check is for **credit** activities only.
- ⚠ If `postCoreTableUpdate` does not fire on DELETE (rejected input) or AUTH-REV (reversal), the utilisation is **not** reversed. Test both (step 13).
- Rebuild / re-authorise the product (AA.PRODUCT.DESIGNER proof and publish) as usual so the arrangements pick up the new condition.

---

## 8a. FT, TELLER and other transactions – no extra routine

An FT, a teller transaction or any other posting on an **AA account** makes T24 create an `ACCOUNTS-CREDIT-ARRANGEMENT` / `ACCOUNTS-DEBIT-ARRANGEMENT` activity on that account, so the ACTIVITY.API routines of step 8 cover them. No routine is attached to FUNDS.TRANSFER / TELLER versions or ACCOUNT.PARAMETER (those routines were built on 21/22-Nov-2026 and removed the same day; they are in the Git history).

- The activity of an FT carries TXN.CONTRACT.ID = the FT id, so each activity of an FT is counted under `<customer>-<date>-<FT id>-<arrangement id>`.
- **Own-account transfer** (same customer on both sides): two arrangements, two records – the debit and the credit are **both checked and counted** (D39), including the max CIF check on the credit.
- There are no non-AA (classic) accounts (D34).
- **DEV 25/26-Nov:** activity created at FT **input** ✔; activity overrides shown on the FT screen ✔ (routines as Post Routine, step 8); FT reversal input reaches the activity as REVERSE; its authorisation is expected as AUTH-REV (take-back still to see in DEV); FT delete → DELETE (to test).

---

## 9. Versions for EB.ALBE.CIF.LIMITS and EB.ALBE.GEN.PARAM

HLSD: "Version for amending EB.ALBE.CIF.LIMITS to be added to user menu." The menu itself is step 15.

| VERSION | Purpose | Fields shown | Authorisers | Notes |
|---|---|---|---|---|
| `EB.ALBE.CIF.LIMITS,AMEND` | amend one customer's limits | CURRENCY, DAILY.CREDIT.LIMIT, DAILY.DEBIT.LIMIT, MONTHLY.CREDIT.LIMIT, MONTHLY.DEBIT.LIMIT, MAXIMUM.OVERALL.CIF.AMOUNT | ≥ 1 | ID = customer id; record normally exists (created at customer authorisation) |
| `EB.ALBE.CIF.LIMITS,VIEW` *(optional)* | look at a customer's limits | same, all no-input | – | for front-office users who must not amend |
| `EB.ALBE.GEN.PARAM,LIMIT` | maintain the generic limits record `CUSTOMER.LIMIT` | KEY.NAME, KEY.VALUE | ≥ 1 | restrict to parameter administrators; changes affect **new** customers only |

- An amended EB.ALBE.CIF.LIMITS record is never overwritten by the creation routine.
- Version names are suggestions – use the team's naming standard.

---

## 10. Logging

- The code logs through logger **`API`** (`Constants.LOGGER_NAME`). TAFJ maps it onto **`ALERT.API`** (errors) and **`DIAGNOSTIC.API`** (info / detail); both write to **`TAFJ/log/api.log`** on the **T24 server**.
- By default both are at **ERROR**, so only our ERROR / FAILED lines appear (seen in DEV 24-Nov-2026). To see the INFO lines (ACTIVITY, RESULT, OVERRIDE, UPDATED …) in DEV / SIT, set on the server, in `TAFJ/conf/TAFJTrace.properties`:
  ```
  logger.DIAGNOSTIC_API.level = INFO
  ```
  and restart T24 / TAFJ. Put it back to `ERROR` in production.
- Every line starts with **`ALBE.CUSTOMER.LIMIT |`**. Monitor in SIT and production:
  ```
  grep -E "ALBE.CUSTOMER.LIMIT.*\| (ERROR|FAILED) \|" <tafj log>
  ```
  A technical failure does **not** block the transaction, so these lines are the only signal that a check was skipped.
- **What you see per transaction (INFO, 25-Nov-2026):** one `ACTIVITY` line per hook (the activity as T24 sent it), one `RESULT` line per step in plain words (the check with its numbers, the overrides / errors raised, what was counted or taken back), plus `OVERRIDE` (exact text sent) and `UPDATED` / `WRITTEN` (records changed). Errors (NOT MAPPED, NO CCY, FAILED, BLOCK) are always written.
  ```
  ACTIVITY | hook=validateRecord status=UNAUTH aaa=AAACT26093... activity=ACCOUNTS-CREDIT-ARRANGEMENT class=... arrangement=AA26093XZ06K customer=100199 contract=FT26093BGWJR txnAmount=11000 ... remarks='ALBE.LIMIT.DATE=20260403'
  RESULT   | CHECK daily/monthly FT26093BGWJR (ACCOUNTS-CREDIT-ARRANGEMENT) customer 100199 CREDIT 11,000.00 EGP on 2026-04-03 arrangement AA26093XZ06K | daily credit (100199-20260403): used 10,000.00 + this 11,000.00 = 21,000.00 > limit 10,000.00 -> EXCEEDED by 11,000.00 | monthly credit (100199-202604): ... -> EXCEEDED by 11,000.00 | raised: EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED, EB-ABLE.MONTHLY.CREDIT.LIMIT.EXCEED
  OVERRIDE | override=EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED sent=[EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED,100199 by 11000.00]
  RESULT   | COUNTED FT26093BGWJR ... | daily 100199-20260403 used credit 10,000.00 -> 21,000.00 | monthly 100199-202604 used credit 10,000.00 -> 21,000.00 | saved EB.ALBE.TXN.DETAILS.LIMIT 100199-20260403-FT26093BGWJR-AA26093XZ06K
  ```
  Find one transaction: `grep "ALBE.CUSTOMER.LIMIT" api.log | grep FT26093BGWJR`; only the outcomes: `grep "| RESULT |"`.
- `Constants.LOG_DETAIL_ENABLED` (default **false**) adds the technical trace (START / CALC / END / OK / BREACH / SKIP / FOUND and every DB read / write). Turn it on only when asked by development.

---

## 11. Data before go-live

| Item | What to do |
|---|---|
| **Existing customers** | The creation routine only runs when a customer is authorised. Existing customers need EB.ALBE.CIF.LIMITS records from a **one-off load** (separate flow – e.g. copy GEN.PARAM `CUSTOMER.LIMIT` for every customer, or the bank's own limits). Without a record every transaction gets the "limit not defined" override. |
| **Migrated monthly utilisation** | If the current month's totals come from the old system, load them into UTILISED.MIGRATED.CREDIT.LIMIT / UTILISED.MIGRATED.DEBIT.LIMIT of `<customer>-YYYYMM`. The routine adds them to the monthly total and never changes them. |
| **Reversal of transactions made before go-live** | They have no EB.ALBE.TXN.DETAILS.LIMIT record; an AUTH-REV then subtracts the amount from **today's** records (floored at 0). No action needed, just be aware. |

---

## 12. Exchange rate (pending)

Needed only when the transaction (or an account) currency differs from the customer's limit currency.

**Transaction amount (D44, 26-Nov) – no rate table needed:** taken from the activity only:

| Case | Amount used |
|---|---|
| same currency | the amount |
| limit in LCY (EGP), LCY amount on the activity | TXN.AMOUNT.LCY / ORIG.TXN.AMT.LCY |
| limit in LCY, no LCY amount, TXN.EXCH.RATE filled | amount × TXN.EXCH.RATE (check in DEV: 1 transaction currency = rate LCY) |
| no LCY amount and no TXN.EXCH.RATE, or limit **not** in LCY | **EB.ERROR EB-ALBE.EXCH.RATE.MISSING – transaction blocked** |

**Max CIF – other accounts in another currency (parked):**
1. Bank to confirm the exchange-rate table, record id and rate field (rate for 1 unit of *from* currency in *to* currency).
2. Developer sets `Constants.EXCH_RATE_TABLE`, `Constants.EXCH_RATE_FIELD` and completes `CustomerLimitRepository.readExchangeRate(fromCcy, toCcy)`.
3. Until then: the **EB-ABLE.EXCH.RATE.MISSING** override and the max CIF check is skipped.

---

## 13. DEV / SIT test cases

Set up a test customer with an EB.ALBE.CIF.LIMITS record, e.g. daily credit 5000, monthly credit 15000, MAXIMUM.OVERALL.CIF.AMOUNT 100000, all in the arrangement currency.

| # | Test | Expected result | Log line to look for |
|---|---|---|---|
| 1 | Authorise a **new** customer via CUSTOMER,ALBE.CORP | EB.ALBE.CIF.LIMITS `<customer>` = GEN.PARAM values | `CustomerLimitCreationController.postUpdateRequest \| END \| ... created=true` |
| 2 | Amend the limit, then amend + authorise the customer | limit record unchanged | `CustomerLimitCreationService.create \| SKIP \| ... already exists` |
| 3 | Credit 3000 (input) | no override; daily + monthly UTILISED.CREDIT.LIMIT = 3000; TXN.DETAILS `<cust>-<today>-<FT/AAA>-<arrangement>`; activity REMARKS ends with `ALBE.LIMIT.DATE=<today>` | `checkLimit \| OK`, `addToRecord \| UPDATED` |
| 4 | Credit 4000 (input) | override EB-ABLE.DAILY.CREDIT.LIMIT.EXCEED "... by 2000" | `checkLimit \| BREACH` |
| 5 | Accept the override and commit test 4 | utilisation 7000 | `postCoreTableUpdate \| ACTION \| COUNT` |
| 6 | **Reject / delete** the unauthorised transaction of test 4 | utilisation back to 3000; its TXN.DETAILS record deleted | `ACTION \| REVERSE rejected input` |
| 7 | Amend an unauthorised transaction's amount (re-input) | counted once with the new amount, not twice | `validate \| CALC \| earlierInputs=1` |
| 8 | Authorise test 3 | no change to utilisation | `postCoreTableUpdate \| SKIP \| status=AUTH` |
| 9 | **Reverse** the authorised transaction of test 3 (next day if possible) | 3000 subtracted from the **original** day's and month's records | `ACTION \| REVERSE reversal` |
| 10 | Debit over the daily debit limit | EB-ABLE.DAILY.DEBIT.LIMIT.EXCEED | `checkLimit \| BREACH \| DAILY.DEBIT.LIMIT` |
| 11 | Monthly breach (with a migrated amount loaded) | EB-ABLE.MONTHLY.CREDIT (or DEBIT).LIMIT.EXCEED; MIGRATED field unchanged | `validateMonthlyLimit \| CALC \| ... migrated=` |
| 12 | Credit that takes total balance over MAX.CIF.BALANCE | EB-ABLE.MAX.CIF.BAL.EXCEED "... by <difference>" | `MaximumCifLimitService.validate \| BREACH` |
| 13 | Customer **without** EB.ALBE.CIF.LIMITS | EB-ABLE.CIF.LIMIT.NOT.DEFINED | `getCustomerLimits \| NOT FOUND` |
| 14 | Blank DAILY.CREDIT.LIMIT | EB-ABLE.CIF.LIMIT.NOT.DEFINED (once) | `checkLimit \| NOT DEFINED` |
| 15 | Transaction in another currency (before step 12) | EB-ABLE.EXCH.RATE.MISSING "from USD to EGP" | `convertRequest \| NO RATE` |
| 16 | Any test | no `ERROR` / `FAILED` lines | `grep "\| ERROR \|\|\| FAILED \|"` |

Also note in the DEV log (once):

- the AAA id format (test 3) – the TXN.DETAILS id must fit;
- the override text shows both `&` values (test 4);
- the T24 "record not found" text for a missing EB.ALBE.CIF.LIMITS (test 1). If you see `cifLimitExists | FAILED` instead of `NOT FOUND`, add the word from that log line to `Constants.RECORD_NOT_FOUND_MARKERS`.

---

## 14. Reports – removed (26-Nov-2026)

The three NOFILE enquiries (summary, period, transactions) were removed from the code. Use standard enquiries / listings on EB.ALBE.UTILISED.LIMIT and EB.ALBE.TXN.DETAILS.LIMIT if needed.

---

## 15. User menu and access

### 15.1 Menu entries

Add a **Customer Limits** sub-menu to the relevant user menus (HELPTEXT.MENU / Browser menu, as used for the bank's other menus) **(confirm)**:

```
Customer Limits
 ├─ Maintain
 │   ├─ Amend customer limits          EB.ALBE.CIF.LIMITS,AMEND   I
 │   ├─ View customer limits           EB.ALBE.CIF.LIMITS,VIEW    S      (optional)
 │   └─ Generic limits (parameter)     EB.ALBE.GEN.PARAM,LIMIT   I      (administrators only)
```

| Menu item | Type | Application | Function |
|---|---|---|---|
| Amend customer limits | version | `EB.ALBE.CIF.LIMITS,AMEND` | input (I) |
| View customer limits | version | `EB.ALBE.CIF.LIMITS,VIEW` | see (S) |
| Generic limits | version | `EB.ALBE.GEN.PARAM,LIMIT` | input (I), record `CUSTOMER.LIMIT` |

### 15.2 Who gets what (suggested)

| Role | Maintain |
|---|---|
| Branch / front office | View customer limits |
| Limit / risk officer | Amend customer limits |
| Authoriser | authorise EB.ALBE.CIF.LIMITS changes |
| Parameter administrator | Generic limits |
| Compliance / audit | View |

Give access through the bank's usual user / role set-up (USER, SMS groups) so only these roles can open the versions **(confirm)**. The bank decides the final role list.

### 15.3 Checks

| # | Check | Expected |
|---|---|---|
| 1 | Log in as each role | sees only its menu items |
| 2 | Amend a limit via the menu, authorise it | new limit used by the next transaction |
| 3 | Front-office user opens EB.ALBE.CIF.LIMITS,AMEND directly | refused by security |
