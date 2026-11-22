package com.temenos.ALBE.CustomerLimit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.temenos.ALBE.CustomerLimit.constants.Constants;
import com.temenos.ALBE.CustomerLimit.model.CustomerLimitDTO;
import com.temenos.ALBE.CustomerLimit.repository.CustomerLimitRepository;
import com.temenos.ALBE.CustomerLimit.util.LogUtil;
import com.temenos.ALBE.CustomerLimit.util.ValidationUtil;
import com.temenos.ALBE.CustomerLimit.validation.CustomerLimitRequestValidator;

/*-----------------------------------------------------------------------------
 * Description : The one entry point of customer limit management. It works only with
 *               TRANSACTION INFO (CustomerLimitDTO: customer, transaction, date, currency,
 *               credit or debit amount) and CUSTOMER LIMIT INFO (EB.ALBE.CIF.LIMIT), so it does
 *               not depend on where the transaction comes from. Every routine - wherever the bank
 *               attaches it (ACTIVITY.API, FT / TT version, ACCOUNT.PARAMETER accounting hook) -
 *               builds the transaction info and calls this class:
 *
 *                 checkDailyMonthly  daily / monthly credit or debit limit
 *                 checkMaxCif        maximum CIF balance (credits only)
 *                 checkAll           both - for routines that are the only check on a transaction
 *                 count              utilisation at input (an earlier input of the same
 *                                    transaction is taken back first - also when the same
 *                                    transaction arrives from a second routine: no double count);
 *                                    own-account transfer (same customer on both sides): not counted
 *                 reverse            utilisation taken back, from the day it was counted on
 *
 *               The request is validated here once (CustomerLimitRequestValidator); the business
 *               rules stay in DailyMonthlyLimitService / MaximumCifLimitService.
 *------------------------------------------------------------------------------
 * Modification History :
 *-----------------------------------------------------------------------------
 * 22-Nov-2026   Development      Initial Version
 * 22-Nov-2026   Development      Key = root reference (no .CR/.DR); own-account transfers not counted
 *-----------------------------------------------------------------------------
 */
public class CustomerLimitEngine {
    private static final String CLASS_NAME = "CustomerLimitEngine";

    private final CustomerLimitRepository repository;
    private final DailyMonthlyLimitService dailyMonthlyService;

    public CustomerLimitEngine(CustomerLimitRepository repository) {
        this.repository = repository;
        this.dailyMonthlyService = new DailyMonthlyLimitService(repository);
    }

    /**
     * Daily / monthly check. Returns the checked request (with its breaches and "limit not defined" /
     * "no rate" flags) for the view to raise overrides; null if the transaction info is not valid.
     */
    public CustomerLimitDTO checkDailyMonthly(CustomerLimitDTO transaction) {
        if (!CustomerLimitRequestValidator.isValidForDailyMonthly(transaction) || isOwnTransfer(transaction)) {
            return null;
        }
        CustomerLimitDTO result = transaction.copyTransaction();
        dailyMonthlyService.validate(result);
        LogUtil.info(CLASS_NAME, "checkDailyMonthly", "END", result.logKey() + " breaches="
                + result.getBreaches().size());
        return result;
    }

    /** Maximum CIF balance check (credits only); null for a debit or invalid transaction info. */
    public CustomerLimitDTO checkMaxCif(CustomerLimitDTO transaction) {
        if (!transaction.isCredit() || !CustomerLimitRequestValidator.isValidForMaxCif(transaction)
                || isOwnTransfer(transaction)) {
            return null;
        }
        CustomerLimitDTO result = transaction.copyTransaction();
        new MaximumCifLimitService(repository).validate(result);
        LogUtil.info(CLASS_NAME, "checkMaxCif", "END", result.logKey() + " breaches=" + result.getBreaches().size());
        return result;
    }

    /** Both checks; the checked requests to raise overrides for (0, 1 or 2). */
    public List<CustomerLimitDTO> checkAll(CustomerLimitDTO transaction) {
        List<CustomerLimitDTO> results = new ArrayList<>();
        CustomerLimitDTO dailyMonthly = checkDailyMonthly(transaction);
        if (dailyMonthly != null) {
            results.add(dailyMonthly);
            if (dailyMonthly.isLimitCcyMissing()) {
                return results;     // error already given once; no second check on a blank currency
            }
        }
        CustomerLimitDTO maxCif = checkMaxCif(transaction);
        if (maxCif != null) {
            results.add(maxCif);
        }
        return results;
    }

    /**
     * Counts the transaction at input; an earlier count of the same transaction (same customer,
     * same side) is replaced. Own-account transfer (the same customer already counted on the other
     * side of this transaction): the earlier side is taken back and the transaction is not counted -
     * a record with amount 0 is kept as a marker so a later reversal takes nothing back.
     */
    public void count(CustomerLimitDTO transaction, String accountNo, String effectiveDate) {
        if (!CustomerLimitRequestValidator.isValidForDailyMonthly(transaction)) {
            return;
        }
        TxnDetailsLimitRecorder recorder = new TxnDetailsLimitRecorder(repository);
        List<CustomerLimitDTO> earlier = recorder.findOriginal(transaction);
        if (!earlier.isEmpty()) {
            CustomerLimitDTO counted = earlier.get(0);
            if (counted.getAmount().signum() == 0) {
                LogUtil.info(CLASS_NAME, "count", "SKIP", "own-account transfer, not counted " + transaction.logKey());
                return;
            }
            if (counted.isCredit() != transaction.isCredit()) {
                LogUtil.info(CLASS_NAME, "count", "OWN TRANSFER", "same customer on both sides - taking back the "
                        + (counted.isCredit() ? Constants.FLAG_CREDIT : Constants.FLAG_DEBIT) + " side, not counted "
                        + transaction.logKey());
                dailyMonthlyService.reverse(transaction, false);
                CustomerLimitDTO marker = transaction.copyTransaction();
                marker.setCreditAmount(BigDecimal.ZERO);
                marker.setDebitAmount(BigDecimal.ZERO);
                recorder.record(marker, accountNo, effectiveDate);
                return;
            }
        }
        LogUtil.info(CLASS_NAME, "count", "ACTION", "COUNT " + transaction.logKey());
        dailyMonthlyService.countAtInput(transaction, accountNo, effectiveDate);
    }

    /**
     * True when this transaction is the second side of an own-account transfer: the same customer
     * and transaction are already recorded on the other side, or as a not-counted marker (amount 0).
     * Checks are skipped then.
     */
    public boolean isOwnTransfer(CustomerLimitDTO transaction) {
        List<CustomerLimitDTO> earlier = new TxnDetailsLimitRecorder(repository).findOriginal(transaction);
        if (earlier.isEmpty()) {
            return false;
        }
        CustomerLimitDTO counted = earlier.get(0);
        boolean own = counted.getAmount().signum() == 0 || counted.isCredit() != transaction.isCredit();
        if (own) {
            LogUtil.info(CLASS_NAME, "isOwnTransfer", "SKIP", "own-account transfer, no check " + transaction.logKey());
        }
        return own;
    }

    /**
     * Takes the transaction back from the day it was counted on. The day comes from the transaction
     * info; if nothing was counted on that day, the counted record is looked up by the transaction
     * (one SELECT, only then) - e.g. a reversal on a later day.
     * @param fallbackToToday when nothing was counted at all, take back from the transaction day anyway
     * @return number of records changed
     */
    public int reverse(CustomerLimitDTO transaction, boolean fallbackToToday) {
        if (!CustomerLimitRequestValidator.isValidForDailyMonthly(transaction)) {
            return 0;
        }
        List<CustomerLimitDTO> recorded = new TxnDetailsLimitRecorder(repository).findOriginal(transaction);
        if (recorded.isEmpty()) {
            LocalDate counted = findCountedDate(transaction.getCustomerId(), transaction.getTransactionId());
            if (counted != null) {
                transaction.setTransactionDate(counted);
                recorded = new TxnDetailsLimitRecorder(repository).findOriginal(transaction);
            }
        }
        if (!recorded.isEmpty() && recorded.get(0).getAmount().signum() == 0) {
            // Own-account transfer marker: nothing was counted, so nothing to take back (no fallback).
            LogUtil.info(CLASS_NAME, "reverse", "SKIP", "own-account transfer, nothing counted " + transaction.logKey());
            return 0;
        }
        LogUtil.info(CLASS_NAME, "reverse", "ACTION", "REVERSE " + transaction.logKey());
        return dailyMonthlyService.reverse(transaction, fallbackToToday);
    }

    /**
     * Counted transaction key, the same wherever the routine is attached: the root reference
     * (e.g. FT26091ABCDE, AAACT26091XYZ). Together with the customer and the day it is the
     * EB.ALBE.TXN.DETAILS.LIMIT id, so one transaction seen by two routines replaces itself
     * instead of being counted twice.
     */
    public static String transactionKey(String reference) {
        return ValidationUtil.rootReference(reference);
    }

    /** Date a transaction was counted on, from its EB.ALBE.TXN.DETAILS.LIMIT id; null if none. */
    private LocalDate findCountedDate(String customerId, String transactionId) {
        for (String recordId : repository.selectTxnDetailsIdsForTransaction(customerId, transactionId)) {
            String[] parts = recordId.split(Constants.TXN_ID_SEPARATOR, 3);
            LocalDate date = parts.length == 3 ? ValidationUtil.toDate(parts[1]) : null;
            if (date != null) {
                LogUtil.info(CLASS_NAME, "findCountedDate", "FOUND", "id=" + recordId);
                return date;
            }
        }
        LogUtil.info(CLASS_NAME, "findCountedDate", "NOT FOUND", "customer=" + customerId + " txn=" + transactionId);
        return null;
    }
}
