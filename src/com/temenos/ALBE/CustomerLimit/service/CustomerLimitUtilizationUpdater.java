package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.t24.api.records.ebalbeutilisedlimit.EbAlbeUtilisedLimitRecord;

/*-----------------------------------------------------------------------------
 * Description : EB.ALBE.UTILISED.LIMIT daily and monthly utilisation.
 *
 *               Daily record   : <customer id>-YYYYMMDD
 *               Monthly record : <customer id>-YYYYMM
 *               UTILISED.CREDIT.LIMIT / UTILISED.DEBIT.LIMIT are changed;
 *               UTILISED.MIGRATED.CREDIT/DEBIT.LIMIT (monthly) are migration data, read only.
 *
 *               The caller reads the record once (for the check) and passes it to add();
 *               a save failure marks the repository (the routine then raises
 *               EB-ALBE.LIMIT.UPDATE.FAILED, D37). No record locking (G2, team decision).
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 20-Nov-2026   Development      Initial Version
 * 25-Nov-2026   Development      notes (used before -> after) for the RESULT log line
 * 26-Nov-2026   Development      Clean rewrite (D47): one add() with the record already read
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitUtilizationUpdater {
    private static final String CLASS_NAME = "CustomerLimitUtilizationUpdater";
    private static final String LABEL_RECORD = "record=";

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

    /**
     * Adds credit / debit (negative to take back) to one daily or monthly record and writes it.
     * @param current the record as read by the caller; null = no record yet, a new one is created
     * @param request gets a note "used before -> after" for the RESULT log line
     */
    public void add(CustomerLimitDTO request, String period, String recordId, EbAlbeUtilisedLimitRecord current,
            BigDecimal credit, BigDecimal debit) {
        if (credit.signum() == 0 && debit.signum() == 0) {
            request.addNote(period + " " + recordId + " unchanged (same amount as the earlier input)");
            return;
        }
        try {
            EbAlbeUtilisedLimitRecord utilRec = current;
            if (utilRec == null) {
                LogUtil.info(CLASS_NAME, "add", "NEW", LABEL_RECORD + recordId);
                utilRec = repository.newUtilisedLimitRecord();
            }
            String oldCredit = utilRec.getUtilisedCreditLimit().getValue();
            String oldDebit = utilRec.getUtilisedDebitLimit().getValue();
            String newCredit = addAmount(oldCredit, credit);
            String newDebit = addAmount(oldDebit, debit);
            utilRec.setUtilisedCreditLimit(newCredit);
            utilRec.setUtilisedDebitLimit(newDebit);
            if (!repository.writeUtilisedLimit(recordId, utilRec)) {
                // logged and flagged by the repository
                request.addNote(period + " " + recordId + " SAVE FAILED");
                return;
            }
            boolean isCredit = credit.signum() != 0;
            request.addNote(period + " " + recordId + " used " + (isCredit ? "credit " : "debit ")
                    + ValidationUtil.formatAmount(ValidationUtil.toAmount(isCredit ? oldCredit : oldDebit)) + " -> "
                    + ValidationUtil.formatAmount(ValidationUtil.toAmount(isCredit ? newCredit : newDebit)));
            LogUtil.info(CLASS_NAME, "add", "UPDATED", LABEL_RECORD + recordId + " credit " + oldCredit + " -> "
                    + newCredit + ", debit " + oldDebit + " -> " + newDebit);
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "add", "FAILED", LABEL_RECORD + recordId, e);
            request.addNote(period + " " + recordId + " SAVE FAILED");
            repository.markSaveFailed(CLASS_NAME, "add " + recordId);
        }
    }

    /** current + delta, never below zero (protects against a take-back larger than what is there). */
    private String addAmount(String current, BigDecimal delta) {
        BigDecimal result = ValidationUtil.toAmount(current).add(delta);
        if (result.signum() < 0) {
            LogUtil.info(CLASS_NAME, "addAmount", "ADJUSTED", "result " + result + " below zero, set to 0");
            result = BigDecimal.ZERO;
        }
        return result.toPlainString();
    }
}
