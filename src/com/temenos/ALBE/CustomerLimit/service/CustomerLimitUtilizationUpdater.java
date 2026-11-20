package com.temenos.ALBE.CustomerLimit.service;

import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.constants.Constants;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : Updates EB.ALBE.UTILISED.LIMIT for daily and monthly utilisation.
 *
 *               Fields: UTILISED.CREDIT.LIMIT, UTILISED.DEBIT.LIMIT,
 *                       UTILISED.MIGRATED.CREDIT.LIMIT, UTILISED.MIGRATED.DEBIT.LIMIT
 *
 *               Daily record   : <customer id>-YYYYMMDD
 *                                UTILISED.CREDIT.LIMIT / UTILISED.DEBIT.LIMIT updated
 *               Monthly record : <customer id>-YYYYMM
 *                                UTILISED.CREDIT.LIMIT / UTILISED.DEBIT.LIMIT updated
 *                                UTILISED.MIGRATED.* preserved (migration data, read only)
 *
 *               No explicit record locking is implemented.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitUtilizationUpdater {
    private static final String CLASS_NAME = "CustomerLimitUtilizationUpdater";

    private final CustomerLimitRepository repository;

    /** @param repository DB access of the calling routine */
    public CustomerLimitUtilizationUpdater(CustomerLimitRepository repository) {
        this.repository = repository;
    }

    /** Daily utilisation record id: <customer id>-YYYYMMDD of the transaction date. */
    public static String dailyRecordId(CustomerLimitDTO request) {
        return request.getCustomerId() + "-" + request.getTransactionDate().format(Constants.DAILY_ID_FORMAT);
    }

    /** Monthly utilisation record id: <customer id>-YYYYMM of the transaction date. */
    public static String monthlyRecordId(CustomerLimitDTO request) {
        return request.getCustomerId() + "-" + request.getTransactionDate().format(Constants.MONTHLY_ID_FORMAT);
    }

    /** Adds the transaction amounts to the daily and monthly utilisation. */
    public void update(CustomerLimitDTO request) {
        LogUtil.info(CLASS_NAME, "update", "CALC", "customer=" + request.getCustomerId() + " aaa=" + request.getTransactionId()
                + " date=" + request.getTransactionDate() + " credit=" + request.getCreditAmount()
                + " debit=" + request.getDebitAmount());
        updateDailyUtilization(request, request.getCreditAmount(), request.getDebitAmount());
        updateMonthlyUtilization(request, request.getCreditAmount(), request.getDebitAmount());
        LogUtil.info(CLASS_NAME, "update", "END", "customer=" + request.getCustomerId() + " daily="
                + dailyRecordId(request) + " monthly=" + monthlyRecordId(request));
    }

    /** Subtracts the original transaction amounts from the daily and monthly utilisation. */
    public void reverse(CustomerLimitDTO originalTransaction) {
        LogUtil.info(CLASS_NAME, "reverse", "CALC", "customer=" + originalTransaction.getCustomerId()
                + " aaa=" + originalTransaction.getTransactionId() + " date=" + originalTransaction.getTransactionDate()
                + " credit=" + originalTransaction.getCreditAmount() + " debit=" + originalTransaction.getDebitAmount());
        updateDailyUtilization(originalTransaction, originalTransaction.getCreditAmount().negate(),
                originalTransaction.getDebitAmount().negate());
        updateMonthlyUtilization(originalTransaction, originalTransaction.getCreditAmount().negate(),
                originalTransaction.getDebitAmount().negate());
        LogUtil.info(CLASS_NAME, "reverse", "END", "customer=" + originalTransaction.getCustomerId() + " daily="
                + dailyRecordId(originalTransaction) + " monthly=" + monthlyRecordId(originalTransaction));
    }

    /** Adds credit / debit (negative for a reversal) to the daily record's UTILISED.CREDIT/DEBIT.LIMIT. */
    private void updateDailyUtilization(CustomerLimitDTO request, BigDecimal credit, BigDecimal debit) {
        String recordId = dailyRecordId(request);
        try {
            EbAlbeUtilisedLimitRecord utilRec = readOrCreate(recordId);
            String oldCredit = utilRec.getUtilisedCreditLimit().getValue();
            String oldDebit = utilRec.getUtilisedDebitLimit().getValue();
            String newCredit = addAmount(oldCredit, credit);
            String newDebit = addAmount(oldDebit, debit);
            utilRec.setUtilisedCreditLimit(newCredit);
            utilRec.setUtilisedDebitLimit(newDebit);
            repository.writeUtilisedLimit(recordId, utilRec);
            LogUtil.info(CLASS_NAME, "updateDailyUtilization", "UPDATED", "record=" + recordId + " credit " + oldCredit + " -> "
                    + newCredit + ", debit " + oldDebit + " -> " + newDebit);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "updateDailyUtilization", "FAILED", "record=" + recordId, e);
        }
    }

    /**
     * Adds credit / debit (negative for a reversal) to the monthly record's UTILISED.CREDIT/DEBIT.LIMIT.
     * UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT hold migration data and are never changed here.
     */
    private void updateMonthlyUtilization(CustomerLimitDTO request, BigDecimal credit, BigDecimal debit) {
        String recordId = monthlyRecordId(request);
        try {
            EbAlbeUtilisedLimitRecord utilRec = readOrCreate(recordId);
            String oldCredit = utilRec.getUtilisedCreditLimit().getValue();
            String oldDebit = utilRec.getUtilisedDebitLimit().getValue();
            String newCredit = addAmount(oldCredit, credit);
            String newDebit = addAmount(oldDebit, debit);
            utilRec.setUtilisedCreditLimit(newCredit);
            utilRec.setUtilisedDebitLimit(newDebit);
            repository.writeUtilisedLimit(recordId, utilRec);
            LogUtil.info(CLASS_NAME, "updateMonthlyUtilization", "UPDATED", "record=" + recordId + " credit " + oldCredit + " -> "
                    + newCredit + ", debit " + oldDebit + " -> " + newDebit);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "updateMonthlyUtilization", "FAILED", "record=" + recordId, e);
        }
    }

    /** Existing utilisation record, or a new empty one for the first transaction of the day / month. */
    private EbAlbeUtilisedLimitRecord readOrCreate(String recordId) {
        EbAlbeUtilisedLimitRecord utilRec = repository.readUtilisedLimit(recordId);
        if (utilRec != null) {
            LogUtil.info(CLASS_NAME, "readOrCreate", "FOUND", "record=" + recordId);
            return utilRec;
        }
        LogUtil.info(CLASS_NAME, "readOrCreate", "NEW", "record=" + recordId);
        return repository.newUtilisedLimitRecord();
    }

    /** current + delta, never below zero (protects against reversals on a later day). */
    private String addAmount(String current, BigDecimal delta) {
        BigDecimal result = ValidationUtil.toAmount(current).add(delta);
        if (result.signum() < 0) {
            LogUtil.info(CLASS_NAME, "addAmount", "ADJUSTED", "result " + result + " below zero, set to 0");
            result = BigDecimal.ZERO;
        }
        return result.toPlainString();
    }
}
