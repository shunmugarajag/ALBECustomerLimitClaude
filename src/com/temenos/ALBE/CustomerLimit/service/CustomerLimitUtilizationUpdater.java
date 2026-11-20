package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
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
        return dailyRecordId(request.getCustomerId(), request.getTransactionDate());
    }

    /** Monthly utilisation record id: <customer id>-YYYYMM of the transaction date. */
    public static String monthlyRecordId(CustomerLimitDTO request) {
        return monthlyRecordId(request.getCustomerId(), request.getTransactionDate());
    }

    /** Daily utilisation record id: <customer id>-YYYYMMDD. */
    public static String dailyRecordId(String customerId, LocalDate date) {
        return customerId + "-" + date.format(Constants.DAILY_ID_FORMAT);
    }

    /** Monthly utilisation record id: <customer id>-YYYYMM. */
    public static String monthlyRecordId(String customerId, LocalDate date) {
        return customerId + "-" + date.format(Constants.MONTHLY_ID_FORMAT);
    }

    /** UTILISED.CREDIT.LIMIT (or UTILISED.DEBIT.LIMIT) of the record; 0 when there is no record yet. */
    public static BigDecimal utilisedAmount(EbAlbeUtilisedLimitRecord utilRec, boolean credit) {
        if (utilRec == null) {
            return BigDecimal.ZERO;
        }
        return ValidationUtil.toAmount(credit ? utilRec.getUtilisedCreditLimit().getValue()
                : utilRec.getUtilisedDebitLimit().getValue());
    }

    /** UTILISED.MIGRATED.CREDIT.LIMIT (or ...DEBIT.LIMIT) of the monthly record; 0 when there is no record. */
    public static BigDecimal migratedAmount(EbAlbeUtilisedLimitRecord utilRec, boolean credit) {
        if (utilRec == null) {
            return BigDecimal.ZERO;
        }
        return ValidationUtil.toAmount(credit ? utilRec.getUtilisedMigratedCreditLimit().getValue()
                : utilRec.getUtilisedMigratedDebitLimit().getValue());
    }

    /** Adds the transaction amounts to the daily and monthly utilisation. */
    public void update(CustomerLimitDTO request) {
        apply(request, "update", request.getCreditAmount(), request.getDebitAmount());
    }

    /** Subtracts the original transaction amounts from the daily and monthly utilisation. */
    public void reverse(CustomerLimitDTO originalTransaction) {
        apply(originalTransaction, "reverse", originalTransaction.getCreditAmount().negate(),
                originalTransaction.getDebitAmount().negate());
    }

    /** Adds credit / debit (negative for a reversal) to the day's and the month's record. */
    private void apply(CustomerLimitDTO request, String action, BigDecimal credit, BigDecimal debit) {
        String dailyId = dailyRecordId(request);
        String monthlyId = monthlyRecordId(request);
        LogUtil.info(CLASS_NAME, action, "START", request.logKey() + " date=" + request.getTransactionDate()
                + " credit=" + credit + " debit=" + debit);
        addToRecord(dailyId, credit, debit);
        addToRecord(monthlyId, credit, debit);
        LogUtil.info(CLASS_NAME, action, "END", request.logKey() + " daily=" + dailyId + " monthly=" + monthlyId);
    }

    /**
     * Adds credit / debit to UTILISED.CREDIT/DEBIT.LIMIT of one daily or monthly record.
     * UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT hold migration data and are never changed here.
     */
    private void addToRecord(String recordId, BigDecimal credit, BigDecimal debit) {
        try {
            EbAlbeUtilisedLimitRecord utilRec = readOrCreate(recordId);
            String oldCredit = utilRec.getUtilisedCreditLimit().getValue();
            String oldDebit = utilRec.getUtilisedDebitLimit().getValue();
            String newCredit = addAmount(oldCredit, credit);
            String newDebit = addAmount(oldDebit, debit);

            utilRec.setUtilisedCreditLimit(newCredit);
            utilRec.setUtilisedDebitLimit(newDebit);
            repository.writeUtilisedLimit(recordId, utilRec);

            LogUtil.info(CLASS_NAME, "addToRecord", "UPDATED", "record=" + recordId
                    + " credit " + oldCredit + " -> " + newCredit + ", debit " + oldDebit + " -> " + newDebit);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, "addToRecord", "FAILED", "record=" + recordId, e);
        }
    }

    /** Existing utilisation record, or a new empty one for the first transaction of the day / month. */
    private EbAlbeUtilisedLimitRecord readOrCreate(String recordId) {
        EbAlbeUtilisedLimitRecord utilRec = repository.readUtilisedLimit(recordId);
        if (utilRec != null) {
            LogUtil.detail(CLASS_NAME, "readOrCreate", "FOUND", "record=" + recordId);
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
