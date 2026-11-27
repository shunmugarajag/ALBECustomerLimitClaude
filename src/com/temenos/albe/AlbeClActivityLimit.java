package com.temenos.albe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.temenos.api.TField;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;
import com.temenos.t24.api.records.ebalbeciflimit.EbAlbeCifLimitRecord;
import com.temenos.t24.api.records.ebalbegenparam.EbAlbeGenParamRecord;
import com.temenos.t24.api.records.ebalbegenparam.KeyNameClass;
import com.temenos.t24.api.records.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitRecord;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebalbetxndetailslimit.EbAlbeTxnDetailsLimitTable;
import com.temenos.t24.api.tables.ebalbeutilisedlimit.EbAlbeUtilisedLimitTable;

/*-----------------------------------------------------------------------------
 * Attached as : ACTIVITY.API - Post Routine (one per mapped activity), method validateRecord
 * EB.API      : ALBE.CL.ACTIVITY.LIMIT (credits also get ALBE.CL.MAX.CIF as Post Routine 2)
 * Attached to : every activity mapped CREDIT / DEBIT in EB.ALBE.GEN.PARAM CUSTOMER.LIMIT.ACTIVITY
 *               (e.g. ACCOUNTS-TRANSFERIN-ARRANGEMENT, ACCOUNTS-TRANSFEROUT-ARRANGEMENT)
 * Description : Customer limit check, daily and monthly, HLSD 7.2.1 - 2.
 *
 *   UNAUTH (input / amendment)
 *     1. EB.ALBE.CIF.LIMIT <customer>   none -> skipped: no check, no override, not counted
 *                                        CURRENCY blank -> ERROR, not counted
 *     2. amount in the limit currency: LCY amount, else x TXN.EXCH.RATE, else ERROR
 *     3. earlier input of this FT: EB.ALBE.TXN.DETAILS.LIMIT <cust>-<date>-<FT>-<arrangement>
 *     4. daily   : used today - earlier + this            > DAILY.<CR|DR>.LIMIT   -> override
 *        monthly : migrated + used month - earlier + this  > MONTHLY.<CR|DR>.LIMIT -> override
 *        a blank limit skips only that check (no override); the amount is still counted
 *        (maximum CIF balance: separate routine AlbeClMaxCif, 2nd Post Routine on credits)
 *     5. count   : daily / monthly = used - earlier + this; TXN.DETAILS written
 *   DELETE / AUTH-REV : take back what TXN.DETAILS shows was counted, from the counted day
 *   AUTH / REVERSE    : nothing
 *
 *   The count is in the same commit as the FT: a rejected override is rolled back by T24.
 *   A failed save raises EB-ALBE.LIMIT.UPDATE.FAILED (blocks); other technical errors are logged.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 26-Nov-2026   Development      Initial Version
 * 26-Nov-2026   Development      Max CIF moved to its own routine AlbeClMaxCif (user)
 * 26-Nov-2026   Development      Package com.temenos.albe, class named after the EB.API (MVC version removed)
 * 27-Nov-2026   Development      No limit record -> skipped; blank limit -> that check skipped, no override (user, D52)
 * 27-Nov-2026   Development      Old EB-ALBE overrides removed from the activity before each new check on input
 *                                (re-validate showed old + new, FT26093000F5); each routine only its own ids (user, D56)
 *-----------------------------------------------------------------------------
 */
public class AlbeClActivityLimit extends ActivityLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(AlbeClConstants.LOGGER_NAME);
    private static final String NAME = "AlbeClActivityLimit:";

    @Override
    public TValidationResponse validateRecord(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord aaaRec, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure currRecord) {

        String status = "";
        try {
            status = nvl(arrangementContext.getActivityStatus()).trim().toUpperCase();
            String activity = nvl(aaaRec.getActivity().getValue()).trim();
            String ft = rootReference(aaaRec.getTxnContractId().getValue());
            String key = ft.isEmpty() ? nvl(arrangementContext.getArrangementActivityId()).trim() : ft;
            String arrangement = nvl(aaaRec.getArrangement().getValue()).trim();
            int removed = AlbeClConstants.STATUS_UNAUTH.equals(status)
                    ? removeOldOverrides(aaaRec, AlbeClConstants.ACTIVITY_LIMIT_OVERRIDES) : 0;
            if (removed > 0) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " OVERRIDES | " + key + " removed " + removed +
                        " old daily / monthly override(s) before the new check");
            }
            logger.info(AlbeClConstants.LOG_TAG + NAME + " ACTION | " +
                    actionOf(status) + " " + key + " (" + activity + ") arrangement " + arrangement
                    + " status " + status);

            if (!AlbeClConstants.STATUS_UNAUTH.equals(status)
                    && !AlbeClConstants.STATUS_DELETE.equals(status)
                    && !AlbeClConstants.STATUS_AUTH_REV.equals(status)) {
                return aaaRec.getValidationResponse();
            }

            DataAccess da = new DataAccess(this);
            Session ss = new Session(this);

            // credit or debit from the mapping; not mapped -> error at input
            String direction = readDirection(da, activity);
            if (direction == null) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " NOT MAPPED | " +
                        activity + " not in " + AlbeClConstants.GEN_PARAM_ACTIVITY_ID);
                if (AlbeClConstants.STATUS_UNAUTH.equals(status)) {
                    aaaRec.getTxnAmount().setError(text(AlbeClConstants.ERR_NOT_MAPPED, activity));
                }
                return aaaRec.getValidationResponse();
            }
            String initiation = initiationType(aaaRec);
            if (!initiation.isEmpty() && !AlbeClConstants.COUNTED_INITIATION_TYPES.contains(initiation)) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " SKIP | " +
                        key + " initiation type " + initiation + " not counted");
                return aaaRec.getValidationResponse();
            }

            // amount: ORIG.TXN.AMT when filled, else TXN.AMOUNT
            boolean useOrig = toAmount(aaaRec.getOrigTxnAmt().getValue()).signum() > 0;
            BigDecimal amount = toAmount(useOrig ? aaaRec.getOrigTxnAmt().getValue() : aaaRec.getTxnAmount().getValue());
            if (amount.signum() <= 0) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " SKIP | " + key + " no amount");
                return aaaRec.getValidationResponse();
            }
            boolean credit = AlbeClConstants.CREDIT.equals(direction);
            String customer = arrangementRecord.getCustomer().get(0).getCustomer().getValue();
            LocalDate date = countedDate(aaaRec, ss);

            boolean[] saveFailed = { false };
            if (AlbeClConstants.STATUS_UNAUTH.equals(status)) {
                input(da, ss, aaaRec, arrangementRecord, customer, key, arrangement, date, credit, amount, useOrig,
                        saveFailed);
            } else {
                takeBack(da, customer, key, arrangement, date, status, saveFailed);
            }
            if (saveFailed[0]) {
                logger.info(AlbeClConstants.LOG_TAG + NAME + " BLOCKED | " +
                        key + " save failed, raising " + AlbeClConstants.ERR_UPDATE_FAILED);
                aaaRec.getTxnAmount().setError(AlbeClConstants.ERR_UPDATE_FAILED);
            }
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " ERROR | status " + status + " " + e);
        }
        return aaaRec.getValidationResponse();
    }

    // =========================================================================
    // Input: check, overrides, count
    // =========================================================================

    private void input(DataAccess da, Session ss, AaArrangementActivityRecord aaaRec,
            AaArrangementRecord arrangementRecord, String customer, String key, String arrangement, LocalDate date,
            boolean credit, BigDecimal txnAmount, boolean useOrig, boolean[] saveFailed) {

        StringBuilder result = new StringBuilder("INPUT " + key + " customer " + customer + (credit ? " CREDIT " : " DEBIT ")
                + txnAmount + " on " + date);
        List<String[]> overrides = new ArrayList<>();   // {override id, parameter, field}

        // 1. customer limits
        EbAlbeCifLimitRecord limits = readCifLimits(da, customer);
        if (limits == null) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " + result + " | no "
                    + AlbeClConstants.CIF_LIMIT_TABLE + " record -> skipped, not checked, not counted");
            return;
        }
        String limitCcy = nvl(limits.getCurrency().getValue()).trim();
        if (limitCcy.isEmpty()) {
            aaaRec.getTxnAmount().setError(text(AlbeClConstants.ERR_CCY_MISSING, customer));
            logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " +
                    result + " | BLOCKED: limit CURRENCY blank, not counted");
            return;
        }

        // 2. amount in the limit currency
        BigDecimal amount = txnAmount.setScale(AlbeClConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
        String txnCcy = nvl(aaaRec.getCurrency().getValue()).trim();
        if (txnCcy.isEmpty()) {
            txnCcy = nvl(arrangementRecord.getCurrency().getValue()).trim();
        }
        if (!txnCcy.isEmpty() && !txnCcy.equalsIgnoreCase(limitCcy)) {
            String fromTo = AlbeClConstants.FROM + txnCcy + AlbeClConstants.TO + limitCcy;
            String lcy = nvl(ss.getLocalCurrency()).trim();
            if (lcy.isEmpty()) {
                aaaRec.getEffectiveDate().setOverride(text(AlbeClConstants.OVR_RATE_MISSING, fromTo));
                logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " +
                        result + " | local currency not readable -> not checked, not counted");
                return;
            }
            BigDecimal lcyAmount = toAmount(useOrig ? aaaRec.getOrigTxnAmtLcy().getValue()
                    : aaaRec.getTxnAmountLcy().getValue());
            BigDecimal rate = toAmount(aaaRec.getTxnExchRate().getValue());
            if (!limitCcy.equalsIgnoreCase(lcy) || (lcyAmount.signum() <= 0 && rate.signum() <= 0)) {
                aaaRec.getTxnAmount().setError(text(AlbeClConstants.ERR_RATE_MISSING, fromTo));
                logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " +
                        result + " | BLOCKED: cannot convert " + fromTo + ", not counted");
                return;
            }
            amount = (lcyAmount.signum() > 0 ? lcyAmount : txnAmount.multiply(rate))
                    .setScale(AlbeClConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
            result.append(" | converted to ").append(amount).append(' ').append(limitCcy);
        }

        // 3. earlier input of this FT (amendment): replaced, not added
        EbAlbeTxnDetailsLimitTable txnTable = new EbAlbeTxnDetailsLimitTable(this);
        String txnId = txnDetailsId(customer, date, key, arrangement);
        EbAlbeTxnDetailsLimitRecord earlierRec = readTxnDetails(txnTable, txnId);
        BigDecimal earlierCr = BigDecimal.ZERO;
        BigDecimal earlierDr = BigDecimal.ZERO;
        if (earlierRec != null) {
            BigDecimal earlier = toAmount(earlierRec.getTxnAmount().getValue());
            if (AlbeClConstants.FLAG_CREDIT.equals(earlierRec.getCreditDebitFlag().getValue())) {
                earlierCr = earlier;
            } else {
                earlierDr = earlier;
            }
            result.append(" | re-input: earlier ").append(earlier).append(" replaced");
        }
        BigDecimal earlier = credit ? earlierCr : earlierDr;

        // 4. daily and monthly (max CIF: routine AlbeClMaxCif)
        EbAlbeUtilisedLimitTable utilTable = new EbAlbeUtilisedLimitTable(this);
        String dailyId = customer + AlbeClConstants.ID_SEPARATOR + date.format(AlbeClConstants.DAY_FORMAT);
        String monthlyId = customer + AlbeClConstants.ID_SEPARATOR + date.format(AlbeClConstants.MONTH_FORMAT);
        EbAlbeUtilisedLimitRecord dailyRec = readUtilised(utilTable, dailyId);
        EbAlbeUtilisedLimitRecord monthlyRec = readUtilised(utilTable, monthlyId);

        BigDecimal usedDay = used(dailyRec, credit).subtract(earlier).max(BigDecimal.ZERO);
        BigDecimal usedMonth = used(monthlyRec, credit).subtract(earlier).max(BigDecimal.ZERO)
                .add(migrated(monthlyRec, credit));
        check(result, overrides, "daily", customer, usedDay, amount,
                credit ? limits.getDailyCreditLimit().getValue() : limits.getDailyDebitLimit().getValue(),
                credit ? AlbeClConstants.OVR_DAILY_CREDIT : AlbeClConstants.OVR_DAILY_DEBIT, "TXN.AMOUNT");
        check(result, overrides, "monthly", customer, usedMonth, amount,
                credit ? limits.getMonthlyCreditLimit().getValue() : limits.getMonthlyDebitLimit().getValue(),
                credit ? AlbeClConstants.OVR_MONTHLY_CREDIT : AlbeClConstants.OVR_MONTHLY_DEBIT,
                "TXN.AMOUNT.LCY");

        // 5. count: used - earlier + this, each record written once
        BigDecimal deltaCr = (credit ? amount : BigDecimal.ZERO).subtract(earlierCr);
        BigDecimal deltaDr = (credit ? BigDecimal.ZERO : amount).subtract(earlierDr);
        add(utilTable, dailyId, dailyRec, deltaCr, deltaDr, result, saveFailed);
        add(utilTable, monthlyId, monthlyRec, deltaCr, deltaDr, result, saveFailed);
        writeTxnDetails(txnTable, txnId, accountNo(arrangementRecord), aaaRec.getEffectiveDate().getValue(), credit,
                amount, result, saveFailed);

        // overrides, each on its own AAA field (a field keeps only one override)
        for (String[] o : overrides) {
            fieldFor(aaaRec, o[2]).setOverride(text(o[0], o[1]));
            result.append(" | raised ").append(o[0]);
        }
        logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " +
                result + " | count kept only if the transaction is committed");
    }

    /**
     * used + this > limit -> override "<customer> by <excess>"; equal is allowed;
     * a blank limit is not checked (no override).
     */
    private void check(StringBuilder result, List<String[]> overrides, String label, String customer,
            BigDecimal used, BigDecimal amount, String limitText, String overrideId, String field) {
        if (isBlank(limitText)) {
            result.append(" | ").append(label).append(": limit blank -> not checked");
            return;
        }
        BigDecimal limit = toAmount(limitText);
        BigDecimal total = used.add(amount);
        result.append(" | ").append(label).append(": used ").append(used).append(" + this ").append(amount)
                .append(" = ").append(total);
        if (total.compareTo(limit) > 0) {
            BigDecimal excess = total.subtract(limit).setScale(AlbeClConstants.AMOUNT_SCALE, RoundingMode.HALF_UP);
            result.append(" > ").append(limit).append(" EXCEEDED by ").append(excess);
            overrides.add(new String[] { overrideId, customer + AlbeClConstants.BY + excess.toPlainString(), field });
        } else {
            result.append(" <= ").append(limit).append(" OK");
        }
    }

    // =========================================================================
    // DELETE / AUTH-REV: take back what was counted
    // =========================================================================

    private void takeBack(DataAccess da, String customer, String key, String arrangement, LocalDate date,
            String status, boolean[] saveFailed) {
        EbAlbeTxnDetailsLimitTable txnTable = new EbAlbeTxnDetailsLimitTable(this);
        String txnId = txnDetailsId(customer, date, key, arrangement);
        EbAlbeTxnDetailsLimitRecord counted = readTxnDetails(txnTable, txnId);
        if (counted == null) {
            // reversal on a later day: find the day it was counted on
            LocalDate countedOn = findCountedDate(da, customer, key, arrangement);
            if (countedOn != null) {
                date = countedOn;
                txnId = txnDetailsId(customer, date, key, arrangement);
                counted = readTxnDetails(txnTable, txnId);
            }
        }
        if (counted == null) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | NOTHING TAKEN BACK " + key + " customer " + customer + " (nothing counted for it)");
            return;
        }
        BigDecimal amount = toAmount(counted.getTxnAmount().getValue());
        boolean credit = AlbeClConstants.FLAG_CREDIT.equals(counted.getCreditDebitFlag().getValue());
        StringBuilder result = new StringBuilder((AlbeClConstants.STATUS_DELETE.equals(status)
                ? "TAKEN BACK (input rejected) " : "TAKEN BACK (reversal) ") + key + " customer " + customer
                + (credit ? " CREDIT " : " DEBIT ") + amount + " counted on " + date);
        EbAlbeUtilisedLimitTable utilTable = new EbAlbeUtilisedLimitTable(this);
        String dailyId = customer + AlbeClConstants.ID_SEPARATOR + date.format(AlbeClConstants.DAY_FORMAT);
        String monthlyId = customer + AlbeClConstants.ID_SEPARATOR + date.format(AlbeClConstants.MONTH_FORMAT);
        BigDecimal cr = credit ? amount.negate() : BigDecimal.ZERO;
        BigDecimal dr = credit ? BigDecimal.ZERO : amount.negate();
        add(utilTable, dailyId, readUtilised(utilTable, dailyId), cr, dr, result, saveFailed);
        add(utilTable, monthlyId, readUtilised(utilTable, monthlyId), cr, dr, result, saveFailed);
        try {
            txnTable.delete(txnId);
            result.append(" | deleted ").append(AlbeClConstants.TXN_DETAILS_TABLE).append(' ').append(txnId);
        } catch (Exception e) {
            saveFailed[0] = true;
            result.append(" | DELETE FAILED ").append(txnId);
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | delete " + txnId + " " + e);
        }
        logger.info(AlbeClConstants.LOG_TAG + NAME + " RESULT | " + result.toString());
    }

    /** Day the transaction was counted on, from its TXN.DETAILS id (any date); null if none. */
    private LocalDate findCountedDate(DataAccess da, String customer, String key, String arrangement) {
        String sep = AlbeClConstants.ID_SEPARATOR;
        String selection = "WITH @ID LIKE " + customer + sep + "..." + sep + key + sep + arrangement;
        try {
            List<String> ids = da.selectRecords("", AlbeClConstants.TXN_DETAILS_TABLE, "", selection);
            logger.info(AlbeClConstants.LOG_TAG + NAME + " SELECT | " +
                    AlbeClConstants.TXN_DETAILS_TABLE + " [" + selection + "] found "
                    + (ids == null ? 0 : ids.size()));
            if (ids != null) {
                for (String id : ids) {
                    String[] parts = id.split(sep, 4);
                    LocalDate d = parts.length >= 3 ? toDate(parts[1]) : null;
                    if (d != null) {
                        return d;
                    }
                }
            }
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | select " + AlbeClConstants.TXN_DETAILS_TABLE + " " + e);
        }
        return null;
    }

    // =========================================================================
    // Reads and writes
    // =========================================================================

    /** CREDIT / DEBIT of the activity from GEN.PARAM CUSTOMER.LIMIT.ACTIVITY; null if not mapped. */
    private String readDirection(DataAccess da, String activity) {
        try {
            EbAlbeGenParamRecord param = new EbAlbeGenParamRecord(da.getRecord(AlbeClConstants.GEN_PARAM_TABLE,
                    AlbeClConstants.GEN_PARAM_ACTIVITY_ID));
            for (KeyNameClass k : param.getKeyName()) {
                if (activity.equals(nvl(k.getKeyName().getValue()).trim())) {
                    String value = nvl(k.getKeyValue().getValue()).trim().toUpperCase();
                    return AlbeClConstants.CREDIT.equals(value) || AlbeClConstants.DEBIT.equals(value)
                            ? value : null;
                }
            }
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " NOT FOUND | " +
                    AlbeClConstants.GEN_PARAM_TABLE + ">" + AlbeClConstants.GEN_PARAM_ACTIVITY_ID
                    + " " + e);
        }
        return null;
    }

    /** EB.ALBE.CIF.LIMIT <customer>; null when there is no record. */
    private EbAlbeCifLimitRecord readCifLimits(DataAccess da, String customer) {
        try {
            EbAlbeCifLimitRecord rec = new EbAlbeCifLimitRecord(da.getRecord(AlbeClConstants.CIF_LIMIT_TABLE,
                    customer));
            return rec.toString().isEmpty() ? null : rec;
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " NOT FOUND | " +
                    AlbeClConstants.CIF_LIMIT_TABLE + ">" + customer);
            return null;
        }
    }

    /** EB.ALBE.UTILISED.LIMIT <id>; null when there is none yet. */
    private EbAlbeUtilisedLimitRecord readUtilised(EbAlbeUtilisedLimitTable table, String id) {
        try {
            return table.read(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * EB.ALBE.TXN.DETAILS.LIMIT <id>; null when there is none. Read through the Table class so a record
     * written earlier in the same commit is found (DEV 26-Nov).
     */
    private EbAlbeTxnDetailsLimitRecord readTxnDetails(EbAlbeTxnDetailsLimitTable table, String id) {
        try {
            return table.read(id);
        } catch (Exception e) {
            return null;
        }
    }

    /** Adds credit / debit (negative to take back) to one utilisation record and writes it; never below 0. */
    private void add(EbAlbeUtilisedLimitTable table, String id, EbAlbeUtilisedLimitRecord rec, BigDecimal credit,
            BigDecimal debit, StringBuilder result, boolean[] saveFailed) {
        if (credit.signum() == 0 && debit.signum() == 0) {
            result.append(" | ").append(id).append(" unchanged");
            return;
        }
        try {
            EbAlbeUtilisedLimitRecord r = rec != null ? rec : new EbAlbeUtilisedLimitRecord(this);
            BigDecimal oldCr = toAmount(r.getUtilisedCreditLimit().getValue());
            BigDecimal oldDr = toAmount(r.getUtilisedDebitLimit().getValue());
            BigDecimal newCr = oldCr.add(credit).max(BigDecimal.ZERO);
            BigDecimal newDr = oldDr.add(debit).max(BigDecimal.ZERO);
            r.setUtilisedCreditLimit(newCr.toPlainString());
            r.setUtilisedDebitLimit(newDr.toPlainString());
            table.write(id, r);
            result.append(" | ").append(id).append(credit.signum() != 0 ? " credit " + oldCr + " -> " + newCr
                    : " debit " + oldDr + " -> " + newDr);
        } catch (Exception e) {
            saveFailed[0] = true;
            result.append(" | ").append(id).append(" SAVE FAILED");
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | write " + id + " " + e);
        }
    }

    /** Writes (or replaces) EB.ALBE.TXN.DETAILS.LIMIT for the counted transaction. */
    private void writeTxnDetails(EbAlbeTxnDetailsLimitTable table, String id, String accountNo, String effDate,
            boolean credit, BigDecimal amount, StringBuilder result, boolean[] saveFailed) {
        try {
            EbAlbeTxnDetailsLimitRecord r = new EbAlbeTxnDetailsLimitRecord(this);
            r.setAccountNo(nvl(accountNo));
            r.setCreditDebitFlag(credit ? AlbeClConstants.FLAG_CREDIT : AlbeClConstants.FLAG_DEBIT);
            r.setEffDate(nvl(effDate));
            r.setTxnAmount(amount.toPlainString());
            table.write(id, r);
            result.append(" | saved ").append(AlbeClConstants.TXN_DETAILS_TABLE).append(' ').append(id);
        } catch (Exception e) {
            saveFailed[0] = true;
            result.append(" | ").append(id).append(" SAVE FAILED");
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | write " + id + " " + e);
        }
    }

    // =========================================================================
    // Small helpers
    // =========================================================================

    private static String txnDetailsId(String customer, LocalDate date, String key, String arrangement) {
        String sep = AlbeClConstants.ID_SEPARATOR;
        return customer + sep + date.format(AlbeClConstants.DAY_FORMAT) + sep + key + sep + arrangement;
    }

    private static BigDecimal used(EbAlbeUtilisedLimitRecord rec, boolean credit) {
        return rec == null ? BigDecimal.ZERO
                : toAmount(credit ? rec.getUtilisedCreditLimit().getValue() : rec.getUtilisedDebitLimit().getValue());
    }

    private static BigDecimal migrated(EbAlbeUtilisedLimitRecord rec, boolean credit) {
        return rec == null ? BigDecimal.ZERO : toAmount(credit ? rec.getUtilisedMigratedCreditLimit().getValue()
                : rec.getUtilisedMigratedDebitLimit().getValue());
    }

    /** T24 date of the activity: ORG.SYSTEM.DATE, else T24 TODAY, else the server clock. */
    private static LocalDate countedDate(AaArrangementActivityRecord aaaRec, Session ss) {
        LocalDate d = toDate(aaaRec.getOrgSystemDate().getValue());
        if (d == null) {
            try {
                d = toDate(ss.getCurrentVariable(AlbeClConstants.T24_TODAY));
            } catch (Exception e) {
                d = null;
            }
        }
        return d != null ? d : LocalDate.now();
    }

    private static String initiationType(AaArrangementActivityRecord aaaRec) {
        try {
            List<TField> types = aaaRec.getInitiationType();
            return types == null || types.isEmpty() ? "" : nvl(types.get(0).getValue()).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String accountNo(AaArrangementRecord arrangementRecord) {
        try {
            return arrangementRecord.getLinkedAppl().get(0).getLinkedApplId().getValue();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Removes this routine's own overrides left on the activity by an earlier validation (re-validate, amendment),
     * so only the overrides of the current check are shown (D56). Only the ids passed in are removed: overrides of
     * the other ALBE routine and of other modules are kept, so the result does not depend on which routines are
     * attached or in which order.
     */
    private static int removeOldOverrides(AaArrangementActivityRecord aaaRec, String[] ownIds) {
        int removed = 0;
        try {
            List<TField> list = aaaRec.getOverride();
            for (int i = list.size() - 1; i >= 0; i--) {
                TField entry = list.get(i);
                String value = entry == null ? "" : nvl(entry.getValue()).trim();
                for (String id : ownIds) {
                    if (value.equals(id) || value.startsWith(id + AlbeClConstants.OVR_TEXT_SEPARATOR)) {
                        aaaRec.removeOverride(i);
                        removed++;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.info(AlbeClConstants.LOG_TAG + NAME + " FAILED | removing old overrides " + e);
        }
        return removed;
    }

    private static TField fieldFor(AaArrangementActivityRecord aaaRec, String field) {
        switch (field) {
            case "TXN.AMOUNT.LCY":
                return aaaRec.getTxnAmountLcy();
            case "ORIG.TXN.AMT":
                return aaaRec.getOrigTxnAmt();
            case "CURRENCY":
                return aaaRec.getCurrency();
            case "EFFECTIVE.DATE":
                return aaaRec.getEffectiveDate();
            default:
                return aaaRec.getTxnAmount();
        }
    }

    private static String actionOf(String status) {
        switch (status) {
            case AlbeClConstants.STATUS_UNAUTH:
                return AlbeClConstants.ACTION_INPUT;
            case AlbeClConstants.STATUS_DELETE:
                return AlbeClConstants.ACTION_DELETE;
            case AlbeClConstants.STATUS_AUTH_REV:
                return AlbeClConstants.ACTION_REVERSAL;
            case AlbeClConstants.STATUS_AUTH:
                return AlbeClConstants.ACTION_AUTHORISE;
            case AlbeClConstants.STATUS_REVERSE:
                return AlbeClConstants.ACTION_REVERSAL_INPUT;
            default:
                return status;
        }
    }

    /** Override / error text T24 expects: [ID,param] (one parameter; "," "[" "]" removed from it). */
    private static String text(String id, String param) {
        String p = nvl(param).replace(",", "").replace("[", "").replace("]", "").trim();
        return "[" + id + "," + p + "]";
    }

    /** "FT26091ABCDE\BNK" or "FT26091ABCDE;1" -> "FT26091ABCDE". */
    private static String rootReference(String reference) {
        String text = nvl(reference).trim();
        int cut = text.indexOf('\\');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        cut = text.indexOf(';');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        return text.trim();
    }

    private static BigDecimal toAmount(String value) {
        try {
            return isBlank(value) ? BigDecimal.ZERO : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate toDate(String value) {
        try {
            return isBlank(value) ? null : LocalDate.parse(value.trim(), AlbeClConstants.DATE_PARSE);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
